#!/usr/bin/env bb
;; amime 網目 — datom emission: own ledger EAVT + kaname-facing :energy mirror graph.
(ns amime.methods.emit
  "amime 網目 — emission layer (ADR-2606212020).

  Two emitters, both pure:
    `datoms`        → amime-native append-only EAVT [op e a v] for the local ledger
                      (kotoba.cljc / autorun.cljc). Every datom flagged :amime/derived +
                      :amime/sourcing. G1: no :amime/price / :amime/trade attribute is ever
                      emitted (a commons mesh, not a market — structurally enforced + tested).
    `kaname-forms`  → a kaname-mirror multilayer graph (vector of :organism/* node maps +
                      :en/* edge maps) tagging the energy-flow concentration into the
                      :energy domain. This is what kaname 要's :energy adapter JOINS
                      (ADR-2606212000): the energy-flow 取 lifted into the system-of-systems
                      multiplex. Concentration accrues onto the importing load over each
                      flowing link (:concentrates); single-path import is :depends-on (the
                      SPOF cascade). Resilience MAP — never a target-list."
  (:require [clojure.string :as str]
            [amime.methods.mesh :as mesh]
            #?(:clj [clojure.java.io :as io])))

(defn- add [e a v] [":db/add" e a v])
(defn- round3 [x] (/ (Math/round (* (double x) 1000.0)) 1000.0))

;; ── amime-native ledger datoms ────────────────────────────────────────────────

(defn datoms
  "Append-only EAVT datom VECTORS for one mesh solve. No :amime/price / :amime/trade (G1)."
  [seed]
  (let [sln (mesh/solve seed)
        cont (mesh/contingency seed)
        crit (first cont)
        crit-id (when crit (name (:link crit)))]
    (vec
     (concat
      ;; sites
      (mapcat
       (fn [[sid sr]]
         (let [e (str "amime-site:" (name sid))]
           [(add e ":amime.site/role" (str (:role sr)))
            (add e ":amime.site/net" (round3 (:net sr)))
            (add e ":amime.site/served" (round3 (:served sr)))
            (add e ":amime.site/unserved" (round3 (:unserved sr)))
            (add e ":amime.site/curtailed" (round3 (:curtailed sr)))
            (add e ":amime/sourcing" ":synthetic")
            (add e ":amime/derived" true)]))
       (sort-by key (:sites sln)))
      ;; links
      (mapcat
       (fn [[lid lr]]
         (let [e (str "amime-link:" (name lid))]
           [(add e ":amime.link/flow" (round3 (:flow lr)))
            (add e ":amime.link/util" (round3 (:util lr)))
            (add e ":amime.link/loss" (round3 (:loss lr)))
            (add e ":amime.link/critical" (= (name lid) crit-id))
            (add e ":amime/sourcing" ":synthetic")
            (add e ":amime/derived" true)]))
       (sort-by key (:links sln)))
      ;; mesh summary
      (let [e "amime-mesh:summary"]
        [(add e ":amime.mesh/served-fraction" (round3 (:served-fraction sln)))
         (add e ":amime.mesh/loss" (round3 (:loss sln)))
         (add e ":amime.mesh/curtailed" (round3 (:curtailed sln)))
         (add e ":amime.mesh/unserved" (round3 (:unserved sln)))
         (when crit-id (add e ":amime.mesh/critical-link" crit-id))
         (add e ":amime/derived" true)])))))

;; ── kaname-facing :energy mirror graph (forms: node + edge maps) ───────────────

(defn kaname-forms
  "Lift the mesh solve into a kaname-mirror multilayer graph for the :energy domain layer.
  Returns a vector of :organism/* node maps + :en/* edge maps (kaname's `parse-graph`
  auto-detects this forms shape). kaname 要 then joins it via the :amime adapter
  (ADR-2606212000) and computes cross-domain leverage natively over the :energy layer."
  [seed]
  (let [sln (mesh/solve seed)
        dep (mesh/import-dependence sln (:links seed))
        site-label (fn [sid sr] (str (str/replace (name sid) "-" " ") " ("
                                     (str/replace (str (:role sr)) ":" "") ", synthetic)"))
        nodes (mapv (fn [[sid sr]]
                      {":organism/id" (name sid)
                       ":organism/kind" ":sos/entity"
                       ":organism/label" (site-label sid sr)
                       ":organism/sourcing" ":representative"
                       ":sos/source-actors" [":amime"]
                       ;; a load served by ≥3 suppliers with <0.5 single-path dependence is
                       ;; an already-redundant (open) energy position — nothing to dissolve.
                       ":sos/open?" (boolean (when-let [d (get dep sid)]
                                               (and (>= (:n-suppliers d) 3)
                                                    (< (:dependence d) 0.5))))})
                    (sort-by key (:sites sln)))
        ;; concentration: each flowing link concentrates import-load onto the consumer.
        max-flow (reduce max 1e-9 (map :flow (vals (:links sln))))
        conc-edges (->> (sort-by key (:links sln))
                        (keep (fn [[lid lr]]
                                (when (> (:flow lr) 1e-9)
                                  {":en/from" (name (:from lr))
                                   ":en/to" (name (:to lr))
                                   ":en/kind" ":concentrates"
                                   ":en/domain" ":energy"
                                   ":en/grasping-load" (round3 (/ (:flow lr) max-flow))
                                   ":en/sourcing" ":representative"
                                   ":en/basis" (str "flow " (round3 (:flow lr)) " kW over link " (name lid))}))))
        ;; SPOF cascade: a deficit site's top-path dependence is a :depends-on fragility edge.
        dep-edges (->> dep
                       (keep (fn [[sid d]]
                               (when (and (> (:dependence d) 0.5) (>= (:served d) 1e-9))
                                 ;; find the top supplier of this site
                                 (let [top (->> (:links seed)
                                                (filter #(or (= (:from %) sid) (= (:to %) sid)))
                                                (map (fn [l] [(if (= (:from l) sid) (:to l) (:from l))
                                                              (get-in sln [:links (:id l) :flow] 0.0)]))
                                                (sort-by (comp - second))
                                                first)]
                                   (when (and top (> (second top) 1e-9))
                                     {":en/from" (name sid)
                                      ":en/to" (name (first top))
                                      ":en/kind" ":depends-on"
                                      ":en/domain" ":energy"
                                      ":en/grasping-load" (round3 (:dependence d))
                                      ":en/sourcing" ":representative"
                                      ":en/basis" (str (format "%.0f%%" (* 100.0 (:dependence d)))
                                                       " of import over a single path")}))))))]
    (vec (concat nodes conc-edges dep-edges))))

;; ── EDN string rendering ───────────────────────────────────────────────────────

(defn render-datoms [seed]
  (str "[\n " (str/join "\n " (map pr-str (datoms seed))) "\n]\n"))

(defn render-kaname-forms [seed]
  (str ";; amime 網目 → kaname 要 :energy domain mirror (ADR-2606212000 / 2606212020).\n"
       ";; A resilience map of energy-flow concentration — NEVER a target-list. :synthetic.\n"
       "[\n" (str/join "\n" (map pr-str (kaname-forms seed))) "\n]\n"))

;; ── CLI: write the committed kaname-facing export ──────────────────────────────

#?(:clj
   (defn -main [& args]
     (let [seed-path (or (first args) "20-actors/amime/kotoba/seed.edn")
           out-path (or (second args) "20-actors/amime/out/energy-sos.kotoba.edn")
           seed (mesh/classify (mesh/load-edn seed-path))]
       (io/make-parents out-path)
       (spit out-path (render-kaname-forms seed))
       (println (str "amime → kaname :energy mirror written: " out-path))
       (println (str "  " (count (kaname-forms seed)) " forms ("
                     (count (:sites seed)) " sites + flow/SPOF 縁)")))))

#?(:clj
   (when (= *file* (System/getProperty "babashka.file"))
     (apply -main *command-line-args*)))
