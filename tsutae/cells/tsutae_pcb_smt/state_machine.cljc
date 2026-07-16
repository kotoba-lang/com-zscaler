(ns tsutae.cells.tsutae-pcb-smt.state-machine
  "1:1 port of cells/tsutae_pcb_smt/state_machine.py (ADR-2605261300 phase `smt`). Surface-mount
  assembly of the mainboard: component sourcing → G9 open-SoC guard → solder-paste + pick-and-place
  → AOI + solder X-ray → pcbAttestation. Emits com.etzhayyim.tsutae.pcbAttestation.

  Constitutional guard: G9 (§2(b) anti-IP-locking) — open RISC-V SoC mandatory; Snapdragon / Apple
  A / closed Helio / Exynos / Dimensity rejected by construction (N1). PcbState dataclass →
  string-keyed map under \"pcb_state\" (all fields present, nil for unset, mirroring __dict__)."
  (:require [clojure.string :as str]))

(def open-soc-allowlist
  ["StarFive-JH7110" "SiFive-HiFive-Unmatched" "Allwinner-D1" "iwakura"])
(def proprietary-soc-denylist
  ["Snapdragon" "Apple-A" "Exynos" "Helio" "Dimensity"])

(defn- is-open-soc [soc]
  (if (some #(str/starts-with? soc %) proprietary-soc-denylist)
    false
    (boolean (some #(str/starts-with? soc %) open-soc-allowlist))))

(defn- s* [state]
  (merge {"components" nil "socGuard" nil "placements" nil "aoi" nil} (get state "pcb_state" {})))

(defn transition-to-components-sourced [state]
  (let [soc (get state "soc" "StarFive-JH7110")]
    {"pcb_state" (assoc (s* state)
                        "components" [{"ref" "U1" "kind" "soc" "part" soc "supplierDid" "did:web:etzhayyim.com:silicon"}
                                      {"ref" "U2" "kind" "pmic" "part" "open-pmic-r0" "supplierDid" "did:web:etzhayyim.com:silicon"}
                                      {"ref" "U3" "kind" "lpddr" "part" "LPDDR4X-4GB" "supplierDid" "did:web:etzhayyim.com:silicon"}
                                      {"ref" "C1..C220" "kind" "passive" "part" "MLCC+R+L" "supplierDid" "representative"}]
                        "phase" "components_sourced" "completionPct" 15)
     "next_node" "soc_guard"}))

(defn transition-to-soc-guard-checked [state]
  (let [soc (get state "soc" "StarFive-JH7110")
        accept (is-open-soc soc)]
    {"pcb_state" (assoc (s* state)
                        "socGuard" {"gate" "G9" "soc" soc "openSoc" accept "accept" accept
                                    "reason" (if accept "open RISC-V SoC verified"
                                                 "proprietary/closed SoC rejected (§2(b) N1 invariant)")}
                        "phase" "soc_guard_checked" "completionPct" 30)
     "next_node" "place"}))

(defn transition-to-smt-placed [state]
  {"pcb_state" (assoc (s* state)
                      "placements" [{"stage" "solder-paste-stencil" "robot" "robot:tedama"}
                                    {"stage" "pick-and-place" "robot" "robot:tedama" "componentCount" 224}
                                    {"stage" "reflow" "profileCid" "bafkreireflow..."}]
                      "phase" "smt_placed" "completionPct" 60)
   "next_node" "aoi"})

(defn transition-to-aoi-passed [state]
  {"pcb_state" (assoc (s* state)
                      "aoi" {"robot" "robot:mimi" "opticalDefects" 0 "xrayVoidPct" 1.8
                             "specVoidLimitPct" 25.0 "accept" true}
                      "phase" "aoi_passed" "completionPct" 85)
   "next_node" "attestation"})

(defn transition-to-attestation-emitted [state]
  (let [s (assoc (s* state) "phase" "attestation_emitted" "completionPct" 100)]
    {"pcb_state" s
     "pcb_attestation" {"$type" "com.etzhayyim.tsutae.pcbAttestation"
                        "boardId" (get s "boardId")
                        "components" (get s "components")
                        "socGuard" (get s "socGuard")
                        "placements" (get s "placements")
                        "aoi" (get s "aoi")
                        "accept" (boolean (and (get (or (get s "socGuard") {}) "accept")
                                               (get (or (get s "aoi") {}) "accept")))
                        "recordedAt" "2026-05-26T09:00:00Z"}
     "next_node" "end"}))
