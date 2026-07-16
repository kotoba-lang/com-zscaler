(ns noroshi.cells.reliability-qual.test-state-machine
  "Tests for the noroshi reliability_qual state machine. Drives the phase
  progression init -> suite_selected -> stress_planned -> acceptance_judged
  -> qual_committed for a clean PASS and a FAILing suite, and pins the
  select-suite gates: missing device_id refused, unknown test-type name
  refused, an omitted suite defaults to all four GR-468-SHAPE tests, and a
  missing result for a selected test fails (never silently passes, G10)."
  (:require [clojure.test :refer [deftest is testing]]
            [noroshi.cells.reliability-qual.state-machine :as sm]
            [noroshi.methods.reliability-qual :as rq]))

(def ^:private clean-results
  {"thermal-cycling" {:cycles-completed 600 :achieved-low-temp-c -42.0
                      :achieved-high-temp-c 87.0 :param-drift-pct 2.0}
   "damp-heat" {:hours-completed 550.0 :achieved-temp-c 85.0
               :achieved-rh-pct 85.0 :param-drift-pct 1.5}
   "mechanical-shock" {:pulses-completed 6 :achieved-peak-g 1550.0
                       :achieved-duration-ms 0.52 :functional-after? true}
   "fibre-pull" {:applied-force-n 6.0 :held-s 6.0 :delaminated? false}})

(defn- run [inp]
  (reduce (fn [s f] (merge s (f s))) inp
          [sm/transition-select-suite sm/transition-stress-plan
           sm/transition-acceptance sm/transition-emit]))

(deftest test-full-happy-path-all-tests-pass
  (let [out (run {"cell_state" {} "device_id" "cpo-2km-100g" "results" clean-results})
        cs (get out "cell_state")]
    (is (= "suite_selected" (get-in (sm/transition-select-suite {"cell_state" {} "device_id" "cpo-2km-100g"}) ["cell_state" "phase"])))
    (is (= "qual_committed" (get cs "phase")))
    (is (= "end" (get out "next_node")))
    (is (= "pass" (get cs "acceptance")))
    (let [qual (get-in cs ["payload" "qual"])]
      (is (= "cpo-2km-100g" (get qual "deviceId")))
      (is (true? (get qual "dryRun")))
      (is (true? (get qual "representative")))
      (is (= "pass" (get qual "acceptance"))))))

(deftest test-default-suite-is-all-four-tests
  (let [s1 (sm/transition-select-suite {"cell_state" {} "device_id" "cpo-2km-100g"})]
    (is (= (sort (map name rq/test-types)) (sort (get-in s1 ["cell_state" "suite"]))))))

(deftest test-missing-device-id-refused
  (is (thrown? clojure.lang.ExceptionInfo
               (sm/transition-select-suite {"cell_state" {} "device_id" ""}))))

(deftest test-unknown-test-type-refused
  (is (thrown? clojure.lang.ExceptionInfo
               (sm/transition-select-suite
                {"cell_state" {} "device_id" "cpo-2km-100g" "suite" ["xray-inspection"]}))))

(deftest test-missing-result-for-selected-test-fails-not-silently-passes
  (testing "G10: a selected test with no submitted result must FAIL, not silently pass"
    (let [out (run {"cell_state" {} "device_id" "cpo-2km-100g"
                    "suite" ["thermal-cycling" "damp-heat"]
                    "results" {"thermal-cycling" (get clean-results "thermal-cycling")}})]
      (is (= "fail" (get-in out ["cell_state" "acceptance"])))
      (is (not (:pass? (get-in out ["cell_state" "payload" "judgment" :per-test :damp-heat])))))))

(deftest test-one-out-of-spec-result-fails-overall
  (let [bad-results (assoc-in clean-results ["thermal-cycling" :cycles-completed] 1)
        out (run {"cell_state" {} "device_id" "cpo-2km-100g" "results" bad-results})]
    (is (= "fail" (get-in out ["cell_state" "acceptance"])))
    (is (= "fail" (get-in out ["cell_state" "payload" "qual" "acceptance"])))))

(deftest test-stress-plan-records-criteria-for-selected-suite-only
  (let [s1 (sm/transition-select-suite {"cell_state" {} "device_id" "cpo-2km-100g"
                                        "suite" ["fibre-pull"]})
        s2 (sm/transition-stress-plan s1)]
    (is (= #{"fibre-pull"} (set (clojure.core/keys (get-in s2 ["cell_state" "payload" "criteria"])))))))
