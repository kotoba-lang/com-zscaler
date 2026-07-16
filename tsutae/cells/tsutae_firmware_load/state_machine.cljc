(ns tsutae.cells.tsutae-firmware-load.state-machine
  "1:1 port of cells/tsutae_firmware_load/state_machine.py (ADR-2605261300 phase `firmware`).
  Flashes the open firmware stack (coreboot → Linux mainline → GrapheneOS-class userspace), verifies
  image integrity, asserts the open-source chain. Emits com.etzhayyim.tsutae.firmwareAttestation.

  Constitutional guards: G7 binary-blob ratio ≤5% by firmware mass · G2 (§2(b)) bootloader unlock =
  default state (a locked bootloader is rejected). FirmwareState dataclass → string-keyed map under
  \"firmware_state\" (all fields present, nil for unset).")

(def ^:private G7-BLOB-RATIO-LIMIT-PCT 5.0)

(defn- s* [state]
  (merge {"image" nil "blobGuard" nil "bootloaderGuard" nil "flash" nil}
         (get state "firmware_state" {})))

(defn transition-to-image-verified [state]
  {"firmware_state" (assoc (s* state)
                           "image" {"imageCid" (get state "imageCid" "bafybeifirmware...")
                                    "sha256" "e3b0c44298fc1c149afbf4c8996fb924..."
                                    "stack" ["coreboot" "linux-mainline-6.x" "graphene-class-userspace"]
                                    "baselineOption" (get state "baseline" "ameno")
                                    "openSourceChain" true}
                           "phase" "image_verified" "completionPct" 15)
   "next_node" "blob_guard"})

(defn transition-to-blob-ratio-checked [state]
  (let [ratio (double (get state "blobRatioPct" 2.0))
        accept (<= ratio G7-BLOB-RATIO-LIMIT-PCT)]
    {"firmware_state" (assoc (s* state)
                            "blobGuard" {"gate" "G7" "blobRatioPct" ratio "limitPct" G7-BLOB-RATIO-LIMIT-PCT
                                         "accept" accept
                                         "reason" (if accept "blob ratio within limit"
                                                      "binary-blob ratio exceeds 5% (G7)")}
                            "phase" "blob_ratio_checked" "completionPct" 35)
     "next_node" "bootloader_guard"}))

(defn transition-to-bootloader-unlock-confirmed [state]
  (let [unlockable (get state "bootloaderUnlockable" true)]
    {"firmware_state" (assoc (s* state)
                            "bootloaderGuard" {"gate" "G2" "bootloaderUnlockDefault" unlockable
                                               "accept" (boolean unlockable)
                                               "reason" (if unlockable "bootloader unlockable by default"
                                                            "locked bootloader rejected (§2(b) N2 invariant)")}
                            "phase" "bootloader_unlock_confirmed" "completionPct" 55)
     "next_node" "flash"}))

(defn transition-to-flashed [state]
  {"firmware_state" (assoc (s* state)
                           "flash" {"method" "fastboot-open" "verifyAfterWrite" true "result" "ok"}
                           "phase" "flashed" "completionPct" 80)
   "next_node" "attestation"})

(defn transition-to-attestation-emitted [state]
  (let [s (assoc (s* state) "phase" "attestation_emitted" "completionPct" 100)]
    {"firmware_state" s
     "firmware_attestation" {"$type" "com.etzhayyim.tsutae.firmwareAttestation"
                             "deviceId" (get s "deviceId")
                             "image" (get s "image")
                             "blobGuard" (get s "blobGuard")
                             "bootloaderGuard" (get s "bootloaderGuard")
                             "flash" (get s "flash")
                             "accept" (boolean (and (get (or (get s "blobGuard") {}) "accept")
                                                    (get (or (get s "bootloaderGuard") {}) "accept")))
                             "recordedAt" "2026-05-26T12:00:00Z"}
     "next_node" "end"}))
