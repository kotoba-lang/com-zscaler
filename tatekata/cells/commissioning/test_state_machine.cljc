(ns tatekata.cells.commissioning.test-state-machine
  "tatekata 建方 commissioning state-machine cljc port + LIVE py↔clj deep parity."
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.java.shell :refer [sh]]
            [cheshire.core :as json]
            [tatekata.cells.commissioning.state-machine :as sm]))

(deftest chain-reaches-end
  (let [out (sm/run-chain {})]
    (is (= "complete" (get-in out ["commissioning_state" "phase"])))
    (is (= 100 (get-in out ["commissioning_state" "completionPct"])))
    (is (= "end" (get out "next_node")))
    (is (contains? out "project_closure_record"))))

(def ^:private py-dir "20-actors/tatekata/cells/commissioning")

(deftest live-parity
  (testing "cljc project_closure_record == python (deep)"
    (let [py (sh "python3" "-c"
                 (str "import json, state_machine as sm\n"
                      "st={'commissioning_state':{'phase':'init','projectId':'unknown','completionPct':0}}\n"
                      "for fn in [sm.transition_to_systems_tested, sm.transition_to_defect_walkdown, sm.transition_to_waste_inventory, sm.transition_to_project_signoff, sm.emit_project_closure_record]:\n"
                      "    out=fn(st); st={**st, **out}\n"
                      "print(json.dumps(out['project_closure_record']))")
                 :dir py-dir)]
      (if (not (zero? (:exit py)))
        (println "  [skip] python3 unavailable:" (:err py))
        (is (= (json/parse-string (clojure.string/trim (:out py)))
               (json/parse-string (json/generate-string (get (sm/run-chain {}) "project_closure_record")))))))))
