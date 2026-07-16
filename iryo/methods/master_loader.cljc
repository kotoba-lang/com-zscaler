(ns iryo.methods.master-loader
  (:require [clojure.string :as str]
            [clojure.java.io :as io]
            [iryo.methods.masters :as masters]))

(def mhlw-defaults
  {:shinryo {:code 2 :name 4 :value 22 :shikibetsu 8 :unit -1 :icd10 -1}
   :iyaku   {:code 2 :name 4 :value 8  :unit 6    :shikibetsu -1 :icd10 -1}
   :tokutei {:code 2 :name 4 :value 8  :unit 6    :shikibetsu -1 :icd10 -1}
   :shobyo  {:code 2 :name 5 :value -1 :icd10 6   :shikibetsu -1 :unit -1}})

(defn- read-rows [path]
  (when (.exists (io/file path))
    (->> (str/split-lines (slurp path))
         (remove #(or (str/blank? %) (str/starts-with? (str/triml %) "#")))
         (mapv (fn [line]
                 ;; simple CSV parse (no quoted fields in test data)
                 (mapv str/trim (str/split line #",")))))))

(defn- opt-rows [directory name]
  (let [p (str directory "/" name)]
    (if (.exists (io/file p))
      (or (read-rows p) [])
      [])))

(defn load-normalized [directory]
  (let [raw {"version" (str "normalized:" (.getName (io/file directory)))
              "tensu_tanka_yen" 10
              "shinryo" {} "iyaku" {} "tokutei" {} "shobyo" {} "shushokugo" {} "comment" {}}
        shinryo (reduce (fn [acc r]
                          (if (>= (count r) 4)
                            (assoc acc (nth r 0) {"name" (nth r 1) "ten" (Integer/parseInt (nth r 2)) "shikibetsu" (nth r 3)})
                            acc))
                        {} (opt-rows directory "shinryo.csv"))
        iyaku (reduce (fn [acc r]
                        (if (>= (count r) 3)
                          (assoc acc (nth r 0) {"name" (nth r 1) "yakka" (Double/parseDouble (nth r 2))
                                                "unit" (if (> (count r) 3) (nth r 3) "")})
                          acc))
                      {} (opt-rows directory "iyaku.csv"))
        tokutei (reduce (fn [acc r]
                          (if (>= (count r) 3)
                            (assoc acc (nth r 0) {"name" (nth r 1) "yakka" (Double/parseDouble (nth r 2))
                                                  "unit" (if (> (count r) 3) (nth r 3) "")})
                            acc))
                        {} (opt-rows directory "tokutei.csv"))
        shobyo (reduce (fn [acc r]
                         (if (>= (count r) 2)
                           (assoc acc (nth r 0) {"name" (nth r 1)
                                                 "icd10" (if (> (count r) 2) (nth r 2) "")})
                           acc))
                       {} (opt-rows directory "shobyo.csv"))
        shushokugo (reduce (fn [acc r]
                             (if (>= (count r) 2)
                               (assoc acc (nth r 0) {"name" (nth r 1)})
                               acc))
                           {} (opt-rows directory "shushokugo.csv"))
        comment (reduce (fn [acc r]
                          (if (>= (count r) 1)
                            (assoc acc (nth r 0)
                                   {"pattern" (if (> (count r) 1) (nth r 1) "")
                                    "name" (if (> (count r) 2) (nth r 2) "")})
                            acc))
                        {} (opt-rows directory "comment.csv"))]
    (assoc raw
           "shinryo" shinryo "iyaku" iyaku "tokutei" tokutei
           "shobyo" shobyo "shushokugo" shushokugo "comment" comment)))

(defn- cell [row idx]
  (if (and (>= idx 0) (< idx (count row)))
    (str/trim (nth row idx))
    ""))

(defn load-mhlw-shinryo
  ([path] (load-mhlw-shinryo path nil))
  ([path colmap]
   (let [cm (or colmap (:shinryo mhlw-defaults))]
     (reduce (fn [out row]
               (let [code (cell row (:code cm))]
                 (if (str/blank? code)
                   out
                   (let [val-str (cell row (:value cm))
                         ten (try (int (Double/parseDouble (if (str/blank? val-str) "0" val-str)))
                                  (catch Exception _ 0))]
                     (assoc out code {"name" (cell row (:name cm))
                                      "ten" ten
                                      "shikibetsu" (let [s (cell row (:shikibetsu cm))]
                                                     (if (str/blank? s) "80" s))})))))
             {} (or (read-rows path) [])))))

(defn masters-with-official
  ([directory] (masters-with-official directory "normalized" nil))
  ([directory fmt] (masters-with-official directory fmt nil))
  ([directory fmt base]
   (let [raw (load-normalized directory)
         loaded (masters/from-dict raw)
         base-m (or base (masters/load))]
     (masters/merge-masters base-m loaded))))
