;; matsurigoto 政 — tax-collect / 源泉所得税の納付処理 (所得税徴収高計算書 = 納付書)。
;; ADR-2606062300 · backs COFOG service `tax.withholding.remit` (源泉徴収納付)。
;;
;; 法人 (源泉徴収義務者) が源泉徴収した所得税+復興特別所得税を国へ納付する処理:
;;   ・所得税徴収高計算書 (納付書) の種類判定 (8様式)
;;   ・法定納期限の計算 (原則=翌月10日 / 納期特例=7-10・翌1-20。土日祝・年末年始は翌開庁日)
;;   ・不納付加算税・延滞税の計算 (国税通則法)
;;   ・納付方法 (e-Tax ダイレクト納付・ネットバンキング・クレカ・コンビニQR・スマホ・窓口)
;;   ・UNSIGNED な納付書レコードの組立 (G1 — 何も署名しない)
;;
;; G2 spec-derived: 所得税法181-223条 / 国税通則法36・60・67条 / 租特法。
;; 延滞税の特例基準割合は年次改定のため :representative パラメータ (deployment が authoritative 供給)。
(ns matsurigoto.tax-collect.payment
  (:require [matsurigoto.tax-collect.jp-calendar :as cal])
  (:import [java.time LocalDate]))

(def ^:const SERVER-HELD-AUTHORITY false)

;; ── 所得税徴収高計算書 (納付書) の8様式 ───────────────────────────────────────
;; :nokitokurei? = 納期の特例 (源泉所得税の納期特例) の対象か。対象は給与・退職・士業報酬のみ。
(def SLIP-TYPES
  {:salary-retirement-general
   {:ja "給与所得・退職所得等の所得税徴収高計算書(一般用)"  :nokitokurei? false
    :区分 ["俸給・給料等" "賞与(役員賞与を除く)" "日雇労務者の賃金" "退職手当等"
           "税理士等の報酬" "役員賞与"]}
   :salary-retirement-special
   {:ja "給与所得・退職所得等の所得税徴収高計算書(納期特例分)" :nokitokurei? true
    :区分 ["俸給・給料等" "賞与(役員賞与を除く)" "退職手当等" "税理士等の報酬" "役員賞与"]}
   :fee
   {:ja "報酬・料金等の所得税徴収高計算書" :nokitokurei? false
    :区分 ["報酬・料金等"]}
   :interest
   {:ja "利子等の所得税徴収高計算書" :nokitokurei? false :区分 ["利子等"]}
   :dividend
   {:ja "配当等の所得税徴収高計算書" :nokitokurei? false :区分 ["配当等"]}
   :installment-savings
   {:ja "定期積金の給付補塡金等の所得税徴収高計算書" :nokitokurei? false :区分 ["給付補塡金等"]}
   :nonresident
   {:ja "非居住者・外国法人の所得税徴収高計算書" :nokitokurei? false :区分 ["非居住者等所得"]}
   :redemption
   {:ja "償還差益の所得税徴収高計算書" :nokitokurei? false :区分 ["償還差益"]}})

(defn slip-type-eligible-for-tokurei?
  "その納付書様式が納期の特例の対象か (給与・退職・士業報酬系のみ true)。"
  [slip-type]
  (boolean (get-in SLIP-TYPES [slip-type :nokitokurei?])))

;; ── 法定納期限 (源泉所得税) ────────────────────────────────────────────────────
(defn statutory-due-date
  "源泉所得税の法定納期限を返す {:legal-due-date (法律上の期限) :due-date (繰下げ後の開庁日)}。
     pay-year/pay-month : 源泉徴収の対象となる支払が属する年月
     special?           : 納期の特例 (承認済) を適用するか
   原則      : 支払月の翌月10日。
   納期特例  : 1〜6月分 → 7月10日 / 7〜12月分 → 翌年1月20日。
   いずれも土日祝・年末年始(税務署閉庁)に当たれば翌開庁日に繰り下げる (通則法10条2項)。
   特例は対象様式 (給与・退職・士業報酬) でのみ適用可。"
  ([pay-year pay-month] (statutory-due-date pay-year pay-month false))
  ([pay-year pay-month special?]
   (let [legal (if special?
                 (if (<= 1 pay-month 6)
                   (LocalDate/of (int pay-year) 7 10)
                   (LocalDate/of (int (inc pay-year)) 1 20))
                 (let [ym (.plusMonths (LocalDate/of (int pay-year) (int pay-month) 1) 1)]
                   (LocalDate/of (.getYear ym) (.getMonthValue ym) 10)))]
     {:legal-due-date (str legal)
      :due-date (str (cal/next-open-day legal))
      :special special?})))

(defn due-date-for-slip
  "納付書様式 + 支払年月から法定納期限を計算。special? が様式の対象外なら例外。"
  [slip-type pay-year pay-month special?]
  (when (and special? (not (slip-type-eligible-for-tokurei? slip-type)))
    (throw (ex-info "この様式は納期の特例の対象外 (対象は給与・退職・士業報酬のみ)"
                    {:slip-type slip-type})))
  (assoc (statutory-due-date pay-year pay-month special?) :slip-type slip-type))

;; ── 端数処理 helpers (国税通則法118・119条) ─────────────────────────────────────
(defn- floor-to ^long [^long x ^long unit] (* unit (quot x unit)))   ; 切捨て

;; ── 不納付加算税 (国税通則法67条) ───────────────────────────────────────────────
(defn non-payment-additional-tax
  "法定納期限までに納付しなかった源泉所得税に対する不納付加算税。
     tax           : 納付すべき本税
     :voluntary?   : 告知前の自主納付か (true → 5% / false → 10%)
     :within-grace : 法定納期限から1か月以内の納付 + 過去1年間期限内納付 (→ 不適用)
   計算基礎は1万円未満切捨て、加算税額が5,000円未満なら全額切捨て(不徴収)。"
  [^long tax & {:keys [voluntary? within-grace?] :or {voluntary? false within-grace? false}}]
  (if within-grace?
    {:additional-tax 0 :rate 0 :applied false :reason "1か月以内+前年期限内納付により不適用"}
    (let [base (floor-to (max 0 tax) 10000)
          rate (if voluntary? 5/100 10/100)
          raw  (long (quot (* base (numerator rate)) (denominator rate)))
          amt  (if (< raw 5000) 0 raw)]
      {:additional-tax amt
       :rate (if voluntary? "5%" "10%")
       :base base
       :applied (pos? amt)
       :reason (cond (< raw 5000) "5,000円未満のため不徴収"
                     voluntary?   "告知前の自主納付 (5%)"
                     :else        "不納付加算税 (10%)")})))

;; ── 延滞税 (国税通則法60条) ─────────────────────────────────────────────────────
;; 延滞税の割合は年次告示 (特例基準割合連動)。国税庁「延滞税の割合」より :authoritative。
;; early = 納期限の翌日〜2か月以内 / late = 2か月を経過した日以後。
;; 出典: https://www.nta.go.jp/taxes/nozei/entaizei/keisan/entai_wariai.htm
;;       https://www.mof.go.jp/tax_policy/summary/tins/n04_5.pdf
(def DELINQUENCY-RATES-BY-YEAR
  {2025 {:early-rate 24/1000 :late-rate 87/1000}   ; 令和7年: 2.4% / 8.7%
   2026 {:early-rate 28/1000 :late-rate 91/1000}}) ; 令和8年: 2.8% / 9.1% (引上げ)

(def DELINQUENCY-RATES-META
  {:sourcing :authoritative
   :source-url "https://www.nta.go.jp/taxes/nozei/entaizei/keisan/entai_wariai.htm"
   :note "年により割合が異なる。延滞期間が複数年にまたがる場合は期間ごとの割合で按分 (本実装は単一年率)。"})

(defn delinquency-rates-for-year
  "暦年 y の延滞税割合 {:early-rate :late-rate}。未収載年は直近(最大)年を使用。"
  [y]
  (merge (or (get DELINQUENCY-RATES-BY-YEAR y)
             (get DELINQUENCY-RATES-BY-YEAR (apply max (keys DELINQUENCY-RATES-BY-YEAR))))
         DELINQUENCY-RATES-META))

;; 後方互換のデフォルト = 当年 (2026) の :authoritative 割合。
(def DEFAULT-DELINQUENCY-RATES (delinquency-rates-for-year 2026))

(defn delinquency-tax
  "延滞税: 法定納期限の翌日から完納日まで。2か月以内は early-rate、超過分は late-rate。
   計算基礎は1万円未満切捨て、確定金額は100円未満切捨て、全体が1,000円未満なら全額切捨て。
   rates 省略時は当年 (2026) の :authoritative 割合。年指定は delinquency-rates-for-year を渡す。"
  ([^long tax due-date-str paid-date-str]
   (delinquency-tax tax due-date-str paid-date-str DEFAULT-DELINQUENCY-RATES))
  ([^long tax due-date-str paid-date-str rates]
   (let [due  (cal/parse due-date-str)
         paid (cal/parse paid-date-str)]
     (if (not (.isAfter paid due))
       {:delinquency-tax 0 :days 0 :applied false :reason "期限内納付"}
       (let [base     (floor-to (max 0 tax) 10000)
             boundary (.plusMonths due 2)              ; 2か月を経過する日
             day      (fn [^LocalDate a ^LocalDate b]   ; (a, b] の日数
                        (max 0 (.between java.time.temporal.ChronoUnit/DAYS a b)))
             d-early  (day due (if (.isAfter paid boundary) boundary paid))
             d-late   (if (.isAfter paid boundary) (day boundary paid) 0)
             {:keys [early-rate late-rate]} rates
             part     (fn [^long days rate]
                        (long (quot (* base (numerator rate) days)
                                    (* (denominator rate) 365))))
             raw      (+ (part d-early early-rate) (part d-late late-rate))
             rounded  (floor-to raw 100)
             amt      (if (< rounded 1000) 0 rounded)]
         {:delinquency-tax amt :base base
          :days (+ d-early d-late) :days-early d-early :days-late d-late
          :applied (pos? amt)
          :rates {:early early-rate :late late-rate :sourcing (:sourcing rates)}})))))

;; ── 納付方法 ───────────────────────────────────────────────────────────────────
(def PAYMENT-METHODS
  {:e-tax-direct       {:ja "ダイレクト納付 (e-Taxからの口座引落)"            :limit nil      :fee false}
   :e-tax-netbanking   {:ja "インターネットバンキング・ペイジー (Pay-easy)"  :limit nil      :fee false}
   :credit-card        {:ja "クレジットカード納付 (国税クレジットカードお支払サイト)" :limit 9999999 :fee true}
   :convenience-qr     {:ja "コンビニ納付 (QRコード)"                          :limit 300000   :fee false}
   :smartphone-app     {:ja "スマホアプリ納付 (○○Pay 等)"                      :limit 300000   :fee false}
   :bank-counter       {:ja "金融機関の窓口 (納付書持参)"                       :limit nil      :fee false}
   :tax-office-counter {:ja "税務署の窓口 (納付書持参)"                         :limit nil      :fee false}})

(defn applicable-payment-methods
  "納付額 amount に対して利用できる納付方法 (限度額の制約を満たすもの)。
   ※ 振替納税は源泉所得税の対象外 (申告所得税・消費税のみ) なので含めない。"
  [^long amount]
  (->> PAYMENT-METHODS
       (filter (fn [[_ {:keys [limit]}]] (or (nil? limit) (<= amount limit))))
       (map (fn [[k v]] (assoc v :method k)))
       (sort-by :method)
       vec))

;; ── 納付書 (所得税徴収高計算書) レコードの組立 — UNSIGNED (G1) ────────────────────
(defn build-remittance-slip
  "源泉徴収済の内訳から所得税徴収高計算書 (納付書) レコードを組み立てる。
   G1: 何も署名しない (:proof nil, :server-held-authority false, :status prepared-unsigned)。
   実際の納付実行 (e-Tax送信・口座引落) は Council+operator gated (solve が raise)。
     opts {:slip-type :pay-year :pay-month :special? :operated-by
           :lines [{:区分 .. :人員 .. :支給額 .. :税額 ..} ...]}"
  [{:keys [slip-type pay-year pay-month special? operated-by lines]
    :or {special? false lines []}}]
  (let [total (reduce + 0 (map :税額 lines))
        due   (due-date-for-slip slip-type pay-year pay-month special?)]
    {:record-kind :income-tax-collection-slip      ; 所得税徴収高計算書
     :slip-type slip-type
     :slip-name (get-in SLIP-TYPES [slip-type :ja])
     :pay-period {:year pay-year :month pay-month :special special?}
     :legal-due-date (:legal-due-date due)
     :due-date (:due-date due)
     :lines lines
     :total-tax total
     :payment-methods (mapv :method (applicable-payment-methods total))
     :operated-by operated-by                        ; G3 — 呼び出し側が納付主体を渡す
     :proof nil                                       ; G1 — 署名なし
     :server-held-authority SERVER-HELD-AUTHORITY     ; false
     :status "prepared-unsigned"}))

(defn solve
  "Cell entry — R0 は納付書の準備のみ。実際の納付 (e-Tax送信・口座引落・窓口納付) は
   Council+operator gated (G8)。"
  [& _]
  (throw (ex-info
          (str "tax-collect/payment R0: prepares an UNSIGNED 所得税徴収高計算書 only. "
               "Live remittance (e-Tax submission / direct debit) is Council+operator gated.")
          {:server-held-authority SERVER-HELD-AUTHORITY})))
