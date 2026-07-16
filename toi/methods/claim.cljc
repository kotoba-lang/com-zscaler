#!/usr/bin/env bb
;; 樋 toi — claim emitter (the 澪 mio suite seam).
(ns toi.methods.claim
  "claim.cljc — 樋 toi → 澪 mio flow-improvement CLAIM emitter (Energy Order Protocol
  R1 seam). Each compute ROUTING becomes a mio claim: the routed compute energy
  (kWh) ordered onto a clean/cool/heat-reusing site, baselined against a warm
  grid-average node, measured by scheduler + carbon-API audit. Pure data — does not
  import mio."
  (:require [toi.methods.analyze :as a]))

(defn claims
  "toi analysis → vector of 澪 mio flow-improvement claim maps (one per routing)."
  [analysis]
  (mapv (fn [r]
          {:type :claim
           :id (str "toi-" (get r "job") "-" (get r "site"))
           :name (str "compute routed: " (get r "job") " → " (get r "site"))
           :flow-class :compute-routing
           :source-actor "toi"
           :order-delta-kwh (get r "kwh")
           :baseline-method "counterfactual: same job on a warm grid-average node (carbon-intensity delta, scheduler + carbon-API audit)"
           :additionality 0.7
           :measurement-source :third-party-audit
           :double-count-key (str "toi:" (get r "job") "@" (get r "site"))
           :leakage 0.05})
        (get analysis "routes")))

(defn from-nodes
  "Convenience: jobs + sites → claims (runs analyze)."
  [jobs sites]
  (claims (a/analyze jobs sites)))
