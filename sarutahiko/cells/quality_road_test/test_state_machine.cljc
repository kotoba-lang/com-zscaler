(ns sarutahiko.cells.quality-road-test.test-state-machine
  "sarutahiko 猿田彦 quality-road-test state-machine cljc port + LIVE py↔clj deep parity."
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.java.shell :refer [sh]]
            [cheshire.core :as json]
            [sarutahiko.cells.quality-road-test.state-machine :as sm]))

(deftest chain-reaches-end-at-100pct
  (let [out (sm/run-chain {})]
    (is (= 100 (get-in out ["road_test_state" "completionPct"])))
    (is (= "record_emitted" (get-in out ["road_test_state" "phase"])))
    (is (= "end" (get out "next_node")))
    (is (contains? out "road_test_record"))))

(def ^:private py-dir "20-actors/sarutahiko/cells/quality_road_test")

(deftest live-parity
  (testing "cljc road_test_record == python (deep)"
    (let [py (sh "python3" "-c"
                 (str "import json, state_machine as sm\n"
                      "st={'road_test_state':{'phase':'init','chassisId':'SARUTAHIKO-CHASSIS-0001','completionPct':0}}\n"
                      "for fn in [sm.transition_to_dyno_run_complete, sm.transition_to_g12_kpi_verified, sm.transition_to_public_road_test_complete, sm.transition_to_norimichi_attestation, sm.transition_to_record_emitted]:\n"
                      "    out=fn(st); st={**st, **out}\n"
                      "print(json.dumps(out['road_test_record']))")
                 :dir py-dir)]
      (if (not (zero? (:exit py)))
        (println "  [skip] python3 unavailable:" (:err py))
        (is (= (json/parse-string (clojure.string/trim (:out py)))
               (json/parse-string (json/generate-string (get (sm/run-chain {}) "road_test_record")))))))))
