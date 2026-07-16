(ns tsutae.cells.tsutae-chassis-assembly.state-machine
  "1:1 port of cells/tsutae_chassis_assembly/state_machine.py (ADR-2605261300 phase `chassis`).
  Integrates the PCB into the igata HPDC Al chassis with battery + speaker + camera + USB-C +
  (removable) cellular + mic — all screw-fastened. Emits com.etzhayyim.tsutae.chassisAttestation.

  Constitutional guards: G6 (§2(c) anti-surveillance) — microphone HARDWARE kill switch mandatory;
  G3 (§2(e) repair-rightful) — modular screw-fastened, adhesive ≤5 g, every required slot
  independently replaceable, no parts-pairing. ChassisState dataclass → string-keyed map under
  \"chassis_state\" (all fields present, nil for unset).")

(def ^:private G3-ADHESIVE-LIMIT-G 5.0)
(def ^:private g3-required-replaceable
  ["battery" "display" "camera" "usb_c" "speaker" "sim" "cellular" "microphone"])

(defn- s* [state]
  (merge {"components" nil "micGuard" nil "repairGuard" nil "fasteners" nil}
         (get state "chassis_state" {})))

(defn transition-to-components-staged [state]
  {"chassis_state" (assoc (s* state)
                          "components" [{"slot" "chassis" "part" "igata-Al-HPDC" "did" "did:web:etzhayyim.com:igata"}
                                        {"slot" "battery" "part" "LFP-3000mAh" "did" "did:web:etzhayyim.com:hikari" "replaceable" true}
                                        {"slot" "display" "part" "LCD-6.1in" "did" "representative" "replaceable" true}
                                        {"slot" "camera" "part" "open-isp-12MP" "did" "representative" "replaceable" true}
                                        {"slot" "usb_c" "part" "USB-C-PD" "did" "representative" "replaceable" true}
                                        {"slot" "speaker" "part" "8ohm-spk" "did" "representative" "replaceable" true}
                                        {"slot" "sim" "part" "nano-SIM-tray" "did" "representative" "replaceable" true}
                                        {"slot" "cellular" "part" "LTE-module-removable" "did" "representative" "replaceable" true}
                                        {"slot" "microphone" "part" "MEMS-mic" "did" "representative" "replaceable" true}]
                          "phase" "components_staged" "completionPct" 15)
   "next_node" "mic_guard"})

(defn transition-to-mic-killswitch-verified [state]
  (let [present (get state "micKillSwitch" true)]
    {"chassis_state" (assoc (s* state)
                            "micGuard" {"gate" "G6" "micHardwareKillSwitch" present "cellularRemovable" true
                                        "accept" (boolean present)
                                        "reason" (if present "hardware mic kill switch present"
                                                     "missing hardware mic kill switch (§2(c) N4 invariant)")}
                            "phase" "mic_killswitch_verified" "completionPct" 35)
     "next_node" "repair_guard"}))

(defn transition-to-repair-modularity-checked [state]
  (let [s (s* state)
        adhesive-g (double (get state "adhesiveGrams" 0.0))
        parts-pairing (boolean (get state "partsPairing" false))
        replaceable (set (for [c (get s "components") :when (get c "replaceable")] (get c "slot")))
        all-replaceable (every? replaceable g3-required-replaceable)
        accept (and (<= adhesive-g G3-ADHESIVE-LIMIT-G) all-replaceable (not parts-pairing))]
    {"chassis_state" (assoc s
                            "repairGuard" {"gate" "G3" "adhesiveGrams" adhesive-g
                                           "adhesiveLimitG" G3-ADHESIVE-LIMIT-G
                                           "allModulesReplaceable" all-replaceable
                                           "partsPairing" parts-pairing "accept" accept
                                           "reason" (if accept "screw-fastened modular, no parts-pairing"
                                                        "excess adhesive / non-modular / parts-pairing rejected (§2(e) N7)")}
                            "phase" "repair_modularity_checked" "completionPct" 55)
     "next_node" "assemble"}))

(defn transition-to-chassis-assembled [state]
  {"chassis_state" (assoc (s* state)
                          "fasteners" {"type" "torx-T5" "count" 9 "pentalobe" false "robot" "robot:otete"}
                          "phase" "chassis_assembled" "completionPct" 80)
   "next_node" "attestation"})

(defn transition-to-attestation-emitted [state]
  (let [s (assoc (s* state) "phase" "attestation_emitted" "completionPct" 100)]
    {"chassis_state" s
     "chassis_attestation" {"$type" "com.etzhayyim.tsutae.chassisAttestation"
                            "chassisId" (get s "chassisId")
                            "components" (get s "components")
                            "micGuard" (get s "micGuard")
                            "repairGuard" (get s "repairGuard")
                            "fasteners" (get s "fasteners")
                            "accept" (boolean (and (get (or (get s "micGuard") {}) "accept")
                                                   (get (or (get s "repairGuard") {}) "accept")))
                            "recordedAt" "2026-05-26T10:00:00Z"}
     "next_node" "end"}))
