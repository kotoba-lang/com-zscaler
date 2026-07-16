#!/usr/bin/env bb
;; noroshi 烽 — validation of the substrate PID controller (make-pid / pid-step! / pid-reset!).
;; Run:  bb --classpath 20-actors 20-actors/noroshi/methods/test_pid_controller.cljc
(ns noroshi.methods.test-pid-controller
  "Validation of the PID controller in noroshi's substrate — make-pid / pid-step! / pid-reset! —
  which the active-alignment loop drives but which had NO test. pid-step! is a textbook PID with
  output clamping AND anti-windup; this pins each term and, crucially, the anti-windup safety
  property (the integral must NOT keep accumulating while the output is saturated — otherwise the
  controller over-shoots badly when the error finally clears):

    raw = kp·error + ki·∫error·dt + kd·d(error)/dt,  out = clamp(raw, out_min, out_max)
    anti-windup: ∫ is frozen on the step whose raw saturates."
  (:require [noroshi.methods._substrate :as sub]
            [clojure.test :refer [deftest is run-tests]]))

(defn- close? [a b] (< (Math/abs (- (double a) (double b))) 1e-9))

(deftest proportional-term-is-kp-times-error
  (let [p (sub/make-pid :kp 2.0)]
    (is (close? (sub/pid-step! p 3.0 0.1) 6.0) "kp·error = 2·3")
    (is (close? (sub/pid-step! p -1.5 0.1) -3.0) "tracks sign and magnitude")))

(deftest integral-term-accumulates-error-over-time
  (let [p (sub/make-pid :ki 1.0)]
    ;; ∫ grows by error·dt each step: 2·0.5 = 1.0, then +1.0 = 2.0, then +1.0 = 3.0
    (is (close? (sub/pid-step! p 2.0 0.5) 1.0))
    (is (close? (sub/pid-step! p 2.0 0.5) 2.0))
    (is (close? (sub/pid-step! p 2.0 0.5) 3.0) "integral keeps accumulating")))

(deftest derivative-term-responds-to-error-rate
  (let [p (sub/make-pid :kd 2.0)]
    (is (close? (sub/pid-step! p 5.0 1.0) 0.0) "no prev-error on the first step → derivative 0")
    (is (close? (sub/pid-step! p 8.0 1.0) 6.0) "kd·Δerror/dt = 2·(8−5)/1")))

(deftest output-is-clamped-to-the-configured-band
  (let [p (sub/make-pid :kp 1.0 :out_min -5.0 :out_max 5.0)]
    (is (close? (sub/pid-step! p 100.0 0.1) 5.0) "raw above out_max is clamped")
    (is (:saturated @p) "and the controller flags saturation")
    (let [q (sub/make-pid :kp 1.0 :out_min -5.0 :out_max 5.0)]
      (is (close? (sub/pid-step! q -100.0 0.1) -5.0) "raw below out_min is clamped"))))

(deftest anti-windup-freezes-the-integral-while-saturated
  ;; THE safety property: with a huge sustained error the integral would wind up to 300 without the
  ;; guard; pid-step! must keep it frozen at 0 (the value before the first saturating step)
  (let [p (sub/make-pid :ki 1.0 :out_max 5.0)]
    (dotimes [_ 3] (sub/pid-step! p 100.0 1.0))
    (is (close? (:integral @p) 0.0) "integral is NOT wound up while the output is saturated")
    (is (:saturated @p) "the controller stays saturated")))

(deftest reset-clears-integral-and-history
  (let [p (sub/make-pid :ki 1.0 :kd 1.0)]
    (sub/pid-step! p 5.0 1.0)
    (sub/pid-reset! p)
    (is (close? (:integral @p) 0.0) "reset zeroes the integral")
    (is (nil? (:prev-error @p)) "reset clears the derivative history (next step has no derivative)")))

#?(:clj
   (when (= *file* (System/getProperty "babashka.file"))
     (let [{:keys [fail error]} (run-tests 'noroshi.methods.test-pid-controller)]
       (System/exit (if (zero? (+ fail error)) 0 1)))))
