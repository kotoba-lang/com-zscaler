#!/usr/bin/env bb
;; tanemaki 種蒔き — tests for score-contributions (the DD fit decomposition).
;; Run:  bb --classpath 20-actors 20-actors/tanemaki/methods/test_score_contributions.cljc
(ns tanemaki.methods.test-score-contributions
  "Tests for score-contributions — decomposing a candidate's dd-fit into per-criterion contributions
  (weight × min(1, evidence)), so a voter sees WHICH criteria drove the score. Advisory transparency
  over disclosed weights + evidence (G4), never a verdict (G3) or the decision (G1)."
  (:require [tanemaki.methods.analyze :as a]
            [clojure.test :refer [deftest is run-tests]]))

;; a small rubric: C1 mission-fit (weight 0.5), C2 openness (0.3), C3 capacity (0.2)
(def ^:private crit
  {"c1" {":criterion/weight" 0.5 ":criterion/code" "C1" ":fs/label" "mission-fit"}
   "c2" {":criterion/weight" 0.3 ":criterion/code" "C2" ":fs/label" "openness"}
   "c3" {":criterion/weight" 0.2 ":criterion/code" "C3" ":fs/label" "capacity"}})

;; evidence: c1 fully met (1.0), c2 fully met, c3 unmet (0)
(def ^:private per {"c1" 1.0 "c2" 1.0 "c3" 0.0})

(deftest contributions-are-weight-times-evidence
  (let [by (into {} (map (fn [[c contrib _ _ _]] [c contrib]) (a/score-contributions crit per)))]
    (is (= 0.5 (get by "c1")) "0.5 weight × 1.0 met")
    (is (= 0.3 (get by "c2")) "0.3 × 1.0")
    (is (= 0.0 (get by "c3")) "0.2 × 0.0 — unmet contributes nothing")))

(deftest ranked-by-contribution-the-driver-first
  (is (= ["c1" "c2" "c3"] (mapv first (a/score-contributions crit per)))
      "mission-fit drove the score, capacity (unmet) last"))

(deftest contributions-sum-to-the-dd-fit
  ;; the fit IS the sum of contributions: 0.5 + 0.3 + 0.0 = 0.8
  (is (< (Math/abs (- 0.8 (reduce + (map second (a/score-contributions crit per))))) 1e-9)))

(deftest evidence-is-capped-at-one-per-criterion
  ;; over-evidenced criterion (per 2.0) still contributes only weight × 1.0, never weight × 2.0
  (let [by (into {} (map (fn [[c contrib _ _ _]] [c contrib]) (a/score-contributions crit {"c1" 2.0})))]
    (is (= 0.5 (get by "c1")) "min(1, evidence) caps the contribution at the full weight")))

(deftest row-carries-code-and-label
  (let [[c contrib w code label] (first (a/score-contributions crit per))]
    (is (= "c1" c)) (is (= 0.5 contrib)) (is (= 0.5 w)) (is (= "C1" code)) (is (= "mission-fit" label))))

#?(:clj
   (when (= *file* (System/getProperty "babashka.file"))
     (let [{:keys [fail error]} (run-tests 'tanemaki.methods.test-score-contributions)]
       (System/exit (if (zero? (+ fail error)) 0 1)))))
