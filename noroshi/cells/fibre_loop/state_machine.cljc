(ns noroshi.cells.fibre-loop.state-machine
  "1:1 port of cells/fibre_loop/state_machine.py (ADR-2606051600) — the noroshi 烽
  fibre-laying operational loop. A cable-lay plow/ROV lays fibre along a planned
  route, the fibre is actively aligned to a coupler, the two ends are fusion-spliced,
  and the segment is committed as a member-signed, server-keyless, dry-run job.

  Phase order: lay → align → splice → commit. Pure unit-tested transitions; .solve()
  raises until Council activation (live actuation G8-gated).

  Invariants (reusing the shared infra-robotics safety gates — the SAME audited gates
  the python imports from kuni-umi/robotics/safety.py, here `kuni-umi.robotics.safety`):
    N1/G3 civilian-use (assert-civilian closed-world) · G5 laser-safety inherited from
    the active-alignment core · G7 no-server-key (require-member-signature, serverHeldKey
    false) · G8 witness quorum ≥2 (witness-quorum-ok) + dry-run-only at R0.

  FibreState dataclass → string-keyed map under \"cell_state\"; Python ValueError →
  (throw (ex-info ...)). Matches the active_alignment cell.cljc convention."
  (:require [kuni-umi.robotics.safety :as safety]))

(def permitted-uses ["lay" "align" "splice" "inspect" "repair" "bury"])
(def splice-loss-max-db 0.10)
(def ^:private splice-k-offset 0.0016)
(def ^:private splice-k-angle 0.012)

(defn- round6 [x]
  (/ (Math/rint (* (double x) 1.0e6)) 1.0e6))

(defn splice-loss-db
  "Fusion-splice insertion loss (dB) — grows with lateral offset² + cleave-angle².
  Mirrors the methods core; round to 6 (Python round)."
  [lateral-offset-um cleave-angle-deg]
  (let [off (Math/abs (double lateral-offset-um))
        ang (Math/abs (double cleave-angle-deg))]
    (round6 (+ (* splice-k-offset off off) (* splice-k-angle ang ang)))))

(def ^:private defaults
  {"phase" "init" "robot_id" "noroshi-cablelay-01" "op" "lay-align-splice" "use" "lay"
   "track_converged" false "final_xte_m" 0.0
   "coupling_loss_db" 0.0 "align_converged" false
   "splice_offset_um" 0.0 "splice_cleave_angle_deg" 0.0 "splice_loss_db" 0.0 "splice_passed" false
   "member_sig" "" "server_sig" "" "server_held_key" false "witness_sigs" [] "payload" {}})

(defn- state* [state] (merge defaults (get state "cell_state" {})))

(defn transition-lay
  "N1/G3: refuse a non-civilian use, then record the cross-track-tracking outcome."
  [state]
  (let [cs0 (state* state)
        use (get state "use" (get cs0 "use"))]
    (safety/assert-civilian use permitted-uses)   ;; raises on a non-civilian / force use
    (let [track-converged (boolean (get state "track_converged" (get cs0 "track_converged")))
          final-xte-m (double (get state "final_xte_m" (get cs0 "final_xte_m")))]
      (when-not track-converged
        (throw (ex-info "lay phase: the plow has not converged onto the planned route"
                        {:noroshi/violation :lay})))
      (let [cs (assoc cs0 "use" use "track_converged" track-converged "final_xte_m" final-xte-m
                      "phase" "laid"
                      "payload" (assoc (get cs0 "payload") "lay"
                                       {"trackConverged" track-converged "finalXteM" final-xte-m}))]
        {"cell_state" cs "next_node" "run_align"}))))

(defn transition-run-align
  "Record the achieved coupling loss from the reused Hooke-Jeeves aligner (G5 in core)."
  [state]
  (let [cs0 (state* state)
        coupling (double (get state "coupling_loss_db" (get cs0 "coupling_loss_db")))
        converged (boolean (get state "align_converged" (get cs0 "align_converged")))]
    (when (< coupling 0.0)
      (throw (ex-info "coupling loss must be ≥ 0 dB" {:noroshi/violation :align})))
    (when-not converged
      (throw (ex-info "align phase: the active-alignment search did not converge"
                      {:noroshi/violation :align})))
    (let [cs (assoc cs0 "coupling_loss_db" coupling "align_converged" converged "phase" "aligned"
                    "payload" (assoc (get cs0 "payload") "align" {"couplingLossDb" coupling}))]
      {"cell_state" cs "next_node" "run_splice"})))

(defn transition-run-splice
  "Evaluate the fusion splice against the acceptance threshold (reuses the loss model)."
  [state]
  (let [cs0 (state* state)
        off (double (get state "splice_offset_um" (get cs0 "splice_offset_um")))
        ang (double (get state "splice_cleave_angle_deg" (get cs0 "splice_cleave_angle_deg")))
        loss (splice-loss-db off ang)
        passed (<= loss splice-loss-max-db)]
    (when-not passed
      (throw (ex-info (str "splice phase: loss " loss " dB exceeds the acceptance threshold "
                           splice-loss-max-db " dB")
                      {:noroshi/violation :splice})))
    (let [cs (assoc cs0 "splice_offset_um" off "splice_cleave_angle_deg" ang
                    "splice_loss_db" loss "splice_passed" passed "phase" "spliced"
                    "payload" (assoc (get cs0 "payload") "splice" {"lossDb" loss "passed" passed}))]
      {"cell_state" cs "next_node" "commit_segment"})))

(defn transition-commit-segment
  "G7 + G8: commit a member-signed, witness-quorum'd segment with NO server-held key (dry-run)."
  [state]
  (let [cs0 (state* state)
        member-sig (get state "member_sig" (get cs0 "member_sig"))
        server-sig (get state "server_sig" (get cs0 "server_sig"))
        witness-sigs (vec (get state "witness_sigs" (get cs0 "witness_sigs")))]
    (safety/require-member-signature member-sig server-sig)   ;; G7: raises on server-sig / missing member-sig
    (let [wq (safety/witness-quorum-ok witness-sigs)]          ;; G8: ≥2 independent robot DIDs
      (when-not (:ok wq)
        (throw (ex-info (str "G8 violation: " (:reason wq)) {:noroshi/violation :g8})))
      (let [cs (assoc cs0 "member_sig" member-sig "server_sig" server-sig "witness_sigs" witness-sigs
                      "server_held_key" false "phase" "segment_committed"
                      "payload" (assoc (get cs0 "payload") "segment"
                                       {"robotId" (get cs0 "robot_id")
                                        "op" (get cs0 "op")
                                        "use" (get cs0 "use")
                                        "finalXteM" (get cs0 "final_xte_m")
                                        "couplingLossDb" (get cs0 "coupling_loss_db")
                                        "spliceLossDb" (get cs0 "splice_loss_db")
                                        "splicePassed" (get cs0 "splice_passed")
                                        "memberSig" member-sig
                                        "witnessOk" true
                                        "serverHeldKey" false
                                        "dryRun" true}))]
        {"cell_state" cs "next_node" "end"}))))
