;; matsurigoto 政 — tax-collect / withholding 源泉徴収計算の conformance test。ADR-2606062300。
;; 法令・国税庁合計税率表に基づく確定値 (報酬/配当/利子/退職/復興2.1%) を厳密に検証する。
;; 給与月額表は :representative パラメータのため「アルゴリズム合成」のみ検証 (値は非主張)。
(ns matsurigoto.tax-collect.test-withholding
  (:require [clojure.test :refer [deftest is testing run-tests]]
            [clojure.edn :as edn]
            [matsurigoto.tax-collect.withholding :as w]))

;; jpn-rates.edn is now Datomic/Datascript tx-data (ADR-2607100030 fan-out): a single
;; [{:db/id -1 :withholding.jpn-rates/<key> <value-or-blob>}] entity. `unblob`/
;; `reconstitute-entity` un-namespace + pr-str-parse it back into the original bare
;; map so `(:salary-monthly-kou RATES)` below keeps working unchanged.
(defn- unblob [v]
  (if (string? v)
    (try (let [parsed (edn/read-string v)] (if (coll? parsed) parsed v))
         (catch Exception _ v))
    v))

(defn- reconstitute-entity [tx-data]
  (into {} (map (fn [[k v]] [(keyword (name k)) (unblob v)]))
        (dissoc (first tx-data) :db/id)))

(def ^:private RATES
  (reconstitute-entity
   (edn/read-string (slurp "20-actors/matsurigoto/data/withholding/jpn-rates.edn"))))

;; ── 復興特別所得税 合計税率 (官報表と一致) ──
(deftest combined-rates-match-official-table
  (is (= 102100 (w/combined-ppm 100000)) "10% → 10.21%")
  (is (= 153150 (w/combined-ppm 150000)) "15% → 15.315%")
  (is (= 51050  (w/combined-ppm 50000))  "5% → 5.105%")
  (is (= 204200 (w/combined-ppm 200000)) "20% → 20.42%")
  (is (= 71470  (w/combined-ppm 70000))  "7% → 7.147%")
  (is (= 163360 (w/combined-ppm 160000)) "16% → 16.336%")
  (is (= 183780 (w/combined-ppm 180000)) "18% → 18.378%")
  (is (= 21/1000 w/RECONSTRUCTION-SURTAX-RATE) "復興特別所得税は所得税の2.1%"))

(deftest floor-is-exact-yen-truncation
  (is (= 7657 (w/tax-by-ppm 75000 (:r10 w/COMBINED-RATES))) "75000×10.21%=7657.5→切捨て7657")
  (is (= 0 (w/tax-by-ppm 0 (:r20 w/COMBINED-RATES))))
  (is (thrown? Exception (w/tax-by-ppm -1 (:r10 w/COMBINED-RATES)))))

;; ── 報酬・料金 (所得税法204条) ──
(deftest fee-standard-10-21-then-20-42
  (is (= 102100 (:withheld (w/fee-standard 1000000))) "100万ちょうど = 100万×10.21%")
  (is (= 10210  (:withheld (w/fee-standard 100000))))
  (testing "100万円超は超過部分20.42% + 102,100円"
    (is (= 142940 (:withheld (w/fee-standard 1200000)))   ; 102100 + 200000×20.42%(40840)
        "120万: 102,100 + 40,840 = 142,940"))
  (is (= 897900 (:net (w/fee-standard 1000000))) "100万 − 102,100"))

(deftest fee-judicial-scrivener-1man-deduction
  (is (= 4084 (:withheld (w/fee-judicial-scrivener 50000))) "(5万−1万)×10.21%")
  (is (= 0 (:withheld (w/fee-judicial-scrivener 8000))) "1万以下は控除後0"))

(deftest fee-diplomat-12man-allowance
  (is (= 8168 (:withheld (w/fee-diplomat 200000))) "(20万−12万)×10.21%")
  (is (= 0 (:withheld (w/fee-diplomat 100000))) "12万以下は0")
  (testing "同月給与があると控除額が圧縮される"
    (is (= 15315 (:withheld (w/fee-diplomat 200000 70000)))
        "allowance=120,000−70,000=50,000 → (200,000−50,000)×10.21%=15,315")))

(deftest fee-hostess-5000-per-day
  (is (= 7657 (:withheld (w/fee-hostess 100000 5))) "(10万−2.5万)×10.21%=7657.5→7657"))

(deftest fee-advertising-prize-50man-deduction
  (is (= 51050 (:withheld (w/fee-advertising-prize 1000000))) "(100万−50万)×10.21%"))

;; ── 配当・利子・非居住者 ──
(deftest dividend-listed-vs-unlisted
  (is (= 15315 (:withheld (w/dividend 100000 :listed)))   "上場 15.315%")
  (is (= 20420 (:withheld (w/dividend 100000 :unlisted))) "非上場 20.42%")
  (is (thrown? Exception (w/dividend 100000 :bogus))))

(deftest interest-15-315
  (is (= 15315 (:withheld (w/interest 100000)))))

(deftest nonresident-20-42-and-treaty
  (is (= 20420 (:withheld (w/nonresident 100000))) "原則20.42%")
  (is (false? (:treaty-reduced (w/nonresident 100000))))
  (is (true? (:treaty-reduced (w/nonresident 100000 (:r10 w/COMBINED-RATES)))) "条約軽減フラグ"))

;; ── 退職所得 ──
(deftest retirement-deduction-table
  (is (= 4000000 (w/retirement-deduction 10))  "40万×10年")
  (is (= 800000  (w/retirement-deduction 1))   "最低80万円")
  (is (= 11500000 (w/retirement-deduction 25)) "800万 + 70万×5"))

(deftest retirement-with-statement-half-and-surtax
  (let [r (w/retirement-with-statement 10000000 10)]
    (is (= 4000000 (:retirement-deduction r)))
    (is (= 3000000 (:taxable-retirement-income r)) "(1000万−400万)÷2")
    ;; 所得税 = 300万×10%−97,500 = 202,500 → ×102.1% = 206,752.5 → 切捨て206,752
    (is (= 206752 (:withheld r)) "復興込み 206,752円"))
  (testing "役員5年以下は1/2なし"
    (let [r (w/retirement-with-statement 10000000 10 :officer-short? true)]
      (is (= 6000000 (:taxable-retirement-income r))))))

(deftest retirement-without-statement-flat-20-42
  (is (= 2042000 (:withheld (w/retirement-without-statement 10000000))) "申告書なし一律20.42%"))

;; ── 賞与 ──
(deftest bonus-by-rate
  (is (= 102100 (:withheld (w/bonus 500000 (:r20 w/COMBINED-RATES)))) "50万×20.42%")
  (is (= 0 (:withheld (w/bonus 0 (:r10 w/COMBINED-RATES))))))

;; ── 給与 (電算特例) — :representative パラメータ。アルゴリズム合成のみ検証 ──
(deftest salary-electronic-algorithm-composes
  (let [params (:salary-monthly-kou RATES)
        r (w/salary-monthly-electronic 300000 1 params)]
    (is (= :salary (:category r)))
    (is (= 90000 (:salary-income-deduction r)) "簡易30%控除 (representative)")
    (is (zero? (mod (:taxable-salary-income r) 1000)) "課税給与所得金額は1,000円未満切捨て")
    (is (= 138000 (:taxable-salary-income r)) "210,000 − 31,667 − 40,000 = 138,333 → 138,000")
    (is (= 7044 (:withheld r)) "138,000×5.105% = 7,044.9 → 7,044 (復興込み税率)")
    (is (= 292956 (:net r))))
  (testing "扶養が増えると課税所得が下がり税額が下がる"
    (let [params (:salary-monthly-kou RATES)
          r0 (w/salary-monthly-electronic 300000 0 params)
          r2 (w/salary-monthly-electronic 300000 2 params)]
      (is (>= (:withheld r0) (:withheld r2))))))

;; ── 集計 + G1/G8 invariants ──
(deftest total-and-invariants
  (is (= 30629 (w/total-withheld [(w/fee-standard 100000)      ; 10210
                                  (w/dividend 100000 :listed)  ; 15315
                                  (w/interest 33333)])))        ; floor(33333×15.315%)=5104 → 10210+15315+5104=30629
  (testing "G1: モジュールは何も署名しない"
    (is (false? w/SERVER-HELD-AUTHORITY)))
  (testing "G8: live remit は solve() で必ず raise"
    (is (thrown? Exception (w/solve)))))

(defn -main [& _]
  (let [r (run-tests 'matsurigoto.tax-collect.test-withholding)]
    (when (pos? (+ (:fail r) (:error r))) (System/exit 1))))
