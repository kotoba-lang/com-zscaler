(ns kuni-umi.cells.audit-witness.cell
  "AuditWitnessCell — continuous robot-witness audit cell of the kuni-umi 国産み
  workflow. 1:1 port of `cells/audit_witness/cell.py`
  (ADR-2605201400 §9 / ADR-2605202200 cell-runtime contract).

  Religious-corp's self-inspection layer — NOT dependent on state inspectors.
  N ≥ 2 independent Giemon robot signatures attest every super-step boundary +
  every recordPhysicalAuditEvent class event.

  Trigger:  continuous + super-step boundary + event-driven; listens to
            `com.etzhayyim.apps.etzhayyim.kuniUmi.recordPhysicalAuditEvent`.
  Effect:   verify N≥2 signatures → on mismatch escalate to Council Lv6+ → feed
            Phenotype.effectiveMultiplier delta (ADR-2605192230).
  Murakumo: levi (leader).

  CRITICAL invariants:
    - N ≥ 2 mandatory always (constitutional).
    - injury / anomaly events trigger Council escalation.
    - witness_mismatch → automatic halt of the relevant ConstructionOrchestrationCell.

  See site-survey cell.cljc for the cell_runtime SHIM conventions. The pure nodes
  are `verify-signatures` + `compare-blobs` + the two routers; substrate-gated
  nodes raise `ex-info` (NotImplementedError parity)."
  (:require [clojure.string :as str]))

;; ── Literal enums (Python value identities preserved) ─────────────

(def event-classes
  "The closed `eventClass` Literal vocabulary."
  {:anomaly          "anomaly"
   :intrusion        "intrusion"
   :injury           "injury"
   :compliance-check "compliance-check"
   :community-event  "community-event"})

(def trigger-nsid
  "MST listener trigger NSID — recordPhysicalAuditEvent."
  "com.etzhayyim.apps.etzhayyim.kuniUmi.recordPhysicalAuditEvent")

(def witness-invariant-min
  "Constitutional invariant: N ≥ 2, NEVER reduce (ADR-2605201400 §9)."
  2)

(def phenotype-feedback-classes
  "Event classes that emit a Phenotype delta (attest_router → feedback_phenotype)."
  #{"injury" "community-event"})

;; ── AuditWitnessState (TypedDict, total=False → plain map) ─────────

(def audit-witness-state
  "AuditWitnessState fresh value — all fields unset (nil)."
  {;; Input
   :siteDid                 nil
   :planDid                 nil
   :eventClass              nil   ;; ∈ event-classes vals
   :subtype                 nil
   :occurredAt              nil
   :evidenceCid             nil
   :participantDids         nil
   :witnessAttestations     nil
   ;; Verification outputs
   :signaturesVerified      nil
   :blobConsistencyOk       nil
   :invariantViolated       nil
   ;; Escalation
   :councilEscalated        nil
   :auditDid                nil
   ;; Phenotype feedback (ADR-2605192230)
   :phenotypeDeltaTargetDid nil
   :phenotypeDeltaBps       nil
   ;; Audit
   :_event_uri              nil
   :_event_nsid             nil})

;; ── kotodama.cell_runtime shim (minimal DI + event helpers) ───────

(defn make-cell-deps
  "Shim of `kotodama.cell_runtime.CellDeps` (DI container)."
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

;; ── Nodes ─────────────────────────────────────────────────────────

(defn- not-implemented
  [from msg]
  (throw (ex-info msg {:kuni-umi/not-implemented true :from from})))

(defn poll-witnesses
  "Fetch latest sensor blob CIDs from N ≥ 2 independent robots. Port of `poll_witnesses`."
  [_state _deps]
  (not-implemented "poll_witnesses" "Requires kotodama.open_robo.fleet poll endpoint."))

(defn verify-signatures
  "Count attestations carrying a signature → signaturesVerified. Port of
  `verify_signatures` (pure; actual Ed25519 verify is a TODO in both impls)."
  [state _deps]
  (let [attestations (get state :witnessAttestations [])]
    (assoc state :signaturesVerified
           (count (filter #(get % :signature) attestations)))))

(defn compare-blobs
  "Shared-channel blob hashes MUST match within tolerance. Port of `compare_blobs`
  (pure): blobConsistencyOk = (≤1 distinct blobHash among those present, else
  false when none); invariantViolated = signaturesVerified < 2 OR not consistent."
  [state _deps]
  (let [attestations (get state :witnessAttestations [])
        blob-hashes  (->> attestations (keep #(get % :blobHash)))
        consistent   (if (seq blob-hashes)
                       (<= (count (distinct blob-hashes)) 1)
                       false)]
    (assoc state
           :blobConsistencyOk consistent
           :invariantViolated (or (< (get state :signaturesVerified 0) witness-invariant-min)
                                  (not consistent)))))

(defn attest-record
  "Write `recordPhysicalAuditEvent` MST record (per-class envelope routing).
  Port of `attest_record`."
  [_state _deps]
  (not-implemented "attest_record"
                   (str "Requires deps.sdk for MST write + per-class XChaCha20-Poly1305 "
                        "envelope routing.")))

(defn escalate-mismatch
  "Witness mismatch → automatic halt + Council Lv6+ escalation. Port of
  `escalate_mismatch` (sets councilEscalated then raises, mirroring the Python)."
  [_state _deps]
  (not-implemented "escalate_mismatch"
                   (str "Requires council_deliberation cell coordination + "
                        "ConstructionOrchestrationCell halt signal.")))

(defn feedback-phenotype
  "Emit Phenotype delta (ADR-2605192230) for injury / community-event. Port of
  `feedback_phenotype`."
  [_state _deps]
  (not-implemented "feedback_phenotype"
                   "Requires deps.geth_private_port + Phenotype.sol contract address (ADR-2605172300)."))

;; ── Routers (conditional edges) ───────────────────────────────────

(defn consistency-router
  "After compare-blobs: invariantViolated → escalate_mismatch, else attest_record.
  Port of `consistency_router`."
  [state]
  (if (get state :invariantViolated) "escalate_mismatch" "attest_record"))

(defn attest-router
  "After attest-record: eventClass ∈ {injury, community-event} → feedback_phenotype,
  else END. Port of `attest_router`."
  [state]
  (if (contains? phenotype-feedback-classes (get state :eventClass))
    "feedback_phenotype"
    "END"))

;; ── build_graph (LangGraph wiring → plain data) ───────────────────

(defn build-graph
  "Build the AuditWitnessCell graph as plain data. Port of `build_graph(deps)` —
  two conditional edges (consistency-router after compare_blobs, attest-router
  after attest_record)."
  [deps]
  {:nodes ["poll_witnesses" "verify_signatures" "compare_blobs"
           "attest_record" "escalate_mismatch" "feedback_phenotype"]
   :steps {"poll_witnesses"     (fn [s] (poll-witnesses s deps))
           "verify_signatures"  (fn [s] (verify-signatures s deps))
           "compare_blobs"      (fn [s] (compare-blobs s deps))
           "attest_record"      (fn [s] (attest-record s deps))
           "escalate_mismatch"  (fn [s] (escalate-mismatch s deps))
           "feedback_phenotype" (fn [s] (feedback-phenotype s deps))}
   :edges [["START" "poll_witnesses"]
           ["poll_witnesses" "verify_signatures"]
           ["verify_signatures" "compare_blobs"]
           ["escalate_mismatch" "END"]
           ["feedback_phenotype" "END"]]
   :conditional-edges {"compare_blobs" consistency-router
                       "attest_record" attest-router}
   :checkpointer (:checkpointer deps)})

;; ── cell-runtime contract exports (ADR-2605202200 §1) ─────────────

(defn state-from-event
  "Port of `state_from_event` — delegates to the default shim."
  [event-record nsid]
  (default-state-from-event event-record nsid))

(defn thread-id-from-event
  "One thread per audit event (idempotent). Port of `thread_id_from_event`."
  [event-record _nsid]
  (str "AuditWitnessCell:" (get event-record "rkey" "unknown")))

(defn healthz-extra
  "Cell-specific /healthz fields. Port of `healthz_extra`."
  [_deps]
  {"role"                     "religious-corp self-inspection (constitutional)"
   "trigger_nsid"             trigger-nsid
   "witness_invariant_min"    witness-invariant-min
   "constitutional_invariant" "N >= 2 NEVER reduce (ADR-2605201400 §9)"
   "feeds_phenotype"          true})
