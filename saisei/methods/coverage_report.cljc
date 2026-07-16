(ns saisei.methods.coverage-report
  "saisei 再生 — honest jurisdiction-coverage report (G10, ADR-2607061800).

  Per-jurisdiction procedure counts, the covered/uncovered ratio against the ~193
  UN member states, and a NAMED gap list that doubles as the next-wave ingest
  worklist (tate precedent, ADR-2606112400)."
  (:require [clojure.string :as str]
            [saisei.methods.edn :as edn]
            [saisei.methods.filing-plan :as plan]))

(def un-member-states 193)

;; next-wave jurisdiction worklist — entries DROP OFF automatically once covered
(def juris-worklist
  [":kr" ":fr" ":it" ":es" ":nl" ":au" ":ca" ":sg" ":br" ":in" ":cn"
   ":pl" ":se" ":at" ":pt" ":ie" ":ch" ":dk" ":fi" ":no" ":mx" ":be" ":ar" ":nz" ":tw"])

(def structural-gaps
  ["個人再生/民事再生に相当する制度が無い法域では自己破産型の1トラックのみ収載予定"
   "IVA (UK) は認可保険数理士(insolvency practitioner)必須のため self-file 対象外 — referral-only track として別途検討"])

(defn- round-half-even
  [x n]
  #?(:clj (.doubleValue (.setScale (java.math.BigDecimal. (double x)) (int n)
                                   java.math.RoundingMode/HALF_EVEN))
     :cljs (let [f (Math/pow 10 n)] (/ (Math/round (* x f)) f))))

(defn- pct2
  [x]
  #?(:clj (str (.toPlainString
                (.setScale (.multiply (java.math.BigDecimal. (double x))
                                      (java.math.BigDecimal. "100"))
                           2 java.math.RoundingMode/HALF_EVEN))
               "%")
     :cljs (str (.toFixed (* x 100) 2) "%")))

(defn- counter [coll]
  (reduce (fn [m v] (update m v (fnil inc 0))) {} coll))

(defn coverage
  ([] (coverage (plan/load-procs) (plan/load-jurisdictions)))
  ([procs juris]
   (let [proc-by-j (counter (map #(get % ":proc/jurisdiction" ":jp") procs))
         covered (sort (keys juris))
         remaining (filterv #(not (contains? juris %)) juris-worklist)
         ;; :proc/mandatory-preconditions / :proc/timeline-rules may be
         ;; datomize-transform blob-string attribute values (see
         ;; filing-plan/track-plan docstring) — edn/unblob reconstitutes the
         ;; structured shape before iterating (no-op on pre-transform data).
         mandatory-precondition-count (count (filter #(seq (edn/unblob (get % ":proc/mandatory-preconditions"))) procs))
         source-count (count (filter (fn [p] (some #(get % ":tl/source-url")
                                                    (edn/unblob (get p ":proc/timeline-rules" []))))
                                     procs))
         source-total (count procs)
         source-gap (if (>= source-count source-total)
                      (str ":source-url 出典: " source-count "/" source-total " 全エントリに一次ソース URL を記録")
                      (str ":source-url 出典: " source-count "/" source-total
                           " に一次ソース URL を記録 — 残りは anchor のみ (推測 URL は入れない, G10)"))
         named-gaps (concat (map #(str % " — 未収載 (worklist)") remaining)
                            [source-gap
                             (str "統計的義務前置き手続き (mandatory precondition) 収載: " mandatory-precondition-count "/" source-total " 手続き")]
                            structural-gaps)]
     {"jurisdictions" (vec covered)
      "covered_count" (count covered)
      "un_member_states" un-member-states
      "coverage_ratio" (round-half-even (/ (double (count covered)) un-member-states) 4)
      "procedures_by_jurisdiction" (into (sorted-map) proc-by-j)
      "proc_total" source-total
      "proc_source_url_count" source-count
      "worklist_remaining" (vec remaining)
      "named_gaps" (vec named-gaps)})))

(defn report
  [cov]
  (let [L (transient ["# saisei 再生 — jurisdiction coverage (honest — G10)" ""])]
    (conj! L (str "- covered: " (get cov "covered_count") " legal systems "
                  "(" (str/join ", " (get cov "jurisdictions")) ") of ~" (get cov "un_member_states")
                  " UN states → ratio ≈ " (pct2 (get cov "coverage_ratio"))
                  " (低いのは仕様 — 推測より空白, R0 seed)"))
    (conj! L "")
    (conj! L "| juris | procedures |")
    (conj! L "|---|---|")
    (doseq [j (get cov "jurisdictions")]
      (conj! L (str "| " j " | " (get (get cov "procedures_by_jurisdiction") j 0) " |")))
    (conj! L "")
    (conj! L "## Named gaps (next-wave worklist)")
    (doseq [g (get cov "named_gaps")]
      (conj! L (str "- " g)))
    (conj! L "")
    (conj! L (str "未カバー管轄の状況は :unknown-jurisdiction に honest degrade し、"
                  "現地法を推測せず free/public legal aid directory の案内のみを行う (filing_plan G10)。"))
    (str (str/join "\n" (persistent! L)) "\n")))
