(ns iryo.methods.insurance)

(defn age-kubun [age]
  (cond
    (< age 6) "乳幼児"
    (< age 70) "成人"
    (< age 75) "前期高齢"
    :else "後期高齢"))

(defn futan-wari
  ([age] (futan-wari age false false))
  ([age gen-eki] (futan-wari age gen-eki false))
  ([age gen-eki ittei-ijo]
   (let [k (age-kubun age)]
     (cond
       (= k "乳幼児") 0.2
       (= k "成人") 0.3
       (= k "前期高齢") (if gen-eki 0.3 0.2)
       :else ;; 後期高齢
       (cond gen-eki 0.3
             ittei-ijo 0.2
             :else 0.1)))))

(defn futan-kubun
  ([num-kohi] (futan-kubun num-kohi true))
  ([num-kohi hoken]
   (cond
     (and hoken (= num-kohi 0)) "1"
     (and hoken (= num-kohi 1)) "2"
     (and hoken (>= num-kohi 2)) "3"
     (and (not hoken) (>= num-kohi 1)) "5"
     :else "1")))

(defn kyufu-wari [patient-futan-wari]
  (int (Math/round (* (- 1.0 patient-futan-wari) 10))))
