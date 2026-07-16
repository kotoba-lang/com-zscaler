#!/usr/bin/env bb
;; tsugite 継ぎ手 — tests for the double-jeopardy (continuity × fragility) intersection lens.
;; Run:  bb --classpath 20-actors 20-actors/tsugite/tests/test_double_jeopardy.cljc
(ns tsugite.tests.test-double-jeopardy
  "Tests for double-jeopardy — collectives bearing BOTH displacement pressure (continuity) AND a
  fragile people↔language coupling (fragility), the product surfacing where the two strands compound.
  Lists ONLY collectives with both > 0. Aggregate / collective-scale, no person-tracking (G1)."
  (:require [tsugite.methods.analyze :as a]
            [clojure.test :refer [deftest is run-tests]]))

(def ^:private nodes
  {"a" {":organism/label" "Lang A"} "b" {":organism/label" "Lang B"} "c" {":organism/label" "Lang C"}})

;; an analyze-shaped result: a + b bear both pressures; c is heavily displaced but linguistically stable
(def ^:private analysis
  {"continuity" {"a" 2.0 "b" 1.0 "c" 3.0}
   "fragility"  {"a" 0.5 "b" 0.8 "c" 0.0}})

(deftest surfaces-only-collectives-affected-on-both-strands
  (let [out (a/double-jeopardy analysis nodes)]
    (is (= 2 (count out)) "only a + b have BOTH continuity and fragility > 0")
    (is (= "a" (ffirst out)) "a (2.0 × 0.5 = 1.0) tops the jeopardy")))

(deftest excludes-the-most-displaced-collective-when-its-language-is-stable
  ;; c has the HIGHEST continuity-pressure (3.0) but fragility 0 → NOT double jeopardy
  (is (not (some #{"c"} (map first (a/double-jeopardy analysis nodes))))
      "the most-pressured collective without language fragility is not double-jeopardy"))

(deftest product-of-both-dimensions
  (let [by (into {} (map (fn [[id j _ _ _]] [id j]) (a/double-jeopardy analysis nodes)))]
    (is (= 1.0 (get by "a")) "2.0 × 0.5")
    (is (< (Math/abs (- 0.8 (get by "b"))) 1e-9) "1.0 × 0.8")))

(deftest row-is-collective-jeopardy-continuity-fragility-label
  (let [[id j c f label :as row] (first (a/double-jeopardy analysis nodes))]
    (is (= "a" id)) (is (= 1.0 j)) (is (= 2.0 c)) (is (= 0.5 f)) (is (= "Lang A" label))
    (is (= 5 (count row)) "[collective jeopardy continuity fragility label] — collective-scale (G1)")))

#?(:clj
   (when (= *file* (System/getProperty "babashka.file"))
     (let [{:keys [fail error]} (run-tests 'tsugite.tests.test-double-jeopardy)]
       (System/exit (if (zero? (+ fail error)) 0 1)))))
