(ns kiyome.cells.surface-cleaning.test-state-machine
  "Tests for the kiyome surface_cleaning state machine (ADR-2606032100 port; supersedes the Python
  cells/test_state_machines.py). Drives init → traversed → cleaned → pass_logged and pins the
  privacy-by-construction hard invariants — G9 on-device-only + no-imagery-retained, N5 no-biometric
  — plus the cleaning-method allow-list and the G3 witness quorum (≥2 robot sigs + a human
  attestation)."
  (:require [clojure.test :refer [deftest is]]
            [kiyome.cells.surface-cleaning.state-machine :as sm]))

(defn- run-to-pass [overrides]
  (-> {"cell_state" {} "area_m2" 42 "method" "vacuum"}
      sm/transition-to-traversed
      sm/transition-to-cleaned
      (merge overrides)
      sm/transition-to-pass-logged))

(deftest test-happy-path-logs-private-pass
  (let [s (run-to-pass {"robot_sigs" ["r1" "r2"] "human_attestation" "occupant-ok"})
        cp (get-in s ["cell_state" "payload" "cleaning_pass"])]
    (is (= "pass_logged" (get-in s ["cell_state" "phase"])))
    (is (= "end" (get s "next_node")))
    (is (= 42 (get cp "areaM2")))
    (is (= "vacuum" (get cp "method")))
    (is (= true (get cp "onDeviceOnly")))
    (is (= false (get cp "imageryRetained")))
    (is (= true (get cp "witnessQuorumMet")))))

(deftest test-phase-progression
  (let [s1 (sm/transition-to-traversed {"cell_state" {} "area_m2" 10})
        s2 (sm/transition-to-cleaned (merge s1 {"method" "mop"}))]
    (is (= "traversed" (get-in s1 ["cell_state" "phase"])))
    (is (= "cleaned" (get s1 "next_node")))
    (is (= 10 (get-in s1 ["cell_state" "area_m2"])))
    (is (= "mop" (get-in s2 ["cell_state" "method"])))
    (is (= "pass_logged" (get s2 "next_node")))))

(deftest test-g9-blocks-off-device-feed
  (is (thrown-with-msg? clojure.lang.ExceptionInfo #"G9 violation: imagery/sensor feed left the robot"
                        (run-to-pass {"on_device_only" false "robot_sigs" ["r1" "r2"] "human_attestation" "ok"}))))

(deftest test-g9-blocks-retained-imagery
  (is (thrown-with-msg? clojure.lang.ExceptionInfo #"G9 violation: occupant imagery retained"
                        (run-to-pass {"imagery_retained" true "robot_sigs" ["r1" "r2"] "human_attestation" "ok"}))))

(deftest test-n5-blocks-biometric-capture
  (is (thrown-with-msg? clojure.lang.ExceptionInfo #"N5 violation: biometric"
                        (run-to-pass {"biometric_capture" true "robot_sigs" ["r1" "r2"] "human_attestation" "ok"}))))

(deftest test-unknown-method-rejected
  (is (thrown-with-msg? clojure.lang.ExceptionInfo #"unknown cleaning method"
                        (sm/transition-to-cleaned {"cell_state" {} "method" "powerwash"})))
  ;; all four permitted methods clean cleanly
  (doseq [m ["sweep" "vacuum" "mop" "wipe"]]
    (is (= "cleaned" (get-in (sm/transition-to-cleaned {"cell_state" {} "method" m}) ["cell_state" "phase"])) m)))

(deftest test-g3-quorum-requires-two-robots-and-a-human
  ;; only one robot sig → quorum not met (still logs, but witnessQuorumMet false)
  (is (= false (get-in (run-to-pass {"robot_sigs" ["r1"] "human_attestation" "ok"})
                       ["cell_state" "payload" "cleaning_pass" "witnessQuorumMet"])))
  ;; two robots but no human → not met
  (is (= false (get-in (run-to-pass {"robot_sigs" ["r1" "r2"] "human_attestation" ""})
                       ["cell_state" "payload" "cleaning_pass" "witnessQuorumMet"])))
  ;; two robots + human → met
  (is (= true (get-in (run-to-pass {"robot_sigs" ["r1" "r2"] "human_attestation" "occupant"})
                      ["cell_state" "payload" "cleaning_pass" "witnessQuorumMet"]))))
