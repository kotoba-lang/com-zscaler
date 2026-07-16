#!/usr/bin/env bb
;; tsumugi 紡ぎ — ie-flow embedding (the SoS scoring leg) via the shared gate-adapter.
(ns tsumugi.methods.ie-flow
  "ie_flow.cljc — tsumugi 紡ぎ embeds the information-energy flow lifecycle
  (etzhayyim.ie-flow, ADR-2606211200 + score ADR-2606212200) via the SHARED
  `etzhayyim.ie-flow.gate-adapter` (not a fork). tsumugi is an INFORMATION-CONTROL
  ACTOR: the scattered 取-HOLDING across public power-entities (held = the integral
  of incident edges, N1) is high-entropy disorder; tsumugi's spirit-in-physics weave
  is a RECTIFIER (整流) that folds that holding onto RELEASE-priority — concentrating
  realised release order onto the few biggest 取-holders (release-target =
  max(0, held−1)), routed toward RELEASE (解放).

  CONSTITUTIONAL (N1/G2): karma/取 lives ONLY on edges; held is their per-entity
  INTEGRAL (tsumugi computes it). The flow routes power-holding toward RELEASE — a
  release MAP, NEVER a target-list. This namespace is PURE measurement; it embeds the
  SHARED gate-adapter. The flow ledger is the per-actor ie-flow record
  (80-data/ie-flow/tsumugi/, gitignored)."
  (:require [tsumugi.methods.analyze :as an]
            [etzhayyim.ie-flow.gate-adapter :as ga]
            [etzhayyim.ie-flow.metrics :as iem]
            [clojure.string :as str]))

(def default-seed "20-actors/tsumugi/data/seed-power-graph.kotoba.edn")

(defn rows
  "Per-entity rows (string-keyed) for the gate-adapter: each public power-entity routed
  toward RELEASE. id = the entity, held = its 取-holding (edge-integral, the scattered
  input), route = \"release\" (uniform — every holder routes toward 解放, never a target-list)."
  [orgs edges]
  (let [a (an/analyze orgs edges)
        held (get a "held")
        rank (get a "rank")]
    (mapv (fn [k] {"id" (str k)
                   "held" (double (get held k 0))
                   "route" "release"})
          rank)))

(defn config
  "The gate-adapter config for tsumugi's 取-concentration weave (the domain model; the shared
  helper does the event/metric/record plumbing). source = the power-entity, route = release,
  volume = held (the scattered 取-holding tsumugi rectifies), value = release-target =
  max(0, held−1)·scale (the RELEASE order — concentrated on the biggest holders, which is the
  rectification). cost = flat, risk = 0 (observation-only — tsumugi mirrors, never holds)."
  [orgs edges]
  {:actor "tsumugi" :id-prefix "tsumugi-" :source-kind "entity"
   :rows (rows orgs edges)
   :route-key "route"
   :volume-fn #(double (get % "held"))
   :value-fn #(* (max 0.0 (- (double (get % "held")) 1.0)) ga/default-value-scale)})

(defn flow-events-from [orgs edges] (ga/flow-events (config orgs edges)))
(defn flow-state-from  [orgs edges] (ga/flow-state (config orgs edges)))

#?(:clj
   (defn flow-state
     "Load the power-graph seed and fold tsumugi's 取-concentration weave through the SHARED
     ie-flow metrics → the order calculus."
     ([] (flow-state default-seed))
     ([seed-path] (let [[orgs edges] (an/load seed-path)] (flow-state-from orgs edges)))))

#?(:clj
   (defn flow-events
     ([] (flow-events default-seed))
     ([seed-path] (let [[orgs edges] (an/load seed-path)] (flow-events-from orgs edges)))))

#?(:clj
   (defn record-flow!
     "Record tsumugi's measured ie-flow EVENTS to the shared per-actor ledger via the
     gate-adapter. Returns {:flow-log :events :order-index}."
     ([] (record-flow! default-seed {}))
     ([seed-path opts]
      (let [[orgs edges] (an/load seed-path)]
        (ga/record-flow! (config orgs edges) opts)))))

#?(:clj
   (defn -main [& args]
     (let [flags (set (filter #(str/starts-with? % "--") args))
           seed (or (first (remove #(str/starts-with? % "--") args)) default-seed)
           st (flow-state seed)]
       (println (iem/summary-line st))
       (when (contains? flags "--record")
         (let [r (record-flow! seed {:tx-id "tsumugi-ie-flow" :as-of "beat"})]
           (println (str "recorded " (:events r) " ie-flow events → " (:flow-log r))))))))

#?(:clj
   (when (= *file* (System/getProperty "babashka.file"))
     (apply -main *command-line-args*)))
