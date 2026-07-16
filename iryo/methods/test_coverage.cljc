(ns iryo.methods.test-coverage
  (:require [clojure.test :refer [deftest is]]
            [iryo.methods.rezept :as rez]
            [iryo.methods.masters :as masters]))

(def M (masters/load))

(deftest test-futan-wari-derived-from-age
  (let [base [{:code "111000110" :count 1}]]
    (is (= 0.2 (:futan-wari (rez/compute {:futan-wari nil :age 5 :acts base} M))))
    (is (= 0.3 (:futan-wari (rez/compute {:futan-wari nil :age 40 :acts base} M))))
    (is (= 0.1 (:futan-wari (rez/compute {:futan-wari nil :age 80 :acts base} M))))))

(deftest test-all-kubun-categories-aggregate
  (let [enc {:futan-wari 0.3
             :acts [{:code "111000110"} {:code "112007410"} {:code "112011010"}
                    {:code "113002510"} {:code "113001610"} {:code "140000110"}
                    {:code "150295810"} {:code "150000490"} {:code "160008010"}
                    {:code "160218010"} {:code "170018510"} {:code "120002910"}
                    {:code "190000810"}]
             :prescriptions [{:shikibetsu "21" :days 1 :drugs [{:code "620008863" :amount 1}] :label ""}]}
        r (rez/compute enc M)]
    (doseq [k ["初診" "再診" "医学管理" "在宅" "投薬" "処置" "手術"
               "麻酔" "検査" "病理" "画像診断" "その他" "入院"]]
      (is (contains? (:kubun-totals r) k) (str "missing 区分 " k)))
    (is (= "初診" (first (keys (:kubun-totals r)))))
    (is (= "入院" (last (keys (:kubun-totals r)))))))

(deftest test-nyuin-with-shokuji-standard-burden
  (let [enc {:futan-wari 0.3 :nyuin true :shokuji-meals 6 :shokuji-tanka-yen 490
             :acts [{:code "190000810" :count 5}]}
        r (rez/compute enc M)]
    (is (= true (:nyuin r)))
    (is (= 2940 (:shokuji-futan-yen r)))
    (is (= (+ (:patient-pay-yen r) 2940) (:total-futan-yen r)))
    (is (= {"入院" (* 1688 5)} (:kubun-totals r)))))

(deftest test-kohi-seikatsuhogo-zeroes-patient-pay
  (let [enc {:futan-wari 0.3 :acts [{:code "111000110" :count 1}]
             :kohi [{"futanWari" 0.0}]}
        r (rez/compute enc M)]
    (is (= 0 (:patient-pay-yen r)))
    (is (= "2" (:futan-kubun r)))
    (is (every? #(= "2" (:futan-kubun %)) (:lines r)))))

(deftest test-kohi-with-jiko-futan-gendo-caps-pay
  (let [enc {:futan-wari 0.3 :acts [{:code "170018510" :count 50}]
             :kohi [{"futanWari" 0.2 "jikoFutanGendo" 5000}]}
        r (rez/compute enc M)]
    (is (= 5000 (:patient-pay-yen r)))))

(deftest test-kogaku-o70-ippan-gairai-cap
  (let [enc {:futan-wari nil :age 80 :kogaku-kubun "一般" :nyuin false
             :acts [{:code "170018510" :count 100}]}
        r (rez/compute enc M)]
    (is (= 210000 (:total-iryohi-yen r)))
    (is (= 21000 (:ichibu-futan-yen r)))
    (is (= 18000 (:kogaku-limit-yen r)))
    (is (= true (:kogaku-applied r)))
    (is (= 18000 (:patient-pay-yen r)))))

(deftest test-kogaku-o70-nyuin-uses-setai-limit
  (let [enc {:futan-wari nil :age 80 :kogaku-kubun "一般" :nyuin true
             :acts [{:code "190000810" :count 50}]}
        r (rez/compute enc M)]
    (is (= 57600 (:kogaku-limit-yen r)))
    (is (= true (:kogaku-applied r)))
    (is (= 57600 (:patient-pay-yen r)))))
