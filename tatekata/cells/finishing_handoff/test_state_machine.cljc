(ns tatekata.cells.finishing-handoff.test-state-machine
  "tatekata 建方 finishing-handoff state-machine cljc port + LIVE py↔clj deep parity."
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.java.shell :refer [sh]]
            [cheshire.core :as json]
            [tatekata.cells.finishing-handoff.state-machine :as sm]))

(deftest chain-reaches-end
  (let [out (sm/run-chain {})]
    (is (= "complete" (get-in out ["finishing_state" "phase"])))
    (is (= 100 (get-in out ["finishing_state" "completionPct"])))
    (is (= "end" (get out "next_node")))
    (is (contains? out "finishing_record"))))

(def ^:private py-dir "20-actors/tatekata/cells/finishing_handoff")

(deftest live-parity
  (testing "cljc finishing_record == python (deep)"
    (let [py (sh "python3" "-c"
                 (str "import json, state_machine as sm\n"
                      "st={'finishing_state':{'phase':'init','projectId':'unknown','completionPct':0}}\n"
                      "for fn in [sm.transition_to_prep_complete, sm.transition_to_drywall_complete, sm.transition_to_paint_complete, sm.transition_to_trim_installed, sm.transition_to_witness_attestation, sm.emit_finishing_record]:\n"
                      "    out=fn(st); st={**st, **out}\n"
                      "print(json.dumps(out['finishing_record']))")
                 :dir py-dir)]
      (if (not (zero? (:exit py)))
        (println "  [skip] python3 unavailable:" (:err py))
        (is (= (json/parse-string (clojure.string/trim (:out py)))
               (json/parse-string (json/generate-string (get (sm/run-chain {}) "finishing_record")))))))))
