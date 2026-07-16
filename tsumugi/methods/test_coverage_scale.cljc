(ns tsumugi.methods.test-coverage-scale
  "Cross-language oracle tests for tsumugi.methods.coverage-scale — the Clojure port
  of methods/coverage_scale.py.

  No test_coverage_scale.py existed, so the expected values were produced by running
  the REAL Python compute(load_data()) over the committed seed-scale-power +
  seed-banner .kotoba.edn and embedded verbatim (628 nodes / 637 ties / 80 distinct
  localities / 13 banners / 5 ents / 14 flies; 40 countries; all scales/kinds/sectors
  exercised; 576 :san nodes → world-listed numerator) — a genuine cross-language oracle."
  (:require [clojure.test :refer [deftest is testing]]
            [tsumugi.methods.coverage-scale :as cs]))

(defn- c [] (apply cs/compute (cs/load-data)))

(deftest raw-counts-match
  (let [raw (get (c) "raw")]
    (is (= 628 (get raw "nodes")))
    (is (= 637 (get raw "ties")))
    (is (= 80 (get raw "distinct_localities")))
    (is (= 13 (get raw "banners")))
    (is (= 5 (get raw "ents")))
    (is (= 14 (get raw "flies")))))

(deftest all-structural-dimensions-exercised
  (let [r (c)]
    (is (= [] (get-in r ["per_scale" "missing"])))
    (is (= [] (get-in r ["per_kind" "missing"])))
    (is (= [] (get-in r ["per_sector" "missing"])))
    ;; exercised lists follow the canonical vocab order
    (is (= [":san" ":kan" ":gaku" ":hou" ":min" ":kin"] (get-in r ["per_sector" "exercised"])))))

(deftest country-coverage
  (let [r (c)
        countries (get-in r ["per_country" "countries"])
        cov (get-in r ["coverages" "Countries (~sovereign states)"])]
    (is (= 40 (count countries)))
    (is (= "ae" (first countries)))                   ; sorted
    (is (= 40 (get cov "numerator")))
    (is (= 195 (get cov "denominator")))
    (is (< (Math/abs (- (get cov "percent") (* (/ 40.0 195) 100))) 1e-9))))

(deftest world-listed-numerator-counts-san-nodes
  (let [cov (get-in (c) ["coverages" "World listed companies (approx)"])]
    (is (= 576 (get cov "numerator")))                ; every :san node
    (is (= 50000 (get cov "denominator")))))

(deftest coverages-have-all-six-denominators
  (let [covs (get (c) "coverages")]
    (is (= 6 (count covs)))
    (is (every? #(contains? % "numerator") (vals covs)))
    (is (every? #(contains? % "percent") (vals covs)))))

(deftest render-is-honest-framing
  (let [md (cs/render (c))]
    (is (clojure.string/includes? md "real-world coverage is ~0 BY DESIGN"))
    (is (clojure.string/includes? md "Power nodes: 628"))
    (is (clojure.string/includes? md "non-adjudicating"))))
