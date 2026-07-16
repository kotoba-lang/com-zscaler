#!/usr/bin/env bb
;; shionome 潮目 — ie-flow embedding (the SoS scoring leg) via the shared gate-adapter.
(ns shionome.methods.ie-flow
  "ie_flow.cljc — shionome 潮目 embeds the information-energy flow lifecycle
  (etzhayyim.ie-flow, ADR-2606211200 + score ADR-2606212200) via the SHARED
  `etzhayyim.ie-flow.gate-adapter` (not a fork). shionome is an INFORMATION-CONTROL
  ACTOR in the literal sense: gross capital CHURN (inflow + outflow) sloshing across
  asset/region buckets is high-entropy scattered movement; shionome's weave is a
  RECTIFIER (整流) that folds that churn into the few buckets where capital POOLS — the
  net-accumulation (inflow > outflow), which is exactly the inflow-concentration / regime
  it reports. The realised order is the net ACCUMULATION (capital concentrating into the
  pools, lower-entropy than the spread churn); the distributing buckets are the scattered
  source (net-out = 0 realised order). value ≤ volume always.

  トレードはしない (G2): the value is an OBSERVED concentration signal, never a trade
  instruction / target / position. shionome's gate has no refuse/capture route — pure
  mirror, non-adjudicating. The flow ledger is the per-actor ie-flow record
  (80-data/ie-flow/shionome/, gitignored)."
  (:require [shionome.methods.edn :as e]
            [shionome.methods.weave :as w]
            [etzhayyim.ie-flow.gate-adapter :as ga]
            [etzhayyim.ie-flow.metrics :as iem]
            [clojure.string :as str]))

(def default-seed "20-actors/shionome/data/seed-capital-flow-graph.kotoba.edn")

(defn config
  "The gate-adapter config for shionome's capital-flow observatory (the domain model; the
  shared helper does the event/metric/record plumbing). source = the bucket, route = the
  bucket itself (each bucket is its own concentration outcome — where capital may pool),
  volume = gross churn inflow+outflow (the SCATTERED input shionome rectifies), value =
  max(0,net) (the net ACCUMULATION — capital pooling into the few concentration buckets,
  the order shionome surfaces as inflow-HHI / regime; net-outflow buckets are the scattered
  source = 0 realised order). value ≤ volume always. cost = flat, risk = 0 (observation-only
  — shionome never trades, G2)."
  [g]
  {:actor "shionome" :id-prefix "shionome-" :source-kind "bucket"
   :rows (w/net-flow-by-bucket g)
   :route-key "bucket"
   :volume-fn #(+ (double (get % "inflow")) (double (get % "outflow")))
   :value-fn  #(max 0.0 (double (get % "net")))})

(defn flow-events-from-graph [g] (ga/flow-events (config g)))
(defn flow-state-from-graph  [g] (ga/flow-state (config g)))

#?(:clj
   (defn flow-state
     "Load the capital-flow seed, weave it, and fold shionome's churn→net rectification
     through the SHARED ie-flow metrics → the order calculus. :clj (file load)."
     ([] (flow-state default-seed))
     ([seed-path] (flow-state-from-graph (w/weave (e/load-edn seed-path))))))

#?(:clj
   (defn record-flow!
     "Record shionome's measured ie-flow EVENTS to the shared per-actor ledger via the
     gate-adapter. Returns {:flow-log :events :order-index}."
     ([] (record-flow! default-seed {}))
     ([seed-path opts] (ga/record-flow! (config (w/weave (e/load-edn seed-path))) opts))))

#?(:clj
   (defn -main [& args]
     (let [flags (set (filter #(str/starts-with? % "--") args))
           seed (or (first (remove #(str/starts-with? % "--") args)) default-seed)
           st (flow-state seed)]
       (println (iem/summary-line st))
       (when (contains? flags "--record")
         (let [r (record-flow! seed {:tx-id "shionome-ie-flow" :as-of "beat"})]
           (println (str "recorded " (:events r) " ie-flow events → " (:flow-log r))))))))

#?(:clj
   (when (= *file* (System/getProperty "babashka.file"))
     (apply -main *command-line-args*)))
