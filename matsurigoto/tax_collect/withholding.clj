;; matsurigoto 政 — tax-collect / 源泉徴収税額の計算 (withholding-tax computation).
;; ADR-2606062300 (parent) · backs COFOG service `tax.withholding.remit` (源泉徴収納付).
;;
;; WHAT IT IS: a deterministic, spec-derived reference engine that computes the Japanese
;; 源泉所得税 + 復興特別所得税 a 法人 (as a 源泉徴収義務者 / withholding agent) must withhold,
;; for every withholding category 法人 encounters:
;;   報酬・料金等 (所得税法 204条) · 給与 (電子計算機計算の特例) · 賞与 · 退職所得 ·
;;   配当 · 利子 · 非居住者等。
;;
;; 復興特別所得税 (復興財源確保法, 2013–2037): 所得税額 × 2.1%。源泉では「合計税率 =
;; 所得税率 × 102.1%」を乗じ、1円未満を切り捨てる (端数処理)。本エンジンは合計税率を
;; ppm (parts-per-million, 100万分率) の整数で保持し、floor を厳密整数演算で行うので、
;; double 誤差なしで官報の合計税率表と一致する。
;;
;; WHAT IT IS NOT (honest R0): NOT a certified payroll engine, NOT wired to any live record.
;;   G1 no-operator-master-key : SERVER-HELD-AUTHORITY = false — このエンジンは何も署名しない。
;;   G2 spec-derived-only      : 所得税法204条 / 復興財源確保法28条 / 国税庁 合計税率表に準拠。
;;                               給与月額表の別表 (年次改定) は data EDN の :representative パラメータ。
;;   G3 authority-bearing      : 納付主体 (:operated-by) は呼び出し側が渡す。本体は主張しない。
;;
;; pure functions, no I/O, no network. babashka-runnable (bb test:matsurigoto).
(ns matsurigoto.tax-collect.withholding
  (:require [clojure.edn :as edn]))

;; G1: this module holds NO signing authority.
(def ^:const SERVER-HELD-AUTHORITY false)

;; ── 復興特別所得税 (Special Reconstruction Income Tax) ─────────────────────────
;; 所得税額 × 2.1% の付加。源泉徴収では基準所得税率に 102.1% を乗じる。
;; 復興財源確保法 (平成23年法律第117号) 第28条。課税期間 2013-01-01 .. 2037-12-31。
(def ^:const RECONSTRUCTION-SURTAX-RATE 21/1000)        ; 2.1%
(def ^:const RECONSTRUCTION-MULTIPLIER 1021/1000)       ; ×102.1%
(def ^:const RECONSTRUCTION-PERIOD {:from "2013-01-01" :to "2037-12-31"})

(defn combined-ppm
  "基準所得税率 (ppm, 例 10% = 100000) → 復興特別所得税込みの合計税率 (ppm)。
   例 100000 (10%) → 102100 (10.21%)。基準 ppm が 1000 の倍数のとき厳密整数。"
  ^long [^long base-rate-ppm]
  (let [n (* base-rate-ppm 1021)]
    (assert (zero? (mod n 1000))
            (str "base-rate-ppm " base-rate-ppm " は復興込み換算で割り切れません"))
    (quot n 1000)))

;; 法人が実務で用いる代表的な合計税率 (基準% → 復興込み ppm)。官報「源泉徴収のための
;; 復興特別所得税及び所得税の合計税率」と一致する。
(def COMBINED-RATES
  {:r5   (combined-ppm  50000)   ;  5.105%
   :r7   (combined-ppm  70000)   ;  7.147%
   :r10  (combined-ppm 100000)   ; 10.21%  報酬・料金等の原則
   :r15  (combined-ppm 150000)   ; 15.315% 上場株式配当・利子
   :r16  (combined-ppm 160000)   ; 16.336%
   :r18  (combined-ppm 180000)   ; 18.378%
   :r20  (combined-ppm 200000)}) ; 20.42%  100万円超部分・非居住者・非上場配当

;; ── 厳密 floor (1円未満切捨て) ────────────────────────────────────────────────
(defn floor-yen
  "非負の整数 / Ratio を円未満切捨てした long に。正の Ratio では quot=floor なので厳密。"
  ^long [x]
  (cond
    (integer? x) (long x)
    (ratio? x)   (do (assert (>= (compare x 0) 0) "floor-yen は非負のみ")
                     (long (quot (numerator x) (denominator x))))
    :else        (long (Math/floor (double x)))))

(defn tax-by-ppm
  "課税対象額 × 合計税率(ppm) を 1円未満切捨て。源泉徴収の中核計算。"
  ^long [^long taxable ^long rate-ppm]
  (when (neg? taxable) (throw (ex-info "課税対象額は0以上" {:taxable taxable})))
  (floor-yen (/ (* taxable rate-ppm) 1000000)))

;; ─────────────────────────────────────────────────────────────────────────────
;; 1. 報酬・料金等 (所得税法 204条1項) — 法人が個人へ支払う際の源泉徴収
;; ─────────────────────────────────────────────────────────────────────────────
(defn fee-standard
  "原稿料・講演料・デザイン料・弁護士/税理士/会計士等の報酬 (204条1項1号・2号)。
   支払金額 100万円以下: ×10.21%。100万円超: 超過部分 ×20.42% + 102,100円。"
  [^long amount]
  (let [threshold 1000000
        tax (if (<= amount threshold)
              (tax-by-ppm amount (:r10 COMBINED-RATES))
              (+ (tax-by-ppm threshold (:r10 COMBINED-RATES))
                 (tax-by-ppm (- amount threshold) (:r20 COMBINED-RATES))))]
    {:category :fee/standard :gross amount :withheld tax :net (- amount tax)
     :basis "所得税法204条1項1号・2号 / 復興財源確保法28条"}))

(defn fee-judicial-scrivener
  "司法書士・土地家屋調査士・海事代理士の報酬 (204条1項2号)。1回の支払につき1万円を
   控除した残額 ×10.21%。"
  [^long amount]
  (let [base (max 0 (- amount 10000))
        tax  (tax-by-ppm base (:r10 COMBINED-RATES))]
    {:category :fee/judicial-scrivener :gross amount :deduction 10000
     :withheld tax :net (- amount tax)
     :basis "所得税法204条1項2号 (1回1万円控除)"}))

(defn fee-diplomat
  "外交員・集金人・電力量計検針人の報酬 (204条1項4号)。その月の支払額から12万円
   (同月給与があればその額を差引いた残り) を控除した残額 ×10.21%。"
  ([^long monthly-amount] (fee-diplomat monthly-amount 0))
  ([^long monthly-amount ^long same-month-salary]
   (let [allowance (max 0 (- 120000 same-month-salary))
         base      (max 0 (- monthly-amount allowance))
         tax       (tax-by-ppm base (:r10 COMBINED-RATES))]
     {:category :fee/diplomat :gross monthly-amount :allowance allowance
      :withheld tax :net (- monthly-amount tax)
      :basis "所得税法204条1項4号 (月12万円控除)"})))

(defn fee-hostess
  "ホステス・バンケットホステス・コンパニオン等の報酬 (204条1項6号)。
   支払金額から (5,000円 × 計算期間の日数) を控除した残額 ×10.21%。"
  [^long amount ^long days]
  (let [base (max 0 (- amount (* 5000 days)))
        tax  (tax-by-ppm base (:r10 COMBINED-RATES))]
    {:category :fee/hostess :gross amount :deduction (* 5000 days) :days days
     :withheld tax :net (- amount tax)
     :basis "所得税法204条1項6号 (5,000円×日数控除)"}))

(defn fee-advertising-prize
  "広告宣伝のための賞金 (204条1項8号)。50万円を控除した残額 ×10.21%。"
  [^long amount]
  (let [base (max 0 (- amount 500000))
        tax  (tax-by-ppm base (:r10 COMBINED-RATES))]
    {:category :fee/advertising-prize :gross amount :deduction 500000
     :withheld tax :net (- amount tax)
     :basis "所得税法204条1項8号 (50万円控除)"}))

;; ─────────────────────────────────────────────────────────────────────────────
;; 2. 配当・利子・非居住者 — 一律税率
;; ─────────────────────────────────────────────────────────────────────────────
(defn dividend
  "配当の源泉徴収。:listed 上場株式等 (大口株主除く) = 15.315% (国税分)。
   :unlisted 非上場・大口 = 20.42%。地方税の特別徴収は別途 (国税源泉はこの額)。"
  [^long amount kind]
  (let [rate (case kind
               :listed   (:r15 COMBINED-RATES)
               :unlisted (:r20 COMBINED-RATES)
               (throw (ex-info "kind は :listed | :unlisted" {:kind kind})))
        tax  (tax-by-ppm amount rate)]
    {:category :dividend :kind kind :gross amount :withheld tax :net (- amount tax)
     :basis "所得税法181条・182条 / 租特法9条の3 (上場15.315%)"}))

(defn interest
  "利子等の源泉徴収 = 15.315% (国税分。地方税5%の特別徴収は別)。"
  [^long amount]
  (let [tax (tax-by-ppm amount (:r15 COMBINED-RATES))]
    {:category :interest :gross amount :withheld tax :net (- amount tax)
     :basis "所得税法181条・182条 / 租特法3条 (15.315%)"}))

(defn nonresident
  "非居住者・外国法人への国内源泉所得の源泉徴収。原則 20.42%。
   租税条約による限度税率の軽減・免除は別計算 (要・租税条約に関する届出書)。"
  ([^long amount] (nonresident amount (:r20 COMBINED-RATES)))
  ([^long amount ^long rate-ppm]
   (let [tax (tax-by-ppm amount rate-ppm)]
     {:category :nonresident :gross amount :rate-ppm rate-ppm
      :withheld tax :net (- amount tax) :treaty-reduced (not= rate-ppm (:r20 COMBINED-RATES))
      :basis "所得税法212条 (非居住者等20.42%。条約軽減は要届出)"})))

;; ─────────────────────────────────────────────────────────────────────────────
;; 3. 退職所得 — 退職所得の受給に関する申告書の提出有無で分岐
;; ─────────────────────────────────────────────────────────────────────────────
(defn retirement-deduction
  "退職所得控除額 (所得税法30条3項)。勤続年数 (1年未満切上げ) years より:
   20年以下: 40万円 × 年 (最低80万円)。20年超: 800万円 + 70万円 × (年−20)。"
  ^long [^long years]
  (let [y (max 1 years)]
    (if (<= y 20)
      (max 800000 (* 400000 y))
      (+ 8000000 (* 700000 (- y 20))))))

;; 退職所得の所得税速算表 [課税退職所得金額の下限(含む) 税率(/100) 控除額(円)] (復興前)。
(def ^:private RETIREMENT-INCOME-TAX-BRACKETS
  [[0         5  0]
   [1950000  10  97500]
   [3300000  20  427500]
   [6950000  23  636000]
   [9000000  33  1536000]
   [18000000 40  2796000]
   [40000000 45  4796000]])

(defn- progressive-income-tax
  "速算表 (rate%, 控除額) による所得税額 (復興前)。A=課税所得金額。厳密 Ratio で返す。"
  [^long A brackets]
  (let [[_ rate ded] (last (filter (fn [[lower _ _]] (>= A lower)) brackets))]
    (max 0 (- (/ (* A rate) 100) ded))))

(defn retirement-with-statement
  "「退職所得の受給に関する申告書」提出済の退職手当等の源泉徴収 (所得税法201条1項)。
   課税退職所得金額 = floor((収入 − 退職所得控除) × 1/2)。ただし役員等で勤続5年以下は
   1/2 を適用しない (officer-short? true)。所得税額に復興2.1%を上乗せ、1円未満切捨て。"
  [^long severance ^long years & {:keys [officer-short?] :or {officer-short? false}}]
  (let [ded     (retirement-deduction years)
        residual (max 0 (- severance ded))
        taxable (if officer-short? residual (floor-yen (/ residual 2)))
        income-tax (progressive-income-tax taxable RETIREMENT-INCOME-TAX-BRACKETS)
        total   (floor-yen (* income-tax RECONSTRUCTION-MULTIPLIER))]
    {:category :retirement :statement true :gross severance :years years
     :retirement-deduction ded :taxable-retirement-income taxable
     :withheld total :net (- severance total)
     :basis "所得税法30条・201条1項 / 復興財源確保法28条"}))

(defn retirement-without-statement
  "申告書の提出がない退職手当等 (所得税法201条3項)。支払金額 × 20.42% (退職所得控除等の
   適用なし)。受給者は後日確定申告で精算可。"
  [^long severance]
  (let [tax (tax-by-ppm severance (:r20 COMBINED-RATES))]
    {:category :retirement :statement false :gross severance
     :withheld tax :net (- severance tax)
     :basis "所得税法201条3項 (申告書なし一律20.42%)"}))

;; ─────────────────────────────────────────────────────────────────────────────
;; 4. 給与 (月額表 甲欄) — 電子計算機等を使用して源泉徴収税額を計算する方法 (財務省告示)
;;    別表 (給与所得控除・扶養親族等控除・税額速算) は年次改定の :representative パラメータ。
;;    汎用アルゴリズム + 局所パラメータ (tax_assess の方式) を踏襲する。
;; ─────────────────────────────────────────────────────────────────────────────
(defn- piecewise-linear
  "区分表 [[下限(含む) 率(/1000) 加減算(円)] …] による区分線形値。
   値 = floor(x × 率 / 1000) + 加減算。給与所得控除(別表第一)等を data で表現するための形。"
  ^long [^long x brackets]
  (let [[_ rate add] (last (filter (fn [[lo _ _]] (>= x lo)) brackets))]
    (+ (floor-yen (/ (* x rate) 1000)) add)))

(defn salary-monthly-electronic
  "甲欄 月額の源泉徴収税額 (電子計算機等を使用して源泉徴収税額を計算する方法 / 財務省告示)。
     social-deducted = その月の給与等から社会保険料等を控除した金額
     dependents      = 控除対象扶養親族等の数 (源泉控除対象配偶者を含む)
     params (data/withholding/jpn-rates.edn の :salary-monthly-kou, :representative):
       :salary-deduction-brackets [[下限 率/1000 加減算] …]  給与所得控除(別表第一相当)
       :dependent-deduction       円/人 (別表第二相当)
       :basic-deduction           円 (基礎控除 月額相当)
       :tax-brackets              [[下限 合計税率ppm 控除額] …] (別表第四・復興込み速算)
   汎用アルゴリズム + 局所パラメータ (tax_assess と同方式)。税率表の年次改定値は
   deployment が :authoritative で供給する。課税給与所得金額は1,000円未満切捨て。
   :tax-brackets の率は復興特別所得税込みの合計税率 (ppm) なので別途上乗せしない。"
  [^long social-deducted ^long dependents params]
  (let [{:keys [salary-deduction-brackets dependent-deduction basic-deduction tax-brackets]} params
        sid       (piecewise-linear social-deducted salary-deduction-brackets) ; 給与所得控除
        after-sid (max 0 (- social-deducted sid))
        deps      (* dependent-deduction (max 0 dependents))
        taxable-raw (max 0 (- after-sid deps basic-deduction))
        taxable   (* 1000 (quot taxable-raw 1000))            ; 1,000円未満切捨て
        [_ rate-ppm ded] (last (filter (fn [[lower _ _]] (>= taxable lower)) tax-brackets))
        tax       (floor-yen (max 0 (- (/ (* taxable rate-ppm) 1000000) ded)))]
    {:category :salary :column :kou :social-deducted social-deducted
     :dependents dependents :salary-income-deduction sid :taxable-salary-income taxable
     :withheld tax :net (- social-deducted tax)
     :basis "給与所得の源泉徴収税額表(月額表)甲欄 / 電子計算機計算の特例 (:representative param)"}))

;; ─────────────────────────────────────────────────────────────────────────────
;; 5. 賞与 — 賞与に対する源泉徴収税額の算出率の表 (前月給与×扶養数→率)
;; ─────────────────────────────────────────────────────────────────────────────
(defn bonus
  "賞与の源泉徴収。算出率 (前月の社会保険料控除後給与と扶養親族等の数から決まる合計税率
   ppm) を呼び出し側が表引きして渡す。税額 = floor(賞与(社保控除後) × 率ppm)。"
  [^long bonus-after-social ^long rate-ppm]
  (let [tax (tax-by-ppm bonus-after-social rate-ppm)]
    {:category :bonus :gross bonus-after-social :rate-ppm rate-ppm
     :withheld tax :net (- bonus-after-social tax)
     :basis "賞与に対する源泉徴収税額の算出率の表 (合計税率込み)"}))

;; ── 集計 ───────────────────────────────────────────────────────────────────────
(defn total-withheld
  "源泉徴収結果マップのシーケンスから合計源泉税額を集計。"
  ^long [results]
  (reduce + 0 (map :withheld results)))

(defn solve
  "Cell entry — R0 は参照計算のみ。実際の納付 (remit) は Council+operator gated (G8)。
   上記の純関数群は conformance test で実行される。"
  [& _]
  (throw (ex-info
          (str "tax-collect/withholding R0: reference computation only. "
               "Live withholding remittance against e-Tax / 所得税徴収高計算書 is "
               "Council+operator gated (principal A: Council Lv7+; principal B: adopting state).")
          {:server-held-authority SERVER-HELD-AUTHORITY})))
