(ns itonami.methods.test-inspect
  "itonami 営み — R2 vision-inspection hand-off tests (ADR-2606082300).
  1:1 Clojure port of tests/test_inspect.py (every assertion)."
  (:require [clojure.test :refer [deftest is run-tests]]
            [clojure.string :as str]
            [clojure.java.io :as io]
            [itonami.methods.analyze :as analyze]
            [itonami.methods.inspect :as vis]))

(def actor-dir (-> *file* io/file .getParentFile .getParentFile))
(def ops (io/file actor-dir "data" "seed-factory-ops.kotoba.edn"))
(def det-file (io/file actor-dir "data" "seed-vision-detections.kotoba.edn"))

(defn- load* []
  (let [{:keys [stations ticks]} (analyze/load-file* ops)
        res (analyze/analyze stations ticks)
        det (vis/load-detections (slurp det-file))]
    [stations res det]))

(deftest test-detections-load-and-carry-no-person
  ;; G2 / manako: detections are object-only — no person/face/biometric/worker field.
  (let [[_ _ det] (load*)]
    (is (>= (count det) 9))
    (doseq [d det]
      (let [keys-str (str/join " " (keys d))]
        (doseq [forbidden [":worker" ":person" ":face" ":biometric" ":operator"]]
          (is (not (str/includes? keys-str forbidden)) (str "detection leaked " forbidden ": " d))))
      (is (contains? #{":pass" ":rework" ":scrap"} (get d ":detect/verdict"))))))

(deftest test-request-targets-highest-scrap-station
  (let [[stations res det] (load*)
        req (vis/inspection-request stations res det)]
    (is (= ":st.cab-weld" (get req "station")))   ; the R0 quality_target
    (is (= "actor:manako" (get req "routed_to")))
    ;; elevated scrap → 100% sampling
    (is (= 1.0 (get req "sample_rate")))))

(deftest test-request-constraints-enforce-manako-invariants
  (let [[stations res det] (load*)
        req (vis/inspection-request stations res det)
        joined (str/lower-case (str/join " " (get req "constraints")))]
    (is (or (str/includes? joined "no biometric") (str/includes? joined "no person")))
    (is (str/includes? joined "on-device"))
    (is (str/includes? joined "no auto-reject"))))   ; G1 — advisory, never actuates

(deftest test-reconcile-pareto-top-defect-is-porosity
  (let [[_ res det] (load*)
        rec (vis/reconcile det res)
        cw (get rec ":st.cab-weld")]
    ;; 3 porosity vs 1 spatter vs 1 misalignment → porosity dominates
    (is (= ":weld-porosity" (get cw "top_defect")))
    (is (= 3 (get (into {} (get cw "defect_pareto")) ":weld-porosity")))))

(deftest test-vision-scrap-cross-checks-scan-cycle
  ;; The detector's scrap count must reconcile with the scan-cycle scrap count (2).
  (let [[_ res det] (load*)
        rec (vis/reconcile det res)
        cw (get rec ":st.cab-weld")]
    (is (== (get cw "scancycle_scrap") 2))
    (is (= 2 (get cw "scrap")))
    (is (true? (get cw "scrap_agrees")))))

(deftest test-emit-transient-only
  (let [[stations res det] (load*)
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
  (let [[stations res det] (load*)
        a (vis/emit (vis/inspection-request stations res det) (vis/reconcile det res) 1)
        [s2 r2 d2] (load*)
        b (vis/emit (vis/inspection-request s2 r2 d2) (vis/reconcile d2 r2) 1)]
    (is (= a b))))

(defn -main [& _]
  (let [{:keys [fail error]} (run-tests 'itonami.methods.test-inspect)]
    (System/exit (if (pos? (+ fail error)) 1 0))))
