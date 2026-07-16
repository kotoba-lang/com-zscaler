#!/usr/bin/env bb
;; iryo 医療 — claim persistence (kotoba Datom log) tests.
;; Run: bb -cp 20-actors:20-actors/kotodama/src 20-actors/iryo/methods/test_kotoba.cljc
(ns iryo.methods.test-kotoba
  (:require [clojure.test :refer [deftest is testing run-tests]]
            [clojure.java.io :as io]
            [iryo.methods.masters :as masters]
            [iryo.methods.rezept :as rezept]
            [iryo.methods.receden :as receden]
            [iryo.methods.karte :as karte]
            [iryo.methods.kotoba :as kotoba]))

;; ── test fixtures ─────────────────────────────────────────────────────────────
(defn- tmp [] (str (System/getProperty "java.io.tmpdir") "/iryo-kotoba-test-" (gensym) ".edn"))

(defn- make-test-encounter []
  {:futan-wari 0.3
   :acts [{:code "111000110" :count 1}
          {:code "112011010" :count 1}
          {:code "160008010" :count 1}]
   :prescriptions [{:shikibetsu "21" :days 7
                    :drugs [{:code "620003991" :amount 1}]}]})

(defn- make-test-karte []
  (let [pat (karte/make-patient "did:web:patient.iryo.etzhayyim.com:testpseudo"
                                "F" 1975 "bafkreidummy")]
    (let [ins (karte/make-insurance "06270013" 0.3 "honnin" "ウ" [])]
      (karte/make-karte pat ins [] []))))

(defn- compute-rez []
  (let [m (masters/load)
        enc (make-test-encounter)]
    (rezept/compute enc m)))

(defn- compute-rows []
  (let [m (masters/load)
        enc (make-test-encounter)
        rez (rezept/compute enc m)
        krt (make-test-karte)
        inst (receden/make-institution "1" "13")]
    (receden/build-receden inst krt rez)))

;; ── CID determinism ──────────────────────────────────────────────────────────
(deftest tx-cid-deterministic-and-content-sensitive
  (let [d1 [(kotoba/add "iryo-claim:abc" ":iryo.claim/total-ten" 441)]
        d2 [(kotoba/add "iryo-claim:abc" ":iryo.claim/total-ten" 999)]]
    (is (= (kotoba/tx-cid d1 "") (kotoba/tx-cid d1 "")))
    (is (not= (kotoba/tx-cid d1 "") (kotoba/tx-cid d2 "")))
    (is (clojure.string/starts-with? (kotoba/tx-cid d1 "") "b"))))

;; ── claim-datoms shape ────────────────────────────────────────────────────────
(deftest claim-datoms-shape
  (let [rez (compute-rez)
        rows (compute-rows)
        did "did:web:patient.iryo.etzhayyim.com:testpseudo"
        ds (kotoba/claim-datoms "claim-001" did rez rows)]
    (is (seq ds))
    (is (every? #(= ":db/add" (first %)) ds))
    ;; has the key claim attrs
    (let [attrs (set (map #(nth % 2) ds))]
      (is (contains? attrs ":iryo.claim/total-ten"))
      (is (contains? attrs ":iryo.claim/patient-did"))
      (is (contains? attrs ":iryo.claim/total-iryohi-yen"))
      (is (contains? attrs ":iryo.claim/patient-pay-yen")))
    ;; has line datoms
    (is (some #(clojure.string/starts-with? (second %) "iryo-line:") ds))))

;; ── PHI refusal invariant (G2 — CRITICAL, do NOT weaken) ────────────────────
(deftest phi-refused-on-name-attr
  (let [phi-datom [(kotoba/add "iryo-claim:x" ":iryo.claim/name" "田中太郎")]]
    (is (thrown? clojure.lang.ExceptionInfo (kotoba/assert-no-phi! phi-datom)))))

(deftest phi-refused-on-dob-attr
  (let [phi-datom [(kotoba/add "iryo-claim:x" ":iryo.claim/dob" "1975-01-01")]]
    (is (thrown? clojure.lang.ExceptionInfo (kotoba/assert-no-phi! phi-datom)))))

(deftest phi-refused-on-mrn-attr
  (let [phi-datom [(kotoba/add "iryo-claim:x" ":iryo.claim/mrn" "MRN-12345")]]
    (is (thrown? clojure.lang.ExceptionInfo (kotoba/assert-no-phi! phi-datom)))))

(deftest phi-refused-on-soap-free-text-attr
  (let [phi-datom [(kotoba/add "iryo-claim:x" ":iryo.claim/soap_s" "主訴: 頭痛")]]
    (is (thrown? clojure.lang.ExceptionInfo (kotoba/assert-no-phi! phi-datom)))))

(deftest phi-refused-on-hihokensha-attr
  (let [phi-datom [(kotoba/add "iryo-claim:x" ":iryo.claim/hihokensha" "12345678")]]
    (is (thrown? clojure.lang.ExceptionInfo (kotoba/assert-no-phi! phi-datom)))))

(deftest phi-predicate-detects-phi-substrings
  ;; "/name" in attribute path
  (is (kotoba/phi? ":iryo.claim/name"))
  ;; freestanding phi keys
  (is (kotoba/phi? "dob"))
  (is (kotoba/phi? "birthdate"))
  (is (kotoba/phi? "soap_s"))
  (is (kotoba/phi? "mrn"))
  ;; non-PHI
  (is (not (kotoba/phi? ":iryo.claim/total-ten")))
  (is (not (kotoba/phi? ":iryo.claim/patient-did")))
  (is (not (kotoba/phi? ":iryo.line/code"))))

(deftest claim-datoms-pass-phi-check
  ;; the computed claim datoms must never trigger the PHI guard
  (let [rez (compute-rez)
        rows (compute-rows)
        did "did:web:patient.iryo.etzhayyim.com:testpseudo"
        ds (kotoba/claim-datoms "claim-phi-check" did rez rows)]
    ;; this must NOT throw
    (is (nil? (kotoba/assert-no-phi! ds)))))

(deftest patient-did-is-persisted-not-name
  ;; the patient-did datom carries the pseudonymous DID, never a real name
  (let [rez (compute-rez)
        rows (compute-rows)
        did "did:web:patient.iryo.etzhayyim.com:testpseudo"
        ds (kotoba/claim-datoms "claim-did-check" did rez rows)
        did-datoms (filter #(= ":iryo.claim/patient-did" (nth % 2)) ds)]
    (is (= 1 (count did-datoms)))
    (is (clojure.string/starts-with? (nth (first did-datoms) 3) "did:web:"))))

;; ── persist! roundtrip ────────────────────────────────────────────────────────
(deftest persist-roundtrip
  (let [p (tmp)]
    (try
      (let [rez (compute-rez)
            rows (compute-rows)
            did "did:web:patient.iryo.etzhayyim.com:testpseudo"
            r1 (kotoba/persist! "claim-001" did rez rows p "tx-1" "2026-06-21")]
        (is (:persisted r1))
        (is (string? (:cid r1)))
        (is (pos? (:datom-count r1)))
        (let [txs (kotoba/read-log p)]
          (is (= 1 (count txs)))
          (is (some #(= ":iryo.claim/total-ten" (nth % 2))
                    (get (first txs) ":tx/datoms")))))
      (finally (io/delete-file p true)))))

;; ── idempotent re-persist ─────────────────────────────────────────────────────
(deftest persist-idempotent
  (let [p (tmp)]
    (try
      (let [rez (compute-rez)
            rows (compute-rows)
            did "did:web:patient.iryo.etzhayyim.com:testpseudo"
            r1 (kotoba/persist! "claim-001" did rez rows p "tx-1" "2026-06-21")
            r2 (kotoba/persist! "claim-001" did rez rows p "tx-2" "2026-06-21")]
        (is (:persisted r1))
        (is (not (:persisted r2)))
        (is (= :no-change (:reason r2)))
        ;; log still has only 1 tx
        (is (= 1 (count (kotoba/read-log p)))))
      (finally (io/delete-file p true)))))

;; ── verify-chain tamper-evident ────────────────────────────────────────────────
(deftest verify-chain-clean
  (let [p (tmp)]
    (try
      (let [rez (compute-rez)
            rows (compute-rows)
            did "did:web:patient.iryo.etzhayyim.com:testpseudo"
            _ (kotoba/persist! "claim-001" did rez rows p "tx-1" "2026-06-21")
            ;; second claim (different id → different tx)
            rez2 (assoc rez :total-ten 999)
            r2 (kotoba/persist! "claim-002" did rez2 rows p "tx-2" "2026-06-21")]
        (is (:persisted r2))
        (let [v (kotoba/verify-chain p)]
          (is (:ok v))
          (is (= 2 (:length v)))))
      (finally (io/delete-file p true)))))

(deftest verify-chain-tamper-detected
  (let [p (tmp)]
    (try
      (let [rez (compute-rez)
            rows (compute-rows)
            did "did:web:patient.iryo.etzhayyim.com:testpseudo"
            _ (kotoba/persist! "claim-001" did rez rows p "tx-1" "2026-06-21")]
        ;; tamper with the log
        (spit p (clojure.string/replace (slurp p) "total-ten" "tampered-ten"))
        (let [v (kotoba/verify-chain p)]
          (is (not (:ok v)))))
      (finally (io/delete-file p true)))))

;; ── chaining: head-cid advances ──────────────────────────────────────────────
(deftest chaining-head-cid-advances
  (let [p (tmp)]
    (try
      (let [rez (compute-rez)
            rows (compute-rows)
            did "did:web:patient.iryo.etzhayyim.com:testpseudo"
            _ (kotoba/persist! "claim-001" did rez rows p "tx-1" "2026-06-21")
            c1 (kotoba/head-cid p)
            rez2 (assoc rez :total-ten 999)
            _ (kotoba/persist! "claim-002" did rez2 rows p "tx-2" "2026-06-21")
            c2 (kotoba/head-cid p)]
        (is (not= c1 c2))
        (is (not= "" c1))
        (is (not= "" c2)))
      (finally (io/delete-file p true)))))

#?(:clj
   (when (= *file* (System/getProperty "babashka.file"))
     (let [{:keys [fail error]} (run-tests 'iryo.methods.test-kotoba)]
       (System/exit (if (zero? (+ fail error)) 0 1)))))
