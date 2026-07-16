(ns iryo.methods.test-master-loader
  (:require [clojure.test :refer [deftest is testing]]
            [iryo.methods.master-loader :as ml]
            [iryo.methods.masters :as masters]
            [clojure.java.io :as io])
  (:import [java.nio.file Files]
           [java.nio.file.attribute FileAttribute]))

(defn- make-temp-dir []
  (str (Files/createTempDirectory "iryo-test" (into-array FileAttribute []))))

(defn- write-file [dir name text]
  (spit (str dir "/" name) text))

(deftest test-load-normalized-all-classes
  (let [d (make-temp-dir)]
    (write-file d "shinryo.csv" "# code,name,ten,shikibetsu\n999999910,新規診療行為,123,60\n")
    (write-file d "iyaku.csv" "999999920,新規薬剤,30.0,錠\n")
    (write-file d "shobyo.csv" "9999999,新規病名,Z999\n")
    (write-file d "shushokugo.csv" "9001,新規修飾語\n")
    (write-file d "comment.csv" "899999999,free,新規コメント\n")
    (let [m (masters/from-dict (ml/load-normalized d))]
      (is (= 123 (:ten (masters/shinryo m "999999910"))))
      (is (= "60" (:shikibetsu (masters/shinryo m "999999910"))))
      (is (= 30.0 (:yakka (masters/drug m "999999920"))))
      (is (= "Z999" (:icd10 (masters/shobyo m "9999999"))))
      (is (= "新規修飾語" (:name (masters/shushokugo m "9001"))))
      (is (= "新規コメント" (:name (masters/comment m "899999999")))))))

(deftest test-official-master-merges-over-seed
  (let [d (make-temp-dir)]
    (write-file d "shinryo.csv" "888888810,特殊手技,9999,50\n")
    (let [merged (ml/masters-with-official d "normalized")]
      (is (= 291 (:ten (masters/shinryo merged "111000110"))))
      (is (= 9999 (:ten (masters/shinryo merged "888888810"))))
      (is (>= (:shinryo (masters/counts merged)) 2)))))

(deftest test-mhlw-colmap-parse-tolerant
  (let [d (make-temp-dir)
        ;; Build a row with enough columns: idx 2=code, idx 4=name, idx 8=shikibetsu, idx 22=value
        row (into [] (concat ["1" "S" "777777710" "" "手技名"] (repeat 3 "") ["60"] (repeat 13 "") ["456"]))]
    (write-file d "s_test.csv" (clojure.string/join "," row))
    (let [out (ml/load-mhlw-shinryo (str d "/s_test.csv")
                                     {:code 2 :name 4 :value 22 :shikibetsu 8 :unit -1 :icd10 -1})]
      (is (= 456 (get-in out ["777777710" "ten"])))
      (is (= "60" (get-in out ["777777710" "shikibetsu"]))))))

(deftest test-merge-does-not-mutate-base
  (let [base (masters/load)
        n0 (:shinryo (masters/counts base))
        other (masters/from-dict {"shinryo" {"000000010" {"name" "x" "ten" 1 "shikibetsu" "80"}}
                                  "iyaku" {} "tokutei" {} "shobyo" {} "shushokugo" {} "comment" {}})
        _ (masters/merge-masters base other)]
    (is (= n0 (:shinryo (masters/counts base))))))
