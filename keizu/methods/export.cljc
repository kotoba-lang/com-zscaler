(ns keizu.methods.export
  "export.cljc — 系図 (keizu) → kanae render payload. ADR-2606066000.
  1:1 Clojure port of `methods/export.py` (same house style as weave/analyze).

  The manifest promise: \"keizu emits the relation/:money datoms kanae visualizes.\" This is the
  outbound side of bridge.py: it maps keizu fiscal `:money` flows into kanae fundFlowEdge shape
  and packages the aggregate concentration into a JSON-safe render payload (Sankey/treemap-ready).

  Honest scope (G11 + G2): only kanae-representable FISCAL kinds are exported as fund flows
  (procurement / subsidy / grant / outlay). `:political-donation` is NOT a government fiscal flow,
  so it is excluded from the kanae payload and reported as a skip count (no silent drop). Offline,
  deterministic; no live publish (G8).

  Requires the merged keizu weave ns for `kw*` (the Python `from weave import _kw`). House style:
  closed-vocab / gate violations throw ex-info; pure fns; file I/O at the #?(:clj) edge."
  (:require [clojure.string :as str]
            [keizu.methods.weave :as w]
            #?(:clj [keizu.methods.edn :as kedn])))

;; `_kw` is private (`kw*`) in weave.cljc — reach it via the var (Python `from weave import _kw`).
(def ^:private kw* @#'w/kw*)

;; keizu money-kind → kanae fundFlowEdge flowType (inverse of bridge.KANAE_FLOW_TO_KIND for the
;; invertible fiscal kinds). political-donation is intentionally absent (not a govt fiscal flow).
(def KEIZU-KIND-TO-KANAE
  {"budget-outlay" "outlay"
   "subsidy" "subsidy"
   "grant" "grant"
   "procurement-award" "procurement"})

(defn- err [msg] (throw (ex-info msg {})))

(defn- to-float
  "Python float(x) — finite double; tolerant of numeric strings (the seed amounts are numbers)."
  [v]
  (cond
    (nil? v) 0.0
    (number? v) (double v)
    (string? v) #?(:clj (Double/parseDouble v) :cljs (js/parseFloat v))
    :else (err "amount must be a number")))

(defn- to-int
  "Python int(x) — truncates toward zero (the seed as-of values are longs)."
  [v]
  (cond
    (nil? v) 0
    (integer? v) (long v)
    (number? v) (long v)
    (string? v) #?(:clj (long (Double/parseDouble v)) :cljs (long (js/parseFloat v)))
    :else 0))

(defn to-kanae-flow
  "One keizu :money → one kanae fundFlowEdge. Raises if the kind is not a govt fiscal flow."
  [m]
  (let [kind (kw* (get m ":money/kind"))]
    (when-not (contains? KEIZU-KIND-TO-KANAE kind)
      (err (str "export: '" kind "' is not a kanae fiscal flow (e.g. political-donation excluded)")))
    {"edgeId" (str "keizu:" (str (get m ":money/id" "?")))
     "flowType" (get KEIZU-KIND-TO-KANAE kind)
     "donor" (get m ":money/payer" "")
     "recipient" (get m ":money/payee" "")
     "amount" (to-float (get m ":money/amount" 0.0))
     "currency" (get m ":money/currency" "")
     "asOf" (to-int (get m ":money/as-of" 0))
     "sources" (vec (get m ":money/sources" []))}))

(defn to-kanae-flows
  "All fiscal :money → kanae flows; non-fiscal kinds (political-donation) skipped + counted."
  [g]
  (let [{:keys [flows skipped]}
        (reduce (fn [acc m]
                  (if (contains? KEIZU-KIND-TO-KANAE (kw* (get m ":money/kind")))
                    (update acc :flows conj (to-kanae-flow m))
                    (update acc :skipped conj (get m ":money/id"))))
                {:flows [] :skipped []} (get g "money"))]
    {"flows" flows "skipped" skipped "skipped_count" (count skipped)}))

(defn render-payload
  "JSON-safe aggregate concentration for a kanae render (Sankey/treemap-ready). Tuples are
  flattened to [key, value] pairs; no sets remain. Carries the mirror/non-adjudicating flags."
  [c]
  {"actor" "keizu"
   "isMirror" true
   "nonAdjudicating" true
   "counts" (into {} (map (fn [k] [k (get c k)])
                          ["node_count" "committee_count" "rel_count"
                           "money_count" "statement_count"]))
   "money_by_payee" (mapv vec (get-in c ["money_concentration" "shares"]))
   "money_by_payer" (mapv vec (get-in c ["payer_concentration" "shares"]))
   "money_hhi" {"payee" (get-in c ["money_concentration" "hhi"])
                "payer" (get-in c ["payer_concentration" "hhi"])}
   "by_jurisdiction" (get c "by_jurisdiction")
   "committee_cross_organ" (get c "committee_cross_organ")
   "cross_committee_seats" (get c "cross_committee_seats")
   "connector_seats" (get c "connector_seats")
   "revolving_door" (get c "revolving_door")
   "award_and_fund" (get c "award_and_fund")
   "statement_index" {"count" (get-in c ["statement_index" "count"])
                      "by_speaker" (mapv vec (get-in c ["statement_index" "by_speaker"]))
                      "by_topic" (get-in c ["statement_index" "by_topic"])}})

;; ── canonical JSON with sort_keys=True (json.dumps(ensure_ascii=False, sort_keys=True)) ──
;; Reuse weave's float repr + string escaper; this serializer differs from weave/to-json only in
;; that map keys are emitted in sorted (not insertion) order, matching Python's sort_keys=True.
(def ^:private py-float-repr @#'w/py-float-repr)
(def ^:private json-str @#'w/json-str)

(defn- to-json-sorted [v]
  (cond
    (nil? v) "null"
    (true? v) "true"
    (false? v) "false"
    (string? v) (json-str v)
    #?(:clj (integer? v) :cljs (and (number? v) (== v (Math/floor v)) (not (instance? js/Number v)))) (str v)
    #?(:clj (instance? Double v) :cljs (number? v)) (py-float-repr (double v))
    #?@(:clj [(instance? Float v) (py-float-repr (double v))])
    (map? v) (str "{" (str/join ", "
                                (map (fn [[k val]] (str (json-str (str k)) ": " (to-json-sorted val)))
                                     (sort-by (fn [[k _]] (str k)) v))) "}")
    (sequential? v) (str "[" (str/join ", " (map to-json-sorted v)) "]")
    :else (json-str (str v))))

(defn render-json
  "The render payload as a JSON string (proves it is fully serializable). sort_keys=True."
  [c]
  (to-json-sorted (render-payload c)))

#?(:clj
   (defn -main
     "CLI mirror of export.py __main__: weave the seed → kanae flows + render-json byte count."
     [& argv]
     (let [seed (if (and (seq argv) (not (str/starts-with? (first argv) "--")))
                  (first argv)
                  (let [here (-> *file* clojure.java.io/file .getParentFile .getParentFile)]
                    (str (clojure.java.io/file here "data" "seed-relation-graph.kotoba.edn"))))
           g (w/weave (kedn/load-edn seed))
           kf (to-kanae-flows g)]
       (println (str "# keizu → kanae export — " (count (get kf "flows"))
                     " fiscal flows, " (get kf "skipped_count") " non-fiscal skipped"))
       (doseq [f (get kf "flows")]
         (println (str "  " (format "%-12s" (get f "flowType")) " "
                       (get f "donor") " → " (get f "recipient") "  "
                       (format "%.0f" (get f "amount")) " " (get f "currency"))))
       (println (str "  render payload JSON bytes: "
                     (count (.getBytes ^String (render-json (w/concentration g)) "UTF-8"))))
       0)))
