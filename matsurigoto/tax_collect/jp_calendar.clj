;; matsurigoto 政 — tax-collect / 日本の国民の祝日 + 税務署閉庁日カレンダー。ADR-2606062300。
;; 納期限 (源泉所得税の法定納期限) が土日祝・年末年始に当たると翌開庁日に繰り下がるため、
;; 「国民の祝日に関する法律」に基づく祝日と税務署の閉庁日 (土日祝 + 12/29–1/3) を判定する。
;;
;; G2 spec-derived: 祝日法 (昭和23年法律第178号)。Happy-Monday・振替休日・国民の休日・
;;   春分/秋分 (近似式 1980–2099) を実装。春分/秋分は天文近似なので :representative
;;   (確定は国立天文台の暦要項; deployment が :authoritative 供給可)。
(ns matsurigoto.tax-collect.jp-calendar
  (:import [java.time LocalDate DayOfWeek]))

(defn- date [y m d] (LocalDate/of (int y) (int m) (int d)))

(defn- nth-weekday
  "その月の n 番目の曜日 (dow = java.time.DayOfWeek) の LocalDate。"
  ^LocalDate [y m dow n]
  (let [first-day (date y m 1)
        first-dow (.getValue (.getDayOfWeek first-day))   ; 1=Mon..7=Sun
        target    (.getValue dow)
        offset    (mod (- target first-dow) 7)]
    (.plusDays first-day (+ offset (* 7 (dec n))))))

(defn vernal-equinox-day
  "春分の日 (3月)。近似式 (1980–2099 有効, :representative)。"
  ^long [y]
  (- (long (Math/floor (+ 20.8431 (* 0.242194 (- y 1980)))))
     (long (Math/floor (/ (- y 1980) 4.0)))))

(defn autumnal-equinox-day
  "秋分の日 (9月)。近似式 (1980–2099 有効, :representative)。"
  ^long [y]
  (- (long (Math/floor (+ 23.2488 (* 0.242194 (- y 1980)))))
     (long (Math/floor (/ (- y 1980) 4.0)))))

(defn- base-holidays
  "振替休日・国民の休日を除く、その年の祝日 (固定 + Happy-Monday + 春分/秋分) の集合。
   2007年以降・現行法 (天皇誕生日2/23, 山の日8/11, スポーツの日) を前提。"
  [y]
  (let [mon DayOfWeek/MONDAY]
    (cond-> #{(date y 1 1)                        ; 元日
              (date y 2 11)                       ; 建国記念の日
              (date y 2 23)                       ; 天皇誕生日 (2020–)
              (date y 4 29)                       ; 昭和の日
              (date y 5 3)                        ; 憲法記念日
              (date y 5 4)                        ; みどりの日
              (date y 5 5)                        ; こどもの日
              (date y 8 11)                       ; 山の日 (2016–)
              (date y 11 3)                       ; 文化の日
              (date y 11 23)                      ; 勤労感謝の日
              (nth-weekday y 1 mon 2)             ; 成人の日 (1月第2月曜)
              (nth-weekday y 7 mon 3)             ; 海の日 (7月第3月曜)
              (nth-weekday y 9 mon 3)             ; 敬老の日 (9月第3月曜)
              (nth-weekday y 10 mon 2)            ; スポーツの日 (10月第2月曜)
              (date y 3 (vernal-equinox-day y))   ; 春分の日
              (date y 9 (autumnal-equinox-day y)) ; 秋分の日
              })))

(defn- sunday? [^LocalDate d] (= DayOfWeek/SUNDAY (.getDayOfWeek d)))

(defn- with-substitutes
  "振替休日 (祝日法3条2項, 2007–): 祝日が日曜なら、その後の最初の非祝日を振替休日に。"
  [holidays]
  (reduce (fn [acc ^LocalDate h]
            (if (sunday? h)
              (loop [d (.plusDays h 1)]
                (if (contains? acc d) (recur (.plusDays d 1)) (conj acc d)))
              acc))
          holidays holidays))

(defn- with-national-holiday
  "国民の休日 (祝日法3条3項): 平日が前後を祝日に挟まれたらその日も休日。"
  [holidays]
  (into holidays
        (for [^LocalDate h holidays
              :let [d (.plusDays h 1)]
              :when (and (not (contains? holidays d))
                         (not (sunday? d))
                         (contains? holidays (.plusDays h 2)))]
          d)))

(defn holidays
  "その年の国民の祝日・休日 (振替休日・国民の休日込み) の集合。"
  [y]
  (-> (base-holidays y) with-substitutes with-national-holiday))

(defn holiday?
  "国民の祝日・休日か。"
  [^LocalDate d]
  (contains? (holidays (.getYear d)) d))

(defn weekend?
  [^LocalDate d]
  (let [dow (.getDayOfWeek d)]
    (or (= dow DayOfWeek/SATURDAY) (= dow DayOfWeek/SUNDAY))))

(defn- year-end-closed?
  "税務署の閉庁日 12/29–1/3 (土日祝以外でも閉庁)。"
  [^LocalDate d]
  (let [m (.getMonthValue d) day (.getDayOfMonth d)]
    (or (and (= m 12) (>= day 29))
        (and (= m 1) (<= day 3)))))

(defn tax-office-closed?
  "税務署の閉庁日 (土日 ∪ 国民の祝日・休日 ∪ 年末年始 12/29–1/3)。"
  [^LocalDate d]
  (or (weekend? d) (holiday? d) (year-end-closed? d)))

(defn next-open-day
  "その日が閉庁日なら翌開庁日まで繰り下げた LocalDate。開庁日ならそのまま。"
  ^LocalDate [^LocalDate d]
  (loop [x d] (if (tax-office-closed? x) (recur (.plusDays x 1)) x)))

(defn parse ^LocalDate [s] (LocalDate/parse s))
