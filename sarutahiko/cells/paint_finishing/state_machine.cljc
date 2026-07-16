(ns sarutahiko.cells.paint-finishing.state-machine
  "Paint-finishing state machine — ADR-2605252500 L5a. 1:1 cljc port of
  `cells/paint_finishing/state_machine.py`. KTL primer + base + clear (water-based,
  VOC <100 g/L, G8). Layers accumulate across transitions. String keys mirror the
  Python __dict__.")

(def phases
  {:init "init" :pretreatment-done "pretreatment_done" :ktl-primer-applied "ktl_primer_applied"
   :base-coat-applied "base_coat_applied" :clear-coat-applied "clear_coat_applied"
   :cured "cured" :attestation-emitted "attestation_emitted"})

(defn init [state]
  {"paint_state" {"phase" (:init phases)
                  "chassisId" (get state "chassisId" "SARUTAHIKO-CHASSIS-0001")
                  "completionPct" 0}})

(defn transition-to-pretreatment-done [state]
  (let [s (-> (get state "paint_state" {})
              (assoc "pretreatmentResult" {"degreased" true "phosphatedNm" 1.2 "rinseRounds" 3}
                     "phase" (:pretreatment-done phases) "completionPct" 15))]
    {"paint_state" s "next_node" "ktl"}))

(defn transition-to-ktl-primer-applied [state]
  (let [s (-> (get state "paint_state" {})
              (assoc "layers" [{"layer" "ktl-primer" "thicknessUm" 22 "filmCid" "bafkreiktl..."}]
                     "phase" (:ktl-primer-applied phases) "completionPct" 35))]
    {"paint_state" s "next_node" "base"}))

(defn transition-to-base-coat-applied [state]
  (let [s0 (get state "paint_state" {})
        s (assoc s0 "layers" (conj (vec (get s0 "layers" []))
                                   {"layer" "base-coat" "thicknessUm" 18 "color" "OEM-default-grey"})
                 "phase" (:base-coat-applied phases) "completionPct" 55)]
    {"paint_state" s "next_node" "clear"}))

(defn transition-to-clear-coat-applied [state]
  (let [s0 (get state "paint_state" {})
        s (assoc s0 "layers" (conj (vec (get s0 "layers" [])) {"layer" "clear-coat" "thicknessUm" 40})
                 "vocGPerL" 92 "phase" (:clear-coat-applied phases) "completionPct" 75)]
    {"paint_state" s "next_node" "cure"}))

(defn transition-to-cured [state]
  (let [s (-> (get state "paint_state" {})
              (assoc "cureRecord" {"tempC" 140 "durationMinutes" 30 "tunnelType" "IR + convection"}
                     "phase" (:cured phases) "completionPct" 90))]
    {"paint_state" s "next_node" "attestation"}))

(defn transition-to-attestation-emitted [state]
  (let [s (-> (get state "paint_state" {})
              (assoc "phase" (:attestation-emitted phases) "completionPct" 100))
        voc (or (get s "vocGPerL") 999)
        record {"$type" "com.etzhayyim.sarutahiko.paintAttestation"
                "chassisId" (get s "chassisId")
                "pretreatmentResult" (get s "pretreatmentResult")
                "layers" (get s "layers")
                "vocGPerL" (get s "vocGPerL")
                "vocLimitGPerL" 100
                "g8Accept" (< voc 100)
                "cureRecord" (get s "cureRecord")
                "recordedAt" "2026-05-26T15:00:00Z"}]
    {"paint_state" s "paint_attestation" record "next_node" "end"}))

(defn run-chain [input-state]
  (reduce (fn [s f] (f s))
          (merge input-state (init input-state))
          [transition-to-pretreatment-done transition-to-ktl-primer-applied
           transition-to-base-coat-applied transition-to-clear-coat-applied
           transition-to-cured transition-to-attestation-emitted]))
