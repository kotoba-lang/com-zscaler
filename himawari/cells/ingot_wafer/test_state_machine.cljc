(ns himawari.cells.ingot-wafer.test-state-machine
  "Tests for the himawari ingot_wafer cell (ADR-2606021200 port).
  1:1 port of cells/test_ingot_wafer.py cases."
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.string :as str]
            [himawari.cells.ingot-wafer.state-machine :as sm]))

(deftest test-ingot-wafer-happy-path-accepted
  (testing "Ingot wafer cell accepts a valid batch with adequate kerf recovery + renewable energy"
    (let [result (sm/solve {"batchId" "batch-001"
                            "polysiliconLotId" "lot-001"
                            "ingotMethod" "czochralski-monocrystalline"
                            "waferCount" 1000
                            "attestingRobots" ["did:web:mimi" "did:web:otete"]
                            "waferThicknessUm" 150
                            "waferDiameterMm" 210
                            "sliceMethod" "diamond-wire"
                            "yieldBps" 9800
                            "processEnergyWh" 100
                            "energySources" ["hikari-solar"]
                            "recordedAt" "2026-06-01T00:00:00Z"})]
      (is (true? (get result "accepted")))
      (is (some? (get result "waferBatchRecord")))
      (is (>= (get result "kerfRecoveryBps") 9000)))))

(deftest test-ingot-wafer-g5-kerf-recovery-floor
  (testing "G5 gate rejects batch with kerf recovery < 90%"
    (let [result (sm/solve {"batchId" "batch-002"
                            "polysiliconLotId" "lot-002"
                            "ingotMethod" "directional-cast-multicrystalline"
                            "waferCount" 1000
                            "attestingRobots" ["did:web:mimi" "did:web:otete"]
                            "kerfRecoveredGrams" 100  ;; very low recovery
                            "energySources" ["hikari-solar"]})]
      (is (false? (get result "accepted")))
      (is (str/includes? (get result "reason") "G5 violation")))))

(deftest test-ingot-wafer-g4-renewable-energy-required
  (testing "G4 gate rejects batch with non-renewable energy sources"
    (let [result (sm/solve {"batchId" "batch-003"
                            "polysiliconLotId" "lot-003"
                            "ingotMethod" "czochralski-monocrystalline"
                            "waferCount" 1000
                            "attestingRobots" ["did:web:mimi" "did:web:otete"]
                            "processEnergyWh" 500
                            "energySources" ["fossil-coal"]})]
      (is (false? (get result "accepted")))
      (is (str/includes? (get result "reason") "G4 violation")))))

(deftest test-ingot-wafer-missing-required-fields-raises
  (testing "Missing batchId raises contract violation"
    (is (thrown? clojure.lang.ExceptionInfo
                 (sm/solve {"polysiliconLotId" "lot" "ingotMethod" "czochralski-monocrystalline"
                            "waferCount" 1000 "attestingRobots" ["a" "b"]})))))

(deftest test-ingot-wafer-insufficient-robots-raises
  (testing "Fewer than 2 attesting robots raises contract violation"
    (is (thrown? clojure.lang.ExceptionInfo
                 (sm/solve {"batchId" "b" "polysiliconLotId" "lot"
                            "ingotMethod" "czochralski-monocrystalline"
                            "waferCount" 1000
                            "attestingRobots" ["only-one"]})))))
