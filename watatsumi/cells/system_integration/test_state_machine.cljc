(ns watatsumi.cells.system-integration.test-state-machine
  "watatsumi 綿津見 system-integration state-machine cljc port + LIVE py↔clj deep parity."
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.java.shell :refer [sh]]
            [cheshire.core :as json]
            [watatsumi.cells.system-integration.state-machine :as sm]))

(def ^:private start {"craftId" "WATATSUMI-RESEARCH-0001"})

(deftest chain-reaches-end-at-100pct
  (let [out (sm/run-chain start)]
    (is (= 100 (get-in out ["system_integration_state" "completionPct"])))
    (is (contains? #{"attestation_emitted" "record_emitted"} (get-in out ["system_integration_state" "phase"])))
    (is (= "end" (get out "next_node")))
    (is (contains? out "system_integration_attestation"))))

(def ^:private py-dir "20-actors/watatsumi/cells/system_integration")

(deftest live-parity
  (testing "cljc system_integration_attestation == python (deep)"
    (let [py (sh "python3" "-c"
                 (str "import json, state_machine as sm\n"
                      "st={'system_integration_state':{'phase':'init','craftId':'WATATSUMI-RESEARCH-0001','completionPct':0}}\n"
                      "for fn in [sm.transition_to_propulsion_installed, sm.transition_to_life_support_installed, sm.transition_to_sensors_installed, sm.transition_to_comms_installed, sm.transition_to_charter_scan_passed, sm.transition_to_attestation_emitted]:\n"
                      "    out=fn(st); st={**st, **out}\n"
                      "print(json.dumps(out['system_integration_attestation']))")
                 :dir py-dir)]
      (if (not (zero? (:exit py)))
        (println "  [skip] python3 unavailable:" (:err py))
        (is (= (json/parse-string (clojure.string/trim (:out py)))
               (json/parse-string (json/generate-string (get (sm/run-chain start) "system_integration_attestation")))))))))
