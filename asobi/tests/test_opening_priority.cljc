#!/usr/bin/env bb
;; asobi 遊び — tests for the opening-priority (enclosure − openness balance) lens.
;; Run:  bb --classpath 20-actors 20-actors/asobi/tests/test_opening_priority.cljc
(ns asobi.tests.test-opening-priority
  "Tests for opening-priority — per-node OPENING priority by the enclosure↔openness BALANCE
  (enclosure − openness), so a work that is enclosed in one venue but open in another is correctly
  de-prioritized vs one that is purely gated. An access map, never an engagement ranking (G1)."
  (:require [asobi.methods.analyze :as a]
            [clojure.test :refer [deftest is run-tests]]))

(def ^:private nodes
  {"w1" {":organism/label" "Streaming series"}
   "w2" {":organism/label" "Olympics"}
   "w3" {":organism/label" "Shakespeare"}})

;; build an analyze-shaped result directly (the integrals the lens reads)
(def ^:private analysis
  {"openness"  {"w1" 0.0 "w2" 0.5 "w3" 1.5}
   "enclosure" {"w1" 2.0 "w2" 0.6 "w3" 0.0}})

(deftest ranks-by-net-enclosure
  (let [out (a/opening-priority analysis nodes)]
    (is (= "w1" (ffirst out)) "purely-enclosed streaming series → top opening priority")
    (is (= 2.0 (nth (first out) 1)) "net = 2.0 − 0.0")))

(deftest a-partly-open-work-is-de-prioritised-below-its-raw-enclosure
  ;; w2 has enclosure 0.6 but openness 0.5 → net only 0.1; despite real enclosure it is nearly open
  (let [by (into {} (map (fn [[id net _ _ _]] [id net]) (a/opening-priority analysis nodes)))]
    (is (< (Math/abs (- 0.1 (get by "w2"))) 1e-9) "net = 0.6 − 0.5 = 0.1, far below its raw enclosure")))

(deftest a-net-open-work-has-a-negative-priority-the-exemplar
  (let [by (into {} (map (fn [[id net _ _ _]] [id net]) (a/opening-priority analysis nodes)))]
    (is (= -1.5 (get by "w3")) "Shakespeare: open 1.5, enclosure 0 → net −1.5 (an accessible exemplar)")
    (is (= "w3" (first (last (a/opening-priority analysis nodes)))) "and it ranks last")))

(deftest row-is-node-net-enclosure-openness-label
  (let [[id net e o label :as row] (first (a/opening-priority analysis nodes))]
    (is (= "w1" id)) (is (= 2.0 e)) (is (= 0.0 o)) (is (= "Streaming series" label))
    (is (= 5 (count row)) "[node net enclosure openness label]")))

#?(:clj
   (when (= *file* (System/getProperty "babashka.file"))
     (let [{:keys [fail error]} (run-tests 'asobi.tests.test-opening-priority)]
       (System/exit (if (zero? (+ fail error)) 0 1)))))
