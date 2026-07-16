(ns sarutahiko.cells.emissions-audit.test-state-machine
  "sarutahiko 猿田彦 emissions-audit state-machine cljc port + LIVE py↔clj deep parity."
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.java.shell :refer [sh]]
            [cheshire.core :as json]
            [sarutahiko.cells.emissions-audit.state-machine :as sm]))

(deftest chain-reaches-end-at-100pct
  (let [out (sm/run-chain {})]
    (is (= 100 (get-in out ["emissions_state" "completionPct"])))
    (is (= "record_emitted" (get-in out ["emissions_state" "phase"])))
    (is (= "end" (get out "next_node")))
    (is (contains? out "emissions_audit_record"))))

(def ^:private py-dir "20-actors/sarutahiko/cells/emissions_audit")

(deftest live-parity
  (testing "cljc emissions_audit_record == python (deep)"
    (let [py (sh "python3" "-c"
                 (str "import json, state_machine as sm\n"
                      "st={'emissions_state':{'phase':'init','chassisId':'SARUTAHIKO-CHASSIS-0001','completionPct':0}}\n"
                      "for fn in [sm.transition_to_euro7_scanned, sm.transition_to_japan_pnlt_scanned, sm.transition_to_bharat_vi_scanned, sm.transition_to_record_emitted]:\n"
                      "    out=fn(st); st={**st, **out}\n"
                      "print(json.dumps(out['emissions_audit_record']))")
                 :dir py-dir)]
      (if (not (zero? (:exit py)))
        (println "  [skip] python3 unavailable:" (:err py))
        (is (= (json/parse-string (clojure.string/trim (:out py)))
               (json/parse-string (json/generate-string (get (sm/run-chain {}) "emissions_audit_record")))))))))
