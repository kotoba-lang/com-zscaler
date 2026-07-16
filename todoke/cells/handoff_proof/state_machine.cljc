(ns todoke.cells.handoff-proof.state-machine
  "Phase state machine for the todoke 届け handoff_proof (受渡証) cell.
  1:1 port of cells/handoff_proof/state_machine.py (ADR-2606042300).

  Proof-of-delivery for the final metre — the actor's privacy spine. Two constitutional invariants
  by construction:
    G8 privacy-by-construction — proof produced ON-DEVICE only; the only admissible proof kinds are
       :recipient-signature, :locker-code, :on-device-photo-hash. A :cloud-image / :face-match /
       :biometric kind is unrepresentable → ex-info refusal (N5).
    G12 no-server-key + G13 consent-bound — the recipient signs the hand-off; the server never signs,
       and a doorstep hand-off without a recorded recipient consent reference is refused.

  Pure, unit-tested transitions; the cell's .solve() raises until Council activation.
  Conventions: dataclass HandoffState → a plain map with the SAME string field keys the Python
  `cs.__dict__` round-trips; phase enum value identities stay strings; ValueError → ex-info."
  (:require [clojure.string :as str]))

;; G8: the ONLY admissible proof kinds. Cloud/biometric kinds are unrepresentable → refused.
(def admissible-proof-kinds #{"recipient-signature" "locker-code" "on-device-photo-hash"})
(def forbidden-proof-kinds #{"cloud-image" "face-match" "biometric"})

;; ── HandoffPhase (enum — Python value identities preserved) ──
(def handoff-phases
  {:init             "init"
   :arrived          "arrived"
   :consent-verified "consent_verified"
   :proof-captured   "proof_captured"
   :proof-sealed     "proof_sealed"})

(def phase-init             (:init handoff-phases))
(def phase-arrived          (:arrived handoff-phases))
(def phase-consent-verified (:consent-verified handoff-phases))
(def phase-proof-captured   (:proof-captured handoff-phases))
(def phase-proof-sealed     (:proof-sealed handoff-phases))

;; ── HandoffState (dataclass → plain map, string keys + field defaults) ──
(def state-defaults
  {"phase"          phase-init
   "job_id"         "did:web:todoke.etzhayyim.com/job/demo-0001"
   "recipient_did"  ""
   "consent_ref"    ""                       ; encrypted consent record reference (G13)
   "proof_kind"     "recipient-signature"
   "recipient_sig"  ""                       ; member/recipient signature (G12 — server never signs)
   "server_signed"  false                    ; must stay false (G12 invariant)
   "on_device_only" true                     ; G8 invariant — proof never leaves the device as imagery
   "payload"        {}})

(defn- cell-state [state]
  (merge state-defaults (get state "cell_state" {})))

(defn transition-to-arrived [state]
  (let [cs (cell-state state)
        cs (assoc cs
                  "phase" phase-arrived
                  "job_id" (get state "job_id" (get cs "job_id"))
                  "recipient_did" (get state "recipient_did" (get cs "recipient_did")))]
    {"cell_state" cs "next_node" "verify_consent"}))

(defn transition-to-consent-verified [state]
  (let [cs (cell-state state)
        consent-ref (get state "consent_ref" "")]
    ;; G13: no doorstep hand-off without a recorded (encrypted) recipient consent reference.
    (when-not (seq consent-ref)
      (throw (ex-info "G13 violation: hand-off requires a recipient consent reference" {:gate "G13"})))
    {"cell_state" (assoc cs "consent_ref" consent-ref "phase" phase-consent-verified)
     "next_node" "capture_proof"}))

(defn transition-to-proof-captured [state]
  (let [cs (cell-state state)
        kind (get state "proof_kind" (get cs "proof_kind"))]
    ;; G8: forbidden (cloud/biometric) proof kinds are a constitutional violation.
    (when (or (contains? forbidden-proof-kinds kind)
              (not (contains? admissible-proof-kinds kind)))
      (throw (ex-info (str "G8 violation: proof_kind " (pr-str kind) " not in "
                           (pr-str (vec admissible-proof-kinds)) " (no cloud/biometric, N5)") {:gate "G8"})))
    {"cell_state" (assoc cs "phase" phase-proof-captured "proof_kind" kind "on_device_only" true)
     "next_node" "seal_proof"}))

(defn transition-to-proof-sealed [state]
  (let [cs (cell-state state)
        recipient-sig (get state "recipient_sig" "")]
    ;; G12: the server must never sign; only the recipient/member signature seals the proof.
    (when (get state "server_signed" false)
      (throw (ex-info "G12 violation: server-side signing is prohibited (no-server-key)" {:gate "G12"})))
    (let [sealed (boolean (seq recipient-sig))
          cs (assoc cs
                    "recipient_sig" recipient-sig
                    "server_signed" false
                    "phase" phase-proof-sealed
                    "payload" {"handoff_proof"
                               {"jobId" (get cs "job_id")
                                "recipientDid" (get cs "recipient_did")
                                "consentRef" (get cs "consent_ref")
                                "proofKind" (get cs "proof_kind")
                                "onDeviceOnly" (get cs "on_device_only")   ; G8 — always true
                                "serverSigned" false                       ; G12 — always false
                                "sealed" sealed}})]                        ; true iff recipient signed
      {"cell_state" cs "next_node" "end"})))

(defn solve
  "R0 scaffold: .solve() raises until Council activation (ADR-2606042300 §Decision)."
  [_input-state]
  (throw (ex-info "todoke R0 scaffold: activate handoff_proof via Council ADR (post-2606042300 ratification)"
                  {:scaffold true})))
