#!/usr/bin/env bb
;; inochi 命 — tests for the systemic-pressures (per-pressure reach) lens.
;; Run:  bb --classpath 20-actors 20-actors/inochi/tests/test_systemic_pressures.cljc
(ns inochi.tests.test-systemic-pressures
  "Tests for systemic-pressures — ranking each ecological PRESSURE by how many DISTINCT bearers it
  threatens (breadth), so a threat spanning many ecosystems outranks one concentrated on a few. The
  ranked 取-holder is the PRESSURE (a restoration map, never a bearer target-list, G1); reach + load
  are edge-primary, counted on read (G2)."
  (:require [inochi.methods.analyze :as a]
            [clojure.test :refer [deftest is run-tests]]))

(def ^:private nodes
  {"p1" {":organism/kind" ":pressure" ":organism/label" "Deforestation"}
   "p2" {":organism/kind" ":pressure" ":organism/label" "Pollution"}
   "b1" {} "b2" {} "b3" {}})

(def ^:private edges
  [{":en/kind" ":pressures" ":en/from" "p1" ":en/to" "b1" ":en/grasping-load" 0.5}
   {":en/kind" ":pressures" ":en/from" "p1" ":en/to" "b2" ":en/grasping-load" 0.5}
   {":en/kind" ":pressures" ":en/from" "p1" ":en/to" "b3" ":en/grasping-load" 0.5}  ; p1 reach 3
   {":en/kind" ":pressures" ":en/from" "p2" ":en/to" "b1" ":en/grasping-load" 0.9}  ; p2 reach 1, high load
   {":en/kind" ":habitat-of" ":en/from" "p2" ":en/to" "b2"}])                       ; not a pressure → ignored

(deftest ranks-pressures-by-distinct-bearer-reach
  (let [[top second] (a/systemic-pressures nodes edges)]
    (is (= "p1" (first top)) "p1 threatens 3 distinct bearers → most systemic")
    (is (= 3 (nth top 1)) "reach = 3 distinct bearers")
    (is (= "p2" (first second)) "p2 threatens only 1")))

(deftest breadth-outranks-per-bearer-load
  ;; p2 has the higher per-bearer load (0.9) but reaches only 1 bearer; p1 spreads across 3
  (is (= "p1" (ffirst (a/systemic-pressures nodes edges)))
      "a threat spanning many bearers outranks one concentrated on a few"))

(deftest only-pressure-edges-counted
  ;; the :habitat-of edge (p2→b2) is not a pressure → p2's reach stays 1, not 2
  (let [by (into {} (map (fn [[p reach _ _]] [p reach]) (a/systemic-pressures nodes edges)))]
    (is (= 1 (get by "p2")) "the non-:pressures edge is ignored")))

(deftest reports-label-and-total-load-pressure-row-only-g1
  (let [[_ reach load label :as row] (first (a/systemic-pressures nodes edges))]
    (is (= "Deforestation" label) "the pressure's label")
    (is (= 3 reach))
    (is (= 1.5 load) "total grasping-load = 0.5+0.5+0.5")
    (is (= 4 (count row)) "[pressure reach load label] — a PRESSURE ranking, no bearer coordinates (G1)")))

#?(:clj
   (when (= *file* (System/getProperty "babashka.file"))
     (let [{:keys [fail error]} (run-tests 'inochi.tests.test-systemic-pressures)]
       (System/exit (if (zero? (+ fail error)) 0 1)))))
