(ns iryo.methods.test-insurance
  (:require [clojure.test :refer [deftest is]]
            [iryo.methods.insurance :as ins]))

(deftest test-age-kubun
  (is (= "乳幼児" (ins/age-kubun 3)))
  (is (= "成人" (ins/age-kubun 40)))
  (is (= "前期高齢" (ins/age-kubun 72)))
  (is (= "後期高齢" (ins/age-kubun 80))))

(deftest test-futan-wari-by-age
  (is (= 0.2 (ins/futan-wari 3)))
  (is (= 0.3 (ins/futan-wari 40)))
  (is (= 0.2 (ins/futan-wari 72)))
  (is (= 0.3 (ins/futan-wari 72 true)))
  (is (= 0.1 (ins/futan-wari 80)))
  (is (= 0.2 (ins/futan-wari 80 false true)))
  (is (= 0.3 (ins/futan-wari 80 true))))

(deftest test-futan-kubun-codes
  (is (= "1" (ins/futan-kubun 0)))
  (is (= "2" (ins/futan-kubun 1)))
  (is (= "3" (ins/futan-kubun 2)))
  (is (= "5" (ins/futan-kubun 1 false))))

(deftest test-kyufu-wari
  (is (= 7 (ins/kyufu-wari 0.3)))
  (is (= 8 (ins/kyufu-wari 0.2)))
  (is (= 9 (ins/kyufu-wari 0.1)))
  (is (= 10 (ins/kyufu-wari 0.0))))
