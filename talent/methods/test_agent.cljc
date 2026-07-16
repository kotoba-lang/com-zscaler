(ns talent.methods.test-agent
  "talent — registry tests. 1:1 port of py/test_agent.py. Verifies the PII-Tier-3 structural
  invariants of ADR-2606072600: G1 self-sovereign (third-party register + prohibited source
  refused), G3 signal-e2e (plaintext identifying field refused, ciphertext accepted), G2 cohort-
  first k-anonymity (below-k suppressed; ≥k aggregate only), G4 hard-delete (forget removes the
  profile entirely, no soft-delete flag)."
  (:require [clojure.test :refer [deftest is]]
            [clojure.string :as str]
            [talent.methods.agent :as agent]))

(def ALICE "did:plc:alice")
(def BOB "did:plc:bob")

(defn- profile* [& kvs]
  (merge {"isco" "2512" "country" "JP" "skills" ["python" "rust"]} (apply hash-map kvs)))

;; ── self-sovereign ──
(deftest test-self-register-ok
  (let [out (agent/register-self ALICE ALICE (profile*))]
    (is (= "registered" (get out "state")))
    (is (= ALICE (get-in out ["profile" "registeredBy"])))
    (is (= (agent/subject-hash ALICE) (get-in out ["profile" "subjectDidHash"])))))

(deftest test-third-party-refused
  (let [out (agent/register-self BOB ALICE (profile*))]   ; caller != subject
    (is (= "refused" (get out "state")))
    (is (str/includes? (get out "reason") "G1"))))

(deftest test-prohibited-source-ingest-refused
  (doseq [src ["linkedin" "indeed" "scraped-db" "purchased-list"]]
    (is (= "refused" (get (agent/ingest-external src) "state")))))

(deftest test-allowed-enrichment
  (is (= "allowed" (get (agent/ingest-external "orcid") "state"))))

(deftest test-prohibited-enrichment-on-profile-refused
  (is (= "refused" (get (agent/register-self ALICE ALICE (profile* "enrichmentSource" "linkedin")) "state"))))

;; ── signal-e2e ──
(deftest test-plaintext-pii-refused
  (let [out (agent/register-self ALICE ALICE (profile* "email" "alice@example.com"))]
    (is (= "refused" (get out "state")))
    (is (str/includes? (get out "reason") "G3"))))

(deftest test-ciphertext-pii-ok
  (is (= "registered" (get (agent/register-self ALICE ALICE (profile* "email" "signal:v1:deadbeef")) "state"))))

(deftest test-is-encrypted
  (is (agent/is-encrypted "signal:v1:abc"))
  (is (not (agent/is-encrypted "plain"))))

;; ── cohort k-anon ──
(deftest test-below-k-suppressed
  (let [profiles (repeat 3 (profile*))                    ; k default 5
        out (agent/cohort-stats "2512" "JP" profiles)]
    (is (get out "suppressed"))
    (is (nil? (get out "count")))))

(deftest test-at-or-above-k-aggregate
  (let [profiles (repeat 5 (profile*))
        out (agent/cohort-stats "2512" "JP" profiles)]
    (is (= false (get out "suppressed")))
    (is (= 5 (get out "count")))
    (is (some #{"python"} (get out "topSkills")))))

(deftest test-no-individual-field-in-output
  (let [profiles (repeat 6 (profile* "email" "signal:v1:x"))
        out (agent/cohort-stats "2512" "JP" profiles)]
    (is (every? (fn [k] (let [kl (str/lower-case k)]
                          (and (not (str/includes? kl "email"))
                               (not (str/includes? kl "profile"))
                               (not (str/includes? kl "name")))))
                (keys out)))))

;; ── hard-delete ──
(def store [{"subjectDidHash" (agent/subject-hash "did:plc:alice") "isco" "2512"}
            {"subjectDidHash" (agent/subject-hash "did:plc:bob") "isco" "2512"}])

(deftest test-forget-removes-entirely
  (let [out (agent/forget-self ALICE ALICE store)]
    (is (= "forgotten" (get out "state")))
    (is (= 1 (get out "hardDeleted")))
    (is (not (some #{(agent/subject-hash ALICE)} (map #(get % "subjectDidHash") (get out "store")))))
    (is (every? #(not (contains? % "_alive")) (get out "store")))))   ; no soft-delete flag (G4)

(deftest test-forget-others-refused
  (is (= "refused" (get (agent/forget-self BOB ALICE store) "state"))))

;; ── enrichment ──
(defn- prof [] {"isco" "2512" "country" "JP" "skills" ["python"]})

(deftest test-self-enrich-merges-skills
  (let [out (agent/attach-enrichment ALICE ALICE (prof) "github-public" {"skills" ["rust" "python"]})]
    (is (= "enriched" (get out "state")))
    (is (= ["python" "rust"] (sort (get-in out ["profile" "skills"]))))   ; deduped union
    (is (= ["github-public"] (get-in out ["profile" "enrichmentProvenance"])))))

(deftest test-third-party-enrich-refused
  (let [out (agent/attach-enrichment BOB ALICE (prof) "orcid" {"skills" ["x"]})]
    (is (= "refused" (get out "state")))
    (is (str/includes? (get out "reason") "G1"))))

(deftest test-prohibited-source-refused
  (is (= "refused" (get (agent/attach-enrichment ALICE ALICE (prof) "linkedin" {"skills" ["x"]}) "state"))))

(deftest test-plaintext-pii-enrichment-refused
  (let [out (agent/attach-enrichment ALICE ALICE (prof) "orcid" {"email" "a@b.com"})]
    (is (= "refused" (get out "state")))
    (is (str/includes? (get out "reason") "G3"))))

;; ── listOccupations ──
(deftest test-below-k-cohort-not-listed
  (let [profiles (repeat 3 {"isco" "2512" "country" "JP" "skills" []})]   ; k=5
    (is (= [] (agent/list-occupations profiles)))))                        ; not even disclosed (G2)

(deftest test-at-or-above-k-listed
  (let [profiles (concat (repeat 5 {"isco" "2512" "country" "JP"})
                         (repeat 2 {"isco" "2512" "country" "US"}))       ; US below k
        out (agent/list-occupations profiles)]
    (is (= [{"isco" "2512" "country" "JP" "count" 5}] out))))             ; only JP
