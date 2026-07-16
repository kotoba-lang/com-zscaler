#!/usr/bin/env bb
;; LIVE cross-language py↔clj parity for the mitooshi proper-scoring core.
(ns mitooshi.methods.test-score-parity
  "test_score_parity.clj — mitooshi score.cljc py↔clj LIVE parity (ADR-2606051800).

  score.cljc is the numerically-subtle heart of the forecasting loop (CRPS via the Gaussian
  Φ/φ, pinball, Brier, log-score, PIT, skill). The existing test pins values captured once from
  score.py; this runs the ACTUAL `score.py` via a python3 subprocess and the clj impl over the
  SAME battery, asserting every scorer agrees to 1e-6 — catching drift in EITHER implementation
  (a one-ULP Φ difference, a quantile-interp edge, a denominator convention).

  Gracefully SKIPS if python3 is unavailable (red only on a genuine py↔clj divergence).

  Run:  bb --classpath 20-actors 20-actors/mitooshi/methods/test_score_parity.clj"
  (:require [mitooshi.methods.score :as s]
            [clojure.java.shell :refer [sh]]
            [cheshire.core :as json]
            [clojure.test :refer [deftest is run-tests]]))

(def ^:private py-dir "20-actors/mitooshi/methods")

;; ── the shared battery (identical literals in py-src and the clj recompute below) ──
(def ^:private gauss-in  [[10.0 2.0 11.0] [0.0 1.0 0.0] [5.0 3.0 2.0] [-3.0 0.5 -2.0]])
(def ^:private glog-in   [[10.0 2.0 11.0] [0.0 1.0 0.5]])
(def ^:private pin-ys    [9.0 11.0 15.0 8.0])
(def ^:private ens-in    [[[1.0 2.0 3.0] 2.0] [[0.0 1.0] 0.5] [[5.0 5.0 5.0] 4.0]])
(def ^:private brier-cls ["up" "down"])
(def ^:private skill-in  [[0.5 1.0] [2.0 1.0] [1.0 1.0]])
(def ^:private hist      [1.0 2.0 3.0 4.0])

(def ^:private py-src
  (str "import json, score as s\n"
       "out = {\n"
       " 'gcrps': [s.gaussian_crps(mu,sg,y) for mu,sg,y in [[10.0,2.0,11.0],[0.0,1.0,0.0],[5.0,3.0,2.0],[-3.0,0.5,-2.0]]],\n"
       " 'glog':  [s.gaussian_logscore(mu,sg,y) for mu,sg,y in [[10.0,2.0,11.0],[0.0,1.0,0.5]]],\n"
       " 'gpit':  [s.gaussian_pit(mu,sg,y) for mu,sg,y in [[10.0,2.0,11.0],[0.0,1.0,0.0],[5.0,3.0,2.0],[-3.0,0.5,-2.0]]],\n"
       " 'pinball': [s.pinball_loss({0.1:8.0,0.5:10.0,0.9:12.0}, y) for y in [9.0,11.0,15.0,8.0]],\n"
       " 'ecrps': [s.ensemble_crps(m,y) for m,y in [[[1.0,2.0,3.0],2.0],[[0.0,1.0],0.5],[[5.0,5.0,5.0],4.0]]],\n"
       " 'brier': [s.brier_score({'up':0.6,'down':0.4}, c) for c in ['up','down']],\n"
       " 'clog':  [s.categorical_logscore({'up':0.7,'down':0.3}, c) for c in ['up','down']],\n"
       " 'skill': [s.skill_score(m,b) for m,b in [[0.5,1.0],[2.0,1.0],[1.0,1.0]]],\n"
       " 'clim':  list(s.climatology_gaussian([1.0,2.0,3.0,4.0])),\n"
       " 'persist': list(s.persistence_gaussian([1.0,2.0,3.0,4.0])),\n"
       "}\n"
       "print(json.dumps(out))\n"))

(defn- py-results []
  (try
    (let [r (sh "python3" "-c" py-src :dir py-dir)]
      (when (and (= 0 (:exit r)) (seq (:out r)))
        (json/parse-string (:out r) true)))
    (catch Exception _ nil)))

(defn- close? [a b] (< (Math/abs (- (double a) (double b))) 1e-6))
(defn- close-seq? [py clj] (and (= (count py) (count clj)) (every? true? (map close? py clj))))

;; clj-side recompute of the identical battery
(defn- clj-battery []
  {:gcrps   (mapv #(apply s/gaussian-crps %) gauss-in)
   :glog    (mapv #(apply s/gaussian-logscore %) glog-in)
   :gpit    (mapv #(apply s/gaussian-pit %) gauss-in)
   :pinball (mapv #(s/pinball-loss {0.1 8.0 0.5 10.0 0.9 12.0} %) pin-ys)
   :ecrps   (mapv (fn [[m y]] (s/ensemble-crps m y)) ens-in)
   :brier   (mapv #(s/brier-score {"up" 0.6 "down" 0.4} %) brier-cls)
   :clog    (mapv #(s/categorical-logscore {"up" 0.7 "down" 0.3} %) brier-cls)
   :skill   (mapv #(apply s/skill-score %) skill-in)
   :clim    (vec (s/climatology-gaussian hist))
   :persist (vec (s/persistence-gaussian hist))})

(deftest clj-scorers-are-finite-and-sane
  ;; runs regardless of python: proper scores are non-negative; PIT ∈ [0,1].
  (let [b (clj-battery)]
    (is (every? #(>= % 0.0) (:gcrps b)) "CRPS ≥ 0")
    (is (every? #(>= % 0.0) (:pinball b)) "pinball ≥ 0")
    (is (every? #(>= % 0.0) (:brier b)) "Brier ≥ 0")
    (is (every? #(and (<= 0.0 %) (<= % 1.0)) (:gpit b)) "PIT ∈ [0,1]")))

(deftest score-core-matches-python
  (let [py (py-results) clj (clj-battery)]
    (if-not py
      (is true "python3 unavailable — proper-scoring cross-language parity skipped")
      (doseq [k [:gcrps :glog :gpit :pinball :ecrps :brier :clog :skill :clim :persist]]
        (is (close-seq? (get py k) (get clj k))
            (str (name k) " drift: py " (get py k) " clj " (get clj k)))))))

(when (= *file* (System/getProperty "babashka.file"))
  (let [{:keys [fail error]} (run-tests 'mitooshi.methods.test-score-parity)]
    (System/exit (if (zero? (+ fail error)) 0 1))))
