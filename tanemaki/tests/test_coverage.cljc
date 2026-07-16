(ns tanemaki.tests.test-coverage
  "tanemaki 種蒔き — coverage-report integrity tests (ADR-2606122001).
  1:1 Clojure port of tests/test_coverage.py."
  (:require [clojure.test :refer [deftest is run-tests]]
            [clojure.string :as str]
            [clojure.java.io :as io]
            [tanemaki.methods.analyze :as analyze]
            [tanemaki.methods.coverage-report :as coverage]))

(def actor-dir (-> *file* io/file .getParentFile .getParentFile))
(def seed (io/file actor-dir "data" "seed-stewardship-graph.kotoba.edn"))

(defn load-seed [] (analyze/load-file* seed))

(deftest test-report-renders-and-invariants-hold
  (let [{:keys [nodes edges]} (load-seed)
        md (coverage/report nodes edges)]
    (is (str/starts-with? md "# tanemaki"))
    (is (str/includes? md "holds for all orgs") "G1 integrity line missing or violated")
    (is (str/includes? md "all instruments on the allowlist") "G2 integrity line missing or violated")
    (is (str/includes? md "**1.00** ✓") "G4 rubric-sum line missing or violated")
    (is (str/includes? md "all named") "G5 evidence-source line missing or violated")
    (is (str/includes? md "all fictional") "G6 synthetic-seed line missing or violated")))

(deftest test-all-routes-exercised
  (let [{:keys [nodes edges]} (load-seed)
        md (coverage/report nodes edges)]
    (doseq [bucket ["propose" "insufficient-evidence" "excluded"]]
      (is (str/includes? md (str "| " bucket " |"))))
    (is (not (str/includes?
              (-> md (str/split #"## Route exercise") second (str/split #"##") first)
              "MISSING"))
        "a route lane is unexercised by the seed")))

(deftest test-every-criterion-sourced
  (let [{:keys [nodes edges]} (load-seed)
        sourced (set (for [e edges :when (= ":sourced-from" (get e ":en/kind"))]
                       (get e ":en/from")))]
    (doseq [[nid n] nodes
            :when (= ":criterion" (get n ":fs/kind"))]
      (is (contains? sourced nid)
          (str "criterion " nid " has no disclosed evidence source (G4)")))))

(deftest test-report-deterministic
  (let [{:keys [nodes edges]} (load-seed)]
    (is (= (coverage/report nodes edges) (coverage/report nodes edges)))))
