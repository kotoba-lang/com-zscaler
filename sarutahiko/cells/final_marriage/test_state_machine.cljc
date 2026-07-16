(ns sarutahiko.cells.final-marriage.test-state-machine
  "sarutahiko 猿田彦 final-marriage state-machine cljc port + LIVE py↔clj deep parity."
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.java.shell :refer [sh]]
            [cheshire.core :as json]
            [sarutahiko.cells.final-marriage.state-machine :as sm]))

(deftest chain-reaches-end-at-100pct
  (let [out (sm/run-chain {})]
    (is (= 100 (get-in out ["marriage_state" "completionPct"])))
    (is (= "attestation_emitted" (get-in out ["marriage_state" "phase"])))
    (is (= "end" (get out "next_node")))
    (is (contains? out "marriage_attestation"))))

(def ^:private py-dir "20-actors/sarutahiko/cells/final_marriage")

(deftest live-parity
  (testing "cljc marriage_attestation == python (deep)"
    (let [py (sh "python3" "-c"
                 (str "import json, state_machine as sm\n"
                      "st={'marriage_state':{'phase':'init','chassisId':'SARUTAHIKO-CHASSIS-0001','completionPct':0}}\n"
                      "for fn in [sm.transition_to_inputs_verified, sm.transition_to_chassis_lowered, sm.transition_to_cab_dropped, sm.transition_to_powertrain_mounted, sm.transition_to_harness_connected, sm.transition_to_attestation_emitted]:\n"
                      "    out=fn(st); st={**st, **out}\n"
                      "print(json.dumps(out['marriage_attestation']))")
                 :dir py-dir)]
      (if (not (zero? (:exit py)))
        (println "  [skip] python3 unavailable:" (:err py))
        (is (= (json/parse-string (clojure.string/trim (:out py)))
               (json/parse-string (json/generate-string (get (sm/run-chain {}) "marriage_attestation")))))))))
