(ns kuni-umi.cells.decommission.test-cell
  "kuni-umi 国産み DecommissionCell smoke + invariant tests. Linear all-gated
  graph; pins enums, the R0 substrate gates, graph wiring, and event shims."
  (:require [clojure.test :refer [deftest is]]
            [kuni-umi.cells.decommission.cell :as cell]))

(deftest closed-enums-preserve-python-value-identities
  (is (= "lifespan-expiry" (:lifespan-expiry cell/trigger-reasons)))
  (is (= "force-majeure" (:force-majeure cell/trigger-reasons)))
  (is (= 3 (count cell/trigger-reasons)))
  (is (= "decommissioned" (:decommissioned cell/site-states)))
  (is (= 3 (count cell/site-states))))

(deftest all-nodes-substrate-gated-at-r0
  (let [deps (cell/make-cell-deps)]
    (doseq [f [cell/decommission-plan cell/disconnect-from-utility cell/physical-teardown
               cell/land-return cell/release-stewardship cell/archive-site]]
      (let [e (try (f cell/decommission-state deps) nil
                   (catch clojure.lang.ExceptionInfo e e))]
        (is (some? e))
        (is (true? (:kuni-umi/not-implemented (ex-data e))))))))

(deftest build-graph-wiring-is-faithful
  (let [g (cell/build-graph (cell/make-cell-deps))]
    (is (= 6 (count (:nodes g))))
    (is (nil? (:conditional-edges g)))   ;; linear, no router
    (is (some #{["START" "decommission_plan"]} (:edges g)))
    (is (some #{["archive_site" "END"]} (:edges g)))
    (is (thrown? clojure.lang.ExceptionInfo
                 ((get-in g [:steps "decommission_plan"]) cell/decommission-state)))))

(deftest event-shims-match-python
  (let [ev {"uri" "at://x" "rkey" "site-001"
            "value" {"siteDid" "did:web:etzhayyim.com:site:001"
                     "triggerReason" "lifespan-expiry"}}
        st (cell/state-from-event ev "nsid")]
    (is (= "lifespan-expiry" (get st "triggerReason")))
    (is (= "DecommissionCell:did:web:etzhayyim.com:site:001"
           (cell/thread-id-from-event ev "nsid")))
    (is (= "DecommissionCell:fallback"
           (cell/thread-id-from-event {"rkey" "fallback" "value" {}} "nsid")))))

(deftest healthz-extra-reports-end-of-life
  (let [h (cell/healthz-extra (cell/make-cell-deps))]
    (is (= "end-of-life" (get h "phase")))
    (is (re-find #"ritual" (get h "religious_invariant")))))
