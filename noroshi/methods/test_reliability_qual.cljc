(ns noroshi.methods.test-reliability-qual
  "Tests for noroshi (烽) reliability-qualification PASS/FAIL engine
  (`methods/reliability_qual.cljc`). Pins: each judge-* fn correctly PASSes a
  clean result and FAILs (with a specific violation reason) an out-of-spec one;
  judge-suite treats an unsubmitted selected test as a FAILURE, never a silent
  pass (G10); the qual-plan record's :acceptance mirrors overall-pass?; and the
  report carries the GR-468/representative honesty markers."
  (:require [clojure.test :refer [deftest is testing run-tests]]
            [noroshi.methods.reliability-qual :as rq]))

(def ^:private clean-thermal
  {:cycles-completed 600 :achieved-low-temp-c -42.0 :achieved-high-temp-c 87.0 :param-drift-pct 2.0})
(def ^:private clean-damp
  {:hours-completed 550.0 :achieved-temp-c 85.0 :achieved-rh-pct 85.0 :param-drift-pct 1.5})
(def ^:private clean-shock
  {:pulses-completed 6 :achieved-peak-g 1550.0 :achieved-duration-ms 0.52 :functional-after? true})
(def ^:private clean-pull
  {:applied-force-n 6.0 :held-s 6.0 :delaminated? false})

;; ── thermal cycling ───────────────────────────────────────────────────────────
(deftest test-thermal-cycling-clean-passes
  (is (:pass? (rq/judge-thermal-cycling (:thermal-cycling rq/default-suite) clean-thermal))))

(deftest test-thermal-cycling-insufficient-cycles-fails
  (let [r (rq/judge-thermal-cycling (:thermal-cycling rq/default-suite)
                                     (assoc clean-thermal :cycles-completed 100))]
    (is (not (:pass? r)))
    (is (some #(re-find #"cycles-completed" %) (:violations r)))))

(deftest test-thermal-cycling-temp-range-not-reached-fails
  (let [r (rq/judge-thermal-cycling (:thermal-cycling rq/default-suite)
                                     (assoc clean-thermal :achieved-low-temp-c -20.0))]
    (is (not (:pass? r)))
    (is (some #(re-find #"low temp" %) (:violations r)))))

(deftest test-thermal-cycling-excess-drift-fails
  (let [r (rq/judge-thermal-cycling (:thermal-cycling rq/default-suite)
                                     (assoc clean-thermal :param-drift-pct 25.0))]
    (is (not (:pass? r)))
    (is (some #(re-find #"drift" %) (:violations r)))))

;; ── damp heat ─────────────────────────────────────────────────────────────────
(deftest test-damp-heat-clean-passes
  (is (:pass? (rq/judge-damp-heat (:damp-heat rq/default-suite) clean-damp))))

(deftest test-damp-heat-insufficient-hours-fails
  (let [r (rq/judge-damp-heat (:damp-heat rq/default-suite) (assoc clean-damp :hours-completed 10.0))]
    (is (not (:pass? r)))
    (is (some #(re-find #"hours-completed" %) (:violations r)))))

(deftest test-damp-heat-rh-not-reached-fails
  (let [r (rq/judge-damp-heat (:damp-heat rq/default-suite) (assoc clean-damp :achieved-rh-pct 40.0))]
    (is (not (:pass? r)))
    (is (some #(re-find #"RH" %) (:violations r)))))

;; ── mechanical shock ──────────────────────────────────────────────────────────
(deftest test-mechanical-shock-clean-passes
  (is (:pass? (rq/judge-mechanical-shock (:mechanical-shock rq/default-suite) clean-shock))))

(deftest test-mechanical-shock-insufficient-peak-fails
  (let [r (rq/judge-mechanical-shock (:mechanical-shock rq/default-suite)
                                      (assoc clean-shock :achieved-peak-g 800.0))]
    (is (not (:pass? r)))
    (is (some #(re-find #"peak" %) (:violations r)))))

(deftest test-mechanical-shock-nonfunctional-after-fails
  (let [r (rq/judge-mechanical-shock (:mechanical-shock rq/default-suite)
                                      (assoc clean-shock :functional-after? false))]
    (is (not (:pass? r)))
    (is (some #(re-find #"non-functional" %) (:violations r)))))

(deftest test-mechanical-shock-duration-out-of-tolerance-fails
  (let [r (rq/judge-mechanical-shock (:mechanical-shock rq/default-suite)
                                      (assoc clean-shock :achieved-duration-ms 2.0))]
    (is (not (:pass? r)))
    (is (some #(re-find #"duration" %) (:violations r)))))

;; ── fibre pull ────────────────────────────────────────────────────────────────
(deftest test-fibre-pull-clean-passes
  (is (:pass? (rq/judge-fibre-pull (:fibre-pull rq/default-suite) clean-pull))))

(deftest test-fibre-pull-insufficient-force-fails
  (let [r (rq/judge-fibre-pull (:fibre-pull rq/default-suite) (assoc clean-pull :applied-force-n 1.0))]
    (is (not (:pass? r)))
    (is (some #(re-find #"applied force" %) (:violations r)))))

(deftest test-fibre-pull-delamination-fails
  (let [r (rq/judge-fibre-pull (:fibre-pull rq/default-suite) (assoc clean-pull :delaminated? true))]
    (is (not (:pass? r)))
    (is (some #(re-find #"delaminated" %) (:violations r)))))

;; ── judge-one dispatch + closed vocabulary ───────────────────────────────────
(deftest test-judge-one-dispatches
  (is (:pass? (rq/judge-one :thermal-cycling (:thermal-cycling rq/default-suite) clean-thermal))))

(deftest test-judge-one-unknown-test-type-refused
  (is (thrown? clojure.lang.ExceptionInfo
               (rq/judge-one :xray-inspection {} {}))))

;; ── judge-suite: unsubmitted selected test is a FAILURE, never a silent pass ──
(deftest test-judge-suite-all-clean-passes-overall
  (let [j (rq/judge-suite rq/test-types
                          {:thermal-cycling clean-thermal :damp-heat clean-damp
                           :mechanical-shock clean-shock :fibre-pull clean-pull})]
    (is (:overall-pass? j))
    (is (every? :pass? (vals (:per-test j))))))

(deftest test-judge-suite-missing-result-fails-not-silently-passes
  (testing "G10: a selected test with no submitted result must FAIL, not silently pass"
    (let [j (rq/judge-suite #{:thermal-cycling :damp-heat} {:thermal-cycling clean-thermal})]
      (is (not (:overall-pass? j)))
      (is (not (:pass? (get-in j [:per-test :damp-heat]))))
      (is (= ["not-submitted"] (get-in j [:per-test :damp-heat :violations]))))))

(deftest test-judge-suite-one-failing-test-fails-overall
  (let [j (rq/judge-suite #{:thermal-cycling :fibre-pull}
                          {:thermal-cycling (assoc clean-thermal :cycles-completed 1)
                           :fibre-pull clean-pull})]
    (is (not (:overall-pass? j)))
    (is (:pass? (get-in j [:per-test :fibre-pull])))
    (is (not (:pass? (get-in j [:per-test :thermal-cycling]))))))

(deftest test-judge-suite-empty-selection-does-not-pass
  (is (not (:overall-pass? (rq/judge-suite #{} {})))))

;; ── qual-plan record shape ────────────────────────────────────────────────────
(deftest test-qual-plan-pass-shape
  (let [j (rq/judge-suite rq/test-types
                          {:thermal-cycling clean-thermal :damp-heat clean-damp
                           :mechanical-shock clean-shock :fibre-pull clean-pull})
        plan (rq/qual-plan "qual-001" "cpo-2km-100g" rq/test-types j)]
    (is (= :pass (:acceptance plan)))
    (is (true? (:dry-run plan)))
    (is (true? (:representative plan)))
    (is (= "cpo-2km-100g" (:device-id plan)))))

(deftest test-qual-plan-fail-shape
  (let [j (rq/judge-suite #{:thermal-cycling} {:thermal-cycling (assoc clean-thermal :cycles-completed 1)})
        plan (rq/qual-plan "qual-002" "cpo-2km-100g" #{:thermal-cycling} j)]
    (is (= :fail (:acceptance plan)))
    (is (true? (:dry-run plan)))))

;; ── honest framing ────────────────────────────────────────────────────────────
(deftest test-report-renders-and-carries-honest-framing
  (let [txt (rq/report)]
    (is (.contains txt "GR-468"))
    (is (.contains txt "representative"))
    (is (.contains txt "G8"))))

#?(:clj
   (defn -main [& _]
     (let [{:keys [fail error]} (run-tests 'noroshi.methods.test-reliability-qual)]
       (System/exit (if (zero? (+ fail error)) 0 1)))))
