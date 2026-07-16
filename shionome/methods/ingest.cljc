(ns shionome.methods.ingest
  "ingest.cljc — 潮目 (shionome) offline public-source normalizer. ADR-2606072200.
  Clojure port of methods/ingest.py (1:1).

  Normalizes batches of public market-data records into shionome :bucket/:flow/:snap datoms.
  Every normalized record is run through the same weave validate-* gates, so an under-sourced,
  a NaN-magnitude, or a TRADE/advisory-bearing input is REFUSED here (トレードはしない), not
  silently ingested. Unknown raw fields are carried through as :bucket/<field> so the
  validate-bucket PII / rating scan (G1/G2/G4/G9) bites on the ingest path.

  Live ingest is G8-gated (operator attestation + SHIONOME_ALLOW_LIVE=1); the gate value is
  injectable (defaults to the env var) so it is testable without env mutation. Depends on the
  already-ported same-actor registry (sourcing-for) + weave (validate-*). stdlib only."
  (:require [clojure.string :as str]
            [shionome.methods.registry :as registry]
            [shionome.methods.weave :as weave]))

(def ^:private known-bucket-fields
  #{"id" "scope" "label" "asset_class" "region" "risk" "sources" "sourcing" "sourceId"})

(defn- colon [v] (str ":" (str/replace (str v) #"^:+" "")))
(defn- nonblank [xs] (vec (filter #(not (str/blank? (str %))) xs)))

(defn- sourcing*
  "G11 — a registry sourceId's verification status WINS (a caller cannot forge :authoritative);
  else honor the caller's declared sourcing, default :representative."
  [raw]
  (if (get raw "sourceId")
    (registry/sourcing-for (get raw "sourceId"))
    (colon (get raw "sourcing" "representative"))))

(defn normalize-bucket
  "Normalize a capital-bucket record → validated :bucket/* datom (raises on G1/G2/G4/G9).
  Extra raw fields are carried through so a smuggled PII / rating field is caught."
  [raw]
  (let [b (cond-> {":bucket/id" (get raw "id")
                   ":bucket/scope" (colon (get raw "scope" ""))
                   ":bucket/sourcing" (sourcing* raw)}
            (get raw "label")       (assoc ":bucket/label" (get raw "label"))
            (get raw "asset_class") (assoc ":bucket/asset-class" (get raw "asset_class"))
            (get raw "region")      (assoc ":bucket/region" (get raw "region"))
            (get raw "risk")        (assoc ":bucket/risk" (colon (get raw "risk")))
            (get raw "sources")     (assoc ":bucket/sources" (nonblank (get raw "sources"))))
        ;; carry unknown raw fields through → surfaces PII / rating / signal / target keys
        b (reduce (fn [m [k v]] (if (contains? known-bucket-fields k) m (assoc m (str ":bucket/" k) v)))
                  b raw)]
    (weave/validate-bucket b)
    b))

(defn normalize-flow
  "Normalize a capital-flow record → validated :flow/* datom (raises on a gate)."
  [raw]
  (let [f {":flow/id" (get raw "id")
           ":flow/source" (get raw "source" "external")
           ":flow/target" (get raw "target" "external")
           ":flow/kind" (colon (get raw "kind"))
           ":flow/magnitude" (double (get raw "magnitude" 0.0))
           ":flow/unit" (get raw "unit" "")
           ":flow/no-trade-notice" true
           ":flow/as-of" (long (get raw "as_of" 0))
           ":flow/sourcing" (sourcing* raw)
           ":flow/sources" (nonblank (get raw "sources" []))}]
    (weave/validate-flow f)
    f))

(defn normalize-snapshot
  "Normalize an observed bucket metric → validated :snap/* datom (raises on a gate)."
  [raw]
  (let [s {":snap/id" (get raw "id")
           ":snap/bucket" (get raw "bucket")
           ":snap/metric" (colon (get raw "metric"))
           ":snap/value" (double (get raw "value" 0.0))
           ":snap/as-of" (long (get raw "as_of" 0))
           ":snap/sourcing" (sourcing* raw)
           ":snap/sources" (nonblank (get raw "sources" []))}]
    (weave/validate-snapshot s)
    s))

(defn normalize-batch
  "Normalize a mixed offline batch into shionome datoms. Each record validated."
  [batch]
  {"buckets" (mapv normalize-bucket (get batch "buckets" []))
   "flows" (mapv normalize-flow (get batch "flows" []))
   "snapshots" (mapv normalize-snapshot (get batch "snapshots" []))})

(defn ingest-live
  "G8 — live ingest from market-data sources is outward-gated. Refuses unless the operator gate
  is set (SHIONOME_ALLOW_LIVE=1, injectable as :allow-live). Still routes to Council Lv6+."
  [& {:keys [allow-live] :or {allow-live #?(:clj (System/getenv "SHIONOME_ALLOW_LIVE") :cljs nil)}}]
  (when (not= "1" allow-live)
    (throw (ex-info (str "shionome R0: live market-data ingest is Council Lv6+ + operator gated "
                         "(G8). Set SHIONOME_ALLOW_LIVE=1 + supply an operator attestation DID to "
                         "proceed (still Council-gated).") {:gate "G8"})))
  (throw (ex-info "shionome R0: live ingest path not wired — design-only (G8)." {:gate "G8"})))
