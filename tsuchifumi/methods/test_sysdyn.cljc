#!/usr/bin/env bb
;; tsuchifumi 土踏み — system-dynamics model tests.
;; Run: bb --classpath 20-actors 20-actors/tsuchifumi/methods/test_sysdyn.cljc
(ns tsuchifumi.methods.test-sysdyn
  (:require [tsuchifumi.methods.sysdyn :as sd]
            [clojure.test :refer [deftest is run-tests]]))

(deftest bands-ordered
  (let [r (sd/ensemble {:steps 30})]
    (doseq [b (get r "bands")]
      (is (<= (get b "p10") (get b "p50") (get b "p90"))
          "p10 ≤ p50 ≤ p90 at every step"))))

(deftest deterministic
  (is (= (sd/ensemble {:steps 10}) (sd/ensemble {:steps 10}))
      "same seed/params → byte-identical ensemble (no Math/random)"))

(deftest relief-bends-burden-down
  (let [scen (sd/run-scenarios)
        fin (fn [k] (-> (get-in scen [k "result" "bands"]) last (get "p50")))]
    (is (< (fin :relief) (fin :baseline) (fin :neglect))
        "institutionalizing access lowers the horizon burden: relief < baseline < neglect")))

(deftest relief-dividend-positive
  (let [s (sd/summary (sd/run-scenarios))]
    (is (pos? (get s "relief_dividend_p50"))
        "the relief dividend (neglect−relief, p50) is positive")
    (is (= "Distribution-only what-if under DISCLOSED parameters (G6); NOT a forecast."
           (get s "note")))))

(deftest band-length-matches-steps
  (let [r (sd/ensemble {:steps 25})]
    (is (= 26 (count (get r "bands"))) "steps+1 band rows (incl. initial)")))

;; ── G6 — a single point forecast is structurally unrepresentable ─────────────
(deftest point-forecast-refused
  (is (thrown? clojure.lang.ExceptionInfo (sd/point-forecast {:steps 30}))
      "the model is distribution-only; a point forecast raises (G6)"))

(deftest step-clamps-state
  (let [s (sd/step {:E 9 :A 9 :I 9 :B 99} sd/default-params 1.0)]
    (is (<= 0.0 (:A s) 1.0))
    (is (<= 0.0 (:I s) 1.0))
    (is (<= 0.0 (:E s) (:E_cap sd/default-params)))
    (is (<= 0.0 (:B s) 5.0))))

(let [{:keys [fail error]} (run-tests 'tsuchifumi.methods.test-sysdyn)]
  (when (pos? (+ fail error)) (System/exit 1)))
