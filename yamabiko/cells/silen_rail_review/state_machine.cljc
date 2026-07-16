(ns yamabiko.cells.silen-rail-review.state-machine
  "1:1 port of cells/silen_rail_review/state_machine.py — ADR-2605252600 governance.

  Council 5-of-7 Safe attestation for new Wave / new trainset / new jurisdiction
  / G7 transition / gate amendment. Required before any L1 fabrication of new
  trainset class.")

(defn- review-state
  "ReviewState defaults merged with any existing \"review_state\" map (string keys).
   Input state values override defaults."
  [state]
  (let [existing (get state "review_state" {})
        review-subject-id (or (get existing "reviewSubjectId")
                              (get state "reviewSubjectId")
                              "REVIEW-001")]
    (merge {"phase" "init"
            "reviewSubjectId" review-subject-id
            "completionPct" 0
            "scope" nil
            "councilSafeAddress" nil
            "councilSignatures" nil
            "decision" nil
            "rationale" nil}
           existing)))

(defn transition-to-scope-declared
  "Declare review scope (baseline, new-wave, new-jurisdiction, etc.)."
  [state]
  (let [s (review-state state)]
    {"review_state" (assoc s
                           "scope" (get state "scope" "r0-scaffold-baseline")
                           "councilSafeAddress" "0xCouncilSafe5of7..."
                           "phase" "scope_declared"
                           "completionPct" 25)
     "next_node" "signatures"}))

(defn transition-to-signatures-collected
  "Collect Council 5-of-7 Safe signatures."
  [state]
  (let [s (review-state state)]
    {"review_state" (assoc s
                           "councilSignatures" [{"councilMemberDid" "did:web:etzhayyim.com:council-member-1"
                                                 "signature" "..."
                                                 "timestamp" "2026-05-27T16:00:00Z"}
                                                {"councilMemberDid" "did:web:etzhayyim.com:council-member-2"
                                                 "signature" "..."
                                                 "timestamp" "2026-05-27T16:00:05Z"}
                                                {"councilMemberDid" "did:web:etzhayyim.com:council-member-3"
                                                 "signature" "..."
                                                 "timestamp" "2026-05-27T16:00:10Z"}
                                                {"councilMemberDid" "did:web:etzhayyim.com:council-member-4"
                                                 "signature" "..."
                                                 "timestamp" "2026-05-27T16:00:15Z"}
                                                {"councilMemberDid" "did:web:etzhayyim.com:council-member-5"
                                                 "signature" "..."
                                                 "timestamp" "2026-05-27T16:00:20Z"}]
                           "phase" "signatures_collected"
                           "completionPct" 70)
     "next_node" "decision"}))

(defn transition-to-decision-recorded
  "Record Council decision (approve/reject with rationale)."
  [state]
  (let [s (review-state state)]
    {"review_state" (assoc s
                           "decision" "approve"
                           "rationale" "Wave 1 R0 scaffold baseline review — Constitutional gates G1..G14 + Non-goals N1..N12 declared per ADR-2605252600. 5 Council signatures collected. Approved."
                           "phase" "decision_recorded"
                           "completionPct" 90)
     "next_node" "record"}))

(defn transition-to-record-emitted
  "Emit final silenRailReview record."
  [state]
  (let [s (review-state state)]
    {"review_state" (assoc s
                           "phase" "record_emitted"
                           "completionPct" 100)
     "silen_rail_review" {"$type" "com.etzhayyim.yamabiko.silenRailReview"
                          "reviewSubjectId" (get s "reviewSubjectId")
                          "scope" (get s "scope")
                          "councilSafeAddress" (get s "councilSafeAddress")
                          "councilSignatures" (get s "councilSignatures")
                          "decision" (get s "decision")
                          "rationale" (get s "rationale")
                          "recordedAt" "2026-05-27T16:01:00Z"}
     "next_node" "end"}))
