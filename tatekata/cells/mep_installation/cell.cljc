(ns tatekata.cells.mep-installation.cell
  "MepInstallationCell — tatekata 建方 R1+ Pregel cell (MEP installation:
  HVAC / electrical / plumbing routed by the Otete arm). Per ADR-2605250715 §2.

  1:1 port of `cells/mep_installation/cell.py` + its sibling `state_machine.py`
  (the Python `cell.py` imports the seven transition / emit / halt fns from
  `state_machine.py`, so both are folded into this one namespace — mirroring how
  the LangGraph wrapper imports the pure transitions, the funadaiku/sea_trial
  pattern).

  R0 scaffold: phase transitions are structural placeholders; `solve` raises
  until Council Lv6+ + an SME MEP engineer ratify the R1 activation
  (ADR-2605250715). Input: structuralAuthRecord (from structural_assembly).
  Output: mepSignoffRecord (MST, witness-signed, ≥2 robot Ed25519 sigs).

  8-node LangGraph super-step state machine:
    START → init → ductwork → conduit → piping → pressure_test
          → witness → emit → END   (pressure_test may branch to halt → END)

  Conventions (mimamori/methods/bond.cljc + funadaiku/sea_trial house style):
    - MepPhase enum → keyword-string map preserving Python value identities
    - @dataclass MepState → a plain map with camelCase-keyword keys matching the
      Python `ms.__dict__` surface (projectId / completionPct / hvacPlan …);
      structural keys (:cell-state etc.) follow house kebab style
    - Python field defaults preserved (\"unknown\" projectId, nil optionals)
    - Python \":…\"-free payload / mock-record string keys stay strings
    - transitions are pure fns returning {:cell-state … :next-node …}; the closed
      MepState surface + R0 solve gate → ex-info"
  (:require [clojure.string :as str]))

;; ── MepPhase (enum — Python value identities preserved) ────────────

(def mep-phases
  "The closed MepPhase vocabulary. Keyed by the idiomatic Clojure enum keyword;
  the value is the Python `MepPhase.<X>.value` string identity."
  {:init            "init"
   :ductwork-routed "ductwork_routed"   ;; HVAC Otete arm trajectory
   :conduit-routed  "conduit_routed"    ;; Electrical Otete arm trajectory
   :piping-routed   "piping_routed"     ;; Water/gas Otete arm trajectory
   :pressure-test   "pressure_test"     ;; Pneumatic/hydro testing
   :witness-wait    "witness_wait"      ;; Fixed-point: wait ≥2 robot sigs
   :test-fail       "test_fail"         ;; Pressure test anomaly detected
   :complete        "complete"})

(def phase-init            (:init mep-phases))             ;; "init"
(def phase-ductwork-routed (:ductwork-routed mep-phases))  ;; "ductwork_routed"
(def phase-conduit-routed  (:conduit-routed mep-phases))   ;; "conduit_routed"
(def phase-piping-routed   (:piping-routed mep-phases))    ;; "piping_routed"
(def phase-pressure-test   (:pressure-test mep-phases))    ;; "pressure_test"
(def phase-witness-wait    (:witness-wait mep-phases))     ;; "witness_wait"
(def phase-test-fail       (:test-fail mep-phases))        ;; "test_fail"
(def phase-complete        (:complete mep-phases))         ;; "complete"

;; ── MepState (dataclass → plain map, camelCase keys, field defaults) ──

(def mep-state
  "MepState default value — the @dataclass field defaults as a plain map. The
  three Python fields without a default (phase / projectId / completionPct) are
  given their natural zero values so a bare `make-mep-state` mirrors a fresh 0%
  INIT record; the optional fields default to nil (Python `None`)."
  {:phase           phase-init   ;; MepPhase.INIT.value
   :projectId       "unknown"
   :completionPct   0            ;; 0–100
   :hvacPlan        nil          ;; Ductwork routing
   :electricalPlan  nil          ;; Conduit routing
   :plumbingPlan    nil          ;; Piping routing
   :testResults     nil          ;; Pressure test data
   :anomalyFlags    nil
   :robotSignatures nil          ;; [{robotDid, timestamp, sig}, ...]
   :photoCid        nil          ;; IPFS CID of MEP installation photos
   :errorMsg        nil})

(defn make-mep-state
  "Construct a MepState map from a partial mep-state map, filling the dataclass
  defaults (MepState(**state.get(\"mep_state\", {}))). Unknown keys → ex-info
  (closed MepState surface — MepState(**...) would TypeError on an unexpected
  kwarg)."
  [ms]
  (let [ms (or ms {})
        allowed (set (keys mep-state))
        extra (remove allowed (keys ms))]
    (when (seq extra)
      (throw (ex-info (str "unknown MepState field(s): " (vec extra))
                      {:tatekata/closed-vocab true :extra (vec extra)})))
    (merge mep-state ms)))

;; ── transitions (pure; 1:1 port of state_machine.py) ──────────────

(defn transition-to-ductwork-routed
  "INIT → DUCTWORK_ROUTED: HVAC Otete arm trajectory synthesis.
  Port of `transition_to_ductwork_routed(state)`."
  [state]
  (let [ms (make-mep-state (:cell-state state))
        mock-hvac {"main_trunk_length_m" 180
                   "branch_ducts_count" 12
                   "supply_cfm" 4500
                   "return_cfm" 4000
                   "filter_location" "return_plenum"
                   "duct_insulation_r_value" 8
                   "sealed_duct_test_passed" true
                   "leakage_rate_cfm_at_25pa" 45} ;; spec ≤50 CFM@25Pa per ASHRAE 90.1
        ms (assoc ms
                  :phase phase-ductwork-routed
                  :hvacPlan mock-hvac
                  :completionPct 20)]
    {:cell-state ms :next-node "route_conduit"}))

(defn transition-to-conduit-routed
  "DUCTWORK_ROUTED → CONDUIT_ROUTED: Electrical Otete arm trajectory.
  Port of `transition_to_conduit_routed(state)`."
  [state]
  (let [ms (make-mep-state (:cell-state state))
        mock-electrical {"main_service_amperage" 200
                         "conduit_total_length_m" 420
                         "wire_pulls_completed" 12
                         "panel_breakers_installed" 24
                         "ground_resistance_ohms" 2.5 ;; spec ≤5 ohms per NEC
                         "insulation_test_passed" true
                         "hi_pot_test_kv" 1500}
        ms (assoc ms
                  :phase phase-conduit-routed
                  :electricalPlan mock-electrical
                  :completionPct 40)]
    {:cell-state ms :next-node "route_piping"}))

(defn transition-to-piping-routed
  "CONDUIT_ROUTED → PIPING_ROUTED: Water/gas Otete arm trajectory.
  Port of `transition_to_piping_routed(state)`."
  [state]
  (let [ms (make-mep-state (:cell-state state))
        mock-plumbing {"water_service_size_mm" 25
                       "water_piping_length_m" 210
                       "supply_pressure_bar" 2.5
                       "hot_water_setpoint_c" 60
                       "gas_service_size_mm" 19
                       "gas_piping_fittings_sealed" true
                       "sanitaryDwvPipingLength_m" 180
                       "storm_drainPipingLength_m" 95
                       "trap_priming_devices_installed" true}
        ms (assoc ms
                  :phase phase-piping-routed
                  :plumbingPlan mock-plumbing
                  :completionPct 60)]
    {:cell-state ms :next-node "pressure_test"}))

(defn transition-to-pressure-test
  "PIPING_ROUTED → PRESSURE_TEST or TEST_FAIL: Hydro/pneumatic testing.
  Port of `transition_to_pressure_test(state)`. The mock test passes, so the
  happy path routes to witness; the TEST_FAIL branch is preserved structurally."
  [state]
  (let [ms (make-mep-state (:cell-state state))
        mock-test {"water_system_test_psi" 150
                   "water_hold_time_minutes" 30
                   "water_leak_observed_ml" 0
                   "water_test_passed" true
                   "gas_system_test_psi" 10
                   "gas_hold_time_hours" 1
                   "gas_leakage_detected" false
                   "gas_test_passed" true
                   "drain_system_smoke_test_passed" true}
        ms (assoc ms
                  :phase phase-pressure-test
                  :testResults mock-test
                  :completionPct 75)]
    ;; Check for test failures
    (if-not (and (get mock-test "water_test_passed")
                 (get mock-test "gas_test_passed")
                 (get mock-test "drain_system_smoke_test_passed"))
      (let [ms (assoc ms
                      :phase phase-test-fail
                      :anomalyFlags ["pressure_test_failure"]
                      :errorMsg (str "Pressure test failed: water/gas/drain "
                                     "system leakage detected"))]
        {:cell-state ms :next-node "halt"})
      {:cell-state ms :next-node "witness"})))

(defn transition-to-witness-attestation
  "PRESSURE_TEST → WITNESS_WAIT: Collect ≥2 robot Ed25519 signatures.
  Port of `transition_to_witness_attestation(state)`."
  [state]
  (let [ms (make-mep-state (:cell-state state))
        mock-sigs [{"robotDid" "did:web:etzhayyim.com:otete-unit-2"
                    "role" "hvac_executor"
                    "timestamp" "2026-05-26T16:20:30Z"
                    "signature" "pL7mN2qR5uT8vW..."}
                   {"robotDid" "did:web:etzhayyim.com:otete-unit-3"
                    "role" "electrical_executor"
                    "timestamp" "2026-05-26T16:20:35Z"
                    "signature" "xK3yZ8aB1cD4eF..."}
                   {"robotDid" "did:web:etzhayyim.com:otete-unit-4"
                    "role" "plumbing_executor"
                    "timestamp" "2026-05-26T16:20:40Z"
                    "signature" "gH5iJ9kL2mN3oP..."}]
        ms (assoc ms
                  :robotSignatures mock-sigs
                  :phase phase-witness-wait
                  :completionPct 85)]
    {:cell-state ms :next-node "emit_record"}))

(defn emit-mep-signoff-record
  "WITNESS_WAIT → COMPLETE: Emit mepSignoffRecord to MST.
  Port of `emit_mep_signoff_record(state)`. Returns the extra :mep-signoff-record
  key (Python `mep_signoff_record`) alongside the next step."
  [state]
  (let [ms (make-mep-state (:cell-state state))
        record {"projectId" (:projectId ms)
                "phase" "mep_installation"
                "completionPct" (:completionPct ms)
                "recordedDate" "2026-05-26T16:21:00Z"
                "hvacSummary" (:hvacPlan ms)
                "electricalSummary" (:electricalPlan ms)
                "plumbingSummary" (:plumbingPlan ms)
                "testResults" (:testResults ms)
                "anomalyFlags" (or (:anomalyFlags ms) [])
                "attestingRobots" (vec
                                   (for [sig (or (:robotSignatures ms) [])]
                                     {"robotDid" (get sig "robotDid")
                                      "role" (get sig "role")
                                      "timestamp" (get sig "timestamp")
                                      "signature" (get sig "signature")}))}
        ms (assoc ms :phase phase-complete :completionPct 100)]
    {:cell-state ms
     :mep-signoff-record record
     :next-node "end"}))

(defn halt-on-test-failure
  "TEST_FAIL: Halt MEP, escalate to mechanical/electrical/plumbing contractor.
  Port of `halt_on_test_failure(state)`. Returns the extra :alert-record key
  (Python `alert_record`)."
  [state]
  (let [ms (make-mep-state (:cell-state state))
        alert-record {"event" "mep_halt"
                      "reason" "pressure_test_failure"
                      "anomalies" (:anomalyFlags ms)
                      "timestamp" "2026-05-26T16:20:50Z"
                      "escalation" "contractor_review_required"
                      "corrective_action" "Retest after repairs complete"}]
    {:cell-state ms
     :alert-record alert-record
     :next-node "end"}))

;; ── initialize (the LangGraph `_initialize_state` node) ───────────

(defn initialize-state
  "Initialize MEP state from input — port of `MepInstallationCell._initialize_state`.
  Reads projectId from the top-level state (default \"unknown\") and seeds a fresh
  0% INIT MepState."
  [state]
  (let [project-id (get state "projectId" "unknown")
        ms (make-mep-state {:phase phase-init
                            :projectId project-id
                            :completionPct 0})]
    {:cell-state ms :next-node "ductwork"}))

;; ── MepInstallationCell (LangGraph wrapper → data graph + R0 solve) ──

(def mep-installation-graph
  "The compiled cell graph as plain data (`MepInstallationCell._build_graph`). The
  8-node LangGraph wiring START→init→ductwork→…→emit→END (with the conditional
  pressure-test branch test→{witness,halt}) is described as an edge list + a
  node→step map + the conditional router; pure data, no langgraph dependency."
  {:nodes ["init" "ductwork" "conduit" "piping" "test" "witness" "emit" "halt"]
   :steps {"init"     initialize-state
           "ductwork" transition-to-ductwork-routed
           "conduit"  transition-to-conduit-routed
           "piping"   transition-to-piping-routed
           "test"     transition-to-pressure-test
           "witness"  transition-to-witness-attestation
           "emit"     emit-mep-signoff-record
           "halt"     halt-on-test-failure}
   :entry "init"
   :edges [["init" "ductwork"]
           ["ductwork" "conduit"]
           ["conduit" "piping"]
           ["piping" "test"]
           ["witness" "emit"]
           ["emit" "END"]
           ["halt" "END"]]
   ;; conditional: route on the step's :next-node (Python route_test default "witness")
   :conditional {"test" {:router (fn [step] (get step :next-node "witness"))
                         :targets {"witness" "witness" "halt" "halt"}}}})

(defn make-mep-installation-cell
  "Construct a MepInstallationCell — MEP installation orchestration (R0 scaffold).
  Port of `MepInstallationCell.__init__` (self.graph = None until built)."
  [& _]
  {:graph mep-installation-graph})

(defn solve
  "Execute the cell — R0 scaffold raises until R1 activation. Port of
  `MepInstallationCell.solve`. The cell map is accepted as the first arg to mirror
  the Python instance-method shape; both args are ignored."
  [& _]
  (throw (ex-info (str "tatekata R0 scaffold: activate via Council "
                       "ADR-2605250715 post-ratification (Lv6+ + SME MEP engineer)")
                  {:tatekata/r0-gate true})))
