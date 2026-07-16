(ns toritsugi.governor-contract-test
  "The concierge charter as executable tests — toritsugi's analog of kyoninka's
  governor_contract_test. Invariant: the actor never guides / drafts / submits a
  procedure the ProcedureGovernor would reject, never auto-files an 代行
  (agent-on-behalf) submission, and never holds plaintext PII.

  These pin the constitutional gates G3/G4/G5/G6/G8/G10/G14/G15 (ADR-2605312030 §4)
  so a future R-phase cell wave cannot silently drift them."
  (:require [clojure.test :refer [deftest is testing]]
            [toritsugi.store :as store]
            [toritsugi.governor :as gov]))

(def today 20260709)

(defn- req
  ([op proc member consent]
   (req op proc member consent
       {:mode :member-self-submit :channel "online"
        :encrypted-pii-ref "com.etzhayyim.encrypted/blob-1"}))
  ([op proc member consent opts]
   (merge {:op op :procedure proc :member member :consent-ref consent
           :session "s-test"} opts)))

(defn- check [request proposal st]
  (gov/check request proposal st {:today today}))

;; ── clean happy path: a verified procedure + consenting member passes ──

(deftest clean-guide-passes
  (testing "G3/G4/G5/G8/G14 clean → ok, not high-stakes"
    (let [st (store/seed-db)
          v  (check (req :guide/build "jp-juminhyo-utsushi"
                         "did:web:member.alice" "consent-alice-juminhyo")
                    {:effect :guide :recommendation :guide-ready :confidence 0.9} st)]
      (is (:ok? v))
      (is (not (:hard? v)))
      (is (not (:high-stakes? v))))))

(deftest clean-member-self-submit-passes
  (testing "a member-self submit on a verified+fresh procedure passes (not high-stakes)"
    (let [st (store/seed-db)
          v  (check (req :submit/transmit "jp-juminhyo-utsushi"
                         "did:web:member.alice" "consent-alice-juminhyo")
                    {:effect :submit :recommendation :submit-ready :confidence 0.9} st)]
      (is (:ok? v) "clean member-self submit is ok")
      (is (not (:high-stakes? v)) "member-self-submit is NOT high-stakes (G15 default)"))))

;; ── G3 — consent-gated + own-procedure only ──

(deftest deficient-no-consent-held-and-unoverridable
  (testing "bob has no active consent → HARD hold (consent-not-bound), unoverridable"
    (let [st (store/seed-db)
          v  (check (req :submit/transmit "jp-juminhyo-utsushi"
                         "did:web:member.bob" "consent-bob-missing")
                    {:effect :submit :recommendation :submit-ready :confidence 0.99} st)]
      (is (:hard? v))
      (is (not (:ok? v)))
      (is (some #(#{:consent-not-bound :no-consent} (:rule %)) (:violations v))))))

(deftest blank-consent-ref-held
  (testing "a blank consent-ref is a HARD hold (no-consent)"
    (let [st (store/seed-db)
          v  (check (req :guide/build "jp-juminhyo-utsushi"
                         "did:web:member.alice" nil)
                    {:effect :guide :recommendation :guide-ready :confidence 0.9} st)]
      (is (:hard? v))
      (is (some #(= :no-consent (:rule %)) (:violations v))))))

;; ── G14 — verified-procedure-only (refuse unverified-seed / stale) ──

(deftest unverified-procedure-held
  (testing "unverified-seed procedure → HARD hold at submit (G14)"
    (let [st (store/seed-db)
          v  (check (req :submit/transmit "jp-unverified"
                         "did:web:member.alice" "consent-alice-juminhyo")
                    {:effect :submit :recommendation :submit-ready :confidence 0.9} st)]
      (is (:hard? v))
      (is (some #(= :unverified-procedure (:rule %)) (:violations v))))))

(deftest stale-procedure-held
  (testing "a verified-but-stale procedure → HARD hold (G14 freshness window)"
    (let [st (store/seed-db)
          v  (check (req :submit/transmit "jp-stale"
                         "did:web:member.alice" "consent-alice-juminhyo")
                    {:effect :submit :recommendation :submit-ready :confidence 0.9} st)]
      (is (:hard? v))
      (is (some #(= :stale-procedure (:rule %)) (:violations v))))))

;; ── G8 — non-fabrication (legal-basis + provenance) ──

(deftest fabricated-procedure-held
  (testing "a verified procedure missing legal-basis + provenance → HARD hold (G8)"
    (let [st (store/seed-db)
          v  (check (req :submit/transmit "jp-fabricated"
                         "did:web:member.alice" "consent-alice-juminhyo")
                    {:effect :submit :recommendation :submit-ready :confidence 0.9} st)]
      (is (:hard? v))
      (is (some #(#{:no-legal-basis :no-provenance} (:rule %)) (:violations v))))))

;; ── G5 — 行政書士法 / UPL boundary (no 作成代理, no advice) ──

(deftest upl-draft-for-member-held
  (testing "a draft proposal that asserts :draft-for-member → HARD hold (G5 UPL)"
    (let [st (store/seed-db)
          v  (check (req :draft/assist "jp-juminhyo-utsushi"
                         "did:web:member.alice" "consent-alice-juminhyo")
                    {:effect :draft-for-member :recommendation :draft-ready :confidence 0.99} st)]
      (is (:hard? v))
      (is (some #(#{:upl-reserve :effect-out-of-scope} (:rule %)) (:violations v))))))

(deftest upl-guide-with-advice-effect-held
  (testing "a guide proposal with :advise effect → HARD hold (G5)"
    (let [st (store/seed-db)
          v  (check (req :guide/build "jp-juminhyo-utsushi"
                         "did:web:member.alice" "consent-alice-juminhyo")
                    {:effect :advise :recommendation :guide-ready :confidence 0.9} st)]
      (is (:hard? v))
      (is (some #(= :upl-reserve (:rule %)) (:violations v))))))

;; ── G6 — PII confidentiality (encrypted.* only) ──

(deftest plaintext-pii-held
  (testing "plaintext-pii on the request → HARD hold (G6)"
    (let [st (store/seed-db)
          v  (check (-> (req :draft/assist "jp-juminhyo-utsushi"
                          "did:web:member.alice" "consent-alice-juminhyo")
                        (dissoc :encrypted-pii-ref)
                        (assoc :plaintext-pii "raw-PII-leak"))
                    {:effect :input-assist :recommendation :draft-ready :confidence 0.9} st)]
      (is (:hard? v))
      (is (some #(#{:plaintext-pii :unencrypted-pii} (:rule %)) (:violations v))))))

(deftest unencrypted-ref-held
  (testing "a draft with no encrypted-ref → HARD hold (G6)"
    (let [st (store/seed-db)
          v  (check (-> (req :draft/assist "jp-juminhyo-utsushi"
                          "did:web:member.alice" "consent-alice-juminhyo")
                        (dissoc :encrypted-pii-ref))
                    {:effect :input-assist :recommendation :draft-ready :confidence 0.9} st)]
      (is (:hard? v))
      (is (some #(= :unencrypted-pii (:rule %)) (:violations v))))))

;; ── G10 — lawful-channel-only ──

(deftest unlawful-channel-held
  (testing "a non-official channel → HARD hold (G10)"
    (let [st (store/seed-db)
          v  (check (req :submit/transmit "jp-juminhyo-utsushi"
                         "did:web:member.alice" "consent-alice-juminhyo"
                         {:mode :member-self-submit :channel "scrape-portal"
                          :encrypted-pii-ref "com.etzhayyim.encrypted/blob-1"})
                    {:effect :submit :recommendation :submit-ready :confidence 0.9} st)]
      (is (:hard? v))
      (is (some #(= :unlawful-channel (:rule %)) (:violations v))))))

;; ── G15 — member-self-submission default; 代行 is the gated exception ──

(deftest agent-on-behalf-is-high-stakes-and-escalates
  (testing "代行 (agent-on-behalf) submit → high-stakes + escalate (needs sign-off)"
    (let [st (store/seed-db)
          v  (check (req :submit/transmit "jp-juminhyo-utsushi"
                         "did:web:member.alice" "consent-alice-juminhyo"
                         {:mode :agent-on-behalf :channel "online"
                          :encrypted-pii-ref "com.etzhayyim.encrypted/blob-1"})
                    {:effect :submit :recommendation :submit-ready :confidence 0.95} st)]
      (is (:high-stakes? v) "代行 is ALWAYS high-stakes (G15)")
      (is (:escalate? v) "代行 escalates → human/Council sign-off")
      (is (not (:ok? v)) "代行 is never auto-ok — needs the interrupt + sign-off")
      (is (not (:hard? v)) "代行 on an otherwise-clean procedure is NOT a hard violation"))))

(deftest invalid-submission-mode-held
  (testing "a mode outside the valid set → HARD hold (G15)"
    (let [st (store/seed-db)
          v  (check (req :submit/transmit "jp-juminhyo-utsushi"
                         "did:web:member.alice" "consent-alice-juminhyo"
                         {:mode :silent-third-party :channel "online"
                          :encrypted-pii-ref "com.etzhayyim.encrypted/blob-1"})
                    {:effect :submit :recommendation :submit-ready :confidence 0.9} st)]
      (is (:hard? v))
      (is (some #(= :invalid-submission-mode (:rule %)) (:violations v))))))

;; ── backend swap: DatomicStore honors the same contract ──

(deftest datomic-store-honors-same-contract
  (testing "MemStore ≡ DatomicStore: the unverified hold reproduces on DatomicStore"
    (let [st (store/datomic-seed-db)
          v  (check (req :submit/transmit "jp-unverified"
                         "did:web:member.alice" "consent-alice-juminhyo")
                    {:effect :submit :recommendation :submit-ready :confidence 0.9} st)]
      (is (:hard? v))
      (is (some #(= :unverified-procedure (:rule %)) (:violations v)))
      (is (= "jp-juminhyo-utsushi" (:procedure-id (store/procedure st "jp-juminhyo-utsushi")))
          "DatomicStore round-trips the coded procedure record"))))
