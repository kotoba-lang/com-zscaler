#!/usr/bin/env bb
;; iriai 入会 — physical-simulation twin tests (real degradation physics, bounded, monotone).
;; Run:  bb --classpath 20-actors 20-actors/iriai/methods/test_twin.cljc
(ns iriai.methods.test-twin
  (:require [iriai.methods.iriai-edn :as ie]
            [iriai.methods.twin :as twin]
            [clojure.string :as str]
            [clojure.test :refer [deftest is run-tests]]))

(def seed-path "20-actors/iriai/kotoba/seed.edn")
(defn- assets [] (vec (filter #(= (:type %) :asset)
                              (ie/parse-edn (slurp seed-path)))))
(defn- by-id [id] (first (filter #(= id (:id %)) (assets))))
(defn- t [id] (twin/assess-asset (by-id id)))

;; ── condition is bounded 0..1 for every asset + lifeline ───────────────────────
(deftest condition-bounded
  (doseq [a (assets)]
    (let [c (:condition (twin/assess-asset a))]
      (is (<= 0.0 c 1.0) (str (:id a) " condition in [0,1]")))))

;; ── degradation is MONOTONE: older = worse (project forward lowers condition) ──
(deftest aging-is-monotone
  (doseq [a (assets)]
    (let [now (:condition (twin/assess-asset a))
          later (:condition (twin/project a 10))]
      (is (<= later (+ now 1e-9)) (str (:id a) " condition must not improve with age")))))

;; ── physics: a heavily-loaded transformer ages FASTER than a lightly-loaded one ─
(deftest thermal-aging-load-dependent
  (let [light (twin/assess-asset {:lifeline :electric :age-years 20 :design-life 30 :load-factor 0.4 :ambient-c 25})
        heavy (twin/assess-asset {:lifeline :electric :age-years 20 :design-life 30 :load-factor 1.0 :ambient-c 40})]
    (is (< (:condition heavy) (:condition light)) "IEEE C57.91: higher load → hotter → faster loss-of-life")
    (is (< (:rul heavy) (:rul light)) "heavy load → shorter remaining life")))

;; ── safety detection per failure mode ──────────────────────────────────────────
(deftest safety-flags
  (is (= :unsafe (:safety (t "xfmr-decom-1"))) "thermally aged-out transformer (loss-of-life ≥ 1)")
  (is (= :unsafe (:safety (t "gas-shima-2")))  "gas main leak-probability > 0.7")
  (is (= :unsafe (:safety (t "road-bridge-1"))) "bridge load-rating < 1.0")
  (is (= :ok (:safety (t "xfmr-midori-1"))))
  (is (= :ok (:safety (t "fibre-machi-1")))))

;; ── RUL: an aged-out asset has RUL ≤ 0; a young one has RUL > 0 ─────────────────
(deftest rul-sign
  (is (<= (:rul (t "xfmr-decom-1")) 0) "past end of life")
  (is (> (:rul (t "xfmr-midori-1")) 0) "young, life remaining"))

;; ── all five lifelines dispatch (no fall-through to the default branch) ────────
(deftest all-lifelines-modelled
  (doseq [lf [:electric :water :gas :telecom :road]]
    (let [a (first (filter #(= lf (:lifeline %)) (assets)))]
      (is (some? a) (str "seed has a " (name lf) " asset"))
      (is (not= "?" (:driver (twin/assess-asset a))) (str (name lf) " has a real degradation driver")))))

;; ── G5 structural: SIMULATION ONLY — no actuation attr in the datoms ───────────
(deftest g5-twin-simulation-only
  (let [edn (str (twin/datoms (twin/assess (assets))))]
    (is (not (str/includes? edn ":iriai/actuate")))
    (is (not (str/includes? edn ":iriai.twin/energize")))
    (is (str/includes? edn ":iriai.twin/condition"))
    (is (str/includes? edn ":iriai.twin/safety"))))

;; ── summary counts ─────────────────────────────────────────────────────────────
(deftest assess-summary
  (let [a (twin/assess (assets))]
    (is (= 11 (get a "count")))
    (is (= 3 (get a "unsafe")) "T9 + gas-G2 + bridge-B1")
    (is (<= 0.0 (get a "mean-condition") 1.0))))

#?(:clj
   (when (= *file* (System/getProperty "babashka.file"))
     (let [{:keys [fail error]} (run-tests 'iriai.methods.test-twin)]
       (System/exit (if (zero? (+ fail error)) 0 1)))))
