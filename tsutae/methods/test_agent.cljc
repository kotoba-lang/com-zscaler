(ns tsutae.methods.test-agent
  "Tests for the tsutae manufacturing agent (ADR-2605261300 port; supersedes py/test_agent.py).
  Covers all handlers: device-order (success / SBT-inactive / proprietary-SoC refusal), is-open-soc,
  production-progress (no-cid / with-cid / unknown-stage), quality (pass/fail/rework state
  transitions), device-attestation quorum, and the settlement tithe split (intent vs executed)."
  (:require [clojure.test :refer [deftest is]]
            [clojure.string :as str]
            [tsutae.methods.agent :as a]))

(deftest test-device-order-success
  (let [r (a/handle-device-order {"buyer_did" "did:web:m" "specs" "open handheld" "soc" "StarFive-JH7110"
                                  "initial_state" "placed" "sbt_active" true})
        o (get r "device_order")]
    (is (some? o))
    (is (= "did:web:m" (get o ":device-order/buyer-did")))
    (is (= "StarFive-JH7110" (get o ":device-order/soc")))
    (is (= "placed" (get o ":device-order/state")))
    (is (str/starts-with? (get o ":device-order/id") "do.new.order."))))   ; generated default id

(deftest test-device-order-sbt-inactive
  (let [r (a/handle-device-order {"buyer_did" "did:web:m" "sbt_active" false})]
    (is (= "cancelled" (get r "state")))
    (is (re-find #"SBT not active" (get r "error")))))

(deftest test-device-order-rejects-proprietary-soc
  (let [r (a/handle-device-order {"buyer_did" "did:web:m" "soc" "Snapdragon-8" "sbt_active" true})]
    (is (= "cancelled" (get r "state")))
    (is (re-find #"open RISC-V only" (get r "error")))))

(deftest test-is-open-soc
  (is (= true (a/is-open-soc "StarFive-JH7110")))
  (is (= true (a/is-open-soc "iwakura")))
  (is (= false (a/is-open-soc "Snapdragon-8")))
  (is (= false (a/is-open-soc "Apple-A17")))
  (is (= false (a/is-open-soc "MysteryChip"))))     ; not on allow-list → rejected

(deftest test-production-progress-no-cid
  (let [r (a/handle-production-progress {"order_id" "o1" "stage" "pcb-smt"})]
    (is (= "pcb-smt" (get-in r ["production_progress" ":production-progress/stage"])))
    (is (nil? (get r "attestation")))))            ; no cid → no attestation record

(deftest test-production-progress-with-cid
  (let [r (a/handle-production-progress {"order_id" "o1" "stage" "firmware-load" "cid" "bafkrei..." "details" "v1"})]
    (is (= "bafkrei..." (get-in r ["attestation" ":attestation/cid"])))
    (is (= "firmware-load" (get-in r ["attestation" ":attestation/type"])))
    (is (re-find #"Details: v1" (get-in r ["production_progress" ":production-progress/note"])))))

(deftest test-production-progress-unknown-stage
  (is (re-find #"unknown stage" (get (a/handle-production-progress {"order_id" "o1" "stage" "frobnicate"}) "error"))))

(deftest test-quality-pass-fail-rework
  (is (= "ready" (get (a/handle-quality {"order_id" "o" "result" "pass" "inspector_did" "did:i"}) "new_order_state")))
  (is (= "cancelled" (get (a/handle-quality {"order_id" "o" "result" "fail" "inspector_did" "did:i"}) "new_order_state")))
  (is (= "in-production" (get (a/handle-quality {"order_id" "o" "result" "rework" "inspector_did" "did:i"}) "new_order_state")))
  (is (re-find #"missing" (get (a/handle-quality {"order_id" "o"}) "error"))))

(deftest test-device-attestation-quorum
  ;; ≥2 distinct signers → accept + minted DID
  (let [r (a/handle-device-attestation {"order_id" "o" "serial" "SN-1" "robot_signers" ["did:a" "did:b"]})]
    (is (= true (get r "accept")))
    (is (= "did:web:etzhayyim.com:tsutae:device:SN-1" (get-in r ["device_record" ":device/did"]))))
  ;; duplicate DIDs collapse to 1 distinct → reject
  (let [r (a/handle-device-attestation {"order_id" "o" "serial" "SN-1" "robot_signers" ["did:a" "did:a"]})]
    (is (= false (get r "accept")))
    (is (re-find #"fewer than 2 distinct" (get r "error")))))

(deftest test-settlement-tithe-split
  (let [s (a/build-settlement-intent 60000000)]
    (is (= 6000000 (get s "titheMinor")))           ; 10% of 60,000,000
    (is (= 54000000 (get s "factoryPayoutMinor")))
    (is (= "intent" (get s "state")))               ; no sig → intent
    (is (= "usdc-base-l2" (get s "rail")))))

(deftest test-settlement-executed-with-sig
  (let [s (a/build-settlement-intent 60000000 "0xsig")]
    (is (= "executed" (get s "state")))
    (is (= "0xsig" (get s "buyerSigRef")))))
