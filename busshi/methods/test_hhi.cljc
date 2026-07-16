#!/usr/bin/env bb
;; busshi 物資 — validation of the Herfindahl–Hirschman concentration index.
;; Run:  bb --classpath 20-actors 20-actors/busshi/methods/test_hhi.cljc
(ns busshi.methods.test-hhi
  "Validation of the Herfindahl–Hirschman concentration index (named-hhi) — the §2(l)
  multi-generational (子・孫) RISK-axis metric over named producer shares — which had no direct
  test. HHI is the sum of squared market shares; a regression in the formula would silently
  mis-rank which commodities are dangerously concentrated. This pins its analytical anchors:
    - a monopoly (one 100% producer) = 1
    - n equal producers = 1/n  (the minimum concentration for n named producers)
    - HHI = Σ share² / 10000  (shares as percentages)
    - the fragmented :other residual is excluded (HHI is a lower bound on true concentration)
    - it rises monotonically with skew (a lopsided split scores higher than an even one)."
  (:require [busshi.methods.analyze :as a]
            [clojure.test :refer [deftest is run-tests]]))

(defn- close? [x y] (< (Math/abs (- (double x) (double y))) 1e-9))
(defn- commodity [pairs] {:producers pairs})

(deftest hhi-monopoly-is-one
  (is (close? (a/named-hhi (commodity [[:x 100]])) 1.0) "a single 100% producer → HHI = 1"))

(deftest hhi-of-n-equal-producers-is-one-over-n
  (doseq [n [2 4 5 10]]
    (let [share (/ 100.0 n)
          c (commodity (mapv (fn [i] [(keyword (str "p" i)) share]) (range n)))]
      (is (close? (a/named-hhi c) (/ 1.0 n)) (str n " equal producers → HHI = 1/" n)))))

(deftest hhi-equals-sum-of-squared-shares-over-10000
  (doseq [pairs [[[:a 60] [:b 30] [:c 10]] [[:a 40] [:b 35] [:c 25]] [[:a 90] [:b 10]]]]
    (let [expect (/ (reduce + (map (fn [[_ s]] (* s s)) pairs)) 10000.0)]
      (is (close? (a/named-hhi (commodity pairs)) expect)
          (str "HHI = Σ share²/10000 for " pairs)))))

(deftest hhi-excludes-the-other-residual
  ;; the fragmented :other share is excluded — HHI is a lower bound on true concentration
  (is (close? (a/named-hhi (commodity [[:a 50] [:other 50]])) 0.25)
      "only named producers count; :other is dropped (50²/10000)")
  (is (close? (a/named-hhi (commodity [[:a 30] [:b 20] [:other 50]]))
              (/ (+ (* 30 30) (* 20 20)) 10000.0))
      ":other is excluded from the sum"))

(deftest hhi-rises-with-concentration-and-is-bounded
  ;; for the same number of producers, a skewed split is more concentrated than an even one
  (is (> (a/named-hhi (commodity [[:a 80] [:b 20]]))
         (a/named-hhi (commodity [[:a 50] [:b 50]])))
      "a skewed duopoly scores higher than an even one")
  (is (<= 0.0 (a/named-hhi (commodity [[:a 33] [:b 33] [:c 34]])) 1.0) "HHI ∈ [0,1]"))

#?(:clj
   (when (= *file* (System/getProperty "babashka.file"))
     (let [{:keys [fail error]} (run-tests 'busshi.methods.test-hhi)]
       (System/exit (if (zero? (+ fail error)) 0 1)))))
