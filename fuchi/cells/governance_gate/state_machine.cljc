(ns fuchi.cells.governance-gate.state-machine
  "Phase state machine for 扶持 (fuchi) governance_gate — the G7 non-adjudicating router.
  1:1 port of cells/governance_gate/state_machine.py (ADR-2606052300). Routes an allocation to the
  body that decides it (rider→refused / invariant→council-lv7 / >ceiling→sbt-vote / else→auto), then
  appends the outcome (only :sbt-vote consults a tally). 扶持 computes+routes, NEVER decides (非裁定)."
  (:require [clojure.string :as str]))

(def optimistic-ceiling-usd-micros-yr 24000000000)
(def rider-forbidden ["advertis" "affiliate" "adsense" "weapon" "munition" "fire-control" "surveillance" "biometric" "addictive" "dark-pattern" "広告" "兵器"])
(def invariant-touch-tokens ["commons-land" "land-grant" "new-land" "force" "license-change" "charter"])

(def state-defaults {"phase" "init" "alloc_id" "" "imputed_total" 0 "context" "" "route" "" "mechanism" "" "outcome" ""})
(defn- cell-state [state] (merge state-defaults (get state "cell_state" {})))
(defn- to-int [v] (long (or v 0)))
(defn- rider [text] (let [t (str/lower-case (str (or text "")))] (some #(when (str/includes? t %) %) rider-forbidden)))
(defn- touches-invariant? [text] (let [t (str/lower-case (str (or text "")))] (boolean (some #(str/includes? t %) invariant-touch-tokens))))

(defn transition-to-routed [state]
  (let [cs (cell-state state)
        cs (assoc cs "alloc_id" (get state "alloc_id" (get cs "alloc_id"))
                  "imputed_total" (to-int (get state "imputed_total" (get cs "imputed_total")))
                  "context" (get state "context" (get cs "context")))
        [route mech] (cond
                       (rider (get cs "context")) ["refused" "council-lv7"]
                       (touches-invariant? (get cs "context")) ["council-lv7" "council-lv7"]
                       (> (get cs "imputed_total") optimistic-ceiling-usd-micros-yr) ["sbt-vote" "sbt-vote"]
                       :else ["auto" "optimistic"])]
    {"cell_state" (assoc cs "route" route "mechanism" mech "phase" "routed")}))

(defn transition-to-decided [state]
  (let [cs (cell-state state)]
    (if (not= (get cs "phase") "routed")
      {"cell_state" (assoc cs "outcome" "pending" "phase" "decided")}
      (let [outcome (case (get cs "route")
                      "auto" "accepted"
                      "refused" "refused"
                      "council-lv7" "pending"
                      "sbt-vote" (if (> (to-int (get state "yes" 0)) (to-int (get state "no" 0))) "accepted" "rejected")
                      "pending")]
        {"cell_state" (assoc cs "outcome" outcome "phase" "decided")}))))

(defn solve [_input-state]
  (throw (ex-info "fuchi R0 scaffold: activate governance_gate via Council ADR (post-2606052300 ratification)" {:scaffold true})))
