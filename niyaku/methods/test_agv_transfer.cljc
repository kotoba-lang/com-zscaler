(ns niyaku.methods.test-agv-transfer
  "Tests for niyaku.methods.agv-transfer — AGV horizontal-transport planning core.
  1:1 Clojure port of methods/test_agv_transfer.py (pytest → clojure.test)."
  (:require [clojure.test :refer [deftest is]]
            [niyaku.methods.agv-transfer :as agv]))

(defn- approx?
  ([a b] (approx? a b 1e-9))
  ([a b tol] (<= (Math/abs (- (double a) (double b))) (* tol (max 1.0 (Math/abs (double b)))))))

(deftest test-zero-and-negative-distance
  (let [a (agv/make-agv)]
    (is (= 0.0 (agv/travel-time 0.0 a)))
    (is (thrown? #?(:clj Exception :cljs js/Error) (agv/travel-time -1.0 a)))))

(deftest test-trapezoidal-long-leg-reaches-cruise
  (let [a (agv/make-agv :v-max 6.0 :a-max 0.8)
        d 200.0
        t (agv/travel-time d a)
        expected (+ (* 2 (/ (:v-max a) (:a-max a)))
                    (/ (- d (/ (* (:v-max a) (:v-max a)) (:a-max a))) (:v-max a)))]
    (is (approx? t expected))
    (is (< (/ d t) (:v-max a)))))

(deftest test-triangular-short-leg-below-cruise
  (let [a (agv/make-agv :v-max 6.0 :a-max 0.8)
        d 10.0
        t (agv/travel-time d a)
        vp (Math/sqrt (* (:a-max a) d))]
    (is (< vp (:v-max a)))
    (is (approx? t (/ (* 2 vp) (:a-max a))))))

(deftest test-travel-time-monotone-in-distance
  (let [a (agv/make-agv)
        ts (map #(agv/travel-time % a) [5 20 45 100 300])]
    (is (= ts (sort ts)))))

(deftest test-reservation-conflict-same-segment-overlap
  (let [r1 (agv/make-segment-reservation "S1" "AGV1" 0.0 10.0)
        r2 (agv/make-segment-reservation "S1" "AGV2" 5.0 15.0)]
    (is (agv/reservations-conflict r1 r2))))

(deftest test-reservation-touching-endpoints-no-conflict
  (let [r1 (agv/make-segment-reservation "S1" "AGV1" 0.0 10.0)
        r2 (agv/make-segment-reservation "S1" "AGV2" 10.0 20.0)]
    (is (not (agv/reservations-conflict r1 r2)))))

(deftest test-reservation-different-segment-or-same-agv
  (let [base (agv/make-segment-reservation "S1" "AGV1" 0.0 10.0)]
    (is (not (agv/reservations-conflict base (agv/make-segment-reservation "S2" "AGV2" 0.0 10.0))))
    (is (not (agv/reservations-conflict base (agv/make-segment-reservation "S1" "AGV1" 0.0 10.0))))))

(deftest test-find-conflicts-pairs
  (let [rs [(agv/make-segment-reservation "S1" "A" 0 10)
            (agv/make-segment-reservation "S1" "B" 5 12)
            (agv/make-segment-reservation "S2" "C" 0 10)]]
    (is (= [[0 1]] (agv/find-conflicts rs)))))

(deftest test-dispatch-balances-makespan
  (let [a (agv/make-agv)
        moves (map-indexed (fn [i d] (agv/make-move (str "m" i) d)) [100 100 100 100])
        res (agv/dispatch moves ["AGV1" "AGV2"] a)
        t-single (agv/travel-time 100 a)]
    (is (every? #(= 2 (count %)) (vals (:assignment res))))
    (is (approx? (agv/makespan res) (* 2 t-single)))))

(deftest test-dispatch-lpt-puts-long-jobs-first
  (let [a (agv/make-agv)
        moves [(agv/make-move "big" 300) (agv/make-move "s1" 20) (agv/make-move "s2" 20)]
        res (agv/dispatch moves ["AGV1" "AGV2"] a)
        sizes (sort (map count (vals (:assignment res))))]
    (is (= [1 2] sizes))
    (is (> (agv/makespan res) 0))))

(deftest test-dispatch-requires-agv
  (is (thrown? #?(:clj Exception :cljs js/Error)
               (agv/dispatch [(agv/make-move "m" 10)] [] (agv/make-agv)))))
