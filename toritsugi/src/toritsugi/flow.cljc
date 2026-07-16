(ns toritsugi.flow
  "ProcedureFlow — one toritsugi operation = one supervised actor run, a
  langgraph-clj StateGraph. The citizen-procedure concierge flow is one auditable
  graph (the executable spine of the BPMN process in
  registry/toritsugi.procedure-flow.bpmn.edn):

    procedure_registry → eligibility_match → intake
       → {guide | draft | submit}  (the request's op)
       → govern → decide → commit | request-approval → commit | hold
       → status_track → END

  Single invariant (the toritsugi analog of kyoninka's safety contract):
    the actor never guides / drafts / submits a procedure the ProcedureGovernor
    would reject, never auto-files an 代行 (agent-on-behalf) submission, and never
    holds plaintext PII.

  The contained concierge advisor is deterministic (G7 Murakumo-only — no vendor
  LLM callout here); it builds a PROPOSAL from the resolved procedure + member
  facts. Each cell node ALSO runs that cell's pure state-machine membrane (the
  same G-gates, structurally) — a cell refusal is folded into the governor
  verdict as a HARD violation, so either layer can force a HOLD. An 代行 submit is
  ALWAYS high-stakes → interrupt-before :request-approval (human/Council sign-off)."
  (:require [langgraph.graph :as g]
            [langgraph.checkpoint :as cp]
            [toritsugi.store :as store]
            [toritsugi.governor :as gov]
            [toritsugi.phase :as phase]
            [toritsugi.cells.procedure-registry.state-machine :as preg]
            [toritsugi.cells.eligibility-match.state-machine :as elig]
            [toritsugi.cells.intake.state-machine :as intk]
            [toritsugi.cells.guide.state-machine :as gde]
            [toritsugi.cells.draft.state-machine :as drft]
            [toritsugi.cells.submit.state-machine :as sub]
            [toritsugi.cells.status-track.state-machine :as trck]))

(defn- today-from [context] (or (get-in context [:today]) gov/default-today))

(defn- fresh? [context proc] (gov/freshness-ok? (today-from context) proc))

(defn- kw-mode [m] (if (keyword? m) m (keyword (str m))))

(defn- refusal-of
  "Extract a cell's refusal message, returning nil when the cell PASSED. Cells set
  `\"refusal\" \"\"` on success; an empty string is truthy in Clojure, so we must
  treat blank as no-refusal."
  [cell-state]
  (let [r (get cell-state "refusal")]
    (when (and (some? r) (pos? (count r))) r)))

;; ───────────────────────── contained concierge advisor (deterministic, G7) ─────────────────────────

(defn- build-proposal
  "Pure deterministic proposal from the resolved procedure + request. This is the
  contained 'intelligence node' (observe→recommend); the Governor censors it
  before anything is recorded. effect stays in the op's allowed set (G5)."
  [op request proc]
  (let [enc-ref (:encrypted-pii-ref request)]
    (case op
      :guide/build    {:effect :guide :recommendation :guide-ready
                       :summary (str (:title proc) " — 案内 + 必要書類チェックリスト")
                       :rationale (str "根拠: " (:legal-basis proc))
                       :cites [:procedure :required-docs] :confidence 0.9}
      :draft/assist   {:effect :input-assist :recommendation :draft-ready
                       :summary (str (:title proc) " — 様式入力補助(暗号化ドラフト)")
                       :rationale "member 著作、toritsugi は入力補助(UPL G5)。"
                       :cites [:procedure :encrypted-draft]
                       :confidence 0.88 :encrypted-pii-ref enc-ref}
      :submit/transmit {:effect :submit :recommendation :submit-ready
                        :summary (str (:title proc) " — 提出")
                        :rationale (str "mode=" (kw-mode (:mode request)) " / channel=" (:channel request))
                        :cites [:procedure :consent :encrypted-pii]
                        :confidence 0.9 :encrypted-pii-ref enc-ref}
      {:effect :noop :recommendation :unknown :summary "未対応"
       :rationale (str op) :cites [] :confidence 0.0})))

(defn- trace [request proposal]
  {:t :concierge-proposal :op (:op request) :procedure (:procedure request)
   :recommendation (:recommendation proposal) :summary (:summary proposal)
   :confidence (:confidence proposal)})

;; ───────────────────────── session lifecycle helpers ─────────────────────────

(defn- ensure-session! [store sid request mode]
  (when (and sid (nil? (store/session store sid)))
    (store/record-datom! store {:kind :session :id sid
                                :value (store/open-session sid (:member request)
                                                           (:procedure request)
                                                           (:consent-ref request) (kw-mode mode))}))
  sid)

(defn- set-session-phase! [store sid ph]
  (when-let [ss (store/session store sid)]
    (store/record-datom! store {:kind :session :id sid :value (assoc ss :phase ph)})))



;; ───────────────────────── build ─────────────────────────

(defn build
  "Compiles a ProcedureFlow bound to `store` (any toritsugi.store/Store).
  opts: :checkpointer (default in-mem). interrupt-before #{:request-approval}."
  ([store] (build store nil))
  ([store {:keys [checkpointer] :or {checkpointer (cp/mem-checkpointer)}}]
   (-> (g/state-graph
        {:channels
         {:request       {:default nil}
          :context       {:default nil}            ; :phase (rollout) + :today
          :proposal      {:default nil}
          :cell-refusals {:reducer into :default []}  ; cell-membrane refusals (HARD)
          :verdict       {:default nil}
          :disposition   {:default nil}
          :record        {:default nil}            ; pending assess artifact (draft/submission)
          :approval      {:default nil}
          :audit         {:reducer into :default []}}})

       ;; ── procedure_registry: resolve the coded procedure (G8/G14 membrane) ──
       (g/add-node :procedure-registry
         (fn [{:keys [request context]}]
           (let [proc  (store/procedure store (:procedure request))
                 fresh (boolean (fresh? context proc))
                 out   (preg/transition
                        {"procedure_id"        (:procedure-id proc)
                         "title"               (:title proc)
                         "verification_status" (:verification-status proc)
                         "freshness_ok"        fresh
                         "legal_basis"         (:legal-basis proc)
                         "provenance"          (:provenance proc)
                         "channel_type"        (:channel-type proc)
                         "required_docs"       (:required-docs proc)
                         "fee_jpy"             (:fee-jpy proc)
                         "statutory_days"      (:statutory-days proc)})
                 ccs   (get out "cell_state")
                 refuse (refusal-of ccs)]
             (ensure-session! store (:session request) request (:mode request))
             (set-session-phase! store (:session request) :init)
             (store/append-ledger! store {:t :resolved :op (:op request)
                                          :procedure (:procedure request)
                                          :session (:session request) :disposition :record
                                          :basis (if refuse :refused :resolved)})
             (cond-> {:audit [{:t :resolved :procedure (:procedure request) :fresh fresh
                               :verification (:verification-status proc)}]}
               refuse (assoc :cell-refusals [refuse])))))

       ;; ── eligibility_match: proactive 制度/給付 案内 (G3/G4 membrane) ──
       (g/add-node :eligibility-match
         (fn [{:keys [request]}]
           (let [out (elig/transition
                      {"member_did" (:member request)
                       "consent_ref" (:consent-ref request)
                       "benefit" (get-in request [:benefit] (:procedure request))
                       "procedure_id" (:procedure request)})
                 ccs (get out "cell_state")
                 refuse (refusal-of ccs)]
             (set-session-phase! store (:session request) :matched)
             (store/append-ledger! store {:t :matched :op (:op request)
                                          :procedure (:procedure request)
                                          :session (:session request) :disposition :record
                                          :basis (if refuse :refused :matched)})
             (cond-> {:audit [{:t :matched :member (:member request)}]}
               refuse (assoc :cell-refusals [refuse])))))

       ;; ── intake: open the procedureGuide session (G3/G4 membrane) ──
       (g/add-node :intake
         (fn [{:keys [request]}]
           (let [out (intk/transition
                      {"session_id" (:session request)
                       "member_did" (:member request)
                       "consent_ref" (:consent-ref request)
                       "procedure_id" (:procedure request)
                       "mode" (:mode request :member-self-submit)})
                 ccs (get out "cell_state")
                 refuse (refusal-of ccs)]
             (set-session-phase! store (:session request) :intaked)
             (store/append-ledger! store {:t :intaked :op (:op request)
                                          :procedure (:procedure request)
                                          :session (:session request) :disposition :record
                                          :basis (if refuse :refused :intaked)})
             (cond-> {:audit [{:t :intaked :session (:session request)}]}
               refuse (assoc :cell-refusals [refuse])))))

       ;; ── guide cell: 案内 + 必要書類 checklist (G5 membrane) ──
       (g/add-node :guide
         (fn [{:keys [request]}]
           (let [proc (store/procedure store (:procedure request))
                 out  (gde/transition
                       {"procedure_id" (:procedure-id proc)
                        "legal_basis" (:legal-basis proc)
                        "provenance" (:provenance proc)
                        "required_docs" (:required-docs proc)
                        "assist_mode" (get request :assist-mode :guide)})
                 ccs (get out "cell_state")
                 refuse (refusal-of ccs)
                 proposal (build-proposal :guide/build request proc)]
             (set-session-phase! store (:session request) :guided)
             (cond-> {:proposal proposal :audit [(trace request proposal)]}
               refuse (assoc :cell-refusals [refuse])))))

       ;; ── draft cell: form input-assist → applicationDraft (G5/G6 membrane) ──
       (g/add-node :draft
         (fn [{:keys [request]}]
           (let [proc (store/procedure store (:procedure request))
                 out  (drft/transition
                       {"session_id" (:session request)
                        "procedure_id" (:procedure-id proc)
                        "assist_mode" (get request :assist-mode :input-assist)
                        "encrypted_draft_ref" (:encrypted-pii-ref request)
                        "draft_body" (:plaintext-pii request)})
                 ccs (get out "cell_state")
                 refuse (refusal-of ccs)
                 proposal (build-proposal :draft/assist request proc)
                 payload (get ccs "payload")]
             (set-session-phase! store (:session request) :drafted)
             (cond-> {:proposal proposal
                      :record (when (and (nil? refuse) payload)
                                {:kind :draft :id (:session request) :value payload})
                      :audit [(trace request proposal)]}
               refuse (assoc :cell-refusals [refuse])))))

       ;; ── submit cell: the ONLY active-outbound (G3/G6/G10/G14/G15 membrane) ──
       (g/add-node :submit
         (fn [{:keys [request context]}]
           (let [proc (store/procedure store (:procedure request))
                 out  (sub/transition
                       {"session_id" (:session request)
                        "procedure_id" (:procedure-id proc)
                        "member_did" (:member request)
                        "consent_ref" (:consent-ref request)
                        "mode" (:mode request :member-self-submit)
                        "channel" (:channel request "online")
                        "verification_status" (:verification-status proc)
                        "freshness_ok" (boolean (fresh? context proc))
                        "encrypted_pii_ref" (:encrypted-pii-ref request)
                        "plaintext_pii" (:plaintext-pii request)
                        "council_gate_ref" (:council-gate-ref request)})
                 ccs (get out "cell_state")
                 refuse (refusal-of ccs)
                 proposal (build-proposal :submit/transmit request proc)
                 payload (get ccs "payload")
                 gated? (get ccs "gated")]
             (set-session-phase! store (:session request) :submitted)
             (cond-> {:proposal proposal
                      :record (when (and (nil? refuse) payload)
                                {:kind :submission :id (:session request)
                                 :value (assoc payload :gated gated?)})
                      :audit [(trace request proposal)]}
               refuse (assoc :cell-refusals [refuse])))))

       ;; ── govern: ProcedureGovernor censors the proposal (+ cell refusals) ──
       (g/add-node :govern
         (fn [{:keys [request proposal cell-refusals context]}]
           (let [v (gov/check request (or proposal {:effect :noop :confidence 0.0}) store
                             {:today (today-from context)})]
             {:verdict
              (if (seq cell-refusals)
                (assoc v :hard? true :ok? false
                       :violations (into (:violations v)
                                         (map (fn [r] {:rule :cell-refused :detail r}) cell-refusals)))
                v)})))

       ;; ── decide: phase gate → disposition ──
       (g/add-node :decide
         (fn [{:keys [request context verdict]}]
           (let [base (phase/verdict->disposition verdict)
                 ph   (or (get-in context [:phase]) phase/default-phase)
                 {:keys [disposition reason]} (phase/gate ph request base)]
             (case disposition
               :hold    {:disposition :hold
                         :audit [(cond-> (gov/hold-fact request verdict)
                                   reason (assoc :phase-reason reason :phase ph))]}
               :escalate {:disposition :escalate
                          :audit [{:t :approval-requested :op (:op request)
                                   :procedure (:procedure request) :session (:session request)
                                   :reason (or reason (if (:high-stakes? verdict) :agent-on-behalf-signoff
                                                          :low-confidence))
                                   :recommendation (:recommendation verdict)
                                   :phase ph :confidence (:confidence verdict)}]}
               :commit  {:disposition :commit}))))

       ;; ── request-approval: interrupt point (代行 G15 / low-confidence) ──
       (g/add-node :request-approval
         (fn [{:keys [request verdict approval]}]
           (if (= :approved (:status approval))
             (let [sf {:t :signoff :op (:op request) :procedure (:procedure request)
                       :session (:session request) :by (:by approval)
                       :mode (:mode request) :disposition :commit}]
               (store/append-ledger! store sf)        ; immutable audit-trail entry
               {:disposition :commit
                :audit [sf]})
             {:disposition :hold
              :audit [(merge (gov/hold-fact request
                                            (assoc verdict :violations
                                                   (conj (:violations verdict)
                                                         {:rule :signoff-rejected})))
                             {:t :signoff-rejected})]})))

       ;; ── commit: persist the assess artifact + ledger (assess path) ──
       (g/add-node :commit
         (fn [{:keys [request record]}]
           (let [f {:t (case (:op request)
                         :guide/build :guided
                         :draft/assist :drafted
                         :submit/transmit :submitted
                         :assessed)
                    :op (:op request) :procedure (:procedure request)
                    :session (:session request) :disposition :commit
                    :basis (:recommendation record)}]
             (when record (store/record-datom! store record))
             (store/append-ledger! store f)
             {:audit [f]})))

       ;; ── hold: append the hold ledger fact ──
       (g/add-node :hold
         (fn [{:keys [audit request]}]
           (when-let [hf (last (filter #(#{:procedure-hold :signoff-rejected} (:t %)) audit))]
             (store/append-ledger! store (assoc hf :disposition :hold)))
           (set-session-phase! store (:session request) :hold)
           {}))

       ;; ── status_track: 処理状況 + 結果 intake (G6 membrane) ──
       (g/add-node :status-track
         (fn [{:keys [request]}]
           (let [proc (store/procedure store (:procedure request))
                 out  (trck/transition
                       {"session_id" (:session request)
                        "procedure_id" (:procedure-id proc)
                        "statutory_days" (:statutory-days proc)
                        "status" (get request :status-state "processing")
                        "encrypted_result_ref" (:encrypted-result-ref request)
                        "plaintext_result" (:plaintext-result request)})
                 ccs (get out "cell_state")
                 refuse (refusal-of ccs)]
             (set-session-phase! store (:session request) :tracked)
             (store/append-ledger! store {:t :tracked :op (:op request)
                                          :procedure (:procedure request)
                                          :session (:session request)
                                          :disposition (if refuse :hold :commit)
                                          :basis (or refuse :tracked)})
             (cond-> {:audit [{:t :tracked :session (:session request)}]}
               refuse (assoc :cell-refusals [refuse])))))

       ;; ── wiring ──
       (g/set-entry-point :procedure-registry)
       (g/add-edge :procedure-registry :eligibility-match)
       (g/add-edge :eligibility-match :intake)
       (g/add-conditional-edges :intake
         (fn [{:keys [request]}]
           (case (:op request)
             :guide/build    :guide
             :draft/assist   :draft
             :submit/transmit :submit
             g/END)))
       (g/add-edge :guide :govern)
       (g/add-edge :draft :govern)
       (g/add-edge :submit :govern)
       (g/add-edge :govern :decide)
       (g/add-conditional-edges :decide
         (fn [{:keys [disposition]}]
           (case disposition :commit :commit, :escalate :request-approval, :hold)))
       (g/add-conditional-edges :request-approval
         (fn [{:keys [disposition]}] (if (= :commit disposition) :commit :hold)))
       ;; only a submit is tracked through to status_track; guide/draft commit → END
       (g/add-conditional-edges :commit
         (fn [{:keys [request]}]
           (if (= :submit/transmit (:op request)) :status-track g/END)))
       (g/add-edge :status-track g/END)
       (g/add-edge :hold g/END)

       (g/compile-graph
        {:checkpointer checkpointer :interrupt-before #{:request-approval}}))))
