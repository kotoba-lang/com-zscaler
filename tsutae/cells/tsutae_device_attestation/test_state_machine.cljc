(ns tsutae.cells.tsutae-device-attestation.test-state-machine
  "Tests for the tsutae device_attestation state machine (ADR-2605261300 port). Drives
  bom_lineage_assembled → robot_quorum_signed → did_minted → attestation_emitted: phase/pct
  progression, BoM lineage, the G4 witness quorum (≥2 distinct robot DIDs — duplicate DIDs do not
  count), the minted per-device DID, and the deviceAttestation accept (quorum ∧ did)."
  (:require [clojure.test :refer [deftest is]]
            [tsutae.cells.tsutae-device-attestation.state-machine :as sm]))

(defn- run-all [signers]
  (-> {"device_state" {"phase" "init" "serial" "SN-0007" "completionPct" 0}}
      sm/transition-to-bom-lineage-assembled
      (cond-> signers (assoc "robotSigners" signers))
      sm/transition-to-robot-quorum-signed
      sm/transition-to-did-minted
      sm/transition-to-attestation-emitted))

(deftest test-full-progression-default-quorum
  (let [s0 {"device_state" {"phase" "init" "serial" "SN-0007" "completionPct" 0}}
        s1 (sm/transition-to-bom-lineage-assembled s0)
        s2 (sm/transition-to-robot-quorum-signed s1)
        s3 (sm/transition-to-did-minted s2)
        s4 (sm/transition-to-attestation-emitted s3)]
    (is (= "bom_lineage_assembled" (get-in s1 ["device_state" "phase"])))
    (is (= [25 55 80 100] (mapv #(get-in % ["device_state" "completionPct"]) [s1 s2 s3 s4])))
    (is (= 5 (count (get-in s1 ["device_state" "bomLineage"]))))
    (is (= 2 (get-in s2 ["device_state" "quorumGuard" "signerCount"])))   ; default 2 signers
    (is (= true (get-in s2 ["device_state" "quorumGuard" "accept"])))
    (is (= "did:web:etzhayyim.com:tsutae:device:SN-0007" (get-in s3 ["device_state" "did"])))
    (is (= "end" (get s4 "next_node")))))

(deftest test-g4-quorum-requires-two-distinct
  ;; only one signer → reject
  (let [qg (get-in (run-all [{"robotDid" "did:web:etzhayyim.com:mimi-unit-1" "role" "aoi"}])
                   ["device_attestation" "quorumGuard"])]
    (is (= 1 (get qg "signerCount")))
    (is (= false (get qg "accept")))
    (is (= "fewer than 2 distinct robot signers (G4)" (get qg "reason"))))
  ;; two signers but SAME DID → distinct count 1 → reject
  (is (= false (get-in (run-all [{"robotDid" "did:x" "role" "a"} {"robotDid" "did:x" "role" "b"}])
                       ["device_attestation" "quorumGuard" "accept"])))
  ;; three distinct → accept (≥2)
  (is (= true (get-in (run-all [{"robotDid" "did:a"} {"robotDid" "did:b"} {"robotDid" "did:c"}])
                      ["device_attestation" "quorumGuard" "accept"]))))

(deftest test-attestation-accept-and-fields
  (let [rec (get (run-all nil) "device_attestation")]
    (is (= "com.etzhayyim.tsutae.deviceAttestation" (get rec "$type")))
    (is (= "SN-0007" (get rec "serial")))
    (is (= "did:web:etzhayyim.com:tsutae:device:SN-0007" (get rec "did")))
    (is (= true (get rec "repairEventReady")))             ; G14
    (is (= true (get rec "accept"))))
  ;; failed quorum → attestation rejected
  (is (= false (get-in (run-all [{"robotDid" "did:solo"}]) ["device_attestation" "accept"]))))
