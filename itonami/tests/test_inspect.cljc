(ns itonami.tests.test-inspect
  "itonami 営み — R2 vision-inspection hand-off tests (ADR-2606082300).
  1:1 Clojure port of tests/test_inspect.py (pytest → clojure.test)."
  (:require [clojure.test :refer [deftest is run-tests]]
            [clojure.string :as str]
            #?(:clj [clojure.java.io :as io])
            [itonami.methods.analyze :as analyze]
            [itonami.methods.inspect :as vis]))

(def ^:private actor-dir (-> *file* io/file .getParentFile .getParentFile))
(def ^:private ops (io/file actor-dir "data" "seed-factory-ops.kotoba.edn"))
(def ^:private det (io/file actor-dir "data" "seed-vision-detections.kotoba.edn"))

(defn- load-all []
  (let [{:keys [stations ticks]} (analyze/load-file* ops)
        res (analyze/analyze stations ticks)
        detections (vis/load-detections (slurp det))]
    [stations res detections]))

(deftest test-detections-load-and-carry-no-person
  (let [[_ _ det] (load-all)]
    (is (>= (count det) 9))
    (doseq [d det]
      (let [keys-str (str/join " " (keys d))]
        (doseq [forbidden [":worker" ":person" ":face" ":biometric" ":operator"]]
          (is (not (str/includes? keys-str forbidden)) (str "detection leaked " forbidden ": " d))))
      (is (contains? #{":pass" ":rework" ":scrap"} (get d ":detect/verdict"))))))

(deftest test-request-targets-highest-scrap-station
  (let [[stations res det] (load-all)
        req (vis/inspection-request stations res det)]
    (is (= ":st.cab-weld" (get req "station")))
    (is (= "actor:manako" (get req "routed_to")))
    (is (= 1.0 (get req "sample_rate")))))

(deftest test-request-constraints-enforce-manako-invariants
  (let [[stations res det] (load-all)
        req (vis/inspection-request stations res det)
        joined (str/lower-case (str/join " " (get req "constraints")))]
    (is (or (str/includes? joined "no biometric") (str/includes? joined "no person")))
    (is (str/includes? joined "on-device"))
    (is (str/includes? joined "no auto-reject"))))

(deftest test-reconcile-pareto-top-defect-is-porosity
  (let [[_ res det] (load-all)
        rec (vis/reconcile det res)
        cw (get rec ":st.cab-weld")]
    (is (= ":weld-porosity" (get cw "top_defect")))
    (is (= 3 (get (into {} (get cw "defect_pareto")) ":weld-porosity")))))

(deftest test-vision-scrap-cross-checks-scan-cycle
  (let [[_ res det] (load-all)
        rec (vis/reconcile det res)
        cw (get rec ":st.cab-weld")]
    ;; scancycle_scrap is a float aggregate (Python `_num`); == compares numerically
    (is (== 2 (get cw "scancycle_scrap")))
    (is (= 2 (get cw "scrap")))
    (is (true? (get cw "scrap_agrees")))))

(deftest test-emit-transient-only
  (let [[stations res det] (load-all)
        req (vis/inspection-request stations res det)
        rec (vis/reconcile det res)
        out (vis/emit req rec 5)]
    (is (str/includes? out ":ops/inspect-routed-to :actor.manako"))
    (is (str/includes? out ":quality/top-defect"))
    (doseq [line (str/split-lines out)]
      (when (and (str/starts-with? line "[") (or (str/includes? line ":ops/") (str/includes? line ":quality/")))
        (is (and (str/includes? line ":derived]") (str/includes? line ":bond/is-transient true")) line)))
    (is (not (str/includes? out ":add]")))))

(deftest test-determinism
  (let [[stations res det] (load-all)
        a (vis/emit (vis/inspection-request stations res det) (vis/reconcile det res) 1)
        [s2 r2 d2] (load-all)
        b (vis/emit (vis/inspection-request s2 r2 d2) (vis/reconcile d2 r2) 1)]
    (is (= a b))))
