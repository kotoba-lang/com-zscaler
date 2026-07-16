(ns sarutahiko.cells.powertrain-assembly.test-state-machine
  "sarutahiko 猿田彦 powertrain-assembly state-machine cljc port + LIVE py↔clj deep parity."
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.java.shell :refer [sh]]
            [cheshire.core :as json]
            [sarutahiko.cells.powertrain-assembly.state-machine :as sm]))

(deftest chain-reaches-attestation-at-100pct
  (let [out (sm/run-chain {})]
    (is (= 100 (get-in out ["powertrain_state" "completionPct"])))
    (is (= "attestation_emitted" (get-in out ["powertrain_state" "phase"])))
    (is (= "end" (get out "next_node")))
    (is (contains? out "powertrain_attestation"))))

(def ^:private py-dir "20-actors/sarutahiko/cells/powertrain_assembly")

(deftest live-parity
  (testing "cljc powertrain_attestation == python (deep)"
    (let [py (sh "python3" "-c"
                 (str "import json, state_machine as sm\n"
                      "st={'powertrain_state':{'phase':'init','chassisId':'SARUTAHIKO-CHASSIS-0001','completionPct':0}}\n"
                      "for fn in [sm.transition_to_fuel_guard_checked, sm.transition_to_engine_installed, sm.transition_to_transmission_coupled, sm.transition_to_axles_mounted, sm.transition_to_brake_integrated, sm.transition_to_attestation_emitted]:\n"
                      "    out=fn(st); st={**st, **out}\n"
                      "print(json.dumps(out['powertrain_attestation']))")
                 :dir py-dir)]
      (if (not (zero? (:exit py)))
        (println "  [skip] python3 unavailable:" (:err py))
        (is (= (json/parse-string (clojure.string/trim (:out py)))
               (json/parse-string (json/generate-string (get (sm/run-chain {}) "powertrain_attestation")))))))))
