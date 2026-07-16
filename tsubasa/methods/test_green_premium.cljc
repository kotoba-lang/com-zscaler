#!/usr/bin/env bb
;; tsubasa 翼 — tests for the green-premium cost↔emissions tradeoff.
;; Run:  bb --classpath 20-actors 20-actors/tsubasa/methods/test_green_premium.cljc
(ns tsubasa.methods.test-green-premium
  "Tests for green-premium — the explicit tradeoff between the cheapest and the greenest fare on a
  route: the extra true total cost to fly the lowest-CO₂ option and the CO₂ it saves. Emissions-honest
  (G4 — neither dimension hidden); transparent (G3 — no dark pattern)."
  (:require [tsubasa.methods.analyze :as a]
            [clojure.test :refer [deftest is run-tests]]))

(def ^:private fares
  ;; C is cheapest (10000) but dirtiest (300); B is greenest (150) at 12000; A in between
  [{:fare/carrier "A" :fare/fare-minor 10000 :fare/baggage-minor 2000 :fare/co2-kg 200.0}
   {:fare/carrier "B" :fare/fare-minor 11000 :fare/baggage-minor 1000 :fare/co2-kg 150.0}
   {:fare/carrier "C" :fare/fare-minor 9000  :fare/baggage-minor 1000 :fare/co2-kg 300.0}])

(deftest quantifies-the-cost-to-fly-green
  (let [g (a/green-premium fares)]
    (is (= 10000 (:cheapest-total-minor g)) "C: 9000 fare + 1000 bag = 10000 true total")
    (is (= 12000 (:greenest-total-minor g)) "B: 11000 + 1000 = 12000")
    (is (= 2000 (:premium-minor g)) "the green premium is 2000 minor (greenest − cheapest)")
    (is (= 150.0 (:co2-saved-kg g)) "and it saves 300 − 150 = 150 kg CO₂")
    (is (not (:green-is-cheapest? g)) "here the greenest costs more than the cheapest")))

(deftest a-win-win-when-the-greenest-is-also-cheapest
  (let [g (a/green-premium [{:fare/carrier "X" :fare/fare-minor 8000 :fare/baggage-minor 0 :fare/co2-kg 100.0}
                            {:fare/carrier "Y" :fare/fare-minor 12000 :fare/baggage-minor 0 :fare/co2-kg 250.0}])]
    (is (<= (:premium-minor g) 0) "the greenest is also the cheapest → non-positive premium")
    (is (:green-is-cheapest? g) "a win-win — the cheapest fare is already the cleanest")
    (is (= 0.0 (:co2-saved-kg g)) "no separate green choice to make: cheapest == greenest, so 0 extra saving")))

(deftest premium-uses-true-total-cost-not-headline-fare
  ;; a low headline fare with a high bag fee must not look cheaper (G4 honesty)
  (let [g (a/green-premium [{:fare/carrier "cheap-looking" :fare/fare-minor 5000 :fare/baggage-minor 9000 :fare/co2-kg 400.0}
                            {:fare/carrier "honest"        :fare/fare-minor 9000 :fare/baggage-minor 1000 :fare/co2-kg 120.0}])]
    (is (= 10000 (:cheapest-total-minor g)) "both total 10000+/14000 — the honest 10000 fare is cheapest by TRUE cost")
    (is (:green-is-cheapest? g) "the greener honest fare is also the cheapest once baggage is counted")))

(deftest single-fare-route-has-zero-premium
  (let [g (a/green-premium [{:fare/carrier "Solo" :fare/fare-minor 7000 :fare/baggage-minor 500 :fare/co2-kg 180.0}])]
    (is (= 0 (:premium-minor g)) "one fare is both cheapest and greenest")
    (is (= 0.0 (:co2-saved-kg g)))
    (is (:green-is-cheapest? g))))

#?(:clj
   (when (= *file* (System/getProperty "babashka.file"))
     (let [{:keys [fail error]} (run-tests 'tsubasa.methods.test-green-premium)]
       (System/exit (if (zero? (+ fail error)) 0 1)))))
