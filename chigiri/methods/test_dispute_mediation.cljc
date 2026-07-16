;; chigiri 契 — unit tests for methods.dispute-mediation (chigiri's first real
;; computed-logic slice, iteration #9 of the etzhayyim design+implementation loop).
;; ALL fixtures below are entirely SYNTHETIC: fictional party names, fictional DIDs
;; (did:key:z6MkFAKE-* under fake jurisdiction code "zz1"), fictional mediator/outcome
;; CIDs. No real dispute, no real person, no real excommunication/legal record of any
;; kind appears anywhere in this file (this is inherently sensitive subject matter —
;; see chigiri's own G14/UPL invariant).
(ns chigiri.methods.test-dispute-mediation
  (:require [clojure.test :refer [deftest is run-tests]]
            [chigiri.methods.dispute-mediation :as dm]))

;; ── synthetic fixture builder — a baseline compliant "still mediating" record,
;;    overridden per test via `merge` ──────────────────────────────────────────────
(defn- record [overrides]
  (merge
   {"createdAt" "2026-07-01T00:00:00Z"
    "claimantDid" "did:key:z6MkFAKE-claimant-alice-zz1"
    "respondentDid" "did:key:z6MkFAKE-respondent-bob-zz1"
    "claimCategory" "vendor-contract"
    "claimSummaryEncryptedCid" "bafySYNTHETICclaimSummaryEnvelopeCidZZ1"
    "currentRound" 1
    "attestingDid" "did:key:z6MkFAKE-councilseat-carol-zz1"}
   overrides))

(defn- mediation-outcome [round status]
  {"round" round
   "mediatorDid" (str "did:key:z6MkFAKE-mediator-round" round "-zz1")
   "outcomeCid" (str "bafySYNTHETICoutcomeRound" round "CidZZ1")
   "outcomeStatus" status
   "completedAt" (str "2026-07-0" (+ round 1) "T00:00:00Z")})

;; ── rule 1: currentRound >= 1 before ANY arbitration channel may be invoked ────────

(deftest test-round-0-rejected-when-invoking-arbitration
  (let [r (record {"currentRound" 0
                    "arbitrationChannel" "internal-council-tribunal"
                    "escalateToArbitration" true
                    "mediationOutcomes" [(mediation-outcome 1 "escalate")]})]
    (is (= [:arbitration-requires-current-round-at-least-1]
           (dm/mediation-first-violations r)))
    (is (false? (dm/mediation-first-compliant? r)))))

(deftest test-round-1-accepted-when-invoking-arbitration
  (let [r (record {"currentRound" 1
                    "arbitrationChannel" "internal-council-tribunal"
                    "escalateToArbitration" true
                    "mediationOutcomes" [(mediation-outcome 1 "escalate")]})]
    (is (= [] (dm/mediation-first-violations r)))
    (is (true? (dm/mediation-first-compliant? r)))))

;; a nil/absent currentRound is treated the same as "not >= 1" when arbitration is invoked
(deftest test-missing-current-round-rejected-when-invoking-arbitration
  (let [r (dissoc (record {"arbitrationChannel" "external-jcaa-rules"
                            "escalateToArbitration" true
                            "mediationOutcomes" [(mediation-outcome 1 "escalate")]})
                   "currentRound")]
    (is (= [:arbitration-requires-current-round-at-least-1]
           (dm/mediation-first-violations r)))))

;; ── rule 2: mediationOutcomes[] must be populated before escalateToArbitration=true ──

(deftest test-escalate-with-no-outcomes-rejected
  (let [r (record {"currentRound" 1 "escalateToArbitration" true "mediationOutcomes" []})]
    (is (= [:escalation-requires-populated-mediation-outcomes]
           (dm/mediation-first-violations r)))))

(deftest test-escalate-with-absent-outcomes-key-rejected
  (let [r (record {"currentRound" 1 "escalateToArbitration" true})]
    (is (= [:escalation-requires-populated-mediation-outcomes]
           (dm/mediation-first-violations r)))))

;; ── rule 3 (lexicon-precise): escalateToArbitration=true requires an outcome entry
;;    whose outcomeStatus is specifically "escalate", not merely a non-empty log ──────

(deftest test-escalate-with-outcomes-but-none-escalate-status-rejected
  (let [r (record {"currentRound" 2
                    "escalateToArbitration" true
                    "mediationOutcomes" [(mediation-outcome 1 "deadlock")
                                         (mediation-outcome 2 "partial-resolve")]})]
    (is (= [:escalation-requires-escalate-outcome-status]
           (dm/mediation-first-violations r)))))

(deftest test-escalate-with-an-escalate-status-outcome-accepted
  (let [r (record {"currentRound" 2
                    "escalateToArbitration" true
                    "mediationOutcomes" [(mediation-outcome 1 "deadlock")
                                         (mediation-outcome 2 "escalate")]})]
    (is (= [] (dm/mediation-first-violations r)))
    (is (true? (dm/mediation-first-compliant? r)))))

;; ── ordinary (non-escalating) mediation-in-progress is always G10-compliant ─────────

(deftest test-ordinary-in-progress-mediation-is-compliant
  (let [r (record {"currentRound" 1})]
    (is (= [] (dm/mediation-first-violations r)))
    (is (true? (dm/mediation-first-compliant? r))))
  (let [r (record {"currentRound" 2
                    "mediationOutcomes" [(mediation-outcome 1 "partial-resolve")]})]
    (is (true? (dm/mediation-first-compliant? r)))))

;; explicit arbitrationChannel="n-a" (no channel selected) never triggers rule 1, even
;; at round 0 — "n-a" is the lexicon's own "not escalating" sentinel, not a channel
(deftest test-arbitration-channel-n-a-is-not-invoking-arbitration
  (let [r (record {"currentRound" 0 "arbitrationChannel" "n-a"})]
    (is (= [] (dm/mediation-first-violations r)))))

;; ── multiple simultaneous violations accumulate independently ──────────────────────

(deftest test-multiple-violations-accumulate
  (let [r (record {"currentRound" 0
                    "arbitrationChannel" "external-siac-rules"
                    "escalateToArbitration" true
                    "mediationOutcomes" []})]
    (is (= [:arbitration-requires-current-round-at-least-1
            :escalation-requires-populated-mediation-outcomes]
           (dm/mediation-first-violations r)))))

#?(:clj
   (when (= *file* (System/getProperty "babashka.file"))
     (let [{:keys [fail error]} (run-tests 'chigiri.methods.test-dispute-mediation)]
       (System/exit (if (zero? (+ fail error)) 0 1)))))
