#!/usr/bin/env bb
;; uzu 渦 — viability envelope: which MEANINGS survive which NICHES (a fitness matrix).
(ns uzu.methods.landscape
  "landscape.cljc — uzu 渦 viability-envelope survey (ADR-2606211500).

  Autonomy as SELF-CHARACTERIZATION: sweep a grid of meanings (preference C) × niches (world
  tapes) over several seasons and report the survival matrix — the organism mapping its own
  viability envelope. It composes the pieces (metabolism + world) into one reading that neither
  alone gives: fitness is the JOINT property of a meaning AND a niche, not of either by itself
  (a meaning that thrives in abundance starves in scarcity; the seed's pathological meanings
  die everywhere). Pure + deterministic; no network (no-server-key)."
  (:require [uzu.methods.metabolism :as metab]
            [uzu.methods.world :as world]
            [clojure.string :as str]))

(defn default-niches []
  [["abundant" (world/abundant-world)]
   ["mixed"    (world/mixed-world)]
   ["scarce"   (world/scarce-world)]])

(defn- cell [organism-config tape epochs]
  (let [s (metab/live-epochs (merge {:temp 0.15 :energy0 12.0} organism-config) tape epochs)
        sm (metab/summary s)]
    {:alive? (:alive? s) :final-energy (:final-energy sm) :lifespan (:lifespan sm)}))

(defn survey
  "Sweep meanings × niches → a survival matrix.
   meanings = [{:id :prefs}…]; niches = [[name tape]…]. Returns a vector of
   {:id :prefs :cells [{:niche :alive? :final-energy :lifespan}…]}."
  [meanings niches epochs]
  (mapv (fn [m]
          {:id (:id m) :prefs (:prefs m)
           :cells (mapv (fn [[nm tape]] (assoc (cell m tape epochs) :niche nm)) niches)})
        meanings))

(defn report
  "Text survival grid: a row per meaning, a column per niche (✓ alive / ✗ dead + energy)."
  [rows]
  (let [niches (map :niche (:cells (first rows)))]
    (str/join "\n"
      (concat
       [(str "uzu 渦 viability envelope (meaning × niche, over seasons)")
        (apply str (format "%-9s" "meaning") (map #(format " %12s" %) niches))]
       (map (fn [r]
              (apply str (format "%-9s" (:id r))
                     (map (fn [c] (format " %12s" (str (if (:alive? c) "✓" "✗")
                                                       (format "%.0f" (:final-energy c)))))
                          (:cells r))))
            rows)))))

(defn datoms
  "EAVT datoms: one survival fact per (meaning, niche)."
  [rows]
  (vec (mapcat
        (fn [r]
          (mapcat (fn [c]
                    (let [e (str "uzu:landscape/" (:id r) "/" (:niche c))]
                      [[":db/add" e ":uzu.landscape/meaning" (:id r)]
                       [":db/add" e ":uzu.landscape/niche" (:niche c)]
                       [":db/add" e ":uzu.landscape/alive" (:alive? c)]
                       [":db/add" e ":uzu.landscape/final-energy" (:final-energy c)]
                       [":db/add" e ":uzu/derived" true]]))
                  (:cells r)))
        rows)))

#?(:clj
   (defn -main [& args]
     (let [seed-path (or (first args) "20-actors/uzu/kotoba/seed.edn")
           epochs (Integer/parseInt (or (second args) "3"))
           orgs (->> (clojure.edn/read-string (slurp seed-path))
                     (filter #(= (:type %) :organism))
                     (mapv #(select-keys % [:id :prefs])))
           rows (survey orgs (default-niches) epochs)]
       (println (report rows))
       (println (str "(over " epochs " seasons; ✓=alive ✗=dead, number=final energy)")))))

#?(:clj
   (when (= *file* (System/getProperty "babashka.file"))
     (apply -main *command-line-args*)))
