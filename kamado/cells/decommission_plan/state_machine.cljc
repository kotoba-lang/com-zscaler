(ns kamado.cells.decommission-plan.state-machine
  "1:1 port of cells/decommission_plan/state_machine.py (ADR-2606051500) — the kamado 竈 fossil-
  refinery decommission/transition planner. §2(d)-permitted robotics on an EXISTING fossil refinery:
  shut down / purge / clean / dismantle / remediate / convert — NEVER expand/restart/extend. Pure
  phase-progression init → scoped → planned → gated with the gates enforced as transitions.

  Invariants: G3 intervention ∈ {decommission,remediate,convert,monitor} (fossil life-extension
  unrepresentable) · G5 no-server-key (member/operator signs; serverHeldKey false) · G8 outward-
  gated (R0 = intent-only dry-run). PlanState dataclass → string-keyed map under \"cell_state\";
  ValueError → (throw (ex-info ...))."
  (:require [clojure.string :as str]))

(def allowed-intervention #{"decommission" "remediate" "convert" "monitor"})
(def allowed-convert #{"hikari-solar" "synthesis-plant" "materials-recovery" "remediated-land" "none"})
(def allowed-principal #{"operator" "member"})

(def ^:private defaults
  {"phase" "init" "refinery" "" "intervention" "decommission" "convert_to" "none"
   "principal" "operator" "server_held_key" false "outward_gated" true "payload" {}})

(defn- state* [state] (merge defaults (get state "cell_state" {})))

(defn- norm
  "Port of _norm: strip a leading ':' from a string value (keyword-style → bare); non-strings via str."
  [v]
  (if (string? v) (str/replace (or v "") #"^:+" "") (str v)))

(defn transition-to-scoped
  "G3: scope the intervention. Raises on any fossil life-extension or unknown convert target."
  [state]
  (let [cs0 (state* state)
        intervention (norm (get state "intervention" (get cs0 "intervention")))]
    (when-not (contains? allowed-intervention intervention)
      (throw (ex-info (str "G3 violation: intervention '" intervention "' is not representable; only "
                           allowed-intervention " permitted on an existing fossil asset (§2(d) — "
                           "decommission/transition only; never expand/restart/extend a fossil unit).")
                      {:kamado/violation :g3 :intervention intervention})))
    (let [convert-to (norm (get state "convert_to" (get cs0 "convert_to")))]
      (when-not (contains? allowed-convert convert-to)
        (throw (ex-info (str "unknown convert-to target '" convert-to "'") {:kamado/convert convert-to})))
      {"cell_state" (assoc cs0
                           "refinery" (get state "refinery" (get cs0 "refinery"))
                           "intervention" intervention
                           "convert_to" convert-to
                           "server_held_key" (boolean (get state "server_held_key" (get cs0 "server_held_key")))
                           "phase" "scoped")})))

(defn transition-to-planned
  "G5: build the dry-run plan. The server holds no key; actuation is signed externally."
  [state]
  (let [cs0 (state* state)]
    (when (not= (get cs0 "phase") "scoped")
      (throw (ex-info "plan requires a scoped intervention first (G3)" {:kamado/violation :g3})))
    (let [principal (norm (get state "principal" (get cs0 "principal")))]
      (when-not (contains? allowed-principal principal)
        (throw (ex-info (str "principal '" principal "' must be operator|member (G5)") {:kamado/violation :g5})))
      (when (or (get cs0 "server_held_key") (get state "server_held_key"))
        (throw (ex-info "G5 violation: serverHeldKey must be false (member/operator signs)" {:kamado/violation :g5})))
      {"cell_state" (assoc cs0
                           "principal" principal
                           "server_held_key" false
                           "payload" {"refinery" (get cs0 "refinery") "intervention" (get cs0 "intervention")
                                      "convertTo" (get cs0 "convert_to") "principal" principal "serverHeldKey" false}
                           "phase" "planned")})))

(defn transition-to-gated
  "G8: real teardown stays gated. R0 emits an intent, never an actuation."
  [state]
  (let [cs0 (state* state)]
    (when (not= (get cs0 "phase") "planned")
      (throw (ex-info "gate requires a planned intervention first" {:kamado/violation :g8})))
    {"cell_state" (assoc cs0
                         "outward_gated" true
                         "payload" (assoc (get cs0 "payload") "outwardGated" true "status" "intent-only")
                         "phase" "gated")}))
