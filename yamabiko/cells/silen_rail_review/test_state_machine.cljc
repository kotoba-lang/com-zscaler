(ns yamabiko.cells.silen-rail-review.test-state-machine
  "Tests for yamabiko silen_rail_review state machine (py→cljc port).
   Governance layer: Council 5-of-7 Safe attestation for new wave/trainset/jurisdiction.
   Required before any L1 fabrication."
  (:require [clojure.test :refer [deftest is testing]]
            [yamabiko.cells.silen-rail-review.state-machine :as sm]))

(deftest test-review-happy-path
  (testing "Full silenRailReview governance happy path"
    (let [init-state {"reviewSubjectId" "REVIEW-WAVE-1-BASELINE" "scope" "r0-scaffold-baseline"}
          s1 (sm/transition-to-scope-declared init-state)
          s2 (sm/transition-to-signatures-collected s1)
          s3 (sm/transition-to-decision-recorded s2)
          s4 (sm/transition-to-record-emitted s3)
          record (get s4 "silen_rail_review")]
      ;; Phase progression
      (is (= "scope_declared" (get-in s1 ["review_state" "phase"])))
      (is (= 25 (get-in s1 ["review_state" "completionPct"])))
      (is (= "signatures_collected" (get-in s2 ["review_state" "phase"])))
      (is (= 70 (get-in s2 ["review_state" "completionPct"])))
      (is (= "decision_recorded" (get-in s3 ["review_state" "phase"])))
      (is (= 90 (get-in s3 ["review_state" "completionPct"])))
      (is (= "record_emitted" (get-in s4 ["review_state" "phase"])))
      (is (= 100 (get-in s4 ["review_state" "completionPct"])))

      ;; Record structure
      (is (= "com.etzhayyim.yamabiko.silenRailReview" (get record "$type")))
      (is (= "REVIEW-WAVE-1-BASELINE" (get record "reviewSubjectId")))
      (is (= "r0-scaffold-baseline" (get record "scope")))
      (is (some? (get record "councilSafeAddress")))
      (is (some? (get record "councilSignatures")))
      (is (some? (get record "decision")))
      (is (some? (get record "rationale"))))))

(deftest test-review-scope-declared
  (testing "Review scope can be declared (r0-scaffold-baseline, new-wave, etc.)"
    (let [s (sm/transition-to-scope-declared {"scope" "new-trainset-class"})
          scope (get-in s ["review_state" "scope"])]
      (is (= "new-trainset-class" scope)))))

(deftest test-review-council-5of7-signatures
  (testing "Review collects exactly 5 Council member signatures (5-of-7 Safe)"
    (let [s (sm/transition-to-signatures-collected {})
          sigs (get-in s ["review_state" "councilSignatures"])]
      (is (= 5 (count sigs)))
      (is (every? #(and (contains? % "councilMemberDid")
                        (contains? % "signature")
                        (contains? % "timestamp"))
                  sigs)))))

(deftest test-review-decision-approve
  (testing "Review decision is approve with rationale"
    (let [s (sm/transition-to-decision-recorded {})
          decision (get-in s ["review_state" "decision"])
          rationale (get-in s ["review_state" "rationale"])]
      (is (= "approve" decision))
      (is (clojure.string/includes? rationale "ADR-2605252600"))
      (is (clojure.string/includes? rationale "Council")))))

(deftest test-review-rationale-cites-gates-nongoals
  (testing "Rationale cites constitutional gates G1..G14 + non-goals N1..N12"
    (let [s (sm/transition-to-decision-recorded {})
          rationale (get-in s ["review_state" "rationale"])]
      (is (clojure.string/includes? rationale "G1"))
      (is (clojure.string/includes? rationale "G14"))
      (is (clojure.string/includes? rationale "N1"))
      (is (clojure.string/includes? rationale "N12")))))

(deftest test-review-council-safe-address
  (testing "Review has Council 5-of-7 Safe address"
    (let [s (sm/transition-to-scope-declared {})
          safe (get-in s ["review_state" "councilSafeAddress"])]
      (is (some? safe))
      (is (clojure.string/includes? safe "CouncilSafe")))))

(deftest test-review-record-structure
  (testing "Review record has all required fields for attestation"
    (let [s (sm/transition-to-record-emitted {})
          record (get s "silen_rail_review")]
      (is (some? (get record "recordedAt")))
      (is (clojure.string/starts-with? (get record "recordedAt") "2026")))))
