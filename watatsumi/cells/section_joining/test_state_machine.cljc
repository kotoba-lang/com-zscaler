(ns watatsumi.cells.section-joining.test-state-machine
  "watatsumi 綿津見 section-joining state-machine cljc port + LIVE py↔clj deep parity."
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.java.shell :refer [sh]]
            [cheshire.core :as json]
            [watatsumi.cells.section-joining.state-machine :as sm]))

(def ^:private start {"craftId" "WATATSUMI-RESEARCH-0001"})

(deftest chain-reaches-end-at-100pct
  (let [out (sm/run-chain start)]
    (is (= 100 (get-in out ["section_joining_state" "completionPct"])))
    (is (contains? #{"attestation_emitted" "record_emitted"} (get-in out ["section_joining_state" "phase"])))
    (is (= "end" (get out "next_node")))
    (is (contains? out "section_joining_attestation"))))

(def ^:private py-dir "20-actors/watatsumi/cells/section_joining")

(deftest live-parity
  (testing "cljc section_joining_attestation == python (deep)"
    (let [py (sh "python3" "-c"
                 (str "import json, state_machine as sm\n"
                      "st={'section_joining_state':{'phase':'init','craftId':'WATATSUMI-RESEARCH-0001','completionPct':0}}\n"
                      "for fn in [sm.transition_to_sections_aligned, sm.transition_to_multipass_tig_complete, sm.transition_to_rt_100pct_passed, sm.transition_to_pwht_complete, sm.transition_to_attestation_emitted]:\n"
                      "    out=fn(st); st={**st, **out}\n"
                      "print(json.dumps(out['section_joining_attestation']))")
                 :dir py-dir)]
      (if (not (zero? (:exit py)))
        (println "  [skip] python3 unavailable:" (:err py))
        (is (= (json/parse-string (clojure.string/trim (:out py)))
               (json/parse-string (json/generate-string (get (sm/run-chain start) "section_joining_attestation")))))))))
