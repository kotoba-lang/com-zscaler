#!/usr/bin/env bb
;; inochi 命 — ie-flow embedding (the SoS scoring leg) via the shared gate-adapter.
(ns inochi.methods.ie-flow
  "ie_flow.cljc — inochi 命 embeds the information-energy flow lifecycle (etzhayyim.ie-flow,
  ADR-2606211200 + score ADR-2606212200) via the SHARED `etzhayyim.ie-flow.gate-adapter`
  (not a fork). inochi is an INFORMATION-CONTROL ACTOR: the raw extinction/degradation
  PRESSURE accumulating across the living world is high-entropy scattered ecological debt;
  inochi's edge-primary analyzer is a RECTIFIER (整流) that folds that raw pressure onto the
  bearers that matter most — re-weighting each bearer's incident pressure by its DISCLOSED
  IUCN essentiality, CONCENTRATING restoration-priority onto the most-essential burdened
  taxa/ecosystems (the custody-debt routed to RESTORATION / release).

  source = the bearer (a living-world node), route = the bearer itself (each is its own
  restoration outcome), volume = raw incident pressure load (the SCATTERED ecological debt),
  value = restoration-priority = Σ(load × IUCN-weight) · scale (the rectified order — pressure
  re-weighted by essentiality, scaled to the colony's value units like the sibling adapters).
  risk = 0 — inochi
  OBSERVES + routes to restoration; it never targets (G1 restoration map, NEVER a target-list;
  edge-primary karma, N1/G2). The flow ledger is the per-actor ie-flow record
  (80-data/ie-flow/inochi/, gitignored)."
  (:require [inochi.methods.analyze :as an]
            [etzhayyim.ie-flow.gate-adapter :as ga]
            [etzhayyim.ie-flow.metrics :as iem]
            [clojure.string :as str]))

(def default-seed "20-actors/inochi/data/seed-biosphere-graph.kotoba.edn")

(defn- bearer-rows
  "Per-bearer rows: raw inbound :pressures load (volume — scattered ecological debt) +
  restoration-priority (value — that load re-weighted by disclosed IUCN essentiality, the
  rectified custody-debt). Bearers with no incident pressure are dropped (0 volume)."
  [g]
  (let [{:keys [nodes edges]} g
        restoration (get (an/analyze nodes edges) "restoration")
        raw (reduce (fn [m e]
                      (if (= ":pressures" (get e ":en/kind"))
                        (update m (get e ":en/to") (fnil + 0.0)
                                (double (or (get e ":en/grasping-load") 0.0)))
                        m))
                    {} edges)]
    (->> raw
         (remove (fn [[_ load]] (zero? (double load))))
         (mapv (fn [[bearer load]]
                 {"bearer" bearer
                  "raw_load" (double load)
                  "restoration" (double (get restoration bearer 0.0))})))))

(defn config
  "The gate-adapter config for inochi's biosphere restoration synthesis (the domain model;
  the shared helper does the event/metric/record plumbing). volume = raw incident pressure
  (the SCATTERED ecological debt inochi rectifies), value = restoration-priority (load ×
  IUCN-weight — the rectified order, concentrated onto the most-essential burdened bearers),
  cost = flat, risk = 0 (observation + restoration routing; never a target-list, G1/G2)."
  [g]
  {:actor "inochi" :id-prefix "inochi-" :source-kind "bearer"
   :rows (bearer-rows g)
   :route-key "bearer"
   :volume-fn #(double (get % "raw_load"))
   :value-fn  #(* (double (get % "restoration")) ga/default-value-scale)})

(defn flow-events-from-graph [g] (ga/flow-events (config g)))
(defn flow-state-from-graph  [g] (ga/flow-state (config g)))

#?(:clj
   (defn flow-state
     "Load the biosphere seed graph and fold inochi's pressure→restoration rectification
     through the SHARED ie-flow metrics → the order calculus. :clj (file load)."
     ([] (flow-state default-seed))
     ([seed-path] (flow-state-from-graph (an/load-file* seed-path)))))

#?(:clj
   (defn record-flow!
     "Record inochi's measured ie-flow EVENTS to the shared per-actor ledger via the
     gate-adapter. Returns {:flow-log :events :order-index}."
     ([] (record-flow! default-seed {}))
     ([seed-path opts] (ga/record-flow! (config (an/load-file* seed-path)) opts))))

#?(:clj
   (defn -main [& args]
     (let [flags (set (filter #(str/starts-with? % "--") args))
           seed (or (first (remove #(str/starts-with? % "--") args)) default-seed)
           st (flow-state seed)]
       (println (iem/summary-line st))
       (when (contains? flags "--record")
         (let [r (record-flow! seed {:tx-id "inochi-ie-flow" :as-of "beat"})]
           (println (str "recorded " (:events r) " ie-flow events → " (:flow-log r))))))))

#?(:clj
   (when (= *file* (System/getProperty "babashka.file"))
     (apply -main *command-line-args*)))
