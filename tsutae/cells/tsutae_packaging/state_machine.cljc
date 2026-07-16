(ns tsutae.cells.tsutae-packaging.state-machine
  "1:1 port of cells/tsutae_packaging/state_machine.py (ADR-2605261300 phase `packaging`). Minimal
  recyclable plastic-free packaging with a bilingual (JA+EN) iFixit-class repair manual + BoM
  disclosure + Charter Rider per device. Emits an internal packagedRecord.

  Constitutional guard: G5 — bilingual repair manual + SOPs + BoM disclosure + Charter Rider MUST
  ship with every device; a package missing the manual is rejected. PackagingState dataclass →
  string-keyed map under \"packaging_state\" (all fields present, nil for unset).")

(defn- s* [state]
  (merge {"materials" nil "manualGuard" nil "pack" nil} (get state "packaging_state" {})))

(defn transition-to-materials-verified [state]
  {"packaging_state" (assoc (s* state)
                            "materials" {"box" "molded-pulp" "plasticFree" true "recyclablePct" 100}
                            "phase" "materials_verified" "completionPct" 25)
   "next_node" "manual_guard"})

(defn transition-to-manual-included [state]
  (let [langs (get state "manualLangs" ["ja" "en"])
        bom (get state "bomDisclosed" true)
        rider (get state "charterRiderIncluded" true)
        langset (set langs)
        accept (boolean (and (contains? langset "ja") (contains? langset "en") bom rider))]
    {"packaging_state" (assoc (s* state)
                              "manualGuard" {"gate" "G5" "manualLangs" langs "bomDisclosed" bom
                                             "charterRiderIncluded" rider "ifixitScore" 9 "accept" accept
                                             "reason" (if accept "bilingual manual + BoM + Rider present"
                                                          "missing bilingual manual / BoM / Charter Rider (G5)")}
                              "phase" "manual_included" "completionPct" 55)
     "next_node" "pack"}))

(defn transition-to-packed [state]
  {"packaging_state" (assoc (s* state)
                            "pack" {"sealed" true "tamperEvident" true "robot" "robot:otete"}
                            "phase" "packed" "completionPct" 80)
   "next_node" "record"})

(defn transition-to-record-emitted [state]
  (let [s (assoc (s* state) "phase" "record_emitted" "completionPct" 100)]
    {"packaging_state" s
     "packaged_record" {"deviceId" (get s "deviceId")
                        "materials" (get s "materials")
                        "manualGuard" (get s "manualGuard")
                        "pack" (get s "pack")
                        "accept" (boolean (get (or (get s "manualGuard") {}) "accept"))
                        "recordedAt" "2026-05-26T14:00:00Z"}
     "next_node" "end"}))
