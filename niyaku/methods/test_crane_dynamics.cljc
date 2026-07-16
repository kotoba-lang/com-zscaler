(ns niyaku.methods.test-crane-dynamics
  "Tests for niyaku.methods.crane-dynamics — gantry anti-sway physics core.
  1:1 Clojure port of methods/test_crane_dynamics.py (pytest → clojure.test)."
  (:require [clojure.test :refer [deftest is]]
            [niyaku.methods.crane-dynamics :as cd]))

(defn- approx?
  ([a b] (approx? a b 1e-9))
  ([a b tol] (<= (Math/abs (- (double a) (double b))) (* tol (max 1.0 (Math/abs (double b)))))))

(deftest test-natural-frequency-and-period
  (let [c (cd/make-gantry-crane :cable-length 30.0 :gravity 9.81)
        w (cd/natural-frequency c)]
    (is (approx? w (Math/sqrt (/ 9.81 30.0))))
    (is (approx? (cd/sway-period c) (/ (* 2 Math/PI) w)))
    ;; longer cable ⇒ slower sway
    (is (< (cd/natural-frequency (cd/make-gantry-crane :cable-length 60.0)) w))))

(deftest test-hanging-load-is-stable-equilibrium
  ;; No input, small initial sway → it decays (gravity restores).
  (let [c (cd/make-gantry-crane :cable-length 20.0 :sway-damping 0.05)
        peak0 (Math/abs 0.15)
        state (loop [i 0 state [0.0 0.0 0.15 0.0]]
                (if (< i 4000)
                  (recur (inc i) (cd/step c state 0.0 (/ 1.0 100.0)))
                  state))]
    (is (< (Math/abs (double (nth state 2))) peak0))
    (is (< (Math/abs (double (nth state 2))) 0.05))
    (is (every? #(Double/isFinite (double %)) state))))

(deftest test-trolley-velocity-envelope-enforced
  (let [c (cd/make-gantry-crane :velocity-max 2.0 :accel-max 5.0)
        state (loop [i 0 state [0.0 0.0 0.0 0.0]]
                (if (< i 2000)
                  (recur (inc i) (cd/step c state 5.0 (/ 1.0 100.0)))
                  state))]
    (is (<= (Math/abs (double (nth state 1))) (+ 2.0 1e-6)))))

(deftest test-accel-command-is-saturated
  (let [c (cd/make-gantry-crane :accel-max 0.6)
        d (cd/derivatives c [0 0 0 0] 100.0)]
    (is (approx? (nth d 1) 0.6))
    (let [d (cd/derivatives c [0 0 0 0] -100.0)]
      (is (approx? (nth d 1) -0.6)))))

(deftest test-simulate-traverse-reaches-and-damps-sway
  (let [c (cd/make-gantry-crane :cable-length 25.0 :accel-max 0.7 :velocity-max 4.0)
        res (cd/simulate-traverse c 30.0 :max-time-s 300.0)]
    (is (:reached res))
    (is (<= (Math/abs (- (double (:final-x res)) 30.0)) 0.10))
    (is (<= (double (:residual-sway-m res)) 0.05))
    (is (> (double (:settle-time-s res)) 0.0))))

(deftest test-anti-sway-beats-no-control-on-residual
  (let [c (cd/make-gantry-crane :cable-length 25.0)
        with-ctrl (cd/simulate-traverse c 25.0 :controller (cd/make-anti-sway-controller) :max-time-s 300.0)
        naive (cd/simulate-traverse c 25.0
                                    :controller (cd/make-anti-sway-controller :k-theta 0.0 :k-thetad 0.0)
                                    :max-time-s 300.0)]
    (is (< (double (:peak-sway-m with-ctrl)) (double (:peak-sway-m naive))))))

(deftest test-traverse-target-beyond-rail-raises
  (let [c (cd/make-gantry-crane :rail-length 60.0)]
    (is (thrown? #?(:clj Exception :cljs js/Error) (cd/simulate-traverse c 80.0)))))

(deftest test-zv-shaper-amplitudes-sum-to-one
  (let [c (cd/make-gantry-crane :cable-length 30.0 :sway-damping 0.02)
        imp (cd/zv-shaper c)
        [t0 a0] (nth imp 0)
        [t1 a1] (nth imp 1)]
    (is (= 2 (count imp)))
    (is (= 0.0 t0))
    (is (approx? (+ a0 a1) 1.0))
    (is (approx? t1 (/ (cd/sway-period c) 2.0) 0.05))))

(deftest test-traverse-records-trajectory-when-requested
  (let [c (cd/make-gantry-crane :cable-length 25.0)
        res (cd/simulate-traverse c 20.0 :max-time-s 300.0 :record true)]
    (is (:reached res))
    (is (= (count (:trajectory res)) (:steps res)))
    (is (every? #(= 4 (count %)) (:trajectory res)))))

(deftest test-traverse-not-settled-within-short-window
  ;; Too little time to settle → reached False and settle_time clamps to horizon.
  (let [c (cd/make-gantry-crane :cable-length 40.0)
        res (cd/simulate-traverse c 55.0 :max-time-s 2.0 :dt (/ 1.0 50.0))]
    (is (false? (:reached res)))
    (is (approx? (:settle-time-s res) 2.0))))

(deftest test-cycle-time-and-productivity
  (let [c (cd/make-gantry-crane :cable-length 25.0)
        t (cd/lift-cycle-time c 30.0 20.0 18.0)]
    (is (> t 0.0))
    (let [mph (cd/moves-per-hour t)]
      (is (and (< 5.0 mph) (< mph 120.0))))
    (is (thrown? #?(:clj Exception :cljs js/Error) (cd/moves-per-hour 0.0)))))
