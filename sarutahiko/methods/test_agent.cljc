(ns sarutahiko.methods.test-agent
  "sarutahiko 猿田彦 — agent handler tests.
   Faithful port of py/test_agent.py; expected values VERBATIM (no alterations).
   No network, no kotoba host, no LLM (Murakumo-only invariant untouched; G16)."
  (:require [clojure.test :refer [deftest is run-tests]]))

;; Require the implementation under test
(require '[sarutahiko.methods.agent :as agent])

;; ---------------------------------------------------------------------------
;; test_handle_vehicle_order_success
;; ---------------------------------------------------------------------------
(deftest test-handle-vehicle-order-success
  (let [out (agent/handle-vehicle-order
             {:buyer-did    "did:web:member.example.etzhayyim.com"
              :specs        "Class-8 truck"
              :initial-state "placed"
              :sbt-active   true})]
    (is (contains? out :vehicle-order)
        "vehicle order created successfully with SBT active")
    (is (= "placed"
           (get (:vehicle-order out) ":vehicle-order/state"))
        "vehicle-order state should be placed")))

;; ---------------------------------------------------------------------------
;; test_handle_vehicle_order_sbt_inactive
;; ---------------------------------------------------------------------------
(deftest test-handle-vehicle-order-sbt-inactive
  (let [out (agent/handle-vehicle-order
             {:buyer-did    "did:web:member.example.etzhayyim.com"
              :specs        "Class-8 truck"
              :initial-state "placed"
              :sbt-active   false})]
    (is (contains? out :error)
        "vehicle order refused if SBT inactive — error key present")
    (is (= "cancelled" (:state out))
        "vehicle order refused if SBT inactive — state is cancelled")))

;; ---------------------------------------------------------------------------
;; test_handle_production_progress_no_cid
;; ---------------------------------------------------------------------------
(deftest test-handle-production-progress-no-cid
  (let [out (agent/handle-production-progress
             {:order-id "vo.test.0001"
              :stage    "frame-fabrication"
              :details  "Steel cutting complete."})]
    (is (contains? out :production-progress)
        "production progress recorded without CID")
    (is (nil? (:attestation out))
        "attestation should be nil when no CID provided")))

;; ---------------------------------------------------------------------------
;; test_handle_production_progress_with_cid
;; ---------------------------------------------------------------------------
(deftest test-handle-production-progress-with-cid
  (let [cid "bafybeicidexample"
        out (agent/handle-production-progress
             {:order-id "vo.test.0002"
              :stage    "paint-finishing"
              :cid      cid
              :details  "White paint applied."})]
    (is (contains? out :production-progress)
        "production progress recorded with CID")
    (is (contains? out :attestation)
        "attestation key present when CID provided")
    (is (= cid (get (:attestation out) ":attestation/cid"))
        "attestation CID matches input")))

;; ---------------------------------------------------------------------------
;; test_handle_quality_pass
;; ---------------------------------------------------------------------------
(deftest test-handle-quality-pass
  (let [out (agent/handle-quality
             {:order-id           "vo.test.0003"
              :result             "pass"
              :inspector-did      "did:web:inspector.example.com"
              :current-order-state "qc"})]
    (is (contains? out :quality-record)
        "quality pass — quality-record present")
    (is (= "pass" (get (:quality-record out) ":quality/result"))
        "quality pass result is pass")
    (is (= "ready" (:new-order-state out))
        "quality pass marks order as ready")))

;; ---------------------------------------------------------------------------
;; test_handle_quality_fail
;; ---------------------------------------------------------------------------
(deftest test-handle-quality-fail
  (let [out (agent/handle-quality
             {:order-id           "vo.test.0004"
              :result             "fail"
              :defects            ["major dent"]
              :inspector-did      "did:web:inspector.example.com"
              :current-order-state "qc"})]
    (is (contains? out :quality-record)
        "quality fail — quality-record present")
    (is (= "fail" (get (:quality-record out) ":quality/result"))
        "quality fail result is fail")
    (is (= "cancelled" (:new-order-state out))
        "quality fail marks order as cancelled")))

;; ---------------------------------------------------------------------------
;; test_handle_quality_rework
;; ---------------------------------------------------------------------------
(deftest test-handle-quality-rework
  (let [out (agent/handle-quality
             {:order-id           "vo.test.0005"
              :result             "rework"
              :defects            ["minor scratch"]
              :inspector-did      "did:web:inspector.example.com"
              :current-order-state "qc"})]
    (is (contains? out :quality-record)
        "quality rework — quality-record present")
    (is (= "rework" (get (:quality-record out) ":quality/result"))
        "quality rework result is rework")
    (is (= "in-production" (:new-order-state out))
        "quality rework marks order as in-production")))

;; ---------------------------------------------------------------------------
;; test_handle_vin_attestation_success
;; ---------------------------------------------------------------------------
(deftest test-handle-vin-attestation-success
  (let [vin "TESTVIN0000000001"
        out (agent/handle-vin-attestation
             {:order-id               "vo.test.0006"
              :vin                    vin
              :emissions-audit-id     "emissions.test.0006"
              :silen-vehicle-review-id "silen.test.0006"
              :attestation-ids        ["attest.test.0006.frame"]})]
    (is (contains? out :vehicle-record)
        "VIN attestation creates vehicle record")
    (is (= vin (get (:vehicle-record out) ":vehicle/vin"))
        "vehicle record VIN matches input")
    (is (= (str "did:web:etzhayyim.com:sarutahiko:vehicle:" vin)
           (get (:vehicle-record out) ":vehicle/did"))
        "vehicle DID is correctly formed (G13)")))

;; ---------------------------------------------------------------------------
;; test_build_settlement_intent_tithe_split
;; ---------------------------------------------------------------------------
(deftest test-build-settlement-intent-tithe-split
  (let [gross-minor 1000000000  ;; 10,000 USDC
        out (agent/build-settlement-intent gross-minor)]
    (is (= 100000000 (:titheMinor out))
        "10% tithe split — titheMinor is 100000000")
    (is (= 900000000 (:factoryPayoutMinor out))
        "10% tithe split — factoryPayoutMinor is 900000000")
    (is (= "intent" (:state out))
        "10% tithe split — state is intent")))

;; ---------------------------------------------------------------------------
;; test_build_settlement_intent_executed_with_sig
;; ---------------------------------------------------------------------------
(deftest test-build-settlement-intent-executed-with-sig
  (let [gross-minor 500000000  ;; 5,000 USDC
        buyer-sig   "0xdeadbeef"
        out         (agent/build-settlement-intent gross-minor buyer-sig)]
    (is (= "executed" (:state out))
        "settlement state is executed with buyer signature")
    (is (= buyer-sig (:buyerSigRef out))
        "settlement buyerSigRef matches input")))
