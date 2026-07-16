(ns iryo.methods.test-rezept
  (:require [clojure.test :refer [deftest is]]
            [iryo.methods.rezept :as rez]
            [iryo.methods.kogaku :as k]
            [iryo.methods.masters :as masters]))

(def M (masters/load))

(deftest test-yakka-to-ten-under-15-is-one-point
  (is (= 1 (rez/yakka-to-ten 15)))
  (is (= 1 (rez/yakka-to-ten 5.9)))
  (is (= 1 (rez/yakka-to-ten 10.1))))

(deftest test-yakka-to-ten-gosha-gocho
  (is (= 2 (rez/yakka-to-ten 21)))
  (is (= 2 (rez/yakka-to-ten 25)))
  (is (= 3 (rez/yakka-to-ten 26)))
  (is (= 6 (rez/yakka-to-ten 56.4)))
  (is (= 19 (rez/yakka-to-ten 193))))

(deftest test-round-ichibu-futan
  (is (= 870 (rez/round-ichibu-futan 873)))
  (is (= 880 (rez/round-ichibu-futan 875)))
  (is (= 1080 (rez/round-ichibu-futan 1080)))
  (is (= 1080 (rez/round-ichibu-futan 1084)))
  (is (= 1090 (rez/round-ichibu-futan 1086))))

(deftest test-kogaku-limit-u-band-is-progressive
  ;; Direct test via kogaku namespace
  (is (= 87430 (k/kogaku-limit 1000000 "ウ"))))

(deftest test-compute-outpatient-basic
  (let [enc {:futan-wari 0.3 :acts [{:code "111000110" :count 1}
                                    {:code "160008010" :count 1}
                                    {:code "160019410" :count 1}]}
        r (rez/compute enc M)]
    (is (= 361 (:total-ten r)))
    (is (= {"初診" 291 "検査" 70} (:kubun-totals r)))
    (is (= 3610 (:total-iryohi-yen r)))
    (is (= 1080 (:ichibu-futan-yen r)))
    (is (= false (:kogaku-applied r)))
    (is (= 1080 (:patient-pay-yen r)))))

(deftest test-compute-drug-internal-multiplies-days
  (let [enc {:futan-wari 0.3 :prescriptions [{:shikibetsu "21" :days 5 :drugs [{:code "620008863" :amount 3}] :label ""}]}
        r (rez/compute enc M)
        drug-line (first (filter #(= "drug" (:kind %)) (:lines r)))]
    (is (= 2 (:unit-ten drug-line)))
    (is (= 5 (:count drug-line)))
    (is (= 10 (:ten drug-line)))
    (is (= {"投薬" 10} (:kubun-totals r)))))

(deftest test-compute-act-count-multiplies
  (let [enc {:futan-wari 0.3 :acts [{:code "170018510" :count 2}]}
        r (rez/compute enc M)]
    (is (= 420 (:total-ten r)))
    (is (= {"画像診断" 420} (:kubun-totals r)))))

(deftest test-compute-material-is-yakka-converted
  (let [enc {:materials [{:code "700020000" :amount 1.0 :shikibetsu "40"}]}
        r (rez/compute enc M)
        mat (first (filter #(= "material" (:kind %)) (:lines r)))]
    (is (= 56 (:ten mat)))
    (is (= {"処置" 56} (:kubun-totals r)))))

(deftest test-compute-applies-kogaku-cap
  (let [enc {:futan-wari 0.3 :kogaku-kubun "オ" :acts [{:code "170018510" :count 60}]}
        r (rez/compute enc M)]
    (is (= 126000 (:total-iryohi-yen r)))
    (is (= 37800 (:ichibu-futan-yen r)))
    (is (= 35400 (:kogaku-limit-yen r)))
    (is (= true (:kogaku-applied r)))
    (is (= 35400 (:patient-pay-yen r)))))

(deftest test-compute-kogaku-not-applied-when-under-limit
  (let [enc {:futan-wari 0.3 :kogaku-kubun "ウ" :acts [{:code "111000110" :count 1}]}
        r (rez/compute enc M)]
    (is (= false (:kogaku-applied r)))
    (is (= (:ichibu-futan-yen r) (:patient-pay-yen r)))))

(deftest test-futan-wari-zero-means-no-patient-pay
  (let [enc {:futan-wari 0.0 :acts [{:code "111000110" :count 1}]}
        r (rez/compute enc M)]
    (is (= 0 (:ichibu-futan-yen r)))
    (is (= 0 (:patient-pay-yen r)))))
