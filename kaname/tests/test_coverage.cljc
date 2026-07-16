(ns kaname.tests.test-coverage
  "kaname 要 — coverage honesty tests (ADR-2606172100; G6). The synthetic seed joins no live
  mirror; empty domain layers + unjoined mirrors are named, never fabricated."
  (:require [clojure.test :refer [deftest is testing run-tests]]
            [clojure.string :as str]
            #?(:clj [clojure.java.io :as io])
            [kaname.methods.sos :as sos]
            [kaname.methods.coverage-report :as cov]))

#?(:clj (def actor-dir (-> *file* io/file .getParentFile .getParentFile)))
#?(:clj (def seed (io/file actor-dir "data" "seed-sos.kotoba.edn")))

#?(:clj
   (defn- cov- []
     (let [{:keys [nodes edges]} (sos/load-file* seed)]
       (cov/coverage nodes edges))))

(deftest test-counts
  (let [c (cov-)]
    (is (= 13 (:n-nodes c)))
    (is (= 20 (:n-edges c)))
    (is (pos? (get-in c [:by-kind ":sos/interface"])))))

(deftest test-empty-domain-layers-named
  (testing "the seed populates 8 of 10 domains; ecology + wellbecoming are honestly named empty"
    (let [c (cov-)]
      (is (contains? (set (:missing-domains c)) ":ecology"))
      (is (contains? (set (:missing-domains c)) ":wellbecoming")))))

(deftest test-mirror-provenance
  (testing "mirrors joined are surfaced; unjoined mirrors are named (no fabricated live join)"
    (let [c (cov-)]
      (is (contains? (set (:mirrors-joined c)) ":chie"))
      (is (contains? (set (:mirrors-joined c)) ":tsumugi"))
      ;; no live join exists — at least one lineage mirror is honestly unjoined
      (is (seq (:unjoined-mirrors c))))))

(deftest test-report-honesty
  (testing "the report disclaims live coverage"
    (let [md (cov/report-md (cov-))]
      (is (str/includes? md "no fabricated coverage"))
      (is (str/includes? md "G7")))))
