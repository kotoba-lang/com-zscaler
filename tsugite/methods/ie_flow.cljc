#!/usr/bin/env bb
;; tsugite 継ぎ手 — ie-flow embedding (the SoS scoring leg) via the shared gate-adapter.
(ns tsugite.methods.ie-flow
  "ie_flow.cljc — tsugite 継ぎ手 embeds the information-energy flow lifecycle (etzhayyim.ie-flow,
  ADR-2606211200 + score ADR-2606212200) via the SHARED `etzhayyim.ie-flow.gate-adapter`
  (not a fork). tsugite is an INFORMATION-CONTROL ACTOR: the raw displacement/erasure PERIL
  borne by human COLLECTIVES (migrant/refugee/stateless populations, indigenous peoples,
  language communities) is high-entropy scattered jeopardy; tsugite's edge-primary analyzer is
  a RECTIFIER (整流) that folds that raw peril onto the collectives that matter most —
  re-weighting each bearer's incident peril by its DISCLOSED vitality/endangerment, CONCENTRATING
  the continuity-priority onto the most-imperilled collectives, routed to CONTINUITY (継承 —
  safe passage / protection / revitalization).

  source = the collective (an aggregate human-collective node), route = the bearer itself (each
  is its own continuity outcome), volume = raw incident peril load (the SCATTERED jeopardy),
  value = continuity = Σ(peril-load × vitality-weight) · scale (the rectified order — peril
  re-weighted by endangerment). risk = 0 — tsugite OBSERVES + routes to continuity; AGGREGATE /
  COLLECTIVE scale ONLY, NEVER person-tracking (G1: no individual/location/biometric, never a
  border-enforcement / deportation aid). A continuity map, NEVER a target-list; edge-primary
  karma (N1/G2). Ledger: per-actor ie-flow record (80-data/ie-flow/tsugite/, gitignored)."
  (:require [tsugite.methods.analyze :as an]
            [etzhayyim.ie-flow.gate-adapter :as ga]
            [etzhayyim.ie-flow.metrics :as iem]
            [clojure.string :as str]))

(def default-seed "20-actors/tsugite/data/seed-peoples-graph.kotoba.edn")

(defn- bearer-rows
  "Per-bearer rows: raw inbound peril load (volume — scattered jeopardy) + continuity-priority
  (value — that load re-weighted by disclosed vitality/endangerment, the rectified continuity
  surface). Bearers with no incident peril are dropped (0 volume)."
  [g]
  (let [{:keys [nodes edges]} g
        continuity (get (an/analyze nodes edges) "continuity")
        raw (reduce (fn [m e]
                      (if (contains? an/pressure-kinds (get e ":en/kind"))
                        (update m (get e ":en/to") (fnil + 0.0)
                                (double (or (get e ":en/peril-load") 0.0)))
                        m))
                    {} edges)]
    (->> raw
         (remove (fn [[_ load]] (zero? (double load))))
         (mapv (fn [[bearer load]]
                 {"bearer" bearer
                  "raw_load" (double load)
                  "continuity" (double (get continuity bearer 0.0))})))))

(defn config
  "The gate-adapter config for tsugite's peoples-continuity synthesis. volume = raw incident
  peril (the SCATTERED jeopardy tsugite rectifies), value = continuity-priority (peril-load ×
  vitality-weight — the rectified order, concentrated onto the most-imperilled collectives),
  cost = flat, risk = 0 (observation + continuity routing; collective-aggregate, never
  person-tracking / a target-list)."
  [g]
  {:actor "tsugite" :id-prefix "tsugite-" :source-kind "collective"
   :rows (bearer-rows g)
   :route-key "bearer"
   :volume-fn #(double (get % "raw_load"))
   :value-fn  #(* (double (get % "continuity")) ga/default-value-scale)})

(defn flow-events-from-graph [g] (ga/flow-events (config g)))
(defn flow-state-from-graph  [g] (ga/flow-state (config g)))

#?(:clj
   (defn flow-state
     "Load the peoples seed graph and fold tsugite's peril→continuity rectification through the
     SHARED ie-flow metrics → the order calculus. :clj (file load)."
     ([] (flow-state default-seed))
     ([seed-path] (flow-state-from-graph (an/load-file* seed-path)))))

#?(:clj
   (defn record-flow!
     ([] (record-flow! default-seed {}))
     ([seed-path opts] (ga/record-flow! (config (an/load-file* seed-path)) opts))))

#?(:clj
   (defn -main [& args]
     (let [flags (set (filter #(str/starts-with? % "--") args))
           seed (or (first (remove #(str/starts-with? % "--") args)) default-seed)
           st (flow-state seed)]
       (println (iem/summary-line st))
       (when (contains? flags "--record")
         (let [r (record-flow! seed {:tx-id "tsugite-ie-flow" :as-of "beat"})]
           (println (str "recorded " (:events r) " ie-flow events → " (:flow-log r))))))))

#?(:clj
   (when (= *file* (System/getProperty "babashka.file"))
     (apply -main *command-line-args*)))
