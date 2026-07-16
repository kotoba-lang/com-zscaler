(ns meibo.methods.coverage-report
  "meibo 名簿 — honest jurisdiction-coverage report (G10, ADR-2607062200)."
  (:require [clojure.string :as str]
            [meibo.methods.directory :as dir]))

(def un-member-states 193)

;; next-wave jurisdiction worklist — overlaps tate's own 30-jurisdiction coverage
;; so a future wave can wire tate's :juris/referrals to real URLs. Entries DROP
;; OFF automatically once covered.
(def juris-worklist
  [":nl" ":pl" ":se" ":at" ":pt" ":ie" ":ch" ":dk" ":fi" ":no"
   ":mx" ":be" ":ar" ":nz" ":tw" ":sg" ":in" ":cn" ":br"])

(defn- counter [coll] (reduce (fn [m v] (update m v (fnil inc 0))) {} coll))

(defn- round-half-even [x n]
  #?(:clj (.doubleValue (.setScale (java.math.BigDecimal. (double x)) (int n)
                                   java.math.RoundingMode/HALF_EVEN))
     :cljs (let [f (Math/pow 10 n)] (/ (Math/round (* x f)) f))))

(defn- pct2 [x]
  #?(:clj (str (.toPlainString
                (.setScale (.multiply (java.math.BigDecimal. (double x))
                                      (java.math.BigDecimal. "100"))
                           2 java.math.RoundingMode/HALF_EVEN))
               "%")
     :cljs (str (.toFixed (* x 100) 2) "%")))

(defn coverage
  ([] (coverage (dir/load-directory)))
  ([entries]
   (let [covered (dir/jurisdictions-covered entries)
         by-j (counter (map #(get % ":dir/jurisdiction") entries))
         remaining (filterv #(not (some #{%} covered)) juris-worklist)
         kinds (counter (map #(get % ":dir/kind") entries))]
     {"jurisdictions" (vec covered)
      "covered_count" (count covered)
      "un_member_states" un-member-states
      "coverage_ratio" (round-half-even (/ (double (count covered)) un-member-states) 4)
      "entries_by_jurisdiction" (into (sorted-map) by-j)
      "entries_by_kind" (into (sorted-map) kinds)
      "entry_total" (count entries)
      "worklist_remaining" (vec remaining)
      "named_gaps" (vec (map #(str % " — 未収載 (worklist)") remaining))})))

(defn report [cov]
  (let [L (transient ["# meibo 名簿 — jurisdiction coverage (honest — G10)" ""])]
    (conj! L (str "- covered: " (get cov "covered_count") " legal systems "
                  "(" (str/join ", " (get cov "jurisdictions")) ") of ~" (get cov "un_member_states")
                  " UN states → ratio ≈ " (pct2 (get cov "coverage_ratio"))
                  " (低いのは仕様 — 推測より空白, R0 seed)"))
    (conj! L "")
    (conj! L "| juris | entries |")
    (conj! L "|---|---|")
    (doseq [j (get cov "jurisdictions")]
      (conj! L (str "| " j " | " (get (get cov "entries_by_jurisdiction") j 0) " |")))
    (conj! L "")
    (conj! L "## Named gaps (next-wave worklist)")
    (doseq [g (get cov "named_gaps")]
      (conj! L (str "- " g)))
    (str (str/join "\n" (persistent! L)) "\n")))
