(ns tate.methods.site-gen
  "tate 盾 — crawlable static site generator (wave 35, R2).
  1:1 Clojure port of `methods/site_gen.py`.

  Projects the registry into human-readable, crawlable static HTML: 1 法域 = 1 page +
  index (coverage matrix + critical census) + per-track landing pages, with schema.org
  FAQPage JSON-LD, sitemap.xml, robots.txt.

  CONSTITUTIONAL: G2/G3 disclaimer on every page; no ad/tracking/external asset; every
  anchor carries a 改正確認 note; deployment is an operator step.

  House style: data maps stay string-keyed; ':…' keyword strings stay strings; file I/O
  only behind #?(:clj …). The Python __main__ demo printer is omitted.

  Parity: `html.escape(s, quote=True)` (escapes & < > \" ') and `json.dumps(d,
  ensure_ascii=False)` (Python default separators ', ' / ': ', raw unicode) are mirrored."
  (:require [clojure.string :as str]
            [tate.methods.terms-scan :as ts]
            [tate.methods.respond-plan :as rp]
            [tate.methods.coverage-report :as cr]))

(def BASE-DEFAULT "https://etzhayyim.com/tate")

(def DISCLAIMER
  (str "本ページは <strong>一般的な法情報 (legal information)</strong> であり、個別の法的助言"
       " (legal advice) ではありません。tate 盾 は条項・手続きを<strong>開示済みの法令アンカー</strong>"
       "に対応付けるだけで、有効・無効の判断はしません (非裁定)。期限の起算点 (送達日) は必ず"
       "ご自身で確認し、重要な判断は各法域の専門家・無料相談窓口へ。法令は改正されます — "
       "アンカーは現行条文で必ず確認してください。"))

(def CSS
  (str "body{font-family:sans-serif;max-width:50em;margin:1em auto;padding:0 1em;line-height:1.6}"
       "h1,h2{border-bottom:1px solid #ccc}.crit{color:#b00;font-weight:bold}"
       ".box{background:#f6f6f6;border-left:4px solid #888;padding:.5em 1em;margin:1em 0}"
       "table{border-collapse:collapse}td,th{border:1px solid #ccc;padding:.2em .6em}"
       "footer{margin-top:2em;font-size:.85em;color:#555}"))

(defn- escape
  "Python html.escape(s, quote=True)."
  [s]
  (-> (str s)
      (str/replace "&" "&amp;")
      (str/replace "<" "&lt;")
      (str/replace ">" "&gt;")
      (str/replace "\"" "&quot;")
      (str/replace "'" "&#x27;")))

;; ── json.dumps(value, ensure_ascii=False) — Python default separators ", " / ": " ──
(defn- json-escape-utf8 [s]
  (str/escape s {\" "\\\"" \\ "\\\\"
                 \backspace "\\b" \tab "\\t" \newline "\\n" \formfeed "\\f" \return "\\r"}))

(defn- json-dumps [v]
  (cond
    (string? v) (str "\"" (json-escape-utf8 v) "\"")
    (boolean? v) (if v "true" "false")
    (nil? v) "null"
    (integer? v) (str v)
    (number? v) (str v)
    (map? v) (str "{" (str/join ", " (map (fn [[k val]]
                                            (str (json-dumps (str k)) ": " (json-dumps val)))
                                          v)) "}")
    (sequential? v) (str "[" (str/join ", " (map json-dumps v)) "]")
    :else (str "\"" (json-escape-utf8 (str v)) "\"")))

(defn- page [title desc body canonical jsonld]
  (let [ld (if jsonld
             (str "<script type=\"application/ld+json\">" (json-dumps jsonld) "</script>")
             "")]
    (str "<!DOCTYPE html>\n<html lang=\"ja\">\n<head>\n<meta charset=\"utf-8\">\n"
         "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">\n"
         "<title>" (escape title) "</title>\n"
         "<meta name=\"description\" content=\"" (escape desc) "\">\n"
         "<link rel=\"canonical\" href=\"" canonical "\">\n"
         "<style>" CSS "</style>\n"
         ld "\n</head>\n<body>\n"
         "<div class=\"box\">" DISCLAIMER "</div>\n"
         body "\n"
         "<footer>tate 盾 — etzhayyim citizen legal-defense concierge · 広告・トラッキングなし ·\n"
         "ソース: <a href=\"https://github.com/etzhayyim/root/tree/main/20-actors/tate\">github.com/etzhayyim/root</a>\n"
         "(Apache 2.0 + Charter Rider)</footer>\n</body>\n</html>\n")))

(defn juris-page [jid juris procs patterns base]
  (let [label (get juris ":juris/label")
        my-procs (filterv #(= (get % ":proc/jurisdiction" ":jp") jid) procs)
        my-pats (filterv #(= (get % ":clause/jurisdiction" ":jp") jid) patterns)
        B (transient [(str "<h1>" (escape label) " — 受け取った法的通知への応答ガイド</h1>")])
        faq (transient [])]
    (conj! B (str "<p>詐欺通知の見分け方: 本物の書類の経路は「" (escape (get juris ":juris/service-note")) "」。"
                  "疑わしい場合は送信者に接触せず: " (escape (str/join " / " (get juris ":juris/fake-help"))) "</p>"))
    (conj! B "<h2>手続きと期限</h2>")
    (doseq [p my-procs]
      (conj! B (str "<h3>" (escape (get p ":proc/label")) "</h3><ul>"))
      (doseq [dl (get p ":proc/deadline-rules" [])]
        (let [crit (if (get dl ":dl/critical") " class=\"crit\"" "")
              mark (if (get dl ":dl/critical") "⚠ " "")]
          (conj! B (str "<li" crit ">" mark "<strong>" (escape (get dl ":dl/label")) "</strong>: "
                        (escape (get dl ":dl/rule")) " <em>(" (escape (get dl ":dl/anchor"))
                        " — 要改正確認)</em></li>"))
          (conj! faq {"@type" "Question"
                      "name" (str (get p ":proc/label") " — " (get dl ":dl/label"))
                      "acceptedAnswer" {"@type" "Answer"
                                        "text" (str (get dl ":dl/rule") " (根拠: " (get dl ":dl/anchor")
                                                    "。法的助言ではありません — 専門家に確認を)")}})))
      (doseq [o (get p ":proc/options" [])]
        (let [star (if (get o ":opt/protective") "🛡 " "")]
          (conj! B (str "<li>" star (escape (get o ":opt/label")) "</li>"))))
      (let [sl (str "tate-" (second (str/split (get p ":proc/id") #":" 2)))
            root (if (str/ends-with? base "/tate") (subs base 0 (- (count base) 5)) base)]
        (conj! B (str "<li>DL: <a href=\"" root "/actor/" sl "/checklist.md\">チェックリスト</a> · "
                      "<a href=\"" root "/actor/" sl "/case.json\">データ (JSON)</a> · "
                      "<a href=\"" root "/actor/" sl "/profile.json\">case actor profile</a></li>")))
      (conj! B (str "<li>相談先: " (escape (str/join " / " (get p ":proc/refer-when" []))) "</li></ul>")))
    (when (seq my-pats)
      (conj! B "<h2>契約の不利条項パターン (非裁定 — 可能性の指摘のみ)</h2><ul>")
      (doseq [p my-pats]
        (conj! B (str "<li><strong>" (escape (get p ":clause/label")) "</strong> — "
                      (escape (get p ":clause/anchor")) "</li>")))
      (conj! B "</ul>"))
    (conj! B (str "<p>無料相談: " (escape (str/join " / " (get juris ":juris/referrals"))) "</p>"))
    (conj! B (str "<p><a href=\"" base "/index.html\">← 全法域一覧</a></p>"))
    (let [faq-v (persistent! faq)
          jsonld (when (seq faq-v) {"@context" "https://schema.org" "@type" "FAQPage" "mainEntity" faq-v})
          native (reduce (fn [acc p]
                           (let [head (first (str/split (get p ":proc/label") #" \(" 2))]
                             (if (some #{head} acc) acc (conj acc head))))
                         [] my-procs)
          kw (str/join "・" (take 4 native))
          desc (if (not= kw "")
                 (str label ": " kw " などへの応答期限・防御選択肢・無料相談先 — 非裁定の法情報")
                 (str label ": 法的通知への応答期限と無料相談先 — 非裁定の法情報"))
          title (if (not= kw "")
                  (str label " — " kw " 応答ガイド | tate 盾")
                  (str label " — 法的通知への応答ガイド | tate 盾"))]
      (page title desc (str/join "\n" (persistent! B))
            (str base "/" (subs jid 1) ".html") jsonld))))

(def TRACK-LABELS
  {":labor" "解雇・労働" ":housing" "立退き・賃貸借" ":enforcement" "差押え・強制執行"
   ":insolvency" "取引先の倒産 (債権者側)" ":family" "離婚・家事"})
;; insertion-ordered for iteration (Python dict preserves insertion order)
(def ^:private TRACK-ORDER [":labor" ":housing" ":enforcement" ":insolvency" ":family"])

(defn track-page [track juris procs base]
  (let [label (get TRACK-LABELS track)
        my (filterv #(= (get % ":proc/track") track) procs)
        B (transient
           [(str "<h1>" (escape label) " — 管轄×期限の比較表</h1>")
            (str "<p>" (count my) "管轄の応答期限と防御の一手を1ページで比較 (詳細・DL は各法域ページ/case actor へ)。</p>")
            "<table><tr><th>管轄</th><th>手続き</th><th>主要期限 (⚠=失権)</th><th>守る一手 🛡</th></tr>"])]
    (doseq [p my]
      (let [jid (get p ":proc/jurisdiction" ":jp")
            jl (get-in juris [jid ":juris/label"])
            dls (get p ":proc/deadline-rules" [])
            d0 (first dls)
            crit (if (and d0 (get d0 ":dl/critical")) "⚠ " "")
            rule (escape (cond
                           (and d0 (> (count (get d0 ":dl/rule")) 80))
                           (str (subs (get d0 ":dl/rule") 0 80) "…")
                           d0 (get d0 ":dl/rule")
                           :else "—"))
            prot0 (or (some #(when (get % ":opt/protective") (get % ":opt/label"))
                            (get p ":proc/options" [])) "—")
            prot (escape (if (> (count prot0) 60) (str (subs prot0 0 60) "…") prot0))]
        (conj! B (str "<tr><td><a href=\"" base "/" (subs jid 1) ".html\">" (escape jl) "</a></td>"
                      "<td>" (escape (get p ":proc/label")) "</td>"
                      "<td>" crit rule "</td><td>🛡 " prot "</td></tr>"))))
    (conj! B "</table>")
    (conj! B (str "<p><a href=\"" base "/index.html\">← 全法域一覧</a></p>"))
    (page (str label " — 世界" (count my) "管轄の期限比較 | tate 盾")
          (str label ": 各国の応答期限・失権期限・member を守る一手の比較表 (非裁定の法情報)")
          (str/join "\n" (persistent! B)) (str base "/track-" (subs track 1) ".html") nil)))

(defn index-page [juris cov base]
  (let [B (transient
           ["<h1>tate 盾 — 世界の法的通知 応答ガイド (非裁定)</h1>"
            (str "<p>" (get cov "covered_count") "法域 + 米国全" (get cov "us_states_total") "州を収載。"
                 "受け取った通知 (支払督促・解雇・立退き・差押え・倒産・離婚) の期限・防御選択肢・無料相談先。</p>")
            "<h2>法域一覧</h2><ul>"])]
    (doseq [jid (get cov "jurisdictions")]
      (conj! B (str "<li><a href=\"" base "/" (subs jid 1) ".html\">"
                    (escape (get-in juris [jid ":juris/label"])) "</a></li>")))
    (let [root (if (str/ends-with? base "/tate") (subs base 0 (- (count base) 5)) base)]
      (conj! B (str "</ul><p>全 case の actor 索引: <a href=\"" root "/actor/tate/cases.json\">cases.json</a> "
                    "(1手続き=1 case actor — profile から checklist/データ DL と相談先へ)</p>")))
    (conj! B "<h2>⚠ 徒過で権利が消える期限 (critical census)</h2><ul>")
    (doseq [cd (get cov "critical_deadlines")]
      (conj! B (str "<li class=\"crit\">[" (get cd "juris") "] " (escape (get cd "label"))
                    " (" (escape (get cd "anchor")) ")</li>")))
    (conj! B "</ul>")
    (page "tate 盾 — 世界の法的通知 応答ガイド (30法域+米50州)"
          "支払督促・解雇通知・立退き・差押え・倒産・離婚 — 30法域の応答期限と無料相談先 (非裁定の法情報)"
          (str/join "\n" (persistent! B)) (str base "/index.html") nil)))

#?(:clj
   (defn generate
     "Write the site to outdir; returns the page-name list (insertion order)."
     ([outdir] (generate outdir BASE-DEFAULT))
     ([outdir base]
      (let [juris (rp/load-jurisdictions)
            procs (rp/load-procs)
            patterns (ts/load-patterns)
            cov (cr/coverage)
            outdir (clojure.java.io/file outdir)]
        (.mkdirs outdir)
        (let [pages (transient [])]
          (spit (clojure.java.io/file outdir "index.html") (index-page juris cov base))
          (conj! pages "index.html")
          ;; for jid, j in juris.items() — preserve registry insertion order
          (doseq [jid (sort (keys juris))]
            (let [j (get juris jid)
                  name (str (subs jid 1) ".html")]
              (spit (clojure.java.io/file outdir name) (juris-page jid j procs patterns base))
              (conj! pages name)))
          (doseq [tk TRACK-ORDER]
            (let [name (str "track-" (subs tk 1) ".html")]
              (spit (clojure.java.io/file outdir name) (track-page tk juris procs base))
              (conj! pages name)))
          (let [ps (persistent! pages)]
            (spit (clojure.java.io/file outdir "sitemap.xml")
                  (str "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                       "<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">\n"
                       (str/join "\n" (map #(str "  <url><loc>" base "/" % "</loc></url>") ps))
                       "\n</urlset>\n"))
            (spit (clojure.java.io/file outdir "robots.txt")
                  (str "User-agent: *\nAllow: /\nSitemap: " base "/sitemap.xml\n"))
            ps))))))
