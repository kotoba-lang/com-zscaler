(ns iryo.methods.test-masters
  (:require [clojure.test :refer [deftest is testing]]
            [iryo.methods.masters :as masters]))

(deftest test-seed-loads
  (let [m (masters/load)]
    (is (pos? (count (:shinryo m))))
    (is (pos? (count (:iyaku m))))
    (is (= 10 (:tensu-tanka-yen m)))))

(deftest test-shinryo-lookup
  (let [m (masters/load)]
    (let [item (masters/shinryo m "111000110")]
      (is (= "111000110" (:code item)))
      (is (= 291 (:ten item)))
      (is (= "11" (:shikibetsu item))))))

(deftest test-shinryo-not-found-throws
  (let [m (masters/load)]
    (is (thrown? clojure.lang.ExceptionInfo (masters/shinryo m "999999999")))))

(deftest test-drug-lookup
  (let [m (masters/load)
        d (masters/drug m "620008863")]
    (is (= "620008863" (:code d)))
    (is (= 5.9 (:yakka d)))))

(deftest test-drug-not-found-throws
  (let [m (masters/load)]
    (is (thrown? clojure.lang.ExceptionInfo (masters/drug m "000000000")))))

(deftest test-counts
  (let [m (masters/load)
        c (masters/counts m)]
    (is (pos? (:shinryo c)))
    (is (pos? (:iyaku c)))))

(deftest test-merge-does-not-mutate-base
  (let [base (masters/load)
        n0 (:shinryo (masters/counts base))
        other (masters/from-dict {"shinryo" {"000000010" {"name" "x" "ten" 1 "shikibetsu" "80"}}
                                  "iyaku" {} "tokutei" {} "shobyo" {} "shushokugo" {} "comment" {}})
        _ (masters/merge-masters base other)]
    (is (= n0 (:shinryo (masters/counts base))))))

(deftest test-merge-overrides
  (let [base (masters/load)
        other (masters/from-dict {"shinryo" {"000000010" {"name" "new" "ten" 999 "shikibetsu" "80"}}
                                  "iyaku" {} "tokutei" {} "shobyo" {} "shushokugo" {} "comment" {}})
        merged (masters/merge-masters base other)]
    (is (= 999 (:ten (masters/shinryo merged "000000010"))))))

(deftest test-from-dict
  (let [d {"version" "v1" "tensu_tanka_yen" 10
            "shinryo" {"111" {"name" "test" "ten" 100 "shikibetsu" "11"}}
            "iyaku" {} "tokutei" {} "shobyo" {} "shushokugo" {} "comment" {}}
        m (masters/from-dict d)]
    (is (= 100 (:ten (masters/shinryo m "111"))))))
