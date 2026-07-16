#!/usr/bin/env bb
;; uzu 渦 — energy-ledger tests (conserved/depleting; the energy half).
;; Run: bb --classpath 20-actors 20-actors/uzu/methods/test_ledger.cljc
(ns uzu.methods.test-ledger
  (:require [uzu.methods.ledger :as l]
            [clojure.test :refer [deftest is run-tests]]))

(def costs l/default-costs)

(deftest intake-depends-on-true-regime
  ;; you can only eat what is actually there, however you read it
  (is (> (l/intake :abundant :forage) (l/intake :scarce :forage)))
  (is (= 0.0 (l/intake :abundant :flee)) "fleeing never feeds"))

(deftest hazard-is-mitigated-by-the-right-action
  (is (= 8.0 (l/hazard costs :hostile :forage)) "foraging a hostile regime takes the hazard")
  (is (= 0.0 (l/hazard costs :hostile :flee)) "fleeing mitigates it")
  (is (= 0.0 (l/hazard costs :abundant :forage)) "no hazard in a benign world"))

(deftest metabolize-balances-intake-against-cost
  ;; forage abundant: +7 intake − (basal1 + inf0.5 + forage2) = +3.5
  (let [m (l/metabolize 10.0 costs :forage :abundant)]
    (is (< (Math/abs (- 13.5 (:e' m))) 1e-9))
    (is (true? (:alive? m)))
    (is (= 7.0 (:gained m)))))

(deftest hostile-forage-is-near-fatal
  ;; +2 intake − (1 + 0.5 + 2 + 8 hazard) = −9.5 ⇒ a misread of danger drains fast
  (let [m (l/metabolize 10.0 costs :forage :hostile)]
    (is (< (Math/abs (- 0.5 (:e' m))) 1e-9))
    (is (= 8.0 (:hazard m)))))

(deftest death-when-balance-hits-zero
  (is (false? (:alive? (l/metabolize 1.0 costs :forage :hostile))))
  (is (false? (l/alive? 0.0)))
  (is (true? (l/alive? 0.01))))

(deftest affordability-shrinks-as-energy-drops
  (is (= #{:rest :forage :flee :explore} (set (l/affordable 100.0 costs))) "rich: all actions")
  (is (= [:rest] (l/affordable 1.6 costs)) "starving: only rest (basal+inf+rest=1.7 > 1.6 ⇒ fallback rest)")
  (is (contains? (set (l/affordable 4.0 costs)) :forage) "enough for forage at 4.0"))

(let [{:keys [fail error]} (run-tests 'uzu.methods.test-ledger)]
  (when (pos? (+ fail error)) (System/exit 1)))
