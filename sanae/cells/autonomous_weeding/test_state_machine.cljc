(ns sanae.cells.autonomous-weeding.test-state-machine
  "State-machine tests for the sanae 早苗 autonomous_weeding cell (R0).
  1:1 port of cells/test_state_machines.py (ADR-2606032100). G9 herbicide-free + G3 witness quorum +
  G2 displacement-dividend coupling (ADR-2606032130); .solve() raises at R0."
  (:require [clojure.test :refer [deftest is]]
            [sanae.cells.autonomous-weeding.state-machine :as sm]))

(defn- run
  [& {:keys [state weeds cleared method robot-sigs human displaced-cohort-id dividend-attested]
      :or {state {} weeds 10 cleared 9 method "mechanical" robot-sigs ["r1" "r2"] human "agronomist-did"
           displaced-cohort-id "did:web:sanae.etzhayyim.com/cohort/parcel-0001" dividend-attested true}}]
  (let [s (sm/transition-to-scanned (merge state {"rows" 40}))
        s (sm/transition-to-classified (merge s {"weeds_detected" weeds}))
        s (sm/transition-to-weed-cleared (merge s {"weeds_cleared" cleared "method" method}))
        s (sm/transition-to-pass-logged (merge s {"robot_sigs" robot-sigs "human_attestation" human
                                                   "displaced_cohort_id" displaced-cohort-id
                                                   "dividend_attested" dividend-attested}))]
    s))

(deftest test-happy-path-reaches-pass-logged
  (let [cs (get (run) "cell_state")
        rec (get-in cs ["payload" "weeding_pass_record"])]
    (is (= sm/phase-pass-logged (get cs "phase")))
    (is (= true (get rec "herbicideFree")))
    (is (= true (get rec "witnessQuorumMet")))
    (is (<= (get rec "weedsCleared") (get rec "weedsDetected")))
    (is (= true (get rec "dividendAttested")))
    (is (= "did:web:sanae.etzhayyim.com/cohort/parcel-0001" (get rec "displacedCohortId")))))

(deftest test-g2-blocks-unattested-dividend
  ;; documented in CLAUDE.md ("Do not let a deployment displace field labour without a funded
  ;; displacement cohort (G2 → ADR-2606032130)") but previously unenforced in this cell — a live
  ;; weeding pass could log without ever registering the displaced cohort's dividend.
  (is (thrown-with-msg? clojure.lang.ExceptionInfo #"G2 violation" (run :dividend-attested false))))

(deftest test-g2-blocks-missing-cohort-id
  (is (thrown-with-msg? clojure.lang.ExceptionInfo #"G2 violation" (run :displaced-cohort-id ""))))

(deftest test-g9-rejects-herbicide-method
  (is (thrown-with-msg? clojure.lang.ExceptionInfo #"G9 violation" (run :method "glyphosate"))))

(deftest test-laser-method-allowed
  (let [cs (get (run :method "laser") "cell_state")]
    (is (= "laser" (get cs "method")))
    (is (= true (get cs "herbicide_free")))))

(deftest test-g3-quorum-requires-two-robots-and-a-human
  ;; only 1 robot → quorum not met
  (is (= false (get-in (run :robot-sigs ["r1"] :human "agronomist-did")
                       ["cell_state" "payload" "weeding_pass_record" "witnessQuorumMet"])))
  ;; no human → quorum not met
  (is (= false (get-in (run :robot-sigs ["r1" "r2"] :human "")
                       ["cell_state" "payload" "weeding_pass_record" "witnessQuorumMet"]))))

(deftest test-solve-raises-at-r0
  (is (thrown-with-msg? clojure.lang.ExceptionInfo #"R0 scaffold" (sm/solve {}))))
