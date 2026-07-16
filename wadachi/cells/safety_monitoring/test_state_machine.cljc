(ns wadachi.cells.safety-monitoring.test-state-machine
  "wadachi 轍 safety-monitoring state-machine cljc port + LIVE py↔clj deep parity."
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.java.shell :refer [sh]]
            [cheshire.core :as json]
            [wadachi.cells.safety-monitoring.state-machine :as sm]))

(deftest chain-reaches-end-at-100pct
  (let [out (sm/run-chain {})]
    (is (= 100 (get-in out ["safety_state" "completionPct"])))
    (is (= "safety_verified" (get-in out ["safety_state" "phase"])))
    (is (= "end" (get out "next_node")))
    (is (contains? out "safety_record"))))

(def ^:private py-dir "20-actors/wadachi/cells/safety_monitoring")

(deftest live-parity
  (testing "cljc safety_record == python (deep)"
    (let [py (sh "python3" "-c"
                 (str "import json, state_machine as sm\n"
                      "st={'safety_state':{'phase':'init','missionId':'MISSION-2026-0001','completionPct':0}}\n"
                      "for fn in [sm.transition_to_sensors_checked, sm.transition_to_hazards_assessed, sm.transition_to_safety_protocol_set, sm.transition_to_safety_verified]:\n"
                      "    out=fn(st); st={**st, **out}\n"
                      "print(json.dumps(out['safety_record']))")
                 :dir py-dir)]
      (if (not (zero? (:exit py)))
        (println "  [skip] python3 unavailable:" (:err py))
        (is (= (json/parse-string (clojure.string/trim (:out py)))
               (json/parse-string (json/generate-string (get (sm/run-chain {}) "safety_record")))))))))
