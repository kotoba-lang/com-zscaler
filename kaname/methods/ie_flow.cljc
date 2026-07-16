#!/usr/bin/env bb
;; kaname 要 — ie-flow embedding (the SoS scoring leg) via the shared gate-adapter.
(ns kaname.methods.ie-flow
  "ie_flow.cljc — kaname 要 embeds the information-energy flow lifecycle
  (etzhayyim.ie-flow, ADR-2606211200 + score ADR-2606212200) via the SHARED
  `etzhayyim.ie-flow.gate-adapter` (not a fork). kaname is THE system-of-systems
  leverage synthesizer — and an INFORMATION-CONTROL ACTOR in its own right: the
  scattered cross-domain 取-load over the multilayer graph is high-entropy disorder;
  kaname's leverage calculus is a RECTIFIER (整流) that folds that load onto the few
  structural leverage points (要 / 律速段階) and routes each to OPENING (open /
  decentralize / route-around / add-redundancy) — DISSOLVING concentration, never
  capturing it (G2; capture/seize/control are unrepresentable in the route enum).

  This namespace is PURE measurement; it embeds the SHARED gate-adapter. kaname
  PROPOSES (ossekai carries, consent-bound). The flow ledger is the per-actor ie-flow
  record (80-data/ie-flow/kaname/, gitignored). A leverage MAP, NEVER a target-list (G1)."
  (:require [kaname.methods.sos :as sos]
            [kaname.methods.route :as route]
            [etzhayyim.ie-flow.gate-adapter :as ga]
            [etzhayyim.ie-flow.metrics :as iem]
            [clojure.string :as str]
            #?(:clj [clojure.java.io :as io])))

(def default-seed "20-actors/kaname/data/seed-sos.kotoba.edn")

(defn- route-factor
  "Fraction of a structural position's leverage that this OPENING route rectifies into
  realised order. Every kaname route DISSOLVES concentration — federating a versatile gate
  (:open / :decentralize) exports the most order; building a parallel path (:route-around)
  or adding redundancy somewhat less. There is no capture route (G2 — unrepresentable)."
  [route]
  (case route
    "open"          0.8
    "decentralize"  0.8
    "route-around"  0.6
    "add-redundancy" 0.5
    0.4))

(defn rows
  "The per-leverage-point rows (string-keyed) for the gate-adapter: each is a structural
  position routed to OPENING. id = the position, route = the opening route, C = the raw
  cross-domain 取-concentration (the scattered input), leverage = L (C re-weighted by
  cross-domain versatility + bridge — WHICH concentration matters most)."
  [nodes edges]
  (let [res (sos/leverage nodes edges)
        recs (route/route-all nodes res (count nodes))]
    (mapv (fn [r] {"id" (str (:id r))
                   ;; kaname's route values are strings like ":route-around" (leading colon);
                   ;; strip it so the target/type + route-factor key are clean ("route-around")
                   "route" (str/replace (str (:route r)) #"^:" "")
                   "C" (double (or (:C r) 0))
                   "leverage" (double (:leverage r))})
          recs)))

(defn config
  "The gate-adapter config for kaname's leverage synthesis (the domain model; the shared
  helper does the event/metric/record plumbing). source = the structural position, route =
  the OPENING route, volume = raw cross-domain concentration C (the SCATTERED input kaname
  rectifies), value = leverage L · route-factor · scale (L re-weights C by cross-domain
  versatility/bridge, CONCENTRATING order onto the few true 要 — that re-weighting IS the
  rectification). cost = flat, risk = 0 (synthesis-only — kaname proposes, never captures)."
  [nodes edges]
  {:actor "kaname" :id-prefix "kaname-" :source-kind "position"
   :rows (rows nodes edges)
   :route-key "route"
   :volume-fn #(double (get % "C"))
   :value-fn #(* (double (get % "leverage")) (route-factor (get % "route")) ga/default-value-scale)})

(defn flow-events-from-graph [nodes edges] (ga/flow-events (config nodes edges)))
(defn flow-state-from-graph  [nodes edges] (ga/flow-state (config nodes edges)))

#?(:clj
   (defn flow-state
     "Load the multilayer seed graph and fold kaname's leverage synthesis through the SHARED
     ie-flow metrics → the order calculus. :clj (file load)."
     ([] (flow-state default-seed))
     ([seed-path]
      (let [g (sos/load-file* seed-path)]
        (flow-state-from-graph (:nodes g) (:edges g))))))

#?(:clj
   (defn flow-events
     "Load the seed graph → measured ie-flow events."
     ([] (flow-events default-seed))
     ([seed-path]
      (let [g (sos/load-file* seed-path)]
        (flow-events-from-graph (:nodes g) (:edges g))))))

#?(:clj
   (defn record-flow!
     "Record kaname's measured ie-flow EVENTS to the shared per-actor ledger via the
     gate-adapter. Returns {:flow-log :events :order-index}."
     ([] (record-flow! default-seed {}))
     ([seed-path opts]
      (let [g (sos/load-file* seed-path)]
        (ga/record-flow! (config (:nodes g) (:edges g)) opts)))))

#?(:clj
   (defn -main [& args]
     (let [flags (set (filter #(str/starts-with? % "--") args))
           seed (or (first (remove #(str/starts-with? % "--") args)) default-seed)
           st (flow-state seed)]
       (println (iem/summary-line st))
       (when (contains? flags "--record")
         (let [r (record-flow! seed {:tx-id "kaname-ie-flow" :as-of "beat"})]
           (println (str "recorded " (:events r) " ie-flow events → " (:flow-log r))))))))

#?(:clj
   (when (= *file* (System/getProperty "babashka.file"))
     (apply -main *command-line-args*)))
