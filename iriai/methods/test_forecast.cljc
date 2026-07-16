#!/usr/bin/env bb
;; iriai 入会 — predictive-maintenance forecast tests.
;; Run:  bb --classpath 20-actors 20-actors/iriai/methods/test_forecast.cljc
(ns iriai.methods.test-forecast
  (:require [iriai.methods.iriai-edn :as ie]
            [iriai.methods.forecast :as fc]
            [clojure.string :as str]
            [clojure.test :refer [deftest is run-tests]]))

(def seed-path "20-actors/iriai/kotoba/seed.edn")
(defn- assets [] (vec (filter #(= (:type %) :asset) (ie/parse-edn (slurp seed-path)))))
(defn- by-id [id] (first (filter #(= id (:id %)) (assets))))
(defn- lead [id] (:lead-time-years (fc/forecast (by-id id))))

;; ── an already-degraded / unsafe asset needs action NOW (lead 0) ───────────────
(deftest degraded-assets-due-now
  (is (= 0 (lead "xfmr-decom-1")) "aged-out transformer → now")
  (is (= 0 (lead "gas-shima-2"))  "leaking gas main → now")
  (is (= 0 (lead "road-bridge-1"))"under-rated bridge → now")
  (is (= 0 (lead "pipe-shima-3")) "worn water main → now"))

;; ── a healthy asset has a POSITIVE lead-time or is beyond the horizon ──────────
(deftest healthy-assets-have-lead
  (let [f (fc/forecast (by-id "pipe-kibou-1"))]
    (is (and (:lead-time-years f) (pos? (:lead-time-years f))) "healthy water main: years of life left"))
  (let [f (fc/forecast (by-id "xfmr-midori-1"))]
    (is (:beyond-horizon? f) "lightly-loaded young transformer ages past the 40-yr horizon")))

;; ── lead-time is MONOTONE: an OLDER copy of an asset is due sooner ─────────────
(deftest lead-time-monotone-in-age
  (let [a (by-id "pipe-kibou-1")
        younger (fc/forecast a)
        older   (fc/forecast (update a :age-years + 15))]
    (is (or (:beyond-horizon? younger)
            (>= (or (:lead-time-years younger) 1e9)
                (or (:lead-time-years older) 1e9)))
        "ageing the asset can only bring the action sooner")))

;; ── the schedule is soonest-first; due-now / beyond-horizon tallied ───────────
(deftest schedule-ordering
  (let [s (fc/schedule (assets))
        leads (map #(if (:beyond-horizon? %) 1e9 (:lead-time-years %)) (get s "forecasts"))]
    (is (= leads (sort leads)) "soonest-first")
    (is (pos? (get s "due-now")))
    (is (= 11 (get s "count")))
    (is (pos? (get s "beyond-horizon")) "some assets outlive the horizon")))

;; ── G5 structural: a forecast is SIMULATION ONLY — no actuation attr ───────────
(deftest g5-forecast-simulation-only
  (let [edn (str (fc/datoms (fc/schedule (assets))))]
    (is (not (str/includes? edn ":iriai/actuate")))
    (is (not (str/includes? edn ":iriai.forecast/dispatch")))
    (is (str/includes? edn ":iriai.forecast/lead-time-years"))
    (is (str/includes? edn ":iriai.forecast/model-based"))))

(deftest report-declares-model-based-future-care
  (let [md (fc/render-report (fc/schedule (assets)))]
    (is (str/includes? md "MODEL projection"))
    (is (str/includes? md "SIMULATION ONLY"))
    (is (str/includes? md "never an actuation"))))

#?(:clj
   (when (= *file* (System/getProperty "babashka.file"))
     (let [{:keys [fail error]} (run-tests 'iriai.methods.test-forecast)]
       (System/exit (if (zero? (+ fail error)) 0 1)))))
