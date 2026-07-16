#!/usr/bin/env bb
;; 燠 okibi — analyze/matching/datoms tests (incl. constitutional + physics invariants).
;; Run:  bb --classpath 20-actors 20-actors/okibi/methods/test_analyze.cljc
(ns okibi.methods.test-analyze
  (:require [okibi.methods.okibi-edn :as oe]
            [okibi.methods.analyze :as a]
            [clojure.string :as str]
            [clojure.test :refer [deftest is run-tests]]))

(def seed-path "20-actors/okibi/kotoba/seed.edn")
(defn- srcs [] (oe/sources seed-path))
(defn- snks [] (oe/sinks seed-path))
(defn- src [id] (first (filter #(= id (:id %)) (srcs))))
(defn- snk [id] (first (filter #(= id (:id %)) (snks))))
(defn- matches [] (get (a/analyze (srcs) (snks)) "matches"))
(defn- match? [s k] (some #(and (= s (get % "src")) (= k (get % "sink"))) (matches)))
(defn- sink-row [id] (first (filter #(= id (get % "id")) (get (a/analyze (srcs) (snks)) "sinks"))))

;; ── physics gates (G2) ───────────────────────────────────────────────────────

(deftest temperature-cascade-gate
  (is (a/feasible? (src "dc-a") (snk "district-a")) "65°C serves req-55°C")
  (is (not (a/feasible? (src "dc-a") (snk "absorption-f"))) "65°C cannot serve req-90°C (cascade)")
  (is (not (a/feasible? (src "geo-c") (snk "absorption-f"))) "82°C cannot serve req-90°C either"))

(deftest distance-gate
  (is (< (a/distance-m (src "dc-a") (snk "district-a")) 300) "site A is ~140 m")
  (is (> (a/distance-m (src "dc-a") (snk "spaceheat-e")) 5000) "space-heat E is remote")
  (is (not (a/feasible? (src "geo-c") (snk "spaceheat-e"))) "remote sink is infeasible (distance)"))

;; ── matching outcomes ────────────────────────────────────────────────────────

(deftest strong-local-match-exists
  (is (match? "dc-a" "district-a") "the DC↔district match is made"))

(deftest cascade-failure-leaves-demand-unmet
  (is (not-any? #(= "absorption-f" (get % "sink")) (matches)) "absorption-f matches nothing")
  (is (> (get (sink-row "absorption-f") "unmet_kw") 0.0) "its demand is unmet (no source hot enough)"))

(deftest distant-sink-unmet
  (is (not-any? #(= "spaceheat-e" (get % "sink")) (matches)))
  (is (> (get (sink-row "spaceheat-e") "unmet_kw") 0.0) "remote demand is unmet"))

(deftest remote-source-is-surplus
  (let [res (a/analyze (srcs) (snks))
        d (first (filter #(= "refrig-d" (get % "id")) (get res "sources")))]
    (is (not-any? #(= "refrig-d" (get % "src")) (matches)) "the remote source matches nothing")
    (is (> (get d "surplus_kw") 0.0) "its heat is surplus")))

(deftest conservation-no-overallocation
  ;; every source's matched-out ≤ its effective capacity (kw × availability) + ε
  (let [res (a/analyze (srcs) (snks))]
    (doseq [r (get res "sources")]
      (let [s (src (get r "id"))
            eff (* (double (:kw s)) (double (:availability s)))]
        (is (<= (get r "matched_kw") (+ eff 1e-6))
            (str (get r "id") " does not over-deliver"))))
    (is (pos? (get-in res ["totals" "matched_kw"])) "some heat is matched")))

(deftest match-quality-bounded
  (doseq [s (srcs) k (snks) :when (a/feasible? s k)]
    (let [q (a/match-quality s k)]
      (is (and (>= q 0.0) (<= q 1.0)) (str (:id s) "→" (:id k) " quality in 0..1")))))

;; ── datom emission + G1/G2 unrepresentability ────────────────────────────────

(deftest datoms-flagged-derived-and-sourced
  (let [edn (a/render-datoms (a/analyze (srcs) (snks)))]
    (is (str/includes? edn ":okibi/derived"))
    (is (str/includes? edn ":okibi/sourcing"))
    (is (str/includes? edn ":okibi.match/matched-kw"))
    (is (str/includes? edn ":okibi.ledger/matched-kw"))
    (is (str/includes? edn ":okibi/source"))))

(deftest g1-g2-no-dispatch-no-trade-no-cooling-sink
  (let [edn (a/render-datoms (a/analyze (srcs) (snks)))]
    (is (not (str/includes? edn ":okibi/dispatch")))      ; G1: never a dispatch order
    (is (not (str/includes? edn ":okibi/trade")))
    (is (not (str/includes? edn ":okibi/signal")))
    (is (not (str/includes? edn "fabricated")))            ; G2: no fabricated match
    (is (not (str/includes? edn "cooling-load")))))        ; §1 anti-pattern unrepresentable

(deftest report-is-matching-map-not-dispatch
  (let [md (a/render-report (a/analyze (srcs) (snks)) (a/coverage (srcs)))]
    (is (str/includes? md "THERMAL MATCHING MAP"))
    (is (str/includes? md "NEVER a dispatch order"))
    (is (str/includes? md "target-list"))))

;; ── coverage ─────────────────────────────────────────────────────────────────

(deftest coverage-gap-nonneg
  (let [cov (a/coverage (srcs))]
    (is (= 6 (count (get cov "by_class"))))
    (is (every? #(>= (get % "gap") 0) (get cov "by_class")))))

#?(:clj
   (when (= *file* (System/getProperty "babashka.file"))
     (let [{:keys [fail error]} (run-tests 'okibi.methods.test-analyze)]
       (System/exit (if (zero? (+ fail error)) 0 1)))))
