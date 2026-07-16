#!/usr/bin/env bb
;; 樋 toi — analyze/routing/datoms tests (incl. constitutional invariants).
;; Run:  bb --classpath 20-actors 20-actors/toi/methods/test_analyze.cljc
(ns toi.methods.test-analyze
  (:require [toi.methods.toi-edn :as te]
            [toi.methods.analyze :as a]
            [clojure.string :as str]
            [clojure.test :refer [deftest is run-tests]]))

(def seed-path "20-actors/toi/kotoba/seed.edn")
(defn- jobs [] (te/jobs seed-path))
(defn- sites [] (te/sites seed-path))
(defn- site [id] (first (filter #(= id (:id %)) (sites))))
(defn- res [] (a/analyze (jobs) (sites)))
(defn- job-row [id] (first (filter #(= id (get % "id")) (get (res) "jobs"))))
(defn- site-row [id] (first (filter #(= id (get % "id")) (get (res) "sites"))))

;; ── G2: Murakumo default-preferred ───────────────────────────────────────────

(deftest murakumo-outscores-commercial-gpu
  (is (> (a/site-score (site "cold-hydro-mk")) (a/site-score (site "commercial-gpu-warm")))
      "a clean Murakumo site outscores commercial GPU")
  (is (< (a/site-score (site "commercial-gpu-warm")) 0.2) "commercial GPU scores low"))

(deftest commercial-gpu-unused-while-clean-capacity-exists
  (is (= 0.0 (get (site-row "commercial-gpu-warm") "utilization_kwh"))
      "commercial GPU is a fallback, not chosen while clean Murakumo capacity exists"))

;; ── routing outcomes ─────────────────────────────────────────────────────────

(deftest movable-jobs-routed-to-clean-sites
  (doseq [r (get (res) "routes")]
    (let [s (site (get r "site"))]
      (is (= :murakumo (:site-class s)) (str (get r "job") " routed to a Murakumo site"))
      (is (< (:carbon-intensity s) a/baseline-carbon-intensity) "cleaner than baseline"))))

(deftest non-movable-job-stays-in-place
  (is (= :in-place (get (job-row "infer-urgent") "route")) "pinned job is never coerced")
  (is (= 0.0 (get (job-row "infer-urgent") "avoided_carbon_kg"))))

(deftest avoided-carbon-positive-and-summed
  (let [r (res)
        manual (reduce + 0.0 (map #(get % "avoided_carbon_kg") (get r "routes")))]
    (is (pos? (get-in r ["totals" "avoided_carbon_kg"])))
    (is (< (Math/abs (- manual (get-in r ["totals" "avoided_carbon_kg"]))) 1e-6)
        "ledger avoided-carbon = sum of routings")
    (is (= 5 (get-in r ["totals" "routed_count"])) "all five movable jobs route")
    (is (= 1 (get-in r ["totals" "in_place_count"])) "one pinned job stays in place")))

(deftest heat-reuse-only-from-heat-sink-sites
  ;; jobs routed to cold-hydro-mk (heat-demand-sink) carry reusable heat; others 0.
  (doseq [r (get (res) "routes")]
    (let [s (site (get r "site"))]
      (if (:heat-demand-sink s)
        (is (pos? (get r "heat_reuse_kwh")) (str (get r "job") " heat reusable"))
        (is (= 0.0 (get r "heat_reuse_kwh")))))))

(deftest capacity-conservation
  (doseq [r (get (res) "sites")]
    (let [s (site (get r "id"))]
      (is (<= (get r "utilization_kwh") (+ (double (:capacity-kwh s)) 1e-6))
          (str (get r "id") " not over-subscribed")))))

(deftest site-score-bounded
  (doseq [s (sites)]
    (let [sc (a/site-score s)]
      (is (and (>= sc 0.0) (<= sc 1.0)) (str (:id s) " score in 0..1")))))

;; ── datom emission + G1 unrepresentability ───────────────────────────────────

(deftest datoms-flagged-derived-and-sourced
  (let [edn (a/render-datoms (res))]
    (is (str/includes? edn ":toi/derived"))
    (is (str/includes? edn ":toi/sourcing"))
    (is (str/includes? edn ":toi.route/avoided-carbon-kg"))
    (is (str/includes? edn ":toi.ledger/avoided-carbon-kg"))
    (is (str/includes? edn ":toi/source"))))

(deftest g1-no-dispatch-no-kill-order-no-trade
  (let [edn (a/render-datoms (res))]
    (is (not (str/includes? edn ":toi/dispatch")))        ; G1: never a forced dispatch
    (is (not (str/includes? edn "kill-order")))            ; G1: never a load-shedding weapon
    (is (not (str/includes? edn ":toi/trade")))
    (is (not (str/includes? edn ":toi/signal")))))

(deftest report-is-routing-map-not-dispatch
  (let [md (a/render-report (res) (a/coverage (sites)))]
    (is (str/includes? md "COMPUTE ROUTING MAP"))
    (is (str/includes? md "NEVER a forced job-kill"))
    (is (str/includes? md "default-preferred"))))

;; ── coverage ─────────────────────────────────────────────────────────────────

(deftest coverage-gap-nonneg
  (let [cov (a/coverage (sites))]
    (is (= 3 (count (get cov "by_class"))))
    (is (every? #(>= (get % "gap") 0) (get cov "by_class")))))

#?(:clj
   (when (= *file* (System/getProperty "babashka.file"))
     (let [{:keys [fail error]} (run-tests 'toi.methods.test-analyze)]
       (System/exit (if (zero? (+ fail error)) 0 1)))))
