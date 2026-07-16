#!/usr/bin/env bb
;; ugachi 穿ち — ie-flow embedding (the SoS scoring leg) via the shared gate-adapter.
(ns ugachi.methods.ie-flow
  "ie_flow.cljc — ugachi 穿ち embeds the information-energy flow lifecycle
  (etzhayyim.ie-flow, ADR-2606211200 + score ADR-2606212200) via the SHARED
  `etzhayyim.ie-flow.gate-adapter` (not a fork). ugachi is an INFORMATION-CONTROL
  ACTOR: the scattered §2(l) multi-generational RISK across many proposed projects
  is high-entropy disorder; ugachi's gate is a RECTIFIER (整流) that folds that risk
  onto stewardship VERDICTS — concentrating realised stewardship order onto the
  projects it can permit-design (:propose-r0) or route to recovery
  (:route-to-recovery → kanayama), while REFUSING the catastrophic/monopolistic ones
  (protective order: disorder prevented, 0 stewardship-energy).

  Assessment-only — ugachi never digs (G1). The flow ledger is the per-actor ie-flow
  record (80-data/ie-flow/ugachi/, gitignored)."
  (:require [ugachi.methods.ugachi-edn :as ue]
            [ugachi.methods.gate :as gate]
            [etzhayyim.ie-flow.gate-adapter :as ga]
            [etzhayyim.ie-flow.metrics :as iem]
            [clojure.string :as str]
            #?(:clj [clojure.edn :as edn])))

(def ^:private value-scale ga/default-value-scale)

(defn- route-factor
  "Fraction of a project's multi-gen risk that this verdict rectifies into realised
  STEWARDSHIP order. :propose-r0 delivers the most (a permitted, designed, low-risk,
  consented, Transparent-Force project); :route-to-recovery routes to kanayama
  (recovery-first); :refuse exports PROTECTIVE order only (a catastrophic/monopolistic
  project rejected — disorder prevented, not stewardship-energy); :insufficient-evidence
  awaits."
  [verdict]
  (case verdict
    :propose-r0            0.8
    :route-to-recovery     0.5
    :refuse                0.0
    :insufficient-evidence 0.1
    0.1))

(defn config
  "The gate-adapter config for ugachi's §2(l) assessment (the domain model; the shared
  helper does the event/metric/record plumbing). source = the project, route = the verdict,
  volume = multigen-risk (the scattered risk ugachi rectifies), value = risk·route-factor·scale,
  cost = flat, risk = 0 (assessment-only — ugachi never digs)."
  [projects]
  {:actor "ugachi" :id-prefix "ugachi-" :source-kind "project"
   :rows (get (gate/assess projects) "projects")
   :route-key "verdict"
   :volume-fn #(double (get % "multigen_risk"))
   :value-fn #(* (double (get % "multigen_risk")) (route-factor (get % "verdict")) value-scale)})

(defn flow-events [projects] (ga/flow-events (config projects)))
(defn flow-state  [projects] (ga/flow-state (config projects)))

#?(:clj
   (defn record-flow!
     "Record ugachi's measured ie-flow EVENTS to the shared per-actor ledger via the
     gate-adapter. Returns {:flow-log :events :order-index}."
     ([projects] (record-flow! projects {}))
     ([projects opts] (ga/record-flow! (config projects) opts))))

#?(:clj
   (defn -main [& args]
     (let [flags (set (filter #(str/starts-with? % "--") args))
           seed (or (first (remove #(str/starts-with? % "--") args)) "20-actors/ugachi/kotoba/seed.edn")
           projects (ue/projects seed)
           st (flow-state projects)]
       (println (iem/summary-line st))
       (when (contains? flags "--record")
         (let [r (record-flow! projects {:tx-id "ugachi-ie-flow" :as-of "beat"})]
           (println (str "recorded " (:events r) " ie-flow events → " (:flow-log r))))))))

#?(:clj
   (when (= *file* (System/getProperty "babashka.file"))
     (apply -main *command-line-args*)))
