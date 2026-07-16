(ns itonami.tests.test-fleet
  "itonami 営み — R10 multi-line fleet rollup tests (ADR-2606082300).
  1:1 Clojure port of tests/test_fleet.py (pytest → clojure.test)."
  (:require [clojure.test :refer [deftest is run-tests]]
            [clojure.string :as str]
            #?(:clj [clojure.java.io :as io])
            [itonami.methods.analyze :as analyze]
            [itonami.methods.fleet :as fleet]))

(def ^:private actor-dir (-> *file* io/file .getParentFile .getParentFile))
(def ^:private seed (io/file actor-dir "data" "seed-fleet-ops.kotoba.edn"))

(defn- rollup []
  (let [{:keys [stations ticks]} (analyze/load-file* seed)]
    [stations (fleet/rollup stations ticks)]))

(deftest test-two-lines-detected
  (let [{:keys [stations ticks]} (analyze/load-file* seed)
        lines (fleet/split-lines stations ticks)]
    (is (= #{":line.sarutahiko-a" ":line.giemon-a"} (set (keys lines))))
    (doseq [[_ [lst lt]] lines]
      (doseq [tk lt]
        (is (contains? lst (get tk ":tick/station")))))))

(deftest test-per-line-oee-computed
  (let [[_ f] (rollup)
        sa (get-in f ["per_line" ":line.sarutahiko-a"])
        gi (get-in f ["per_line" ":line.giemon-a"])]
    (is (and (< 0 (get sa "oee")) (< (get sa "oee") (get gi "oee"))))
    (is (> (get gi "oee") 0.8))))

(deftest test-worst-line-attended-first
  (let [[_ f] (rollup)]
    (is (= ":line.sarutahiko-a" (first (get f "ranked"))))
    (is (= ":line.sarutahiko-a" (get-in f ["plant" "worst_line"])))
    (is (>= (get-in f ["per_line" ":line.sarutahiko-a" "critical"]) 1))
    (is (= 0 (get-in f ["per_line" ":line.giemon-a" "critical"])))))

(deftest test-plant-aggregate-sums-lines
  (let [[_ f] (rollup)
        p (get f "plant")
        per (get f "per_line")]
    (is (= 2 (get p "n_lines")))
    (is (< (Math/abs (- (get p "good") (reduce + 0.0 (map #(get-in per [% "good"]) (keys per))))) 1e-9))
    (is (< (Math/abs (- (get p "kwh") (reduce + 0.0 (map #(get-in per [% "kwh"]) (keys per))))) 1e-9))))

(deftest test-no-worker-dimension
  (let [{:keys [stations ticks]} (analyze/load-file* seed)
        blob (str (pr-str stations) (pr-str ticks) (fleet/emit (fleet/rollup stations ticks)))]
    (doseq [forbidden [":worker" ":person" ":operator"]]
      (is (not (str/includes? blob forbidden))))))

(deftest test-emit-transient-only
  (let [[_ f] (rollup)
        out (fleet/emit f 5)]
    (is (and (str/includes? out ":fleet/oee") (str/includes? out ":fleet/attend-first :line.sarutahiko-a")))
    (doseq [line (str/split-lines out)]
      (when (and (str/starts-with? line "[") (str/includes? line ":fleet/"))
        (is (and (str/includes? line ":derived]") (str/includes? line ":bond/is-transient true")) line)))
    (is (not (str/includes? out ":add]")))))

(deftest test-determinism
  (let [[_ a] (rollup)
        [_ b] (rollup)]
    (is (= (fleet/emit a) (fleet/emit b)))))
