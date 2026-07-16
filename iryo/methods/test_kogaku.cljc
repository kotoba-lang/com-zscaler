(ns iryo.methods.test-kogaku
  (:require [clojure.test :refer [deftest is]]
            [iryo.methods.kogaku :as k]))

(deftest test-u70-progressive-bands
  (is (= (+ 252600 (int (/ (- 900000 842000) 100))) (k/kogaku-limit-u70 900000 "ア")))
  (is (= (+ 167400 (int (/ (- 900000 558000) 100))) (k/kogaku-limit-u70 900000 "イ")))
  (is (= 87430 (k/kogaku-limit-u70 1000000 "ウ"))))

(deftest test-u70-flat-bands
  (is (= 57600 (k/kogaku-limit-u70 5000000 "エ")))
  (is (= 35400 (k/kogaku-limit-u70 5000000 "オ")))
  (is (nil? (k/kogaku-limit-u70 100000 "Z"))))

(deftest test-o70-geneki-progressive
  (is (= (+ 252600 (int (/ (- 900000 842000) 100))) (k/kogaku-limit-o70 900000 "現役3")))
  (is (= (+ 167400 (int (/ (- 900000 558000) 100))) (k/kogaku-limit-o70 900000 "現役2")))
  (is (= (+ 80100 (int (/ (- 900000 267000) 100))) (k/kogaku-limit-o70 900000 "現役1"))))

(deftest test-o70-flat-gairai-vs-setai
  (is (= 18000 (k/kogaku-limit-o70 500000 "一般" true)))
  (is (= 57600 (k/kogaku-limit-o70 500000 "一般" false)))
  (is (= 8000 (k/kogaku-limit-o70 500000 "低2" true)))
  (is (= 24600 (k/kogaku-limit-o70 500000 "低2" false)))
  (is (= 15000 (k/kogaku-limit-o70 500000 "低1" false))))

(deftest test-o70-full-name-aliases
  (is (= (k/kogaku-limit-o70 500000 "現役3") (k/kogaku-limit-o70 500000 "現役並みⅢ")))
  (is (= 8000 (k/kogaku-limit-o70 500000 "低所得Ⅱ" true))))

(deftest test-dispatch-uses-age-to-pick-regime
  (is (= 18000 (k/kogaku-limit 500000 "一般" 80 true)))
  (is (= 87430 (k/kogaku-limit 1000000 "ウ" 45)))
  (is (nil? (k/kogaku-limit 100000 nil 45))))
