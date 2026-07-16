(ns noroshi.cells.active-alignment.state-machine
  "1:1 port of cells/active_alignment/state_machine.py (ADR-2606051600) — the noroshi 烽 photonic-
  packaging safety-critical coded cell. A robot aligns an optical fibre to an on-chip grating
  coupler: it must pass a hard laser-safety gate before energising, and the packaging job must be
  member-signed with NO server-held key. Pure, unit-tested transitions (the Hooke-Jeeves alignment
  search itself lives in methods/active_alignment, not here — coupling loss is passed through).

  Invariants: G3/N1 civilian-use (weaponisation unrepresentable) · G5 laser-safety (hazardous IEC
  60825 class ⇒ interlock + attestation) · G7 no-server-key (member-signed, server sig refused) ·
  G8 outward-gated (dry-run only at R0). AlignState dataclass → string-keyed map under \"cell_state\";
  Python ValueError → (throw (ex-info ...)).")

(def permitted-uses #{"alignment" "soldering" "trimming" "inspection" "comms"})
(def forbidden-uses #{"weapon" "directed-energy" "dazzle" "fire-control"})
(def hazardous-classes #{"2" "3R" "3B" "4"})

(def ^:private defaults
  {"phase" "init" "robot_id" "noroshi-aligner-01" "op" "active-align"
   "laser_class" "1" "use" "alignment" "interlock" false "attestation_ref" ""
   "coupling_loss_db" 0.0 "member_sig" "" "server_sig" "" "server_held_key" false "payload" {}})

(defn- state* [state] (merge defaults (get state "cell_state" {})))

(defn transition-verify-laser-safety
  "G3/N1 + G5: refuse to energise unless the use is civilian and any hazardous class is interlocked."
  [state]
  (let [cs (-> (state* state)
               (assoc "use" (get state "use" (get (state* state) "use"))
                      "laser_class" (get state "laser_class" (get (state* state) "laser_class"))
                      "interlock" (get state "interlock" (get (state* state) "interlock"))
                      "attestation_ref" (get state "attestation_ref" (get (state* state) "attestation_ref"))))
        use (get cs "use")
        lclass (get cs "laser_class")]
    (when (or (contains? forbidden-uses use) (not (contains? permitted-uses use)))
      (throw (ex-info (str "N1 violation: laser use '" use "' is not a permitted civilian fab use; "
                           "weaponisation / directed-energy can never be energised (Mission Charter §1.12)")
                      {:noroshi/violation :n1 :use use})))
    (when (contains? hazardous-classes lclass)
      (when-not (get cs "interlock")
        (throw (ex-info (str "G5 violation: a Class-" lclass " laser requires a physical enclosure "
                             "interlock before energising") {:noroshi/violation :g5 :laser-class lclass})))
      (when (empty? (get cs "attestation_ref"))
        (throw (ex-info (str "G5 violation: a Class-" lclass " laser requires an operator safety "
                             "attestation reference before energising") {:noroshi/violation :g5 :laser-class lclass}))))
    {"cell_state" (assoc cs "phase" "laser_safe") "next_node" "run_alignment"}))

(defn transition-run-alignment
  "Run (a stand-in for) the Hooke-Jeeves active-alignment search; record the achieved loss."
  [state]
  (let [cs (state* state)
        loss (double (get state "coupling_loss_db" (get cs "coupling_loss_db")))]
    (when (< loss 0.0)
      (throw (ex-info "coupling loss must be ≥ 0 dB" {:noroshi/violation :loss})))
    {"cell_state" (assoc cs "coupling_loss_db" loss "phase" "aligned"
                         "payload" (assoc (get cs "payload") "alignment" {"couplingLossDb" loss}))
     "next_node" "commit_job"}))

(defn transition-commit-job
  "G7: commit a member-signed packaging job with NO server-held key; refuse any server signature."
  [state]
  (let [cs0 (state* state)
        cs (assoc cs0
                  "member_sig" (get state "member_sig" (get cs0 "member_sig"))
                  "server_sig" (get state "server_sig" (get cs0 "server_sig"))
                  "server_held_key" false)
        server-sig (get cs "server_sig")
        member-sig (get cs "member_sig")]
    (when (seq server-sig)
      (throw (ex-info "G7 violation: server signature refused (no-server-key, ADR-2605231525)"
                      {:noroshi/violation :g7})))
    (when-not (seq member-sig)
      (throw (ex-info "G7 violation: a member/operator signature is required to commit a packaging job"
                      {:noroshi/violation :g7})))
    {"cell_state" (assoc cs "phase" "job_committed"
                         "payload" (assoc (get cs "payload") "job"
                                          {"robotId" (get cs "robot_id")
                                           "op" (get cs "op")
                                           "use" (get cs "use")
                                           "laserClass" (get cs "laser_class")
                                           "interlock" (get cs "interlock")
                                           "couplingLossDb" (get cs "coupling_loss_db")
                                           "memberSig" member-sig
                                           "serverHeldKey" false
                                           "dryRun" true}))
     "next_node" "end"}))
