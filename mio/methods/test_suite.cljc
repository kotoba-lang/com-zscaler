#!/usr/bin/env bb
;; Energy Order Protocol — SUITE INTEGRATION test (the full pipeline through 澪 mio).
;; Each leg (撓/燠/樋/委) emits flow-improvement claims; mio verifies + accounts the
;; org Flowrate. This proves the cross-actor seam end-to-end.
;; Run:  bb --classpath 20-actors 20-actors/mio/methods/test_suite.cljc
(ns mio.methods.test-suite
  (:require [mio.methods.analyze :as mio]
            [tawami.methods.tawami-edn :as tawami-edn]
            [tawami.methods.claim :as tawami-claim]
            [okibi.methods.okibi-edn :as okibi-edn]
            [okibi.methods.claim :as okibi-claim]
            [toi.methods.toi-edn :as toi-edn]
            [toi.methods.claim :as toi-claim]
            [yudane.methods.yudane-edn :as yudane-edn]
            [yudane.methods.claim :as yudane-claim]
            [clojure.test :refer [deftest is run-tests]]))

(defn- all-claims []
  (concat
   (tawami-claim/from-assets (tawami-edn/assets "20-actors/tawami/kotoba/seed.edn"))
   (okibi-claim/from-nodes (okibi-edn/sources "20-actors/okibi/kotoba/seed.edn")
                           (okibi-edn/sinks "20-actors/okibi/kotoba/seed.edn"))
   (toi-claim/from-nodes (toi-edn/jobs "20-actors/toi/kotoba/seed.edn")
                         (toi-edn/sites "20-actors/toi/kotoba/seed.edn"))
   (yudane-claim/from-offers (yudane-edn/offers "20-actors/yudane/kotoba/seed.edn"))))

;; ── the pipeline assembles ───────────────────────────────────────────────────

(deftest all-four-legs-emit-claims
  (let [cs (all-claims)
        actors (set (map :source-actor cs))]
    (is (= #{"tawami" "okibi" "toi" "yudane"} actors) "all four legs contribute claims")
    ;; tawami 12 + okibi 4 + toi 5 + yudane 4(consented) = 25
    (is (= 25 (count cs)) "expected claim count across the suite")))

;; ── mio verifies the pipeline ────────────────────────────────────────────────

(deftest mio-verifies-the-suite-and-accounts-flowrate
  (let [a (mio/analyze (all-claims))
        totals (get a "totals")]
    (is (pos? (get totals "verified_flowrate_score")) "the org Flowrate is positive")
    (is (>= (get totals "verified_claims") 20) "most claims verify (all five facts present)")
    (is (= 25 (get totals "total_claims")))))

(deftest no-cross-actor-double-counting
  ;; each leg namespaces its double-count-key, so there are NO collisions across the suite.
  (let [a (mio/analyze (all-claims))
        verdicts (map #(get % "verdict") (get a "claims"))]
    (is (zero? (count (filter #(= :rejected-double-count %) verdicts)))
        "namespaced keys → no cross-actor double counting")))

(deftest suite-spans-the-flow-classes
  (let [a (mio/analyze (all-claims))
        classes (set (map #(get % "flow_class") (get a "claims")))]
    ;; the four legs cover waste-heat, compute-routing, intention + the flex best-uses
    (is (contains? classes :waste-heat) "okibi")
    (is (contains? classes :compute-routing) "toi")
    (is (contains? classes :intention) "yudane")
    (is (some #{:peak-shave :renewable-absorb :flexibility} classes) "tawami flex best-uses")))

(deftest every-verified-claim-routes-to-reward
  (let [a (mio/analyze (all-claims))]
    (doseq [r (get a "claims")]
      (when (= :verified (get r "verdict"))
        (is (= :reward (get r "route")) (str (get r "id") " verified → reward"))))))

#?(:clj
   (when (= *file* (System/getProperty "babashka.file"))
     (let [{:keys [fail error]} (run-tests 'mio.methods.test-suite)]
       (System/exit (if (zero? (+ fail error)) 0 1)))))
