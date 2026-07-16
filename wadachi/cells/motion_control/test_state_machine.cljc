(ns wadachi.cells.motion-control.test-state-machine
  "wadachi 轍 motion-control state-machine cljc port + LIVE py↔clj deep parity."
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.java.shell :refer [sh]]
            [cheshire.core :as json]
            [wadachi.cells.motion-control.state-machine :as sm]))

(deftest chain-reaches-end-at-100pct
  (let [out (sm/run-chain {})]
    (is (= 100 (get-in out ["motion_state" "completionPct"])))
    (is (= "motion_complete" (get-in out ["motion_state" "phase"])))
    (is (= "end" (get out "next_node")))
    (is (contains? out "motion_record"))))

(def ^:private py-dir "20-actors/wadachi/cells/motion_control")

(deftest live-parity
  (testing "cljc motion_record == python (deep)"
    (let [py (sh "python3" "-c"
                 (str "import json, state_machine as sm\n"
                      "st={'motion_state':{'phase':'init','missionId':'MISSION-2026-0001','completionPct':0}}\n"
                      "for fn in [sm.transition_to_motors_engaged, sm.transition_to_path_following, sm.transition_to_speed_regulated, sm.transition_to_motion_complete]:\n"
                      "    out=fn(st); st={**st, **out}\n"
                      "print(json.dumps(out['motion_record']))")
                 :dir py-dir)]
      (if (not (zero? (:exit py)))
        (println "  [skip] python3 unavailable:" (:err py))
        (is (= (json/parse-string (clojure.string/trim (:out py)))
               (json/parse-string (json/generate-string (get (sm/run-chain {}) "motion_record")))))))))
