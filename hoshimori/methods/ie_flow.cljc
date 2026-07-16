#!/usr/bin/env bb
;; hoshimori 星守 — ie-flow embedding (the SoS scoring leg) via the shared gate-adapter.
(ns hoshimori.methods.ie-flow
  "ie_flow.cljc — hoshimori 星守 embeds the information-energy flow lifecycle (etzhayyim.ie-flow,
  ADR-2606211200 + score ADR-2606212200) via the SHARED `etzhayyim.ie-flow.gate-adapter`
  (not a fork). hoshimori is an INFORMATION-CONTROL ACTOR: the raw orbital-congestion HAZARD
  accumulating across orbital regimes/objects is high-entropy scattered crowding; hoshimori's
  edge-primary analyzer is a RECTIFIER (整流) that folds that raw hazard onto the shells that
  matter most — re-weighting each bearer's incident hazard by its DISCLOSED orbital-regime
  importance, CONCENTRATING the congestion surface onto the most-congested regimes, routed to
  STEWARDSHIP (orbital sustainability — remediate / deconflict / deorbit).

  source = the orbital bearer (a regime/object node), route = the bearer itself (each is its own
  stewardship outcome), volume = raw incident hazard load (the SCATTERED orbital crowding),
  value = congestion = Σ(hazard-load × regime-weight) · scale (the rectified order — hazard
  re-weighted by regime importance). risk = 0 — hoshimori OBSERVES + routes to stewardship;
  shell/regime-AGGREGATE only, NEVER a targeting / interception aid (G1 dual-use; no precise
  predictive ephemeris; ASAT unrepresentable §1.12). A stewardship map, NEVER a target-list;
  edge-primary karma (N1/G2). Ledger: per-actor ie-flow record (80-data/ie-flow/hoshimori/, gitignored)."
  (:require [hoshimori.methods.analyze :as an]
            [etzhayyim.ie-flow.gate-adapter :as ga]
            [etzhayyim.ie-flow.metrics :as iem]
            [clojure.string :as str]))

(def default-seed "20-actors/hoshimori/data/seed-orbit-graph.kotoba.edn")

(defn- bearer-rows
  "Per-bearer rows: raw inbound hazard load (volume — scattered orbital crowding) +
  congestion-concentration (value — that load re-weighted by disclosed regime importance, the
  rectified stewardship surface). Bearers with no incident hazard are dropped (0 volume)."
  [g]
  (let [{:keys [nodes edges]} g
        congestion (get (an/analyze nodes edges) "congestion")
        raw (reduce (fn [m e]
                      (if (contains? an/hazard-kinds (get e ":en/kind"))
                        (update m (get e ":en/to") (fnil + 0.0)
                                (double (or (get e ":en/orbit-load") 0.0)))
                        m))
                    {} edges)]
    (->> raw
         (remove (fn [[_ load]] (zero? (double load))))
         (mapv (fn [[bearer load]]
                 {"bearer" bearer
                  "raw_load" (double load)
                  "congestion" (double (get congestion bearer 0.0))})))))

(defn config
  "The gate-adapter config for hoshimori's orbital-stewardship synthesis. volume = raw incident
  hazard (the SCATTERED orbital crowding hoshimori rectifies), value = congestion-concentration
  (hazard-load × regime-weight — the rectified order, concentrated onto the most-congested
  regimes), cost = flat, risk = 0 (observation + stewardship routing; shell-aggregate, never a
  targeting aid / target-list)."
  [g]
  {:actor "hoshimori" :id-prefix "hoshimori-" :source-kind "orbital"
   :rows (bearer-rows g)
   :route-key "bearer"
   :volume-fn #(double (get % "raw_load"))
   :value-fn  #(* (double (get % "congestion")) ga/default-value-scale)})

(defn flow-events-from-graph [g] (ga/flow-events (config g)))
(defn flow-state-from-graph  [g] (ga/flow-state (config g)))

#?(:clj
   (defn flow-state
     "Load the orbit seed graph and fold hoshimori's hazard→stewardship rectification through
     the SHARED ie-flow metrics → the order calculus. :clj (file load)."
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
         (let [r (record-flow! seed {:tx-id "hoshimori-ie-flow" :as-of "beat"})]
           (println (str "recorded " (:events r) " ie-flow events → " (:flow-log r))))))))

#?(:clj
   (when (= *file* (System/getProperty "babashka.file"))
     (apply -main *command-line-args*)))
