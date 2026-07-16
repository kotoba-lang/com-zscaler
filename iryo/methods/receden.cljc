(ns iryo.methods.receden
  (:require [clojure.string :as str])
  (:import [java.time LocalDate]))

(def ^:private eras
  [[(LocalDate/of 2019 5 1)  5 2018]   ;; 令和
   [(LocalDate/of 1989 1 8)  4 1988]   ;; 平成
   [(LocalDate/of 1926 12 25) 3 1925]  ;; 昭和
   [(LocalDate/of 1912 7 30)  2 1911]  ;; 大正
   [(LocalDate/of 1868 1 25)  1 1867]]); ;; 明治

(defn wareki [d]
  (let [found (first (filter (fn [[start _ _]] (not (.isBefore d start))) eras))]
    (when (nil? found) (throw (ex-info (str "date out of supported 元号 range: " d) {})))
    (let [[_ code base] found]
      (format "%d%02d%02d%02d" code (- (.getYear d) base) (.getMonthValue d) (.getDayOfMonth d)))))

(defn wareki-ym [year month]
  (subs (wareki (LocalDate/of year month 1)) 0 5))

(defn rezept-shubetsu [& {:keys [nyuin honnin kokuho]}]
  (str "1" (if kokuho "2" "1") (if nyuin "1" "2") (if honnin "2" "6")))

(defn- sex-code [sex]
  (get {"M" "1" "男" "1" "1" "1" "F" "2" "女" "2" "2" "2"} sex "3"))

(defn- tenki-code [outcome]
  (get {"継続" "" "治癒" "1" "死亡" "2" "中止" "3" "軽快" "4"} outcome ""))

(defn make-institution [shinsa-shiharai prefecture & {:keys [tensu-hyo iryokikan-code name]
                                                       :or {tensu-hyo "1" iryokikan-code "0000000" name ""}}]
  {:shinsa-shiharai shinsa-shiharai :prefecture prefecture :tensu-hyo tensu-hyo
   :iryokikan-code iryokikan-code :name name})

(defn- parse-date [s]
  (let [[y mo d] (map #(Integer/parseInt %) (str/split s #"-"))]
    (LocalDate/of y mo d)))

(defn build-receden [inst karte rez & {:keys [shinryo-year shinryo-month jitsunissu nyuin rezept-no phi tokki comments shojo-shoki]
                                        :or {shinryo-year 2026 shinryo-month 6 jitsunissu 1 nyuin false rezept-no 1}}]
  (let [rows (atom [])
        phi-data (if phi (phi karte) {})]

    ;; IR
    (swap! rows conj ["IR" (:shinsa-shiharai inst) (:prefecture inst) (or (:tensu-hyo inst) "1")
                      (or (:iryokikan-code inst) "0000000") "1" (wareki-ym shinryo-year shinryo-month)])

    ;; RE
    (let [shubetsu (rezept-shubetsu :nyuin nyuin
                                     :honnin (= (get-in karte [:insurance :honnin-kazoku]) "honnin")
                                     :kokuho (= (count (get-in karte [:insurance :hokensha-bango])) 6))
          did-tail (last (str/split (get-in karte [:patient :pseudonym-did]) #":"))
          name (or (get phi-data "name") did-tail)
          birth (if (get phi-data "birth")
                  (wareki (get phi-data "birth"))
                  "")]
      (swap! rows conj ["RE" (str rezept-no) shubetsu (wareki-ym shinryo-year shinryo-month)
                        name (sex-code (get-in karte [:patient :sex])) birth]))

    ;; TY
    (when tokki
      (swap! rows conj (into ["TY"] tokki)))

    ;; HO
    (let [kyufu (int (Math/round (* (- 1.0 (:futan-wari rez)) 10.0)))]
      (swap! rows conj ["HO" (get-in karte [:insurance :hokensha-bango]) (get phi-data "hihokensha" "")
                        (str kyufu) (str jitsunissu) (str (:total-ten rez)) (str (:patient-pay-yen rez))]))

    ;; KO
    (doseq [fusha (get-in karte [:insurance :kohi] [])]
      (swap! rows conj ["KO" fusha (str (:total-ten rez)) (str (:patient-pay-yen rez))]))

    ;; SY
    (doseq [d (:diagnoses karte)]
      (let [onset (if (:onset d)
                    (wareki (parse-date (:onset d)))
                    "")]
        (swap! rows conj ["SY" (:shobyo-code d) onset (tenki-code (:outcome d))
                          (if (:is-main d) "01" "") (:icd10 d)])))

    ;; SI / IY / TO
    (doseq [line (:lines rez)]
      (let [fk (or (:futan-kubun line) "1")]
        (cond
          (= (:kind line) "act")
          (swap! rows conj ["SI" (:shikibetsu line) fk (:code line)
                            (str (:count line)) (str (:unit-ten line)) (str (:count line))])
          (= (:kind line) "drug")
          (swap! rows conj ["IY" (:shikibetsu line) fk (:code line)
                            "" (str (:unit-ten line)) (str (:count line))])
          (= (:kind line) "material")
          (swap! rows conj ["TO" (:shikibetsu line) fk (:code line)
                            "" (str (:ten line)) (str (:count line))]))))

    ;; CO
    (doseq [c (or comments [])]
      (swap! rows conj ["CO" (get c "shikibetsu" "") (get c "code" "") (get c "text" "")]))

    ;; SJ
    (doseq [[i sj] (map-indexed #(vector (inc %1) %2) (or shojo-shoki []))]
      (swap! rows conj ["SJ" (format "%02d" i) sj]))

    @rows))

(defn- q-cell [s]
  (let [s (str s)]
    (if (or (str/includes? s ",") (str/includes? s "\""))
      (str "\"" s "\"")
      s)))

(defn to-csv [rows & {:keys [newline] :or {newline "\r\n"}}]
  (str (str/join newline (map (fn [row] (str/join "," (map q-cell row))) rows)) newline))

(defn record-summary [rows]
  (reduce (fn [acc row] (update acc (first row) (fnil inc 0))) {} rows))

(defn uke-filename [inst year month]
  (format "RECEIPTC_%s_%d%02d.UKE" (:iryokikan-code inst) year month))
