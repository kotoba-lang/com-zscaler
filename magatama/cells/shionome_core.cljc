(ns magatama.cells.shionome-core
  "shionome_core — pure capital-flow logic shared by the shionome_* fleet cells.
  Per ADR-2606072200.

  THE DEFINING INVARIANT — トレードはしない (G2): trade/advisory tokens are refused on every
  flow kind AND on every dry-run post body; only capital-movement kinds feed the money math.
  Nothing here is a buy/sell signal, price target, or portfolio instruction.

  Stdlib only. Deterministic.")

;; ── Constants ────────────────────────────────────────────────────────────────────

(def capital-movement #{"rotation" "fund-inflow" "fund-outflow" "fx-flow"})

(def flow-kinds
  (into capital-movement
        #{"price-move" "cross-correlation" "volume-shift" "yield-shift"}))

(def trade-tokens
  ["buy" "sell" "long" "short" "overweight" "underweight" "recommend"
   "target price" "target-price" "推奨" "買い" "売り" "目標株価" "空売り"])

(def disclaimer
  "【観測ミラー / capital-flow observation — NOT financial advice、トレードはしない】 公開市場データから観測した資金フローの集計です。売買の推奨・目標価格・ポジション提案はしません。")

;; ── Helpers ──────────────────────────────────────────────────────────────────────

(defn- kw->str
  "Strip leading `:` and namespace prefix, lowercase — mirrors Python _kw()."
  [v]
  (let [s (-> (str (or v ""))
              (clojure.string/replace #"^:" ""))]
    (-> (last (clojure.string/split s #"/"))
        clojure.string/lower-case)))

(defn trade-token-in
  "Returns the first trade token found in text, or empty string."
  [text]
  (let [blob (clojure.string/lower-case (str (or text "")))]
    (or (some #(when (clojure.string/includes? blob %) %) trade-tokens) "")))

;; ── Core logic ───────────────────────────────────────────────────────────────────

(defn screen-flows
  "G1/G2/G3 intake screen. Each flow needs a factual (non-trade) kind + >=2 sources.
  Throws on violation (refuse the batch, never silently drop). Returns the screened flows."
  [flows]
  (doseq [f flows]
    (let [kind (kw->str (or (get f :kind) (get f "kind")))]
      (let [t (trade-token-in kind)]
        (when (not= t "")
          (throw (ex-info (str "G2: flow kind contains trade token " (pr-str t)
                               " — unrepresentable (トレードはしない)")
                          {:gate :g2 :token t}))))
      (when-not (contains? flow-kinds kind)
        (throw (ex-info (str "G2: flow kind " (pr-str kind) " not a factual observation")
                        {:gate :g2 :kind kind})))
      (let [sources (or (get f :sources) (get f "sources") [])
            valid-count (count (filter #(not= (clojure.string/trim (str %)) "") sources))]
        (when (< valid-count 2)
          (throw (ex-info "G3: a flow needs >=2 public-source citations"
                          {:gate :g3 :sources sources}))))))
  flows)

(defn net-flow
  "Per-bucket net capital flow (in − out), capital-movement kinds only. Descending by net."
  [flows]
  (let [into-m  (atom {})
        outof-m (atom {})]
    (doseq [f flows]
      (let [kind (kw->str (or (get f :kind) (get f "kind")))]
        (when (contains? capital-movement kind)
          (let [mag (double (or (get f :magnitude) (get f "magnitude") 0.0))
                src (or (get f :source) (get f "source"))
                tgt (or (get f :target) (get f "target"))]
            (when (and tgt (not= tgt "external"))
              (swap! into-m update tgt (fnil + 0.0) mag))
            (when (and src (not= src "external"))
              (swap! outof-m update src (fnil + 0.0) mag))))))
    (let [all-buckets (into #{} (concat (keys @into-m) (keys @outof-m)))
          scale 10000.0
          round4 #(/ (Math/rint (* (double %) scale)) scale)
          rows (for [b all-buckets]
                 {"bucket" b
                  "net"    (round4 (- (get @into-m b 0.0) (get @outof-m b 0.0)))})]
      (sort-by (juxt #(- (get % "net")) #(get % "bucket")) rows))))

(defn top-rotation
  "The largest bucket->bucket rotation (capital-movement kinds only), or nil."
  [flows]
  (let [pairs (atom {})]
    (doseq [f flows]
      (let [kind (kw->str (or (get f :kind) (get f "kind")))]
        (when (contains? capital-movement kind)
          (let [s (or (get f :source) (get f "source"))
                t (or (get f :target) (get f "target"))]
            (when (and s t
                       (not= s "external") (not= t "external")
                       (not= s t))
              (swap! pairs update [s t] (fnil + 0.0)
                     (double (or (get f :magnitude) (get f "magnitude") 0.0))))))))
    (when (seq @pairs)
      (let [[[s t] m] (apply max-key val @pairs)
            scale 10000.0
            rounded (/ (Math/rint (* m scale)) scale)]
        {"from" s "to" t "magnitude" rounded}))))

(defn regime
  "FACTUAL cross-asset regime from net flow into risk vs safe buckets.
  Descriptive, NOT advice."
  [net-rows risk-tags]
  (let [risk-net (reduce + 0.0
                         (for [r net-rows
                               :when (= (get risk-tags (get r "bucket")) "risk")]
                           (get r "net" 0.0)))
        safe-net (reduce + 0.0
                         (for [r net-rows
                               :when (= (get risk-tags (get r "bucket")) "safe")]
                           (get r "net" 0.0)))
        label (cond
                (and (= risk-net 0.0) (= safe-net 0.0)) "indeterminate"
                (and (> risk-net 0) (<= safe-net 0))     "risk-on"
                (and (< risk-net 0) (>= safe-net 0))     "risk-off"
                :else                                     "mixed")
        scale 10000.0
        round4 #(/ (Math/rint (* (double %) scale)) scale)]
    {"regime"          label
     "risk_net"        (round4 risk-net)
     "safe_net"        (round4 safe-net)
     "no_trade_notice" true}))

(defn draft-dry-run-post
  "A dry-run post (status dry-run only, G8).
  Refuses a trade-token body (G2) / <2 sources (G3)."
  [body-core sources]
  (let [t (trade-token-in body-core)]
    (when (not= t "")
      (throw (ex-info (str "G2: post body contains trade token " (pr-str t)
                           " — refused (トレードはしない)")
                      {:gate :g2 :token t}))))
  (let [valid-sources (filter #(not= (clojure.string/trim (str %)) "") (or sources []))]
    (when (< (count valid-sources) 2)
      (throw (ex-info "G3: a post needs >=2 public-source citations"
                      {:gate :g3}))))
  {"status"           "dry-run"
   "is_mirror"        true
   "no_trade_notice"  true
   "server_held_key"  false
   "body"             (str disclaimer "\n\n" body-core)
   "sources"          (vec sources)})
