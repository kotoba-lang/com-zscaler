(ns tokigusuri.tests.test-coverage
  "tokigusuri 時薬 — coverage-report tests (ADR-2606171300). Sibling of hokorobi tests/test_coverage."
  (:require [clojure.test :refer [deftest is run-tests]]
            [clojure.string :as str]
            [clojure.set]
            [clojure.java.io :as io]
            [tokigusuri.methods.analyze :as analyze]
            [tokigusuri.methods.coverage-report :as coverage]))

(def actor-dir (-> *file* io/file .getParentFile .getParentFile))
(def seed (io/file actor-dir "data" "seed-pharma-patent-graph.kotoba.edn"))

(defn load-seed [] (analyze/load-file* seed))

(deftest test-coverage-renders-and-is-honest
  (let [{:keys [nodes edges]} (load-seed)
        md (coverage/report nodes edges)]
    (is (str/includes? md "coverage of all marketed drugs is ~0 by design"))
    (is (str/includes? md "Gap map"))
    ;; both modalities + both ends of the cliff appear in a real seed
    (is (and (str/includes? md "small-molecule")
             (str/includes? md "biologic")
             (str/includes? md "off-patent")
             (str/includes? md "on-patent")))))

(deftest test-cliff-both-ends-present
  (let [{:keys [nodes]} (load-seed)
        statuses (set (for [n (vals nodes)
                            :when (= ":drug" (get n ":organism/kind"))]
                        (get n ":drug/exclusivity-status")))
        modalities (set (for [n (vals nodes)
                              :when (= ":drug" (get n ":organism/kind"))]
                          (get n ":drug/modality")))]
    (is (clojure.set/subset? #{":on-patent" ":off-patent"} statuses)
        (str "the patent cliff needs both ends: " statuses))
    (is (clojure.set/subset? #{":small-molecule" ":biologic"} modalities)
        (str "missing a modality (small-molecule + biologic): " modalities))))
