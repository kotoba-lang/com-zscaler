(ns yamabiko.cells.homologation-binder.test-state-machine
  "Tests for yamabiko homologation_binder state machine (py→cljc port).
   Terminal layer L5c. Enforces G2 (open registry) + G13 (per-trainset DID).
   Aggregates all upstream attestations."
  (:require [clojure.test :refer [deftest is testing]]
            [yamabiko.cells.homologation-binder.state-machine :as sm]))

(deftest test-homologation-happy-path
  (testing "Full homologation terminal layer happy path"
    (let [init-state {"trainsetId" "TEST-TS-001"}
          s1 (sm/transition-to-records-collected init-state)
          s2 (sm/transition-to-serial-assigned s1)
          s3 (sm/transition-to-trainset-did-issued s2)
          s4 (sm/transition-to-homologation-authority-review s3)
          s5 (sm/transition-to-kotoba-datomic-anchored s4)
          s6 (sm/transition-to-record-emitted s5)
          record (get s6 "homologation_record")]
      ;; Phase progression
      (is (= "records_collected" (get-in s1 ["homologation_state" "phase"])))
      (is (= 20 (get-in s1 ["homologation_state" "completionPct"])))
      (is (= "serial_assigned" (get-in s2 ["homologation_state" "phase"])))
      (is (= 40 (get-in s2 ["homologation_state" "completionPct"])))
      (is (= "trainset_did_issued" (get-in s3 ["homologation_state" "phase"])))
      (is (= 55 (get-in s3 ["homologation_state" "completionPct"])))
      (is (= "homologation_authority_review" (get-in s4 ["homologation_state" "phase"])))
      (is (= 75 (get-in s4 ["homologation_state" "completionPct"])))
      (is (= "kotoba_datomic_anchored" (get-in s5 ["homologation_state" "phase"])))
      (is (= 90 (get-in s5 ["homologation_state" "completionPct"])))
      (is (= "record_emitted" (get-in s6 ["homologation_state" "phase"])))
      (is (= 100 (get-in s6 ["homologation_state" "completionPct"])))

      ;; Record structure
      (is (= "com.etzhayyim.yamabiko.homologationRecord" (get record "$type")))
      (is (= "TEST-TS-001" (get record "trainsetId")))
      (is (some? (get record "serial")))
      (is (some? (get record "trainsetDid")))
      (is (some? (get record "upstreamRecords")))
      (is (some? (get record "authorityReview")))
      (is (some? (get record "kotoba-datomicAnchor"))))))

(deftest test-homologation-upstream-records-collected
  (testing "All upstream attestations collected (carbody, bogie, interior, traction, final, dynamic, acoustic)"
    (let [s (sm/transition-to-records-collected {})
          upstream (get-in s ["homologation_state" "upstreamRecords"])]
      (is (some? (get upstream "carbodyAttestations")))
      (is (some? (get upstream "bogieAttestations")))
      (is (some? (get upstream "interiorAttestations")))
      (is (some? (get upstream "tractionElectricalAttestation")))
      (is (some? (get upstream "finalAssemblyAttestation")))
      (is (some? (get upstream "dynamicTestRecord")))
      (is (some? (get upstream "acousticEmissionsAuditRecord"))))))

(deftest test-homologation-serial-assignment
  (testing "Serial number assigned"
    (let [s (sm/transition-to-serial-assigned {})
          serial (get-in s ["homologation_state" "serial"])]
      (is (some? serial))
      (is (clojure.string/includes? serial "ETZYAMABIKO"))
      (is (clojure.string/includes? serial "2026")))))

(deftest test-homologation-g13-trainset-did
  (testing "G13 enforces per-trainset DID (did:web:etzhayyim.com:yamabiko:trainset:<serial>)"
    (let [s (sm/transition-to-trainset-did-issued {"homologation_state" {"serial" "TEST-SERIAL-001"}})
          did (get-in s ["homologation_state" "trainsetDid"])]
      (is (clojure.string/includes? did "did:web:etzhayyim.com:yamabiko:trainset:"))
      (is (clojure.string/includes? did "TEST-SERIAL-001")))))

(deftest test-homologation-authority-review-rams
  (testing "Authority review covers RAMS standards (EN 50126/50128/50129)"
    (let [s (sm/transition-to-homologation-authority-review {})
          auth (get-in s ["homologation_state" "authorityReview"])]
      (is (some #{"EN 50126"} (get auth "ramsStandards")))
      (is (some #{"EN 50128"} (get auth "ramsStandards")))
      (is (some #{"EN 50129"} (get auth "ramsStandards")))
      (is (= "JP" (get auth "jurisdiction")))
      (is (= "ISSUE_TYPE_APPROVAL" (get auth "decision"))))))

(deftest test-homologation-g2-kotoba-datomic-anchor
  (testing "G2 enforces kotoba-datomic anchoring (open registry)"
    (let [s (sm/transition-to-kotoba-datomic-anchored {})
          anchor (get-in s ["homologation_state" "kotoba_datomicAnchor"])]
      (is (= true (get anchor "g2Compliant")))
      (is (= true (get anchor "openTrainsetRegistry")))
      (is (= "com.etzhayyim.yamabiko" (get anchor "membraneNamespace"))))))
