#!/usr/bin/env bb
;; 澪 mio — analyze/datoms/coverage tests (incl. constitutional invariants).
;; Run:  bb --classpath 20-actors 20-actors/mio/methods/test_analyze.cljc
(ns mio.methods.test-analyze
  (:require [mio.methods.mio-edn :as me]
            [mio.methods.analyze :as a]
            [clojure.string :as str]
            [clojure.test :refer [deftest is run-tests]]))

(def seed-path "20-actors/mio/kotoba/seed.edn")
(defn- cs [] (me/claims seed-path))
(defn- by-id [id] (first (filter #(= id (:id %)) (cs))))
(defn- row [id] (first (filter #(= id (get % "id")) (get (a/analyze (cs)) "claims"))))

;; ── the §9 verification verdicts ─────────────────────────────────────────────

(deftest verified-claim-with-all-five-facts
  (is (= :verified (get (row "wh-dc-district-01") "verdict"))
      "signed-meter + baseline + additionality + unique key + low leakage → verified")
  (is (= :reward (get (row "wh-dc-district-01") "route"))))

(deftest self-report-alone-cannot-verify
  ;; §9 defence: a self-report (weight 0.3) cannot clear the threshold → no reward.
  (is (= :insufficient-evidence (get (row "wh-selfreport-01") "verdict")))
  (is (= :review (get (row "wh-selfreport-01") "route"))))

(deftest leakage-above-max-rejected
  ;; waste heat sent to a region that needs cooling — offset elsewhere.
  (is (= :rejected-leakage (get (row "wh-aircon-zone-01") "verdict"))))

(deftest low-additionality-insufficient
  ;; would have happened anyway (additionality 0.25 < 0.3).
  (is (= :insufficient-evidence (get (row "fx-coldstore-precool-01") "verdict")))
  (is (= :insufficient-evidence (get (row "ra-noadd-01") "verdict"))))

(deftest double-count-rejected
  ;; the second pipeline claiming the SAME cold-region batch is rejected.
  (is (= :verified (get (row "cr-batch-coldregion-01") "verdict")) "first claim verifies")
  (is (= :rejected-double-count (get (row "cr-double-of-l-01") "verdict")) "the duplicate is rejected"))

(deftest missing-baseline-insufficient
  (is (= :insufficient-evidence (get (row "in-no-baseline-01") "verdict"))))

(deftest intention-claim-content-free-can-verify
  ;; the consented cohort aggregate offer verifies WITHOUT any per-person text.
  (is (= :verified (get (row "in-cohort-presence-01") "verdict"))))

(deftest verification-confidence-bounded
  (doseq [c (cs)]
    (let [v (a/verification-confidence c)]
      (is (and (>= v 0.0) (<= v 1.0)) (str (:id c) " confidence in 0..1")))))

;; ── G1 BACKBONE: reward from ORDER, never from CONSUMPTION ────────────────────

(deftest g1-useful-flow-zero-unless-verified
  (doseq [r (get (a/analyze (cs)) "claims")]
    (when (not= :verified (get r "verdict"))
      (is (= 0.0 (get r "useful_flow_score"))
          (str (get r "id") " earns nothing unless verified"))))
  ;; verified claims carry a positive useful-flow score = order Δ × confidence
  (is (pos? (get (row "fx-battery-peak-01") "useful_flow_score"))))

(deftest g1-flowrate-equals-sum-of-verified
  (let [a (a/analyze (cs))
        verified (filter #(= :verified (get % "verdict")) (get a "claims"))
        manual (reduce + 0.0 (map #(get % "useful_flow_score") verified))]
    (is (= 9 (count verified)) "nine claims verify in the seed")
    (is (< (Math/abs (- manual (get-in a ["totals" "verified_flowrate_score"]))) 1e-9)
        "Flowrate is exactly the sum of verified useful-flow scores")))

;; ── datom emission + G1/G3 unrepresentability ────────────────────────────────

(deftest datoms-flagged-derived-and-sourced
  (let [edn (a/render-datoms (a/analyze (cs)))]
    (is (str/includes? edn ":mio/derived"))
    (is (str/includes? edn ":mio/sourcing"))
    (is (str/includes? edn ":mio.obs/useful-flow-score"))
    (is (str/includes? edn ":mio.obs/verdict"))
    (is (str/includes? edn ":mio.ledger/verified-flowrate-score"))))

(deftest authoritative-provenance-folded
  (let [edn (a/render-datoms (a/analyze (cs)))]
    (is (= :authoritative (get (row "wh-dc-district-01") "sourcing")))
    (is (str/includes? (get (row "wh-dc-district-01") "source") "BTU meter"))
    (is (= :representative (get (row "wh-food-greenhouse-01") "sourcing")))
    (is (str/includes? edn ":authoritative"))
    (is (str/includes? edn ":mio/source"))))

(deftest g1-g3-no-trade-no-signal-no-consumed-reward
  (let [edn (a/render-datoms (a/analyze (cs)))]
    (is (not (str/includes? edn ":mio/trade")))
    (is (not (str/includes? edn ":mio/signal")))
    (is (not (str/includes? edn "consumed-reward")))      ; G1: never reward from consumption
    (is (not (str/includes? edn "price-forecast-point")))
    (is (not (str/includes? edn ":mio.person")))))         ; G5: no per-person intent

(deftest report-is-flow-ledger-not-market-signal
  (let [md (a/render-report (a/analyze (cs)) (a/coverage (cs)))]
    (is (str/includes? md "PROOF OF USEFUL FLOW"))
    (is (str/includes? md "NEVER a "))                     ; the disclaimer line
    (is (str/includes? md "Hashrate → Flowrate"))
    (is (str/includes? md "ORDERED flow"))))

;; ── coverage ─────────────────────────────────────────────────────────────────

(deftest coverage-gap-nonneg
  (let [cov (a/coverage (cs))]
    (is (= 6 (count (get cov "by_class"))))
    (is (every? #(>= (get % "gap") 0) (get cov "by_class")))
    (is (>= (get cov "total_have") 15))
    (is (>= (get cov "total_gap") 0))))

#?(:clj
   (when (= *file* (System/getProperty "babashka.file"))
     (let [{:keys [fail error]} (run-tests 'mio.methods.test-analyze)]
       (System/exit (if (zero? (+ fail error)) 0 1)))))
