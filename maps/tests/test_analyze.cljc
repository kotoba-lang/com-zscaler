(ns maps.tests.test-analyze
  "maps — analyze + datom-emit + coverage-report tests (ADR-2606064500).
  1:1 Clojure port of `methods/test_methods.py` (TestAnalyze) +
  datom-emit and coverage-report smoke tests. stdlib; network-free."
  (:require [clojure.test :refer [deftest is run-tests]]
            [clojure.java.io :as io]
            [maps.methods.analyze         :as analyze]
            [maps.methods.datom-emit      :as datom-emit]
            [maps.methods.coverage-report :as coverage-report]))

(def seed-path
  (-> *file* io/file .getParentFile .getParentFile
      (io/file "data" "seed-spatial-graph.kotoba.edn") str))

(deftest test-seed-classifies
  (let [rows (analyze/load-edn seed-path)
        [features rels aliases] (analyze/classify rows)]
    (is (>= (count features) 10) (str "expected ≥10 features, got " (count features)))
    (is (>= (count rels) 1) "expected ≥1 topology rel")
    (is (>= (count aliases) 1) "expected ≥1 geo-alias")))

(deftest test-every-feature-has-sourcing
  ;; G3 — sourcing honesty is mandatory on every feature in the seed
  (let [[features _ _] (analyze/classify (analyze/load-edn seed-path))]
    (doseq [[fid f] features]
      (is (contains? f ":feature/sourcing") (str "no :feature/sourcing on " fid)))))

(deftest test-labels-present
  ;; The seed must contain at least :building and :station (Tokyo Station anchor)
  (let [[features _ _] (analyze/classify (analyze/load-edn seed-path))
        a (analyze/analyze features [] {})]
    (is (contains? (:label-count a) ":building"))
    (is (contains? (:label-count a) ":station"))))

(deftest test-coverage-fraction-is-honest-and-tiny
  ;; res-6 cell count: 2 + 120 × 7^6 = 14 117 882 exactly
  (is (= 14117882 (analyze/h3-cells-at-res 6)))
  (let [[features _ _] (analyze/classify (analyze/load-edn seed-path))
        a (analyze/analyze features [] {})]
    (is (pos? (count (:cells-r6 a))) "expected >0 res-6 cells")
    (is (< (/ (count (:cells-r6 a)) 14117882.0) 1e-3)
        "coverage fraction must be tiny (honest seed)")))

(deftest test-bbox-and-anchor
  (let [[features _ _] (analyze/classify (analyze/load-edn seed-path))
        a (analyze/analyze features [] {})]
    (is (some? (:bbox a)) "bbox must be set")
    (is (some? (:densest a)) "densest hot spot must be set")
    (let [[[la lo] _] (:densest a)]
      (is (< (Math/abs (- la 35.68)) 0.1) "anchor lat must be ~35.68 (Tokyo Station)")
      (is (< (Math/abs (- lo 139.76)) 0.1) "anchor lon must be ~139.76"))))

(deftest test-render-report
  (let [[features _ _] (analyze/classify (analyze/load-edn seed-path))
        a (analyze/analyze features [] {})
        rep (analyze/render-report features a)]
    (is (clojure.string/includes? rep "coverage report"))
    (is (clojure.string/includes? rep "res-6"))))

(deftest test-render-datoms
  (let [[features _ _] (analyze/classify (analyze/load-edn seed-path))
        a (analyze/analyze features [] {})
        d (analyze/render-datoms a)]
    (is (clojure.string/includes? d ":coverage/feature-count"))
    (is (clojure.string/includes? d ":coverage/derived true"))))

(deftest test-datom-emit-structure
  (let [[features rels aliases] (analyze/classify (analyze/load-edn seed-path))
        {:keys [ground derived]} (datom-emit/emit-datoms features rels aliases)]
    (is (seq ground) "expected ground datoms")
    (is (seq derived) "expected derived datoms")
    ;; Every ground datom string starts with [
    (doseq [d ground]
      (is (clojure.string/starts-with? d "[") (str "bad datom: " d)))
    ;; No ground datom claims :bond/is-transient
    (doseq [d ground]
      (is (not (clojure.string/includes? d "is-transient")) (str "ground datom claims transient: " d)))
    ;; All derived datoms declare :bond/is-transient true
    (doseq [d derived]
      (is (clojure.string/includes? d "is-transient") (str "derived datom missing transient: " d)))))

(deftest test-coverage-report-has-gaps-section
  (let [[features rels aliases] (analyze/classify (analyze/load-edn seed-path))
        rep (coverage-report/report features rels aliases)]
    (is (clojure.string/includes? rep "gap") "expected gap mention in coverage report")
    (is (clojure.string/includes? rep "G3") "expected G3 honesty note")
    (is (clojure.string/includes? rep "G9") "expected G9 feature-not-person note")))

#?(:clj
   (when (= *ns* (find-ns 'maps.tests.test-analyze))
     (run-tests)))
