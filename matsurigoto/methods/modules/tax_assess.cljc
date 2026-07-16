(ns matsurigoto.methods.modules.tax-assess
  "tax_assess.py — matsurigoto 政 `tax-assess` module (R0 reference implementation).
  1:1 Clojure port of `methods/modules/tax_assess.py` (ADR-2606062300).

  A PURE-FUNCTION tax-assessment engine for tax.income.file / tax.corporate.file /
  tax.vat.file. Income/corporate tax is a progressive marginal-bracket computation; VAT
  is output−input. The bracket table is the localized jurisdiction parameter (G2). One
  universal algorithm serves every polity.

    G1 no-operator-master-key : SERVER-HELD-AUTHORITY false; the module SIGNS NOTHING; a
                                filing receipt is returned UNSIGNED.
    G2 spec-derived-only      : progressive marginal brackets; rate tables cite source.
    G3 authority-bearing      : the caller passes :operated-by; this module never asserts it.

  Conformance is checked against the published JP 速算表 (see test-tax-assess).

  House style: result maps stay string-keyed (byte-for-byte json.loads shapes); ':…' keyword
  strings stay strings; pure fns; file I/O only behind #?(:clj ...). Mirrors Python round()
  (HALF_EVEN) via BigDecimal. The Python __main__ demo is omitted.

  load-rate-tables (R1.D) merges per-jurisdiction tables from data/rates/*.edn into the
  RATE-TABLES atom at namespace load — the embedded JPN/FLAT20 remain as fallback."
  (:require [matsurigoto.methods._edn :as edn]
            [clojure.string :as str]
            #?(:clj [clojure.java.io :as io])))

;; G1: this module holds NO signing authority. It computes; the governing organ signs.
(def SERVER-HELD-AUTHORITY false)

;; ── Reference marginal-bracket rate tables (the localized G2 parameter) ──
;; Each table: ascending list of [lower-bound-inclusive marginal-rate]. The last bracket
;; extends to +∞. :representative reference figures anchored to public tax law.
(def ^:private embedded-rate-tables
  {"JPN.income"
   {"currency" "JPY"
    "source"   "所得税法 / 国税庁 速算表 (:representative)"
    "brackets" [[0 0.05]
                [1950000 0.10]
                [3300000 0.20]
                [6950000 0.23]
                [9000000 0.33]
                [18000000 0.40]
                [40000000 0.45]]}
   "FLAT20.income"
   {"currency" "XXX"
    "source"   "illustrative flat 20% (:representative)"
    "brackets" [[0 0.20]]}})

;; RATE-TABLES — the mutable registry (Python module-global dict). string key → table map.
(def RATE-TABLES (atom embedded-rate-tables))

#?(:clj
   (defn- default-rates-dir []
     ;; pathlib.Path(__file__).resolve().parent.parent.parent / "data" / "rates"
     ;; from methods/modules/tax_assess → matsurigoto/data/rates
     (-> *file* io/file .getParentFile .getParentFile .getParentFile
         (io/file "data" "rates"))))

(defn load-rate-tables
  "R1.D: merge per-jurisdiction rate tables from data/rates/*.edn into RATE-TABLES.

  Each file is a map \"<KEY>\" → {:currency :source :brackets [[lower rate] ...]}. Returns
  the number of tables loaded. Robust: a missing dir / parse error leaves embedded tables."
  ([] #?(:clj (load-rate-tables (default-rates-dir))
         :cljs 0))
  ([directory]
   #?(:clj
      (let [dir (io/file directory)]
        (if-not (.exists dir)
          0
          (let [files (->> (.listFiles dir)
                           (filter #(str/ends-with? (.getName ^java.io.File %) ".edn"))
                           (sort-by #(.getName ^java.io.File %)))]
            (reduce
             (fn [n f]
               (let [doc (try (edn/load-edn f) (catch Exception _ ::err))]
                 (if (= doc ::err)
                   n
                   (do
                     (doseq [[key tbl] (or doc {})]
                       (swap! RATE-TABLES assoc key
                              {"currency" (get tbl ":currency" "XXX")
                               "source"   (get tbl ":source" "")
                               "brackets" (mapv (fn [b] [(nth b 0) (nth b 1)]) (get tbl ":brackets"))}))
                     (+ n (count (or doc {})))))))
             0
             files))))
      :cljs 0)))

;; ── Python round() parity: HALF_EVEN over the exact double ──
(defn- pyround
  "Python round(x, n): banker's rounding (HALF_EVEN) to n decimal places, returning a double."
  [x n]
  #?(:clj (-> (java.math.BigDecimal. (double x))
              (.setScale (int n) java.math.RoundingMode/HALF_EVEN)
              (.doubleValue))
     :cljs (let [f (Math/pow 10 n)] (/ (Math/round (* x f)) f))))

(defn assess-income-tax
  "Progressive marginal-bracket assessment. Pure function.

  `brackets` = ascending [[lower-inclusive marginal-rate] ...]; the top bracket → +∞.
  Returns the per-bracket breakdown, total liability, and effective rate."
  [taxable-income brackets]
  (when (< taxable-income 0)
    (throw (ex-info "taxable_income must be >= 0" {})))
  (when (empty? brackets)
    (throw (ex-info "brackets must be non-empty" {})))
  (let [n (count brackets)
        lines
        (reduce
         (fn [acc i]
           (let [[lower rate] (nth brackets i)
                 upper (if (< (inc i) n)
                         (double (nth (nth brackets (inc i)) 0))
                         #?(:clj Double/POSITIVE_INFINITY :cljs js/Infinity))]
             (if (> taxable-income lower)
               (let [amount (- (min (double taxable-income) upper) lower)
                     tax (* amount rate)]
                 (conj acc {"lower" lower "upper" upper "rate" rate
                            "taxable_in_bracket" amount "tax_in_bracket" tax}))
               acc)))
         []
         (range n))
        total (reduce + 0.0 (map #(get % "tax_in_bracket") lines))]
    {"taxable_income" taxable-income
     "liability" (pyround total 2)
     "effective_rate" (if (and (number? taxable-income) (not (zero? taxable-income)))
                        (pyround (/ total taxable-income) 6)
                        0.0)
     "brackets" (mapv (fn [ln]
                        {"lower" (get ln "lower") "upper" (get ln "upper") "rate" (get ln "rate")
                         "taxable_in_bracket" (get ln "taxable_in_bracket")
                         "tax_in_bracket" (pyround (get ln "tax_in_bracket") 2)})
                      lines)}))

(defn- unsigned-receipt
  "A filing-receipt SKELETON. G1: unsigned — the governing organ signs with ITS key."
  [amount currency]
  {"assessed_amount" amount
   "currency" currency
   "proof" nil                       ; G1 — this module signs nothing
   "server_held_authority" SERVER-HELD-AUTHORITY  ; false
   "status" "assessed-unsigned"})

(defn assess-from-return
  "Assess income tax from a return-shaped input (gross − deductions → taxable).

  `table-key` selects a RATE-TABLES entry (the localized G2 param)."
  [gross-income deductions table-key]
  (let [tables @RATE-TABLES]
    (when-not (contains? tables table-key)
      (throw (ex-info (str "unknown rate table " (pr-str table-key)) {})))
    (let [table (get tables table-key)
          taxable (max 0.0 (- gross-income deductions))
          out (assess-income-tax taxable (get table "brackets"))]
      (assoc out
             "currency" (get table "currency")
             "rate_table" table-key
             "rate_table_source" (get table "source")
             "receipt" (unsigned-receipt (get out "liability") (get table "currency"))))))

(defn assess-vat
  "Net VAT = output VAT − input VAT (EN 16931 / SAF-T aggregates). Pure function.
  Negative net → a refund position. No key, no filing; receipt is unsigned (G1)."
  ([output-vat input-vat] (assess-vat output-vat input-vat "XXX"))
  ([output-vat input-vat currency]
   (let [net (pyround (- output-vat input-vat) 2)]
     {"output_vat" output-vat
      "input_vat" input-vat
      "net_vat_due" (if (> net 0) net 0.0)
      "refund_due" (if (< net 0) (- net) 0.0)
      "currency" currency
      "receipt" (unsigned-receipt (if (> net 0) net 0.0) currency)})))

;; R1.D: load per-jurisdiction rate tables at namespace load (embedded JPN/FLAT20 fallback).
#?(:clj (load-rate-tables))

(defn solve
  "Cell entry — R0 is reference-only; a LIVE filing is Council+operator gated."
  [& _]
  (throw (ex-info (str "tax-assess R0: reference assessment only. Live filing against a "
                       "government record is Council+operator gated (principal A: Council "
                       "Lv7+; principal B: adopting state).")
                  {})))
