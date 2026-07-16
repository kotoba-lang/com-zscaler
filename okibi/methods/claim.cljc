#!/usr/bin/env bb
;; 燠 okibi — claim emitter (the 澪 mio suite seam).
(ns okibi.methods.claim
  "claim.cljc — 燠 okibi → 澪 mio flow-improvement CLAIM emitter (Energy Order
  Protocol R1 seam). Each realized thermal MATCH becomes a mio claim: delivered
  waste heat, measured by a signed BTU meter, baselined against heat rejected to
  ambient. Pure data — does not import mio.

  Waste heat that would otherwise be dumped is highly additional (0.8)."
  (:require [okibi.methods.analyze :as a]))

(defn claims
  "okibi analysis → vector of 澪 mio flow-improvement claim maps (one per match)."
  [analysis]
  (mapv (fn [m]
          {:type :claim
           :id (str "okibi-" (get m "src") "-" (get m "sink"))
           :name (str "waste heat delivered: " (get m "src") " → " (get m "sink"))
           :flow-class :waste-heat
           :source-actor "okibi"
           :order-delta-kwh (get m "matched_kw")
           :baseline-method "counterfactual: waste heat rejected to ambient; demand met by a separate heat source (BTU-metered)"
           :additionality 0.8
           :measurement-source :signed-meter
           :double-count-key (str "okibi:" (get m "src") "~" (get m "sink"))
           :leakage 0.1})
        (get analysis "matches")))

(defn from-nodes
  "Convenience: sources + sinks → claims (runs analyze)."
  [sources sinks]
  (claims (a/analyze sources sinks)))
