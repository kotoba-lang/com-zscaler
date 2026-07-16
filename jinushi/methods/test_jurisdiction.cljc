(ns jinushi.methods.test-jurisdiction
  "jinushi 地主 — per-jurisdiction public-record gate tests."
  (:require [clojure.test :refer [deftest is run-tests]]
            [jinushi.methods.jurisdiction :as j]))

(deftest test-registry-enums-valid
  (doseq [[cc r] j/registry]
    (is (re-matches #"[A-Z]{2}" cc) (str cc " is ISO-2"))
    (is (contains? #{:public :restricted :unknown} (:access r)) (str cc " access enum"))
    (is (contains? #{:yes :priced :per-parcel :no :unknown} (:bulk r)) (str cc " bulk enum"))
    (is (contains? #{:visible :restricted :unknown} (:person-names r)) (str cc " names enum"))))

(deftest test-unknown-degrades-honestly
  (let [u (j/jurisdiction "ZZ")]
    (is (= :unknown (:access u)) "absent jurisdiction → :unknown")
    (is (false? (j/persons-bulk-ingestable? "ZZ")) "unknown jurisdiction NEVER bulk-ingests persons")
    (is (= :unknown (j/persons-mode "ZZ")))))

(deftest test-public-bulk-ingestable
  ;; Sweden / US: public + bulk + names visible → persons bulk-ingestable
  (is (j/persons-bulk-ingestable? "SE") "SE public+bulk")
  (is (j/persons-bulk-ingestable? "US") "US public+bulk")
  (is (= :bulk-public (j/persons-mode "SE"))))

(deftest test-restricted-not-ingestable
  ;; Germany Grundbuch: legitimate-interest only → restricted, NOT ingestable
  (is (false? (j/persons-bulk-ingestable? "DE")) "DE restricted")
  (is (= :restricted (j/persons-mode "DE")))
  ;; France: owner names restricted even though parcels open
  (is (false? (j/persons-bulk-ingestable? "FR")) "FR owner-names restricted")
  (is (contains? #{:names-restricted :restricted} (j/persons-mode "FR"))))

(deftest test-per-parcel-not-bulk
  ;; Japan / Korea: anyone may obtain per-parcel, but no open bulk → not bulk-ingestable
  (is (false? (j/persons-bulk-ingestable? "JP")) "JP per-parcel only, not bulk")
  (is (= :per-parcel-only (j/persons-mode "JP")))
  (is (= :per-parcel-only (j/persons-mode "KR"))))

(when (= *file* (System/getProperty "babashka.file"))
  (let [{:keys [fail error]} (run-tests 'jinushi.methods.test-jurisdiction)]
    (System/exit (+ (or fail 0) (or error 0)))))
