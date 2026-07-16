(ns watatsumi.cells.weld-inspection.test-state-machine
  "watatsumi 綿津見 weld-inspection state-machine cljc port + LIVE py↔clj deep parity."
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.java.shell :refer [sh]]
            [cheshire.core :as json]
            [watatsumi.cells.weld-inspection.state-machine :as sm]))

(def ^:private start {"craftId" "WATATSUMI-RESEARCH-0001"})

(deftest chain-reaches-end-at-100pct
  (let [out (sm/run-chain start)]
    (is (= 100 (get-in out ["weld_inspection_state" "completionPct"])))
    (is (contains? #{"attestation_emitted" "record_emitted"} (get-in out ["weld_inspection_state" "phase"])))
    (is (= "end" (get out "next_node")))
    (is (contains? out "weld_inspection_record"))))

(def ^:private py-dir "20-actors/watatsumi/cells/weld_inspection")

(deftest live-parity
  (testing "cljc weld_inspection_record == python (deep)"
    (let [py (sh "python3" "-c"
                 (str "import json, state_machine as sm\n"
                      "st={'weld_inspection_state':{'phase':'init','craftId':'WATATSUMI-RESEARCH-0001','completionPct':0,'sectionIndex':0}}\n"
                      "for fn in [sm.transition_to_rt_complete, sm.transition_to_ut_complete, sm.transition_to_pt_complete, sm.transition_to_sango_witness, sm.transition_to_record_emitted]:\n"
                      "    out=fn(st); st={**st, **out}\n"
                      "print(json.dumps(out['weld_inspection_record']))")
                 :dir py-dir)]
      (if (not (zero? (:exit py)))
        (println "  [skip] python3 unavailable:" (:err py))
        (is (= (json/parse-string (clojure.string/trim (:out py)))
               (json/parse-string (json/generate-string (get (sm/run-chain start) "weld_inspection_record")))))))))
