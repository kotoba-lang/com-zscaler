(ns himawari.cells.module-assembly.test-state-machine
  "Tests for the himawari module_assembly state machine (ADR-2606021200 port).
  1:1 parity with cells/module_assembly/test_cell.py."
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.string :as str]
            [himawari.cells.module-assembly.state-machine :as sm]))

;; ── Happy-path fixture ──

(def ^:private valid-state
  {"moduleSerial"       "MOD-2026-0001"
   "cellBatchId"        "batch-001"
   "feedstockLotId"     "lot-2026-0001"
   "bomCid"             "bafy~sha256-bom"
   "ratedWp"            400
   "destinationActorDid" "did:web:etzhayyim.com:hikari"
   "recordedAt"         "2026-06-21T00:00:00Z"
   "attestingRobots"    [{"robotDid" "did:web:etzhayyim.com:himawari:robot:otete"
                           "role"     "framing"}
                          {"robotDid" "did:web:etzhayyim.com:himawari:robot:mimi"
                           "role"     "metrology"}]
   "flashIv"            {"voc" 0.68 "isc" 9.8 "pmax" 400}
   "elImage"            {"type" "el-image" "pixels" 256}})

(deftest test-happy-path-attestation
  (testing "Valid module produces a signed moduleAttestation"
    (let [result (sm/solve valid-state)
          attest (get result "moduleAttestation")]
      (is (nil? (get result "refused")))
      (is (some? attest))
      (is (= "MOD-2026-0001" (get attest "moduleSerial")))
      (is (= "batch-001" (get attest "cellBatchId")))
      (is (= "lot-2026-0001" (get attest "feedstockLotId")))
      (is (false? (get-in attest ["signature" "serverHeldKey"] true))))))

(deftest test-happy-path-attestation-type
  (testing "moduleAttestation has correct $type"
    (let [attest (get (sm/solve valid-state) "moduleAttestation")]
      (is (= "com.etzhayyim.himawari.moduleAttestation" (get attest "$type"))))))

(deftest test-happy-path-provenance-chain
  (testing "Provenance chain is complete"
    (let [result (sm/solve valid-state)
          prov   (get result "provenance")]
      (is (true? (get prov "complete")))
      (is (= "lot-2026-0001->batch-001->MOD-2026-0001" (get prov "link"))))))

(deftest test-happy-path-not-binned
  (testing "Module within flash tolerance is not binned"
    (let [result (sm/solve (assoc valid-state "measuredWp" 398))]
      (is (false? (boolean (get result "binned")))))))

(deftest test-happy-path-attesting-robots
  (testing "≥2 co-witnessing robots are recorded"
    (let [attest (get (sm/solve valid-state) "moduleAttestation")
          robots (get attest "attestingRobots")]
      (is (>= (count robots) 2))
      (is (every? #(get % "robotDid") robots))
      (is (every? #(get % "signature") robots)))))

(deftest test-g11-missing-serial-refused
  (testing "G11: module with no serial is refused"
    (let [result (sm/solve (assoc valid-state "moduleSerial" ""))]
      (is (true? (get result "refused")))
      (is (str/includes? (get result "reason") "G11")))))

(deftest test-g11-missing-cell-batch-refused
  (testing "G11: serial not bound to a cell batch is refused"
    (let [result (sm/solve (assoc valid-state "cellBatchId" ""))]
      (is (true? (get result "refused")))
      (is (str/includes? (get result "reason") "G11")))))

(deftest test-g11-missing-feedstock-lot-refused
  (testing "G11: serial not traceable to a feedstock lot is refused"
    (let [result (sm/solve (assoc valid-state "feedstockLotId" ""))]
      (is (true? (get result "refused")))
      (is (str/includes? (get result "reason") "G11")))))

(deftest test-g12-external-destination-refused
  (testing "G12: external destination is refused"
    (let [result (sm/solve (assoc valid-state "destinationActorDid" "did:web:external.example"))]
      (is (true? (get result "refused")))
      (is (str/includes? (get result "reason") "G12")))))

(deftest test-g12-no-destination-refused
  (testing "G12: module with no destination DID is refused"
    (let [result (sm/solve (assoc valid-state "destinationActorDid" ""))]
      (is (true? (get result "refused")))
      (is (str/includes? (get result "reason") "G12")))))

(deftest test-g12-non-hikari-internal-refused
  (testing "G12: internal DID for a non-hikari actor is refused"
    (let [result (sm/solve (assoc valid-state "destinationActorDid" "did:web:etzhayyim.com:mitsuho"))]
      (is (true? (get result "refused")))
      (is (str/includes? (get result "reason") "G12")))))

(deftest test-g11-too-few-robots-refused
  (testing "G11: fewer than 2 co-attesting robots is refused"
    (let [result (sm/solve (assoc valid-state "attestingRobots" [{"robotDid" "did:web:etzhayyim.com:himawari:robot:mimi"}]))]
      (is (true? (get result "refused")))
      (is (str/includes? (get result "reason") "G11")))))

(deftest test-g11-flash-binned-outside-tolerance
  (testing "G11: module flashing far outside rated Wp is binned"
    (let [result (sm/solve (assoc valid-state "measuredWp" 300))  ;; -25% of 400 Wp
          attest (get result "moduleAttestation")]
      (is (true? (boolean (get result "binned"))))
      (is (true? (boolean (get attest "binned"))))
      (is (str/includes? (str (get attest "binReason")) "G11")))))

(deftest test-recyclability-below-floor-flag
  (testing "G5: recyclabilityBps below floor sets recyclabilityBelowFloor flag"
    (let [result (sm/solve (assoc valid-state "recyclabilityBps" 8000))
          attest (get result "moduleAttestation")]
      (is (true? (get attest "recyclabilityBelowFloor"))))))

(deftest test-recyclability-above-floor-no-flag
  (testing "G5: recyclabilityBps at floor does not set recyclabilityBelowFloor"
    (let [result (sm/solve (assoc valid-state "recyclabilityBps" 9000))
          attest (get result "moduleAttestation")]
      (is (false? (boolean (get attest "recyclabilityBelowFloor")))))))

(deftest test-flash-iv-cid-deterministic
  (testing "flashIvCid is deterministic for identical flashIv payloads"
    (let [r1 (sm/solve valid-state)
          r2 (sm/solve valid-state)]
      (is (= (get-in r1 ["moduleAttestation" "flashIvCid"])
             (get-in r2 ["moduleAttestation" "flashIvCid"]))))))

(deftest test-signature-has-required-fields
  (testing "Module signature has alg, signedDigest, binding, signer, serverHeldKey"
    (let [sig (get-in (sm/solve valid-state) ["moduleAttestation" "signature"])]
      (is (some? (get sig "alg")))
      (is (some? (get sig "signedDigest")))
      (is (some? (get sig "binding")))
      (is (= "asher" (get sig "signer")))
      (is (false? (get sig "serverHeldKey"))))))
