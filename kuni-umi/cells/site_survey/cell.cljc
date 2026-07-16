(ns kuni-umi.cells.site-survey.cell
  "SiteSurveyCell — Phase 1 of the kuni-umi 国産み 4-phase planetary-infra
  deployment workflow. 1:1 port of `cells/site_survey/cell.py`
  (ADR-2605201400 §3 / ADR-2605201500 / ADR-2605202200 cell-runtime contract).

  Trigger:  MST listener on
            `com.etzhayyim.apps.etzhayyim.kuniUmi.defineDeploymentSite`
  Effect:   dispatch Giemon scout fleet → collect sensor blobs → DMN
            jurisdiction gate → N ≥ 2 witness signatures → emit
            `submitSiteSurvey` MST record.
  Murakumo: naphtali (leader).

  ── kotodama.cell_runtime SHIM ────────────────────────────────────
  The Python module does `from kotodama.cell_runtime import (CellDeps,
  default_state_from_event, default_thread_id_from_event)`. That is the
  Pregel cell-runner FRAMEWORK (ADR-2605202200) — NOT pure cell logic. Rather
  than require the whole runtime, this ns reproduces the *minimal* surface the
  cell actually touches, so the transitions / graph / solve-gate are testable
  on bb/SCI with no Python runtime:

    - `make-cell-deps`            ← CellDeps frozen dataclass (DI container);
                                     a plain map with the ADR-2605202200 §2
                                     fields, all substrate ports default nil.
    - `default-state-from-event`  ← cell_runtime.default_state_from_event:
                                     {_event_uri _event_cid _event_indexed_at
                                      **value}.
    - `default-thread-id-from-event` ← cell_runtime.default_thread_id_from_event:
                                     \"{nsid}:{rkey}\".

  Nothing else from the runtime (MstCheckpointSaver, cell_host, the subprocess
  spawn / SIGTERM lifecycle, the /healthz HTTP server) is reproduced — those
  are runner concerns, not cell logic.

  ── Conventions (funadaiku sea_trial cell.cljc + mimamori bond.cljc) ──
    - SiteSurveyState TypedDict → plain map; camelCase-keyword keys preserved
      to match the Python `state[...]` surface the LangGraph step threads.
    - utilityClass / domain Literal enums → keyword-string maps preserving the
      Python value identities.
    - Python `\":…\"` / MST payload string keys stay strings.
    - LangGraph `build_graph` wiring → plain data (edge list + node→fn map +
      conditional router); no langgraph dependency at this layer.
    - The hardware/SDK-gated nodes raise via `ex-info` (R0 NotImplementedError
      parity); the only pure node is `jurisdiction-eligibility`."
  (:require [clojure.string :as str]))

;; ── Literal enums (Python value identities preserved) ─────────────

(def utility-classes
  "The closed `utilityClass` Literal vocabulary. Keyed by idiomatic enum
  keyword; value = the Python Literal string identity."
  {:electric "electric" :gas "gas"   :water "water" :network "network"
   :power    "power"    :rail "rail" :airplane "airplane"
   :port     "port"     :multi "multi"})

(def domains
  "The closed `domain` Literal vocabulary."
  {:terrestrial "terrestrial" :ocean "ocean" :river "river"
   :atmosphere  "atmosphere"  :orbit "orbit"})

(def trigger-nsid
  "MST listener trigger NSID (ADR-2605202200 §5)."
  "com.etzhayyim.apps.etzhayyim.kuniUmi.defineDeploymentSite")

(def submit-nsid
  "Output record NSID emitted by `emit-survey`."
  "com.etzhayyim.apps.etzhayyim.kuniUmi.submitSiteSurvey")

(def witness-invariant-min
  "Constitutional invariant: N ≥ 2 independent robot DIDs (ADR-2605201400 §9)."
  2)

;; ── SiteSurveyState (TypedDict, total=False → plain map) ───────────

(def site-survey-state
  "SiteSurveyState fresh value — the TypedDict(total=False) fields, all unset
  (nil) since the Python TypedDict carries no defaults. camelCase-keyword keys
  mirror the Python `state[...]` surface."
  {;; Input (from defineDeploymentSite MST event)
   :siteDid                  nil
   :siteCode                 nil
   :geo                      nil   ;; GeoJSON Feature
   :utilityClass             nil   ;; ∈ utility-classes vals
   :domain                   nil   ;; ∈ domains vals
   :jurisdictionDid          nil
   :stewardDid               nil
   :intendedUse              nil
   :intendedBeneficiaryDids  nil
   :localLawAttestationCid   nil
   ;; Phase 1 outputs
   :fleetId                  nil
   :surveyBlobCids           nil
   :ecologyBaseline          nil
   :witnessAttestations      nil
   :accepted                 nil
   :rejectionReason          nil
   ;; Audit
   :_event_uri               nil
   :_event_cid               nil
   :_event_nsid              nil})

;; ── kotodama.cell_runtime shim (minimal DI + event helpers) ───────

(defn make-cell-deps
  "Shim of `kotodama.cell_runtime.CellDeps` (frozen dataclass DI container,
  ADR-2605202200 §2). A plain map: cell-name/node-name/checkpointer always
  present, every substrate / LLM port defaulting nil, config an empty map.
  Cells read only the fields they need."
  [& {:keys [cell-name node-name checkpointer
             sdk base-l2-port geth-private-port pds-client
             llm-primary llm-fallback-local config]}]
  {:cell-name         cell-name
   :node-name         node-name
   :checkpointer      checkpointer
   :sdk               sdk
   :base-l2-port      base-l2-port
   :geth-private-port geth-private-port
   :pds-client        pds-client
   :llm-primary       llm-primary
   :llm-fallback-local llm-fallback-local
   :config            (or config {})})

(defn default-state-from-event
  "Shim of `cell_runtime.default_state_from_event`: pass through
  event-record's \"value\" + audit fields. MST payload keys stay strings."
  [event-record _nsid]
  (merge {"_event_uri"        (get event-record "uri")
          "_event_cid"        (get event-record "cid")
          "_event_indexed_at" (get event-record "indexedAt")}
         (get event-record "value" {})))

(defn default-thread-id-from-event
  "Shim of `cell_runtime.default_thread_id_from_event`: \"{nsid}:{rkey}\"."
  [event-record nsid]
  (str nsid ":" (get event-record "rkey" "unknown")))

;; ── Nodes (pure where the Python is; hardware/SDK gates raise) ────

(defn- not-implemented
  "R0 NotImplementedError parity — a hardware/SDK-gated node that raises until
  the substrate is provisioned (ADR-2605201500 checklist)."
  [from msg]
  (throw (ex-info msg {:kuni-umi/not-implemented true :from from})))

(defn allocate-scout-fleet
  "Request N ≥ 2 Giemon scout robots from the open-robo fleet.
  Port of `allocate_scout_fleet` — raises until Otete + Mimi base-station
  fleet operational (ADR-2605201500 hardware/DID provisioning checklist)."
  [_state _deps]
  (not-implemented "allocate_scout_fleet"
                   (str "Requires Giemon Otete + Mimi base-station fleet "
                        "operational. See ADR-2605201500 hardware/DID "
                        "provisioning checklist.")))

(defn collect-sensor-blob
  "Collect RGB-D / LIDAR / chem-sensor / multispectral blobs and pin to IPFS.
  Port of `collect_sensor_blob` — raises until deps.sdk + live fleet wired."
  [_state _deps]
  (not-implemented "collect_sensor_blob"
                   (str "Requires deps.sdk for IPFS pin via @etzhayyim/sdk "
                        "and live Giemon fleet.")))

(def constitutional-intended-uses
  "Hard invariant per this actor's own CLAUDE.md boundary table (`intendedUse`
  row): civilian/community/commons only. military/proprietary-closed-design
  is NEVER eligible, independent of any advisor's judgment — this constant
  is the governor's protected floor, the yosoku/tashikame/kouhou/fleet
  `ComplianceFloor` analog for this actor."
  #{"civilian" "community" "commons"})

(defn jurisdiction-governor
  "Independently re-derives :accepted from the RAW state — never trusts
  `advisor-proposal`'s self-reported :accepted (same invariant as
  yosoku.governor/tashikame.governor/kouhou's *Governor: the advisor
  proposes, an independent check decides). Priority order: (1) HARD
  constitutional intendedUse boundary — cannot be overridden by any advisor
  or confidence level; (2) required-field presence (jurisdictionDid,
  localLawAttestationCid) — soft-scaffold checks pending the real
  ADR-2605201400 §5 Rego policy; (3) the advisor's own judgment + confidence
  floor. `advisor-proposal` may be nil (no advisor injected)."
  [state advisor-proposal]
  (let [intended-use (get state :intendedUse)
        confidence   (get advisor-proposal :confidence 1.0)]
    (cond
      (not (contains? constitutional-intended-uses intended-use))
      {:accepted false
       :rejectionReason (str "intendedUse " (pr-str intended-use)
                             " is not in " constitutional-intended-uses
                             " (constitutional boundary, this actor's CLAUDE.md)")}

      (nil? (get state :jurisdictionDid))
      {:accepted false :rejectionReason "jurisdictionDid missing"}

      (nil? (get state :localLawAttestationCid))
      {:accepted false :rejectionReason "localLawAttestationCid missing"}

      (and advisor-proposal (false? (:accepted advisor-proposal)))
      {:accepted false
       :rejectionReason (or (:rationale advisor-proposal) "advisor flagged ineligible")}

      (and advisor-proposal (< confidence 0.5))
      {:accepted false
       :rejectionReason (str "advisor confidence " confidence " below floor 0.5")}

      :else
      {:accepted true :rejectionReason nil})))

(defn mock-advise
  "Deterministic default advisor: defers entirely to the governor's hard/soft
  rules above (no independent judgment of its own) — safe offline default,
  same role as yosoku/tashikame/kouhou's `mock-advisor`."
  [_state]
  {:accepted true :rationale "mock advisor: deferring to governor rules" :confidence 1.0})

(defn jurisdiction-eligibility
  "Run DMN: 20-actors/kuni-umi/dmn/jurisdiction-eligibility.md.
  Port of `jurisdiction_eligibility` — the ONLY pure node. `deps`'s
  `:llm-primary` (already an anticipated `make-cell-deps` DI field), if
  present, must be a plain fn of `state -> {:accepted bool :rationale str
  :confidence 0..1}` (see `cells/site_survey/advisor.cljc` for a real
  Murakumo-backed implementation — deliberately NOT required by this ns, to
  keep this cell layer dependency-free per its own `no langgraph dependency
  at this layer` convention). Falls back to `mock-advise` when absent. The
  advisor's output is a PROPOSAL only — `jurisdiction-governor` independently
  re-derives the actual decision and can override it in either direction of
  strictness (a permissive advisor never buys past the constitutional
  intendedUse floor; governor's soft checks add scrutiny the mock advisor
  alone would not).

  Previously (R0 scaffold) this always returned accepted=true unconditionally
  — real DMN integration was pending the ADR-2605201400 §5 Rego policy. This
  is a first real cut of that policy (constitutional hard rule + governed
  advisor), not the full Rego engine; superseding it with the real DMN
  remains a follow-up, not regressed by this change."
  [state deps]
  (let [advise (or (:llm-primary deps) mock-advise)
        proposal (advise state)
        {:keys [accepted rejectionReason]} (jurisdiction-governor state proposal)]
    (assoc state :accepted accepted :rejectionReason rejectionReason)))

(defn witness-attest
  "Collect Ed25519 signatures from N ≥ 2 robot DIDs over the survey blob hash.
  Port of `witness_attest` — raises until per-robot DID keypair registration
  + signing endpoint exist. Constitutional invariant N ≥ 2 (ADR-2605201400 §9)."
  [_state _deps]
  (not-implemented "witness_attest"
                   (str "Requires per-robot DID keypair registration and "
                        "signing endpoint. Constitutional invariant: N >= 2 "
                        "must hold (ADR-2605201400 §9).")))

(defn emit-survey
  "Write `submitSiteSurvey` MST record via @etzhayyim/sdk.
  Port of `emit_survey` — raises until deps.sdk (subprocess RPC) wired."
  [_state _deps]
  (not-implemented "emit_survey"
                   (str "Requires deps.sdk (@etzhayyim/sdk subprocess RPC). "
                        "Writes " submit-nsid " to MST.")))

;; ── Router (jurisdiction_eligibility conditional edge) ────────────

(defn router
  "Conditional router after jurisdiction-eligibility: accepted → witness_attest,
  else → emit_survey. Port of the inner `def router(state)`."
  [state]
  (if (:accepted state) "witness_attest" "emit_survey"))

;; ── build_graph (LangGraph wiring → plain data, no langgraph dep) ──

(defn build-graph
  "Build the SiteSurveyCell graph per ADR-2605202200 §1 as plain data — the
  START→allocate→collect→jurisdiction→{witness|emit}→END wiring described as a
  node→fn map + edge list + conditional edge. Port of `build_graph(deps)`; each
  node fn is closed over `deps` to mirror the Python `lambda s: f(s, deps)`."
  [deps]
  {:nodes   ["allocate_scout_fleet" "collect_sensor_blob"
             "jurisdiction_eligibility" "witness_attest" "emit_survey"]
   :steps   {"allocate_scout_fleet"     (fn [s] (allocate-scout-fleet s deps))
             "collect_sensor_blob"      (fn [s] (collect-sensor-blob s deps))
             "jurisdiction_eligibility" (fn [s] (jurisdiction-eligibility s deps))
             "witness_attest"           (fn [s] (witness-attest s deps))
             "emit_survey"              (fn [s] (emit-survey s deps))}
   :edges   [["START" "allocate_scout_fleet"]
             ["allocate_scout_fleet" "collect_sensor_blob"]
             ["collect_sensor_blob" "jurisdiction_eligibility"]
             ["witness_attest" "emit_survey"]
             ["emit_survey" "END"]]
   ;; conditional edge: jurisdiction_eligibility → (router) → node
   :conditional-edges {"jurisdiction_eligibility" router}
   :checkpointer (:checkpointer deps)})

;; ── cell-runtime contract exports (ADR-2605202200 §1) ─────────────

(defn state-from-event
  "Map a defineDeploymentSite event to SiteSurveyState.
  Port of `state_from_event` — delegates to the default shim."
  [event-record nsid]
  (default-state-from-event event-record nsid))

(defn thread-id-from-event
  "Thread id = siteDid (one survey per site, idempotent re-processing).
  Port of `thread_id_from_event` — falls back siteDid → siteCode → rkey."
  [event-record _nsid]
  (let [value    (get event-record "value" {})
        site-did (or (get value "siteDid")
                     (get value "siteCode")
                     (get event-record "rkey" "unknown"))]
    (str "SiteSurveyCell:" site-did)))

(defn healthz-extra
  "Cell-specific /healthz fields (ADR-2605202200 §5). Port of `healthz_extra`."
  [_deps]
  {"phase"                 "1-survey"
   "fleet_required"        ["otete" "mimi-base-station"]
   "witness_invariant_min" witness-invariant-min
   "trigger_nsid"          trigger-nsid})
