#!/usr/bin/env bb
;; tsuchifumi 土踏み — risk register + leverage-point tests.
;; Run: bb --classpath 20-actors 20-actors/tsuchifumi/methods/test_risk.cljc
(ns tsuchifumi.methods.test-risk
  (:require [tsuchifumi.methods.tsuchifumi-edn :as te]
            [tsuchifumi.methods.risk :as risk]
            [clojure.test :refer [deftest is run-tests]]))

(def seed-path "20-actors/tsuchifumi/kotoba/seed.edn")
(defn- drivers [] (:drivers (te/load-seed seed-path)))
(defn- by-id [id] (first (filter #(= id (:id %)) (drivers))))

(deftest register-sorted-by-risk-score
  (let [reg (get (risk/assess (drivers)) "register")]
    (is (apply >= (map #(get % "risk_score") reg)) "register sorted by evidence-discounted risk-score")))

;; ── G2 — evidence discount: a contested driver cannot dominate on assertion ──
(deftest contested-driver-discounted
  (let [d (by-id "drv-product-pseudoscience")]
    (is (< (risk/risk-score d) (risk/raw-severity d))
        "a :contested driver's risk-score is discounted below its raw-severity (G2)"))
  (let [reg (get (risk/assess (drivers)) "register")
        last-row (last reg)]
    (is (= "drv-product-pseudoscience" (get last-row "id"))
        "the pseudoscience driver lands at the bottom of the evidence-discounted register")))

(deftest leverage-strength-monotonic
  ;; lower Meadows level → stronger leverage
  (is (> (risk/leverage-strength {:leverage 2}) (risk/leverage-strength {:leverage 6}))
      "Meadows L2 (paradigm/goal) has stronger leverage than L6"))

(deftest institutional-gap-is-top-leverage
  (let [lp (get (risk/assess (drivers)) "leverage_points")
        top2 (set (map #(get % "id") (take 2 lp)))]
    (is (contains? top2 "drv-no-grounding-standard")
        "the missing grounding/greenspace standard is among the top leverage points")))

(deftest severity-bands
  (is (= :critical (risk/severity-band {:likelihood 0.95 :impact 0.7})))
  (is (= :low (risk/severity-band {:likelihood 0.2 :impact 0.2}))))

(deftest datoms-flagged-and-clean
  (let [ds (risk/datoms (risk/assess (drivers)))
        attrs (set (map (fn [[_ _ a _]] a) ds))]
    (is (contains? attrs ":tsuchifumi.risk/leverage-priority"))
    (is (some (fn [[_ _ a v]] (and (= a ":tsuchifumi/derived") (= v true))) ds))
    (is (not (contains? attrs ":tsuchifumi.person/health")) "no person attribute (G1/G3)")))

(let [{:keys [fail error]} (run-tests 'tsuchifumi.methods.test-risk)]
  (when (pos? (+ fail error)) (System/exit 1)))
