#!/usr/bin/env bb
;; busshi 物資 — ie-flow embedding (the SoS scoring leg) via the shared gate-adapter.
(ns busshi.methods.ie-flow
  "ie_flow.cljc — busshi 物資 embeds the information-energy flow lifecycle
  (etzhayyim.ie-flow, ADR-2606211200 + score ADR-2606212200) via the SHARED
  `etzhayyim.ie-flow.gate-adapter` (not a fork). busshi is an INFORMATION-CONTROL
  ACTOR: the scattered §2(l) multi-generational RISK borne across many commodities is
  high-entropy disorder; busshi's observation is a RECTIFIER (整流) that folds that
  risk onto RESILIENCE routes — concentrating realised order onto the commodities
  whose concentration (:de-monopolization) or footprint (:restoration) most needs
  routing, leaving the diffuse low-risk ones at baseline (:resilience).

  OBSERVATION-ONLY — busshi never trades and never mines; it moves INFORMATION-energy
  (a resilience map), never physical commodity. The flow ledger is the per-actor
  ie-flow record (80-data/ie-flow/busshi/, gitignored). The map is routed to RESILIENCE,
  NEVER a target-list."
  (:require [busshi.methods.busshi-edn :as be]
            [busshi.methods.analyze :as an]
            [etzhayyim.ie-flow.gate-adapter :as ga]
            [etzhayyim.ie-flow.metrics :as iem]
            [clojure.string :as str]
            #?(:clj [clojure.edn :as edn])))

(def ^:private value-scale ga/default-value-scale)

(defn- route-factor
  "Fraction of a commodity's multi-gen risk that this resilience route rectifies into
  realised order. A clear monopoly chokepoint (:de-monopolization) or a clear footprint
  (:restoration) is high-value order (a route-around / stewardship target identified);
  diffuse baseline (:resilience) is low. busshi never punishes — every route is order
  routed to RESILIENCE, never a target-list."
  [route]
  (case route
    :de-monopolization 0.8
    :restoration       0.7
    :resilience        0.3
    0.3))

(defn config
  "The gate-adapter config for busshi's §2(l) commodity observation (the domain model; the
  shared helper does the event/metric/record plumbing). source = the commodity, route = the
  resilience route, volume = multigen-risk (the scattered risk busshi rectifies), value =
  risk·route-factor·scale, cost = flat, risk = 0 (observation-only — never trades/mines)."
  [commodities]
  {:actor "busshi" :id-prefix "busshi-" :source-kind "commodity"
   :rows (get (an/analyze commodities) "commodities")
   :route-key "route"
   :volume-fn #(double (get % "multigen_risk"))
   :value-fn #(* (double (get % "multigen_risk")) (route-factor (get % "route")) value-scale)})

(defn flow-events [commodities] (ga/flow-events (config commodities)))
(defn flow-state  [commodities] (ga/flow-state (config commodities)))

#?(:clj
   (defn record-flow!
     "Record busshi's measured ie-flow EVENTS to the shared per-actor ledger via the
     gate-adapter. Returns {:flow-log :events :order-index}."
     ([commodities] (record-flow! commodities {}))
     ([commodities opts] (ga/record-flow! (config commodities) opts))))

#?(:clj
   (defn -main [& args]
     (let [flags (set (filter #(str/starts-with? % "--") args))
           seed (or (first (remove #(str/starts-with? % "--") args)) "20-actors/busshi/kotoba/seed.edn")
           commodities (be/commodities seed)
           st (flow-state commodities)]
       (println (iem/summary-line st))
       (when (contains? flags "--record")
         (let [r (record-flow! commodities {:tx-id "busshi-ie-flow" :as-of "beat"})]
           (println (str "recorded " (:events r) " ie-flow events → " (:flow-log r))))))))

#?(:clj
   (when (= *file* (System/getProperty "babashka.file"))
     (apply -main *command-line-args*)))
