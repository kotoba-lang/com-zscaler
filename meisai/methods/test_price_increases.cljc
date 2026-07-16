#!/usr/bin/env bb
;; meisai 明細 — tests for price-increases (stealth subscription price-hike detection).
;; Run:  bb --classpath 20-actors 20-actors/meisai/methods/test_price_increases.cljc
(ns meisai.methods.test-price-increases
  "Tests for price-increases — recurring charges whose amount has crept up across statements (a
  stronger kaiyaku review signal than recurring's amount-stable? flag). Read-only over the member's
  own local rows; merchant is a service, never a person; no credential/PAN (only merchant + amount +
  month)."
  (:require [meisai.methods.recurring :as r]
            [clojure.test :refer [deftest is run-tests]]))

(defn- rd [eid stmt merchant amt]
  [[":add" eid ":meisai.row/stmt" stmt] [":add" eid ":meisai.row/merchant" merchant]
   [":add" eid ":meisai.row/amount" amt] [":add" eid ":meisai.row/currency" ":jpy"]])

(def ^:private datoms
  (concat (rd "r1" "s1" "Netflix" 990) (rd "r2" "s2" "Netflix" 1490) (rd "r3" "s3" "Netflix" 1490)  ; 990 → 1490
          (rd "p1" "s1" "Spotify" 980) (rd "p2" "s2" "Spotify" 980)                                 ; stable
          (rd "g1" "s1" "Gym" 8000)    (rd "g2" "s2" "Gym" 7000)                                     ; DROPPED, not a hike
          [[":add" "s1" ":meisai.stmt/month" "2026-01"]
           [":add" "s2" ":meisai.stmt/month" "2026-02"]
           [":add" "s3" ":meisai.stmt/month" "2026-03"]]))

(deftest flags-a-recurring-charge-that-crept-up
  (let [out (r/price-increases datoms)
        nf (first (filter #(= "Netflix" (:merchant %)) out))]
    (is (= 990 (:first-amount nf)) "earliest Netflix amount")
    (is (= 1490 (:last-amount nf)) "latest Netflix amount")
    (is (= 500 (:increase nf)))
    (is (< (Math/abs (- 0.5050505 (:pct nf))) 1e-5) "≈50.5% increase")
    (is (= 3 (:months nf)))))

(deftest a-stable-charge-is-not-flagged
  (is (not (some #(= "Spotify" (:merchant %)) (r/price-increases datoms)))
      "Spotify held at 980 — no price increase"))

(deftest a-decrease-is-not-a-price-increase
  (is (not (some #(= "Gym" (:merchant %)) (r/price-increases datoms)))
      "Gym dropped 8000 → 7000 — surfacing increases only"))

(deftest min-pct-threshold-filters-small-drift
  ;; a 2% drift is below the default 5% threshold
  (let [tiny (concat (rd "t1" "s1" "Svc" 1000) (rd "t2" "s2" "Svc" 1020)
                     [[":add" "s1" ":meisai.stmt/month" "2026-01"] [":add" "s2" ":meisai.stmt/month" "2026-02"]])]
    (is (empty? (r/price-increases tiny)) "2% < 5% default → not flagged")
    (is (seq (r/price-increases tiny {:min-pct 0.01})) "but a 1% threshold catches it")))

#?(:clj
   (when (= *file* (System/getProperty "babashka.file"))
     (let [{:keys [fail error]} (run-tests 'meisai.methods.test-price-increases)]
       (System/exit (if (zero? (+ fail error)) 0 1)))))
