#!/usr/bin/env bb
;; uzu 渦 — deterministic world-tape generator: build niches to test organisms against.
(ns uzu.methods.world
  "world.cljc — uzu 渦 world-tape generator (ADR-2606211500).

  Builds deterministic environment tapes from a sequence of regimes, so an organism can be
  evaluated against different NICHES. This makes the iteration-3 insight concrete: a good
  generative model is necessary but not sufficient — self-maintenance also needs a NET-POSITIVE
  niche. The same organism that starves in a scarce world sustains across many seasons in an
  abundant one. Signals are the regime signatures plus a small DETERMINISTIC jitter (derived
  from the step index — no Math/random), so the world is varied but resume-safe."
  (:require [uzu.methods.model :as model]))

(defn- clamp01 [x] (max 0.0 (min 1.0 (double x))))

(defn- jitter
  "Small deterministic perturbation in [-0.05, 0.05], a function of (step, dimension) only."
  [i k]
  (- (/ (double (mod (+ (* 37 (inc i)) (* 13 k)) 11)) 100.0) 0.05))

(defn build-tape
  "Build a world tape (vector of :world-step rows) from a sequence of regimes."
  [regimes]
  (vec (map-indexed
        (fn [i r]
          (let [{:keys [nutrient threat]} (model/regime-signature r)]
            {:type :world-step :step i :regime r
             :signal {:nutrient (clamp01 (+ nutrient (jitter i 0)))
                      :threat   (clamp01 (+ threat (jitter i 1)))}}))
        regimes)))

;; ── presets (niches) ──────────────────────────────────────────────────────────
(def abundant-regimes
  (vec (take 12 (cycle [:abundant :benign :abundant :abundant :benign :benign]))))
(def scarce-regimes
  (vec (take 12 (cycle [:scarce :hostile :scarce :scarce :hostile :scarce]))))
(def mixed-regimes
  (vec (take 12 (cycle [:abundant :benign :scarce :hostile]))))

(defn abundant-world [] (build-tape abundant-regimes))
(defn scarce-world   [] (build-tape scarce-regimes))
(defn mixed-world    [] (build-tape mixed-regimes))

(defn richness
  "Net-positive fraction of a tape: the share of steps in a feeding regime (abundant/benign).
  A crude niche-quality reading — higher means more sustainable."
  [tape]
  (let [n (count tape)]
    (if (zero? n) 0.0
        (/ (double (count (filter #(#{:abundant :benign} (:regime %)) tape))) n))))

#?(:clj
   (defn -main [& _]
     (doseq [[nm tp] [["abundant" (abundant-world)] ["mixed" (mixed-world)] ["scarce" (scarce-world)]]]
       (println (format "%-9s richness=%.2f regimes=%s" nm (richness tp)
                        (vec (map :regime tp)))))))

#?(:clj
   (when (= *file* (System/getProperty "babashka.file"))
     (apply -main *command-line-args*)))
