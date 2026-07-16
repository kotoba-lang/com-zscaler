(ns noroshi.cells.device-design.test-state-machine
  "Tests for the noroshi device_design state machine. Drives the phase
  progression init -> intent_captured -> civilian_cleared -> plan_generated
  -> device_emitted for an assembly kind and a discrete component, and pins
  the civilian gate: an unknown kind or a non-civilian force-class refuses
  before any plan is generated."
  (:require [clojure.test :refer [deftest is]]
            [noroshi.cells.device-design.state-machine :as sm]))

(defn- run [inp]
  (reduce (fn [s f] (merge s (f s))) inp
          [sm/transition-intent sm/transition-civilian-gate
           sm/transition-epda-plan sm/transition-emit]))

(deftest test-full-happy-path-assembly-kind
  (let [out (run {"cell_state" {} "kind" "cpo-module" "force_class" "civilian-comms"
                  "name" "test-cpo-design"})
        cs (get out "cell_state")]
    (is (= "device_emitted" (get cs "phase")))
    (is (= "end" (get out "next_node")))
    (let [dev (get-in cs ["payload" "device"])]
      (is (= "civilian-comms" (get dev "forceClass")))
      (is (= "open-pdk" (get dev "process")))
      (is (true? (get dev "representative")))
      (is (= "test-cpo-design" (get dev "id"))))
    (is (> (:total-waveguide-um (get-in cs ["payload" "plan"])) 0.0))))

(deftest test-full-happy-path-discrete-component
  (let [out (run {"cell_state" {} "kind" "modulator" "force_class" "civilian-comms"})
        cs (get out "cell_state")]
    (is (= "device_emitted" (get cs "phase")))
    (is (zero? (:total-waveguide-um (get-in cs ["payload" "plan"]))))))

(deftest test-unknown-kind-refused-before-plan-generated
  (let [s1 (sm/transition-intent {"cell_state" {} "kind" "quantum-dazzler" "force_class" "civilian-comms"})]
    (is (thrown? clojure.lang.ExceptionInfo (sm/transition-civilian-gate s1)))))

(deftest test-non-civilian-force-class-refused
  (let [s1 (sm/transition-intent {"cell_state" {} "kind" "modulator" "force_class" "weaponizable"})]
    (is (thrown? clojure.lang.ExceptionInfo (sm/transition-civilian-gate s1)))))

(deftest test-intent-transition-only-captures-supplied-fields
  (let [s1 (sm/transition-intent {"cell_state" {} "kind" "laser" "force_class" "civilian-comms"})]
    (is (= "laser" (get-in s1 ["cell_state" "kind"])))
    (is (nil? (get-in s1 ["cell_state" "name"])))))

(deftest test-emit-honors-overrides
  (let [out (run {"cell_state" {} "kind" "laser" "force_class" "civilian-comms"
                  "line_rate_gbps" 25.0 "eda" "meep"})
        dev (get-in out ["cell_state" "payload" "device"])]
    (is (= 25.0 (get dev "lineRateGbps")))
    (is (= "meep" (get dev "eda")))))
