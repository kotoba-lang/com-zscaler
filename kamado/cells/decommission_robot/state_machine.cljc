(ns kamado.cells.decommission-robot.state-machine
  "1:1 port of cells/decommission_robot/state_machine.py (ADR-2606051500) — the kamado
  竈 hot-zone purge → entry-gated cut phase machine. The runnable purge + entry loop
  lives in methods/decommission_robot (already cljc-ported); this wires it into a phase
  machine ending at a member-signed, dry-run cut record. .solve() stays Council-gated —
  these transitions are exercised by tests, not live actuation. Reuses the audited
  methods core (kamado.methods.decommission-robot: purge-to-entry / plan-cut-entry /
  to-datoms) — actors compose.

  Invariants: ENTRY GATE (cut requires a completed purge; un-purged zone → SafetyError in
  plan-cut-entry) · G3 civilian-use closed-world · G5/G7 no-server-key · G8 witness quorum
  ≥2 + dry-run-only at R0 · G9 labor-liberation (freed worker → BHI cohort). RobotState
  dataclass → string-keyed map under \"cell_state\"; Python ValueError → (throw (ex-info ...))."
  (:require [kamado.methods.decommission-robot :as m]))

(def ^:private defaults
  {"phase" "init" "job_id" "decom-01" "refinery" "rf.jp.muroran" "robot_id" "kamado-arm-01"
   "gas" "H2S" "use" "decommission" "target_ppm" m/H2S-ENTRY-PPM "gas_limit" m/H2S-ENTRY-PPM
   "initial_ppm" 120.0 "target_x" 1.5 "target_y" 0.4 "human_present" false
   "member_sig" "" "server_sig" "" "witness_sigs" []
   "final_ppm" 0.0 "entry_permitted" false "reachable" false "envelope_ok" false "payload" {}})

(defn- state* [state] (merge defaults (get state "cell_state" {})))

(defn transition-purge
  "Run the purge/inert loop (raises on non-civilian / fossil life-extension use, N1/G3)."
  [state]
  (let [cs0 (state* state)
        use (get state "use" (get cs0 "use"))
        gas (get state "gas" (get cs0 "gas"))
        target-ppm (double (get state "target_ppm" (get cs0 "target_ppm")))
        gas-limit (double (get state "gas_limit" (get cs0 "gas_limit")))
        initial-ppm (double (get state "initial_ppm" (get cs0 "initial_ppm")))
        res (m/purge-to-entry :gas gas :target-ppm target-ppm :use use :initial-ppm initial-ppm)
        cs (assoc cs0 "use" use "gas" gas "target_ppm" target-ppm "gas_limit" gas-limit
                  "initial_ppm" initial-ppm
                  "final_ppm" (:final-ppm res) "entry_permitted" (:entry-permitted res)
                  "phase" "purged"
                  "payload" (assoc (get cs0 "payload")
                                   "_purge" res
                                   "purge" {"gas" (:gas res) "targetPpm" (:target-ppm res)
                                            "finalPpm" (:final-ppm res) "entryPermitted" (:entry-permitted res)
                                            "settlingSeconds" (:settling-seconds res)}))]
    {"cell_state" cs "next_node" "plan_cut"}))

(defn transition-plan-cut
  "ENTRY GATE: plan the entry + cut. Raises if the zone is un-purged."
  [state]
  (let [cs0 (state* state)]
    (when (not= (get cs0 "phase") "purged")
      (throw (ex-info "cut plan requires a completed purge first (entry gate)" {:kamado/violation :entry-gate})))
    (let [target-x (double (get state "target_x" (get cs0 "target_x")))
          target-y (double (get state "target_y" (get cs0 "target_y")))
          human-present (boolean (get state "human_present" (get cs0 "human_present")))
          member-sig (get state "member_sig" (get cs0 "member_sig"))
          server-sig (get state "server_sig" (get cs0 "server_sig"))
          witness-sigs (get state "witness_sigs" (get cs0 "witness_sigs"))
          ;; plan-cut-entry raises on un-purged zone / non-civilian use / server-key (the safety crux)
          plan (m/plan-cut-entry :target-xy [target-x target-y]
                                 :final-ppm (get cs0 "final_ppm") :gas-limit (get cs0 "gas_limit")
                                 :member-sig member-sig :witness-sigs witness-sigs
                                 :use (get cs0 "use") :human-present human-present :server-sig server-sig)
          cs (assoc cs0 "target_x" target-x "target_y" target-y "human_present" human-present
                    "member_sig" member-sig "server_sig" server-sig "witness_sigs" witness-sigs
                    "reachable" (get plan ":reachable") "envelope_ok" (get plan ":envelopeOk")
                    "phase" "cut_planned"
                    "payload" (assoc (get cs0 "payload") "_plan" plan "cut" plan))]
      {"cell_state" cs "next_node" "commit_entry"})))

(defn transition-commit-entry
  "Commit a dry-run entry-cut record only if reachable + envelope-safe + quorum met."
  [state]
  (let [cs0 (state* state)]
    (when (not= (get cs0 "phase") "cut_planned")
      (throw (ex-info "commit requires a planned cut first" {:kamado/violation :commit-order})))
    (let [plan (get-in cs0 ["payload" "_plan"])]
      (when-not (get cs0 "reachable")
        (throw (ex-info "target unreachable: cannot commit entry-cut" {:kamado/violation :unreachable})))
      (when-not (get cs0 "envelope_ok")
        (throw (ex-info "trajectory violates safety envelope: cannot commit entry-cut" {:kamado/violation :envelope})))
      (when-not (get plan ":witnessOk")
        (throw (ex-info "witness quorum < 2 (G8): cannot commit entry-cut" {:kamado/violation :g8})))
      (let [record (-> (m/to-datoms (get-in cs0 ["payload" "_purge"]) plan
                                    :job-id (get cs0 "job_id") :refinery (get cs0 "refinery")
                                    :robot-id (get cs0 "robot_id"))
                       (assoc ":decommission/committed" true))
            cs (assoc cs0 "phase" "entry_committed"
                      "payload" (assoc (get cs0 "payload") "record" record))]
        {"cell_state" cs "next_node" "end"}))))
