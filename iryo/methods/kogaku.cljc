(ns iryo.methods.kogaku)

(def ^:private u70
  {"ア" {:base 252600 :threshold 842000 :rate 0.01}
   "イ" {:base 167400 :threshold 558000 :rate 0.01}
   "ウ" {:base 80100  :threshold 267000 :rate 0.01}
   "エ" {:flat 57600}
   "オ" {:flat 35400}})

(def ^:private o70-geneki
  {"現役3" {:base 252600 :threshold 842000 :rate 0.01}
   "現役2" {:base 167400 :threshold 558000 :rate 0.01}
   "現役1" {:base 80100  :threshold 267000 :rate 0.01}})

(def ^:private o70-flat
  {"一般" {:gairai 18000 :setai 57600}
   "低2"  {:gairai 8000  :setai 24600}
   "低1"  {:gairai 8000  :setai 15000}})

(def ^:private alias-map
  {"現役並みⅢ" "現役3" "現役並み3" "現役3" "現役並みIII" "現役3"
   "現役並みⅡ" "現役2" "現役並み2" "現役2" "現役並みII" "現役2"
   "現役並みⅠ" "現役1" "現役並み1" "現役1" "現役並みI" "現役1"
   "低所得Ⅱ" "低2" "低所得2" "低2" "低所得II" "低2"
   "低所得Ⅰ" "低1" "低所得1" "低1" "低所得I" "低1"})

(defn- canon [kubun]
  (get alias-map kubun kubun))

(defn kogaku-limit-u70 [total-iryohi-yen kubun]
  (when-let [spec (get u70 kubun)]
    (if-let [flat (:flat spec)]
      (int flat)
      (let [{:keys [base threshold]} spec]
        (int (+ base (Math/floor (/ (max 0 (- total-iryohi-yen threshold)) 100))))))))

(defn kogaku-limit-o70
  ([total-iryohi-yen kubun] (kogaku-limit-o70 total-iryohi-yen kubun false))
  ([total-iryohi-yen kubun gairai-only]
   (let [k (canon kubun)]
     (cond
       (get o70-geneki k)
       (let [{:keys [base threshold]} (get o70-geneki k)]
         (int (+ base (Math/floor (/ (max 0 (- total-iryohi-yen threshold)) 100)))))
       (get o70-flat k)
       (let [{:keys [gairai setai]} (get o70-flat k)]
         (int (if gairai-only gairai setai)))
       :else nil))))

(defn kogaku-limit
  ([total-iryohi-yen kubun] (kogaku-limit total-iryohi-yen kubun nil false))
  ([total-iryohi-yen kubun age] (kogaku-limit total-iryohi-yen kubun age false))
  ([total-iryohi-yen kubun age gairai-only]
   (when kubun
     (let [k (canon kubun)
           is-o70 (or (contains? o70-geneki k) (contains? o70-flat k))
           is-u70 (contains? u70 kubun)]
       (if age
         (cond
           (and (>= age 70) is-o70) (kogaku-limit-o70 total-iryohi-yen kubun gairai-only)
           (and (< age 70) is-u70) (kogaku-limit-u70 total-iryohi-yen kubun)
           is-o70 (kogaku-limit-o70 total-iryohi-yen kubun gairai-only)
           is-u70 (kogaku-limit-u70 total-iryohi-yen kubun)
           :else nil)
         (cond
           is-o70 (kogaku-limit-o70 total-iryohi-yen kubun gairai-only)
           is-u70 (kogaku-limit-u70 total-iryohi-yen kubun)
           :else nil))))))
