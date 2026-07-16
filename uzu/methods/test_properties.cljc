#!/usr/bin/env bb
;; uzu 渦 — sharp cross-cutting property tests (invariants the design implies but stated nowhere else).
;; Run: bb --classpath 20-actors 20-actors/uzu/methods/test_properties.cljc
(ns uzu.methods.test-properties
  (:require [uzu.methods.uzu-edn :as ue]
            [uzu.methods.model :as mo]
            [uzu.methods.ledger :as l]
            [uzu.methods.metabolism :as metab]
            [uzu.methods.measure :as measure]
            [uzu.methods.world :as w]
            [uzu.methods.digest :as dg]
            [clojure.test :refer [deftest is run-tests]]))

(def seed (ue/classify (ue/load-edn "20-actors/uzu/kotoba/seed.edn")))
(defn org [id] (first (filter #(= id (:id %)) (:organisms seed))))
(def obs-seq [{:nutrient 0.9 :threat 0.1} {:nutrient 0.4 :threat 0.85} {:nutrient 0.15 :threat 0.2}])

;; ── belief machinery identities ──────────────────────────────────────────────
(deftest fold-beliefs-equals-sequential-update
  (is (= (mo/fold-beliefs mo/uniform obs-seq 0.15)
         (reduce #(mo/update-belief %1 %2 0.15) mo/uniform obs-seq))
      "fold-beliefs is exactly the sequential perception fold"))

(deftest predict-leak-endpoints
  (let [q (mo/update-belief mo/uniform {:nutrient 0.9 :threat 0.1} 0.15)]
    (is (= mo/uniform (mo/predict q 1.0)) "full leak ⇒ belief relaxes all the way to uniform")
    (is (= q (mo/predict q 0.0)) "no leak ⇒ belief unchanged")))

(deftest belief-of-most-likely-is-stable-argmax
  (is (= :abundant (mo/most-likely (mo/update-belief mo/uniform (get mo/regime-signature :abundant) 0.15)))))

;; ── the pathology is DORMANT without hazard (sharp equality) ─────────────────
(deftest pathology-dormant-equals-the-survivor-without-hazard
  ;; meial (threat-SEEKING) and kurage (threat-averse) only DIVERGE where hazard exists.
  ;; In an abundant niche (no hostile steps) their lives are byte-for-byte identical.
  (let [a (w/abundant-world)]
    (is (= (:energy (metab/live-epochs (org "kurage") a 1)) (:energy (metab/live-epochs (org "meial") a 1))))
    (is (= (:energy (metab/live-epochs (org "kurage") a 3)) (:energy (metab/live-epochs (org "meial") a 3)))
        "no hazard to punish it ⇒ the threat-seeking meaning behaves exactly like the survivor")
    (is (= (:history (metab/live (org "kurage") a)) (:history (metab/live (org "meial") a)))
        "identical action+energy history when the niche never tests the difference")))

;; ── niche ordering ───────────────────────────────────────────────────────────
(deftest richness-orders-the-niches
  (is (> (w/richness (w/abundant-world)) (w/richness (w/mixed-world)) (w/richness (w/scarce-world)))))

;; ── digest is internally consistent ──────────────────────────────────────────
(deftest digest-survival-rate-matches-the-flags
  (let [lives (mapv #(metab/live % (:tape seed)) (:organisms seed))
        field (measure/field {:flows (:flows seed) :edges (:edges seed)})
        d (dg/colony lives field)
        n-alive (count (filter :alive? lives))]
    (is (= n-alive (:n-alive d)))
    ;; digest rounds survival-rate to 3 decimals (0.333 vs 1/3), so allow the rounding error
    (is (< (Math/abs (- (:survival-rate d) (/ (double n-alive) (:n d)))) 1e-3))))

;; ── the energy veto is absolute: an unaffordable preferred action is never taken ──
(deftest energy-veto-overrides-preference
  ;; belief→hostile, kurage would PREFER to flee — but if only :rest is affordable, it rests
  (let [q (mo/update-belief mo/uniform (get mo/regime-signature :hostile) 0.15)]
    (is (= :flee (mo/choose q {:nutrient 1.0 :threat 0.0} [:forage :flee :rest])) "with means, it flees")
    (is (= :rest (mo/choose q {:nutrient 1.0 :threat 0.0} (l/affordable 1.6 l/default-costs)))
        "starving (only rest affordable) ⇒ it cannot afford to flee the danger it sees")))

(let [{:keys [fail error]} (run-tests 'uzu.methods.test-properties)]
  (when (pos? (+ fail error)) (System/exit 1)))
