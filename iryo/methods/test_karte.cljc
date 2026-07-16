(ns iryo.methods.test-karte
  (:require [clojure.test :refer [deftest is]]
            [iryo.methods.karte :as karte]))

(defn- make-test-karte []
  {:patient (karte/make-patient "did:web:patient.iryo.etzhayyim.com:abc123" "M" 1980 "bafy...phi")
   :insurance (karte/make-insurance "01130012" 0.3 "honnin" nil [])
   :diagnoses [(karte/make-diagnosis "4019005" "I10" "高血圧症" "2026-01-10" "継続" true)]
   :notes []})

(deftest test-public-meta-is-codes-only-no-phi
  (let [meta (karte/public-meta (make-test-karte))]
    (karte/assert-no-phi! meta)
    (is (.startsWith (str (:patient-did meta)) "did:web:"))
    (is (= "I10" (:icd10 (first (:diagnoses meta)))))
    (is (not (contains? meta :name)))
    (is (not (contains? meta :dob)))))

(deftest test-assert-no-phi-rejects-smuggled-plaintext
  (is (thrown? clojure.lang.ExceptionInfo
               (karte/assert-no-phi! {:patient-did "did:.." :name "山田太郎"}))))

(deftest test-assert-no-phi-rejects-phi-in-diagnosis
  (is (thrown? clojure.lang.ExceptionInfo
               (karte/assert-no-phi! {:diagnoses [{:icd10 "I10" :note "本人談"}]}))))

(deftest test-soap-note-requires-encrypted-cid
  (is (thrown? clojure.lang.ExceptionInfo
               (karte/make-soap-note "2026-06-07" "did:web:dr" "")))
  (let [ok (karte/make-soap-note "2026-06-07" "did:web:dr" "bafy...soap")]
    (is (seq (:encrypted-cid ok)))))

(deftest test-rotating-pseudonym-changes-per-period
  (let [a (karte/rotating-pseudonym-did "patient-secret" "2026-06")
        b (karte/rotating-pseudonym-did "patient-secret" "2026-07")]
    (is (not= a b))
    (is (= a (karte/rotating-pseudonym-did "patient-secret" "2026-06")))
    (is (.startsWith a "did:web:patient.iryo.etzhayyim.com:"))))
