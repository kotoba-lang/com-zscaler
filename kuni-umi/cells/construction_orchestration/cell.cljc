(ns kuni-umi.cells.construction-orchestration.cell
  "ConstructionOrchestrationCell — Phase 3 of the kuni-umi 国産み 4-phase
  planetary-infra deployment workflow. 1:1 port of
  `cells/construction_orchestration/cell.py`
  (ADR-2605201400 §3 / ADR-2605202200 cell-runtime contract).

  Trigger:  MST listener on
            `com.etzhayyim.apps.etzhayyim.kuniUmi.proposeDeploymentPlan`
            (accepted=true, decision=accept).
  Effect:   dispatch Giemon fleet → drive Pregel super-step (1 cell = 1 Giemon
            unit) → stream `recordConstructionProgress` at 1–10 Hz → witness
            verify at each super-step → on completion emit handoff blob.
  Murakumo: joseph (leader).

  CRITICAL invariants:
    - cadence-hz-max = 10 (hard-RT motion stays in Giemon firmware) — enforced
      in `build-graph` (raises if exceeded).
    - witness N ≥ 2 per super-step (constitutional, ADR-2605201400 §9) — enforced
      in `witness-router`.

  See site-survey cell.cljc for the cell_runtime SHIM + plain-data StateGraph
  conventions. Substrate-gated nodes raise `ex-info` (NotImplementedError parity);
  the pure logic is `witness-router` + the `build-graph` cadence check."
  (:require [clojure.string :as str]))

;; ── Literal enums (Python value identities preserved) ─────────────

(def phases
  "The closed `phase` Literal vocabulary."
  {:queued       "queued"
   :in-progress  "in-progress"
   :complete     "complete"
   :halted       "halted"
   :handoff-ready "handoff-ready"})

(def trigger-nsid
  "MST listener trigger NSID — proposeDeploymentPlan."
  "com.etzhayyim.apps.etzhayyim.kuniUmi.proposeDeploymentPlan")

(def progress-nsid
  "Streamed progress record NSID."
  "com.etzhayyim.apps.etzhayyim.kuniUmi.recordConstructionProgress")

(def cadence-hz-max-default
  "Constitutional cadence ceiling: hard-RT motion stays in Giemon firmware
  (ADR-2605201400 §3 Phase 3)."
  10)

(def witness-invariant-min
  "Constitutional invariant: N ≥ 2 robot signatures per super-step (§9)."
  2)

;; ── ConstructionOrchestrationState (TypedDict, total=False → map) ──

(def construction-orchestration-state
  "ConstructionOrchestrationState fresh value — all fields unset (nil)."
  {;; Input (from proposeDeploymentPlan)
   :planDid             nil
   :siteDid             nil
   :fleetDid            nil
   :fleetAllocation     nil
   ;; Construction progress
   :superStepIndex      nil
   :currentCellId       nil
   :phase               nil   ;; ∈ phases vals
   :completionPct       nil
   :sensorBlobCids      nil
   :anomalyFlags        nil
   :witnessAttestations nil
   ;; Outputs
   :progressDids        nil
   :handoffBlob         nil
   :finalized           nil
   ;; Audit
   :_event_uri          nil
   :_event_nsid         nil})

;; ── kotodama.cell_runtime shim (minimal DI + event helpers) ───────

(defn make-cell-deps
  "Shim of `kotodama.cell_runtime.CellDeps` (DI container). A plain map; every
  substrate / LLM port defaults nil, config an empty map."
  [& {:keys [cell-name node-name checkpointer
             sdk base-l2-port geth-private-port pds-client
             llm-primary llm-fallback-local config]}]
  {:cell-name          cell-name
   :node-name          node-name
   :checkpointer       checkpointer
   :sdk                sdk
   :base-l2-port       base-l2-port
   :geth-private-port  geth-private-port
   :pds-client         pds-client
   :llm-primary        llm-primary
   :llm-fallback-local llm-fallback-local
   :config             (or config {})})

(defn default-state-from-event
  "Shim of `cell_runtime.default_state_from_event`."
  [event-record _nsid]
  (merge {"_event_uri"        (get event-record "uri")
          "_event_cid"        (get event-record "cid")
          "_event_indexed_at" (get event-record "indexedAt")}
         (get event-record "value" {})))

(defn default-thread-id-from-event
  "Shim of `cell_runtime.default_thread_id_from_event`."
  [event-record nsid]
  (str nsid ":" (get event-record "rkey" "unknown")))

;; ── Nodes (all substrate-gated → raise) ───────────────────────────

(defn- not-implemented
  [from msg]
  (throw (ex-info msg {:kuni-umi/not-implemented true :from from})))

(defn dispatch-fleet
  "open_robo.fleet.dispatch(fleetDid, taskCid). Port of `dispatch_fleet`."
  [_state _deps]
  (not-implemented "dispatch_fleet"
                   "Requires kotodama.open_robo.fleet.dispatch — not yet implemented."))

(defn super-step-loop
  "Drive Pregel super-step iteration (cadence ≤ 10 Hz). Port of `super_step_loop`."
  [_state _deps]
  (not-implemented "super_step_loop"
                   (str "Requires open-robo BSP super-step driver + cadence enforcement; "
                        "per ADR-2605151200 §6 and ADR-2605201400 §3 Phase 3.")))

(defn stream-progress
  "Emit `recordConstructionProgress` MST record. Port of `stream_progress`."
  [_state _deps]
  (not-implemented "stream_progress"
                   (str "Requires deps.sdk for MST write of " progress-nsid
                        " + witness attestation field.")))

(defn witness-verify
  "Collect N ≥ 2 robot signatures at each super-step boundary. Port of `witness_verify`."
  [_state _deps]
  (not-implemented "witness_verify"
                   (str "Requires AuditWitnessCell coordination (sibling cell on levi). "
                        "Constitutional invariant: N >= 2 mandatory per super-step boundary.")))

(defn handler-anomaly
  "Handle sensor/actuator anomaly → encrypted audit event → halt or resume.
  Port of `handler_anomaly`."
  [_state _deps]
  (not-implemented "handler_anomaly"
                   (str "Requires deps.sdk for XChaCha20-Poly1305 envelope "
                        "(ADR-2605181100) + Council notification.")))

(defn complete-handoff
  "Prepare handover blob → triggers CommissioningCell. Port of `complete_handoff`."
  [_state _deps]
  (not-implemented "complete_handoff"
                   (str "Requires as-built CIM record update via open-* utility "
                        "lexicons + handoff blob CID pin.")))

;; ── Router (witness_router conditional edge) ──────────────────────

(defn witness-router
  "Conditional router after witness-verify. Port of the inner `witness_router`:
    - < 2 attestations (constitutional violation) → handler_anomaly
    - anomalyFlags present                        → handler_anomaly
    - phase = handoff-ready                        → complete_handoff
    - else                                         → super_step_loop (next step)."
  [state]
  (let [attestations (get state :witnessAttestations [])]
    (cond
      (< (count attestations) witness-invariant-min) "handler_anomaly"
      (seq (get state :anomalyFlags))                "handler_anomaly"
      (= (get state :phase) "handoff-ready")         "complete_handoff"
      :else                                          "super_step_loop")))

;; ── build_graph (LangGraph wiring → plain data) ───────────────────

(defn build-graph
  "Build the ConstructionOrchestrationCell graph as plain data. Port of
  `build_graph(deps)`. Enforces the cadence-hz-max ≤ 10 constitutional invariant
  on instantiation (raises ex-info, mirroring the Python ValueError)."
  [deps]
  (let [cadence-hz-max (get (:config deps) "cadence_hz_max" cadence-hz-max-default)]
    (when (> cadence-hz-max cadence-hz-max-default)
      (throw (ex-info
              (str "ConstructionOrchestrationCell cadence_hz_max=" cadence-hz-max
                   " exceeds constitutional limit " cadence-hz-max-default
                   " (per ADR-2605201400 §3 Phase 3 + ADR-2605202200). "
                   "Hard-RT motion must stay in Giemon firmware.")
              {:kuni-umi/cadence-violation true :cadence-hz-max cadence-hz-max})))
    {:nodes ["dispatch_fleet" "super_step_loop" "stream_progress"
             "witness_verify" "handler_anomaly" "complete_handoff"]
     :steps {"dispatch_fleet"   (fn [s] (dispatch-fleet s deps))
             "super_step_loop"  (fn [s] (super-step-loop s deps))
             "stream_progress"  (fn [s] (stream-progress s deps))
             "witness_verify"   (fn [s] (witness-verify s deps))
             "handler_anomaly"  (fn [s] (handler-anomaly s deps))
             "complete_handoff" (fn [s] (complete-handoff s deps))}
     :edges [["START" "dispatch_fleet"]
             ["dispatch_fleet" "super_step_loop"]
             ["super_step_loop" "stream_progress"]
             ["stream_progress" "witness_verify"]
             ["handler_anomaly" "END"]
             ["complete_handoff" "END"]]
     :conditional-edges {"witness_verify" witness-router}
     :checkpointer (:checkpointer deps)}))

;; ── cell-runtime contract exports (ADR-2605202200 §1) ─────────────

(defn state-from-event
  "Port of `state_from_event` — delegates to the default shim."
  [event-record nsid]
  (default-state-from-event event-record nsid))

(defn thread-id-from-event
  "One thread per plan. Port of `thread_id_from_event` — planDid → rkey."
  [event-record _nsid]
  (let [value    (get event-record "value" {})
        plan-did (or (get value "planDid")
                     (get event-record "rkey" "unknown"))]
    (str "ConstructionOrchestrationCell:" plan-did)))

(defn healthz-extra
  "Cell-specific /healthz fields. Port of `healthz_extra`."
  [deps]
  {"phase"                 "3-construction"
   "trigger_nsid"          trigger-nsid
   "cadence_hz_max"        (get (:config deps) "cadence_hz_max" cadence-hz-max-default)
   "witness_invariant_min" witness-invariant-min
   "hard_rt_owner"         "Giemon firmware (open-robo + open-ot field tier WAMR)"})
