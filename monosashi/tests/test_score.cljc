(ns monosashi.tests.test-score
  "monosashi 物差し — skill-band + charter-invariant tests. Pure, network-free, deterministic.
  Asserts STATISTICS and ADVERSARIAL inputs, not just shapes."
  (:require [clojure.test :refer [deftest is run-tests]]
            [clojure.java.io :as io]
            [monosashi.methods.score :as score]
            [monosashi.methods.autorun :as autorun]))

;; data/seed-scores.kotoba.edn is stored datomized (tx-data shape); go through
;; autorun/load-residuals, which reconstitutes the original {:residuals [...]}
;; map so this test keeps working unchanged.
(def seed
  (-> (io/file *file*) .getParentFile .getParentFile
      (io/file "data" "seed-scores.kotoba.edn") str
      autorun/load-residuals))

(def as-of "2026-06-27T00:00:00Z")
(defn- bands [] (score/evaluate (:residuals seed) {:as-of as-of}))

(deftest band-is-monotone-distribution
  (doseq [b (bands)]
    (is (<= (:eval/p10 b) (:eval/p50 b) (:eval/p90 b)) "p10 ≤ p50 ≤ p90")
    (is (false? (:eval/point-asserted b)) "G1: point-asserted is structurally false")
    (is (not (contains? b :eval/point)) "G1: no :eval/point key")
    (is (= ":model-assessment" (:eval/use b)) "G2: non-speculative use")
    (is (not (contains? b :eval/coherence)) "S6: no fabricated coherence without a same-series model")))

(deftest s7-grouped-per-actor-and-baseline
  ;; mitooshi has TWO baselines (climatology, persistence) → two separate bands; skill is never pooled.
  (is (= [["hakoniwa" "random-walk"] ["mitooshi" "climatology"] ["mitooshi" "persistence"]]
         (mapv (juxt :eval/actor :eval/baseline) (bands)))
      "one band per (actor, baseline), sorted")
  (doseq [b (bands)] (is (string? (:eval/baseline b)) "G12: a single documented baseline")))

(deftest g3-anti-goodhart-refuses-reward
  (doseq [k [:eval/reward :eval/target :eval/payout :eval/incentive :eval/stake :eval/bounty :eval/prize]]
    (is (thrown? clojure.lang.ExceptionInfo (score/assert-no-reward {k 1.0}))
        (str "G3: " k " must be refused")))
  (doseq [b (bands)]
    (is (every? #(not (contains? b %)) [:eval/reward :eval/target :eval/payout]) "no reward field")))

(deftest g5-leak-free-instant-correct
  ;; future outcome (Z) → refused
  (is (thrown? clojure.lang.ExceptionInfo
               (score/skill-band [{:forecast/fid "leak" :score/observed-at "2999-01-01T00:00:00Z" :score/skill 0.9}]
                                 {:as-of as-of :actor "x" :baseline "b"}))
      "future outcome refused")
  ;; MISSING observed-at → refused (no silent skip)
  (is (thrown? clojure.lang.ExceptionInfo
               (score/skill-band [{:forecast/fid "nots" :score/skill 0.9}]
                                 {:as-of as-of :actor "x" :baseline "b"}))
      "S1: missing :score/observed-at refused")
  ;; +09:00 offset that is actually BEFORE as-of (2026-06-26T23:00Z ≤ 2026-06-27T00:00Z) → OK,
  ;; NOT a leak. Lexicographic string compare would have falsely refused it.
  (is (some? (score/skill-band [{:forecast/fid "ok" :score/observed-at "2026-06-27T08:00:00+09:00" :score/skill 0.5}]
                               {:as-of as-of :actor "x" :baseline "b"}))
      "S1: offset timestamp before as-of accepted (instant-correct)")
  (is (true? (score/instant<= "2026-06-27T08:00:00+09:00" "2026-06-27T00:00:00Z")) "offset → instant order"))

(deftest b1-calibration-deviation-direction
  ;; A near-UNIFORM PIT set is CALIBRATED → deviation ≈ 0.
  (let [uniform (mapv (fn [p] {:score/pit p}) [0.05 0.15 0.25 0.35 0.45 0.55 0.65 0.75 0.85 0.95])
        ;; All PIT = 0.5 is severe UNDER-dispersion → deviation LARGE (the old mean|PIT−0.5| bug reported 0).
        underdispersed (mapv (fn [_] {:score/pit 0.5}) (range 10))]
    (is (< (score/calibration-deviation uniform) 0.01) "uniform PITs ⇒ deviation ≈ 0 (calibrated)")
    (is (> (score/calibration-deviation underdispersed) 1.0) "all-0.5 PITs ⇒ LARGE deviation (mis-calibrated)")
    (is (> (score/calibration-deviation underdispersed) (score/calibration-deviation uniform))
        "B1: under-dispersion scores WORSE than uniform (metric not inverted)")))

(deftest g12-skilled-honesty
  (let [by (into {} (map (juxt (juxt :eval/actor :eval/baseline) identity)) (bands))
        mito (by ["mitooshi" "climatology"])
        hako (by ["hakoniwa" "random-walk"])]
    (is (true? (:eval/skilled mito)) "mitooshi/climatology p50>0 ⇒ skilled")
    (is (= (boolean (pos? (:eval/p50 hako))) (:eval/skilled hako)) "skilled iff p50>0")))

(deftest coherence-same-series-only
  (let [rs [{:forecast/series-id "B-burden" :forecast/band-width 2.0 :score/observed-at "2026-06-25T00:00:00Z" :score/skill 0.2}]
        same (score/skill-band rs {:as-of as-of :actor "x" :baseline "b" :sysdyn {:series "B-burden" :band-width 2.0}})
        diff (score/skill-band rs {:as-of as-of :actor "x" :baseline "b" :sysdyn {:series "other" :band-width 2.0}})]
    (is (= 1.0 (:eval/coherence same)) "same-series, equal width ⇒ coherence 1.0")
    (is (not (contains? diff :eval/coherence)) "different series ⇒ coherence omitted (not smeared)")))

(deftest deterministic
  (is (= (bands) (bands)) "two evaluations are equal"))

(deftest percentile-interpolates
  (is (= 1.5 (score/percentile [1 2] 0.5)) "midpoint interpolation")
  (is (= 2.0 (score/percentile [1 2 3] 0.5)) "median of odd coll")
  (is (nil? (score/percentile [] 0.5)) "empty → nil"))
