#!/usr/bin/env bb
;; hokorobi 綻び — ie-flow embedding (the SoS scoring leg) via the shared gate-adapter.
(ns hokorobi.methods.ie-flow
  "ie_flow.cljc — hokorobi 綻び embeds the information-energy flow lifecycle (etzhayyim.ie-flow,
  ADR-2606211200 + score ADR-2606212200) via the SHARED `etzhayyim.ie-flow.gate-adapter`
  (not a fork). hokorobi is an INFORMATION-CONTROL ACTOR: the raw systemic-finance RISK
  accumulating across institutions (insurers / banks / pensions) is high-entropy scattered
  fragility; hokorobi's edge-primary analyzer is a RECTIFIER (整流) that folds that raw risk
  onto the institutions that matter most — re-weighting each bearer's incident risk by its
  DISCLOSED systemic-importance (G-SIB / D-SIB / …), CONCENTRATING the systemic-risk surface
  onto the most-systemically-important burdened institutions, routed to RESILIENCE (繕い).

  source = the institution (a finrisk node), route = the institution itself (each is its own
  resilience outcome), volume = raw incident risk load (the SCATTERED systemic fragility),
  value = systemic-risk-concentration = Σ(risk-load × SII-weight) · scale (the rectified order
  — risk re-weighted by systemic importance). risk = 0 — hokorobi OBSERVES + routes to
  resilience; it NEVER trades, never a panic/market signal, never a solvency verdict (a
  resilience map, NEVER a target-list; edge-primary karma, N1/G2 — no score-of-institution).
  The flow ledger is the per-actor ie-flow record (80-data/ie-flow/hokorobi/, gitignored)."
  (:require [hokorobi.methods.analyze :as an]
            [etzhayyim.ie-flow.gate-adapter :as ga]
            [etzhayyim.ie-flow.metrics :as iem]
            [clojure.string :as str]))

(def default-seed "20-actors/hokorobi/data/seed-finrisk-graph.kotoba.edn")

(defn- bearer-rows
  "Per-bearer rows: raw inbound risk load (volume — scattered systemic fragility) +
  systemic-risk-concentration (value — that load re-weighted by disclosed systemic-importance,
  the rectified resilience surface). Bearers with no incident risk are dropped (0 volume)."
  [g]
  (let [{:keys [nodes edges]} g
        systemic (get (an/analyze nodes edges) "systemic")
        raw (reduce (fn [m e]
                      (if (contains? an/risk-kinds (get e ":en/kind"))
                        (update m (get e ":en/to") (fnil + 0.0)
                                (double (or (get e ":en/risk-load") 0.0)))
                        m))
                    {} edges)]
    (->> raw
         (remove (fn [[_ load]] (zero? (double load))))
         (mapv (fn [[bearer load]]
                 {"bearer" bearer
                  "raw_load" (double load)
                  "systemic" (double (get systemic bearer 0.0))})))))

(defn config
  "The gate-adapter config for hokorobi's systemic-risk synthesis (the domain model; the
  shared helper does the event/metric/record plumbing). volume = raw incident risk (the
  SCATTERED systemic fragility hokorobi rectifies), value = systemic-risk-concentration
  (risk-load × SII-weight — the rectified order, concentrated onto the most-systemically-
  important burdened institutions), cost = flat, risk = 0 (observation + resilience routing;
  never a panic signal or solvency verdict, never a target-list)."
  [g]
  {:actor "hokorobi" :id-prefix "hokorobi-" :source-kind "institution"
   :rows (bearer-rows g)
   :route-key "bearer"
   :volume-fn #(double (get % "raw_load"))
   :value-fn  #(* (double (get % "systemic")) ga/default-value-scale)})

(defn flow-events-from-graph [g] (ga/flow-events (config g)))
(defn flow-state-from-graph  [g] (ga/flow-state (config g)))

#?(:clj
   (defn flow-state
     "Load the finrisk seed graph and fold hokorobi's risk→resilience rectification through
     the SHARED ie-flow metrics → the order calculus. :clj (file load)."
     ([] (flow-state default-seed))
     ([seed-path] (flow-state-from-graph (an/load-file* seed-path)))))

#?(:clj
   (defn record-flow!
     "Record hokorobi's measured ie-flow EVENTS to the shared per-actor ledger via the
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
         (let [r (record-flow! seed {:tx-id "hokorobi-ie-flow" :as-of "beat"})]
           (println (str "recorded " (:events r) " ie-flow events → " (:flow-log r))))))))

#?(:clj
   (when (= *file* (System/getProperty "babashka.file"))
     (apply -main *command-line-args*)))
