#!/usr/bin/env bb
;; hokorobi 綻び — tests for the contagion-linchpins (interconnect-degree) lens.
;; Run:  bb --classpath 20-actors 20-actors/hokorobi/tests/test_contagion_linchpins.cljc
(ns hokorobi.tests.test-contagion-linchpins
  "Tests for contagion-linchpins — the count of :interconnects edges incident to a node (its
  contagion degree), surfacing the linchpin market infrastructure (CCPs, dealer banks) whose
  interconnectedness makes them systemic concentrators. A structural network reading, never a
  per-institution solvency verdict (G1)."
  (:require [hokorobi.methods.analyze :as a]
            [clojure.test :refer [deftest is run-tests]]))

(def ^:private nodes
  {"ccp" {":organism/label" "CCP"} "b1" {":organism/label" "Bank 1"} "b2" {} "b3" {} "x" {}})

(def ^:private edges
  [{":en/kind" ":interconnects" ":en/from" "ccp" ":en/to" "b1"}
   {":en/kind" ":interconnects" ":en/from" "ccp" ":en/to" "b2"}
   {":en/kind" ":interconnects" ":en/from" "ccp" ":en/to" "b3"}   ; ccp degree 3
   {":en/kind" ":interconnects" ":en/from" "b1"  ":en/to" "b2"}   ; b1, b2 each +1
   {":en/kind" ":exposes"       ":en/from" "x"   ":en/to" "b1"}]) ; not contagion → ignored

(deftest the-most-interconnected-node-is-the-linchpin
  (let [[top] (a/contagion-linchpins nodes edges)]
    (is (= "ccp" (first top)) "the CCP, interconnected to 3 banks, is the top linchpin")
    (is (= 3 (nth top 1)) "contagion degree 3")))

(deftest counts-interconnects-in-both-directions
  (let [by (into {} (map (fn [[n d _]] [n d]) (a/contagion-linchpins nodes edges)))]
    (is (= 2 (get by "b1")) "b1: inbound from ccp + outbound to b2")
    (is (= 2 (get by "b2")))
    (is (= 1 (get by "b3")))))

(deftest only-interconnects-edges-create-a-link
  ;; the :exposes edge (x → b1) is not contagion → x is not a linchpin
  (is (not (some #{"x"} (map first (a/contagion-linchpins nodes edges))))
      "a non-:interconnects edge does not create a contagion link"))

(deftest row-is-node-degree-label
  (let [[n d label :as row] (first (a/contagion-linchpins nodes edges))]
    (is (= "ccp" n)) (is (= 3 d)) (is (= "CCP" label))
    (is (= 3 (count row)) "[node contagion-degree label] — structural network reading (G1)")))

#?(:clj
   (when (= *file* (System/getProperty "babashka.file"))
     (let [{:keys [fail error]} (run-tests 'hokorobi.tests.test-contagion-linchpins)]
       (System/exit (if (zero? (+ fail error)) 0 1)))))
