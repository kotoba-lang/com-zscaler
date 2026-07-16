(ns makura.methods.test-agent
  "makura 枕 — agent gate tests. 1:1 port of py/test_agent.py (custom harness → clojure.test).
  Offline: KPI caps (G11), isocyanate exposure (G6), FR-chemistry exclusion (G8), no-embedded-
  sensors (G14), USDC + tithe settlement (G17/G18)."
  (:require [clojure.test :refer [deftest is]]
            [makura.methods.agent :as agent]))

(deftest test-kpi-mass-cap
  (is (= false (get (agent/kpi-caps-ok 2.5 [50 40 20] 3.0) "ok"))))

(deftest test-kpi-dims-cap
  (is (= false (get (agent/kpi-caps-ok 1.5 [90 40 20] 3.0) "ok"))))

(deftest test-kpi-within
  (is (= true (get (agent/kpi-caps-ok 1.5 [60 40 20] 3.0) "ok"))))

(deftest test-exposure-mdi-ceiling
  (is (= false (get (agent/exposure-ok 6.0 1.0) "ok"))))

(deftest test-exposure-tdi-ceiling
  (is (= false (get (agent/exposure-ok 3.0 3.0) "ok"))))

(deftest test-fr-chemistry-excluded
  (is (= false (get (agent/fr-chemistry-ok ["polyol" "TDCPP"]) "ok"))))

(deftest test-fr-free-ok
  (is (= true (get (agent/fr-chemistry-ok ["polyol" "mdi" "surfactant"]) "ok"))))

(deftest test-bom-rejects-embedded
  (is (= false (get (agent/bom-no-embedded ["foam" "fabric" "RFID tag"]) "ok"))))

(deftest test-bom-clean
  (is (= true (get (agent/bom-no-embedded ["foam" "fabric" "zipper"]) "ok"))))

(deftest test-foam-batch-blocked-on-fr
  (is (= true (get (agent/record-foam-batch "b" ["pbde"] 1.0 1.0) "blocked"))))

(deftest test-foam-batch-blocked-on-exposure
  (is (= true (get (agent/record-foam-batch "b" ["polyol"] 9.0 1.0) "blocked"))))

(deftest test-lot-blocked-over-kpi
  (is (= true (get (agent/finalize-pillow-lot "l" 3.0 [50 40 20] 3.0 []) "blocked"))))

(deftest test-settlement-tithe-split
  (let [s (agent/build-settlement-intent 20000000)]
    (is (= 2000000 (get s "titheMinor")))
    (is (= "intent" (get s "state")))
    (is (= "usdc-base-l2" (get s "rail")))))

(deftest test-settlement-executed-with-sig
  (is (= "executed" (get (agent/build-settlement-intent 1000000 "0xsig") "state"))))
