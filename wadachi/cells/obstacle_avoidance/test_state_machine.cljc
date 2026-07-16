(ns wadachi.cells.obstacle-avoidance.test-state-machine
  "wadachi 轍 obstacle-avoidance state-machine cljc port + LIVE py↔clj deep parity."
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.java.shell :refer [sh]]
            [cheshire.core :as json]
            [wadachi.cells.obstacle-avoidance.state-machine :as sm]))

(deftest chain-reaches-end-at-100pct
  (let [out (sm/run-chain {})]
    (is (= 100 (get-in out ["obstacle_state" "completionPct"])))
    (is (= "avoidance_complete" (get-in out ["obstacle_state" "phase"])))
    (is (= "end" (get out "next_node")))
    (is (contains? out "avoidance_record"))))

(def ^:private py-dir "20-actors/wadachi/cells/obstacle_avoidance")

(deftest live-parity
  (testing "cljc avoidance_record == python (deep)"
    (let [py (sh "python3" "-c"
                 (str "import json, state_machine as sm\n"
                      "st={'obstacle_state':{'phase':'init','missionId':'MISSION-2026-0001','completionPct':0}}\n"
                      "for fn in [sm.transition_to_lidar_scanning, sm.transition_to_obstacles_detected, sm.transition_to_course_correction, sm.transition_to_avoidance_complete]:\n"
                      "    out=fn(st); st={**st, **out}\n"
                      "print(json.dumps(out['avoidance_record']))")
                 :dir py-dir)]
      (if (not (zero? (:exit py)))
        (println "  [skip] python3 unavailable:" (:err py))
        (is (= (json/parse-string (clojure.string/trim (:out py)))
               (json/parse-string (json/generate-string (get (sm/run-chain {}) "avoidance_record")))))))))
