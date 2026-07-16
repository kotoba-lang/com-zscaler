#!/usr/bin/env bb
(ns umisachi.tests.test-coverage
  "umisachi 海幸 — coverage report + Datom-emit invariants (ADR-2606074200).

  Run:  bb --classpath 20-actors 20-actors/umisachi/tests/test_coverage.clj"
  (:require [umisachi.methods.analyze :as a]
            [umisachi.methods.coverage-report :as cov]
            [umisachi.methods.datom-emit :as de]
            [clojure.java.io :as io]
            [clojure.edn :as edn]
            [clojure.string :as str]
            [clojure.test :refer [deftest is run-tests]]))

(def ^:private this-file *file*)
(defn- seed-path []
  (-> this-file io/file .getAbsoluteFile .getParentFile .getParentFile
      (io/file "data" "seed-umisachi-graph.kotoba.edn")))
(defn- graph [] (a/load-file (seed-path)))

;; ── coverage ─────────────────────────────────────────────────────────────────
(deftest coverage-report-names-the-spines
  (let [[nodes edges] (graph)
        md (cov/report nodes edges)]
    (is (str/includes? md "Stock-status spread"))
    (is (str/includes? md "Sustainable fishery-method coverage"))
    (is (str/includes? md "Market-access coverage"))
    (is (str/includes? md "Pressure-kind coverage"))))

(deftest coverage-gap-map-is-honest
  (let [[nodes edges] (graph)
        md (cov/report nodes edges)]
    ;; the seed has no :unknown stock-status and no :trap / :hand-gather method → named as gaps
    (is (str/includes? md "Gap map"))
    (is (str/includes? md "unknown"))
    (is (str/includes? md "trap"))))

;; ── Datom emit ───────────────────────────────────────────────────────────────
(deftest emitted-log-is-valid-edn
  (let [[nodes edges] (graph)
        res (a/analyze nodes edges)
        log (de/emit nodes edges res 1)
        forms (edn/read-string log)]                ; the whole [...] vector must parse
    (is (vector? forms))
    (is (every? vector? forms))
    ;; every datom is [e a v tx op]
    (is (every? #(= 5 (count %)) forms))
    (is (every? #(#{:add :derived} (nth % 4)) forms))))

(deftest emitted-log-has-ground-and-derived
  (let [[nodes edges] (graph)
        res (a/analyze nodes edges)
        forms (edn/read-string (de/emit nodes edges res 1))
        ops (group-by #(nth % 4) forms)]
    (is (pos? (count (:add ops))))
    (is (pos? (count (:derived ops))))
    ;; derived readouts carry the three edge-primary integrals
    (let [attrs (set (map second (:derived ops)))]
      (is (contains? attrs :bond/provisioning-nourishment))
      (is (contains? attrs :bond/depletion-load))
      (is (contains? attrs :bond/depletion-imposed)))))

(deftest g4-factory-fishing-never-a-method-in-the-log
  ;; in the durable log, :factory-fishing appears ONLY as a :pressure/kind, never a :fishery/method
  (let [[nodes edges] (graph)
        res (a/analyze nodes edges)
        forms (edn/read-string (de/emit nodes edges res 1))
        methods (->> forms (filter #(= :fishery/method (second %))) (map #(nth % 2)) set)]
    (is (not (contains? methods :factory-fishing)))
    (is (not-any? a/forbidden-methods methods))
    ;; but the harm IS disclosed as a pressure
    (let [pressures (->> forms (filter #(= :pressure/kind (second %))) (map #(nth % 2)) set)]
      (is (contains? pressures :factory-fishing)))))

(when (= *file* (System/getProperty "babashka.file"))
  (let [{:keys [fail error]} (run-tests 'umisachi.tests.test-coverage)]
    (System/exit (if (zero? (+ fail error)) 0 1))))
