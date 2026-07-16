(ns sarutahiko.cells.electrical-integration.test-state-machine
  "sarutahiko 猿田彦 electrical-integration state-machine cljc port + LIVE py↔clj deep parity."
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.java.shell :refer [sh]]
            [cheshire.core :as json]
            [sarutahiko.cells.electrical-integration.state-machine :as sm]))

(deftest chain-reaches-attestation-at-100pct
  (let [out (sm/run-chain {})]
    (is (= 100 (get-in out ["electrical_state" "completionPct"])))
    (is (= "attestation_emitted" (get-in out ["electrical_state" "phase"])))
    (is (= "end" (get out "next_node")))
    (is (contains? out "electrical_attestation"))))

(def ^:private py-dir "20-actors/sarutahiko/cells/electrical_integration")

(deftest live-parity
  (testing "cljc electrical_attestation == python (deep)"
    (let [py (sh "python3" "-c"
                 (str "import json, state_machine as sm\n"
                      "st={'electrical_state':{'phase':'init','chassisId':'SARUTAHIKO-CHASSIS-0001','completionPct':0}}\n"
                      "for fn in [sm.transition_to_harness_routed, sm.transition_to_ecu_flashed, sm.transition_to_open_source_verified, sm.transition_to_diagnostics_passed, sm.transition_to_attestation_emitted]:\n"
                      "    out=fn(st); st={**st, **out}\n"
                      "print(json.dumps(out['electrical_attestation']))")
                 :dir py-dir)]
      (if (not (zero? (:exit py)))
        (println "  [skip] python3 unavailable:" (:err py))
        (is (= (json/parse-string (clojure.string/trim (:out py)))
               (json/parse-string (json/generate-string (get (sm/run-chain {}) "electrical_attestation")))))))))
