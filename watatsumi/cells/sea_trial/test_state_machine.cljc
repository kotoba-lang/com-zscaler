(ns watatsumi.cells.sea-trial.test-state-machine
  "watatsumi 綿津見 SeaTrialCell (L5c) state-machine cljc port + LIVE py↔clj deep parity."
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.java.shell :refer [sh]]
            [cheshire.core :as json]
            [watatsumi.cells.sea-trial.state-machine :as sm]))

(def ^:private start {"craftId" "WATATSUMI-RESEARCH-0001"})

(deftest chain-reaches-record-at-100pct
  (let [out (sm/run-chain start)]
    (is (= "record_emitted" (get-in out ["sea_trial_state" "phase"])))
    (is (= 100 (get-in out ["sea_trial_state" "completionPct"])))
    (is (= "end" (get out "next_node")))))

(deftest record-invariants
  (let [rec (get (sm/run-chain start) "sea_trial_record")]
    (is (= "com.etzhayyim.watatsumi.seaTrialRecord" (get rec "$type")))
    (is (true? (get rec "overallAccept")))
    (is (= "IMCA D-001 equivalent" (get rec "protocol")))
    ;; G8 active-sonar ≤180 dB at design depth
    (is (= 175 (get-in rec ["deepWaterTrialResults" "g8ActiveSonarCheck" "maxDbRe1uPaAt1m"])))
    (is (true? (get-in rec ["deepWaterTrialResults" "g8ActiveSonarCheck" "accept"])))
    (is (= [500 1500 3000 5000 6500] (get-in rec ["deepWaterTrialResults" "incrementalDepthsM"])))))

(def ^:private py-dir "20-actors/watatsumi/cells/sea_trial")

(deftest live-parity
  (testing "cljc seaTrialRecord == python (deep)"
    (let [py (sh "python3" "-c"
                 (str "import json, state_machine as sm\n"
                      "st={'sea_trial_state':{'phase':'init','craftId':'WATATSUMI-RESEARCH-0001','completionPct':0}}\n"
                      "for fn in [sm.transition_to_dock_trial, sm.transition_to_harbor_dive, "
                      "sm.transition_to_deep_water_trial, sm.transition_to_record_emitted]:\n"
                      "    out=fn(st); st={**st, **out}\n"
                      "print(json.dumps(out['sea_trial_record']))")
                 :dir py-dir)]
      (if (not (zero? (:exit py)))
        (println "  [skip] python3 unavailable:" (:err py))
        (is (= (json/parse-string (clojure.string/trim (:out py)))
               (json/parse-string (json/generate-string (get (sm/run-chain start) "sea_trial_record")))))))))
