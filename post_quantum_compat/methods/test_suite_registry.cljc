(ns post-quantum-compat.methods.test-suite-registry
  "post_quantum-compat — suite/migration registry tests (ADR-2606111300).
  1:1 Clojure port of tests/test_suite_registry.py — asserts the paper's §7 coverage
  EMPIRICALLY: every Shor-vulnerable layer carries an accounted status, FIPS 203/204 constants +
  draft multicodecs match the landed @etzhayyim/sdk + did-web implementations, Mosca/Grover
  helpers reproduce the paper's headline numbers, and Datom emit is deterministic + ground/derived
  stratified. Tests the cljc-twinned suite + datom-emit modules (no network, pure)."
  (:require [clojure.test :refer [deftest is]]
            [clojure.set :as set]
            [clojure.string :as str]
            [post-quantum-compat.methods.suite :as suite]
            [post-quantum-compat.methods.datom-emit :as datom-emit]))

(deftest test-registry-nontrivial-and-fully-accounted
  (is (>= (count suite/LAYERS) 10))
  (let [accounted (set/union suite/MIGRATION-DONE suite/GATED)]
    (doseq [layer suite/LAYERS]
      (let [status (get layer ":layer/status")]
        (is (contains? accounted status)
            (str (get layer ":layer/id") " has unaccounted status " status))))))

(deftest test-every-shor-layer-is-migrated-or-explicitly-gated
  (doseq [layer suite/LAYERS]
    (when (suite/shor-applies layer)
      (let [s (get layer ":layer/status")]
        (is (or (= s ":migrated") (contains? suite/GATED s)) (get layer ":layer/id"))
        (when (contains? suite/GATED s)
          ;; a gated layer must say WHY (honesty: no silent debt)
          (is (get layer ":layer/note") (get layer ":layer/id")))))))

(deftest test-migrated-layers-carry-provenance
  (doseq [layer suite/LAYERS]
    (when (= (get layer ":layer/status") ":migrated")
      (is (get layer ":layer/adr") (get layer ":layer/id"))
      (is (get layer ":layer/pr") (get layer ":layer/id")))))

(deftest test-fips-constants-match-landed-implementation
  (let [kem (get-in suite/SUITES [":suite/pqh-v1" ":suite/kem"])
        sig (get-in suite/SUITES [":suite/pqh-v1" ":suite/sig"])]
    ;; FIPS 203 ML-KEM-768 / FIPS 204 ML-DSA-65 — same numbers the SDK tests + size-cost table use
    (is (= 1184 (get kem ":kem/pq-public-bytes")))
    (is (= 1088 (get kem ":kem/pq-ciphertext-bytes")))
    (is (= 1952 (get sig ":sig/pq-public-bytes")))
    (is (= 3309 (get sig ":sig/pq-signature-bytes")))
    ;; draft multicodec registrations (multiformats table)
    (is (= 0x120C (get kem ":kem/pq-multicodec")))
    (is (= 0x1211 (get sig ":sig/pq-multicodec")))))

(deftest test-grover-bound
  (is (= 128 (suite/grover-effective-bits 256)))
  (is (= 64 (suite/grover-effective-bits 128))))

(deftest test-mosca-inequality-matches-paper
  ;; etzhayyim parameters from §6: x≈30 (permanent public ciphertext),
  ;; y≈4 (50+ actor rollout), z≈15 (median CRQC) → act now.
  (let [r (suite/mosca 30 4 15)]
    (is (= true (get r ":mosca/act-now")))
    (is (= -19 (get r ":mosca/slack-years"))))
  ;; sanity inversion: a distant CRQC removes the urgency
  (is (= false (get (suite/mosca 5 2 100) ":mosca/act-now"))))

(deftest test-coverage-readout-is-honest
  (let [cov (suite/coverage-report)]
    (is (= 0 (get cov ":coverage/unknown")))
    (is (>= (get cov ":coverage/shor-vulnerable") 6))
    (is (>= (get cov ":coverage/migrated") 3))
    (is (< 0.0 (get cov ":coverage/migrated-fraction") 1.0)
        "claiming 100% would be dishonest while gated layers remain")
    (is (seq (get cov ":coverage/gated-ids")) "gated layers must be enumerated, not hidden")))

(deftest test-datom-emit-deterministic-and-stratified
  (let [a (datom-emit/emit 7)
        b (datom-emit/emit 7)]
    (is (= a b) "emit must be byte-identical across runs")
    (is (str/includes? a "[:layer/key-wrap :layer/status :migrated 7 :add]"))
    ;; mirror python a.partition(";; ── DERIVED") → (ground, sep, derived)
    (let [marker ";; ── DERIVED"
          i (str/index-of a marker)
          ground (subs a 0 i)
          derived (subs a i)]
      (is (not (str/includes? ground ":pq/coverage")) "derived readouts must not be ground datoms")
      (is (str/includes? derived ":pq/is-transient true")))))
