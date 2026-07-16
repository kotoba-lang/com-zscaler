#!/usr/bin/env bb
;; mitsuho 瑞穂 — agent gate tests (Clojure port of test_agent.py).
;;
;; ADR-2605261015, R0 scaffold. Exercises the 5 handlers + settlement + gates with
;; no kotoba host, no network, no LLM. Tests focus on:
;;   G7  seed-source sovereignty (validate-seed-source gate)
;;   G9  prohibited-pesticide refusal (validate-pesticides gate)
;;   G8  soil carbon floor → pending_council_review
;;   G3/G5  settlement stops at :intent; tithe split 10%
;;   G4  settlement executes only with member sig
;;
;; Run:  bb --classpath 20-actors 20-actors/mitsuho/py/test_agent.clj
(ns mitsuho.methods.test-agent
  (:require [clojure.test :refer [deftest is testing run-tests]]
            [mitsuho.methods.agent :as agent]))

;; ── parcel attestation ────────────────────────────────────────────────────────
(deftest test-parcel-attestation-recorded
  (testing "parcel attestation recorded"
    (let [out (agent/handle-parcel-attestation
               {:parcel_id           "p1"
                :soil_health_score   7.5
                :water_quality_score 8.0
                :biodiversity_impact "no-harm"})]
      (is (= "recorded" (:attestation_state out)))
      (is (= "p1" (:parcel_id out))))))

;; ── seed-source validation (G7) ──────────────────────────────────────────────
(deftest test-seed-source-svalbard-approved
  (testing "Svalbard seed source approved (G7)"
    (let [out (agent/validate-seed-source "did:web:svalbard.seedvault.no")]
      (is (true? (:valid out))))))

(deftest test-seed-source-patented-rejected
  (testing "patented seed source rejected (G7)"
    (let [out (agent/validate-seed-source "Monsanto proprietary line X")]
      (is (false? (:valid out))))))

;; ── crop plan — pesticide gates (G9) ─────────────────────────────────────────
(deftest test-crop-plan-pesticide-neonicotinoid-rejected
  (testing "neonicotinoid pesticide rejected (G9)"
    (let [out (agent/handle-crop-plan
               {:crop_plan_id         "cp1"
                :seed_source          "Svalbard"
                :pesticide_manifest   ["neonicotinoid imidacloprid"]
                :organic_certification false})]
      (is (= "rejected" (:plan_state out))))))

(deftest test-crop-plan-glyphosate-rejected
  (testing "glyphosate rejected (G9)"
    (let [out (agent/handle-crop-plan
               {:crop_plan_id         "cp2"
                :seed_source          "national gene bank"
                :pesticide_manifest   ["glyphosate"]
                :organic_certification false})]
      (is (= "rejected" (:plan_state out))))))

(deftest test-crop-plan-no-pesticides-approved
  (testing "organic plan with no pesticides approved (G9)"
    (let [out (agent/handle-crop-plan
               {:crop_plan_id         "cp3"
                :seed_source          "NAVDANYA"
                :pesticide_manifest   []
                :organic_certification true})]
      (is (= "recorded" (:plan_state out))))))

;; ── harvest — soil carbon gate (G8) ──────────────────────────────────────────
(deftest test-harvest-positive-soil-carbon-recorded
  (testing "harvest with positive soil carbon recorded (G8)"
    (let [out (agent/handle-harvest
               {:harvest_id                   "h1"
                :yield_quantity_kg            5000.0
                :quality_grade                "Grade A"
                :soil_carbon_delta_tons_co2eq 2.5})]
      (is (= "recorded" (:harvest_state out)))
      (is (= 2.5 (:soil_carbon_delta_tons_co2eq out))))))

(deftest test-harvest-negative-soil-carbon-pending-council
  (testing "harvest with negative soil carbon pending Council review (G8)"
    (let [out (agent/handle-harvest
               {:harvest_id                   "h2"
                :yield_quantity_kg            4000.0
                :quality_grade                "Grade B"
                :soil_carbon_delta_tons_co2eq -1.2})]
      (is (= "pending_council_review" (:harvest_state out))))))

;; ── food lot attestation ──────────────────────────────────────────────────────
(deftest test-food-lot-attestation-recorded
  (testing "food lot attestation recorded"
    (let [out (agent/handle-food-lot
               {:food_lot_id             "lot1"
                :kilojoules_per_kg       1510.0
                :protein_g_per_100g      6.3
                :carbohydrate_g_per_100g 78.0
                :fat_g_per_100g          1.2
                :shelf_life_days         365
                :packaging_type          "vacuum-sealed"})]
      (is (= "recorded" (:lot_state out)))
      (is (= "lot1" (:food_lot_id out))))))

;; ── settlement — G3/G5 tithe split + stops at :intent ────────────────────────
(deftest test-settlement-tithe-split
  (testing "10% tithe split + stops at intent (G3/G5)"
    (let [s (agent/build-settlement-intent 250000000)]
      (is (= 25000000  (:titheMinor s)))
      (is (= 225000000 (:producerPayoutMinor s)))
      (is (= "intent"  (:state s)))
      (is (= "usdc-base-l2" (:rail s))))))

(deftest test-settlement-executed-only-with-member-sig
  (testing "settlement executes only with member signature (G4)"
    (let [s (agent/build-settlement-intent 1000000 "0xmembersig")]
      (is (= "executed" (:state s))))))

;; ── runner ────────────────────────────────────────────────────────────────────
(when (= *file* (System/getProperty "babashka.file"))
  (let [{:keys [fail error]} (clojure.test/run-tests 'mitsuho.methods.test-agent)]
    (System/exit (if (zero? (+ fail error)) 0 1))))
