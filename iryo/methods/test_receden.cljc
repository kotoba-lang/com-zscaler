(ns iryo.methods.test-receden
  (:require [clojure.test :refer [deftest is]]
            [iryo.methods.receden :as rcd]
            [iryo.methods.karte :as karte]
            [iryo.methods.rezept :as rez]
            [iryo.methods.masters :as masters])
  (:import [java.time LocalDate]))

(def M (masters/load))

(defn- make-karte []
  {:patient (karte/make-patient "did:web:patient.iryo.etzhayyim.com:zz9" "M" 1980 nil)
   :insurance (karte/make-insurance "01130012" 0.3 "honnin" nil [])
   :diagnoses [(karte/make-diagnosis "8843689" "J069" "急性上気道炎" "2026-06-07" "継続" true)]
   :notes []})

(defn- make-enc []
  {:futan-wari 0.3
   :acts [{:code "111000110" :count 1} {:code "160008010" :count 1}]
   :prescriptions [{:shikibetsu "21" :days 5 :drugs [{:code "620008863" :amount 3}] :label ""}]})

(deftest test-wareki-reiwa-and-showa
  (is (= "5080607" (rcd/wareki (LocalDate/of 2026 6 7))))
  (is (= "50806" (rcd/wareki-ym 2026 6)))
  (is (= "3550401" (rcd/wareki (LocalDate/of 1980 4 1)))))

(deftest test-rezept-shubetsu-encoding
  (is (= "1122" (rcd/rezept-shubetsu :nyuin false :honnin true :kokuho false)))
  (is (= "1216" (rcd/rezept-shubetsu :nyuin true :honnin false :kokuho true))))

(deftest test-build-receden-has-required-records
  (let [r (rez/compute (make-enc) M)
        rows (rcd/build-receden (rcd/make-institution "1" "13" :iryokikan-code "1234567") (make-karte) r
                                :shinryo-year 2026 :shinryo-month 6 :jitsunissu 1)
        ids (mapv first rows)
        summary (rcd/record-summary rows)]
    (is (= "IR" (first ids)))
    (is (some #{"RE"} ids))
    (is (some #{"HO"} ids))
    (is (some #{"SY"} ids))
    (is (some #{"SI"} ids))
    (is (some #{"IY"} ids))
    (is (= 2 (get summary "SI")))
    (is (= 1 (get summary "IY")))))

(deftest test-receden-is-phi-free-by-default
  (let [r (rez/compute (make-enc) M)
        rows (rcd/build-receden (rcd/make-institution "1" "13") (make-karte) r
                                :shinryo-year 2026 :shinryo-month 6)
        re-row (first (filter #(= "RE" (first %)) rows))]
    (is (= "zz9" (nth re-row 4)))
    (is (= "" (nth re-row 6)))
    (let [csv (rcd/to-csv rows)]
      (is (not (.contains csv "山田")))
      (is (.endsWith csv "\r\n")))))

(deftest test-receden-phi-injected-only-via-callback
  (let [r (rez/compute (make-enc) M)
        rows (rcd/build-receden (rcd/make-institution "1" "13") (make-karte) r
                                :shinryo-year 2026 :shinryo-month 6
                                :phi (fn [_] {"name" "ヤマダタロウ" "birth" (LocalDate/of 1980 4 1)}))
        re-row (first (filter #(= "RE" (first %)) rows))]
    (is (= "ヤマダタロウ" (nth re-row 4)))
    (is (= "3550401" (nth re-row 6)))))

(deftest test-ho-record-carries-kyufu-and-totals
  (let [r (rez/compute (make-enc) M)
        rows (rcd/build-receden (rcd/make-institution "1" "13") (make-karte) r
                                :shinryo-year 2026 :shinryo-month 6 :jitsunissu 1)
        ho (first (filter #(= "HO" (first %)) rows))]
    (is (= "7" (nth ho 3)))
    (is (= (str (:total-ten r)) (nth ho 5)))
    (is (= (str (:patient-pay-yen r)) (nth ho 6)))))

(deftest test-receden-carries-futan-kubun-from-kohi
  (let [enc (assoc (make-enc) :kohi [{"futanWari" 0.2}])
        r (rez/compute enc M)
        kt (update-in (make-karte) [:insurance :kohi] (constantly ["54136015"]))
        rows (rcd/build-receden (rcd/make-institution "1" "13") kt r
                                :shinryo-year 2026 :shinryo-month 6)
        si (first (filter #(= "SI" (first %)) rows))]
    (is (= "2" (nth si 2)))
    (is (some #(= "KO" (first %)) rows))))

(deftest test-receden-optional-ty-co-sj-records
  (let [r (rez/compute (make-enc) M)
        rows (rcd/build-receden (rcd/make-institution "1" "13") (make-karte) r
                                :shinryo-year 2026 :shinryo-month 6
                                :tokki ["26区ア"]
                                :comments [{"shikibetsu" "60" "code" "830000001" "text" "前回より継続"}]
                                :shojo-shoki ["経過は安定"])
        ids (set (mapv first rows))]
    (is (contains? ids "TY"))
    (is (contains? ids "CO"))
    (is (contains? ids "SJ"))))
