#!/usr/bin/env bb
;; tsuchifumi 土踏み — analyze (relief gate + evidence-honesty) tests.
;; Run: bb --classpath 20-actors 20-actors/tsuchifumi/methods/test_analyze.cljc
(ns tsuchifumi.methods.test-analyze
  (:require [tsuchifumi.methods.tsuchifumi-edn :as te]
            [tsuchifumi.methods.analyze :as an]
            [clojure.string :as str]
            [clojure.test :refer [deftest is run-tests]]))

(def seed-path "20-actors/tsuchifumi/kotoba/seed.edn")
(defn- seed [] (te/load-seed seed-path))
(defn- regions [] (:regions (seed)))
(defn- evidence [] (:evidence (seed)))
(defn- by-id [id] (first (filter #(= id (:id %)) (regions))))
(defn- v [id] (:verdict (an/verdict (by-id id))))

;; ── verdict spread — every branch is exercised by the seed ───────────────────
(deftest all-verdicts-present
  (let [a (an/assess (regions) (evidence))
        t (get a "tally")]
    (is (pos? (get t :relief-priority 0)))
    (is (pos? (get t :infrastructure-gap 0)))
    (is (pos? (get t :await-evidence 0)))
    (is (pos? (get t :await-consent 0)))
    (is (pos? (get t :monitor 0)))))

(deftest consent-gate-first
  (is (= :await-consent (v "suburb-noconsent-d"))
      "no outreach consent → await, regardless of burden (G4)"))

(deftest high-deficit-low-exposure-is-infrastructure-gap
  (is (= :infrastructure-gap (v "town-paved-g"))
      "real institutional access gap on established evidence — no harm claim needed"))

;; ── G2 — a contested-pathway burden is NEVER asserted as harm ─────────────────
(deftest contested-pathway-routes-to-await-evidence
  (is (= :await-evidence (v "metro-emfheavy-f"))
      "burden dominated by ambient-EMF (contested) → harm not asserted (G2)")
  (is (= :contested (:tier (an/verdict (by-id "metro-emfheavy-f"))))))

(deftest relief-priority-never-rests-on-contested
  (doseq [r (get (an/assess (regions) (evidence)) "regions")]
    (when (= :relief-priority (get r "verdict"))
      (is (#{:established :emerging} (get r "evidence_tier"))
          "a relief-priority verdict must rest on ≥ emerging evidence (G2)"))))

(deftest confidence-tracks-tier
  (is (= 0.35 (an/confidence (by-id "metro-emfheavy-f"))) "contested → 0.35")
  (is (= 1.0 (an/confidence (by-id "town-paved-g"))) "access-dominant → established 1.0"))

;; ── scoring sanity ───────────────────────────────────────────────────────────
(deftest scores-in-range
  (doseq [r (regions)]
    (is (<= 0.0 (an/exposure-load r) 1.0))
    (is (<= 0.0 (an/earthing-deficit r) 1.0))
    (is (<= 0.0 (an/health-burden r) 1.0))))

(deftest relief-gap-ranked
  (let [a (an/assess (regions) (evidence))
        gap (get a "relief_gap")]
    (is (= (count (regions)) (count gap)))
    (is (apply >= (map #(get % "gap") gap)) "relief-gap is sorted descending")))

;; ── G1/G3/G5 — forbidden attributes never emitted ────────────────────────────
(deftest no-forbidden-attributes-in-datoms
  (let [a (an/assess (regions) (evidence))
        attrs (set (map (fn [[_ _ at _]] at) (an/datoms a)))]
    (doseq [bad [":tsuchifumi/diagnose" ":tsuchifumi/treat" ":tsuchifumi/cure"
                 ":tsuchifumi/product" ":tsuchifumi.person/health"
                 ":tsuchifumi.person/biometric" ":tsuchifumi/point-forecast"]]
      (is (not (contains? attrs bad)) (str bad " must never be emitted")))))

(deftest burden-datom-always-paired-with-tier
  ;; every health-burden datom must co-occur with an evidence-tier datom for the same entity (G2)
  (let [a (an/assess (regions) (evidence))
        ds (an/datoms a)
        ents-burden (set (keep (fn [[_ e at _]] (when (= at ":tsuchifumi.rel/health-burden") e)) ds))
        ents-tier   (set (keep (fn [[_ e at _]] (when (= at ":tsuchifumi.rel/evidence-tier") e)) ds))]
    (is (= ents-burden ents-tier)
        "a health-burden is never emitted without its evidence-tier (G2)")))

(deftest datoms-flagged
  (let [ds (an/datoms (an/assess (regions) (evidence)))]
    (is (some (fn [[_ _ at v]] (and (= at ":tsuchifumi/derived") (= v true))) ds))
    (is (some (fn [[_ _ at v]] (and (= at ":tsuchifumi/sourcing") (= v ":synthetic"))) ds))))

(let [{:keys [fail error]} (run-tests 'tsuchifumi.methods.test-analyze)]
  (when (pos? (+ fail error)) (System/exit 1)))
