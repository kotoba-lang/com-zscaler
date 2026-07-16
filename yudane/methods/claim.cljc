#!/usr/bin/env bb
;; 委 yudane — claim emitter (the 澪 mio suite seam).
(ns yudane.methods.claim
  "claim.cljc — 委 yudane → 澪 mio flow-improvement CLAIM emitter (Energy Order
  Protocol R1 seam). Each CONSENTED, content-free aggregate intention offer becomes
  a mio claim: the realized aggregate flex, baselined against fixed-schedule
  operation. Pure data — does not import mio.

  PRIVACY-PRESERVING SEAM: only CONSENTED offers emit claims, and the claim carries
  only the aggregate (cohort/flex) — never per-person data (content-free, G2)."
  (:require [yudane.methods.analyze :as a]))

(defn claims
  "yudane analysis → vector of 澪 mio flow-improvement claim maps (one per CONSENTED
  offer only). Refused offers emit nothing (consent-bound seam)."
  [analysis]
  (->> (get analysis "offers")
       (filter #(= :consented (get % "verdict")))
       (mapv (fn [r]
               {:type :claim
                :id (str "yudane-" (get r "id"))
                :name (str "consented aggregate flex: " (get r "name"))
                :flow-class :intention
                :source-actor "yudane"
                :order-delta-kwh (get r "flex_kwh")
                :baseline-method "counterfactual: fixed-schedule operation ignoring consented aggregate intention (aggregate kWh baseline)"
                :additionality 0.7
                :measurement-source :signed-meter
                :double-count-key (str "yudane:" (get r "id"))
                :leakage 0.1}))))

(defn from-offers
  "Convenience: offers → claims (runs analyze)."
  [offers]
  (claims (a/analyze offers)))
