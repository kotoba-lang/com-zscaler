(ns mimamori.tests.test-coverage
  "mimamori coverage tests — aggregate-only (G5): no DID ever appears in the report.
  Port of tests/test_coverage.py."
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.test :refer [deftest is]]
            [mimamori.methods.bond :as bond]
            [mimamori.methods.coverage-report :as cov]))

(defn- seed []
  (bond/load-seed-file (io/resource "mimamori/data/seed-mimamori-bonds.json")))

(deftest aggregates-on-seed
  (let [c (cov/coverage (seed))]
    (is (= 12 (:members-total c)))
    ;; active keepers: bet (by aleph), gimel (by bet), he (by vav after relay) = 3
    (is (= 3 (:with-keeper c)))
    (is (= 1 (:offers-pending c)))        ;; het (offer from zayin, unanswered)
    (is (= 8 (:unkept-count c)))          ;; the gap, counted not named
    (is (= 1 (:relays c)))
    (is (= (:members-total c)
           (+ (:with-keeper c) (:offers-pending c) (:unkept-count c))))))

(deftest g5-no-did-in-report
  (let [rep (cov/render (cov/coverage (seed)))]
    (is (not (str/includes? rep "did:")))        ;; no person named, ever
    (is (not (str/includes? rep "fictional")))
    (is (str/includes? rep "unkept"))))          ;; the gap IS reported — as a count

(deftest deterministic-report
  (let [s (seed)]
    (is (= (cov/render (cov/coverage s)) (cov/render (cov/coverage s))))))
