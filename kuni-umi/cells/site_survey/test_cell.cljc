(ns kuni-umi.cells.site-survey.test-cell
  "kuni-umi 国産み SiteSurveyCell smoke + invariant tests.

  cell.py has no cell-level Python test (the actor's test_agent.py exercises the
  flat py/agent.py module, not the cells — same as tatekata). These assertions
  pin the ported cell's pure surface + its R0 hardware/SDK gates + the
  constitutional N≥2 witness invariant (ADR-2605201400 §9)."
  (:require [clojure.test :refer [deftest is testing]]
            [kuni-umi.cells.site-survey.cell :as cell]))

(deftest closed-enums-preserve-python-value-identities
  (is (= "electric" (:electric cell/utility-classes)))
  (is (= "multi" (:multi cell/utility-classes)))
  (is (= "orbit" (:orbit cell/domains)))
  (is (= 9 (count cell/utility-classes)))
  (is (= 5 (count cell/domains))))

(deftest witness-invariant-is-two
  ;; constitutional invariant N ≥ 2 independent robot DIDs (ADR-2605201400 §9)
  (is (= 2 cell/witness-invariant-min))
  (is (= 2 (get (cell/healthz-extra (cell/make-cell-deps)) "witness_invariant_min"))))

(def eligible-site-state
  "A realistic, constitutionally-eligible site state — used by tests that
  need jurisdiction-eligibility to actually accept."
  (merge cell/site-survey-state
         {:intendedUse "community" :jurisdictionDid "did:web:example.gov"
          :localLawAttestationCid "bafyattestation"}))

(deftest jurisdiction-eligibility-accepts-a-realistic-eligible-site
  (let [out (cell/jurisdiction-eligibility eligible-site-state (cell/make-cell-deps))]
    (is (true? (:accepted out)))
    (is (nil? (:rejectionReason out)))))

(deftest jurisdiction-eligibility-rejects-the-empty-fresh-state
  ;; Was previously accepted=true unconditionally (R0 placeholder); now the
  ;; constitutional intendedUse floor + required-field checks correctly
  ;; reject a state with no intendedUse/jurisdictionDid/attestation at all.
  (let [out (cell/jurisdiction-eligibility cell/site-survey-state (cell/make-cell-deps))]
    (is (false? (:accepted out)))
    (is (some? (:rejectionReason out)))))

(deftest jurisdiction-eligibility-hard-rejects-military-intended-use
  (testing "the constitutional intendedUse boundary cannot be overridden by
            an advisor that says :accepted true — governor wins"
    (let [state (merge eligible-site-state {:intendedUse "military"})
          out (cell/jurisdiction-eligibility
               state (cell/make-cell-deps
                      :llm-primary (fn [_] {:accepted true :rationale "advisor says fine"
                                            :confidence 1.0})))]
      (is (false? (:accepted out)))
      (is (re-find #"military" (:rejectionReason out))))))

(deftest jurisdiction-eligibility-rejects-missing-jurisdiction-did
  (let [state (dissoc eligible-site-state :jurisdictionDid)
        out (cell/jurisdiction-eligibility state (cell/make-cell-deps))]
    (is (false? (:accepted out)))
    (is (re-find #"jurisdictionDid" (:rejectionReason out)))))

(deftest jurisdiction-eligibility-rejects-missing-local-law-attestation
  (let [state (dissoc eligible-site-state :localLawAttestationCid)
        out (cell/jurisdiction-eligibility state (cell/make-cell-deps))]
    (is (false? (:accepted out)))
    (is (re-find #"localLawAttestationCid" (:rejectionReason out)))))

(deftest jurisdiction-eligibility-respects-an-injected-advisor-rejection
  (let [out (cell/jurisdiction-eligibility
             eligible-site-state
             (cell/make-cell-deps
              :llm-primary (fn [_] {:accepted false :rationale "looks off"
                                    :confidence 0.9})))]
    (is (false? (:accepted out)))
    (is (= "looks off" (:rejectionReason out)))))

(deftest jurisdiction-eligibility-rejects-low-confidence-advisor
  (let [out (cell/jurisdiction-eligibility
             eligible-site-state
             (cell/make-cell-deps
              :llm-primary (fn [_] {:accepted true :rationale "unsure"
                                    :confidence 0.2})))]
    (is (false? (:accepted out)))
    (is (re-find #"confidence" (:rejectionReason out)))))

(deftest mock-advise-defers-entirely-to-governor
  (is (= {:accepted true :rationale "mock advisor: deferring to governor rules" :confidence 1.0}
         (cell/mock-advise eligible-site-state))))

(deftest router-branches-on-accepted
  (is (= "witness_attest" (cell/router {:accepted true})))
  (is (= "emit_survey" (cell/router {:accepted false}))))

(deftest hardware-sdk-nodes-raise-at-r0
  (let [deps (cell/make-cell-deps)]
    (doseq [f [cell/allocate-scout-fleet cell/collect-sensor-blob
               cell/witness-attest cell/emit-survey]]
      (let [e (try (f cell/site-survey-state deps) nil
                   (catch clojure.lang.ExceptionInfo e e))]
        (is (some? e))
        (is (true? (:kuni-umi/not-implemented (ex-data e))))))))

(deftest build-graph-wiring-is-faithful
  (let [g (cell/build-graph (cell/make-cell-deps))]
    (is (= 5 (count (:nodes g))))
    (is (contains? (:steps g) "jurisdiction_eligibility"))
    (is (= cell/router (get-in g [:conditional-edges "jurisdiction_eligibility"])))
    (is (some #{["emit_survey" "END"]} (:edges g)))
    ;; the pure node is callable through the graph; the gated ones raise
    (is (true? (:accepted ((get-in g [:steps "jurisdiction_eligibility"])
                           eligible-site-state))))
    (is (thrown? clojure.lang.ExceptionInfo
                 ((get-in g [:steps "allocate_scout_fleet"]) cell/site-survey-state)))))

(deftest event-shims-match-python
  (let [ev {"uri" "at://x" "cid" "bafy" "indexedAt" "2026-06-12"
            "rkey" "site-001" "value" {"siteDid" "did:web:etzhayyim.com:site:001"
                                       "utilityClass" "water"}}
        st (cell/state-from-event ev cell/trigger-nsid)]
    ;; default-state passes through value + audit fields (MST keys stay strings)
    (is (= "water" (get st "utilityClass")))
    (is (= "at://x" (get st "_event_uri")))
    ;; thread id = siteDid → siteCode → rkey
    (is (= "SiteSurveyCell:did:web:etzhayyim.com:site:001"
           (cell/thread-id-from-event ev cell/trigger-nsid)))
    (is (= "SiteSurveyCell:fallback"
           (cell/thread-id-from-event {"rkey" "fallback" "value" {}} cell/trigger-nsid)))))

(deftest nsids-are-the-charter-constants
  (is (= "com.etzhayyim.apps.etzhayyim.kuniUmi.defineDeploymentSite" cell/trigger-nsid))
  (is (= "com.etzhayyim.apps.etzhayyim.kuniUmi.submitSiteSurvey" cell/submit-nsid)))
