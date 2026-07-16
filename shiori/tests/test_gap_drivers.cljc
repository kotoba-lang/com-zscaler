#!/usr/bin/env bb
;; shiori 栞 — tests for gap-drivers (the detractors whose harm lands on the under-served).
;; Run:  bb --classpath 20-actors 20-actors/shiori/tests/test_gap_drivers.cljc
(ns shiori.tests.test-gap-drivers
  "Tests for gap-drivers — each structural detractor's :diminishes harm WEIGHTED by the relief-gap of
  the cohort it lands on, so a detractor that harms an under-served cohort outranks one whose targets
  are already well-served. Cohort-aggregate; the ranked 取-holder is a structural pattern, never a
  person (G1)."
  (:require [shiori.methods.analyze :as a]
            [clojure.test :refer [deftest is run-tests]]))

(def ^:private nodes
  {"d1" {":organism/label" "Precarity"} "d2" {":organism/label" "Overwork"}
   "c-under" {":organism/label" "Under-served cohort"} "c-served" {":organism/label" "Served cohort"}})

;; analyze result: c-under has a big relief-gap (2.0), c-served is fully relieved (gap 0)
(def ^:private analysis {"gap" {"c-under" 2.0 "c-served" 0.0}})

(def ^:private edges
  [{":en/kind" ":diminishes" ":en/from" "d1" ":en/to" "c-under"  ":en/load" 0.5}  ; harms the under-served
   {":en/kind" ":diminishes" ":en/from" "d2" ":en/to" "c-served" ":en/load" 0.9}  ; harms the well-served
   {":en/kind" ":relieves"   ":en/from" "mit" ":en/to" "c-served" ":en/load" 0.9}]) ; not :diminishes → ignored

(deftest weights-harm-by-the-targets-relief-gap
  (let [out (a/gap-drivers analysis nodes edges)]
    (is (= "d1" (ffirst out)) "d1 harms the under-served cohort → top priority")
    (is (= 1.0 (nth (first out) 1)) "0.5 load × 2.0 gap = 1.0")))

(deftest a-detractor-of-a-well-served-cohort-drops-out
  ;; d2 has the HIGHER raw load (0.9) but its target is fully relieved (gap 0) → weighted harm 0
  (is (not (some #{"d2"} (map first (a/gap-drivers analysis nodes edges))))
      "harming an already-served cohort contributes nothing to the relief priority"))

(deftest only-diminishes-edges-count
  (let [drivers (map first (a/gap-drivers analysis nodes edges))]
    (is (not (some #{"mit"} drivers)) "the :relieves edge does not make the mitigator a detractor")))

(deftest row-is-detractor-weight-label
  (let [[d w label :as row] (first (a/gap-drivers analysis nodes edges))]
    (is (= "d1" d)) (is (= 1.0 w)) (is (= "Precarity" label))
    (is (= 3 (count row)) "[detractor gap-weighted-harm label] — a structural pattern (G1)")))

#?(:clj
   (when (= *file* (System/getProperty "babashka.file"))
     (let [{:keys [fail error]} (run-tests 'shiori.tests.test-gap-drivers)]
       (System/exit (if (zero? (+ fail error)) 0 1)))))
