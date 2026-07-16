(ns toritsugi.methods.test-charter-gates
  "toritsugi 取次 — constitutional-gate conformance tests (manifest + central lexicons).

  Substrate-native Clojure (clj + datomic first tier). toritsugi is the citizen-side
  government/municipal-procedure CONCIERGE: it guides a consenting member through their OWN
  procedure, member-self-submission is the default, and the 行政書士法/UPL boundary is the
  critical gate. Its 15 gates are declared in the manifest `constitutionalGates` and encoded
  structurally across the 6 central AT-Proto lexicons at
  00-contracts/lexicons/com/etzhayyim/toritsugi/. This suite pins them so a future R-phase cell
  wave cannot silently drift them:

    G3  consent-gated (submission / benefit-match require a consentRef)
    G4  identity-bound — the member is the named 申請者本人 (every member record requires memberDid)
    G5  行政書士法 / UPL boundary — assistMode is `input-assist` ONLY (NEVER 作成代理/draft-for-member)
    G6  PII confidentiality — the draft body is an encrypted ref, never plaintext on MST
    G8  non-fabrication — a procedure must cite legalBasis + provenance (no invented 手続き/根拠)
    G10 lawful-channel-only — submission channel ⊆ {online, in-person, postal} (official channels)
    G14 verified-procedure-only — verificationStatus is the 3-tier set; unverified-seed exists
        to be REFUSED at submit
    G15 member-self-submission default — mode is exactly {member-self-submit, agent-on-behalf}
        (代行 is the single gated exception, not a silent third path)

  Reads central lexicons via cheshire (string keys). It weakens no gate; it asserts them.
  Touches neither the substrate-wide no-server-key (G7) nor Murakumo-only (its own G7) — the
  manifest already pins Murakumo-only inference and toritsugi holds no key."
  (:require [clojure.test :refer [deftest is run-tests]]
            [cheshire.core :as json]))

#?(:clj
   (do
     (def ^:private here (.getParentFile (java.io.File. ^String *file*)))      ;; methods/
     (def ^:private actor-dir (.getParentFile here))                          ;; toritsugi/
     (def ^:private root (.getParentFile (.getParentFile actor-dir)))          ;; repo root
     (def ^:private lexdir
       (java.io.File. root "00-contracts/lexicons/com/etzhayyim/toritsugi"))
     (defn- lex [name]
       (json/parse-string (slurp (java.io.File. lexdir (str name ".json")))))
     (defn- manifest []
       (:actor/manifest (clojure.edn/read-string (slurp (java.io.File. actor-dir "manifest.edn")))))))

(defn- record-node [doc]
  (let [main (get-in doc ["defs" "main"])]
    (or (get main "record") main)))
(defn- required-of [doc] (set (get (record-node doc) "required")))
(defn- prop-keys [doc] (set (keys (get (record-node doc) "properties"))))
(defn- known-vals [doc field]
  (set (get-in (record-node doc) ["properties" field "knownValues"])))

;; ── 15 gates declared (manifest dict, keys G1…G15) ──
(deftest all-15-gates-declared
  (let [gates (get-in (manifest) ["constitutionalGates" "gates"])
        gates (or gates (get (manifest) "constitutionalGates"))
        nums  (->> (keys gates)
                   (keep #(second (re-matches #"G(\d+).*" %)))
                   (map #(Integer/parseInt %)) set)]
    (is (= (set (range 1 16)) nums) "manifest must declare G1–G15")))

;; ── G5 — 行政書士法 / UPL boundary: input-assist ONLY, never 作成代理 ──
(deftest g5-upl-input-assist-only
  (is (= #{"input-assist"} (known-vals (lex "applicationDraft") "assistMode"))
      "G5: applicationDraft.assistMode must be input-assist ONLY (no 作成代理/draft-for-member)"))

;; ── G15 — member-self-submission default; 代行 is the single gated exception ──
(deftest g15-self-submission-default
  (is (= #{"member-self-submit" "agent-on-behalf"} (known-vals (lex "submissionRecord") "mode"))
      "G15: submissionRecord.mode must be exactly {member-self-submit, agent-on-behalf}")
  (is (contains? (prop-keys (lex "submissionRecord")) "councilGateRef")
      "G15: the agent-on-behalf path must carry a councilGateRef (gated exception)"))

;; ── G14 — verified-procedure-only: 3-tier verification status, unverified-seed exists to refuse ──
(deftest g14-verified-procedure-only
  (let [p (lex "procedure")]
    (is (contains? (required-of p) "verificationStatus") "G14: procedure must require verificationStatus")
    (is (contains? (required-of p) "lastVerified") "G14: procedure must require lastVerified")
    (is (= #{"unverified-seed" "maintainer-verified" "council-verified"}
           (known-vals p "verificationStatus"))
        "G14: verificationStatus is the 3-tier set (unverified-seed must be refusable at submit)")))

;; ── G8 — non-fabrication: a procedure cites its legal basis + provenance ──
(deftest g8-non-fabrication
  (let [r (required-of (lex "procedure"))]
    (is (contains? r "legalBasis") "G8: procedure must cite legalBasis")
    (is (contains? r "provenance") "G8: procedure must carry provenance")))

;; ── G10 — lawful-channel-only: official channels, no scraping/automation channel ──
(deftest g10-lawful-channel-only
  (is (= #{"online" "in-person" "postal"} (known-vals (lex "submissionRecord") "channel"))
      "G10: submissionRecord.channel must be official channels only"))

;; ── G3 consent-gated + G6 PII-encrypted ──
(deftest g3-consent-g6-encrypted
  (doseq [n ["submissionRecord" "benefitMatch"]]
    (is (contains? (required-of (lex n)) "consentRef")
        (str "G3: " n " must require consentRef")))
  (is (contains? (required-of (lex "applicationDraft")) "encryptedDraftRef")
      "G6: applicationDraft body must be an encrypted ref (no plaintext draft)")
  (is (not (contains? (prop-keys (lex "applicationDraft")) "draftBody"))
      "G6: no plaintext draft body field representable"))

;; ── G4 — identity-bound: every member-facing record names the member by DID ──
(deftest g4-identity-bound
  (doseq [n ["procedureGuide" "applicationDraft" "submissionRecord" "statusTrack" "benefitMatch"]]
    (is (contains? (required-of (lex n)) "memberDid")
        (str "G4: " n " must require memberDid (member = the named 申請者本人)"))))

;; ── ProcedureGovernor HARD surface = G3/G4/G5/G6/G8/G10/G14/G15 (machine-verify) ──
;; The governor's HARD set is the unoverridable charter surface (ADR-2605312030 §4).
;; We read src/toritsugi/governor.cljc, locate the explicit `(def hard-gates #{...})`
;; literal, and parse it with edn/read-string so the pin survives source reformatting
;; but BREAKS the moment a gate is dropped from the HARD surface. This is the toritsugi
;; analog of kyoninka's safety-contract test / robotaxi's MRC test: it pins exactly
;; which gates the independent Governor can veto the concierge intelligence on.
(defn- governor-hard-gates
  "Returns the parsed #{...} literal of `(def hard-gates ...)` from governor.cljc,
  or nil when the declaration is absent."
  []
  (let [src (slurp (java.io.File. actor-dir "src/toritsugi/governor.cljc"))
        idx (.indexOf ^String src "(def hard-gates")]
    (when (>= idx 0)
      (let [rest   (subs src idx)
            start  (.indexOf ^String rest "#{")
            end    (.indexOf ^String rest "}" start)]
        (when (and (>= start 0) (> end start))
          (clojure.edn/read-string (subs rest start (inc end))))))))

(deftest governor-hard-gates-cover-charter
  (let [hg (governor-hard-gates)]
    (is (some? hg) "governor declares an explicit `(def hard-gates #{...})` surface")
    (is (= #{:G3 :G4 :G5 :G6 :G8 :G10 :G14 :G15} hg)
        "Governor HARD invariants are exactly G3/G4/G5/G6/G8/G10/G14/G15 (ADR-2605312030 §4)")))

;; ── BPMN gateway mode_gw reflects G15 (member-self default | 代行 gated) ──
;; registry/toritsugi.procedure-flow.bpmn.edn is the executable BPMN-as-edn spine of
;; the StateGraph. The exclusive-gateway `mode_gw` must encode G15: member-self-submit
;; is the DEFAULT flow; 代行 (agent-on-behalf) is the single GATED exception routed
;; through a human/Council user-task (approval), never a silent third path.
;;
;; As of the Phase 4 datomize pass (2026-07-10), the file on disk is Datomic/
;; Datascript tx-data (`[{:db/id -1 :bpmn/id ... :bpmn/nodes "<pr-str blob>" ...}]`)
;; instead of a bare map — nested collections (:bpmn/nodes / :bpmn/flows) are
;; pr-str'd blob strings. `bpmn` below is tolerant of BOTH the legacy bare-map
;; shape and the new tx-data shape, and un-blobs :bpmn/nodes / :bpmn/flows back
;; into live maps so the assertions below (keyword/string lookups) are unchanged.
(defn- unblob [v]
  (if (string? v)
    (try (let [parsed (clojure.edn/read-string v)] (if (coll? parsed) parsed v))
         (catch Exception _ v))
    v))

(defn- bpmn []
  (let [raw (clojure.edn/read-string (slurp (java.io.File. actor-dir "registry/toritsugi.procedure-flow.bpmn.edn")))
        entity (if (and (vector? raw) (map? (first raw)) (contains? (first raw) :db/id))
                 (dissoc (first raw) :db/id)
                 raw)]
    (into {} (map (fn [[k v]] [k (unblob v)])) entity)))

(deftest bpmn-mode-gateway-reflects-g15
  (let [b    (bpmn)
        nodes (:bpmn/nodes b)
        flows (:bpmn/flows b)
        gw    (get nodes "mode_gw")]
    (is (= :exclusive-gateway (:bpmn/type gw))
        "mode_gw is an exclusive-gateway (G15: exactly two paths)")
    (is (= "F_self" (:bpmn/default gw))
        "member-self-submit (F_self) is the default gateway branch (G15 default)")
    (let [f-self  (get flows "F_self")
          f-agent (get flows "F_agent")]
      (is (and f-self (re-find #"member-self-submit" (:bpmn/condition f-self)))
          "F_self carries the member-self-submit condition (the G15 default)")
      (is (and f-agent (re-find #"agent-on-behalf" (:bpmn/condition f-agent)))
          "F_agent carries the agent-on-behalf condition (the single G15 gated exception)"))
    (is (= :user-task (:bpmn/type (get nodes "approval")))
        "the 代行 path routes through a human/Council user-task (G15 interrupt-before sign-off)")
    (is (= :user-task (:bpmn/type (get nodes "intake")))
        "intake is a user-task (G3/G4 — consent + DID binding are member-interactive)")))

#?(:clj
   (defn -main [& _]
     (let [r (run-tests 'toritsugi.methods.test-charter-gates)]
       (System/exit (if (zero? (+ (:fail r) (:error r))) 0 1)))))
