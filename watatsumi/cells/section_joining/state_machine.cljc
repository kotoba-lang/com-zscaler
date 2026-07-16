(ns watatsumi.cells.section-joining.state-machine
  "Section-joining state machine — ADR-2605252200 L5a. 1:1 cljc port of
  `cells/section_joining/state_machine.py`. The terminal weld that closes the
  pressure boundary: align → multi-pass TIG → 100% RT → PWHT → ≥2-robot
  attestation. String keys mirror the Python __dict__.")

(def phases
  {:init "init" :sections-aligned "sections_aligned"
   :multipass-tig-complete "multipass_tig_complete" :rt-100pct-passed "rt_100pct_passed"
   :pwht-complete "pwht_complete" :attestation-emitted "attestation_emitted"})

(defn init [state]
  {"section_joining_state"
   {"phase" (:init phases)
    "craftId" (get state "craftId" "WATATSUMI-RESEARCH-0001")
    "completionPct" 0}})

(defn transition-to-sections-aligned [state]
  (let [sj (-> (get state "section_joining_state" {})
               (assoc "phase" (:sections-aligned phases)
                      "sectionPairs" [{"sectionA" 0 "sectionB" 1 "alignmentToleranceMm" 0.8}
                                      {"sectionA" 1 "sectionB" 2 "alignmentToleranceMm" 0.6}]
                      "completionPct" 20))]
    {"section_joining_state" sj "next_node" "tig"}))

(defn transition-to-multipass-tig-complete [state]
  (let [sj (-> (get state "section_joining_state" {})
               (assoc "phase" (:multipass-tig-complete phases)
                      "multiPassDetails" [{"sectionPair" "0-1" "passes" 6 "videoCid" "bafkreitig01..."}
                                          {"sectionPair" "1-2" "passes" 6 "videoCid" "bafkreitig12..."}]
                      "completionPct" 55))]
    {"section_joining_state" sj "next_node" "rt"}))

(defn transition-to-rt-100pct-passed [state]
  (let [sj (-> (get state "section_joining_state" {})
               (assoc "phase" (:rt-100pct-passed phases)
                      "rtResults" [{"sectionPair" "0-1" "rtFilmCid" "bafkreirt01..." "coveragePct" 100 "indications" []}
                                   {"sectionPair" "1-2" "rtFilmCid" "bafkreirt12..." "coveragePct" 100 "indications" []}]
                      "completionPct" 75))]
    {"section_joining_state" sj "next_node" "pwht"}))

(defn transition-to-pwht-complete [state]
  (let [sj (-> (get state "section_joining_state" {})
               (assoc "phase" (:pwht-complete phases)
                      "pwhtRecord" {"soakTemperatureC" 620 "soakDurationMinutes" 240
                                    "rampUpCPerHour" 110 "rampDownCPerHour" 80
                                    "thermocoupleLogCid" "bafkreipwhtlog..."}
                      "completionPct" 90))]
    {"section_joining_state" sj "next_node" "attestation"}))

(def ^:private robot-sigs
  [{"robotDid" "did:web:etzhayyim.com:ama-unit-1" "role" "weld_lead"
    "timestamp" "2026-05-26T15:00:00Z" "signature" "..."}
   {"robotDid" "did:web:etzhayyim.com:tako-unit-1" "role" "interior_witness"
    "timestamp" "2026-05-26T15:00:05Z" "signature" "..."}])

(defn transition-to-attestation-emitted [state]
  (let [sj (-> (get state "section_joining_state" {})
               (assoc "robotSignatures" robot-sigs
                      "phase" (:attestation-emitted phases) "completionPct" 100))
        record {"$type" "com.etzhayyim.watatsumi.sectionJoiningAttestation"
                "craftId" (get sj "craftId")
                "sectionPairs" (get sj "sectionPairs")
                "multiPassDetails" (get sj "multiPassDetails")
                "rtResults" (get sj "rtResults")
                "pwhtRecord" (get sj "pwhtRecord")
                "attestingRobots" robot-sigs
                "recordedAt" "2026-05-26T15:00:10Z"}]
    {"section_joining_state" sj "section_joining_attestation" record "next_node" "end"}))

(defn run-chain [input-state]
  (reduce (fn [s f] (f s))
          (merge input-state (init input-state))
          [transition-to-sections-aligned transition-to-multipass-tig-complete
           transition-to-rt-100pct-passed transition-to-pwht-complete
           transition-to-attestation-emitted]))
