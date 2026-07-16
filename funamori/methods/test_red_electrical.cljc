#!/usr/bin/env bb
;; funamori 舫 — validation of the RED (reverse-electrodialysis) electrical model.
;; Run:  bb --classpath 20-actors 20-actors/funamori/methods/test_red_electrical.cljc
(ns funamori.methods.test-red-electrical
  "Validation of the RED electrical model in salinity_gradient.cljc — salinity-difference-g-l,
  red-internal-resistance, and red-max-power. test_salinity_gradient pins the INTENSIVE power
  DENSITY (E²/8R, stack-size-independent), but the absolute stack power, the internal resistance,
  and the salinity difference had NO direct test. This pins their closed forms and the
  maximum-power-transfer theorem red-max-power implements:
    Δsalinity      = draw − feed
    R_int          = N · area-resistance / pair-area
    P_max          = EMF_stack² / (4 · R_int)   (matched load R_L = R_int — no load delivers more)
  and the extensive/intensive split (absolute power scales with the stack; density does not)."
  (:require [funamori.methods.salinity-gradient :as sg]
            [clojure.test :refer [deftest is run-tests]]))

(defn- close? [a b] (< (Math/abs (- (double a) (double b))) 1e-9))

(deftest salinity-difference-is-draw-minus-feed
  (doseq [[d f] [[35.0 0.5] [35.0 1.0] [40.0 0.0] [30.0 5.0]]]
    (is (close? (sg/salinity-difference-g-l (sg/make-source-pair :draw-g-l d :feed-g-l f)) (- d f))
        (str "Δsalinity = draw − feed for " d "/" f))))

(deftest red-internal-resistance-is-N-Ra-over-area
  ;; R_int = cell-pairs · area-resistance / pair-area
  (doseq [n [10 50 200] a [0.5 1.0 2.0]]
    (let [st (sg/make-red-stack :cell-pairs n :pair-area-m2 a)]
      (is (close? (sg/red-internal-resistance st) (/ (* n (:area-resistance st)) a))
          (str "R_int = N·Ra/A for N=" n " A=" a))))
  ;; doubling cell-pairs doubles R_int; doubling pair-area halves it
  (let [base (sg/red-internal-resistance (sg/make-red-stack :cell-pairs 50 :pair-area-m2 1.0))]
    (is (close? (sg/red-internal-resistance (sg/make-red-stack :cell-pairs 100 :pair-area-m2 1.0)) (* 2 base)))
    (is (close? (sg/red-internal-resistance (sg/make-red-stack :cell-pairs 50 :pair-area-m2 2.0)) (/ base 2)))))

(deftest red-max-power-is-matched-load-transfer
  ;; P_max = EMF²/(4·R_int) is the maximum-power-transfer optimum: no load resistance delivers more
  (let [pair (sg/make-source-pair :draw-g-l 35.0 :feed-g-l 0.5)]
    (doseq [n [20 100 200]]
      (let [st (sg/make-red-stack :cell-pairs n)
            emf (sg/red-stack-emf st pair)
            r (sg/red-internal-resistance st)
            pmax (sg/red-max-power st pair)]
        (is (close? pmax (/ (* emf emf) (* 4.0 r))) (str "P_max = EMF²/4R for N=" n))
        (doseq [k [0.2 0.5 0.9 1.0 1.1 2.0 5.0]]
          (let [rl (* k r), p (/ (* emf emf rl) (* (+ r rl) (+ r rl)))]
            (is (<= p (+ pmax 1e-9))
                (str "no load (×" k " R_int) exceeds the matched-load max power (N=" n ")"))))))))

(deftest red-max-power-is-extensive-in-stack-size
  ;; unlike the intensive power DENSITY (stack-size-independent per test_salinity_gradient), the
  ;; ABSOLUTE max power grows with the number of cell pairs
  (let [pair (sg/make-source-pair)]
    (is (< (sg/red-max-power (sg/make-red-stack :cell-pairs 50) pair)
           (sg/red-max-power (sg/make-red-stack :cell-pairs 200) pair))
        "more cell pairs → more absolute power")))

#?(:clj
   (when (= *file* (System/getProperty "babashka.file"))
     (let [{:keys [fail error]} (run-tests 'funamori.methods.test-red-electrical)]
       (System/exit (if (zero? (+ fail error)) 0 1)))))
