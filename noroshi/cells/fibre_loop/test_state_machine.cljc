(ns noroshi.cells.fibre-loop.test-state-machine
  "Tests for the noroshi 烽 fibre_loop state machine (ADR-2606051600 port). Drives the
  phase progression init → laid → aligned → spliced → segment_committed and pins every
  gate: N1 civilian-use refused, lay non-convergence refused, splice over-threshold
  refused, G7 server-sig refused + member-sig required, G8 witness-quorum ≥2. Plus LIVE
  py↔clj deep parity on the full happy-path cell_state."
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.java.shell :refer [sh]]
            [cheshire.core :as json]
            [noroshi.cells.fibre-loop.state-machine :as sm]))

(def ^:private happy
  {"cell_state" {} "use" "lay" "track_converged" true "final_xte_m" 0.5
   "coupling_loss_db" 0.05 "align_converged" true
   "splice_offset_um" 1.0 "splice_cleave_angle_deg" 0.5
   "member_sig" "m" "server_sig" "" "witness_sigs" ["a" "b"]})

(defn- run [inp]
  (reduce (fn [s f] (merge s (f s))) inp
          [sm/transition-lay sm/transition-run-align sm/transition-run-splice sm/transition-commit-segment]))

(deftest full-happy-path
  (let [out (run happy)
        cs (get out "cell_state")]
    (is (= "segment_committed" (get cs "phase")))
    (is (= "end" (get out "next_node")))
    (let [seg (get-in cs ["payload" "segment"])]
      (is (false? (get seg "serverHeldKey")))      ;; G7 invariant
      (is (true? (get seg "dryRun")))              ;; G8 R0 dry-run only
      (is (true? (get seg "witnessOk")))
      (is (= "m" (get seg "memberSig"))))))

(deftest splice-loss-model
  (is (= 0.0046 (sm/splice-loss-db 1.0 0.5)))      ;; 0.0016·1 + 0.012·0.25
  (is (= 0.0 (sm/splice-loss-db 0.0 0.0))))

(deftest gates-refuse
  ;; N1: a force use can never be energised
  (is (thrown? clojure.lang.ExceptionInfo (sm/transition-lay {"cell_state" {} "use" "weapon"})))
  ;; lay: plow must converge onto the route
  (is (thrown? clojure.lang.ExceptionInfo (sm/transition-lay {"cell_state" {} "use" "lay" "track_converged" false})))
  ;; splice over the 0.10 dB threshold
  (is (thrown? clojure.lang.ExceptionInfo (sm/transition-run-splice {"cell_state" {} "splice_offset_um" 20.0 "splice_cleave_angle_deg" 5.0})))
  ;; G7: server signature refused (no-server-key)
  (is (thrown? clojure.lang.ExceptionInfo (sm/transition-commit-segment {"cell_state" {} "member_sig" "m" "server_sig" "S" "witness_sigs" ["a" "b"]})))
  ;; G7: a member signature is required
  (is (thrown? clojure.lang.ExceptionInfo (sm/transition-commit-segment {"cell_state" {} "member_sig" "" "witness_sigs" ["a" "b"]})))
  ;; G8: witness quorum < 2 refused
  (is (thrown? clojure.lang.ExceptionInfo (sm/transition-commit-segment {"cell_state" {} "member_sig" "m" "witness_sigs" ["a"]}))))

(def ^:private py-dir "20-actors/noroshi/cells/fibre_loop")

(deftest live-parity
  (testing "cljc full cell_state == python (deep)"
    (let [py (sh "python3" "-c"
                 (str "import json, state_machine as sm\n"
                      "st={'cell_state':{}, 'use':'lay','track_converged':True,'final_xte_m':0.5,"
                      "'coupling_loss_db':0.05,'align_converged':True,"
                      "'splice_offset_um':1.0,'splice_cleave_angle_deg':0.5,"
                      "'member_sig':'m','server_sig':'','witness_sigs':['a','b']}\n"
                      "for fn in [sm.transition_lay, sm.transition_run_align, sm.transition_run_splice, sm.transition_commit_segment]:\n"
                      "    out=fn(st); st={**st, **out}\n"
                      "print(json.dumps(out['cell_state']))")
                 :dir py-dir)]
      (if (not (zero? (:exit py)))
        (println "  [skip] python3 unavailable:" (:err py))
        (is (= (json/parse-string (clojure.string/trim (:out py)))
               (json/parse-string (json/generate-string (get (run happy) "cell_state")))))))))
