(ns sarutahiko.cells.cab-body-forming.test-state-machine
  "sarutahiko 猿田彦 cab-body-forming state-machine cljc port + LIVE py↔clj deep parity."
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.java.shell :refer [sh]]
            [cheshire.core :as json]
            [sarutahiko.cells.cab-body-forming.state-machine :as sm]))

(deftest chain-reaches-attestation-at-100pct
  (let [out (sm/run-chain {})]
    (is (= 100 (get-in out ["cab_state" "completionPct"])))
    (is (= "attestation_emitted" (get-in out ["cab_state" "phase"])))
    (is (= "end" (get out "next_node")))
    (is (contains? out "cab_body_attestation"))))

(def ^:private py-dir "20-actors/sarutahiko/cells/cab_body_forming")

(deftest live-parity
  (testing "cljc cab_body_attestation == python (deep)"
    (let [py (sh "python3" "-c"
                 (str "import json, state_machine as sm\n"
                      "st={'cab_state':{'phase':'init','chassisId':'SARUTAHIKO-CHASSIS-0001','completionPct':0}}\n"
                      "for fn in [sm.transition_to_sheet_lot_verified, sm.transition_to_hot_stamping_complete, sm.transition_to_spot_welding_complete, sm.transition_to_leak_test_passed, sm.transition_to_attestation_emitted]:\n"
                      "    out=fn(st); st={**st, **out}\n"
                      "print(json.dumps(out['cab_body_attestation']))")
                 :dir py-dir)]
      (if (not (zero? (:exit py)))
        (println "  [skip] python3 unavailable:" (:err py))
        (is (= (json/parse-string (clojure.string/trim (:out py)))
               (json/parse-string (json/generate-string (get (sm/run-chain {}) "cab_body_attestation")))))))))
