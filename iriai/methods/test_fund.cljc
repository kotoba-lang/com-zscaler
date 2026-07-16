#!/usr/bin/env bb
;; iriai 入会 — funding (資金) model tests (incl. the cash≡0 / give-only / steward invariants).
;; Run:  bb --classpath 20-actors 20-actors/iriai/methods/test_fund.cljc
(ns iriai.methods.test-fund
  (:require [iriai.methods.iriai-edn :as ie]
            [iriai.methods.fund :as fund]
            [iriai.methods.gates :as g]
            [clojure.string :as str]
            [clojure.test :refer [deftest is run-tests]]))

(def seed-path "20-actors/iriai/kotoba/seed.edn")
(defn- cells [] (ie/cells seed-path))
(defn- cell [region lifeline]
  (first (filter #(and (= region (:region %)) (= lifeline (:lifeline %))) (cells))))

;; ── only provision/reinforce/redundancy cells get a proposal ───────────────────
(deftest fundable-cells-only
  (is (some? (fund/proposal (cell "kibou" :electric))) "provision → proposal")
  (is (some? (fund/proposal (cell "saigai" :water)))   "reinforce → proposal")
  (is (some? (fund/proposal (cell "shima" :electric))) "redundancy → proposal")
  (is (nil? (fund/proposal (cell "midori" :electric))) "maintain → no proposal")
  (is (nil? (fund/proposal (cell "yama" :electric)))   "await-consent → no proposal")
  (is (nil? (fund/proposal (cell "machi" :gas)))       "monitor → no proposal"))

(deftest plan-totals
  (let [pl (fund/plan (cells))]
    ;; kibou 5 (4 utilities + road provision) + shima 2 (redundancy) + saigai 4 (reinforce) = 11
    (is (= 11 (get pl "count")))
    (is (pos? (get pl "imputed_annual_usd_total")))
    (is (= 0 (get pl "cash_to_consumer_total")))))

;; ── G2: commons, never a market — cash≡0, give-only instruments ────────────────
(deftest g2-cash-zero-to-consumer
  (doseq [p (get (fund/plan (cells)) "proposals")]
    (is (= 0 (get p "cash_to_consumer")) "§1.16 in-kind: the consumer is never billed")
    (is (= "§1.16-social-security-in-kind" (get p "delivery")))))

(deftest g2-give-only-instruments
  (doseq [p (get (fund/plan (cells)) "proposals")]
    (is (g/fundable-instruments (get p "instrument"))
        "instruments are give-only {:grant :milestone-escrow :in-kind}")
    ;; the gate assertion accepts it (and would throw on equity/debt)
    (is (= (get p "instrument") (g/check-instrument (get p "instrument"))))))

(deftest g2-no-market-attrs-in-datoms
  (let [edn (fund/render-datoms (fund/plan (cells)))]
    (is (not (str/includes? edn ":iriai.fund/tariff")))
    (is (not (str/includes? edn ":iriai.fund/price")))
    (is (not (str/includes? edn ":iriai.fund/subscription")))
    (is (not (str/includes? edn ":iriai.fund/equity")))
    (is (not (str/includes? edn ":iriai.fund/disconnect-for-nonpayment")))
    (is (str/includes? edn ":iriai.fund/cash-to-consumer"))
    (is (str/includes? edn ":iriai.fund/delivery"))))

;; ── G3: steward, not sovereign — advisory + binds-fund false ───────────────────
(deftest g3-advisory-binds-fund-false
  (doseq [p (get (fund/plan (cells)) "proposals")]
    (is (true? (get p "advisory")))
    (is (false? (get p "binds_fund")) "the Public Fund (1 SBT = 1 vote) decides, not iriai")
    (is (= "1-sbt-1-vote" (get p "decided_by")))
    (is (true? (g/check-advisory p)))))

;; ── G4: non-profit rails ───────────────────────────────────────────────────────
(deftest g4-non-profit-funding-source
  (doseq [p (get (fund/plan (cells)) "proposals")]
    (is (= "donation->tithe-10%->public-fund" (get p "funding_source")))
    (is (true? (get p "displacement_dividend_coupled")))))

;; ── instrument routing matches verdict ─────────────────────────────────────────
(deftest instrument-routing
  (is (= :milestone-escrow (get (fund/proposal (cell "kibou" :electric)) "instrument")) "provision → staged build")
  (is (= :grant            (get (fund/proposal (cell "saigai" :water))   "instrument")) "disaster → fast grant")
  (is (= :in-kind          (get (fund/proposal (cell "shima" :electric)) "instrument")) "redundancy → donated equipment"))

;; ── imputed value is the §1.16 reach value (provision reaches the gap pop) ──────
(deftest imputed-value-reaches-the-gap
  (let [p (fund/proposal (cell "kibou" :electric))]
    ;; provision reaches served (200) + gap (800) = 1000 people × $450/yr electric ref
    (is (= 1000 (get p "reach_pop")))
    (is (= 450000.0 (get p "imputed_annual_usd")))))

#?(:clj
   (when (= *file* (System/getProperty "babashka.file"))
     (let [{:keys [fail error]} (run-tests 'iriai.methods.test-fund)]
       (System/exit (if (zero? (+ fail error)) 0 1)))))
