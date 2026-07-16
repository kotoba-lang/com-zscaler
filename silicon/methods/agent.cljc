(ns silicon.methods.agent
  "silicon 珪 — fab-orchestration agent (gate-enforcing). 1:1 port of py/agent.py. The kotoba-facing
  concerns complementing the per-process cells: append-only lot traceability (G8), the §2(a)(c)
  force-review gate (G1 — litho/implant require a clearing force-review, never auto-pass), and chip
  inalienability (G2 — chips are LEASED, never sold/transferred/burned). Pure compute; no platform
  key (G7); no LLM needed (Murakumo-only otherwise, ADR-2605215000).")

;; 8 fab process steps (lexicon knownValues)
(def PROCESS-STEPS ["litho" "deposition" "etch" "implant" "cmp" "metrology" "test" "packaging"])
;; steps with HIGH §2(a)(c) weapons/surveillance-diversion risk → force-review REQUIRED (G1)
(def ^:private FORCE-REVIEW-REQUIRED #{"litho" "implant"})
;; verdicts that permit a gated step to proceed
(def ^:private CLEARING-VERDICTS #{"approve" "approve-with-conditions"})

(defn force-review-gate
  "Decide whether a process step may run. litho/implant require a force-review with a clearing
  verdict; an unresolved/denied review blocks (never auto-passes, G1)."
  [process review]
  (cond
    (not (contains? FORCE-REVIEW-REQUIRED process))
    {"allowed" true "reason" "not a §2(a)(c)-gated step"}
    (not review)
    {"allowed" false "reason" (str process " requires a silenForceReview (G1)")}
    :else
    (let [verdict (get review "verdict")]
      (if (contains? CLEARING-VERDICTS verdict)
        {"allowed" true "reason" (str "force-review " verdict)}
        {"allowed" false "reason" (str "force-review verdict '" verdict "' does not clear (G1)")}))))

(defn record-process-step
  "Append one process-step attestation to a lot's history. Enforces the force-review gate (G1) and
  monotonic step indexing (G8 — never rewrites prior steps)."
  ([lot process equipment-did completed-at] (record-process-step lot process equipment-did completed-at nil "ok"))
  ([lot process equipment-did completed-at review] (record-process-step lot process equipment-did completed-at review "ok"))
  ([lot process equipment-did completed-at review outcome]
   (if-not (contains? (set PROCESS-STEPS) process)
     {"error" (str "unknown process '" process "'")}
     (let [gate (force-review-gate process review)]
       (if-not (get gate "allowed")
         {"error" (get gate "reason") "blocked" true}
         (let [history (vec (get lot "history" []))
               step-index (count history)
               step (cond-> {"stepIndex" step-index "process" process "equipmentDid" equipment-did
                             "outcome" outcome "completedAt" completed-at}
                      review (assoc "forceReviewUri" (get review "id" "")))
               state (cond (and (= process "packaging") (= outcome "ok")) "verified"
                           (contains? #{"scrapped" "quarantined"} outcome) outcome
                           :else (get lot "state" "in-fab"))]
           (merge lot {"history" (conj history step) "currentStepIndex" step-index "state" state})))))))

(defn lot-traceable
  "A lot is traceable iff its step indices form a gap-free monotonic 0..n chain (G8)."
  [lot]
  (let [idx (mapv #(get % "stepIndex") (get lot "history" []))]
    (= idx (vec (range (count idx))))))

(defn lease-chip
  "Lease a manufactured die to an SBT-holder. A chip is never owned/sold/transferred (land-trust-
  analogue inalienability, G2). Ship requires a force-review (G1)."
  [chip lessee-did force-review-uri]
  (if-not force-review-uri
    {"error" "ship/lease requires a force-review (G1)" "blocked" true}
    (merge chip {"leasedToDid" lessee-did "forceReviewUri" force-review-uri})))

(defn assert-no-transfer
  "Reject any sale/transfer/burn of silicon assets (G2). Only :lease is permitted."
  [action]
  (if (contains? #{"sell" "transfer" "burn" "set-owner" "gift"} action)
    {"allowed" false "reason" (str "'" action "' violates silicon inalienability (G2); only lease-to-SBT is permitted")}
    {"allowed" (= action "lease") "reason" (if (= action "lease") "lease permitted" (str "unknown action '" action "'"))}))
