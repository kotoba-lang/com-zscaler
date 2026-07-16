#!/usr/bin/env bb
;; 撓 tawami — claim emitter (the 澪 mio suite seam).
(ns tawami.methods.claim
  "claim.cljc — 撓 tawami → 澪 mio flow-improvement CLAIM emitter (Energy Order
  Protocol R1 seam). Converts a tawami analysis into the mio claim shape (the five
  §9 verification facts) so an exercised flexibility can be verified + accounted by
  the backbone. The emitter is pure data — it does NOT import mio; it emits the
  agreed claim map and mio.analyze verifies it.

  R0/representative: every mapped asset emits a candidate claim for its exercised
  flexibility; additionality reflects the asset's tier (faster/more-responsive flex
  is more clearly additional). Measurement is a signed meter (operator G7 ingest)."
  (:require [tawami.methods.analyze :as a]))

(defn- additionality-for [tier]
  (case tier :fast-flex 0.8 :mid-flex 0.6 :slow-flex 0.4 0.5))

(defn claims
  "tawami analysis → vector of 澪 mio flow-improvement claim maps (one per asset)."
  [analysis]
  (mapv (fn [r]
          {:type :claim
           :id (str "tawami-" (get r "id"))
           :name (str "flexibility exercised: " (get r "name"))
           :flow-class (get r "best_use")
           :source-actor "tawami"
           :order-delta-kwh (get r "flex_value")
           :baseline-method (str "counterfactual: " (name (get r "resource_class"))
                                 " load at uncontrolled timing (metered baseline)")
           :additionality (additionality-for (get r "tier"))
           :measurement-source :signed-meter
           :double-count-key (str "tawami:" (get r "id"))
           :leakage 0.05})
        (get analysis "assets")))

(defn from-assets
  "Convenience: assets → claims (runs analyze)."
  [assets]
  (claims (a/analyze assets)))
