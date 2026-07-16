(ns meibo.tests.test-directory
  "meibo 名簿 — directory-registry tests (ADR-2607062200). clojure.test."
  (:require [clojure.test :refer [deftest is run-tests]]
            [clojure.string :as str]
            [meibo.methods.directory :as dir]))

(deftest test-10-jurisdictions-covered
  (is (= (count (dir/jurisdictions-covered)) 10))
  (is (= (set (dir/jurisdictions-covered))
         #{":jp" ":us" ":uk" ":de" ":kr" ":fr" ":au" ":ca" ":it" ":es"})))

(deftest test-every-entry-verified-https-url
  (doseq [d (dir/load-directory)]
    (is (str/starts-with? (get d ":dir/url") "https://"))
    (is (some? (get d ":dir/kind")))
    (is (some? (get d ":dir/label")))))

(deftest test-every-jurisdiction-has-bar-association
  (doseq [j (dir/jurisdictions-covered)]
    (is (some #(= (get % "kind") ":bar-association") (dir/by-jurisdiction j))
        (str j " missing a :bar-association entry"))))

(deftest test-jp-flags-gyoseishoshi-court-submission-limit
  (let [jp (dir/by-jurisdiction ":jp")
        gyosei (some #(when (= (get % "id") "dir:jp-gyoseishoshi") %) jp)]
    (is (some? gyosei))
    (is (str/includes? (get gyosei "note") "訴訟書類"))))

(deftest test-uncovered-jurisdiction-degrades-empty
  (is (= (dir/by-jurisdiction ":br") [])))

(deftest test-institution-level-only-no-pii-fields
  ;; G1 — no per-individual field names anywhere in the schema
  (doseq [d (dir/load-directory)]
    (is (nil? (get d ":dir/attorney-name")))
    (is (nil? (get d ":dir/bar-number")))))

(def valid-disclosure-bases
  #{":statutory-mandatory" ":mandatory-registration-public-by-practice"
    ":voluntary-opt-in" ":varies-by-subunit" ":unconfirmed"})

(deftest test-disclosure-basis-present-for-professional-registries
  ;; every :bar-association / :licensed-scrivener / :insolvency-practitioner-register
  ;; entry must carry a researched (never-guessed, G10) disclosure-basis + a
  ;; note citing the actual finding — :court-locator entries are exempt (a
  ;; court locator isn't a professional-registry disclosure question)
  (doseq [d (dir/load-directory)
          :when (contains? #{":bar-association" ":licensed-scrivener" ":insolvency-practitioner-register"}
                            (get d ":dir/kind"))]
    (is (contains? valid-disclosure-bases (get d ":dir/disclosure-basis"))
        (str (get d ":dir/id") " missing/invalid :dir/disclosure-basis"))
    (is (string? (get d ":dir/disclosure-note"))
        (str (get d ":dir/id") " missing :dir/disclosure-note")))
  (doseq [d (dir/load-directory)
          :when (= (get d ":dir/kind") ":court-locator")]
    (is (nil? (get d ":dir/disclosure-basis")))))

(deftest test-jp-bengoshi-is-voluntary-opt-in
  ;; the specific finding that motivated this field: JFBA's own ひまわりサーチ
  ;; disclaims responsibility for listing content — opt-in, not a statutory
  ;; public-disclosure mandate, unlike DE/FR/UK-solicitors below.
  (let [jp (dir/by-jurisdiction ":jp")
        bengoshi (some #(when (= (get % "id") "dir:jp-bengoshi") %) jp)]
    (is (= (get bengoshi "disclosure_basis") ":voluntary-opt-in"))))

(deftest test-de-fr-uk-solicitors-are-statutory-mandatory
  (doseq [[juris id] [[":de" "dir:de-anwaltsverzeichnis"]
                      [":fr" "dir:fr-avocats"]
                      [":uk" "dir:uk-solicitors-register"]]]
    (let [entries (dir/by-jurisdiction juris)
          e (some #(when (= (get % "id") id) %) entries)]
      (is (= (get e "disclosure_basis") ":statutory-mandatory")
          (str id " expected :statutory-mandatory")))))
