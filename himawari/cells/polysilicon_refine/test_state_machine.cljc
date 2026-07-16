(ns himawari.cells.polysilicon-refine.test-state-machine
  "Tests for the himawari polysilicon_refine state machine (ADR-2606021200 port).
  1:1 parity with cells/polysilicon_refine/test_cell.py."
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.string :as str]
            [himawari.cells.polysilicon-refine.state-machine :as sm]))

;; ── Happy-path fixture ──

(def ^:private valid-state
  {"lotId"                      "lot-2026-0001"
   "feedstockGrade"             "solar-grade-6N"
   "process"                    "siemens"
   "declaredOrigin"             "JP"
   "supplierDid"                "did:web:supplier.example"
   "originRegionAttestationCid" "bafy~sha256-origin"
   "sourcingAuditCid"           "bafy~sha256-audit"
   "attestingEngineerDid"       "did:plc:eng-001"
   "recordedAt"                 "2026-06-21T00:00:00Z"
   "attestingRobots"            ["did:web:etzhayyim.com:himawari:robot:mimi"
                                  "did:web:etzhayyim.com:himawari:robot:otete"]})

(deftest test-happy-path-accepted
  (testing "Valid solar-grade lot from non-XUAR region is accepted"
    (let [result (sm/solve valid-state)]
      (is (true? (get result "accepted")))
      (is (empty? (get result "violations")))
      (is (= "ingot_wafer" (get result "routeToCell")))
      (is (some? (get result "provenance")))
      (is (some? (get result "chainOfCustodyCid"))))))

(deftest test-happy-path-provenance-record
  (testing "Provenance record has expected shape and fields"
    (let [result (sm/solve valid-state)
          prov   (get result "provenance")]
      (is (= "com.etzhayyim.himawari.polysiliconProvenanceAttestation" (get prov "$type")))
      (is (= "lot-2026-0001" (get prov "lotId")))
      (is (= "solar-grade-6N" (get prov "feedstockGrade")))
      (is (= "accepted" (get prov "qaVerdict")))
      (is (empty? (get prov "violations"))))))

(deftest test-chain-of-custody-built
  (testing "chainOfCustody is a vector of ≥1 #custodyHop objects"
    (let [result (sm/solve valid-state)
          prov   (get result "provenance")
          coc    (get prov "chainOfCustody")]
      (is (>= (count coc) 1))
      (is (map? (first coc)))
      (is (get (first coc) "stage"))
      (is (get (first coc) "custodianDid")))))

(deftest test-attesting-robots-normalized
  (testing "attestingRobots are normalized into #robotSignature objects"
    (let [result (sm/solve valid-state)
          prov   (get result "provenance")
          robots (get prov "attestingRobots")]
      (is (= 2 (count robots)))
      (is (every? #(get % "robotDid") robots))
      (is (every? #(get % "signature") robots)))))

(deftest test-g2-xuar-rejected
  (testing "G2/N6: XUAR origin is refused outright"
    (let [result (sm/solve (assoc valid-state "declaredOrigin" "Xinjiang Province"))]
      (is (false? (get result "accepted")))
      (is (some #(str/includes? % "N6 constitutional") (get result "violations")))
      (is (nil? (get result "routeToCell"))))))

(deftest test-g2-xinjiang-lowercase-rejected
  (testing "G2/N6: xinjiang (lowercase) is case-insensitively rejected"
    (let [result (sm/solve (assoc valid-state "declaredOrigin" "xinjiang"))]
      (is (false? (get result "accepted"))))))

(deftest test-n1-logic-grade-rejected
  (testing "N1: logic-grade EG-Si (9N+) is rejected (not solar-grade)"
    (let [result (sm/solve (assoc valid-state "feedstockGrade" "logic-grade-9N"))]
      (is (false? (get result "accepted")))
      (is (some #(str/includes? % "N1") (get result "violations"))))))

(deftest test-n1-recycled-kerf-accepted
  (testing "N1: recycled-kerf is a valid solar-grade feedstock"
    (let [result (sm/solve (assoc valid-state "feedstockGrade" "recycled-kerf"))]
      (is (true? (get result "accepted"))))))

(deftest test-g2-conflict-mineral-rejected
  (testing "G2: conflict-mineral dopant In is rejected"
    (let [result (sm/solve (assoc valid-state "dopantElements" ["In" "B"]))]
      (is (false? (get result "accepted")))
      (is (some #(str/includes? % "conflict-mineral") (get result "violations"))))))

(deftest test-g2-missing-provenance-rejected
  (testing "G2: missing originRegionAttestationCid is rejected"
    (let [result (sm/solve (dissoc valid-state "originRegionAttestationCid"))]
      (is (false? (get result "accepted")))
      (is (some #(str/includes? % "originRegionAttestationCid") (get result "violations"))))))

(deftest test-g11-missing-lot-id-rejected
  (testing "G11: missing lotId is rejected"
    (let [result (sm/solve (assoc valid-state "lotId" ""))]
      (is (false? (get result "accepted")))
      (is (some #(str/includes? % "lotId") (get result "violations"))))))

(deftest test-g11-too-few-robots-rejected
  (testing "G11: fewer than 2 attesting robots is rejected"
    (let [result (sm/solve (assoc valid-state "attestingRobots" ["only-one"]))]
      (is (false? (get result "accepted")))
      (is (some #(str/includes? % "≥2") (get result "violations"))))))

(deftest test-missing-recorded-at-rejected
  (testing "recordedAt is required (G11 as-of)"
    (let [result (sm/solve (assoc valid-state "recordedAt" ""))]
      (is (false? (get result "accepted")))
      (is (some #(str/includes? % "recordedAt") (get result "violations"))))))

(deftest test-refusal-record-has-provenance
  (testing "Even a refused lot returns a provenance record for audit"
    (let [result (sm/solve (assoc valid-state "declaredOrigin" "xinjiang"))]
      (is (false? (get result "accepted")))
      (is (some? (get result "provenance")))
      (is (= "refused" (get-in result ["provenance" "qaVerdict"]))))))

(deftest test-caller-supplied-chain-of-custody-passed-through
  (testing "Caller-supplied chainOfCustody hops are passed through"
    (let [hop    {"stage" "quarry" "custodianDid" "did:web:quarry.example"
                  "regionCode" "JP" "evidenceCid" "bafy~sha256-qry"}
          result (sm/solve (assoc valid-state "chainOfCustody" [hop]))]
      (is (true? (get result "accepted")))
      (is (= "quarry" (get-in result ["provenance" "chainOfCustody" 0 "stage"]))))))
