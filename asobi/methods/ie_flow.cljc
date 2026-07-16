#!/usr/bin/env bb
;; asobi 遊び — ie-flow embedding (the SoS scoring leg) via the shared gate-adapter.
(ns asobi.methods.ie-flow
  "ie_flow.cljc — asobi 遊び embeds the information-energy flow lifecycle (etzhayyim.ie-flow,
  ADR-2606211200 + score ADR-2606212200) via the SHARED `etzhayyim.ie-flow.gate-adapter`
  (not a fork). asobi is an INFORMATION-CONTROL ACTOR for the telos of labour liberation —
  freed-time / play / cultural expression: the raw ACCESS provided across works/events/venues
  (open-access / teaching / participation / hosting / performing) is scattered cultural supply;
  asobi's edge-primary analyzer is a RECTIFIER (整流) that folds that raw access onto the works
  that matter most — re-weighting each bearer's incident access by its DISCLOSED openness
  importance, CONCENTRATING participation-openness onto the most-open cultural bearers, routed
  to OPENING (widening access vs enclosure).

  source = the cultural bearer (a work/event/venue node), route = the bearer itself (each is its
  own opening outcome), volume = raw incident access-provision load (the SCATTERED cultural
  supply), value = openness = Σ(access-load × access-weight) · scale (the rectified order —
  access re-weighted by openness importance). risk = 0 — asobi OBSERVES + routes to opening;
  an ACCESS map, NEVER an engagement/popularity ranking (no retention metric; no-addictive-design,
  Wellbecoming §1.13); edge-primary karma (N1/G2). Ledger: per-actor ie-flow record
  (80-data/ie-flow/asobi/, gitignored)."
  (:require [asobi.methods.analyze :as an]
            [etzhayyim.ie-flow.gate-adapter :as ga]
            [etzhayyim.ie-flow.metrics :as iem]
            [clojure.string :as str]))

(def default-seed "20-actors/asobi/data/seed-asobi-graph.kotoba.edn")

(defn- bearer-rows
  "Per-bearer rows: raw inbound access-provision load (volume — scattered cultural supply) +
  openness-concentration (value — that access re-weighted by disclosed openness importance, the
  rectified opening surface). Bearers with no incident access are dropped (0 volume)."
  [g]
  (let [{:keys [nodes edges]} g
        openness (get (an/analyze nodes edges) "openness")
        raw (reduce (fn [m e]
                      (if (contains? an/opening-kinds (get e ":en/kind"))
                        (update m (get e ":en/to") (fnil + 0.0)
                                (double (or (get e ":en/access-load") 0.0)))
                        m))
                    {} edges)]
    (->> raw
         (remove (fn [[_ load]] (zero? (double load))))
         (mapv (fn [[bearer load]]
                 {"bearer" bearer
                  "raw_load" (double load)
                  "openness" (double (get openness bearer 0.0))})))))

(defn config
  "The gate-adapter config for asobi's access-opening synthesis. volume = raw incident access
  (the SCATTERED cultural supply asobi rectifies), value = openness-concentration (access-load ×
  access-weight — the rectified order, concentrated onto the most-open cultural bearers),
  cost = flat, risk = 0 (observation + opening routing; an access map, never an engagement/
  popularity ranking)."
  [g]
  {:actor "asobi" :id-prefix "asobi-" :source-kind "work"
   :rows (bearer-rows g)
   :route-key "bearer"
   :volume-fn #(double (get % "raw_load"))
   :value-fn  #(* (double (get % "openness")) ga/default-value-scale)})

(defn flow-events-from-graph [g] (ga/flow-events (config g)))
(defn flow-state-from-graph  [g] (ga/flow-state (config g)))

#?(:clj
   (defn flow-state
     "Load the asobi seed graph and fold asobi's access→opening rectification through the
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
         (let [r (record-flow! seed {:tx-id "asobi-ie-flow" :as-of "beat"})]
           (println (str "recorded " (:events r) " ie-flow events → " (:flow-log r))))))))

#?(:clj
   (when (= *file* (System/getProperty "babashka.file"))
     (apply -main *command-line-args*)))
