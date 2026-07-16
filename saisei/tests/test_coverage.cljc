(ns saisei.tests.test-coverage
  "saisei 再生 — coverage-report tests (G10, ADR-2607061800). clojure.test."
  (:require [clojure.test :refer [deftest is run-tests]]
            [clojure.string :as str]
            [saisei.methods.coverage-report :as c]))

(deftest test-coverage-counts-4-jurisdictions
  (let [cov (c/coverage)]
    (is (= (get cov "covered_count") 4))
    (is (= (set (get cov "jurisdictions")) #{":jp" ":us" ":uk" ":de"}))))

(deftest test-worklist-excludes-covered
  (let [cov (c/coverage)]
    (doseq [j (get cov "jurisdictions")]
      (is (not (some #{j} (get cov "worklist_remaining")))))))

(deftest test-named-gaps-non-empty
  (let [cov (c/coverage)]
    (is (seq (get cov "named_gaps")))))

(deftest test-report-renders-table
  (let [out (c/report (c/coverage))]
    (is (string? out))
    (is (str/includes? out "| juris | procedures |"))
    (is (str/includes? out ":jp"))))
