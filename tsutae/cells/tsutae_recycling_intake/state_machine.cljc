(ns tsutae.cells.tsutae-recycling-intake.state-machine
  "1:1 port of cells/tsutae_recycling_intake/state_machine.py (ADR-2605261300 phase `eol`). EOL
  take-back: non-destructive dismantling (reverse of chassis_assembly, hand tools) → per-material
  sort → route Al chassis to kanayama + Li cells to a battery recycler → per-material mass balance.
  Emits com.etzhayyim.tsutae.recyclingCertificate; closes the G10 take-back loop.

  Constitutional guard: G10 — material recovery ≥80% by mass by R3; below target is FLAGGED
  (accept=false) so the loss is surfaced rather than hidden. RecyclingState dataclass → string-keyed
  map under \"recycling_state\" (all fields present, nil for unset).")

(def ^:private G10-RECOVERY-TARGET-PCT 80.0)

(defn- pyround [x n]
  (-> (java.math.BigDecimal. (double x))
      (.setScale (int n) java.math.RoundingMode/HALF_EVEN)
      .doubleValue))

(defn- s* [state]
  (merge {"dismantle" nil "sort" nil "recoveryGuard" nil} (get state "recycling_state" {})))

(defn transition-to-dismantled [state]
  {"recycling_state" (assoc (s* state)
                            "dismantle" {"method" "hand-tool-reverse" "modules" 9 "destructive" false}
                            "phase" "dismantled" "completionPct" 25)
   "next_node" "sort"})

(defn transition-to-materials-sorted [state]
  {"recycling_state" (assoc (s* state)
                            "sort" [{"material" "aluminum" "massG" 78.0 "route" "kanayama"}
                                    {"material" "li-cell" "massG" 42.0 "route" "battery-recycler"}
                                    {"material" "pcb-cu-au" "massG" 18.0 "route" "kanayama"}
                                    {"material" "glass" "massG" 24.0 "route" "cullet"}
                                    {"material" "polymer" "massG" 11.0 "route" "pyrolysis"}]
                            "phase" "materials_sorted" "completionPct" 55)
   "next_node" "route"})

(defn transition-to-kanayama-routed [state]
  (let [s (s* state)
        total (double (get state "totalMassG" 200.0))
        recovered (reduce + 0.0 (map #(get % "massG") (get s "sort")))
        pct (if (zero? total) 0.0 (pyround (* 100.0 (/ recovered total)) 1))
        accept (>= pct G10-RECOVERY-TARGET-PCT)]
    {"recycling_state" (assoc s
                             "recoveryGuard" {"gate" "G10" "recoveredMassPct" pct
                                              "targetPct" G10-RECOVERY-TARGET-PCT "accept" accept
                                              "reason" (if accept "take-back recovery meets target"
                                                           (str "recovery " pct "% below " G10-RECOVERY-TARGET-PCT
                                                                "% target (flagged, not hidden)"))}
                             "phase" "kanayama_routed" "completionPct" 80)
     "next_node" "certificate"}))

(defn transition-to-certificate-emitted [state]
  (let [s (assoc (s* state) "phase" "certificate_emitted" "completionPct" 100)]
    {"recycling_state" s
     "recycling_certificate" {"$type" "com.etzhayyim.tsutae.recyclingCertificate"
                              "serial" (get s "serial")
                              "dismantle" (get s "dismantle")
                              "materialBalance" (get s "sort")
                              "recoveryGuard" (get s "recoveryGuard")
                              "accept" (boolean (get (or (get s "recoveryGuard") {}) "accept"))
                              "recordedAt" "2026-05-26T16:00:00Z"}
     "next_node" "end"}))
