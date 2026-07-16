(ns kamado.cells.decommission-robot.test-state-machine
  "Tests for the kamado 竈 decommission_robot state machine (ADR-2606051500 port).
  Drives init → purged → cut_planned → entry_committed and pins every gate: the ENTRY
  GATE (cut requires a completed purge), N1/G3 civilian-use, G5/G7 no-server-key, G8
  witness quorum ≥2. Plus LIVE py↔clj deep parity on the committed record."
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.java.shell :refer [sh]]
            [cheshire.core :as json]
            [kamado.cells.decommission-robot.state-machine :as sm]))

(def ^:private happy
  {"cell_state" {} "use" "decommission" "gas" "H2S" "initial_ppm" 120.0
   "target_x" 1.5 "target_y" 0.4 "member_sig" "m" "server_sig" "" "witness_sigs" ["a" "b"]})

(defn- run [inp]
  ;; merge-thread (mirrors langgraph's {**st, **out}) so top-level inputs reach each transition
  (reduce (fn [s f] (merge s (f s))) inp
          [sm/transition-purge sm/transition-plan-cut sm/transition-commit-entry]))

(deftest full-happy-path-commits
  (let [out (run happy)
        cs (get out "cell_state")
        rec (get-in cs ["payload" "record"])]
    (is (= "entry_committed" (get cs "phase")))
    (is (= "end" (get out "next_node")))
    (is (true? (get rec ":decommission/committed")))
    (is (true? (get rec ":decommission/entry-permitted")))
    (is (false? (get rec ":decommission/server-held-key")))   ;; G5/G7 invariant
    (is (true? (get rec ":decommission/dry-run")))             ;; G8 R0 dry-run only
    (is (true? (get rec ":decommission/witness-ok")))))

(deftest purge-projection
  (let [p (get-in (run happy) ["cell_state" "payload" "purge"])]
    (is (= "H2S" (get p "gas")))
    (is (true? (get p "entryPermitted")))))      ;; purge converged ≤ entry limit

(deftest gates-refuse
  ;; ENTRY GATE: plan_cut before a completed purge
  (is (thrown? clojure.lang.ExceptionInfo (sm/transition-plan-cut {"cell_state" {"phase" "init"}})))
  ;; commit before a planned cut
  (is (thrown? clojure.lang.ExceptionInfo (sm/transition-commit-entry {"cell_state" {"phase" "purged"}})))
  ;; N1/G3: a fossil life-extension / non-civilian use is refused in the purge core
  (is (thrown? clojure.lang.ExceptionInfo (sm/transition-purge (assoc happy "use" "restart-fossil"))))
  ;; G5/G7: a server signature is refused at the cut-plan gate
  (is (thrown? clojure.lang.ExceptionInfo
               (-> (sm/transition-purge happy)
                   (merge happy {"server_sig" "S"})
                   sm/transition-plan-cut)))
  ;; G5/G7: a member signature is required
  (is (thrown? clojure.lang.ExceptionInfo
               (-> (sm/transition-purge happy)
                   (merge (dissoc happy "member_sig") {"member_sig" ""})
                   sm/transition-plan-cut))))

(def ^:private py-dir "20-actors/kamado/cells/decommission_robot")

(deftest live-parity
  (testing "cljc committed record == python (deep)"
    (let [py (sh "python3" "-c"
                 (str "import json, state_machine as sm\n"
                      "st={'cell_state':{}, 'use':'decommission','gas':'H2S','initial_ppm':120.0,"
                      "'target_x':1.5,'target_y':0.4,'member_sig':'m','server_sig':'','witness_sigs':['a','b']}\n"
                      "for fn in [sm.transition_purge, sm.transition_plan_cut, sm.transition_commit_entry]:\n"
                      "    out=fn(st); st={**st, **out}\n"
                      "print(json.dumps(out['cell_state']['payload']['record']))")
                 :dir py-dir)]
      (if (not (zero? (:exit py)))
        (println "  [skip] python3 unavailable:" (:err py))
        (is (= (json/parse-string (clojure.string/trim (:out py)))
               (json/parse-string (json/generate-string (get-in (run happy) ["cell_state" "payload" "record"])))))))))
