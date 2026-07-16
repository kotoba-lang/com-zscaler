(ns itonami.tests.test-plan
  "itonami 営み — R5 throughput / line-balance plan tests (ADR-2606082300).
  1:1 Clojure port of tests/test_plan.py (pytest → clojure.test)."
  (:require [clojure.test :refer [deftest is run-tests]]
            [clojure.string :as str]
            #?(:clj [clojure.java.io :as io])
            [itonami.methods.analyze :as analyze]
            [itonami.methods.plan :as P]))

(def ^:private actor-dir (-> *file* io/file .getParentFile .getParentFile))
(def ^:private ops (io/file actor-dir "data" "seed-factory-ops.kotoba.edn"))

(defn- load-all []
  (let [{:keys [stations ticks]} (analyze/load-file* ops)]
    [stations (analyze/analyze stations ticks)]))

(deftest test-capacity-is-uptime-over-takt
  (let [[stations res] (load-all)
        cap (P/station-capacity stations res)]
    (doseq [[sid c] cap]
      (is (< (Math/abs (- (get c "capacity_run") (/ (get-in res [sid "run_s"]) (get c "takt")))) 1e-9))
      (is (>= (get c "capacity_planned") (- (get c "capacity_run") 1e-9))))))

(deftest test-throughput-bottleneck-is-paint
  (let [[stations res] (load-all)
        plan (P/line-plan stations res)]
    (is (= ":st.paint" (get plan "throughput_bottleneck")))
    (is (< (Math/abs (- (get plan "units_per_window_gross") (/ 7200 1500))) 1e-9))))

(deftest test-throughput-bottleneck-differs-from-oee-bottleneck
  (let [[stations res] (load-all)
        plan (P/line-plan stations res)
        oee-bn (get-in res ["_recommend" "bottleneck" "station"])]
    (is (= ":st.frame-weld" oee-bn))
    (is (not= (get plan "throughput_bottleneck") oee-bn))))

(deftest test-daily-scaling-uses-documented-hours
  (let [[stations res] (load-all)
        p16 (P/line-plan stations res 16)
        p8 (P/line-plan stations res 8)]
    (is (< (Math/abs (- (get p16 "units_per_day_gross") (* 2 (get p8 "units_per_day_gross")))) 1e-9))))

(deftest test-relief-recovers-paint-idle-window
  (let [[stations res] (load-all)
        plan (P/line-plan stations res)
        relief (P/relief-plan stations res plan)]
    (is (< (Math/abs (- (get relief "current_units_per_window") 4.8)) 1e-9))
    (is (< (Math/abs (- (get relief "recovered_units_per_window") 7.2)) 1e-9))
    (is (< (Math/abs (- (get relief "uplift_frac") 0.5)) 1e-9))))

(deftest test-relief-lever-is-availability-within-takt-not-speedup
  (let [[stations res] (load-all)
        relief (P/relief-plan stations res)
        lever (str/lower-case (get relief "lever"))]
    (is (and (str/includes? lever "within takt") (str/includes? lever "availability")))
    (doseq [forbidden ["speed-up" "speedup" "below takt" "faster than takt" "worker"]]
      (is (not (str/includes? lever forbidden))))))

(deftest test-emit-transient-only
  (let [[stations res] (load-all)
        plan (P/line-plan stations res)
        relief (P/relief-plan stations res plan)
        out (P/emit plan relief 6)]
    (is (and (str/includes? out ":ops/throughput-bottleneck") (str/includes? out ":ops/units-per-day-good")))
    (doseq [line (str/split-lines out)]
      (when (and (str/starts-with? line "[") (str/includes? line ":ops/"))
        (is (and (str/includes? line ":derived]") (str/includes? line ":bond/is-transient true")) line)))
    (is (not (str/includes? out ":add]")))))

(deftest test-determinism
  (let [[stations res] (load-all)
        a (P/emit (P/line-plan stations res) (P/relief-plan stations res))
        [s2 r2] (load-all)
        b (P/emit (P/line-plan s2 r2) (P/relief-plan s2 r2))]
    (is (= a b))))
