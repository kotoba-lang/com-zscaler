(ns iryo.methods.rezept
  (:require [iryo.methods.masters :as masters]
            [iryo.methods.insurance :as insurance]
            [iryo.methods.kogaku :as kogaku]))

(def shikibetsu-kubun
  {"11" "初診" "12" "再診" "13" "医学管理" "14" "在宅"
   "21" "投薬" "22" "投薬" "23" "投薬" "24" "投薬"
   "25" "投薬" "26" "投薬" "27" "投薬" "28" "投薬"
   "31" "注射" "32" "注射" "33" "注射"
   "40" "処置" "50" "手術" "54" "麻酔"
   "60" "検査" "64" "病理" "70" "画像診断"
   "80" "その他" "90" "入院"})

(def kubun-order
  ["初診" "再診" "医学管理" "在宅" "投薬" "注射"
   "処置" "手術" "麻酔" "検査" "病理" "画像診断" "その他" "入院"])

(def ^:private kubun-rank
  (into {} (map-indexed (fn [i k] [k i]) kubun-order)))

(defn yakka-to-ten [price-yen]
  (if (<= price-yen 15)
    1
    (let [q (/ price-yen 10.0)
          floor-q (Math/floor q)
          frac (- q floor-q)]
      (int (if (<= frac (+ 0.5 1e-9)) floor-q (+ floor-q 1))))))

(defn round-ichibu-futan [yen]
  (* (int (/ (+ (int (Math/round (double yen))) 5) 10)) 10))

(defn- kubun-of [shikibetsu]
  (get shikibetsu-kubun shikibetsu "その他"))

(defn compute-drug-ten [rx m]
  (let [yakka-per-unit (reduce + 0.0 (map (fn [d] (* (:yakka (masters/drug m (:code d))) (:amount d))) (:drugs rx)))
        ten-per-unit (yakka-to-ten yakka-per-unit)]
    (* ten-per-unit (max 1 (:days rx 1)))))

(defn resolved-futan-wari [enc]
  (if-let [fw (:futan-wari enc)]
    fw
    (if-let [age (:age enc)]
      (insurance/futan-wari age (boolean (:gen-eki enc)) (boolean (:ittei-ijo enc)))
      0.3)))

(defn compute [enc m]
  (let [fk (insurance/futan-kubun (count (:kohi enc [])))
        ;; 1) 診療行為
        act-lines (mapv (fn [a]
                          (let [item (masters/shinryo m (:code a))
                                cnt (or (:count a) 1)]
                            {:kind "act" :shikibetsu (:shikibetsu item) :kubun (kubun-of (:shikibetsu item))
                             :code (:code item) :name (:name item) :unit-ten (:ten item)
                             :count cnt :ten (* (:ten item) cnt) :futan-kubun fk}))
                        (:acts enc []))
        ;; 2) 薬剤料
        drug-lines (mapv (fn [rx]
                           (let [ten (compute-drug-ten rx m)
                                 names (clojure.string/join "+" (map #(:name (masters/drug m (:code %))) (:drugs rx)))
                                 label (if (seq (or (:label rx) "")) (:label rx) names)
                                 days (max 1 (or (:days rx) 1))
                                 unit-ten (int (/ ten days))]
                             {:kind "drug" :shikibetsu (:shikibetsu rx) :kubun (kubun-of (:shikibetsu rx))
                              :code (if (seq (:drugs rx)) (:code (first (:drugs rx))) "")
                              :name label :unit-ten unit-ten :count days :ten ten :futan-kubun fk}))
                         (:prescriptions enc []))
        ;; 3) 特定器材
        mat-lines (mapv (fn [mat]
                          (let [item (masters/material m (:code mat))
                                ten (yakka-to-ten (* (:yakka item) (or (:amount mat) 1.0)))]
                            {:kind "material" :shikibetsu (or (:shikibetsu mat) "40") :kubun (kubun-of (or (:shikibetsu mat) "40"))
                             :code (:code item) :name (:name item) :unit-ten ten :count 1 :ten ten :futan-kubun fk}))
                        (:materials enc []))
        lines (concat act-lines drug-lines mat-lines)
        kubun-totals-raw (reduce (fn [acc l] (update acc (:kubun l) (fnil + 0) (:ten l))) {} lines)
        total-ten (reduce + 0 (map :ten lines))
        wari (resolved-futan-wari enc)
        tanka (or (:tensu-tanka-yen m) 10)
        total-iryohi-yen (* total-ten tanka)
        ichibu (round-ichibu-futan (* total-iryohi-yen wari))
        limit (when (:kogaku-kubun enc)
                (kogaku/kogaku-limit total-iryohi-yen (:kogaku-kubun enc) (:age enc) (not (boolean (:nyuin enc)))))
        applied (and limit (> ichibu limit))
        patient-pay (if applied limit ichibu)
        ;; 公費 — kohi entries may be maps with keyword or string keys
        patient-pay (reduce (fn [pp k]
                              (let [kf (double (or (get k :futan-wari) (get k "futanWari") 0.0))
                                    kohi-cap (round-ichibu-futan (* total-iryohi-yen kf))
                                    pp2 (min pp kohi-cap)
                                    gendo (or (get k :jiko-futan-gendo) (get k "jikoFutanGendo"))]
                                (if gendo (min pp2 (int gendo)) pp2)))
                            patient-pay (:kohi enc []))
        shokuji (if (boolean (:nyuin enc)) (* (or (:shokuji-meals enc) 0) (or (:shokuji-tanka-yen enc) 490)) 0)
        total-futan (+ (int patient-pay) shokuji)
        sorted-base (sorted-map-by (fn [a b] (compare (get kubun-rank a 99) (get kubun-rank b 99))))
        ordered (reduce (fn [acc k] (if (contains? kubun-totals-raw k) (assoc acc k (get kubun-totals-raw k)) acc))
                        sorted-base kubun-order)]
    {:lines (vec lines)
     :kubun-totals ordered
     :total-ten total-ten
     :total-iryohi-yen total-iryohi-yen
     :futan-wari wari
     :ichibu-futan-yen ichibu
     :kogaku-kubun (:kogaku-kubun enc)
     :kogaku-limit-yen limit
     :kogaku-applied (boolean applied)
     :patient-pay-yen (int patient-pay)
     :nyuin (boolean (:nyuin enc))
     :shokuji-futan-yen shokuji
     :total-futan-yen total-futan
     :futan-kubun fk}))

(defn result->dict [r]
  {"lines" (mapv (fn [l] {"kind" (:kind l) "shikibetsu" (:shikibetsu l) "kubun" (:kubun l)
                           "code" (:code l) "name" (:name l) "unitTen" (:unit-ten l)
                           "count" (:count l) "ten" (:ten l) "futanKubun" (:futan-kubun l)}) (:lines r))
   "kubunTotals" (into {} (map (fn [[k v]] [k v]) (:kubun-totals r)))
   "totalTen" (:total-ten r)
   "totalIryohiYen" (:total-iryohi-yen r)
   "futanWari" (:futan-wari r)
   "ichibuFutanYen" (:ichibu-futan-yen r)
   "kogakuKubun" (:kogaku-kubun r)
   "kogakuLimitYen" (:kogaku-limit-yen r)
   "kogakuApplied" (:kogaku-applied r)
   "patientPayYen" (:patient-pay-yen r)
   "nyuin" (:nyuin r)
   "shokujiFutanYen" (:shokuji-futan-yen r)
   "totalFutanYen" (:total-futan-yen r)
   "futanKubun" (:futan-kubun r)})
