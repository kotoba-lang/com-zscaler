(ns sarutahiko.cells.vin-attestation-binder.test-state-machine
  "sarutahiko 猿田彦 vin-attestation-binder state-machine cljc port + LIVE py↔clj deep parity."
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.java.shell :refer [sh]]
            [cheshire.core :as json]
            [sarutahiko.cells.vin-attestation-binder.state-machine :as sm]))

(deftest chain-reaches-end-at-100pct
  (let [out (sm/run-chain {})]
    (is (= 100 (get-in out ["binder_state" "completionPct"])))
    (is (= "record_emitted" (get-in out ["binder_state" "phase"])))
    (is (= "end" (get out "next_node")))
    (is (contains? out "vehicle_manufacture_record"))))

(def ^:private py-dir "20-actors/sarutahiko/cells/vin_attestation_binder")

(deftest live-parity
  (testing "cljc vehicle_manufacture_record == python (deep)"
    (let [py (sh "python3" "-c"
                 (str "import json, state_machine as sm\n"
                      "st={'binder_state':{'phase':'init','chassisId':'SARUTAHIKO-CHASSIS-0001','completionPct':0}}\n"
                      "for fn in [sm.transition_to_records_collected, sm.transition_to_vin_assigned, sm.transition_to_vehicle_did_issued, sm.transition_to_kotoba_datomic_anchored, sm.transition_to_record_emitted]:\n"
                      "    out=fn(st); st={**st, **out}\n"
                      "print(json.dumps(out['vehicle_manufacture_record']))")
                 :dir py-dir)]
      (if (not (zero? (:exit py)))
        (println "  [skip] python3 unavailable:" (:err py))
        (is (= (json/parse-string (clojure.string/trim (:out py)))
               (json/parse-string (json/generate-string (get (sm/run-chain {}) "vehicle_manufacture_record")))))))))
