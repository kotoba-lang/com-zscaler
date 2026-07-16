#!/usr/bin/env bb
;; 撓 tawami — analyze/datoms/coverage tests (incl. constitutional invariants).
;; Run:  bb --classpath 20-actors 20-actors/tawami/methods/test_analyze.cljc
(ns tawami.methods.test-analyze
  (:require [tawami.methods.tawami-edn :as te]
            [tawami.methods.analyze :as a]
            [clojure.string :as str]
            [clojure.test :refer [deftest is run-tests]]))

(def seed-path "20-actors/tawami/kotoba/seed.edn")
(defn- as [] (te/assets seed-path))
(defn- by-id [id] (first (filter #(= id (:id %)) (as))))
(defn- row [id] (first (filter #(= id (get % "id")) (get (a/analyze (as)) "assets"))))

;; ── analytics correctness ────────────────────────────────────────────────────

(deftest responsiveness-boundaries
  (is (= 1.0 (a/responsiveness 1)))
  (is (= 0.9 (a/responsiveness 5)))
  (is (= 0.7 (a/responsiveness 15)))
  (is (= 0.5 (a/responsiveness 45)))
  (is (= 0.3 (a/responsiveness 90))))

(deftest flex-value-known
  ;; community battery: cap 1000 kWh × avail 0.9 × resp 1.0 × (0.5 + 0.5·8/24)
  (is (< (Math/abs (- 600.0 (a/flex-value (by-id "batt-community-01")))) 1e-9)))

(deftest tier-by-responsiveness
  (is (= :fast-flex (get (row "batt-community-01") "tier")))
  (is (= :fast-flex (get (row "ev-depot-01") "tier")))
  (is (= :mid-flex  (get (row "coldstore-dc-01") "tier")))
  (is (= :slow-flex (get (row "ind-arc-furnace-01") "tier"))))

(deftest best-use-mapping
  (is (= :peak-shave       (get (row "batt-community-01") "best_use")))
  (is (= :renewable-absorb (get (row "ev-depot-01") "best_use")))
  (is (= :compute-routing  (get (row "dc-murakumo-01") "best_use")))
  (is (= :flexibility      (get (row "ind-arc-furnace-01") "best_use"))))

(deftest shiftability-bounded
  (doseq [x (as)]
    (let [s (a/shiftability x)]
      (is (and (>= s 0.0) (<= s 1.0)) (str (:id x) " shiftability in 0..1")))))

(deftest analyze-shape
  (let [res (a/analyze (as))]
    (is (= (count (get res "assets")) (count (as))))
    (is (= 6 (count (get res "classes"))) "six resource-class aggregates")
    (is (pos? (get-in res ["totals" "total_flex_value"])))
    (is (pos? (get-in res ["totals" "fast_flex_count"])))))

;; ── datom emission + G1/G2/G3 unrepresentability ─────────────────────────────

(deftest datoms-flagged-derived-and-sourced
  (let [edn (a/render-datoms (a/analyze (as)))]
    (is (str/includes? edn ":tawami/derived"))
    (is (str/includes? edn ":tawami/sourcing"))
    (is (str/includes? edn ":tawami.obs/flex-value"))
    (is (str/includes? edn ":tawami.obs/best-use"))
    (is (str/includes? edn ":tawami.ledger/total-flex-value"))))

(deftest authoritative-provenance-folded
  (let [edn (a/render-datoms (a/analyze (as)))]
    (is (= :authoritative (get (row "batt-community-01") "sourcing")))
    (is (str/includes? (get (row "batt-community-01") "source") "SCADA"))
    (is (= :representative (get (row "batt-home-fleet-01") "sourcing")))
    (is (str/includes? edn ":tawami/source"))))

(deftest g1-g2-g3-map-not-dispatch-no-person-no-trade
  (let [edn (a/render-datoms (a/analyze (as)))]
    (is (not (str/includes? edn ":tawami/dispatch")))      ; G1: never a dispatch order
    (is (not (str/includes? edn "curtail-order")))
    (is (not (str/includes? edn ":tawami.person")))        ; G2: no per-person load profile
    (is (not (str/includes? edn ":tawami/trade")))         ; G3: never a trade
    (is (not (str/includes? edn ":tawami/signal")))))

(deftest report-is-flexibility-map-not-dispatch
  (let [md (a/render-report (a/analyze (as)) (a/coverage (as)))]
    (is (str/includes? md "FLEXIBILITY MAP"))
    (is (str/includes? md "NEVER a dispatch order"))
    (is (str/includes? md "ABILITY TO "))))

;; ── coverage ─────────────────────────────────────────────────────────────────

(deftest coverage-gap-nonneg
  (let [cov (a/coverage (as))]
    (is (= 6 (count (get cov "by_class"))))
    (is (every? #(>= (get % "gap") 0) (get cov "by_class")))
    (is (>= (get cov "total_have") 11))))

#?(:clj
   (when (= *file* (System/getProperty "babashka.file"))
     (let [{:keys [fail error]} (run-tests 'tawami.methods.test-analyze)]
       (System/exit (if (zero? (+ fail error)) 0 1)))))
