(ns yakushi.methods.test-agent
  "yakushi 薬師 — agent gate tests. 1:1 port of py/test_agent.py (custom harness → clojure.test).
  Offline: OTC Wave 1 (G1), silen-pharma-review (G3), QP co-sign (G4), adverse-event aggregation
  (G5/G10), witness invariant (G9), record handlers, USDC + tithe settlement (G17/G18)."
  (:require [clojure.test :refer [deftest is]]
            [yakushi.methods.agent :as agent]))

(defn- blocked? [r] (contains? r "blocked"))

(deftest test-api-otc-wave1 (is (= true (get (agent/api-otc-ok "sodium-cromoglicate") "ok"))))
(deftest test-api-not-wave1 (is (= false (get (agent/api-otc-ok "omeprazole") "ok"))))
(deftest test-review-approved (is (= true (get (agent/review-attested "approve" "Wave 1") "ok"))))
(deftest test-review-rejected (is (= false (get (agent/review-attested "reject" "Wave 1") "ok"))))
(deftest test-qp-signature-ok (is (= true (get (agent/qp-signature-ok "did:web:...qp" "passkey-ref") "ok"))))
(deftest test-qp-signature-missing (is (= false (get (agent/qp-signature-ok "" "") "ok"))))
(deftest test-ae-valid-aggregation (is (= true (get (agent/adverse-event-ok "lot:001" "mild" "recovered") "ok"))))
(deftest test-ae-invalid-severity (is (= false (get (agent/adverse-event-ok "lot:001" "extreme" "unknown") "ok"))))
(deftest test-ae-invalid-outcome (is (= false (get (agent/adverse-event-ok "lot:001" "mild" "cured") "ok"))))
(deftest test-ae-missing-lot (is (= false (get (agent/adverse-event-ok "" "mild" "recovered") "ok"))))
(deftest test-witness-quorum-ok (is (= true (get (agent/witness-quorum-ok ["did1" "did2"]) "ok"))))
(deftest test-witness-quorum-low (is (= false (get (agent/witness-quorum-ok ["did1"]) "ok"))))

(deftest test-synthesis-wave1-ok
  (is (not (blocked? (agent/record-synthesis "sodium-cromoglicate" "literature-ref" ["did1" "did2"])))))
(deftest test-synthesis-non-wave1
  (is (= true (get (agent/record-synthesis "omeprazole" "literature-ref" ["did1" "did2"]) "blocked"))))
(deftest test-synthesis-low-witness
  (is (= true (get (agent/record-synthesis "sodium-cromoglicate" "literature-ref" ["did1"]) "blocked"))))

(deftest test-fill-aseptic-ok
  (is (not (blocked? (agent/record-fill "eye-drop" "aseptic-0.22µm-filter" "op-did" "qp-did")))))
(deftest test-fill-autoclave-ok
  (is (not (blocked? (agent/record-fill "tablet" "terminal-autoclave" "op-did" "qp-did")))))
(deftest test-fill-invalid-sterilization
  (is (= true (get (agent/record-fill "eye-drop" "uv-sterilization" "op-did" "qp-did") "blocked"))))

(deftest test-qc-release-ok
  (is (not (blocked? (agent/record-qc "lot:001" "ICH Q3 compliant" "qp-did" "release")))))
(deftest test-qc-release-no-qp
  (is (= true (get (agent/record-qc "lot:001" "ICH Q3 compliant" "" "release") "blocked"))))

(deftest test-ae-record-valid
  (is (not (blocked? (agent/record-ae "lot:001" "moderate" "not-recovered" "ipfs-cid")))))
(deftest test-ae-record-invalid
  (is (= true (get (agent/record-ae "lot:001" "bad" "unknown") "blocked"))))

(deftest test-settlement-intent
  (let [s (agent/build-settlement-intent 10000000)]
    (is (= 1000000 (get s "titheMinor")))
    (is (= "intent" (get s "state")))
    (is (= "usdc-base-l2" (get s "rail")))))

(deftest test-settlement-executed-with-sig
  (is (= "executed" (get (agent/build-settlement-intent 10000000 "0xsig") "state"))))

(deftest test-raw-material-valid-grade
  (is (not (blocked? (agent/record-raw-material "sodium cromoglicate" "公定" "low-risk")))))
(deftest test-raw-material-invalid-grade
  (is (= true (get (agent/record-raw-material "sodium cromoglicate" "custom" "low-risk") "blocked"))))
