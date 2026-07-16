#!/usr/bin/env bb
;; busshi 物資 — analyze/datoms/coverage tests (incl. constitutional invariants).
;; Run:  bb --classpath 20-actors 20-actors/busshi/methods/test_analyze.cljc
(ns busshi.methods.test-analyze
  (:require [busshi.methods.busshi-edn :as be]
            [busshi.methods.analyze :as a]
            [clojure.string :as str]
            [clojure.test :refer [deftest is run-tests]]))

(def seed-path "20-actors/busshi/kotoba/seed.edn")
(defn- cs [] (be/commodities seed-path))
(defn- by-id [id] (first (filter #(= id (:id %)) (cs))))

;; ── analytics correctness ────────────────────────────────────────────────────

(deftest top-share-excludes-other
  ;; gold's :other 59 must NOT count as a producer share (China 12 is the max)
  (is (= 12 (a/top-producer-share (by-id "au"))))
  (is (= 99 (a/top-producer-share (by-id "ga")))))  ; USGS: China 99% of primary low-purity gallium

(deftest chokepoint-levels
  (is (= :critical (a/chokepoint-risk (a/top-producer-share (by-id "ga"))))) ; gallium 98
  (is (= :critical (a/chokepoint-risk (a/top-producer-share (by-id "co"))))) ; cobalt cd=76 (USGS 2024e)
  (is (= :low      (a/chokepoint-risk (a/top-producer-share (by-id "au"))))) ; gold 10
  (is (= :moderate (a/chokepoint-risk (a/top-producer-share (by-id "zn"))))) ; zinc cn=33
  (is (= :critical (a/chokepoint-risk (a/top-producer-share (by-id "ni")))))) ; nickel id=67 (USGS MCS 2026 2025e)

(deftest route-by-dominant-driver
  (is (= :de-monopolization (a/route (by-id "ga"))) "gallium: monopoly dominant")
  (is (= :restoration (a/route (by-id "coal"))) "coal: carbon/irreversibility dominant")
  (is (= :resilience (a/route (by-id "au"))) "gold: neither dominant"))

(deftest multigen-risk-bounded
  (doseq [c (cs)]
    (let [r (a/multigen-risk c)]
      (is (and (>= r 0.0) (<= r 1.0)) (str (:id c) " risk in 0..1")))))

(deftest analyze-shape
  (let [res (a/analyze (cs))]
    (is (= (count (get res "commodities")) (count (cs))))
    (is (= 5 (count (get res "classes"))) "five class aggregates")
    (is (every? #(contains? % "mean_multigen_risk") (get res "classes")))))

;; ── datom emission + constitutional invariants ───────────────────────────────

(deftest datoms-flagged-derived-and-sourced
  (let [edn (a/render-datoms (a/analyze (cs)))]
    (is (str/includes? edn ":busshi/derived"))
    (is (str/includes? edn ":busshi/sourcing"))
    (is (str/includes? edn ":busshi.obs/multigen-risk"))
    (is (str/includes? edn ":busshi.obs/chokepoint-risk"))))

(deftest authoritative-provenance-folded
  ;; G7 operator-triggered ingest: rows carrying :sourcing :authoritative emit
  ;; :busshi/sourcing :authoritative + a cited :busshi/source; :representative
  ;; rows carry neither an :authoritative tag nor a :busshi/source.
  (let [edn (a/render-datoms (a/analyze (cs)))
        co  (a/analyze-commodity (by-id "co"))]
    (is (= :authoritative (get co "sourcing")) "cobalt is USGS-sourced")
    (is (str/includes? (get co "source") "USGS MCS"))
    (is (= :representative (get (a/analyze-commodity (by-id "ge")) "sourcing"))
        "germanium (USGS calls its own refinery-production data unverifiable) stays representative")
    (is (str/includes? edn ":authoritative"))
    (is (str/includes? edn ":busshi/source"))
    (is (str/includes? edn "USGS MCS"))
    ;; the authoritative folding changed the disclosed cobalt share to the real value
    (is (= 73 (a/top-producer-share (by-id "co"))))))  ; USGS MCS 2026 (2025e)

(deftest g1-g3-no-trade-no-signal-no-forecast
  ;; G1: never a trade. G3: never a signal / point forecast.
  (let [edn (a/render-datoms (a/analyze (cs)))]
    (is (not (str/includes? edn ":busshi/trade")))
    (is (not (str/includes? edn ":busshi/signal")))
    (is (not (str/includes? edn "price-forecast-point")))
    (is (not (str/includes? edn ":busshi.obs/buy")))
    (is (not (str/includes? edn ":busshi.obs/sell")))))

(deftest g5-report-is-resilience-map-not-target-list
  (let [md (a/render-report (a/analyze (cs)) (a/coverage (cs)))]
    (is (str/includes? md "target-list") "must say it is NOT a target-list")
    (is (str/includes? md "RESILIENCE MAP"))
    (is (not (str/includes? md "mine-coordinates")))))

;; ── coverage ─────────────────────────────────────────────────────────────────

(deftest coverage-gap-nonneg
  (let [cov (a/coverage (cs))]
    (is (= 5 (count (get cov "by_class"))))
    (is (every? #(>= (get % "gap") 0) (get cov "by_class")))
    (is (>= (get cov "total_have") 24))
    (is (>= (get cov "total_gap") 0))))

#?(:clj
   (when (= *file* (System/getProperty "babashka.file"))
     (let [{:keys [fail error]} (run-tests 'busshi.methods.test-analyze)]
       (System/exit (if (zero? (+ fail error)) 0 1)))))
