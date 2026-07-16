(ns tatekata.cells.mep-installation.test-cell
  "clojure.test port of the mep_installation slice of the generic cell
  state-machine contract (cf. funadaiku `cells/test_state_machines.py`,
  ADR-2605250715).

  The Python `test_state_machines.py` (funadaiku) is GENERIC over all of an
  actor's cells (it discovers every `cells/*/state_machine.py` and asserts each
  transition chain reaches 100%). This file ports ONLY the mep_installation slice
  of that contract — the INIT→DUCTWORK→CONDUIT→PIPING→PRESSURE_TEST→WITNESS→COMPLETE
  chain + the TEST_FAIL→HALT branch + MepState defaults + the closed-surface guard
  + the R0 `solve()` raise — exercised against the ported `cell.cljc`.

  Deferred (still Python-only `state_machine.py` / `cell.py`, not yet ported to
  .cljc): the OTHER 4 tatekata cells — foundation_excavation, structural_assembly,
  finishing_handoff, commissioning. ALSO deferred: the `py/test_agent.py` suite —
  it exercises the separate flat `py/agent.py` module (KPI caps G11, witness quorum
  G3, phase advancement, USDC+tithe settlement G15/G16), which does NOT use the
  mep_installation cell/state_machine at all (its `handle_mep_installation` returns
  a different plan shape); that whole module is out of scope for this cell port."
  (:require [clojure.test :refer [deftest is testing run-tests]]
            [tatekata.cells.mep-installation.cell :as cell]))

;; ── MepState defaults (fresh 0% INIT record) ──────────────────────

(deftest test-mep-state-defaults-to-zero-pct-init
  (let [ms cell/mep-state]
    (is (= 0 (:completionPct ms)) "MepState should default to 0%")
    (is (= "init" (:phase ms)) "default phase is MepPhase.INIT.value")
    (is (= "unknown" (:projectId ms)))
    (is (nil? (:hvacPlan ms)) "optional fields default to nil (Python None)")
    (is (nil? (:robotSignatures ms)))))

;; ── the transition chain (each step well-formed; phase ∈ enum) ────

(def ^:private phase-values (set (vals cell/mep-phases)))

(deftest test-each-transition-well-formed
  (testing "every transition returns {cell-state {phase∈enum, 0<pct≤100}, next-node}"
    (doseq [f [cell/transition-to-ductwork-routed
               cell/transition-to-conduit-routed
               cell/transition-to-piping-routed
               cell/transition-to-pressure-test
               cell/transition-to-witness-attestation
               cell/emit-mep-signoff-record]]
      (let [out (f {:cell-state {}})
            new (:cell-state out)
            pct (:completionPct new)]
        (is (map? out))
        (is (contains? phase-values (:phase new)))
        (is (and (integer? pct) (< 0 pct) (<= pct 100)))
        (is (contains? out :next-node))))))

(deftest test-transitions-monotone-and-reach-completion
  ;; the happy-path chain (pressure_test passes → witness → emit), pcts strictly up
  (let [pcts (map (fn [f] (-> (f {:cell-state {}}) :cell-state :completionPct))
                  [cell/transition-to-ductwork-routed
                   cell/transition-to-conduit-routed
                   cell/transition-to-piping-routed
                   cell/transition-to-pressure-test
                   cell/transition-to-witness-attestation
                   cell/emit-mep-signoff-record])]
    (is (= [20 40 60 75 85 100] (vec pcts)))
    (is (= (count pcts) (count (set pcts))) "no duplicate completionPct")
    (is (= 100 (apply max pcts)) "transitions reach 100%")))

(deftest test-full-chain-init-to-complete
  ;; thread the chain through initialize → … → emit so the next-node hand-offs
  ;; line up with the graph edges
  (let [s-init (cell/initialize-state {"projectId" "proj-蔵-001"})
        s0 (cell/transition-to-ductwork-routed s-init)
        s1 (cell/transition-to-conduit-routed s0)
        s2 (cell/transition-to-piping-routed s1)
        s3 (cell/transition-to-pressure-test s2)
        s4 (cell/transition-to-witness-attestation s3)
        s5 (cell/emit-mep-signoff-record s4)]
    (is (= "ductwork" (:next-node s-init)))
    (is (= "proj-蔵-001" (:projectId (:cell-state s-init))))
    (is (= "route_conduit" (:next-node s0)))
    (is (= "route_piping" (:next-node s1)))
    (is (= "pressure_test" (:next-node s2)))
    (is (= "witness" (:next-node s3)) "mock pressure test passes → witness")
    (is (= "emit_record" (:next-node s4)))
    (is (= "end" (:next-node s5)))
    (is (= "complete" (:phase (:cell-state s5))))
    (is (= 100 (:completionPct (:cell-state s5))))))

;; ── projectId threads into the emitted mepSignoffRecord ───────────

(deftest test-emit-record-carries-project-id-and-attesting-robots
  (let [s3 (cell/transition-to-pressure-test {:cell-state {:projectId "P-9"}})
        s4 (cell/transition-to-witness-attestation s3)
        s5 (cell/emit-mep-signoff-record s4)
        record (:mep-signoff-record s5)]
    (is (some? record))
    (is (= "P-9" (get record "projectId")))
    (is (= "mep_installation" (get record "phase")))
    ;; the record captures the INCOMING pct (85), matching the Python order
    ;; (record built from ms before ms.completionPct is bumped to 100)
    (is (= 85 (get record "completionPct")))
    (is (= 100 (:completionPct (:cell-state s5))) "the state itself reaches 100%")
    (is (= 3 (count (get record "attestingRobots"))) "≥2 robot Ed25519 sigs (G3)")
    (is (= "did:web:etzhayyim.com:otete-unit-2"
           (get (first (get record "attestingRobots")) "robotDid")))
    (is (= [] (get record "anomalyFlags")) "happy path → no anomalies")))

;; ── pressure-test branch + halt path ──────────────────────────────

(deftest test-pressure-test-mock-passes-to-witness
  (let [out (cell/transition-to-pressure-test {:cell-state {}})]
    (is (= "witness" (:next-node out)))
    (is (= "pressure_test" (:phase (:cell-state out))))
    (is (= 75 (:completionPct (:cell-state out))))
    (is (true? (get (:testResults (:cell-state out)) "water_test_passed")))))

(deftest test-halt-emits-alert-record
  ;; halt is reachable structurally even though the mock test passes; it carries
  ;; the alert_record + routes to end
  (let [out (cell/halt-on-test-failure {:cell-state {:anomalyFlags ["x"]}})
        alert (:alert-record out)]
    (is (= "end" (:next-node out)))
    (is (= "mep_halt" (get alert "event")))
    (is (= "pressure_test_failure" (get alert "reason")))
    (is (= ["x"] (get alert "anomalies")))))

;; ── payload string-key identity (Python dict keys stay strings) ───

(deftest test-mock-payload-keys-stay-strings
  (let [hvac (-> (cell/transition-to-ductwork-routed {:cell-state {}})
                 :cell-state :hvacPlan)]
    (is (contains? hvac "main_trunk_length_m") "Python payload keys stay strings")
    (is (= 45 (get hvac "leakage_rate_cfm_at_25pa")))
    (is (true? (get hvac "sealed_duct_test_passed")))))

;; ── closed MepState surface (MepState(**...) parity) ──────────────

(deftest test-closed-mep-state-surface
  (testing "an unexpected cell-state field raises (MepState(**...) TypeError parity)"
    (is (thrown? clojure.lang.ExceptionInfo
                 (cell/transition-to-ductwork-routed {:cell-state {:bogus 1}})))))

;; ── graph shape (data-described LangGraph) ────────────────────────

(deftest test-graph-is-plain-data
  (let [g (:graph (cell/make-mep-installation-cell))]
    (is (= 8 (count (:nodes g))) "8-node super-step graph")
    (is (= "init" (:entry g)))
    (is (contains? (:conditional g) "test") "pressure_test branches conditionally")
    (let [{:keys [router targets]} (get (:conditional g) "test")]
      (is (= "witness" (get targets (router {:next-node "witness"}))))
      (is (= "halt" (get targets (router {:next-node "halt"})))))))

;; ── R0 solve gate (cell .solve() raises — ADR-2605250715) ─────────

(deftest test-solve-raises-at-r0
  (testing "solve raises until Council R1 activation (the R0 scaffold gate)"
    (is (thrown? clojure.lang.ExceptionInfo (cell/solve {} {})))
    (is (thrown-with-msg? clojure.lang.ExceptionInfo #"ADR-2605250715"
                          (cell/solve (cell/make-mep-installation-cell) {})))))

#?(:clj
   (defn -main [& _]
     (run-tests 'tatekata.cells.mep-installation.test-cell)))
