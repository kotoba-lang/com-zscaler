(ns itonami.methods.test-optimize
  "itonami 営み — R1 optimization-proposal tests (ADR-2606082300).
  1:1 Clojure port of tests/test_optimize.py (every assertion)."
  (:require [clojure.test :refer [deftest is run-tests]]
            [clojure.string :as str]
            [clojure.java.io :as io]
            [itonami.methods.analyze :as analyze]
            [itonami.methods.optimize :as optimize]))

(def actor-dir (-> *file* io/file .getParentFile .getParentFile))
(def seed (io/file actor-dir "data" "seed-factory-ops.kotoba.edn"))

(defn- load* []
  (let [{:keys [stations ticks]} (analyze/load-file* seed)]
    [stations ticks (analyze/analyze stations ticks)]))

(deftest test-idle-powerdown-recovers-only-a-fraction-of-idle-energy
  (let [[_ ticks res] (load*)
        e (optimize/idle-powerdown res)
        ;; total idle energy across the line, recomputed independently from ticks
        total-idle (reduce + 0.0 (map #(double (get % ":tick/kwh"))
                                      (filter #(not= (get % ":tick/state") analyze/RUN) ticks)))]
    (is (< (Math/abs (- (get e "recoverable_kwh") (* total-idle optimize/RECOVERABLE-IDLE-FRACTION))) 1e-9))
    (is (and (< 0 (get e "recoverable_kwh")) (< (get e "recoverable_kwh") (+ total-idle 1e-9))))
    (is (and (< 0.0 (get e "energy_reduction_frac")) (< (get e "energy_reduction_frac") 1.0)))))

(deftest test-energy-reduction-is-honest-not-inflated
  ;; G5: our synthetic line has modest idle waste — the % must NOT be silently set to 10%.
  (let [[_ _ res] (load*)
        e (optimize/idle-powerdown res)]
    ;; paint(45) + frame-weld down(8) = 53 idle kWh ; line = 825 kWh ; ×0.7 = 37.1 → ~4.5%
    (is (and (< 0.03 (get e "energy_reduction_frac")) (< (get e "energy_reduction_frac") 0.06))
        (str (get e "energy_reduction_frac")))
    (is (= ":st.paint" (get e "top_station")))))

(deftest test-bottleneck-relief-lifts-line-to-second-worst
  (let [[_ _ res] (load*)
        b (optimize/bottleneck-relief res)
        sids (filter #(not (str/starts-with? % "_")) (keys res))
        oees (sort (map #(get-in res [% "oee"]) sids))]
    (is (= ":st.frame-weld" (get b "bottleneck")))
    (is (< (Math/abs (- (get b "current_line_oee") (nth oees 0))) 1e-9))
    (is (< (Math/abs (- (get b "target_line_oee") (nth oees 1))) 1e-9))
    (is (> (get b "oee_uplift_frac") 0))))

(deftest test-relief-levers-never-propose-sub-takt-speedup
  ;; G2: relief is availability/performance recovery within takt, never speed-up.
  (let [[_ _ res] (load*)
        b (optimize/bottleneck-relief res)
        text (str/lower-case (str/join " " (get b "relief_levers")))]
    (is (str/includes? text "within takt"))
    (doseq [forbidden ["speed-up" "speedup" "faster than takt" "below takt" "worker"]]
      (is (not (str/includes? text forbidden))))))

(deftest test-emit-proposals-all-transient
  (let [[stations ticks res] (load*)
        opt (optimize/optimize stations ticks res)
        out (optimize/emit-proposals opt 4)]
    (is (str/includes? out ":ops/proposal-energy-reduction-frac"))
    (is (str/includes? out ":ops/proposal-bottleneck"))
    (doseq [line (str/split-lines out)]
      (when (and (str/starts-with? line "[") (str/includes? line ":ops/proposal"))
        (is (and (str/includes? line ":derived]") (str/includes? line ":bond/is-transient true")) line)))
    ;; proposals are NEVER emitted as durable :add facts
    (is (not (str/includes? out ":add]")))))

(deftest test-determinism
  (let [[stations ticks res] (load*)
        a (optimize/emit-proposals (optimize/optimize stations ticks res) 1)
        [s2 t2 r2] (load*)
        b (optimize/emit-proposals (optimize/optimize s2 t2 r2) 1)]
    (is (= a b))))

(defn -main [& _]
  (let [{:keys [fail error]} (run-tests 'itonami.methods.test-optimize)]
    (System/exit (if (pos? (+ fail error)) 1 0))))
