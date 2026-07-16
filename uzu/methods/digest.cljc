#!/usr/bin/env bb
;; uzu 渦 — colony self-reflection: the organism reasons about its own life + field.
(ns uzu.methods.digest
  "digest.cljc — uzu 渦 colony self-reflection (ADR-2606211500).

  Autonomy as SELF-REFLECTION: after living the tape, the colony reads its OWN history and
  the measured field and produces a structured digest — who self-maintained, which meanings
  (C) fit the world, the energy economy (drawn vs spent), and the field's dissipation
  (waste heat). Pure + deterministic (a fold over the lived states; no Math/random, no wall
  clock). Murakumo narration of this digest is the LIVE/gated step (ADR-2605215000) and is
  intentionally absent here — this namespace does no network I/O (no-server-key).

  The digest keeps the two ledgers distinct: the ENERGY economy (gained/spent/final, conserved)
  and the survival outcome are reported separately from the field's INFORMATION/physical
  measures, and physical dissipation is the only flow class with a waste-heat reading (G2/G3)."
  (:require [uzu.methods.metabolism :as metab]
            [uzu.methods.measure :as measure]
            [clojure.string :as str]))

(defn- r3 [x] (/ (Math/round (* 1000.0 (double x))) 1000.0))
(defn- mean [xs] (if (seq xs) (r3 (/ (reduce + (map double xs)) (count xs))) 0.0))

(defn colony
  "Reflect on a lived colony (vector of lived organism states from metabolism/live) and the
  measured field (from measure/field). Returns a structured, JSON-able digest."
  [lives field]
  (let [sums (map metab/summary lives)
        alive (filter :alive? sums)
        dead  (remove :alive? sums)
        beats (mapcat :history lives)
        gained (reduce + (map :gained beats))
        spent  (reduce + (map :spent beats))
        ;; the 'fittest' = the survivor with the most energy left (or, if none survived,
        ;; the one that lived longest) — a reading of which meaning best fit this world
        fittest (or (when (seq alive) (apply max-key :final-energy alive))
                    (when (seq sums) (apply max-key :lifespan sums)))
        phys-waste (->> (:flows field)
                        (keep measure/dissipation)
                        (map :waste-W) (reduce + 0.0))]
    {:n (count sums)
     :n-alive (count alive)
     :n-dead (count dead)
     :survival-rate (if (seq sums) (r3 (/ (count alive) (count sums))) 0.0)
     :energy {:drawn (r3 gained) :spent (r3 spent) :net (r3 (- gained spent))
              :mean-final-survivor (mean (map :final-energy alive))}
     :fittest (when fittest {:id (:id fittest) :alive? (:alive? fittest)
                             :final-energy (:final-energy fittest) :lifespan (:lifespan fittest)})
     :organisms (mapv (fn [s] {:id (:id s) :alive? (:alive? s)
                               :final-energy (:final-energy s) :lifespan (:lifespan s)
                               :belief (str (:final-belief-of s))}) sums)
     :field {:closed? (:closed? field)
             :physical-waste-W (r3 phys-waste)
             :totals (into {} (map (fn [[k v]] [k (:total v)]) (:totals field)))}}))

(defn report
  "A human-readable, narration-free rendering of the colony digest."
  [d]
  (str/join
   "\n"
   (concat
    [(format "uzu 渦 colony — %d/%d self-maintained (survival %.0f%%)"
             (:n-alive d) (:n d) (* 100.0 (:survival-rate d)))
     (format "energy economy: drawn %.1f − spent %.1f = net %.1f (survivor mean final %.1f)"
             (get-in d [:energy :drawn]) (get-in d [:energy :spent])
             (get-in d [:energy :net]) (get-in d [:energy :mean-final-survivor]))
     (when (:fittest d)
       (format "best-fitted meaning: %s (%s, energy %.1f, lifespan %d)"
               (get-in d [:fittest :id]) (if (get-in d [:fittest :alive?]) "alive" "dead")
               (get-in d [:fittest :final-energy]) (get-in d [:fittest :lifespan])))]
    (map (fn [o] (format "  %-7s %-5s energy %6.2f lifespan %2d belief %s"
                         (:id o) (if (:alive? o) "alive" "dead")
                         (:final-energy o) (:lifespan o) (:belief o))) (:organisms d))
    [(format "field: circulation closed=%s · physical waste-heat %.3e W" (:closed? (:field d)) (get-in d [:field :physical-waste-W]))])))

(defn datoms
  "Render the colony digest to EAVT datoms (:uzu.digest/*). Colony-level reflection only."
  [d]
  (let [e "uzu:digest/colony"]
    [[":db/add" e ":uzu.digest/n" (:n d)]
     [":db/add" e ":uzu.digest/n-alive" (:n-alive d)]
     [":db/add" e ":uzu.digest/n-dead" (:n-dead d)]
     [":db/add" e ":uzu.digest/survival-rate" (:survival-rate d)]
     [":db/add" e ":uzu.digest/energy-drawn" (get-in d [:energy :drawn])]
     [":db/add" e ":uzu.digest/energy-spent" (get-in d [:energy :spent])]
     [":db/add" e ":uzu.digest/energy-net" (get-in d [:energy :net])]
     [":db/add" e ":uzu.digest/fittest" (str (get-in d [:fittest :id]))]
     [":db/add" e ":uzu.digest/physical-waste-W" (get-in d [:field :physical-waste-W])]
     [":db/add" e ":uzu.digest/circulation-closed" (:closed? (:field d))]
     [":db/add" e ":uzu/derived" true]
     [":db/add" e ":uzu/sourcing" ":synthetic"]]))

#?(:clj
   (defn -main [& args]
     (let [seed-path (or (first args) "20-actors/uzu/kotoba/seed.edn")
           rows (clojure.edn/read-string (slurp seed-path))
           tape (->> rows (filter #(= (:type %) :world-step)) (sort-by :step) vec)
           orgs (vec (filter #(= (:type %) :organism) rows))
           flows (vec (filter #(= (:type %) :flow) rows))
           edges (vec (filter #(= (:type %) :circulation) rows))
           lives (mapv #(metab/live % tape) orgs)
           d (colony lives (measure/field {:flows flows :edges edges}))]
       (println (report d)))))

#?(:clj
   (when (= *file* (System/getProperty "babashka.file"))
     (apply -main *command-line-args*)))
