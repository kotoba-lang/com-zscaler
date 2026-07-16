#!/usr/bin/env bb
;; kaname 要 — tests for the leverage-distribution concentration meta-metric.
;; Run:  bb -cp "20-actors:20-actors/kotodama/src" 20-actors/kaname/tests/test_leverage_concentration.cljc
(ns kaname.tests.test-leverage-concentration
  "Tests for leverage-concentration — the meta-metric on the R1 leverage distribution: is the SoS's
  leverage concentrated in one 要 (a single point of leverage) or distributed across comparable
  positions (no single chokepoint)? A structural read OF the leverage map (an aggregate distribution
  property, never a per-entity score / target-list)."
  (:require [kaname.methods.centrality :as c]
            [clojure.test :refer [deftest is run-tests]]))

(defn- close? [x y] (< (Math/abs (- (double x) (double y))) 1e-9))

(deftest one-dominant-yo-is-concentrated
  (let [r (c/leverage-concentration {:leverage-r1 {"a" 10.0 "b" 1.0 "c" 1.0}})]
    (is (= "a" (:top-id r)) "the dominant node is the top leverage position")
    (is (close? (/ 10.0 12.0) (:top-share r)) "its share of total leverage")
    (is (< (:effective-points r) 2.0) "≈1.4 effective leverage points — a single point of leverage")
    (is (:concentrated? r))))

(deftest evenly-distributed-leverage-is-not-concentrated
  (let [r (c/leverage-concentration {:leverage-r1 {"a" 1.0 "b" 1.0 "c" 1.0 "d" 1.0}})]
    (is (close? 4.0 (:effective-points r)) "4 equal points → 4 effective points")
    (is (close? 0.25 (:hhi r)))
    (is (not (:concentrated? r)) "leverage is diffuse — no single chokepoint")))

(deftest effective-points-is-the-inverse-of-hhi
  (let [r (c/leverage-concentration {:leverage-r1 {"a" 3.0 "b" 1.0}})]
    (is (close? (/ 1.0 (:hhi r)) (:effective-points r)) "effective-points = 1/HHI (inverse Simpson)")))

(deftest empty-leverage-yields-nils
  (let [r (c/leverage-concentration {:leverage-r1 {}})]
    (is (nil? (:effective-points r)))
    (is (not (:concentrated? r)))))

#?(:clj
   (when (= *file* (System/getProperty "babashka.file"))
     (let [{:keys [fail error]} (run-tests 'kaname.tests.test-leverage-concentration)]
       (System/exit (if (zero? (+ fail error)) 0 1)))))
