#!/usr/bin/env bb
;; 委 yudane — analyze/consent/datoms tests (incl. consent + privacy invariants).
;; Run:  bb --classpath 20-actors 20-actors/yudane/methods/test_analyze.cljc
(ns yudane.methods.test-analyze
  (:require [yudane.methods.yudane-edn :as ye]
            [yudane.methods.analyze :as a]
            [clojure.string :as str]
            [clojure.test :refer [deftest is run-tests]]))

(def seed-path "20-actors/yudane/kotoba/seed.edn")
(defn- offers [] (ye/offers seed-path))
(defn- row [id] (first (filter #(= id (get % "id")) (get (a/analyze (offers)) "offers"))))

;; ── G1 consent-bound ─────────────────────────────────────────────────────────

(deftest no-capability-refused
  (is (= :refused-no-capability (get (row "no-cap-01") "verdict")) "no capability → nothing read")
  (is (= 0.0 (get (row "no-cap-01") "flex_kwh"))))

(deftest expired-capability-refused
  (is (= :refused-expired (get (row "expired-cap-01") "verdict")) "expired leash → refused"))

(deftest non-reciprocal-refused
  (is (= :refused-not-reciprocal (get (row "non-reciprocal-01") "verdict")) "Rider §2(c) symmetry"))

;; ── G2 content-free / k-anonymity ────────────────────────────────────────────

(deftest below-k-anon-refused
  ;; a cohort of 3 is refused — an individual could be identified.
  (is (= :refused-below-k-anon (get (row "small-cohort-01") "verdict")))
  (is (= 0.0 (get (row "small-cohort-01") "flex_kwh"))))

(deftest consented-offer-translates
  (is (= :consented (get (row "presence-wfh-01") "verdict")))
  (is (= :flex-offer (get (row "presence-wfh-01") "route")))
  (is (pos? (get (row "presence-wfh-01") "flex_kwh"))))

(deftest cohort-of-8-passes-k-anon
  ;; k-anon floor is 5 — a cohort of 8 (≥5) is permitted.
  (is (= :consented (get (row "schedule-office-01") "verdict"))))

;; ── totals + structure ───────────────────────────────────────────────────────

(deftest consented-flex-equals-sum
  (let [r (a/analyze (offers))
        consented (filter #(= :consented (get % "verdict")) (get r "offers"))
        manual (reduce + 0.0 (map #(get % "flex_kwh") consented))]
    (is (= 4 (count consented)) "four offers consent in the seed")
    (is (= 4 (get-in r ["totals" "refused_count"])) "four are refused (one per gate)")
    (is (< (Math/abs (- manual (get-in r ["totals" "consented_flex_kwh"]))) 1e-9)
        "ledger flex = sum of consented flex")))

(deftest best-use-mapping
  (is (= :peak-shave (get (row "presence-wfh-01") "best_use")))
  (is (= :renewable-absorb (get (row "defer-laundry-01") "best_use"))))

;; ── datom emission + G1/G2/G4 unrepresentability (the degeneration series) ───

(deftest datoms-flagged-derived-and-content-free
  (let [edn (a/render-datoms (a/analyze (offers)))]
    (is (str/includes? edn ":yudane/derived"))
    (is (str/includes? edn ":yudane.obs/verdict"))
    (is (str/includes? edn ":yudane.obs/flex-kwh"))
    (is (str/includes? edn ":yudane.ledger/consented-flex-kwh"))
    ;; cohort-size is an AGGREGATE (permitted); per-person data is not.
    (is (str/includes? edn ":yudane.offer/cohort-size"))))

(deftest g1-g2-g4-degeneration-series-unrepresentable
  (let [edn (a/render-datoms (a/analyze (offers)))]
    (is (not (str/includes? edn ":yudane.person")))   ; G2: no per-person field
    (is (not (str/includes? edn "intent-content")))   ; G2: no per-person intent text
    (is (not (str/includes? edn ":yudane.person/score"))) ; no social-credit score
    (is (not (str/includes? edn "denunciation")))     ; no 隣組/Stasi denunciation
    (is (not (str/includes? edn ":yudane/surveil")))  ; no asymmetric/covert watching
    (is (not (str/includes? edn ":yudane/dispatch"))) ; G4: translation only, never controls
    (is (not (str/includes? edn ":yudane/trade")))))

(deftest report-is-content-free-and-consent-bound
  (let [md (a/render-report (a/analyze (offers)) (a/coverage (offers)))]
    (is (str/includes? md "content-free"))
    (is (str/includes? md "CONSENTED"))
    (is (str/includes? md "k-anonymity"))
    (is (str/includes? md "degeneration series"))))

;; ── coverage ─────────────────────────────────────────────────────────────────

(deftest coverage-gap-nonneg
  (let [cov (a/coverage (offers))]
    (is (= 4 (count (get cov "by_class"))))
    (is (every? #(>= (get % "gap") 0) (get cov "by_class")))))

#?(:clj
   (when (= *file* (System/getProperty "babashka.file"))
     (let [{:keys [fail error]} (run-tests 'yudane.methods.test-analyze)]
       (System/exit (if (zero? (+ fail error)) 0 1)))))
