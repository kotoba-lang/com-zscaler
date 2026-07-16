(ns matsurigoto.methods.modules.test-credential-issue
  "test_credential_issue.py — conformance tests for the credential-issue module.
  1:1 Clojure port (stdlib unittest-style → clojure.test).

  The check-digit tests reproduce the published ICAO Doc 9303 worked examples exactly.
  The __main__ runner is omitted."
  (:require [clojure.test :refer [deftest is run-tests]]
            [matsurigoto.methods.modules.credential-issue :as P]))

;; canonical ICAO 9303 specimen line 2
(def SPECIMEN-L2 "L898902C36UTO7408122F1204159ZE184226B<<<<<10")

(deftest test-no-server-authority-document-unsigned
  (is (= P/SERVER-HELD-AUTHORITY false))
  (let [p (P/issue-passport "L898902C3" "UTO" "UTO" "ERIKSSON" "ANNA MARIA"
                            "740812" "F" "120415" "did:web:x" "ZE184226B")]
    (is (nil? (get-in p ["document" "sod"])))
    (is (nil? (get-in p ["document" "proof"])))))

(deftest test-icao-doc-number-check-digit
  (is (= (P/mrz-check-digit "L898902C3") "6")))

(deftest test-icao-dob-check-digit
  (is (= (P/mrz-check-digit "740812") "2")))

(deftest test-icao-expiry-check-digit
  (is (= (P/mrz-check-digit "120415") "9")))

(deftest test-filler-value-zero
  (is (= (P/mrz-check-digit "<<<<<<") "0")))

(deftest test-full-specimen-line2-reproduced
  (let [mrz (P/build-td3-mrz "L898902C3" "UTO" "UTO" "ERIKSSON" "ANNA MARIA"
                             "740812" "F" "120415" "ZE184226B")]
    (is (= (get mrz "line2") SPECIMEN-L2))
    (is (= (count (get mrz "line2")) 44))))

(deftest test-specimen-line1
  (let [mrz (P/build-td3-mrz "L898902C3" "UTO" "UTO" "ERIKSSON" "ANNA MARIA"
                             "740812" "F" "120415" "ZE184226B")
        base "P<UTOERIKSSON<<ANNA<MARIA"]
    (is (= (get mrz "line1") (str base (apply str (repeat (- 44 (count base)) "<")))))
    (is (= (count (get mrz "line1")) 44))))

(deftest test-validate-specimen-passes
  (is (= (P/validate-td3-line2 SPECIMEN-L2) true)))

(deftest test-validate-detects-corruption
  (let [bad (vec SPECIMEN-L2)
        bad (assoc bad 0 (if (not= (nth bad 0) \X) \X \Y))]
    (is (= (P/validate-td3-line2 (apply str bad)) false))))

(deftest test-validate-rejects-wrong-length
  (is (= (P/validate-td3-line2 "TOO SHORT") false)))

(deftest test-roundtrip-arbitrary-passport-validates
  (let [p (P/issue-passport "AB1234567" "JPN" "JPN" "YAMADA" "TARO"
                            "900101" "M" "300101" "did:web:etz")]
    (is (= (P/validate-td3-line2 (get-in p ["mrz" "line2"])) true))))

(deftest test-bad-country-code-raises
  (is (thrown? #?(:clj Exception :cljs js/Error)
               (P/build-td3-mrz "X" "JP" "JPN" "A" "B" "900101" "M" "300101"))))

(deftest test-solve-is-gated-at-r0
  (is (thrown? #?(:clj Exception :cljs js/Error) (P/solve))))

#?(:clj (defn -main [& _] (run-tests 'matsurigoto.methods.modules.test-credential-issue)))
