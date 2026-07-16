(ns shionome.methods.export
  "export.cljc — 潮目 (shionome) → kanae render payload. ADR-2606072200.
  Clojure port of methods/export.py (1:1).

  kanae (鼎) renders fund flows (Sankey/treemap). shionome emits its
  capital-movement :flow datoms in kanae fundFlowEdge shape and packages the
  aggregate concentration into a JSON-safe render payload.

  Honest scope (G11 + G2): only CAPITAL-MOVEMENT flow kinds are exported as fund
  flows (rotation / fund-inflow / fund-outflow / fx-flow). Observation-only kinds
  (cross-correlation / price-move / volume-shift / yield-shift) are in other units
  (zscore / pct / bps), NOT capital amounts, so they are excluded and reported as a
  skip count (no silent drop). Every payload carries isMirror + noTrade flags.
  Offline, deterministic; no live publish (G8).

  render-json uses Python json.dumps(sort_keys=True); the suite only round-trips +
  substring-checks the JSON (never byte-compares), so cheshire's serialization is
  faithful for the oracle — the ordered-map values concentration returns are plain
  maps (insertion-order tracked via metadata) and serialize transparently."
  (:require [shionome.methods.weave :as weave]
            [clojure.string :as str]
            #?(:clj [cheshire.core :as json])))

(defn- kw*
  "Normalize an edn keyword/string to a bare lowercase token (':flow/kind' → 'kind').
  Mirror of weave's private `kw*` (Python `export` imports `_kw` from weave; the
  behavior, not the symbol, is what the cross-language oracle pins)."
  [v]
  (let [s (-> (str (or v "")) (str/replace #"^:+" ""))]
    (-> (last (str/split s #"/" -1)) (str/lower-case))))

(defn to-kanae-flow
  "One shionome capital-movement :flow → one kanae fundFlowEdge. Raises if the kind
  is an observation-only kind (not a capital amount)."
  [f]
  (let [kind (kw* (get f ":flow/kind"))]
    (when-not (some #(= % kind) weave/CAPITAL-MOVEMENT-KINDS)
      (throw (ex-info (str "export: " (pr-str kind)
                           " is an observation, not a capital flow (excluded from kanae render)")
                      {:kind kind})))
    {"edgeId"    (str "shionome:" (get f ":flow/id" "?"))
     "flowType"  kind
     "donor"     (get f ":flow/source" "")
     "recipient" (get f ":flow/target" "")
     "amount"    (double (get f ":flow/magnitude" 0.0))
     "currency"  (get f ":flow/unit" "")
     "asOf"      (long (get f ":flow/as-of" 0))
     "noTrade"   true
     "sources"   (vec (get f ":flow/sources" []))}))

(defn to-kanae-flows
  "All capital-movement :flow → kanae flows; observation-only kinds skipped + counted."
  [g]
  (let [in-kinds? (fn [f] (some #(= % (kw* (get f ":flow/kind"))) weave/CAPITAL-MOVEMENT-KINDS))
        flows  (vec (get g "flows"))
        kept   (filterv in-kinds? flows)
        skip   (remove in-kinds? flows)]
    {"flows"         (mapv to-kanae-flow kept)
     "skipped"       (mapv #(get % ":flow/id") skip)
     "skipped_count" (count skip)}))

(defn render-payload
  "JSON-safe aggregate concentration for a kanae render (Sankey/treemap-ready).
  Tuples are flattened to [key value] pairs; no sets remain. Carries the
  mirror/no-trade flags."
  [c]
  {"actor"                "shionome"
   "isMirror"             true
   "noTrade"              true
   "counts"               (into {} (map (fn [k] [k (get c k)])
                                        ["bucket_count" "flow_count" "snapshot_count"]))
   "net_flow_by_bucket"   (get c "net_flow_by_bucket")
   "rotation_pairs"       (get c "rotation_pairs")
   "inflow_shares"        (mapv vec (get-in c ["inflow_concentration" "shares"]))
   "inflow_hhi"           (get-in c ["inflow_concentration" "hhi"])
   "by_asset_class"       (get c "by_asset_class")
   "by_region"            (get c "by_region")
   "regime"               (get c "regime")
   "correlation_clusters" (get c "correlation_clusters")})

#?(:clj
   (defn render-json
     "The render payload as a JSON string (proves it is fully serializable)."
     [c]
     (json/generate-string (render-payload c))))
