(ns aburi.tests.test-coverage
  "aburi 炙り — coverage-report tests (ADR-2606161630). 1:1 Clojure port of tests/test_coverage.py."
  (:require [clojure.test :refer [deftest is run-tests]]
            [clojure.string :as str]
            [clojure.set]
            [clojure.java.io :as io]
            [aburi.methods.analyze :as analyze]
            [aburi.methods.coverage-report :as coverage]))

(def actor-dir (-> *file* io/file .getParentFile .getParentFile))
(def seed (io/file actor-dir "data" "seed-tracker-exposure.kotoba.edn"))

(defn load-seed [] (analyze/load-file* seed))

(deftest test-report-renders-all-spines
  (let [{:keys [nodes edges]} (load-seed)
        md (coverage/report nodes edges)]
    (doseq [spine ["Surface-kind coverage" "Permission-kind coverage" "Collector-kind coverage"
                   "Collector-catalogue coverage" "Data-type-kind coverage" "Gap map"]]
      (is (str/includes? md spine) (str "coverage report missing spine: " spine)))))

(deftest test-every-collector-catalogue-bucket-is-a-real-provenance
  ;; G5: every catalogue bucket the report counts is a real public source (no invented source).
  (let [valid #{":exodus" ":apple-privacy" ":play-data-safety" ":sellers-json" ":iab"}]
    (doseq [k coverage/catalogs]
      (is (contains? valid k) (str "coverage tracks a non-public catalogue bucket: " k)))))

(deftest test-backbone-kinds-present
  ;; The covered backbone (the kinds the seed actually exercises) must be non-empty.
  (let [{:keys [nodes]} (load-seed)
        surf (set (for [n (vals nodes) :when (= ":surface" (get n ":organism/kind"))]
                    (get n ":surface/kind")))
        col (set (for [n (vals nodes) :when (= ":collector" (get n ":organism/kind"))]
                   (get n ":collector/kind")))]
    (is (clojure.set/subset? #{":search" ":social" ":app-store"} surf)
        (str "surface backbone thin: " surf))
    (is (clojure.set/subset? #{":ad-network" ":data-broker" ":analytics"} col)
        (str "collector backbone thin: " col))))
