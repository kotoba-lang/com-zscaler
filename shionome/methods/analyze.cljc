(ns shionome.methods.analyze
  "analyze.cljc — 潮目 (shionome) end-to-end membrane (dry-run). ADR-2606072201.
  1:1 Clojure port of `methods/analyze.py` (same house style as keizu/inochi/rasen).

  Load seed → weave + validate → aggregate concentration → dry-run social posts → render
  `out/intel-report.md` + `out/capital-flow-graph.kotoba.edn` + `out/kanae-render.json`.
  Aggregate-first (G3), edge-primary (G4), non-advisory / no-trade (G2, トレードはしない),
  mirror-not-signal (G5). No live posting (G8).

  The CORE (edn loader + weave/concentration) is the already-ported `shionome.methods.edn` +
  `shionome.methods.weave`; this driver reuses them verbatim. The dry-run social-post projection
  (DISCLAIMER + draft-netflow/rotation/regime + the トレードはしない body scan, social.py) and the
  kanae export (to-kanae-flows, export.py) are the membrane's own surface and are ported inline so
  the end-to-end report is reproducible.

  CONSTITUTIONAL (preserved by construction):
    G2 — THE DEFINING INVARIANT (トレードはしない): noTradeNotice true; the body is SCANNED for
      trade/advisory tokens and REFUSED (throws) if any appear. The report narrates where money
      MOVED, never what to do.
    G3 — ≥2 public-source citations on every post (`guard-sources` throws otherwise).
    G5 — every post opens with the observational-mirror DISCLAIMER (is-mirror true).
    G7 — server-held-key false; the member signs, the server never does.
    G8 — every post status is ':dry-run' (':published' unrepresentable); no live I/O.

  House style: Python ':…' keyword strings stay strings; pure fns; file I/O only at the #?(:clj)
  edge. Byte-parity: `-main` writes the SAME bytes analyze.py writes to out/intel-report.md."
  (:require [clojure.string :as str]
            [shionome.methods.edn :as sedn]
            [shionome.methods.weave :as w]))

;; ── float formatting (Python f-string :.Nf / :+.Nf — ROUND_HALF_EVEN on the exact double) ────
(defn- fmt-f
  "Python f\"{x:.Nf}\" — fixed-point with n fractional digits, HALF_EVEN over the exact binary value."
  [x n]
  #?(:clj (-> (java.math.BigDecimal. (double x))
              (.setScale (int n) java.math.RoundingMode/HALF_EVEN)
              (.toPlainString))
     :cljs (.toFixed (double x) n)))

(defn- fmt-sf
  "Python f\"{x:+.Nf}\" — like fmt-f but always with a leading sign (+ or -)."
  [x n]
  (let [s (fmt-f x n)]
    (if (str/starts-with? s "-") s (str "+" s))))

;; ── dry-run social-post projection (1:1 of social.py) ─────────────────────────────────────────
(def DISCLAIMER
  (str "【観測ミラー / capital-flow observation — NOT financial advice, トレードはしない】 "
       "公開市場データから観測した資金フローの集計です。売買の推奨・目標価格・ポジション提案は一切しません。"))

(defn- guard-sources
  "G3 — a post needs ≥2 non-blank public-source citations; refuse a prohibited market-data terminal."
  [sources]
  (let [s (vec (filter #(seq (str/trim (str %))) (or sources [])))]
    (when (< (count s) 2)
      (throw (ex-info "G3: a post needs ≥2 public-source citations" {})))
    (let [d (w/source-denied s)]
      (when (seq d)
        (throw (ex-info (str "Rider §2(e)/N5: source '" d "' is a commercial market-data terminal — a post may not cite it") {}))))
    s))

(defn- guard-no-trade
  "G2 core (トレードはしない) — refuse to emit a post whose body contains a trade/advisory token.
  The disclaimer text is exempt (it NAMES the prohibited acts to disclaim them)."
  [body]
  (let [scanned (str/replace body DISCLAIMER "")
        t (w/trade-token-in scanned)]
    (when (seq t)
      (throw (ex-info (str "G2: post body contains the trade/advisory token '" t "' — refused (shionome never "
                           "recommends a trade; it only observes flows). トレードはしない.") {})))))

(defn- post-record
  "Assemble a networkPost record with every invariant pinned. status is ALWAYS :dry-run."
  [subject body sources author]
  {":post/subject" subject
   ":post/body" body
   ":post/status" ":dry-run"            ;; G8 — published is unrepresentable
   ":post/is-mirror" true               ;; G5
   ":post/no-trade-notice" true         ;; G2 — トレードはしない
   ":post/server-held-key" false        ;; G7 / no-server-key
   ":post/author" author                ;; member DID (required only for a gated live post)
   ":post/sources" sources})            ;; G3

(defn draft-netflow-post
  "A dry-run post about where money is going / leaving (top inflow + top outflow bucket)."
  ([net-rows sources] (draft-netflow-post net-rows sources ""))
  ([net-rows sources author]
   (let [srcs (guard-sources sources)
         inflows (filter #(> (get % "net") 0) net-rows)
         outflows (filter #(< (get % "net") 0) net-rows)
         top-in (first inflows)
         top-out (when (seq outflows) (apply min-key #(get % "net") outflows))
         ;; parts = [DISCLAIMER ""] + [maybe top-in] + [maybe top-out] + [sources]
         parts (cond-> [DISCLAIMER ""]
                 top-in  (conj (str "資金流入トップ: " (get top-in "label") " (net +" (fmt-f (get top-in "net") 1) "bn)。"))
                 top-out (conj (str "資金流出トップ: " (get top-out "label") " (net " (fmt-f (get top-out "net") 1) "bn)。"))
                 :always (conj (str "出典 " (count srcs) " 件。")))
         body (if (> (count parts) 2)
                (str/join "\n\n" [(nth parts 0) (str/join " " (drop 2 parts))])
                DISCLAIMER)]
     (guard-no-trade body)
     (post-record "netflow" body srcs author))))

(defn draft-rotation-post
  "A dry-run post about the largest observed rotation pair (どこからどこへ)."
  ([rotation-rows sources] (draft-rotation-post rotation-rows sources ""))
  ([rotation-rows sources author]
   (let [srcs (guard-sources sources)
         top (first rotation-rows)
         body (if top
                (str DISCLAIMER "\n\n"
                     "最大の資金回転: " (get top "from_label") " → " (get top "to_label")
                     " (" (fmt-f (get top "magnitude") 1) "bn 相当)。"
                     "出典 " (count srcs) " 件。")
                (str DISCLAIMER "\n\n観測された資金回転はありません。出典 " (count srcs) " 件。"))]
     (guard-no-trade body)
     (post-record "rotation" body srcs author))))

(defn draft-regime-post
  "A dry-run post stating the FACTUAL cross-asset regime descriptor. Descriptive only."
  ([regime sources] (draft-regime-post regime sources ""))
  ([regime sources author]
   (let [srcs (guard-sources sources)
         jp (get {"risk-on" "リスクオン" "risk-off" "リスクオフ" "mixed" "まちまち"
                  "indeterminate" "判定不能"} (get regime "regime") (get regime "regime"))
         body (str DISCLAIMER "\n\n"
                   "クロスアセット観測: " jp " (" (get regime "regime") ") — リスク資産 net "
                   (fmt-sf (get regime "risk_net") 1) "bn / 安全資産 net " (fmt-sf (get regime "safe_net") 1) "bn。"
                   "記述であり助言ではありません。出典 " (count srcs) " 件。")]
     (guard-no-trade body)
     (post-record "regime" body srcs author))))

;; ── kanae export (1:1 of export.py to_kanae_flow / to_kanae_flows) ────────────────────────────
(defn- to-kanae-flow
  "One shionome capital-movement :flow → one kanae fundFlowEdge. Throws if the kind is
  observation-only (not a capital amount)."
  [f]
  (let [kind (#'w/kw* (get f ":flow/kind"))]
    (when-not (#'w/in-vec? w/CAPITAL-MOVEMENT-KINDS kind)
      (throw (ex-info (str "export: '" kind "' is an observation, not a capital flow (excluded from kanae render)") {})))
    {"edgeId" (str "shionome:" (str (get f ":flow/id" "?")))
     "flowType" kind
     "donor" (get f ":flow/source" "")
     "recipient" (get f ":flow/target" "")
     "amount" (#'w/to-finite-double (get f ":flow/magnitude" 0.0) "magnitude must be a number")
     "currency" (get f ":flow/unit" "")
     "asOf" (long (get f ":flow/as-of" 0))
     "noTrade" true
     "sources" (vec (get f ":flow/sources" []))}))

(defn to-kanae-flows
  "All capital-movement :flow → kanae flows; observation-only kinds skipped + counted."
  [g]
  (let [[flows skipped]
        (reduce (fn [[fs sk] f]
                  (if (#'w/in-vec? w/CAPITAL-MOVEMENT-KINDS (#'w/kw* (get f ":flow/kind")))
                    [(conj fs (to-kanae-flow f)) sk]
                    [fs (conj sk (get f ":flow/id"))]))
                [[] []] (get g "flows"))]
    {"flows" flows "skipped" skipped "skipped_count" (count skipped)}))

;; ── render payload JSON (1:1 of export.render_payload / render_json) ──────────────────────────
(defn- render-payload [c]
  {"actor" "shionome"
   "isMirror" true
   "noTrade" true
   "counts" (into {} (map (fn [k] [k (get c k)]) ["bucket_count" "flow_count" "snapshot_count"]))
   "net_flow_by_bucket" (get c "net_flow_by_bucket")
   "rotation_pairs" (get c "rotation_pairs")
   "inflow_shares" (mapv vec (get-in c ["inflow_concentration" "shares"]))
   "inflow_hhi" (get-in c ["inflow_concentration" "hhi"])
   "by_asset_class" (get c "by_asset_class")
   "by_region" (get c "by_region")
   "regime" (get c "regime")
   "correlation_clusters" (get c "correlation_clusters")})

(defn- json-sorted
  "json.dumps(..., ensure_ascii=False, sort_keys=True) — recursively sort map keys, then reuse
  the weave to-json scalar/float repr. (::order metadata is ignored; keys go alphabetical.)"
  [v]
  (cond
    (map? v) (str "{" (str/join ", " (map (fn [[k val]]
                                            (str (#'w/json-str k) ": " (json-sorted val)))
                                          (sort-by key (seq v)))) "}")
    (sequential? v) (str "[" (str/join ", " (map json-sorted v)) "]")
    :else (w/to-json v)))

(defn render-json
  "The render payload as a sort_keys=True JSON string (proves it is fully serializable)."
  [c]
  (json-sorted (render-payload c)))

;; ── the membrane (1:1 of analyze.run) ─────────────────────────────────────────────────────────
(defn run
  "Weave the seed graph → concentration → dry-run posts + kanae flows. Pure over a parsed graph.
  Returns {\"concentration\" c \"posts\" posts \"kanae_flows\" kf \"graph\" g}."
  [graph]
  (let [g (w/weave graph)
        c (w/concentration g)
        allsrcs (vec (sort (set (mapcat #(get % ":flow/sources" []) (get g "flows")))))
        posts (if (seq allsrcs)
                (cond-> []
                  (seq (get c "net_flow_by_bucket"))
                  (conj (draft-netflow-post (get c "net_flow_by_bucket") allsrcs))
                  (seq (get c "rotation_pairs"))
                  (conj (draft-rotation-post (get c "rotation_pairs") allsrcs))
                  :always
                  (conj (draft-regime-post (get c "regime") allsrcs)))
                [])]
    {"graph" g
     "concentration" c
     "posts" posts
     "kanae_flows" (to-kanae-flows g)}))

;; ── report rendering (1:1 of analyze._write_report f-strings) ─────────────────────────────────
(defn report-md
  "Render the intel-report markdown byte-for-byte with analyze.py's _write_report."
  [c posts]
  (let [L (transient
           ["# 潮目 (shionome) — cross-asset capital-flow intel (dry-run)\n"
            (str "_Observational mirror of where money moved — NOT financial advice, NOT a trade signal. "
                 "トレードはしない. :representative seed._\n")
            (str "\nbuckets=" (get c "bucket_count") " flows=" (get c "flow_count")
                 " snapshots=" (get c "snapshot_count") "\n")
            (str "\n_referential integrity: " (get-in c ["integrity" "dangling_count"]) " dangling reference(s)._\n")
            (str "\n## Cross-asset regime: **" (get-in c ["regime" "regime"]) "** "
                 "(risk_net=" (fmt-sf (get-in c ["regime" "risk_net"]) 1)
                 " / safe_net=" (fmt-sf (get-in c ["regime" "safe_net"]) 1) ") "
                 "— descriptive, not advice\n")
            "\n## Net flow by bucket (どこに資金が向かっているか)\n"])]
    (doseq [r (get c "net_flow_by_bucket")]
      (let [net (get r "net")
            arrow (cond (> net 0) "▲ in" (< net 0) "▼ out" :else "= flat")]
        (conj! L (str "- **" (get r "label") "** — net " (fmt-sf net 2) " " arrow
                      " (in " (fmt-f (get r "inflow") 2) " / out " (fmt-f (get r "outflow") 2) ")"))))
    (conj! L "\n## Rotation pairs (どこからどこへ)\n")
    (doseq [r (get c "rotation_pairs")]
      (conj! L (str "- " (get r "from_label") " → " (get r "to_label") ": " (fmt-f (get r "magnitude") 2))))
    (when-not (seq (get c "rotation_pairs"))
      (conj! L "- (none in seed)"))
    (conj! L "\n## By asset class\n")
    (doseq [r (get c "by_asset_class")]
      (conj! L (str "- **" (get r "asset_class") "** — net " (fmt-sf (get r "net") 2)
                    " (" (get r "buckets") " bucket(s))")))
    (conj! L "\n## By region\n")
    (doseq [r (get c "by_region")]
      (conj! L (str "- **" (get r "region") "** — net " (fmt-sf (get r "net") 2)
                    " (" (get r "buckets") " bucket(s))")))
    (let [ic (get c "inflow_concentration")]
      (conj! L (str "\n## Inflow concentration — HHI=" (w/to-json (get ic "hhi"))
                    " over total " (fmt-f (get ic "total") 2) "\n"))
      (doseq [[bucket share] (get ic "shares")]
        (conj! L (str "- `" bucket "`: " (fmt-f (* share 100) 1) "%"))))
    (conj! L "\n## Correlation clusters\n")
    (doseq [cl (get c "correlation_clusters")]
      (conj! L (str "- {" (str/join ", " (get cl "members")) "} (size " (get cl "size") ")")))
    (when-not (seq (get c "correlation_clusters"))
      (conj! L "- (none in seed)"))
    (conj! L "\n## Dry-run social posts\n")
    (doseq [p posts]
      (conj! L (str "> " (get p ":post/body") "\n>\n> _status=" (get p ":post/status")
                    " isMirror=" (if (get p ":post/is-mirror") "True" "False")
                    " noTrade=" (if (get p ":post/no-trade-notice") "True" "False")
                    " serverHeldKey=" (if (get p ":post/server-held-key") "True" "False")
                    " sources=" (count (get p ":post/sources")) "_\n")))
    (str (str/join "\n" (persistent! L)) "\n")))

;; ── derived capital-flow-graph edn (1:1 of analyze._write_graph) ──────────────────────────────
(defn graph-edn
  "Emit the validated capital-flow graph as derived edn (the kotoba-ingest body shape)."
  [g]
  (let [head [";; GENERATED by shionome analyze.py — validated capital-flow graph (do not hand-edit)"
              "{:graph {:name \"shionome-capital-flow-v1\" :visibility :public}"
              " :flows ["]
        flows (map (fn [f]
                     (str "  {:flow/id \"" (get f ":flow/id") "\" :flow/source \"" (get f ":flow/source") "\" "
                          ":flow/target \"" (get f ":flow/target") "\" :flow/kind " (get f ":flow/kind") " "
                          ":flow/magnitude " (w/to-json (#'w/to-finite-double (get f ":flow/magnitude" 0.0) "x")) " :flow/no-trade-notice true}"))
                   (get g "flows"))
        lines (concat head flows [" ]}"])]
    (str (str/join "\n" lines) "\n")))

#?(:clj
   (defn -main
     "CLI: weave the seed → write out/intel-report.md + out/capital-flow-graph.kotoba.edn +
     out/kanae-render.json. Byte-parity target = intel-report.md (matches analyze.py)."
     [& argv]
     (let [argv (vec argv)
           methods-dir (delay (-> *file* clojure.java.io/file .getParentFile))
           seed (if (and (seq argv) (not (str/starts-with? (first argv) "--")))
                  (clojure.java.io/file (first argv))
                  (clojure.java.io/file @methods-dir ".." "data" "seed-capital-flow-graph.kotoba.edn"))
           outdir (if (some #{"--out"} argv)
                    (clojure.java.io/file (nth argv (inc (.indexOf argv "--out"))))
                    (clojure.java.io/file @methods-dir "out"))
           res (run (sedn/load-edn seed))
           c (get res "concentration")
           g (get res "graph")]
       (.mkdirs outdir)
       (spit (clojure.java.io/file outdir "intel-report.md") (report-md c (get res "posts")))
       (spit (clojure.java.io/file outdir "capital-flow-graph.kotoba.edn") (graph-edn g))
       (spit (clojure.java.io/file outdir "kanae-render.json") (render-json c))
       (println (str "# shionome analyze — buckets=" (get c "bucket_count")
                     " flows=" (get c "flow_count") " snapshots=" (get c "snapshot_count")))
       0)))
