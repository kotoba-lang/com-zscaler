(ns kuni-umi.cells.deployment-planning.cell
  "DeploymentPlanningCell — Phase 2 of the kuni-umi 国産み 4-phase planetary-infra
  deployment workflow. 1:1 port of `cells/deployment_planning/cell.py`
  (ADR-2605201400 §3 / ADR-2605202200 cell-runtime contract).

  Trigger:  MST listener on
            `com.etzhayyim.apps.etzhayyim.kuniUmi.submitSiteSurvey` (accepted=true).
  Effect:   derive target topology → invoke UNSPSC fleet for BoM → counterparty
            filter → proportionality DMN → optional governance vote → payment plan
            → fleet allocation → emit `proposeDeploymentPlan` (BoM encrypted).
  Murakumo: zebulun (leader, treasury-adjacent for BoM cost calc).

  ── kotodama.cell_runtime SHIM (see site-survey cell.cljc) ────────
  Reproduces only the minimal `cell_runtime` surface the cell touches
  (`make-cell-deps` / `default-state-from-event` / `default-thread-id-from-event`),
  so the transitions / graph / pure nodes are testable on bb/SCI with no Python
  runtime. Runner concerns (checkpointer/host/healthz server) are not reproduced.

  ── Conventions (site-survey cell.cljc) ──────────────────────────
    - DeploymentPlanningState TypedDict → plain map; camelCase-keyword keys.
    - decision Literal enum → keyword-string map preserving the Python identities.
    - LangGraph `build_graph` wiring → plain data (node list + node→fn map + edges).
    - hardware/SDK/contract-gated nodes raise via `ex-info` (R0 NotImplementedError
      parity); the only pure node is `proportionality-check`."
  (:require [clojure.string :as str]))

;; ── Literal enums (Python value identities preserved) ─────────────

(def decisions
  "The closed `decision` Literal vocabulary. Keyed by idiomatic enum keyword;
  value = the Python Literal string identity."
  {:accept                       "accept"
   :reject                       "reject"
   :awaiting-governance          "awaiting-governance"
   :awaiting-force-authorization "awaiting-force-authorization"
   :awaiting-public-fund         "awaiting-public-fund"})

(def trigger-nsid
  "MST listener trigger NSID — submitSiteSurvey (accepted=true)."
  "com.etzhayyim.apps.etzhayyim.kuniUmi.submitSiteSurvey")

(def submit-nsid
  "Output record NSID emitted by `propose-plan`."
  "com.etzhayyim.apps.etzhayyim.kuniUmi.proposeDeploymentPlan")

(def governance-population-threshold
  "proportionality-check: populationImpacted > 100 → governance vote
  (ADR-2605201400 §3 proportionality DMN)."
  100)

;; ── DeploymentPlanningState (TypedDict, total=False → plain map) ───

(def deployment-planning-state
  "DeploymentPlanningState fresh value — the TypedDict(total=False) fields, all
  unset (nil). camelCase-keyword keys mirror the Python `state[...]` surface."
  {;; Input (from submitSiteSurvey MST event)
   :siteDid               nil
   :surveyDid             nil
   :surveyBlobCids        nil
   :ecologyBaseline       nil
   :populationImpacted    nil
   :reversibilityScore    nil
   ;; Phase 2 outputs
   :planCode              nil
   :targetTopologyDids    nil
   :bomEnvelopeCid        nil
   :fleetAllocation       nil
   :paymentPlanCid        nil
   :timeline              nil
   :lifespanYears         nil
   :requiresGovernance    nil
   :governanceProposalUri nil
   :requiresPublicFund    nil
   :publicFundProposalUri nil
   :accepted              nil
   :decision              nil   ;; ∈ decisions vals
   :rejectionReason       nil
   ;; Audit
   :_event_uri            nil
   :_event_nsid           nil})

;; ── kotodama.cell_runtime shim (minimal DI + event helpers) ───────

(defn make-cell-deps
  "Shim of `kotodama.cell_runtime.CellDeps` (frozen dataclass DI container,
  ADR-2605202200 §2). A plain map; every substrate / LLM port defaults nil."
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
  "Shim of `cell_runtime.default_state_from_event`: pass through event-record's
  \"value\" + audit fields. MST payload keys stay strings."
  [event-record _nsid]
  (merge {"_event_uri"        (get event-record "uri")
          "_event_cid"        (get event-record "cid")
          "_event_indexed_at" (get event-record "indexedAt")}
         (get event-record "value" {})))

(defn default-thread-id-from-event
  "Shim of `cell_runtime.default_thread_id_from_event`: \"{nsid}:{rkey}\"."
  [event-record nsid]
  (str nsid ":" (get event-record "rkey" "unknown")))

;; ── Nodes (pure where the Python is; gated nodes raise) ───────────

(defn- not-implemented
  "R0 NotImplementedError parity — a node gated on substrate not yet provisioned."
  [from msg]
  (throw (ex-info msg {:kuni-umi/not-implemented true :from from})))

(defn derive-target-topology
  "Translate utilityClass + survey blob into open-* CIM record target DIDs.
  Port of `derive_target_topology` — raises until the utilityClass→CIM mapper exists."
  [_state _deps]
  (not-implemented "derive_target_topology"
                   (str "Requires utilityClass-to-CIM mapper "
                        "(open-denki/open-gas/open-water/open-network/...). "
                        "S2 single-utility scope = open-denki "
                        "defineGenerationNode/defineSubstation/defineFeeder/registerSmartMeter.")))

(defn bom-generation
  "Parallel dispatch UNSPSC agent fleet for each commodity in target topology
  (ADR-2605171300 — 18,345 agents indexed by UNSPSC). Port of `bom_generation`."
  [_state _deps]
  (not-implemented "bom_generation"
                   (str "Requires kotodama.unispsc.dispatch helper + 18,345 specialized "
                        "agents at 40-engine/kotoba/crates/kotoba-kotodama/unispsc_agents/. "
                        "Currently MCP server exists but Python dispatch wrapper does not.")))

(defn counterparty-classification
  "DMN counterparty-classification: reject Non-Aligned suppliers
  (ChartersComplianceRegistry, ADR-2605192230). Port of `counterparty_classification`."
  [_state _deps]
  (not-implemented "counterparty_classification"
                   (str "Requires deps.base_l2_port + ChartersComplianceRegistry.sol "
                        "address; currently deployed only on local Anvil.")))

(defn proportionality-check
  "DMN proportionality-check: sets requiresGovernance / requiresPublicFund.
  Port of `proportionality_check` — the ONLY pure node. R0 scaffold wires only
  the populationImpacted > 100 → governance rule; estimatedCostUsdc not yet
  threaded, so requiresPublicFund stays false. Returns the next state map."
  [state _deps]
  (let [pop (or (get state :populationImpacted) 0)]
    (assoc state
           :requiresGovernance (> pop governance-population-threshold)
           :requiresPublicFund false)))

(defn payment-plan
  "Schedule USDC milestone disbursements via Etzhayyim.pay() + TitheRouter 90/10.
  Port of `payment_plan` — raises until MilestoneEscrow.sol + TitheRouter wired."
  [_state _deps]
  (not-implemented "payment_plan"
                   (str "Requires deps.base_l2_port + MilestoneEscrow.sol + "
                        "TitheRouter.sol addresses. MilestoneEscrow not yet authored "
                        "(ADR-2605192145 §future).")))

(defn fleet-allocation
  "Request Giemon fleet from open-robo + estimate robot-hours.
  Port of `fleet_allocation` — raises until kotodama.open_robo.fleet exists."
  [_state _deps]
  (not-implemented "fleet_allocation"
                   "Requires kotodama.open_robo.fleet (does not exist yet)."))

(defn propose-plan
  "Write encrypted `proposeDeploymentPlan` MST record + governance proposals if
  needed. Port of `propose_plan` — raises until deps.sdk wired."
  [_state _deps]
  (not-implemented "propose_plan"
                   (str "Requires deps.sdk for XChaCha20-Poly1305 envelope "
                        "(ADR-2605181100) + MST write.")))

;; ── build_graph (LangGraph wiring → plain data, no langgraph dep) ──

(defn build-graph
  "Build the DeploymentPlanningCell graph per ADR-2605202200 §1 as plain data —
  the linear START→…→propose_plan→END wiring as a node→fn map + edge list. Port
  of `build_graph(deps)`; each node fn is closed over `deps`."
  [deps]
  {:nodes ["derive_target_topology" "bom_generation" "counterparty_classification"
           "proportionality_check" "payment_plan" "fleet_allocation" "propose_plan"]
   :steps {"derive_target_topology"      (fn [s] (derive-target-topology s deps))
           "bom_generation"              (fn [s] (bom-generation s deps))
           "counterparty_classification" (fn [s] (counterparty-classification s deps))
           "proportionality_check"       (fn [s] (proportionality-check s deps))
           "payment_plan"                (fn [s] (payment-plan s deps))
           "fleet_allocation"            (fn [s] (fleet-allocation s deps))
           "propose_plan"                (fn [s] (propose-plan s deps))}
   :edges [["START" "derive_target_topology"]
           ["derive_target_topology" "bom_generation"]
           ["bom_generation" "counterparty_classification"]
           ["counterparty_classification" "proportionality_check"]
           ["proportionality_check" "payment_plan"]
           ["payment_plan" "fleet_allocation"]
           ["fleet_allocation" "propose_plan"]
           ["propose_plan" "END"]]
   :checkpointer (:checkpointer deps)})

;; ── cell-runtime contract exports (ADR-2605202200 §1) ─────────────

(defn state-from-event
  "Map a submitSiteSurvey event to DeploymentPlanningState. Port of
  `state_from_event` — delegates to the default shim."
  [event-record nsid]
  (default-state-from-event event-record nsid))

(defn thread-id-from-event
  "Thread id = siteDid (one plan per site, idempotent). Port of
  `thread_id_from_event` — falls back siteDid → rkey."
  [event-record _nsid]
  (let [value    (get event-record "value" {})
        site-did (or (get value "siteDid")
                     (get event-record "rkey" "unknown"))]
    (str "DeploymentPlanningCell:" site-did)))

(defn healthz-extra
  "Cell-specific /healthz fields (ADR-2605202200 §5). Port of `healthz_extra`."
  [_deps]
  {"phase"                 "2-planning"
   "trigger_nsid"          trigger-nsid
   "depends_on_fleet"      ["kotodama.unispsc.dispatch" "kotodama.open_robo.fleet"]
   "depends_on_contracts"  ["ChartersComplianceRegistry" "TitheRouter" "MilestoneEscrow"]})
