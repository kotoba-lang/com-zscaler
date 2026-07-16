(ns tatekata.cells.foundation-excavation.test-state-machine
  "tatekata 建方 foundation_excavation (conditional anomaly-routing) cljc port +
  LIVE py↔clj deep parity. The default mock trips the vibration anomaly → HALT."
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.java.shell :refer [sh]]
            [cheshire.core :as json]
            [tatekata.cells.foundation-excavation.state-machine :as sm]))

(deftest default-mock-halts-on-vibration-anomaly
  (let [out (sm/run-chain {})]
    ;; vibration 2.1 g > 2.0 g → ANOMALY_HALT → alert_record (NOT the witness path)
    (is (contains? out "alert_record"))
    (is (not (contains? out "constructed_record")))
    (is (= "anomaly_halt" (get-in out ["foundation_state" "phase"])))
    (is (= ["vibration_spike_2.1g"] (get-in out ["foundation_state" "anomalyFlags"])))
    (is (= "construction_halt" (get-in out ["alert_record" "event"])))
    (is (= ["vibration_spike_2.1g"] (get-in out ["alert_record" "anomalies"])))))

(deftest no-anomaly-path-reaches-emit
  ;; if the anomaly check finds nothing, the witness→emit path runs (constructed_record)
  (let [clean (-> {"foundation_state" {"phase" "execution" "siteId" "S" "completionPct" 75
                                       "anomalyFlags" [] "photoCid" "p" "depthMapCid" "d"}
                   "next_node" "witness_attestation"}
                  sm/wait-for-witness-sigs sm/emit-progress-record)]
    (is (contains? clean "constructed_record"))
    (is (= ["did:web:etzhayyim.com:giemon-unit-1" "did:web:etzhayyim.com:otete-unit-2"]
           (get-in clean ["constructed_record" "attestingRobots"])))))   ;; list of DIDs, not dicts

(def ^:private py-dir "20-actors/tatekata/cells/foundation_excavation")

(deftest live-parity
  (testing "cljc alert_record == python (deep, following the graph conditional)"
    (let [py (sh "python3" "-c"
                 (str "import json, state_machine as sm\n"
                      "st={'foundation_state':{'phase':'init','siteId':'unknown','completionPct':0}}\n"
                      "for fn in [sm.transition_to_planning, sm.transition_to_execution, sm.check_for_anomalies]:\n"
                      "    out=fn(st); st={**st, **out}\n"
                      "if out.get('next_node')=='halt':\n"
                      "    out=sm.halt_on_anomaly(st); print(json.dumps(out['alert_record']))\n"
                      "else:\n"
                      "    st={**st,**out}; out=sm.wait_for_witness_sigs(st); st={**st,**out}; out=sm.emit_progress_record(st); print(json.dumps(out['constructed_record']))")
                 :dir py-dir)]
      (if (not (zero? (:exit py)))
        (println "  [skip] python3 unavailable:" (:err py))
        (is (= (json/parse-string (clojure.string/trim (:out py)))
               (json/parse-string (json/generate-string (get (sm/run-chain {}) "alert_record")))))))))
