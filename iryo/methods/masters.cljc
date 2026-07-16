(ns iryo.methods.masters
  (:require [cheshire.core :as json]
            [clojure.java.io :as io]))

(defn- seed-path []
  "20-actors/iryo/data/seed_masters.json")

(defn load-json [path]
  (-> (slurp path) (json/parse-string)))

(defn from-dict [d]
  {:version (get d "version" "unknown")
   :tensu-tanka-yen (int (get d "tensu_tanka_yen" 10))
   :shinryo (into {} (for [[k v] (get d "shinryo" {})]
                       [k {:code k :name (get v "name") :ten (int (get v "ten" 0)) :shikibetsu (get v "shikibetsu" "")}]))
   :iyaku (into {} (for [[k v] (get d "iyaku" {})]
                     [k {:code k :name (get v "name") :yakka (double (get v "yakka" 0.0)) :unit (get v "unit" "")}]))
   :tokutei (into {} (for [[k v] (get d "tokutei" {})]
                       [k {:code k :name (get v "name") :yakka (double (get v "yakka" 0.0)) :unit (get v "unit" "")}]))
   :shobyo (into {} (for [[k v] (get d "shobyo" {})]
                      [k {:code k :name (get v "name") :icd10 (get v "icd10" "")}]))
   :shushokugo (into {} (for [[k v] (get d "shushokugo" {})]
                          [k {:code k :name (get v "name")}]))
   :comment (into {} (for [[k v] (get d "comment" {})]
                       [k {:code k :pattern (get v "pattern" "") :name (get v "name" "")}]))})

(defn load []
  (from-dict (load-json (seed-path))))

(def default-masters load)

(defn shinryo [m code]
  (or (get (:shinryo m) code)
      (throw (ex-info (str "診療行為コード not in master: " code) {:error :not-found :code code}))))

(defn drug [m code]
  (or (get (:iyaku m) code)
      (throw (ex-info (str "医薬品コード not in master: " code) {:error :not-found :code code}))))

(defn material [m code]
  (or (get (:tokutei m) code)
      (throw (ex-info (str "特定器材コード not in master: " code) {:error :not-found :code code}))))

(defn shobyo [m code]
  (or (get (:shobyo m) code)
      (throw (ex-info (str "傷病名コード not in master: " code) {:error :not-found :code code}))))

(defn shushokugo [m code]
  (or (get (:shushokugo m) code)
      (throw (ex-info (str "修飾語コード not in master: " code) {:error :not-found :code code}))))

(defn comment [m code]
  (or (get (:comment m) code)
      (throw (ex-info (str "コメントコード not in master: " code) {:error :not-found :code code}))))

(defn has-shinryo? [m code]
  (boolean (get (:shinryo m) code)))

(defn counts [m]
  {:shinryo (count (:shinryo m))
   :iyaku (count (:iyaku m))
   :tokutei (count (:tokutei m))
   :shobyo (count (:shobyo m))
   :shushokugo (count (:shushokugo m))
   :comment (count (:comment m))})

(defn merge-masters [base other]
  {:version (str (:version base) "+" (:version other))
   :tensu-tanka-yen (or (:tensu-tanka-yen other) (:tensu-tanka-yen base))
   :shinryo (merge (:shinryo base) (:shinryo other))
   :iyaku (merge (:iyaku base) (:iyaku other))
   :tokutei (merge (:tokutei base) (:tokutei other))
   :shobyo (merge (:shobyo base) (:shobyo other))
   :shushokugo (merge (:shushokugo base) (:shushokugo other))
   :comment (merge (:comment base) (:comment other))})
