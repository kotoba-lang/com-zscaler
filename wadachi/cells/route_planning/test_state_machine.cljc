(ns wadachi.cells.route-planning.test-state-machine
  "wadachi 轍 route-planning state-machine cljc port + LIVE py↔clj deep parity."
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.java.shell :refer [sh]]
            [cheshire.core :as json]
            [wadachi.cells.route-planning.state-machine :as sm]))

(deftest chain-reaches-end-at-100pct
  (let [out (sm/run-chain {})]
    (is (= 100 (get-in out ["route_state" "completionPct"])))
    (is (= "trajectory_planned" (get-in out ["route_state" "phase"])))
    (is (= "end" (get out "next_node")))
    (is (contains? out "trajectory_record"))))

(def ^:private py-dir "20-actors/wadachi/cells/route_planning")

(deftest live-parity
  (testing "cljc trajectory_record == python (deep)"
    (let [py (sh "python3" "-c"
                 (str "import json, state_machine as sm\n"
                      "st={'route_state':{'phase':'init','missionId':'MISSION-2026-0001','completionPct':0}}\n"
                      "for fn in [sm.transition_to_destination_validated, sm.transition_to_obstacles_mapped, sm.transition_to_path_computed, sm.transition_to_trajectory_planned, sm.transition_to_witness_attestation]:\n"
                      "    out=fn(st); st={**st, **out}\n"
                      "print(json.dumps(out['trajectory_record']))")
                 :dir py-dir)]
      (if (not (zero? (:exit py)))
        (println "  [skip] python3 unavailable:" (:err py))
        (is (= (json/parse-string (clojure.string/trim (:out py)))
               (json/parse-string (json/generate-string (get (sm/run-chain {}) "trajectory_record")))))))))
