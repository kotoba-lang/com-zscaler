(ns noroshi.cells.device-design.state-machine
  "Phase state machine for the noroshi (烽) device_design cell — the chip
  face's generative photonic-device design job (ADR-2606051600; matures this
  cell from a pure `.edn` scaffold to real, tested logic, alongside
  `reliability_qual`).

  An NL-shaped device-design intent (kind + force-class, + optional
  name/route-um/line-rate-gbps/energy-pj-bit/eda) is civilian-gated (G3/N1)
  before any EDA plan is generated (G1), then compiled into a photonicDevice-
  shaped record (`kotoba/schema.edn`'s `:pdev/*` attributes; `:representative`
  true, G10). DELIBERATELY calls `noroshi.methods.device-design` directly
  (the plan-generation ENGINE, not a stub) — see
  `cells/reliability_qual/state_machine.cljc`'s docstring for why this
  maturity pass departs from `active_alignment`'s/`fibre_loop`'s
  \"state machine never calls its methods/ sibling\" precedent.

  Phase order: init -> intent_captured -> civilian_cleared -> plan_generated
  -> device_emitted. `next_node` values match this cell's own
  `cells/device_design.edn` `:cell/state-graph` node ids."
  (:require [noroshi.methods.device-design :as dd]))

(def ^:private defaults
  {"phase" "init" "kind" "" "force_class" "" "name" nil "route_um" nil
   "line_rate_gbps" nil "energy_pj_bit" nil "eda" nil "payload" {}})

(defn- state* [state] (merge defaults (get state "cell_state" {})))

(def ^:private intent-fields
  ["kind" "force_class" "name" "route_um" "line_rate_gbps" "energy_pj_bit" "eda"])

(defn- intent-map
  "cell_state (snake_case string keys) -> the kebab-keyword intent map
  noroshi.methods.device-design expects."
  [cs]
  {:kind (get cs "kind") :force-class (get cs "force_class") :name (get cs "name")
   :route-um (get cs "route_um") :line-rate-gbps (get cs "line_rate_gbps")
   :energy-pj-bit (get cs "energy_pj_bit") :eda (get cs "eda")})

(defn transition-intent
  "Capture the intent fields present on the incoming request onto cell_state."
  [state]
  (let [cs (reduce (fn [cs k] (if (contains? state k) (assoc cs k (get state k)) cs))
                    (state* state) intent-fields)]
    {"cell_state" (assoc cs "phase" "intent_captured")
     "next_node" "civilian-gate"}))

(defn transition-civilian-gate
  "G1/G3/N1: refuse an unknown kind or a non-civilian force-class before any
  plan is generated. Raises via `device-design/civilian-gate`."
  [state]
  (let [cs (state* state)]
    (dd/civilian-gate (intent-map cs))
    {"cell_state" (assoc cs "phase" "civilian_cleared")
     "next_node" "epda-plan"}))

(defn transition-epda-plan
  "Generate the open-EDA ModelOp plan (G1) via `device-design/design-plan`
  (civilian-gated again internally — cheap, and matches the \"always gate at
  the point of action\" posture `active_alignment`'s `align`/`coarse-scan`/
  `spiral-search` each independently re-run `enable-laser` before probing)."
  [state]
  (let [cs (state* state)
        plan (dd/design-plan (intent-map cs))]
    {"cell_state" (assoc cs "phase" "plan_generated"
                         "payload" (assoc (get cs "payload") "plan" plan))
     "next_node" "emit"}))

(defn transition-emit
  "Emit the photonicDevice-shaped record (the kotoba :pdev/* datom shape)."
  [state]
  (let [cs (state* state)
        plan (get-in cs ["payload" "plan"])
        dev (dd/device-record (intent-map cs) plan)]
    {"cell_state" (assoc cs "phase" "device_emitted"
                         "payload" (assoc (get cs "payload") "device"
                                          {"id" (:id dev) "kind" (:kind dev)
                                           "platform" (:platform dev)
                                           "forceClass" (:force-class dev)
                                           "lineRateGbps" (:line-rate-gbps dev)
                                           "energyPjPerBit" (:energy-pj-bit dev)
                                           "process" (:process dev) "eda" (:eda dev)
                                           "representative" (:representative dev)}))
     "next_node" "end"}))
