(ns tate.methods.coverage-report
  "tate 盾 — honest jurisdiction-coverage report (G10, ADR-2606112400).
  1:1 Clojure port of `methods/coverage_report.py`.

  Per-jurisdiction clause-pattern + procedure counts, the covered/uncovered ratio against
  the ~193 UN member states, and a NAMED gap list that doubles as the ingest worklist.

  House style: ':…' strings stay strings; pure fns; HALF_EVEN round via exact BigDecimal;
  Python f'{x:.2%}' / f'{n:,}' matched exactly. Portable .cljc."
  (:require [clojure.string :as str]
            [tate.methods.terms-scan :as terms]
            [tate.methods.respond-plan :as respond]))

(def un-member-states 193)

;; next-wave jurisdiction worklist — entries DROP OFF automatically once covered
(def juris-worklist
  [":it" ":es" ":nl" ":kr" ":fr" ":cn" ":tw" ":in"
   ":br" ":au" ":ca" ":sg" ":mx"
   ":dk" ":fi" ":ie" ":be" ":ch" ":no"
   ":ar" ":cl"])

;; structural gaps — true regardless of how many jurisdictions land
(def structural-gaps
  [":eu は越境 instruments のみ (加盟国国内法は各国エントリで個別収載)"
   "刑事手続は全管轄でスコープ外 (N6 — 即時弁護士照会のみ)"])

(def us-states-total 50)
(def specialty-tracks-planned [])  ;; all planned tracks opened

(defn- counter
  "Counter(seq) → map value->count (mirrors collections.Counter / defaultdict(int))."
  [coll]
  (reduce (fn [m v] (update m v (fnil inc 0))) {} coll))

(defn- round-half-even
  "Python round(x, n) — HALF_EVEN (banker's rounding) via exact BigDecimal."
  [x n]
  #?(:clj (.doubleValue (.setScale (java.math.BigDecimal. (double x)) (int n)
                                   java.math.RoundingMode/HALF_EVEN))
     :cljs (let [f (Math/pow 10 n)] (/ (Math/round (* x f)) f))))

(defn- pct2
  "Python f'{x:.2%}' — multiply by 100, 2 fraction digits (HALF_EVEN), trailing %."
  [x]
  #?(:clj (str (.toPlainString
                (.setScale (.multiply (java.math.BigDecimal. (double x))
                                      (java.math.BigDecimal. "100"))
                           2 java.math.RoundingMode/HALF_EVEN))
               "%")
     :cljs (str (.toFixed (* x 100) 2) "%")))

(defn coverage
  ([] (coverage (terms/load-patterns) (respond/load-procs)
                (respond/load-jurisdictions) (respond/load-us-states)))
  ([patterns procs juris states]
   (let [pat-by-j (counter (map #(get % ":clause/jurisdiction" ":jp") patterns))
         proc-by-j (counter (map #(get % ":proc/jurisdiction" ":jp") procs))
         covered (sort (keys juris))
         remaining (filterv #(not (contains? juris %)) juris-worklist)
         us-state-gap (if (>= (count states) us-states-total)
                        (str ":us 州レベル: 全" us-states-total "州収載 — 次の課題は改正追跡 "
                             "(:verify-current-law) と DC/準州")
                        (str ":us 州レベル: " (count states) "/" us-states-total " 州を収載 — "
                             "残り" (- us-states-total (count states)) "州は『州不明』honest degrade"))
         tracks (counter (map #(get % ":proc/track" ":civil") procs))
         ;; juris → track → count (insertion order then sorted, like the Python sorted())
         matrix (reduce (fn [m p]
                          (update-in m [(get p ":proc/jurisdiction" ":jp")
                                        (get p ":proc/track" ":civil")]
                                     (fnil inc 0)))
                        {} procs)
         track-counts (str ":labor " (get tracks ":labor" 0) " / :housing " (get tracks ":housing" 0)
                           " / :enforcement " (get tracks ":enforcement" 0) " / "
                           ":insolvency " (get tracks ":insolvency" 0) " / "
                           ":family " (get tracks ":family" 0))
         track-gap (if (seq specialty-tracks-planned)
                     (str "専門トラック: " track-counts " 件収載 — "
                          (str/join " / " specialty-tracks-planned) " 未収載")
                     (str "専門トラック: " track-counts " 件 — 計画トラックは全て開削済み; "
                          "次の深化は各トラックの管轄横展開 (多くは jp/us/de の3管轄のみ)"))
         civil-only (sort (for [[j ts] matrix
                                :when (and (= (set (keys ts)) #{":civil"}) (not= j ":eu"))]
                            j))
         civil-only-gap (if (seq civil-only)
                          (str "専門トラック未開削の管轄 (civil のみ): " (str/join " " civil-only))
                          "全管轄に専門トラックあり (:eu は越境 instruments のみで対象外)")
         ;; provenance: how many clause patterns carry a verified primary-source URL in the
         ;; data itself (G10 — only checked URLs recorded; the rest carry anchor text only)
         src-count (count (filter #(get % ":clause/source-url") patterns))
         pat-total (count patterns)
         proc-src-count (count (filter (fn [p] (some #(get % ":dl/source-url")
                                                     (get p ":proc/deadline-rules" [])))
                                       procs))
         proc-total (count procs)
         source-url-gap (if (and (>= src-count pat-total) (>= proc-src-count proc-total))
                          (str ":source-url 出典: clause " src-count "/" pat-total
                               " + proc " proc-src-count "/" proc-total
                               " 全エントリに一次ソース URL を記録")
                          (str ":source-url 出典: clause " src-count "/" pat-total
                               " + proc " proc-src-count "/" proc-total
                               " に一次ソース URL を記録 — 残りは anchor のみ "
                               "(worklist; 一次ソース検証後に付与、推測 URL は入れない G10)"))
         named-gaps (concat (map #(str % " — 未収載 (worklist)") remaining)
                            [us-state-gap track-gap civil-only-gap source-url-gap]
                            structural-gaps)
         all-tracks-order [":civil" ":labor" ":housing" ":enforcement" ":insolvency" ":family"]
         sort-track-map (fn [ts] (into (sorted-map) ts))]
     {"us_states_covered" (count states)
      "us_states_total" us-states-total
      "procedure_tracks" (into (sorted-map) tracks)
      "track_matrix" (into (sorted-map) (for [[j ts] matrix] [j (sort-track-map ts)]))
      "civil_only_jurisdictions" (vec civil-only)
      "_procs" procs
      "critical_deadlines"
      (vec (for [p procs dl (get p ":proc/deadline-rules" [])
                 :when (get dl ":dl/critical")]
             {"proc" (get p ":proc/id") "juris" (get p ":proc/jurisdiction" ":jp")
              "label" (get dl ":dl/label") "anchor" (get dl ":dl/anchor")}))
      "jurisdictions" (vec covered)
      "patterns_by_jurisdiction" (into (sorted-map) pat-by-j)
      "procedures_by_jurisdiction" (into (sorted-map) proc-by-j)
      "covered_count" (count covered)
      "clause_source_url_count" src-count
      "clause_total" pat-total
      "proc_source_url_count" proc-src-count
      "proc_total" proc-total
      "un_member_states" un-member-states
      "coverage_ratio" (round-half-even (/ (double (count covered)) un-member-states) 4)
      "worklist_remaining" (vec remaining)
      "named_gaps" (vec named-gaps)})))

(defn- lstrip-colon [s] (if (str/starts-with? s ":") (subs s 1) s))

(defn report
  [cov]
  (let [L (transient ["# tate 盾 — jurisdiction coverage (honest — G10)" ""])]
    (conj! L (str "- covered: " (get cov "covered_count") " legal systems "
                  "(" (str/join ", " (get cov "jurisdictions")) ") of ~" (get cov "un_member_states")
                  " UN states → ratio ≈ " (pct2 (get cov "coverage_ratio"))
                  " (低いのは仕様 — 推測より空白)"))
    (conj! L (str "- :us 州レベル: " (get cov "us_states_covered") "/" (get cov "us_states_total")
                  " 州 (州不明の通知は honest degrade)"))
    (conj! L "")
    (conj! L "| juris | clause patterns | procedures |")
    (conj! L "|---|---|---|")
    (doseq [j (get cov "jurisdictions")]
      (conj! L (str "| " j " | " (get (get cov "patterns_by_jurisdiction") j 0)
                    " | " (get (get cov "procedures_by_jurisdiction") j 0) " |")))
    (conj! L "")
    (conj! L "## Track × jurisdiction matrix (横展開ギャップの可視化)")
    (conj! L "")
    (let [all-tracks [":civil" ":labor" ":housing" ":enforcement" ":insolvency" ":family"]]
      (conj! L (str "| juris | " (str/join " | " (map lstrip-colon all-tracks)) " |"))
      (conj! L (str "|---|" (apply str (repeat (count all-tracks) "---|"))))
      (doseq [[j ts] (get cov "track_matrix")]
        (conj! L (str "| " j " | "
                      (str/join " | " (map #(str (get ts % "·")) all-tracks)) " |")))
      (let [matrix (get cov "track_matrix")
            n-juris (max 1 (count matrix))
            depth (str/join " · "
                            (for [t [":labor" ":housing" ":enforcement" ":insolvency" ":family"]]
                              (str (lstrip-colon t) " "
                                   (count (filter #(pos? (get % t 0)) (vals matrix)))
                                   "/" n-juris)))]
        (conj! L "")
        (conj! L (str "track depth (管轄横展開率): " depth))
        (let [n-protective (count (for [p (get cov "_procs") o (get p ":proc/options")
                                        :when (= (get o ":opt/protective") true)] o))]
          (conj! L (str "protective options (member を守る一手): " n-protective)))))
    (conj! L "")
    (conj! L "## Critical deadlines (徒過で権利が消える期限 — 全管轄一覧)")
    (conj! L "")
    (doseq [cd (get cov "critical_deadlines")]
      (conj! L (str "- [" (get cd "juris") "] " (get cd "proc") " — " (get cd "label")
                    " (" (get cd "anchor") ")")))
    (conj! L "")
    (conj! L "## Named gaps (next-wave worklist)")
    (doseq [g (get cov "named_gaps")]
      (conj! L (str "- " g)))
    (conj! L "")
    (conj! L (str "未カバー管轄の通知は :unknown-jurisdiction に honest degrade し、"
                  "現地法を推測せず証拠保全 + 専門家照会のみを案内する (respond_plan G10)。"))
    (str (str/join "\n" (persistent! L)) "\n")))
