#!/usr/bin/env bb
;; hinagata 雛形 — tests for statute-reach (which statute the commons most depends on).
;; Run:  bb --classpath 20-actors 20-actors/hinagata/methods/test_statute_reach.cljc
(ns hinagata.methods.test-statute-reach
  "Tests for statute-reach — the inverse of statutes-grounding-template: per statute, the count of
  distinct clauses/templates that cite it (:cites-statute / :mandated-by), the change-impact map. A
  disclosed structural fact (citation counts), never a verdict (G1/G3)."
  (:require [hinagata.methods.query :as q]
            [clojure.test :refer [deftest is run-tests]]))

(def ^:private nodes
  {"st-gdpr" {":lt/kind" ":statute" ":lt/label" "GDPR Art.28"}
   "st-civ"  {":lt/kind" ":statute" ":lt/label" "民法627条"}
   "cl-dpa"  {":lt/kind" ":clause"} "cl-emp" {":lt/kind" ":clause"} "tmpl-x" {":lt/kind" ":template"}})

(def ^:private edges
  [{":en/kind" ":cites-statute" ":en/from" "cl-dpa"  ":en/to" "st-gdpr"}
   {":en/kind" ":mandated-by"   ":en/from" "cl-emp"  ":en/to" "st-gdpr"}   ; GDPR cited by 2
   {":en/kind" ":cites-statute" ":en/from" "tmpl-x"  ":en/to" "st-civ"}    ; 民法 cited by 1
   {":en/kind" ":has-clause"    ":en/from" "tmpl-x"  ":en/to" "cl-dpa"}])  ; not a citation → ignored

(deftest ranks-statutes-by-citation-count
  (let [out (q/statute-reach nodes edges)]
    (is (= "st-gdpr" (ffirst out)) "GDPR is cited by 2 clauses → most-depended-on")
    (is (= 2 (nth (first out) 1)))
    (is (= "GDPR Art.28" (nth (first out) 2)) "carries the statute label")))

(deftest counts-both-cite-kinds
  (let [by (into {} (map (fn [[s cnt _]] [s cnt]) (q/statute-reach nodes edges)))]
    (is (= 2 (get by "st-gdpr")) ":cites-statute AND :mandated-by both count")
    (is (= 1 (get by "st-civ")))))

(deftest counts-distinct-citers-only
  ;; two edges from the same clause to the same statute count once
  (let [dup [{":en/kind" ":cites-statute" ":en/from" "cl-dpa" ":en/to" "st-gdpr"}
             {":en/kind" ":mandated-by"   ":en/from" "cl-dpa" ":en/to" "st-gdpr"}]
        by (into {} (map (fn [[s cnt _]] [s cnt]) (q/statute-reach nodes dup)))]
    (is (= 1 (get by "st-gdpr")) "the same citer is counted once")))

(deftest non-citation-edges-do-not-count
  (let [by (into {} (map (fn [[s cnt _]] [s cnt]) (q/statute-reach nodes edges)))]
    (is (= 1 (get by "st-civ")) "the :has-clause edge contributes no citation")))

#?(:clj
   (when (= *file* (System/getProperty "babashka.file"))
     (let [{:keys [fail error]} (run-tests 'hinagata.methods.test-statute-reach)]
       (System/exit (if (zero? (+ fail error)) 0 1)))))
