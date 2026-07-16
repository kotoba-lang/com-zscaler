(ns shionome.methods.weave
  "weave.cljc — 潮目 (shionome) capital-flow graph build + aggregate concentration. ADR-2606072201.
  1:1 Clojure port of `methods/weave.py` (same house style as keizu/rasen/inochi/kabuto).

  THE HEART of the actor and the G1/G2/G3/G4 anchor. Given the seed flow-graph it:
    1. VALIDATES every bucket / flow / snapshot against the closed structural vocab (mirror of
       the ontology). A person/account/portfolio bucket scope, a TRADE/advisory flow kind
       (buy / sell / recommend / target-price), an under-sourced flow, or a NaN/negative
       magnitude throws ex-info (mirroring Python's ValueError) — not a silent drop.
    2. WEAVES the validated records into an in-memory capital-flow graph.
    3. Computes AGGREGATE, EDGE-PRIMARY flow metrics (G4) — there is NO per-bucket
       rating/signal/target/score anywhere: net flow per bucket, rotation pairs,
       per-bucket inflow HHI, by-asset-class / by-region, a FACTUAL regime descriptor,
       the stock-pyramid sizing view, and undirected correlation clusters.

  THE DEFINING INVARIANT — トレードはしない: nothing here is a buy/sell signal, a price
  target, an over/under-weight call, or a portfolio instruction. `trade-token-in` is the gate
  scanner that REFUSES trade-token text (throws). Every output is an observational MAP of
  realized flows, NON-advisory: the metrics describe where capital moved, never what to do.

  House style: Python ':…' keyword strings stay strings (incl. all :*/* attrs); validation /
  closed-vocab / gate violations throw ex-info; pure fns; file I/O only at the #?(:clj) edge.

  Insertion-order parity: Python iterates plain dicts (into / outof / by_bucket / klass / reg /
  layers / pairs / latest) in insertion order, then stable-sorts. shionome's set iteration is
  ONLY ever `set(into) | set(outof)` (net-flow keys, immediately iterated to build rows that are
  then stable-sorted by (-net, bucket)) and the `correlation_clusters` traversal (whose outer
  loop is `sorted(adj)` and whose components are emitted `sorted(comp)`, so the result is
  sorted, never CPython-set-order-dependent). Plain ::order insertion-tracking suffices — no
  siphash13/setobject port needed. The ::order metadata + stable sort-by ties the Python dict
  iteration order byte-for-byte for the `shares`/`by_bucket`/`by_payer` rankings + map keys."
  (:require [clojure.string :as str]
            [clojure.set]
            #?(:clj [shionome.methods.edn :as sedn])))

;; ── closed vocab (mirror of the ontology :db/allowed) ───────────────────────────
(def BUCKET-SCOPES ["asset-class" "sector" "region" "theme"])
(def FLOW-KINDS
  ["rotation" "fund-inflow" "fund-outflow" "price-move"
   "cross-correlation" "volume-shift" "yield-shift" "fx-flow"])
;; the subset of flow kinds that move actual CAPITAL (a measurable amount of money).
(def CAPITAL-MOVEMENT-KINDS ["rotation" "fund-inflow" "fund-outflow" "fx-flow"])
(def SNAPSHOT-METRICS
  ["return-pct" "net-fund-flow" "volume-z" "yield-pct" "spread-bps" "drawdown-pct" "outstanding-usd"])
;; the snapshot metric carrying a STOCK (the total SIZE of an asset class in USD trillions).
(def STOCK-METRIC "outstanding-usd")
(def REGIMES ["risk-on" "risk-off" "mixed" "indeterminate"])
(def SOURCING ["representative" "authoritative"])

;; THE NO-TRADE TOKENS (トレードはしない) — any of these as a flow/bucket kind, or appearing
;; in a social post body, turns an observation into a trade instruction / advice.
(def TRADE-TOKENS
  ["buy" "sell" "long" "short" "overweight" "underweight" "accumulate"
   "recommend" "recommendation" "rating" "target price" "target-price" "price target"
   "stop loss" "stop-loss" "take profit" "take-profit" "entry point" "exit point"
   "allocate" "strong buy" "strong sell" "outperform-rated" "should buy" "should sell"
   "推奨" "買い推奨" "売り推奨" "買い" "売り" "目標株価" "空売り" "ロング" "ショート"
   "利確" "損切り" "エントリー" "建玉" "ポジション取"])

;; Charter Rider §2(e) / N5 — commercial market-data / sell-side terminals are PROHIBITED as a
;; citation source. A derived datom citing one of these is refused on EVERY path.
(def SOURCE-DENY
  ["bloomberg terminal" "bloomberg.com/professional" "refinitiv" "eikon"
   "factset" "capital iq" "capiq" "morningstar direct" "pitchbook"
   "tradingview premium" "koyfin pro" "四季報" "q(uick)" "sell-side desk"])

;; G9 / G1 no-doxxing — a node is a public BUCKET, so an individual-investor / account /
;; personal field is unrepresentable (no tracking of who holds what).
(def PII-FORBIDDEN-BUCKET-ATTRS
  #{"account" "account-id" "broker" "holder" "owner" "investor" "trader"
    "wallet" "address" "email" "phone" "name" "person" "portfolio" "position-size"})

(defn source-denied
  "Return the first prohibited commercial market-data term found in any source, or '' if clean."
  [sources]
  (let [blob (str/lower-case (str/join " " (map str (or sources []))))]
    (or (some (fn [d] (when (str/includes? blob d) d)) SOURCE-DENY) "")))

(defn trade-token-in
  "Return the first no-trade/advisory token found in `text`, or '' if clean. The core
  トレードはしない guard — used on every flow/bucket kind AND on every social-post body."
  [text]
  (let [blob (str/lower-case (str (or text "")))]
    (or (some (fn [t] (when (str/includes? blob t) t)) TRADE-TOKENS) "")))

(defn- in-vec?
  "Membership test against a vector closed-vocab (Python `x in (...)` over a tuple)."
  [v x]
  (boolean (some #(= % x) v)))

(defn- kw*
  "Normalize an edn keyword/string to a bare lowercase token (':flow/kind' → 'kind')."
  [v]
  (let [s (-> (str (or v "")) (str/replace #"^:+" ""))]
    (-> (last (str/split s #"/" -1)) (str/lower-case))))

(defn- err [msg] (throw (ex-info msg {})))

(defn- to-finite-double
  "float(...) with TypeError/ValueError → 'must be a number'."
  [v emsg]
  (cond
    (nil? v) 0.0
    (number? v) (double v)
    (string? v) (try #?(:clj (Double/parseDouble v) :cljs (let [n (js/parseFloat v)]
                                                            (if (js/isNaN n) (err "x") n)))
                     (catch #?(:clj Exception :cljs :default) _ (err emsg)))
    :else (err emsg)))

(defn- finite? [x] #?(:clj (and (not (Double/isNaN x)) (not (Double/isInfinite x)))
                      :cljs (js/isFinite x)))

;; ── validation (G1/G2/G3) ───────────────────────────────────────────────────────
(defn validate-bucket [b]
  (let [scope (kw* (get b ":bucket/scope" ""))]
    (when-not (in-vec? BUCKET-SCOPES scope)
      (err (str "G1: bucket scope '" scope "' not in " (pr-str BUCKET-SCOPES)
                " — a person/account/portfolio is unrepresentable (shionome maps public capital "
                "buckets, never individual investors)")))
    (doseq [forbidden ["rating" "signal" "target" "score" "recommendation"]]
      (when (or (contains? b (str ":bucket/" forbidden)) (contains? b forbidden))
        (err (str "G2/G4: a per-bucket '" forbidden "' is a trade instruction — unrepresentable "
                  "(トレードはしない; concentration is edge-primary, computed on read)"))))
    (doseq [key (keys b)]
      (when (contains? PII-FORBIDDEN-BUCKET-ATTRS (kw* key))
        (err (str "G9/G1 no-doxxing: bucket field '" key "' is an individual-investor/account "
                  "field — unrepresentable on a public capital bucket (shionome never tracks who "
                  "holds what)"))))
    (when-not (in-vec? SOURCING (kw* (get b ":bucket/sourcing" "")))
      (err "G11: every bucket must declare :bucket/sourcing"))))

(defn validate-flow [f]
  (let [kind (kw* (get f ":flow/kind" ""))
        t (trade-token-in kind)]
    (when (seq t)
      (err (str "G2: flow kind '" kind "' contains the trade token '" t "' — unrepresentable (トレードはしない)")))
    (when-not (in-vec? FLOW-KINDS kind)
      (err (str "G2: flow kind '" kind "' not in the factual observation vocab " (pr-str FLOW-KINDS))))
    (when-not (true? (get f ":flow/no-trade-notice"))
      (err "G2: :flow/no-trade-notice must be true (an observation, never a trade instruction)"))
    (let [srcs (or (get f ":flow/sources") [])]
      (when-not (and (vector? srcs) (>= (count srcs) 2))
        (err (str "G3: flow '" (get f ":flow/id") "' needs ≥2 public-source citations")))
      (let [d (source-denied srcs)]
        (when (seq d)
          (err (str "Rider §2(e)/N5: source '" d "' is a commercial market-data terminal — prohibited citation")))))
    (let [mag (to-finite-double (get f ":flow/magnitude" 0.0)
                                (str "flow '" (get f ":flow/id") "' magnitude must be a number"))]
      (when (or (not (finite? mag)) (< mag 0))
        (err (str "flow '" (get f ":flow/id") "' magnitude must be finite and ≥ 0 "
                  "(a negative/NaN magnitude corrupts the net-flow/HHI math)"))))
    (when-not (in-vec? SOURCING (kw* (get f ":flow/sourcing" "")))
      (err "G11: every flow must declare :flow/sourcing"))))

(defn validate-snapshot
  "An observed as-of bucket metric (return / net-flow / volume / yield). Must name a known
  metric (G2), carry ≥1 public source (G3, no prohibited terminal), declare sourcing (G11),
  and have a finite value."
  [s]
  (let [metric (kw* (get s ":snap/metric" ""))]
    (when-not (in-vec? SNAPSHOT-METRICS metric)
      (err (str "G2: snapshot metric '" metric "' not in " (pr-str SNAPSHOT-METRICS))))
    (let [srcs (or (get s ":snap/sources") [])]
      (when-not (and (vector? srcs) (>= (count srcs) 1))
        (err (str "G3: snapshot '" (get s ":snap/id") "' needs ≥1 public source")))
      (let [d (source-denied srcs)]
        (when (seq d)
          (err (str "Rider §2(e)/N5: source '" d "' is a commercial market-data terminal — prohibited citation")))))
    (let [val (to-finite-double (get s ":snap/value" 0.0)
                                (str "snapshot '" (get s ":snap/id") "' value must be a number"))]
      (when-not (finite? val)
        (err (str "snapshot '" (get s ":snap/id") "' value must be finite"))))
    (when-not (in-vec? SOURCING (kw* (get s ":snap/sourcing" "")))
      (err "G11: every snapshot must declare :snap/sourcing"))))

;; ── ordered map (mirror a Python plain dict's first-touch insertion order) ──────
;; ::order is a vector of keys in first-touch order; the stable sort-by on shares / by_bucket /
;; by_payer / klass / reg / layers then ties exactly the Python dict iteration order.
(defn- ordered-map [] ^{::order []} {})

(defn- omap-assoc
  "Set k → v, recording k's first-touch position in ::order metadata."
  [m k v]
  (let [had? (contains? m k)
        m' (assoc m k v)]
    (if had?
      (with-meta m' (meta m))
      (with-meta m' (update (meta m) ::order conj k)))))

(defn- omap-update
  [m k f default]
  (omap-assoc m k (f (get m k default))))

(defn- omap-items
  "Items in first-touch order (matches Python `dict.items()` / iteration)."
  [d]
  (let [order (::order (meta d))]
    (if order (map (fn [k] [k (get d k)]) order) (seq d))))

(defn- omap
  "Build an ordered map (::order-tagged) from a flat seq of k v k v … so JSON serialization
  preserves the literal key order (mirroring a Python dict literal's insertion order)."
  [& kvs]
  (reduce (fn [m [k v]] (omap-assoc m k v)) (ordered-map) (partition 2 kvs)))

;; ── weave ───────────────────────────────────────────────────────────────────────
(defn weave
  "Validate + index the seed graph into an in-memory capital-flow graph. Throws on a gate.
  `buckets` is an ordered map (insertion order = seed order, like a Python dict)."
  [graph]
  (let [buckets (reduce (fn [m b] (omap-assoc m (get b ":bucket/id") b))
                        (ordered-map) (get graph ":buckets" []))
        flows (vec (get graph ":flows" []))
        snapshots (vec (get graph ":snapshots" []))]
    (doseq [b (map second (omap-items buckets))] (validate-bucket b))
    (doseq [f flows] (validate-flow f))
    (doseq [s snapshots] (validate-snapshot s))
    {"buckets" buckets "flows" flows "snapshots" snapshots}))

;; ── float formatting (Python round + repr parity) ────────────────────────────────
(defn pyround
  "Python round(x, n): HALF_EVEN over the exact binary value of the double."
  [x n]
  #?(:clj (-> (java.math.BigDecimal. (double x))
              (.setScale (int n) java.math.RoundingMode/HALF_EVEN)
              (.doubleValue))
     :cljs (let [f (Math/pow 10 n) y (* (double x) f)
                 r (Math/round y)] (/ r f))))

;; ── aggregate, edge-primary flow metrics (G4) ────────────────────────────────────
(defn net-flow-by-bucket
  "Per bucket: net capital flow = (Σ magnitude INTO it) − (Σ OUT of it). Positive = money
  flowing IN; negative = OUT. Aggregate, edge-primary. The synthetic 'external' node is excluded."
  [g]
  (let [[into outof]
        (reduce (fn [[ix ox] f]
                  (if-not (in-vec? CAPITAL-MOVEMENT-KINDS (kw* (get f ":flow/kind")))
                    [ix ox]
                    (let [mag (to-finite-double (get f ":flow/magnitude" 0.0) "magnitude must be a number")
                          tgt (get f ":flow/target") src (get f ":flow/source")
                          ix (if (and tgt (not= tgt "external")) (omap-update ix tgt #(+ % mag) 0.0) ix)
                          ox (if (and src (not= src "external")) (omap-update ox src #(+ % mag) 0.0) ox)]
                      [ix ox])))
                [(ordered-map) (ordered-map)] (get g "flows"))
        keys- (vec (distinct (concat (map first (omap-items into)) (map first (omap-items outof)))))
        out (mapv (fn [b]
                    (let [i (get into b 0.0) o (get outof b 0.0)]
                      (omap "bucket" b
                            "label" (get-in (get g "buckets") [b ":bucket/label"] b)
                            "inflow" (pyround i 4)
                            "outflow" (pyround o 4)
                            "net" (pyround (- i o) 4))))
                  keys-)]
    (vec (sort-by (juxt #(- (get % "net")) #(get % "bucket")) out))))

(defn rotation-pairs
  "Ranked source→target rotation flows: どこからどこへ. Only bucket→bucket capital-movement
  flows where BOTH ends are real buckets. Aggregate (summed over duplicate pairs), factual."
  [g]
  (let [pairs (reduce (fn [m f]
                        (if-not (in-vec? CAPITAL-MOVEMENT-KINDS (kw* (get f ":flow/kind")))
                          m
                          (let [src (get f ":flow/source") tgt (get f ":flow/target")]
                            (if (and src tgt (not= src "external") (not= tgt "external") (not= src tgt))
                              (omap-update m [src tgt]
                                           #(+ % (to-finite-double (get f ":flow/magnitude" 0.0) "magnitude must be a number"))
                                           0.0)
                              m))))
                      (ordered-map) (get g "flows"))
        out (mapv (fn [[[src tgt] mag]]
                    (omap "from" src
                          "from_label" (get-in (get g "buckets") [src ":bucket/label"] src)
                          "to" tgt
                          "to_label" (get-in (get g "buckets") [tgt ":bucket/label"] tgt)
                          "magnitude" (pyround mag 4)))
                  (omap-items pairs))]
    (vec (sort-by (juxt #(- (get % "magnitude")) #(get % "from") #(get % "to")) out))))

(defn inflow-concentration
  "Per-bucket INFLOW share + Herfindahl-Hirschman Index (HHI) over gross inflows.
  HHI ∈ (0,1]; higher = capital crowding into fewer buckets. Aggregate, factual (G4)."
  [g]
  (let [[by-bucket total]
        (reduce (fn [[bb t] f]
                  (if-not (in-vec? CAPITAL-MOVEMENT-KINDS (kw* (get f ":flow/kind")))
                    [bb t]
                    (let [tgt (get f ":flow/target")]
                      (if (and tgt (not= tgt "external"))
                        (let [mag (to-finite-double (get f ":flow/magnitude" 0.0) "magnitude must be a number")]
                          [(omap-update bb tgt #(+ % mag) 0.0) (+ t mag)])
                        [bb t]))))
                [(ordered-map) 0.0] (get g "flows"))
        shares (reduce (fn [m [b v]]
                         (omap-assoc m b (if (not= total 0.0) (/ v total) 0.0)))
                       (ordered-map) (omap-items by-bucket))
        hhi (reduce + 0.0 (map (fn [[_ s]] (* s s)) (omap-items shares)))
        ranked (vec (map (fn [[b v]] [b v])
                         (sort-by (fn [[_ v]] (- v)) (omap-items shares))))]
    (omap "total" (pyround total 4) "hhi" (pyround hhi 4) "shares" ranked
          "by_bucket" by-bucket)))

(defn by-asset-class
  "Net flow aggregated to the ASSET-CLASS level. Money attributed to a flow end's bucket's
  asset-class. Aggregate, factual."
  [g]
  (let [net (net-flow-by-bucket g)
        klass (reduce (fn [k r]
                        (let [bid (get r "bucket")
                              n (get r "net")
                              ac (get-in (get g "buckets") [bid ":bucket/asset-class"] "(unknown)")
                              slot (or (get k ac) (omap "asset_class" ac "net" 0.0 "buckets" 0))]
                          (omap-assoc k ac (-> slot
                                               (assoc "net" (+ (get slot "net") n))
                                               (assoc "buckets" (inc (get slot "buckets")))))))
                      (ordered-map) net)
        rows (map (fn [[_ v]] (assoc v "net" (pyround (get v "net") 4))) (omap-items klass))]
    (vec (sort-by (juxt #(- (get % "net")) #(get % "asset_class")) rows))))

(defn by-region
  "Net flow aggregated to the REGION level. Aggregate, factual."
  [g]
  (let [net (net-flow-by-bucket g)
        reg (reduce (fn [k r]
                      (let [bid (get r "bucket")
                            n (get r "net")
                            rg (get-in (get g "buckets") [bid ":bucket/region"] "(unknown)")
                            slot (or (get k rg) (omap "region" rg "net" 0.0 "buckets" 0))]
                        (omap-assoc k rg (-> slot
                                             (assoc "net" (+ (get slot "net") n))
                                             (assoc "buckets" (inc (get slot "buckets")))))))
                    (ordered-map) net)
        rows (map (fn [[_ v]] (assoc v "net" (pyround (get v "net") 4))) (omap-items reg))]
    (vec (sort-by (juxt #(- (get % "net")) #(get % "region")) rows))))

(defn regime
  "A FACTUAL cross-asset regime descriptor (risk-on / risk-off / mixed / indeterminate),
  derived from the SIGN of net flow into :risk buckets vs :safe buckets. DESCRIPTIVE, NOT
  advice (G2). A bucket with no :bucket/risk tag is :neutral and ignored."
  [g]
  (let [net (net-flow-by-bucket g)
        [risk-net safe-net]
        (reduce (fn [[rn sn] r]
                  (let [bid (get r "bucket") n (get r "net")
                        tag (kw* (get-in (get g "buckets") [bid ":bucket/risk"] ""))]
                    (cond
                      (= tag "risk") [(+ rn n) sn]
                      (= tag "safe") [rn (+ sn n)]
                      :else [rn sn])))
                [0.0 0.0] net)
        label (cond
                (and (= risk-net 0.0) (= safe-net 0.0)) "indeterminate"
                (and (> risk-net 0) (<= safe-net 0)) "risk-on"
                (and (< risk-net 0) (>= safe-net 0)) "risk-off"
                :else "mixed")]
    (omap "regime" label
          "risk_net" (pyround risk-net 4)
          "safe_net" (pyround safe-net 4)
          "no_trade_notice" true)))

(defn correlation-clusters
  "Buckets observed moving together (:cross-correlation edges) — undirected co-movement
  components. Aggregate; the substance lives on the edges (G4), never a per-bucket score."
  [g]
  (let [adj (reduce (fn [m f]
                      (if (= (kw* (get f ":flow/kind")) "cross-correlation")
                        (let [a (get f ":flow/source") b (get f ":flow/target")]
                          (if (and a b (not= a "external") (not= b "external"))
                            (-> m
                                (update a (fnil conj #{}) b)
                                (update b (fnil conj #{}) a))
                            m))
                        m))
                    {} (get g "flows"))
        nodes-sorted (sort (keys adj))
        [_ clusters]
        (reduce (fn [[seen clusters] node]
                  (if (contains? seen node)
                    [seen clusters]
                    (let [[seen' comp]
                          (loop [stack [node] seen seen comp []]
                            (if (empty? stack)
                              [seen comp]
                              (let [n (peek stack) stack (pop stack)]
                                (if (contains? seen n)
                                  (recur stack seen comp)
                                  (let [seen (conj seen n)
                                        comp (conj comp n)
                                        new-neighbors (clojure.set/difference (get adj n #{}) seen)]
                                    (recur (into stack new-neighbors) seen comp))))))]
                      [seen'
                       (if (> (count comp) 1)
                         (conj clusters (omap "members" (vec (sort comp)) "size" (count comp)))
                         clusters)])))
                [#{} []] nodes-sorted)]
    (vec (sort-by (juxt #(- (get % "size")) #(get % "members")) clusters))))

(defn active-as-of
  "G10 / 非終末論 — how many flows/snapshots are observed as of `ts` (as-of ≤ ts)."
  [g ts]
  (let [active-flows (filter #(<= (long (get % ":flow/as-of" 0)) ts) (get g "flows"))
        active-snaps (filter #(<= (long (get % ":snap/as-of" 0)) ts) (get g "snapshots"))]
    (omap "ts" ts
          "active_flows" (count active-flows)
          "total_flows" (count (get g "flows"))
          "active_snapshots" (count active-snaps)
          "total_snapshots" (count (get g "snapshots")))))

(defn check-integrity
  "Referential integrity: every flow/snapshot end must resolve to an existing bucket (or the
  synthetic 'external' node for a flow end). A data-quality diagnostic, not a charter gate."
  [g]
  (let [buckets (set (keys (get g "buckets")))
        flow-space (conj buckets "external")
        dangling (transient [])
        chk (fn [ref space kind owner field]
              (when (and ref (not (contains? space ref)))
                (conj! dangling (omap "kind" kind "owner" owner "field" field "ref" ref))))]
    (doseq [f (get g "flows")]
      (chk (get f ":flow/source") flow-space "flow" (get f ":flow/id") "source")
      (chk (get f ":flow/target") flow-space "flow" (get f ":flow/id") "target"))
    (doseq [s (get g "snapshots")]
      (chk (get s ":snap/bucket") buckets "snapshot" (get s ":snap/id") "bucket"))
    (let [d (persistent! dangling)]
      (omap "dangling_count" (count d) "dangling" d))))

(defn assert-integrity
  "Strict mode — throw if any reference dangles (used by the ingest data-quality gate)."
  [g]
  (let [rep (check-integrity g)]
    (when (pos? (get rep "dangling_count"))
      (let [first- (first (get rep "dangling"))]
        (err (str "integrity: " (get rep "dangling_count") " dangling ref(s); e.g. "
                  (get first- "kind") " '" (get first- "owner") "' "
                  (get first- "field") "→'" (get first- "ref") "' (no such bucket)"))))))

;; ── stock layer (the money-and-markets pyramid) ───────────────────────────────────
(defn latest-stock-by-bucket
  "For each bucket carrying an :outstanding-usd snapshot, the LATEST observed stock size
  (USD trillions), taken by max :snap/as-of (G10 append-only). Returns an ordered map
  {bucket_id → [value as_of]}. Factual sizes, never a rating/signal (G2/G4)."
  [g]
  (reduce (fn [latest s]
            (if-not (= (kw* (get s ":snap/metric")) STOCK-METRIC)
              latest
              (let [bid (get s ":snap/bucket")]
                (if-not bid
                  latest
                  (let [as-of (long (get s ":snap/as-of" 0))
                        val (to-finite-double (get s ":snap/value" 0.0) "value must be a number")]
                    (if (or (not (contains? latest bid)) (>= as-of (second (get latest bid))))
                      (omap-assoc latest bid [val as-of])
                      latest))))))
          (ordered-map) (get g "snapshots")))

(defn stock-pyramid
  "THE 'how big is everything' SIZING VIEW — the money-and-markets pyramid. Aggregates the
  latest :outstanding-usd stock per bucket up to the ASSET-CLASS level and sizes each layer
  against the grand total. FACTUAL stock sizing — descriptive, NOT advice (G2/G4)."
  [g]
  (let [latest (latest-stock-by-bucket g)
        layers (reduce (fn [k [bid [val _as-of]]]
                         (let [ac (get-in (get g "buckets") [bid ":bucket/asset-class"] "(unknown)")
                               slot (or (get k ac) (omap "asset_class" ac "usd_tn" 0.0 "buckets" 0))]
                           (omap-assoc k ac (-> slot
                                                (assoc "usd_tn" (+ (get slot "usd_tn") val))
                                                (assoc "buckets" (inc (get slot "buckets")))))))
                       (ordered-map) (omap-items latest))
        grand (reduce + 0.0 (map (fn [[_ l]] (get l "usd_tn")) (omap-items layers)))
        sorted-layers (sort-by (juxt #(- (get % "usd_tn")) #(get % "asset_class"))
                               (map second (omap-items layers)))
        rows (mapv (fn [l]
                     (omap "asset_class" (get l "asset_class")
                           "usd_tn" (pyround (get l "usd_tn") 4)
                           "share" (if (not= grand 0.0) (pyround (/ (get l "usd_tn") grand) 4) 0.0)
                           "buckets" (get l "buckets")))
                   sorted-layers)]
    (omap "layers" rows
          "grand_total_usd_tn" (pyround grand 4)
          "bucket_count" (count (omap-items latest))
          "unit" "usd-tn"
          "no_trade_notice" true)))

(defn concentration
  "The full aggregate-first flow report (G3/G4). All metrics are derived on read from
  flows/snapshots; nothing is a per-bucket rating/signal/target/score (トレードはしない)."
  [g]
  (omap "bucket_count" (count (omap-items (get g "buckets")))
        "flow_count" (count (get g "flows"))
        "snapshot_count" (count (get g "snapshots"))
        "net_flow_by_bucket" (net-flow-by-bucket g)
        "rotation_pairs" (rotation-pairs g)
        "inflow_concentration" (inflow-concentration g)
        "by_asset_class" (by-asset-class g)
        "by_region" (by-region g)
        "stock_pyramid" (stock-pyramid g)
        "regime" (regime g)
        "correlation_clusters" (correlation-clusters g)
        "integrity" (check-integrity g)))

;; ── canonical JSON (json.dumps(ensure_ascii=False) parity, insertion-order keys) ──
(defn- py-float-repr
  "repr(float) for finite values in shionome's range: shortest round-trip plain decimal
  (Python prints exponential only for |x| ≥ 1e16 or < 1e-4; all shionome magnitudes are plain)."
  [^double x]
  #?(:clj
     (cond
       (zero? x) "0.0"
       :else
       (let [s (Double/toString x)
             ax (Math/abs x)]
         (if (and (>= ax 1.0e-4) (< ax 1.0e16))
           (let [p (.toPlainString (java.math.BigDecimal. s))]
             (if (str/includes? p ".")
               (let [t (str/replace p #"0+$" "")]
                 (if (str/ends-with? t ".") (str t "0") t))
               (str p ".0")))
           s)))
     :cljs (str x)))

(defn- json-str [s]
  (str \"
       (-> (str s)
           (str/replace "\\" "\\\\")
           (str/replace "\"" "\\\"")
           (str/replace "\n" "\\n")
           (str/replace "\t" "\\t")
           (str/replace "\r" "\\r"))
       \"))

(defn to-json
  "Canonical JSON of a weave/concentration value — matches Python json.dumps(ensure_ascii=False,
  sort_keys=False) with the default `, ` / `: ` separators. Maps keep insertion order
  (::order-aware); doubles use Python repr; longs print without a decimal point."
  [v]
  (cond
    (nil? v) "null"
    (true? v) "true"
    (false? v) "false"
    (string? v) (json-str v)
    #?(:clj (integer? v) :cljs (and (number? v) (== v (Math/floor v)) (not (instance? js/Number v)))) (str v)
    #?(:clj (instance? Double v) :cljs (number? v)) (py-float-repr (double v))
    #?@(:clj [(instance? Float v) (py-float-repr (double v))])
    (map? v) (str "{" (str/join ", " (map (fn [[k val]] (str (json-str k) ": " (to-json val)))
                                          (omap-items v))) "}")
    (sequential? v) (str "[" (str/join ", " (map to-json v)) "]")
    :else (json-str (str v))))

#?(:clj
   (defn -main
     "CLI: weave the seed → concentration → print canonical JSON (for byte-parity cmp)."
     [& argv]
     (let [here (-> *file* clojure.java.io/file .getParentFile .getParentFile)
           seed (if (and (seq argv) (not (str/starts-with? (first argv) "--")))
                  (first argv)
                  (str (clojure.java.io/file here "data" "seed-capital-flow-graph.kotoba.edn")))
           g (weave (sedn/load-edn seed))
           c (concentration g)]
       (println (to-json c))
       0)))
