#!/usr/bin/env bb
;; hoshimori 星守 — tests for the stewardship-gap (congested-but-unaddressed shells).
;; Run:  bb --classpath 20-actors 20-actors/hoshimori/tests/test_stewardship_gap.cljc
(ns hoshimori.tests.test-stewardship-gap
  "Tests for stewardship-gap — orbital shells that are congested yet have NO remediation /
  deconfliction / deorbit edge (where stewardship is MISSING, not merely where congestion is high).
  Shell-level aggregate (no ephemeris, G1); edge-primary (G2); routed to stewardship."
  (:require [hoshimori.methods.analyze :as a]
            [clojure.test :refer [deftest is run-tests]]))

(def ^:private nodes
  {"s1" {":organism/label" "GEO"} "s2" {":organism/label" "LEO-low"} "op1" {} "steward1" {}})

(def ^:private edges
  [{":en/kind" ":congests" ":en/from" "op1" ":en/to" "s1" ":en/orbit-load" 1.0}   ; s1 congested, unstewarded
   {":en/kind" ":congests" ":en/from" "op1" ":en/to" "s2" ":en/orbit-load" 2.0}   ; s2 MORE congested...
   {":en/kind" ":remediates" ":en/from" "steward1" ":en/to" "s2"}])               ; ...but stewarded

(deftest surfaces-only-congested-but-unstewarded-shells
  (let [out (a/stewardship-gap nodes edges)]
    (is (= 1 (count out)) "only s1 — s2 is congested but stewarded")
    (is (= "s1" (ffirst out)))))

(deftest the-gap-is-not-the-most-congested-shell
  ;; s2 carries MORE congestion (2.0) but is stewarded → addressed; s1 (1.0, unstewarded) is the gap
  (is (= ["s1"] (mapv first (a/stewardship-gap nodes edges)))
      "the unaddressed shell, not the most-congested one, is the priority"))

(deftest a-shell-with-no-congestion-is-not-a-gap
  (is (= [] (a/stewardship-gap {"s1" {}} [])) "no hazard edges → no gap")
  (is (= [] (a/stewardship-gap nodes
                              [{":en/kind" ":congests" ":en/from" "op1" ":en/to" "s2" ":en/orbit-load" 2.0}
                               {":en/kind" ":deorbits" ":en/from" "steward1" ":en/to" "s2"}]))
      "the only congested shell is stewarded → empty gap"))

(deftest row-is-shell-load-label-aggregate-g1
  (let [[shell load label :as row] (first (a/stewardship-gap nodes edges))]
    (is (= "s1" shell)) (is (= 1.0 load)) (is (= "GEO" label))
    (is (= 3 (count row)) "[shell load label] — shell-level, no per-object ephemeris (G1)")))

#?(:clj
   (when (= *file* (System/getProperty "babashka.file"))
     (let [{:keys [fail error]} (run-tests 'hoshimori.tests.test-stewardship-gap)]
       (System/exit (if (zero? (+ fail error)) 0 1)))))
