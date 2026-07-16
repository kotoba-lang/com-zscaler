#!/usr/bin/env bb
;; uzu 渦 — world-tape generator + niche tests: a good model needs a net-positive niche.
;; Run: bb --classpath 20-actors 20-actors/uzu/methods/test_world.cljc
(ns uzu.methods.test-world
  (:require [uzu.methods.world :as w]
            [uzu.methods.uzu-edn :as ue]
            [uzu.methods.metabolism :as metab]
            [uzu.methods.validate :as v]
            [clojure.edn :as edn]
            [clojure.test :refer [deftest is run-tests]]))

(def ont (ue/ontology-map (edn/read-string (slurp "20-actors/uzu/kotoba/ontology.uzu.edn"))))
(def kurage (first (filter #(= "kurage" (:id %)) (ue/organisms "20-actors/uzu/kotoba/seed.edn"))))

(deftest richness-reads-the-niche
  (is (< (Math/abs (- 1.0 (w/richness (w/abundant-world)))) 1e-9) "abundant world = all feeding regimes")
  (is (< (Math/abs (- 0.0 (w/richness (w/scarce-world)))) 1e-9) "scarce world = no feeding regimes")
  (is (< (Math/abs (- 0.5 (w/richness (w/mixed-world)))) 1e-9) "mixed = half feeding"))

(deftest generated-tapes-are-well-formed
  (let [tp (w/mixed-world)]
    (is (= 12 (count tp)))
    (is (= (range 12) (map :step tp)) "contiguous steps")
    ;; a generated tape validates against the ontology (no organisms/flows needed here)
    (is (true? (:ok (v/validate {:tape tp :organisms [] :flows [] :edges []} ont))))))

(deftest generated-tapes-are-deterministic
  (is (= (w/abundant-world) (w/abundant-world)) "no Math/random ⇒ identical tape every build"))

;; ── the niche decides: a good model needs a net-positive world ───────────────
(deftest abundant-niche-sustains-and-accumulates
  (let [e1 (:energy (metab/live-epochs kurage (w/abundant-world) 1))
        e5 (:energy (metab/live-epochs kurage (w/abundant-world) 5))]
    (is (true? (:alive? (metab/live-epochs kurage (w/abundant-world) 5))) "kurage thrives across 5 seasons")
    (is (> e5 e1) "energy ACCUMULATES season over season in a net-positive niche")))

(deftest scarce-niche-starves-the-same-organism
  ;; identical meaning (C), opposite fate — self-maintenance is not just a good model
  (is (false? (:alive? (metab/live-epochs kurage (w/scarce-world) 1))) "the same kurage dies in season 1 of a scarce world"))

(let [{:keys [fail error]} (run-tests 'uzu.methods.test-world)]
  (when (pos? (+ fail error)) (System/exit 1)))
