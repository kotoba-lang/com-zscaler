(ns watatsumi.cells.hull-ring-fabrication.test-state-machine
  "watatsumi 綿津見 hull-ring-fabrication state-machine cljc port + LIVE py↔clj deep parity."
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.java.shell :refer [sh]]
            [cheshire.core :as json]
            [watatsumi.cells.hull-ring-fabrication.state-machine :as sm]))

(def ^:private start {"craftId" "WATATSUMI-RESEARCH-0001"})

(deftest chain-reaches-end-at-100pct
  (let [out (sm/run-chain start)]
    (is (= 100 (get-in out ["hull_ring_state" "completionPct"])))
    (is (contains? #{"attestation_emitted" "record_emitted"} (get-in out ["hull_ring_state" "phase"])))
    (is (= "end" (get out "next_node")))
    (is (contains? out "pressure_hull_attestation"))))

(def ^:private py-dir "20-actors/watatsumi/cells/hull_ring_fabrication")

(deftest live-parity
  (testing "cljc pressure_hull_attestation == python (deep)"
    (let [py (sh "python3" "-c"
                 (str "import json, state_machine as sm\n"
                      "st={'hull_ring_state':{'phase':'init','craftId':'WATATSUMI-RESEARCH-0001','completionPct':0,'ringIndex':0}}\n"
                      "for fn in [sm.transition_to_material_verified, sm.transition_to_plate_rolled, sm.transition_to_ring_frame_welded, sm.transition_to_roundness_qa, sm.transition_to_attestation_emitted]:\n"
                      "    out=fn(st); st={**st, **out}\n"
                      "print(json.dumps(out['pressure_hull_attestation']))")
                 :dir py-dir)]
      (if (not (zero? (:exit py)))
        (println "  [skip] python3 unavailable:" (:err py))
        (is (= (json/parse-string (clojure.string/trim (:out py)))
               (json/parse-string (json/generate-string (get (sm/run-chain start) "pressure_hull_attestation")))))))))
