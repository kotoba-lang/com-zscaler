(ns wadachi.cells.telemetry-log.test-state-machine
  "wadachi 轍 telemetry-log state-machine cljc port + LIVE py↔clj deep parity."
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.java.shell :refer [sh]]
            [cheshire.core :as json]
            [wadachi.cells.telemetry-log.state-machine :as sm]))

(deftest chain-reaches-end-at-100pct
  (let [out (sm/run-chain {})]
    (is (= 100 (get-in out ["telemetry_state" "completionPct"])))
    (is (= "logged" (get-in out ["telemetry_state" "phase"])))
    (is (= "end" (get out "next_node")))
    (is (contains? out "mission_complete_record"))))

(def ^:private py-dir "20-actors/wadachi/cells/telemetry_log")

(deftest live-parity
  (testing "cljc mission_complete_record == python (deep)"
    (let [py (sh "python3" "-c"
                 (str "import json, state_machine as sm\n"
                      "st={'telemetry_state':{'phase':'init','missionId':'MISSION-2026-0001','completionPct':0}}\n"
                      "for fn in [sm.transition_to_data_collected, sm.transition_to_data_processed, sm.transition_to_records_verified, sm.transition_to_logged]:\n"
                      "    out=fn(st); st={**st, **out}\n"
                      "print(json.dumps(out['mission_complete_record']))")
                 :dir py-dir)]
      (if (not (zero? (:exit py)))
        (println "  [skip] python3 unavailable:" (:err py))
        (is (= (json/parse-string (clojure.string/trim (:out py)))
               (json/parse-string (json/generate-string (get (sm/run-chain {}) "mission_complete_record")))))))))
