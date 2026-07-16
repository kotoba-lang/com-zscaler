(ns himawari.cells.supply-procurement.test-state-machine
  "Tests for the himawari supply_procurement state machine (ADR-2606021200 port).
  1:1 parity with cells/supply_procurement/test_cell.py."
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.string :as str]
            [himawari.cells.supply-procurement.state-machine :as sm]))

;; ── Happy-path fixtures ──

(def ^:private valid-need
  {"needText"                    "solar-grade polysilicon"
   "lotId"                       "lot-2026-0042"
   "feedstockGrade"              "solar-grade-6N"
   "process"                     "siemens"
   "originRegion"                "JP"
   "supplierDid"                 "did:web:supplier.example"
   "buyerDid"                    "did:web:etzhayyim.com:himawari"
   "grossMinor"                  4200000
   "originRegionAttestationCid"  "bafy~sha256-origin"
   "sourcingAuditCid"            "bafy~sha256-audit"
   "attestingEngineerDid"        "did:plc:eng-001"
   "attestingRobots"             ["did:web:etzhayyim.com:himawari:robot:mimi"
                                   "did:web:etzhayyim.com:himawari:robot:otete"]
   "embodiedEnergyWhPerKg"       90000})

(deftest test-happy-path-external-procurement
  (testing "Valid external feedstock produces a procurement order"
    (let [result (sm/solve {"need" valid-need})
          order  (get result "procurementOrder")]
      (is (nil? (get result "refused")))
      (is (some? order))
      (is (= "external-pending-operator" (get order "state")))
      (is (= "lot-2026-0042" (get order "lotId"))))))

(deftest test-happy-path-sbom-attestation
  (testing "SBOM attestation is built with CycloneDX shape"
    (let [result (sm/solve {"need" valid-need})
          sbom   (get result "sbomAttestation")]
      (is (some? sbom))
      (is (= "CycloneDX" (get sbom "bomFormat")))
      (is (= "1.5" (get sbom "specVersion")))
      (is (>= (get sbom "componentCount") 1)))))

(deftest test-happy-path-provenance-attestation
  (testing "Per-lot provenance attestation is built for a lot with lotId"
    (let [result (sm/solve {"need" valid-need})
          prov   (get result "provenanceAttestation")]
      (is (some? prov))
      (is (= "com.etzhayyim.himawari.polysiliconProvenanceAttestation"
             (get-in prov ["record" "$type"]))))))

(deftest test-happy-path-intra-fab-transport
  (testing "intraFabTransport is set to the giemon AGV"
    (let [result (sm/solve {"need" valid-need})]
      (is (= "giemon-agv" (get result "intraFabTransport"))))))

(deftest test-happy-path-kotoba-writes
  (testing "kotobaWrites contains ≥1 entity (SBOM + provenance)"
    (let [result (sm/solve {"need" valid-need})]
      (is (>= (count (get result "kotobaWrites")) 1)))))

(deftest test-g2-n1-logic-grade-refused
  (testing "G2/N1: logic-grade EG-Si is refused"
    (let [result (sm/solve {"need" (assoc valid-need "feedstockGrade" "logic-grade-9N")})]
      (is (true? (get result "refused")))
      (is (str/includes? (str (get result "reason")) "N1")))))

(deftest test-g2-n6-xuar-origin-refused
  (testing "G2/N6: XUAR origin is refused"
    (let [result (sm/solve {"need" (assoc valid-need "originRegion" "Xinjiang")})]
      (is (true? (get result "refused")))
      (is (str/includes? (str (get result "reason")) "XUAR")))))

(deftest test-g2-uyghur-case-insensitive-refused
  (testing "G2/N6: uyghur (case-insensitive) is refused"
    (let [result (sm/solve {"need" (assoc valid-need "originRegion" "Uyghur Region")})]
      (is (true? (get result "refused"))))))

(deftest test-commons-ring-recycled-kerf
  (testing "recycled-kerf feedstock routes to commons ring"
    (let [result (sm/solve {"need" (assoc valid-need "feedstockGrade" "recycled-kerf"
                                                     "originRegion" "JP")})
          order  (get result "procurementOrder")]
      (is (nil? (get result "refused")))
      (is (= "commons" (get order "ring")))
      (is (= "commons-recovery" (get order "state")))
      (is (= 0 (get order "titheMinor"))))))

(deftest test-internal-ring-settlement-intent
  (testing "Internal ring produces a settlement intent with tithe"
    (let [need   (assoc valid-need "ring" "internal" "makerActor" "kanayama"
                                   "grossMinor" 1000000)
          result (sm/solve {"need" need})
          order  (get result "procurementOrder")]
      (is (nil? (get result "refused")))
      (is (= "internal" (get order "ring")))
      (is (some? (get order "settlement"))))))

(deftest test-external-with-operator-ref
  (testing "External procurement with operatorRef produces external-handoff"
    (let [need   (assoc valid-need "operatorRef" "council-op-001")
          result (sm/solve {"need" need})
          order  (get result "procurementOrder")]
      (is (= "external-handoff" (get order "state"))))))

(deftest test-sbom-includes-feedstock-component
  (testing "SBOM always includes the feedstock as a primary component"
    (let [result (sm/solve {"need" valid-need})
          sbom   (get result "sbomAttestation")
          comps  (get-in sbom ["cyclonedx" "components"])]
      (is (>= (count comps) 1))
      (is (some #(str/starts-with? (str (get % "purl")) "pkg:himawari-feedstock/") comps)))))

(deftest test-provenance-attested-when-complete
  (testing "Provenance record is attested when all required fields present"
    (let [result (sm/solve {"need" valid-need})
          prov   (get-in result ["provenanceAttestation" "record"])]
      (is (true? (get prov "attested"))))))

(deftest test-provenance-unattested-when-missing-fields
  (testing "Provenance record is unattested when required fields are missing"
    (let [incomplete-need (dissoc valid-need "sourcingAuditCid")
          result (sm/solve {"need" incomplete-need})
          prov   (get-in result ["provenanceAttestation" "record"])]
      (is (false? (boolean (get prov "attested")))))))

(deftest test-no-lot-id-no-provenance
  (testing "No lotId → no provenanceAttestation (nothing to anchor)"
    (let [result (sm/solve {"need" (dissoc valid-need "lotId")})]
      (is (nil? (get result "provenanceAttestation"))))))
