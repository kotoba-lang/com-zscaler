(ns himawari.cells.outbound-logistics.test-state-machine
  "Tests for the himawari outbound_logistics state machine (ADR-2606021200 port).
  1:1 parity with cells/outbound_logistics/test_outbound_logistics.py."
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.string :as str]
            [himawari.cells.outbound-logistics.state-machine :as sm]))

;; ── Happy-path fixture ──

(def ^:private valid-state
  {"manifestId"   "manifest-001"
   "consigneeDid" "did:web:etzhayyim.com:hikari:site-a"
   "recordedAt"   "2026-06-21T00:00:00Z"
   "carrierClass" "car"
   "crossBorder"  false
   "loadingRecord" {"loadingId"    "load-001"
                    "moduleSerials" ["MOD-001" "MOD-002"]}})

(deftest test-happy-path-road
  (testing "Domestic road manifest emits outboundManifest"
    (let [result (sm/solve valid-state)
          manifest (get result "outboundManifest")]
      (is (some? manifest))
      (is (= "com.etzhayyim.himawari.outboundManifest" (get manifest "$type")))
      (is (= "manifest-001" (get manifest "manifestId")))
      (is (= "car" (get manifest "carrierClass")))
      (is (= "hikari-install-site" (get manifest "destinationKind"))))))

(deftest test-happy-path-marine
  (testing "Marine transport selects ship carrier class"
    (let [result (sm/solve (-> valid-state
                               (dissoc "carrierClass")
                               (assoc "transportMode" "marine")))
          manifest (get result "outboundManifest")]
      (is (= "ship" (get manifest "carrierClass"))))))

(deftest test-g13-telemetry-encrypted
  (testing "G13: telemetryEncrypted is always true"
    (let [manifest (get (sm/solve valid-state) "outboundManifest")]
      (is (true? (get manifest "telemetryEncrypted"))))))

(deftest test-g13-no-weaponization
  (testing "G13: weaponizationPayload is always false"
    (let [manifest (get (sm/solve valid-state) "outboundManifest")]
      (is (false? (get manifest "weaponizationPayload"))))))

(deftest test-domestic-no-customs
  (testing "Domestic leg: customs.required is false"
    (let [manifest (get (sm/solve valid-state) "outboundManifest")
          customs  (get manifest "customs")]
      (is (false? (get customs "required"))))))

(deftest test-cross-border-customs
  (testing "Cross-border leg: lodgeDeclaration is built"
    (let [result   (sm/solve (assoc valid-state "crossBorder" true
                                                "hsCode" "854143"
                                                "declaredValueUsd" 5000))
          manifest (get result "outboundManifest")
          customs  (get manifest "customs")]
      (is (some? (get customs "lodgeDeclaration")))
      (is (= "854143" (get-in customs ["lodgeDeclaration" "hsCode"])))
      (is (str/ends-with? (get-in customs ["lodgeDeclaration" "declarationId"]) ":decl")))))

(deftest test-cross-border-customs-engine-namespace
  (testing "Cross-border: customs engine uses the correct (real) namespace"
    (let [result  (sm/solve (assoc valid-state "crossBorder" true))
          customs (get-in result ["outboundManifest" "customs"])]
      (is (= "com.etzhayyim.etzhayyim.apps.customsClearance" (get customs "engine"))))))

(deftest test-g13-external-consignee-throws
  (testing "G13: non-hikari consignee raises an exception"
    (is (thrown? Exception
                 (sm/solve (assoc valid-state "consigneeDid" "did:web:external.example"))))))

(deftest test-invalid-carrier-class-throws
  (testing "Unknown carrier class raises an exception"
    (is (thrown? Exception
                 (sm/solve (assoc valid-state "carrierClass" "hovercraft"))))))

(deftest test-route-request-has-gnc-reference
  (testing "routeRequest references kami-autodrive GNC"
    (let [route (get-in (sm/solve valid-state) ["outboundManifest" "routeRequest"])]
      (is (= "kami-autodrive" (get route "gnc")))
      (is (= "car" (get route "vehicleClass"))))))

(deftest test-loading-id-threaded-through
  (testing "loadingId from loadingRecord is threaded into the manifest"
    (let [manifest (get (sm/solve valid-state) "outboundManifest")]
      (is (= "load-001" (get manifest "loadingId"))))))

(deftest test-module-serials-threaded-through
  (testing "moduleSerials from loadingRecord are threaded into the manifest"
    (let [manifest (get (sm/solve valid-state) "outboundManifest")]
      (is (= ["MOD-001" "MOD-002"] (get manifest "moduleSerials"))))))

(deftest test-attesting-robots-not-empty
  (testing "attestingRobots is always ≥1 entry (minItems 1 per lexicon)"
    (let [manifest (get (sm/solve valid-state) "outboundManifest")]
      (is (>= (count (get manifest "attestingRobots")) 1)))))
