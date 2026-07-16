(ns meibo.tests.test-coverage
  "meibo 名簿 — coverage-report tests (G10, ADR-2607062200). clojure.test."
  (:require [clojure.test :refer [deftest is run-tests]]
            [clojure.string :as str]
            [meibo.methods.coverage-report :as c]))

(deftest test-coverage-counts-10-jurisdictions
  (is (= (get (c/coverage) "covered_count") 10)))

(deftest test-worklist-excludes-covered
  (let [cov (c/coverage)]
    (doseq [j (get cov "jurisdictions")]
      (is (not (some #{j} (get cov "worklist_remaining")))))))

(deftest test-report-renders-table
  (let [out (c/report (c/coverage))]
    (is (string? out))
    (is (str/includes? out "| juris | entries |"))
    (is (str/includes? out ":jp"))))
