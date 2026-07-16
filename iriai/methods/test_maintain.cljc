#!/usr/bin/env bb
;; iriai 入会 — maintenance-lifecycle tests (incl. the SAFETY-FLOOR invariant).
;; Run:  bb --classpath 20-actors 20-actors/iriai/methods/test_maintain.cljc
(ns iriai.methods.test-maintain
  (:require [iriai.methods.iriai-edn :as ie]
            [iriai.methods.twin :as twin]
            [iriai.methods.maintain :as maint]
            [clojure.string :as str]
            [clojure.test :refer [deftest is run-tests]]))

(def seed-path "20-actors/iriai/kotoba/seed.edn")
(defn- assets [] (vec (filter #(= (:type %) :asset)
                              (ie/parse-edn (slurp seed-path)))))
(defn- by-id [id] (first (filter #(= id (:id %)) (assets))))
(defn- v [id] (let [a (by-id id)] (:verdict (maint/verdict a (twin/assess-asset a)))))

;; ── each asset reaches its designed verdict ────────────────────────────────────
(deftest verdicts-as-designed
  (is (= :ok (v "xfmr-midori-1")))
  (is (= :refurbish (v "xfmr-machi-2")) "mid-life, not yet refurbished")
  (is (= :decommission (v "xfmr-decom-1")) "unsafe + RUL ≤ 0")
  (is (= :preventive-service (v "pipe-kibou-1")) "service interval due")
  (is (= :renew (v "pipe-shima-3")) "condition < 0.25")
  (is (= :corrective-repair (v "gas-saigai-1")) "condition < 0.5, still safe")
  (is (= :corrective-repair (v "gas-shima-2")) "unsafe gas → immediate")
  (is (= :inspect (v "fibre-machi-1")) "inspect interval due")
  (is (= :renew (v "road-saigai-2")) "road RUL < 3")
  (is (= :corrective-repair (v "road-bridge-1")) "under-rated bridge → immediate")
  (is (= :ok (v "road-midori-1"))))

;; ── THE SAFETY-FLOOR INVARIANT: no unsafe asset is ever deferred ───────────────
(deftest safety-floor-never-deferred
  (doseq [a (assets)]
    (let [t (twin/assess-asset a)
          vd (:verdict (maint/verdict a t))]
      (when (= :unsafe (:safety t))
        (is (#{:corrective-repair :decommission} vd)
            (str (:id a) " is unsafe → must be corrective-repair or decommission, never deferred for cost"))))))

;; ── a safety-floor corrective carries the :safety-floor reason ─────────────────
(deftest safety-floor-reason-tagged
  (let [a (by-id "gas-shima-2")
        r (maint/verdict a (twin/assess-asset a))]
    (is (= :corrective-repair (:verdict r)))
    (is (= :safety-floor (:reason r))
        "an unsafe-but-still-has-life asset is a SAFETY-FLOOR corrective, not a cost-deferred one"))
  ;; and the structural proof: gas-shima-2 condition < 0.25 would say :renew, but safety wins
  (is (not= :renew (v "gas-shima-2")) "safety floor fires BEFORE the condition-renew branch"))

;; ── OpEx aggregates; consumer is never charged (G2 cash≡0) ─────────────────────
(deftest opex-and-cash-zero
  (let [pl (maint/plan (assets))]
    (is (pos? (get pl "opex_annual_usd")) "upkeep has real OpEx")
    (is (= 2 (get pl "safety_floor_actions")) "gas-G2 + bridge-B1")
    ;; cash≡0 to the consumer is structural — no per-consumer charge attribute exists
    (let [edn (maint/render-datoms pl)]
      (is (not (str/includes? edn ":iriai.maint/consumer-bill")))
      (is (not (str/includes? edn ":iriai.maint/tariff"))))))

;; ── G5 structural: DESIGN ONLY — all :intent, no dispatch/actuate attr ─────────
(deftest g5-design-only
  (let [pl (maint/plan (assets))
        edn (maint/render-datoms pl)]
    (is (true? (get pl "all_intent")))
    (is (not (str/includes? edn ":iriai.maint/dispatch-crew")))
    (is (not (str/includes? edn ":iriai/actuate")))
    (is (str/includes? edn ":iriai.maint/actuation-class"))
    (is (str/includes? edn ":iriai.maint/verdict"))))

;; ── executor routing is present for every actionable verdict ───────────────────
(deftest executor-routing
  (doseq [r (get (maint/plan (assets)) "actions")]
    (when (not= :ok (get r "verdict"))
      (is (not= "—" (get r "executor")) (str (get r "id") " actionable verdict has an executor")))))

#?(:clj
   (when (= *file* (System/getProperty "babashka.file"))
     (let [{:keys [fail error]} (run-tests 'iriai.methods.test-maintain)]
       (System/exit (if (zero? (+ fail error)) 0 1)))))
