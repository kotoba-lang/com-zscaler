(ns tatekata.methods.agent
  "tatekata 建方 — construction langgraph actor (kotoba WASM cell).

  ADR-2605250715, R0 scaffold. Faithful 1:1 port of py/agent.py.

  Constitutional gates enforced (as ex-info throws where flagged):
    G3  witness-quorum  — ≥2 distinct robot DIDs per phase boundary
    G11 KPI caps        — ≤2 stories / ≤5000 m² footprint
    G15 tithe-non-fiat  — USDC Base L2 + ERC-4337 + TitheRouter 10%
    G16 no-server-key   — owner/operator signs; platform holds no key
    G17 consent-bound   — compute-only R0; settlement stops at :intent

  This R0 build computes and returns plans/records; it does not dispatch
  real construction work and does not broadcast settlements (G17-gated).")

;; ── constants (verbatim from py/agent.py) ───────────────────────────

(def tithe-bps 1000)            ;; 10% TitheRouter auto-split (G15), basis points
(def max-stories 2)             ;; G11 KPI cap
(def max-footprint-m2 5000)     ;; G11 KPI cap
(def phases
  ["foundation" "structural" "mep" "finishing" "commissioning"])

;; ── G11 — KPI caps (stories / footprint) ────────────────────────────

(defn kpi-caps-ok
  "Check project scope against KPI caps.
  Port of `kpi_caps_ok(stories, footprint_m2)`."
  [stories footprint-m2]
  (cond
    (> stories max-stories)
    {:ok false :reason (str "stories " stories " > " max-stories " (G11)")}

    (> footprint-m2 max-footprint-m2)
    {:ok false :reason (str "footprint " footprint-m2 " m² > " max-footprint-m2 " (G11)")}

    :else
    {:ok true :reason "within KPI caps"}))

;; ── G3 — witness quorum (≥2 distinct robot DIDs per phase boundary) ─

(defn witness-quorum-ok
  "Verify ≥2 distinct robot DIDs witness the phase boundary.
  Port of `witness_quorum_ok(robot_dids)`."
  [robot-dids]
  (let [distinct (count (set robot-dids))]
    (if (< distinct 2)
      {:ok false :reason (str "witness count " distinct " < 2 (G3)")}
      {:ok true :reason "witness quorum satisfied"})))

;; ── Phase advancement state machine (G17 — every stage recorded) ────

(defn advance-phase
  "Advance one phase along phases. Return error if already terminal.
  Port of `advance_phase(current_phase)`."
  [current-phase]
  (let [idx (.indexOf phases current-phase)]
    (cond
      (= idx -1)
      {:state current-phase :blocked "unknown phase"}

      (= idx (dec (count phases)))
      {:state current-phase :blocked "already terminal (commissioning)"}

      :else
      {:state (nth phases (inc idx)) :blocked nil})))

;; ── foundation_excavation handler ───────────────────────────────────

(defn handle-foundation-excavation
  "Parse site plan, survey utilities, generate giemon excavation plan, approve.
  Port of `handle_foundation_excavation(state)`."
  [state]
  (let [spec (get state :spec (get state "spec" ""))]
    (if (empty? spec)
      (assoc state :blocked "spec required")
      (assoc state :plan
             {:phase              "foundation"
              :spec               spec
              :giemon-plan-type   "excavation-grid-5m-spacing"
              :trajectory-logged  true}))))

;; ── structural_assembly handler ──────────────────────────────────────

(defn handle-structural-assembly
  "Load BIM, validate foundations, coordinate robots, get metrology witness.
  Port of `handle_structural_assembly(state)`."
  [state]
  (let [bim-ref (get state :bim_ref (get state "bim_ref" ""))]
    (if (empty? bim-ref)
      (assoc state :blocked "BIM reference required")
      (assoc state :plan
             {:phase              "structural"
              :bim-ref            bim-ref
              :robot-coordination "giemon-otete-shoring-sequence"
              :metrology          "mimi-witness-plumb-level"}))))

;; ── mep_installation handler ─────────────────────────────────────────

(defn handle-mep-installation
  "Route ductwork, conduit, piping; perform pressure testing.
  Port of `handle_mep_installation(state)`."
  [state]
  (let [mep-design (get state :mep_design (get state "mep_design" ""))]
    (if (empty? mep-design)
      (assoc state :blocked "MEP design required")
      (assoc state :plan
             {:phase          "mep"
              :ductwork-route "otete-arm-3d-trajectory"
              :conduit-route  "otete-arm-conduit-chase"
              :piping-route   "otete-arm-press-fit-sequence"
              :pressure-test-type "pneumatic-5-bar-15min"}))))

;; ── finishing_handoff handler ─────────────────────────────────────────

(defn handle-finishing-handoff
  "Prep surfaces, drywall/tape/mud (R0 manual), paint, trim install.
  Port of `handle_finishing_handoff(state)`."
  [state]
  (let [phase-input (get state :phase_input (get state "phase_input" ""))]
    (if (empty? phase-input)
      (assoc state :blocked "phase input required")
      (assoc state :plan
             {:phase        "finishing"
              :surface-prep "giemon-substrate-clean"
              :drywall      "manual-subcontractor-r0"
              :paint        "spray-cure-r0"
              :trim         "manual-installation-r0"}))))

;; ── commissioning handler ─────────────────────────────────────────────

(defn handle-commissioning
  "Final systems test, defect walkdown, waste inventory, sign-off.
  Port of `handle_commissioning(state)`."
  [state]
  (let [phase-input (get state :phase_input (get state "phase_input" ""))]
    (if (empty? phase-input)
      (assoc state :blocked "phase input required")
      (assoc state :plan
             {:phase               "commissioning"
              :hvac-test           "airflow-temp-control-verify"
              :electrical-test     "circuit-continuity-load-test"
              :plumbing-test       "water-pressure-flow-rate"
              :defect-walkdown     "photo-punch-list-generation"
              :waste-categorization "reuse/recycle/landfill percent"}))))

;; ── progress record ───────────────────────────────────────────────────

(defn record-phase-progress
  "Create a phase-boundary progress record. Requires ≥2 robot witnesses (G3).
  Port of `record_phase_progress(phase, project_did, robot_dids, photo_cid, depth_map_cid)`."
  ([phase project-did robot-dids]
   (record-phase-progress phase project-did robot-dids "" ""))
  ([phase project-did robot-dids photo-cid depth-map-cid]
   (let [witness (witness-quorum-ok robot-dids)]
     (if-not (:ok witness)
       {:error (:reason witness) :blocked true}
       {":progress/phase"        phase
        ":progress/project-did" project-did
        ":progress/robot-dids"  robot-dids
        ":progress/photo-cid"   photo-cid
        ":progress/depth-map-cid" depth-map-cid}))))

;; ── settlement — USDC + TitheRouter intent (NOT broadcast; G15/G16/G17) ──

(defn build-settlement-intent
  "Compute the USDC settlement split. 10% tithe → Public Fund.
  Stops at :intent — broadcast needs owner signature (G16) + operator gate (G17).
  Port of `build_settlement_intent(gross_minor, owner_sig_ref)`."
  ([gross-minor]
   (build-settlement-intent gross-minor nil))
  ([gross-minor owner-sig-ref]
   (let [tithe (quot (* gross-minor tithe-bps) 10000)]
     {:rail              "usdc-base-l2"
      :grossMinor        gross-minor
      :titheMinor        tithe
      :ownerPayoutMinor  (- gross-minor tithe)
      :titheRouter       "50-infra/etzhayyim-tithe-router"
      :state             (if owner-sig-ref "executed" "intent")
      :ownerSigRef       (or owner-sig-ref "")})))
