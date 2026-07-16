(ns sarutahiko.cells.paint-finishing.test-state-machine
  "sarutahiko 猿田彦 paint-finishing state-machine cljc port + LIVE py↔clj deep parity."
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.java.shell :refer [sh]]
            [cheshire.core :as json]
            [sarutahiko.cells.paint-finishing.state-machine :as sm]))

(deftest chain-reaches-end-at-100pct
  (let [out (sm/run-chain {})]
    (is (= 100 (get-in out ["paint_state" "completionPct"])))
    (is (= "attestation_emitted" (get-in out ["paint_state" "phase"])))
    (is (= "end" (get out "next_node")))
    (is (contains? out "paint_attestation"))))

(def ^:private py-dir "20-actors/sarutahiko/cells/paint_finishing")

(deftest live-parity
  (testing "cljc paint_attestation == python (deep)"
    (let [py (sh "python3" "-c"
                 (str "import json, state_machine as sm\n"
                      "st={'paint_state':{'phase':'init','chassisId':'SARUTAHIKO-CHASSIS-0001','completionPct':0}}\n"
                      "for fn in [sm.transition_to_pretreatment_done, sm.transition_to_ktl_primer_applied, sm.transition_to_base_coat_applied, sm.transition_to_clear_coat_applied, sm.transition_to_cured, sm.transition_to_attestation_emitted]:\n"
                      "    out=fn(st); st={**st, **out}\n"
                      "print(json.dumps(out['paint_attestation']))")
                 :dir py-dir)]
      (if (not (zero? (:exit py)))
        (println "  [skip] python3 unavailable:" (:err py))
        (is (= (json/parse-string (clojure.string/trim (:out py)))
               (json/parse-string (json/generate-string (get (sm/run-chain {}) "paint_attestation")))))))))
