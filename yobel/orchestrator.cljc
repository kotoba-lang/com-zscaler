(ns yobel.orchestrator
  "Yobel BPMN orchestrator — maps bpmn/yobel-rite-lifecycle.bpmn ServiceTasks to
  langgraph-clj cells. Clojure port of `orchestrator.py` (ADR-2605201800).

  This is a thin orchestrator that:
    1. Mirrors the BPMN definition (yobel-rite-lifecycle.bpmn)
    2. Maps each ServiceTask implementation=\"cell:<name>\" to the corresponding
       cell `build-graph`
    3. Drives state through the BPMN flow (declare → ratify → enroll fan-out →
       release → audit)

  This is NOT a full BPMN engine — it implements only the subset of BPMN
  constructs that yobel uses: sequenceFlow, exclusiveGateway, parallelGateway,
  multiInstance loop on enrollment, and event-subprocess for audit witness.

  Use cases:
    - dry-run fixtures drive end-to-end without an external BPMN engine
    - integration tests verify cell sequencing matches BPMN sequenceFlow
    - operators inspect rite state mid-flow via `snapshot`"
  (:require [clojure.string :as str]
            [langgraph.graph :as g]
            [yobel.cells.audit-witness.cell :as audit]
            [yobel.cells.creditor-enrollment.cell :as creditor]
            [yobel.cells.debtor-enrollment.cell :as debtor]
            [yobel.cells.release-settlement.cell :as release]
            [yobel.cells.rite-declaration.cell :as declaration]))

;; ─── Ports ────────────────────────────────────────────────────────

(defrecord YobelPorts [rite-registry creditor-enrollment council-sbt
                       charter-compliance erc725 envelope-crypto
                       tithe-router base-l2-paymaster lawfirm-invoke
                       anchor-bridge audit-witness-emit witness-keystore
                       audit-log public-fund council-notifier
                       council-ratification land-registry])

(defn ports
  "All 17 ports collected for the orchestrator. Missing keys = stubbed dry runs."
  [m]
  (map->YobelPorts m))

;; ─── Snapshot ─────────────────────────────────────────────────────

(defn rite-lifecycle-snapshot
  "Read-only mid-flow state snapshot for operators / tests.
  :phase ∈ declared / ratified / enrolling / releasing / complete / rejected / tampered"
  [rite-id phase rite-status]
  {:rite-id rite-id
   :phase phase
   :rite-status rite-status
   :creditor-enrollments []
   :debtor-enrollments []
   :releases []
   :audit-events []})

;; ─── Orchestrator (cells built lazily) ────────────────────────────

(defn make-orchestrator
  "Cells are built lazily (only when needed) so a dry-run with no debtors
  doesn't pay for release-settlement's build-graph."
  [ports checkpointer]
  {:ports ports
   :checkpointer checkpointer
   :graphs
   {:declaration
    (delay (declaration/build-graph
            {:checkpointer checkpointer
             :charter-compliance-port (:charter-compliance ports)
             :council-sbt-port (:council-sbt ports)
             :council-ratification-port (:council-ratification ports)
             :land-registry-port (:land-registry ports)
             :anchor-bridge (:anchor-bridge ports)}))
    :creditor
    (delay (creditor/build-graph
            {:checkpointer checkpointer
             :rite-registry-port (:rite-registry ports)
             :council-sbt-port (:council-sbt ports)
             :charter-compliance-port (:charter-compliance ports)
             :erc725-port (:erc725 ports)
             :envelope-crypto (:envelope-crypto ports)
             :anchor-bridge (:anchor-bridge ports)}))
    :debtor
    (delay (debtor/build-graph
            {:checkpointer checkpointer
             :rite-registry-port (:rite-registry ports)
             :creditor-enrollment-port (:creditor-enrollment ports)
             :council-sbt-port (:council-sbt ports)
             :charter-compliance-port (:charter-compliance ports)
             :envelope-crypto (:envelope-crypto ports)
             :anchor-bridge (:anchor-bridge ports)}))
    :release
    (delay (release/build-graph
            {:checkpointer checkpointer
             :creditor-enrollment-port (:creditor-enrollment ports)
             :envelope-crypto (:envelope-crypto ports)
             :tithe-router-port (:tithe-router ports)
             :base-l2-paymaster (:base-l2-paymaster ports)
             :lawfirm-invoke (:lawfirm-invoke ports)
             :anchor-bridge (:anchor-bridge ports)
             :audit-witness-emit (:audit-witness-emit ports)}))
    :audit
    (delay (audit/build-graph
            {:checkpointer checkpointer
             :audit-log-port (:audit-log ports)
             :witness-keystore (:witness-keystore ports)
             :anchor-bridge (:anchor-bridge ports)
             :public-fund-port (:public-fund ports)
             :council-notifier (:council-notifier ports)}))}})

(defn- graph [orchestrator k]
  @(get-in orchestrator [:graphs k]))

;; ─── BPMN flow execution ──────────────────────────────────────────

(defn witness-super-step
  "Invoke the audit-witness cell on a super-step boundary. Witness is
  observational — its failure must never block the primary flow."
  [orchestrator {:keys [rite-id tx-digest] :as event}]
  (let [{:keys [audit-log witness-keystore]} (:ports orchestrator)]
    (if (and (nil? audit-log) (nil? witness-keystore))
      {} ;; pure-stub mode: no audit witness needed
      (try
        (g/invoke (graph orchestrator :audit) event
                  {:thread-id (str "audit-" rite-id "-"
                                   (subs tx-digest 0 (min 16 (count tx-digest))))})
        (catch #?(:clj Exception :cljs :default) _ {})))))

(defn declare-rite
  "Start_DeclareRequest → Task_RiteDeclaration → Gate_CouncilRatification →
  Gateway_CouncilDecision."
  [orchestrator rite-id rite-input]
  (let [result (g/invoke (graph orchestrator :declaration) rite-input
                         {:thread-id (str "declare-" rite-id)})]
    (witness-super-step orchestrator
                        {:rite-id rite-id
                         :source-kind "super_step"
                         :state-root-before "genesis"
                         :state-root-after (get result :rite-vertex-uri "")
                         :tx-digest (str "declare:" rite-id)
                         :event-payload {:step "declare_rite"
                                         :status (:rite-status result)}})
    result))

(defn enroll-creditor
  "Task_CreditorEnrollment (multiInstance loop iteration)."
  [orchestrator rite-id enrollment-input]
  (let [creditor-did (get enrollment-input :creditor-did "")
        result (g/invoke (graph orchestrator :creditor) enrollment-input
                         {:thread-id (str "credit-" rite-id "-" creditor-did)})]
    (witness-super-step orchestrator
                        {:rite-id rite-id
                         :source-kind "super_step"
                         :state-root-before "enroll_creditor:before"
                         :state-root-after (get result :enrollment-vertex-uri "")
                         :tx-digest (str "credit:" creditor-did)
                         :event-payload {:step "enroll_creditor"
                                         :debt-count (get result :debt-count 0)}})
    result))

(defn enroll-debtor
  "Task_DebtorEnrollment (multiInstance loop iteration)."
  [orchestrator rite-id enrollment-input]
  (let [debtor-did (get enrollment-input :debtor-did "")
        result (g/invoke (graph orchestrator :debtor) enrollment-input
                         {:thread-id (str "debt-" rite-id "-" debtor-did)})]
    (witness-super-step orchestrator
                        {:rite-id rite-id
                         :source-kind "super_step"
                         :state-root-before "enroll_debtor:before"
                         :state-root-after (get result :enrollment-vertex-uri "")
                         :tx-digest (str "debt:" debtor-did)
                         :event-payload {:step "enroll_debtor"
                                         :pairing-status (get result :pairing-status "")}})
    result))

(defn record-release
  "Task_ReleaseSettlement (multiInstance loop iteration). The release cell's own
  emit-audit-event already called audit-witness-emit; we additionally witness
  the super-step transition for chain continuity."
  [orchestrator rite-id release-input]
  (let [release-id (get release-input :release-id "")
        result (g/invoke (graph orchestrator :release) release-input
                         {:thread-id (str "rel-" rite-id "-" release-id)})]
    (witness-super-step orchestrator
                        {:rite-id rite-id
                         :source-kind "release_finalized"
                         :state-root-before "release:before"
                         :state-root-after (get result :release-vertex-uri "")
                         :tx-digest (str "rel:" release-id)
                         :event-payload {:step "record_release"
                                         :settlement-ok (get result :settlement-ok false)
                                         :base-l2-tx-hash (get result :base-l2-tx-hash "")}})
    result))

;; ─── stub-bridge write inspection ─────────────────────────────────

(defn- bridge-writes
  "A stub anchor-bridge may expose its recorded writes as a derefable under
  :writes (the Clojure analogue of the Python `hasattr(bridge, \"writes\")`
  duck-typing). Real bridges expose nothing and this returns nil."
  [anchor-bridge]
  (some-> (:writes anchor-bridge) deref))

;; ─── Composite execution (the BPMN happy path) ────────────────────

(defn run-rite-lifecycle
  "Drive a rite through the entire BPMN: declare → enroll fan-out → release.

  Releases are processed only if the rite ratified. Enrollments and releases run
  sequentially in this reference orchestrator (a real BPMN engine would
  parallelize the multiInstance loop); ordering is creditors-then-debtors-then-
  releases. Returns the lifecycle snapshot map."
  [orchestrator {:keys [rite-input creditor-enrollments debtor-enrollments release-inputs]}]
  (let [rite-id (:rite-id rite-input)
        anchor-bridge (get-in orchestrator [:ports :anchor-bridge])
        ;; Phase 0: declaration + Council ratification
        decl (declare-rite orchestrator rite-id rite-input)
        rite-status (get decl :rite-status "cancelled")]
    (if (not= "active" rite-status)
      (rite-lifecycle-snapshot rite-id "rejected" rite-status)
      ;; Phase 1: enroll fan-out (creditors then debtors)
      (let [cred-results (mapv #(enroll-creditor orchestrator rite-id %) creditor-enrollments)
            accepted-creditor-dids
            (into #{}
                  (keep (fn [[input result]]
                          (when (seq (get result :enrollment-vertex-uri ""))
                            (:creditor-did input)))
                        (map vector creditor-enrollments cred-results)))
            deb-results (mapv #(enroll-debtor orchestrator rite-id %) debtor-enrollments)
            ;; debtor cell sets :pairing-status "paired" AND anchors with
            ;; :eligible true only when eligible — read back from the bridge
            ;; writes (defense against pairing-without-eligibility)
            eligible-debtor-dids
            (into #{}
                  (keep (fn [[input result]]
                          (when (and (= "paired" (get result :pairing-status))
                                     (some (fn [w]
                                             (and (= "com.etzhayyim.apps.etzhayyim.yobel.debtorEnrollment"
                                                     (:collection w))
                                                  (= (:debtor-did input) (get-in w [:payload :debtor-did]))
                                                  (= rite-id (get-in w [:payload :rite-id]))
                                                  (true? (get-in w [:payload :eligible]))))
                                           (bridge-writes anchor-bridge)))
                            (:debtor-did input)))
                        (map vector debtor-enrollments deb-results)))
            ;; Phase 2: releases (BPMN parallelGateway join — eligible pairs only)
            releases
            (mapv (fn [rel]
                    (cond
                      (not (accepted-creditor-dids (:creditor-did rel)))
                      {:release-vertex-uri ""
                       :settlement-ok false
                       :settlement-error (str "creditor " (:creditor-did rel)
                                              " not accepted (Charter Rider gate)")}
                      (not (eligible-debtor-dids (:debtor-did rel)))
                      {:release-vertex-uri ""
                       :settlement-ok false
                       :settlement-error (str "debtor " (:debtor-did rel)
                                              " ineligible (DMN gate)")}
                      :else (record-release orchestrator rite-id rel)))
                  release-inputs)]
        (assoc (rite-lifecycle-snapshot rite-id "complete" rite-status)
               :creditor-enrollments cred-results
               :debtor-enrollments deb-results
               :releases releases)))))

;; ─── Operator-facing snapshot ─────────────────────────────────────

(defn snapshot
  "Read-only state lookup. Reconstructs from anchor-bridge writes in stub mode."
  [orchestrator rite-id]
  (let [writes (bridge-writes (get-in orchestrator [:ports :anchor-bridge]))]
    (reduce
     (fn [snap w]
       (let [collection (get w :collection "")
             payload (:payload w)]
         (if (not= rite-id (:rite-id payload))
           snap
           (cond
             (str/ends-with? collection ".rite")
             (let [status (get payload :status "unknown")]
               (assoc snap
                      :rite-status status
                      :phase (if (= "active" status) "ratified" "rejected")))
             (str/ends-with? collection ".creditorEnrollment")
             (update snap :creditor-enrollments conj payload)
             (str/ends-with? collection ".debtorEnrollment")
             (update snap :debtor-enrollments conj payload)
             (str/ends-with? collection ".release")
             (update snap :releases conj payload)
             :else snap))))
     (rite-lifecycle-snapshot rite-id "unknown" "unknown")
     (or writes []))))
