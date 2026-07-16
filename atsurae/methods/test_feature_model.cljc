#!/usr/bin/env bb
;; atsurae 誂え — PLE feature-model tests (incl. constraint-satisfaction + commons-not-license).
;; Run:  bb --classpath 20-actors 20-actors/atsurae/methods/test_feature_model.cljc
(ns atsurae.methods.test-feature-model
  (:require [atsurae.methods.feature-model :as fm]
            [atsurae.methods.emit :as emit]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing run-tests]]))

(def seed-path "20-actors/atsurae/kotoba/seed.edn")
(defn- model [] (fm/classify (fm/load-edn seed-path)))
(defn- valid? [sel] (:valid? (fm/valid-config? (model) sel)))

;; ── validation: structural cardinalities ──────────────────────────────────────

(deftest mandatory-and-xor
  (testing "a complete minimal config is valid; xor groups admit exactly one child"
    (is (valid? #{:robot-base :locomotion :wheels :power :battery}))
    (is (not (valid? #{:robot-base :power :battery})) "missing mandatory locomotion")
    (is (not (valid? #{:robot-base :locomotion :wheels :tracks :power :battery}))
        "two locomotion children violate the xor group")
    (is (not (valid? #{:robot-base :locomotion :power :battery}))
        "an xor group with zero children selected is invalid")))

(deftest optional-and-or-group
  (testing "sensing is optional; when present its or-group needs ≥1 sensor"
    (is (valid? #{:robot-base :locomotion :wheels :power :battery :sensing :camera}))
    (is (not (valid? #{:robot-base :locomotion :wheels :power :battery :sensing}))
        "sensing selected but no sensor child violates the or-group")
    (is (valid? #{:robot-base :locomotion :wheels :power :battery})
        "sensing absent is fine (optional)")))

;; ── validation: cross-tree constraints ────────────────────────────────────────

(deftest requires-and-excludes
  (testing "requires/excludes constraints are enforced"
    (is (not (valid? #{:robot-base :locomotion :wheels :power :battery :autonomy}))
        "autonomy requires lidar")
    (is (valid? #{:robot-base :locomotion :wheels :power :battery :sensing :lidar :autonomy})
        "autonomy with lidar is valid")
    (is (not (valid? #{:robot-base :locomotion :legs :power :tethered}))
        "legs excludes tethered")
    (is (not (valid? #{:robot-base :locomotion :wheels :power :tethered :sensing :lidar :autonomy}))
        "autonomy excludes tethered")))

(deftest orphan-rejected
  (testing "a feature whose parent is unselected is an orphan"
    (is (not (valid? #{:robot-base :wheels :power :battery}))
        "wheels without its locomotion parent")))

;; ── enumeration / commonality / variability ───────────────────────────────────

(deftest variant-space
  (testing "the feature model enumerates a bounded valid variant space"
    (let [a (fm/analyze (model))]
      (is (= 15 (:n-features a)))
      (is (= 176 (:n-variants a)) "constraint-pruned valid variant count")
      (is (empty? (:dead a)) "no feature is constraint-unreachable")
      ;; every enumerated variant is actually valid
      (is (every? #(:valid? (fm/valid-config? (model) %)) (:variants a))))))

(deftest commonality-platform-vs-variation
  (testing "platform features are in every variant; variation points are partial"
    (let [a (fm/analyze (model))
          comm (:commonality a)]
      ;; the common platform: every variant has a base, a locomotion, a power source
      (is (= #{:robot-base :locomotion :power} (set (:platform a))))
      (is (= 1.0 (get comm :robot-base)))
      ;; autonomy is a genuine variation point (some variants, not all/none)
      (is (< 0.0 (get comm :autonomy) 1.0))
      (is (some #{:autonomy} (:variation-points a)))
      ;; requires makes lidar at least as common as autonomy
      (is (>= (get comm :lidar) (get comm :autonomy))))))

;; ── BOM derivation (composes with uchiwake / open-kyber) ──────────────────────

(deftest bom-derivation
  (testing "a variant's BOM is the union of its selected features' parts, qty summed"
    (let [sel #{:robot-base :locomotion :wheels :power :battery}
          bom (fm/derive-bom (model) sel)]
      (is (= 4 (get bom :wheel-motor)) "wheels binds 4 wheel-motors")
      (is (= 1 (get bom :lfp-pack)))
      (is (= 1 (get bom :frame)) "robot-base binds the frame")
      (is (nil? (get bom :h2-stack)) "fuel-cell not selected → no h2-stack"))))

;; ── G1/G2: commons spec, never a license key; spec only, never builds ─────────

(deftest g1-g2-no-lock-no-manufacture
  (testing "a feature model is a COMMONS spec — no license-lock / drm / manufacture attribute"
    (let [edn (emit/render-datoms (model))]
      (is (not (str/includes? edn ":atsurae/license-lock")))
      (is (not (str/includes? edn ":atsurae/drm")))
      (is (not (str/includes? edn ":atsurae/manufacture")))
      (is (str/includes? edn ":atsurae.feature/commonality"))
      (is (str/includes? edn ":atsurae/derived")))))

(deftest g2-g3-report-framing
  (testing "the report declares commons-not-license + spec-only-never-manufactures"
    (let [md (fm/report-md (model))]
      (is (str/includes? md "COMMONS spec"))
      (is (str/includes? md "never manufactures"))
      (is (str/includes? md "never a license key")))))

#?(:clj
   (when (= *file* (System/getProperty "babashka.file"))
     (let [{:keys [fail error]} (run-tests 'atsurae.methods.test-feature-model)]
       (System/exit (if (zero? (+ fail error)) 0 1)))))
