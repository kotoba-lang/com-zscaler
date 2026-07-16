(ns jinushi.methods.confidence
  "jinushi 地主 — source RELIABILITY (信頼度) model + trust-weighted conflict resolution.

  As jinushi ingests from ALL sources (WDQS, government open-data cadastres, GLEIF, OSM, …) the
  same parcel/building/owner can be described by several, sometimes disagreeing. This module
  assigns each source a documented TRUST tier and resolves conflicts by trust (highest-trust
  assertion wins; disagreement is recorded, never silently dropped — G2).

  Trust is about PROVENANCE QUALITY, not about any owner: an authoritative public registry
  (gov cadastre / GLEIF) is more reliable than a curated-crowd KG (Wikidata) which is more
  reliable than open-crowd mapping (OSM) which is more reliable than web extraction."
  (:require [clojure.string :as str]))

;; source-id → {:tier :score 0..1 :note}
(def source-trust
  {:nyc-pluto      {:tier :authoritative-gov      :score 0.95 :note "NYC gov cadastre (Socrata, public domain), official BBL"}
   :registry-api   {:tier :authoritative-gov      :score 0.95 :note "official land-registry API"}
   :dvf            {:tier :authoritative-gov      :score 0.95 :note "FR DVF (DGFiP/Etalab) — official transaction record"}
   :gleif          {:tier :authoritative-registry :score 0.95 :note "GLEIF — official ISO-17442 LEI register"}
   :wikidata       {:tier :curated-crowd          :score 0.70 :note "Wikidata — curated but crowd-sourced (P-claims)"}
   :municipality-notice {:tier :official-notice    :score 0.80 :note "municipal public notice"}
   :osm            {:tier :open-crowd             :score 0.60 :note "OpenStreetMap (ODbL) — open crowd mapping"}
   :overture       {:tier :open-crowd             :score 0.65 :note "Overture Maps — conflated open footprints"}
   :commoncrawl    {:tier :web-extracted          :score 0.40 :note "extracted from public web pages (noisy)"}})

(def default-trust {:tier :unknown :score 0.30 :note "unrecorded source — low default trust (honest)"})

(defn trust [source-kw] (get source-trust source-kw default-trust))
(defn trust-score [source-kw] (:score (trust source-kw)))

(defn record-confidence
  "Combine SOURCE trust with FIELD completeness for one record. A record from a trusted source
  with the key fields present scores high; a sparse record from an unknown source scores low.
  Returns a 0..1 confidence + the components (transparent, G2)."
  [{:keys [source fields-present fields-expected]
    :or {fields-present 0 fields-expected 1}}]
  (let [st (trust-score source)
        completeness (if (pos? fields-expected)
                       (/ (double (min fields-present fields-expected)) fields-expected)
                       1.0)
        conf (* st (+ 0.5 (* 0.5 completeness)))]   ;; completeness scales trust within [0.5,1]×trust
    {:confidence conf :source-trust st :completeness completeness}))

(defn resolve-conflict
  "Given several assertions of the SAME attribute from different sources, pick the highest-trust
  value; record whether sources disagreed. Each assertion = {:source :value}.
  Returns {:value :source :trust :agreement :n-sources :disagreed?}."
  [assertions]
  (when (seq assertions)
    (let [ranked (sort-by (comp - trust-score :source) assertions)
          winner (first ranked)
          distinct-vals (distinct (map :value assertions))
          disagreed? (> (count distinct-vals) 1)]
      {:value (:value winner) :source (:source winner) :trust (trust-score (:source winner))
       :n-sources (count assertions) :disagreed? disagreed?
       :agreement (/ (double (count (filter #(= (:value %) (:value winner)) assertions)))
                     (count assertions))})))

(defn reliability-tier
  "Bucket a confidence score for reporting."
  [conf]
  (cond (>= conf 0.85) :high (>= conf 0.6) :medium (>= conf 0.4) :low :else :very-low))

(defn report
  "Summary of source mix + confidence over a seq of records carrying a :source keyword."
  [records]
  (let [by-source (frequencies (map :source records))
        scored (map (fn [r] (:confidence (record-confidence
                                          {:source (:source r) :fields-present 2 :fields-expected 2}))) records)
        tiers (frequencies (map reliability-tier scored))]
    {:total (count records)
     :by-source by-source
     :by-source-trust (into (sorted-map) (map (fn [[s n]] [s {:n n :trust (trust-score s) :tier (:tier (trust s))}]) by-source))
     :tiers tiers
     :mean-trust (if (seq records) (/ (reduce + (map (comp trust-score :source) records)) (count records)) 0.0)}))
