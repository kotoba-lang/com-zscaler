(ns kuni-umi.cells.decommission.cell
  "DecommissionCell — End-of-life cell of the kuni-umi 国産み planetary-infra
  workflow. 1:1 port of `cells/decommission/cell.py`
  (ADR-2605201400 §3 + Open Question 5 / ADR-2605202200 cell-runtime contract).

  Trigger:  cron (0 0 1 * *) + governance-vote + lifespan-expiry-monitor.
  Effect:   decommission plan → disconnect from utility → physical teardown
            (reverse construction Pregel) → land return → release stewardship
            → archive site → permanent L2 anchor (multi-generational record).
  Murakumo: dan (leader).

  Constitutional alignment (ADR-2605192100 §mission.land_as_religious_trust):
  the land does not belong to the project — the project was stewarded BY the
  land; end-of-life is a religious ritual return.

  See site-survey cell.cljc for the cell_runtime SHIM + plain-data StateGraph
  conventions. Every node is substrate-gated → raises `ex-info` (R0
  NotImplementedError parity); the graph is linear (no pure node)."
  (:require [clojure.string :as str]))

;; ── Literal enums (Python value identities preserved) ─────────────

(def trigger-reasons
  "The closed `triggerReason` Literal vocabulary."
  {:lifespan-expiry "lifespan-expiry"
   :governance-vote "governance-vote"
   :force-majeure   "force-majeure"})

(def site-states
  "The closed `siteState` Literal vocabulary."
  {:decommissioned "decommissioned"
   :in-progress    "in-progress"
   :ecology-debt   "ecology-debt"})

;; ── DecommissionState (TypedDict, total=False → plain map) ─────────

(def decommission-state
  "DecommissionState fresh value — all fields unset (nil)."
  {;; Input (cron OR explicit governance vote)
   :siteDid               nil
   :planDid               nil
   :triggerReason         nil   ;; ∈ trigger-reasons vals
   :governanceProposalUri nil
   ;; Decommission workflow
   :decommissionBomCid    nil
   :recyclableMaterials   nil
   :urbanMiningRoutingCid nil
   ;; Physical teardown
   :teardownProgress      nil
   :witnessAttestations   nil
   ;; Land return
   :ecologyRestorationCid nil
   :landReturnAttested    nil
   ;; Stewardship release
   :landRegistryUpdated   nil
   :siteState             nil   ;; ∈ site-states vals
   ;; Audit
   :_event_uri            nil
   :_event_nsid           nil})

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

;; ── Nodes (all substrate-gated → raise) ───────────────────────────

(defn- not-implemented
  [from msg]
  (throw (ex-info msg {:kuni-umi/not-implemented true :from from})))

(defn decommission-plan
  "Generate teardown BoM + identify recyclable materials → open-robo urban-mining.
  Port of `decommission_plan`."
  [_state _deps]
  (not-implemented "decommission_plan"
                   (str "Requires reverse-BoM derivation from original planDid + "
                        "open-robo urban-mining cell binding.")))

(defn disconnect-from-utility
  "Coordinate with open-ot to retire defineLoop + mark assets retired.
  Port of `disconnect_from_utility`."
  [_state _deps]
  (not-implemented "disconnect_from_utility"
                   "Requires open-ot retireLoop XRPC + per-utility lexicon retired-state update."))

(defn physical-teardown
  "Re-dispatch Giemon fleet for disassembly (witness audit applies).
  Port of `physical_teardown`."
  [_state _deps]
  (not-implemented "physical_teardown"
                   "Requires kotodama.open_robo.fleet + AuditWitnessCell coordination."))

(defn land-return
  "Restore ecology to baseline; emit land-return audit event. Port of `land_return`."
  [_state _deps]
  (not-implemented "land_return"
                   "Requires post-restoration survey + ecology delta comparison + audit emission."))

(defn release-stewardship
  "Update LandRegistry / OceanStewardship / … (site → commons or new steward).
  Port of `release_stewardship`."
  [_state _deps]
  (not-implemented "release_stewardship"
                   "Requires deps.geth_private_port + LandRegistry.sol releaseSteward()."))

(defn archive-site
  "Finalize site DID → decommissioned + permanent L2 anchor (multi-generational).
  Port of `archive_site`."
  [_state _deps]
  (not-implemented "archive_site"
                   "Requires deps.sdk for final MST snapshot + Base L2 EtzhayyimAnchor commit."))

;; ── build_graph (LangGraph wiring → plain data, linear) ───────────

(defn build-graph
  "Build the DecommissionCell graph as plain data (linear START→…→archive→END).
  Port of `build_graph(deps)`."
  [deps]
  {:nodes ["decommission_plan" "disconnect_from_utility" "physical_teardown"
           "land_return" "release_stewardship" "archive_site"]
   :steps {"decommission_plan"       (fn [s] (decommission-plan s deps))
           "disconnect_from_utility" (fn [s] (disconnect-from-utility s deps))
           "physical_teardown"       (fn [s] (physical-teardown s deps))
           "land_return"             (fn [s] (land-return s deps))
           "release_stewardship"     (fn [s] (release-stewardship s deps))
           "archive_site"            (fn [s] (archive-site s deps))}
   :edges [["START" "decommission_plan"]
           ["decommission_plan" "disconnect_from_utility"]
           ["disconnect_from_utility" "physical_teardown"]
           ["physical_teardown" "land_return"]
           ["land_return" "release_stewardship"]
           ["release_stewardship" "archive_site"]
           ["archive_site" "END"]]
   :checkpointer (:checkpointer deps)})

;; ── cell-runtime contract exports (ADR-2605202200 §1) ─────────────

(defn state-from-event
  "Cron-triggered: synthesize event from siteDid + lifespan expiry detection.
  Port of `state_from_event` — delegates to the default shim."
  [event-record nsid]
  (default-state-from-event event-record nsid))

(defn thread-id-from-event
  "Port of `thread_id_from_event` — siteDid → rkey."
  [event-record _nsid]
  (let [value    (get event-record "value" {})
        site-did (or (get value "siteDid")
                     (get event-record "rkey" "unknown"))]
    (str "DecommissionCell:" site-did)))

(defn healthz-extra
  "Cell-specific /healthz fields. Port of `healthz_extra`."
  [_deps]
  {"phase"               "end-of-life"
   "trigger"             "cron-monthly + governance-vote + lifespan-expiry"
   "religious_invariant" "land_as_religious_trust — return is ritual (ADR-2605192100 §mission)"
   "feeds_urban_mining"  "60-apps/etzhayyim-project-open-robo/docs/urban-mining-automation-v1.md"})
