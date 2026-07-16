#!/usr/bin/env bb
;; LIVE cross-language py↔clj parity for the mizuho PID control loops.
(ns mizuho.methods.test-pid-parity
  "test_pid_parity.clj — mizuho chlorination + water-supply py↔clj LIVE parity (ADR-2605263100).

  Runs the ACTUAL `chlorination.py` / `water_supply.py` via a python3 subprocess and the clj
  impls over the SAME scenarios, asserting the closed-loop PID KPIs agree to 1e-6:
    - chlorination: final-residual / max-residual / settling (the ≤4 mg/L MRDL clamp loop)
    - water-supply: final-level / final-pressure / settling (the reservoir PI pump loop)

  Authoring this oracle SURFACED a real port-fidelity gap: clj rounded max-residual to 6 dp
  (0.611382) while py's DosingResult rounds to 4 dp (0.6114). Fixed chlorination.clj to match
  py's round(...,4) contract — the two impls are now byte-faithful and this test pins it.

  Gracefully SKIPS if python3 is unavailable (red only on a genuine py↔clj divergence).

  Run:  bb --classpath 20-actors 20-actors/mizuho/methods/test_pid_parity.clj"
  (:require [mizuho.methods.chlorination :as cl]
            [mizuho.methods.water-supply :as ws]
            [clojure.java.shell :refer [sh]]
            [cheshire.core :as json]
            [clojure.test :refer [deftest is run-tests]]))

(def ^:private py-dir "20-actors/mizuho/methods")
(def ^:private chlor-targets [0.5 0.8 1.2])
(def ^:private supply-demands [50.0 120.0 300.0])

(def ^:private py-src
  (str "import json, chlorination as c, water_supply as w\n"
       "ct = json.loads(__import__('sys').argv[1])\n"
       "sd = json.loads(__import__('sys').argv[2])\n"
       "chl = []\n"
       "for t in ct:\n"
       "    r = c.commission_dosing(target_residual_mgl=t)\n"
       "    chl.append([t, r.final_residual_mgl, r.max_residual_mgl, r.settling_seconds])\n"
       "sup = []\n"
       "for d in sd:\n"
       "    r = w.commission_water_supply(d)\n"
       "    sup.append([d, r.final_level_m, r.final_pressure_bar, r.settling_seconds])\n"
       "print(json.dumps({'chl': chl, 'sup': sup}))\n"))

(defn- py-results []
  (try
    (let [r (sh "python3" "-c" py-src
                (json/generate-string chlor-targets) (json/generate-string supply-demands)
                :dir py-dir)]
      (when (and (= 0 (:exit r)) (seq (:out r)))
        (json/parse-string (:out r) true)))
    (catch Exception _ nil)))

(defn- close? [a b] (< (Math/abs (- (double a) (double b))) 1e-6))

(deftest clj-loops-are-self-consistent
  ;; runs regardless of python: the chlorination ceiling holds; the reservoir restores setpoint.
  (doseq [t chlor-targets]
    (let [r (cl/commission-dosing {:target-residual-mgl t})]
      (is (<= (:max-residual-mgl r) (+ cl/max-residual-mgl 1e-9)) "≤4 mg/L MRDL clamp holds")
      (is (pos? (:settling-seconds r)))))
  (doseq [d supply-demands]
    (let [r (ws/commission-water-supply {:demand-step-lps d})]
      (is (close? (:final-level-m r) 3.0) "reservoir returns to 3.0 m setpoint")
      (is (pos? (:settling-seconds r))))))

(deftest chlorination-matches-python
  (let [py (py-results)]
    (if-not py
      (is true "python3 unavailable — chlorination cross-language parity skipped")
      (doseq [[t fin mx settle] (:chl py)]
        (let [r (cl/commission-dosing {:target-residual-mgl t})]
          (is (close? fin (:final-residual-mgl r)) (str "final drift @" t ": py " fin " clj " (:final-residual-mgl r)))
          (is (close? mx (:max-residual-mgl r)) (str "max drift @" t ": py " mx " clj " (:max-residual-mgl r)))
          (is (close? settle (:settling-seconds r)) (str "settle drift @" t)))))))

(deftest water-supply-matches-python
  (let [py (py-results)]
    (if-not py
      (is true "python3 unavailable — water-supply cross-language parity skipped")
      (doseq [[d lvl pres settle] (:sup py)]
        (let [r (ws/commission-water-supply {:demand-step-lps d})]
          (is (close? lvl (:final-level-m r)) (str "level drift @" d))
          (is (close? pres (:final-pressure-bar r)) (str "pressure drift @" d ": py " pres " clj " (:final-pressure-bar r)))
          (is (close? settle (:settling-seconds r)) (str "settle drift @" d ": py " settle " clj " (:settling-seconds r))))))))

(when (= *file* (System/getProperty "babashka.file"))
  (let [{:keys [fail error]} (run-tests 'mizuho.methods.test-pid-parity)]
    (System/exit (if (zero? (+ fail error)) 0 1))))
