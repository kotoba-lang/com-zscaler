(ns shionome.methods.social
  "social.cljc — 潮目 (shionome) DRY-RUN social-post projection. ADR-2606072200.
  Clojure port of methods/social.py (1:1).

  Projects an AGGREGATE capital-flow finding into a social post
  (app.bsky.feed.post-shaped), enforcing the post invariants in their third home:

    G2 — THE DEFINING INVARIANT (トレードはしない): noTradeNotice=true; the body is
         SCANNED for trade/advisory tokens and REFUSED if any appear (the disclaimer,
         which NAMES the prohibited acts, is stripped before the scan so it is exempt).
    G5 — every post opens with the observational-mirror disclaimer (isMirror=true).
    G7 — serverHeldKey=false; the member signs, the server never does.
    G8 — status is ':dry-run' only at R0; ':published' is unrepresentable; build-live raises.
    G3 — the post carries the same ≥2 public-source citations as the finding.

  Stdlib only. Deterministic. Float formatting via fmt1/fmt1+ (cljc-safe;
  the suite never byte-compares the rendered numbers)."
  (:require [clojure.string :as str]
            [shionome.methods.weave :as weave]))

(def DISCLAIMER
  (str "【観測ミラー / capital-flow observation — NOT financial advice, トレードはしない】 "
       "公開市場データから観測した資金フローの集計です。売買の推奨・目標価格・ポジション提案は一切しません。"))

(defn- fmt1
  "Format a number to 1 decimal place (Python `{:.1f}`)."
  [x]
  #?(:clj  (format "%.1f" (double x))
     :cljs (.toFixed (double x) 1)))

(defn- fmt1+
  "Format with an explicit leading sign (Python `{:+.1f}`)."
  [x]
  (let [s (fmt1 x)] (if (str/starts-with? s "-") s (str "+" s))))

(defn- guard-sources
  "G3 — ≥2 non-blank citations, none a commercial market-data terminal."
  [sources]
  (let [s (vec (remove (fn [x] (str/blank? (str x))) (or sources [])))]
    (when (< (count s) 2)
      (throw (ex-info "G3: a post needs ≥2 public-source citations" {})))
    (let [d (weave/source-denied s)]
      (when (seq d)
        (throw (ex-info (str "Rider §2(e)/N5: source " (pr-str d)
                             " is a commercial market-data terminal — a post may not cite it")
                        {:denied d}))))
    s))

(defn guard-no-trade
  "G2 core (トレードはしない) — refuse a post whose body contains a trade/advisory token.
  The disclaimer text is exempt (it NAMES the prohibited acts to disclaim them)."
  [body]
  (let [scanned (str/replace (str body) DISCLAIMER "")
        t (weave/trade-token-in scanned)]
    (when (seq t)
      (throw (ex-info (str "G2: post body contains the trade/advisory token " (pr-str t)
                           " — refused (shionome never recommends a trade; it only observes "
                           "flows). トレードはしない.")
                      {:token t})))))

(defn- post
  "Assemble a networkPost record with every invariant pinned. status is ALWAYS :dry-run."
  [subject body sources author]
  {":post/subject"         subject
   ":post/body"            body
   ":post/status"          ":dry-run"        ; G8 — published is unrepresentable
   ":post/is-mirror"       true              ; G5
   ":post/no-trade-notice" true              ; G2 — トレードはしない
   ":post/server-held-key" false             ; G7 / no-server-key
   ":post/author"          author            ; member DID (required only for a gated live post)
   ":post/sources"         sources})         ; G3

(defn draft-netflow-post
  "A dry-run post about where money is going / leaving (top inflow + top outflow bucket)."
  ([net-rows sources] (draft-netflow-post net-rows sources ""))
  ([net-rows sources author]
   (let [srcs    (guard-sources sources)
         inflows (filter #(> (get % "net") 0) net-rows)
         outflows (filter #(< (get % "net") 0) net-rows)
         top-in  (first inflows)
         top-out (when (seq outflows) (apply min-key #(get % "net") outflows))
         content (cond-> []
                   top-in  (conj (str "資金流入トップ: " (get top-in "label")
                                      " (net +" (fmt1 (get top-in "net")) "bn)。"))
                   top-out (conj (str "資金流出トップ: " (get top-out "label")
                                      " (net " (fmt1 (get top-out "net")) "bn)。"))
                   true    (conj (str "出典 " (count srcs) " 件。")))
         body    (if (seq content) (str DISCLAIMER "\n\n" (str/join " " content)) DISCLAIMER)]
     (guard-no-trade body)
     (post "netflow" body srcs author))))

(defn draft-rotation-post
  "A dry-run post about the largest observed rotation pair (どこからどこへ)."
  ([rotation-rows sources] (draft-rotation-post rotation-rows sources ""))
  ([rotation-rows sources author]
   (let [srcs (guard-sources sources)
         top  (first rotation-rows)
         body (if top
                (str DISCLAIMER "\n\n"
                     "最大の資金回転: " (get top "from_label") " → " (get top "to_label")
                     " (" (fmt1 (get top "magnitude")) "bn 相当)。出典 " (count srcs) " 件。")
                (str DISCLAIMER "\n\n観測された資金回転はありません。出典 " (count srcs) " 件。"))]
     (guard-no-trade body)
     (post "rotation" body srcs author))))

(defn draft-regime-post
  "A dry-run post stating the FACTUAL cross-asset regime descriptor (risk-on/off/mixed)."
  ([regime sources] (draft-regime-post regime sources ""))
  ([regime sources author]
   (let [srcs (guard-sources sources)
         r    (get regime "regime")
         jp   (get {"risk-on" "リスクオン" "risk-off" "リスクオフ" "mixed" "まちまち"
                    "indeterminate" "判定不能"} r r)
         body (str DISCLAIMER "\n\n"
                   "クロスアセット観測: " jp " (" r ") — リスク資産 net " (fmt1+ (get regime "risk_net"))
                   "bn / 安全資産 net " (fmt1+ (get regime "safe_net"))
                   "bn。記述であり助言ではありません。出典 " (count srcs) " 件。")]
     (guard-no-trade body)
     (post "regime" body srcs author))))

(defn build-live
  "G8 — live posting is outward-gated. Refuses by construction at R0."
  [& _]
  (throw (ex-info (str "shionome R0: live social posting is Council Lv6+ + operator + "
                       "member-signature gated (G8). Only dry-run posts are producible offline.")
                  {:gate :G8})))
