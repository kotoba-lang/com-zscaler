(ns mizuho.methods.test-chlorination
  "Tests for mizuho residual-dosing (chlorination) operational loop
  (methods/chlorination.cljc). 1:1 port of methods/test_chlorination.py.

    bb --classpath 20-actors -e \"(require 'mizuho.methods.test-chlorination 'clojure.test) \\
       (clojure.test/run-tests 'mizuho.methods.test-chlorination)\""
  (:require [clojure.test :refer [deftest is]]
            [mizuho.methods.substrate :as sub]
            [mizuho.methods.chlorination :as cl]))

(defn- approx? [actual expected tol]
  (<= (Math/abs (- (double actual) (double expected))) tol))

(deftest test-chlorine-holds-target-residual-without-consent
  ;; Community-wide disinfection: no per-member consent needed (G6).
  (let [res (cl/commission-dosing :agent "disinfect" :target-residual-mgl 0.5)]
    (is (:residual-held res))
    (is (approx? (:final-residual-mgl res) 0.5 1e-2))
    (is (:ceiling-respected res))
    (is (> (:settling-seconds res) 0))))

(deftest test-residual-never-exceeds-regulatory-ceiling
  ;; Even commanding a target right at the ceiling, the modeled residual must
  ;; never cross MAX-RESIDUAL-MGL.
  (let [res (cl/commission-dosing :agent "disinfect" :target-residual-mgl 3.9)]
    (is (<= (:max-residual-mgl res) (+ cl/max-residual-mgl 1e-9)))
    (is (:ceiling-respected res))))

(deftest test-clamp-holds-even-with-aggressive-gains
  ;; The clamp is structural — no choice of gains can drive the residual over
  ;; the regulatory ceiling.
  (let [plant (cl/residual-chlorine-plant :residual-mgl 0.0 :k-decay 0.0)
        pid (sub/pid :kp 1000.0 :ki 1000.0 :out-min 0.0 :out-max 1e6)
        doser (cl/clamped-doser plant pid 0.1)
        res (sub/simulate plant doser 999.0 3000 0.1 :tol 1e-3)
        worst (reduce (fn [m [_ pv _]] (max m pv)) 0.0 (:trajectory res))]
    (is (<= worst (+ cl/max-residual-mgl 1e-9)))))

(deftest test-target-above-ceiling-refused
  (is (thrown? clojure.lang.ExceptionInfo
               (cl/commission-dosing :agent "disinfect"
                                     :target-residual-mgl (+ cl/max-residual-mgl 0.1)))))

(deftest test-fluoride-without-consent-refused-g6
  ;; No mandatory fluoridation — anti-paternalism (G6).
  (is (thrown? clojure.lang.ExceptionInfo
               (cl/commission-dosing :agent "fluoridate" :target-residual-mgl 0.7))))

(deftest test-fluoride-with-consent-passes
  (let [res (cl/commission-dosing :agent "fluoridate" :target-residual-mgl 0.7
                                  :per-member-consent true)]
    (is (:residual-held res))
    (is (approx? (:final-residual-mgl res) 0.7 1e-2))
    (is (:ceiling-respected res))))

(deftest test-unknown-agent-refused
  (is (thrown? clojure.lang.ExceptionInfo
               (cl/commission-dosing :agent "bleach-the-river" :target-residual-mgl 0.5))))

(deftest test-datoms-are-dry-run-no-server-key
  (let [res (cl/commission-dosing :agent "disinfect" :target-residual-mgl 0.5)
        d (cl/to-datoms res "spring-001")]
    (is (= (get d ":water.dosing/dry-run") true))
    (is (= (get d ":water.dosing/server-held-key") false))
    (is (= (get d ":water.dosing/ceiling-respected") true))
    (is (= (get d ":water.dosing/ceiling-mgl") cl/max-residual-mgl))))
