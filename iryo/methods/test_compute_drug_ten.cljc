#!/usr/bin/env bb
;; iryo 医療 — validation of compute-drug-ten (薬剤料 point computation).
;; Run:  bb --classpath 20-actors 20-actors/iryo/methods/test_compute_drug_ten.cljc
(ns iryo.methods.test-compute-drug-ten
  "Validation of compute-drug-ten — the 薬剤料 (drug-cost) point computation. It sums a
  prescription's drug costs (薬価 × 数量), converts the per-day yen total to points via
  五捨五超四入 (yakka-to-ten), and multiplies by the number of days. yakka-to-ten's rounding rule is
  already pinned in test_rezept, but the composition around it — the multi-drug Σ(薬価×amount), the
  ×days, and the days default/floor — had NO test, so a regression that dropped the amount weight
  or the day multiplier would mis-bill silently."
  (:require [iryo.methods.rezept :as rez]
            [clojure.test :refer [deftest is run-tests]]))

;; a minimal 医薬品マスタ (code → {:yakka 円}); compute-drug-ten reads it via masters/drug
(def ^:private m {:iyaku {"D1" {:yakka 100.0} "D2" {:yakka 250.0} "Dlow" {:yakka 12.0}}})

(deftest sums-costs-by-amount-converts-to-points-and-scales-by-days
  ;; Σ(薬価×数量) = 100·2 + 250·1 = 450 円/日 → yakka-to-ten 45 点 → ×3 日 = 135
  (is (= 135 (rez/compute-drug-ten {:drugs [{:code "D1" :amount 2} {:code "D2" :amount 1}] :days 3} m)))
  ;; one drug, one day: 100 円 → 10 点
  (is (= 10 (rez/compute-drug-ten {:drugs [{:code "D1" :amount 1}] :days 1} m)))
  ;; the per-drug amount scales the cost: 100×3 = 300 円 → 30 点
  (is (= 30 (rez/compute-drug-ten {:drugs [{:code "D1" :amount 3}] :days 1} m))))

(deftest days-defaults-to-one-and-is-floored-at-one
  (is (= 10 (rez/compute-drug-ten {:drugs [{:code "D1" :amount 1}]} m))
      "missing :days is treated as 1 day")
  (is (= 10 (rez/compute-drug-ten {:drugs [{:code "D1" :amount 1}] :days 0} m))
      "0 days is floored to 1 (a dispensed drug bills at least one day)"))

(deftest low-cost-drug-floors-at-one-point-then-scales
  ;; ≤15 円 → 1 点 (the yakka-to-ten low-cost rule), then ×days: 12 円 → 1 点 × 2 日 = 2
  (is (= 2 (rez/compute-drug-ten {:drugs [{:code "Dlow" :amount 1}] :days 2} m))))

#?(:clj
   (when (= *file* (System/getProperty "babashka.file"))
     (let [{:keys [fail error]} (run-tests 'iryo.methods.test-compute-drug-ten)]
       (System/exit (if (zero? (+ fail error)) 0 1)))))
