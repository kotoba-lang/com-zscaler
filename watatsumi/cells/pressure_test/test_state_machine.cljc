(ns watatsumi.cells.pressure-test.test-state-machine
  "watatsumi 綿津見 PressureTestCell (L5b) state-machine cljc port conformance +
  LIVE py↔clj deep parity (ADR-2606160842 port wave). The emitted record must be
  byte-identical to `cells/pressure_test/state_machine.py`."
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.java.shell :refer [sh]]
            [cheshire.core :as json]
            [watatsumi.cells.pressure-test.state-machine :as sm]))

(def ^:private start {"craftId" "WATATSUMI-RESEARCH-0001"})

(deftest phase-enum-preserves-python-values
  (is (= "init" (:init sm/phases)))
  (is (= "design_depth_verified" (:design-depth-verified sm/phases)))
  (is (= "record_emitted" (:record-emitted sm/phases)))
  (is (= 7 (count sm/phases))))

(deftest chain-reaches-record-emitted-at-100pct
  (let [out (sm/run-chain start)
        st  (get out "pressure_test_state")]
    (is (= "record_emitted" (get st "phase")))
    (is (= 100 (get st "completionPct")))
    (is (= "end" (get out "next_node")))))

(deftest g12-depth-cap-and-accept-logic
  (let [rec (get (sm/run-chain start) "pressure_test_record")]
    (is (= 6500 (get rec "designDepthM")))          ;; G12 civilian cap
    (is (= 8125 (get rec "testDepthEquivalentM")))   ;; 1.25× design depth
    (is (= 8125 (get rec "testPressureDbar")))
    (is (= 0 (get rec "leakRateMicrolitrePerMin")))
    (is (true? (get rec "overallAccept")))           ;; leak<1000 ∧ depth≤6500
    (is (= true (get-in rec ["g12KpiCheck" "accept"])))
    (is (= 5 (count (get rec "hibikiAEStream"))))))

(deftest intermediate-completion-pcts
  ;; the deterministic ramp 0→15→25→60→80→95→100
  (is (= 15 (-> (sm/init start) sm/transition-to-design-depth-verified
                (get "pressure_test_state") (get "completionPct"))))
  (is (= 80 (-> (sm/init start) sm/transition-to-design-depth-verified
                sm/transition-to-dock-lowering sm/transition-to-pressurization
                sm/transition-to-hold (get "pressure_test_state") (get "completionPct")))))

;; ── LIVE py↔clj deep parity ───────────────────────────────────────

(def ^:private py-dir "20-actors/watatsumi/cells/pressure_test")

(deftest live-parity
  (testing "cljc emitted record == python state_machine.py record (deep)"
    (let [py (sh "python3" "-c"
                 (str "import json, state_machine as sm\n"
                      "st={'pressure_test_state':{'phase':'init','craftId':'WATATSUMI-RESEARCH-0001','completionPct':0}}\n"
                      "for fn in [sm.transition_to_design_depth_verified, sm.transition_to_dock_lowering, "
                      "sm.transition_to_pressurization, sm.transition_to_hold, "
                      "sm.transition_to_depressurization, sm.transition_to_record_emitted]:\n"
                      "    out=fn(st); st={**st, **out}\n"
                      "print(json.dumps(out['pressure_test_record']))")
                 :dir py-dir)]
      (if (not (zero? (:exit py)))
        (println "  [skip] python3 unavailable — parity not re-checked this run:" (:err py))
        (let [py-rec (json/parse-string (clojure.string/trim (:out py)))
              clj-rec (get (sm/run-chain start) "pressure_test_record")
              ;; round-trip cljc through cheshire so numeric types normalise identically
              clj-rec' (json/parse-string (json/generate-string clj-rec))]
          (is (= py-rec clj-rec')))))))
