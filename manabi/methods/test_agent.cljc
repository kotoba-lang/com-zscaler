(ns manabi.methods.test-agent
  "manabi 学び — agent cell tests (no kotoba host, no network, no LLM).

  ADR-2605261045 Phase 4. Faithful clj/cljc port of py/test_agent.py.
  Exercises the 5 handlers + settlement + gates with injected functions so the
  suite runs offline (Murakumo-only invariant untouched; G5).

  Expected values copied VERBATIM from test_agent.py."
  (:require [clojure.test :refer [deftest is testing run-tests]]
            [manabi.methods.agent :as agent]))

;; ── tests (1:1 port of test_agent.py) ────────────────────────────────────────

(deftest test-literacy-advances-stage
  (testing "literacy advances from intro to phonemic-awareness"
    (let [out (agent/handle-literacy {"current_stage" "intro" "completion_fraction" 0.0})]
      (is (= (get out "current_stage") "phonemic-awareness"))
      (is (> (get out "completion_fraction") 0.0)))))

(deftest test-numeracy-advances-stage
  (testing "numeracy advances from counting to place-value"
    (let [out (agent/handle-numeracy {"current_stage" "counting" "completion_fraction" 0.0})]
      (is (= (get out "current_stage") "place-value"))
      (is (> (get out "completion_fraction") 0.0)))))

(deftest test-civics-charter-levels-understanding
  (testing "civics_charter promotes understanding level"
    (let [out (agent/handle-civics-charter {"learner_did" "did:web:test"
                                            "charter_understanding_level" "foundational"})]
      (is (= (get out "charter_understanding_level") "intermediate")))))

(deftest test-civics-charter-anti-indoctrination
  (testing "civics_charter enforces comparative-religion (G12, G14)"
    (let [out (agent/handle-civics-charter {"learner_did" "did:web:test"
                                            "charter_understanding_level" "foundational"})]
      (is (contains? out "timestamp"))
      (is (not= (get out "charter_understanding_level") "foundational")))))

(deftest test-vocational-creates-skill-attestation
  (testing "vocational creates replayable skill attestation (anti-credentialism G7)"
    (let [out (agent/handle-vocational {"learner_did" "did:web:test"
                                        "domain" "agriculture"})]
      (is (contains? out "demonstration_cid"))
      (is (= (get out "skill_domain") "agriculture")))))

(deftest test-lifelong-inquiry-learner-led
  (testing "lifelong_inquiry is learner-led (G13, G11)"
    (let [out (agent/handle-lifelong-inquiry {"learner_did" "did:web:test"
                                              "inquiry_topic" ""
                                              "completion_fraction" 0.0})]
      (is (contains? out "inquiry_topic"))
      (is (pos? (count (get out "inquiry_topic")))))))

(deftest test-settlement-tithe-split
  (testing "10% tithe split + stops at intent (G7)"
    ;; Verbatim from test_agent.py:
    ;;   s["tithe_minor"] == 100000
    ;;   s["factory_payout_minor"] == 900000
    ;;   s["state"] == "intent"
    ;;   s["rail"] == "usdc-base-l2"
    (let [s (agent/build-settlement-intent 1000000)]
      (is (= (get s "tithe_minor") 100000))
      (is (= (get s "factory_payout_minor") 900000))
      (is (= (get s "state") "intent"))
      (is (= (get s "rail") "usdc-base-l2")))))

(deftest test-settlement-executed-with-member-sig
  (testing "settlement executes only with member signature (G15)"
    ;; Verbatim from test_agent.py:
    ;;   s["state"] == "executed"
    ;;   s["buyer_sig_ref"] == "0xmembersig"
    (let [s (agent/build-settlement-intent 1000000 "0xmembersig")]
      (is (= (get s "state") "executed"))
      (is (= (get s "buyer_sig_ref") "0xmembersig")))))

(deftest test-domain-gate-offline
  (testing "domain gate enforces (G1...G14)"
    (let [mock-gate (fn [did _gate-name]
                      {"allowed" (not= did "blocked") "reason" "test gate"})
          out (agent/check-domain-gate "allowed_did" "G1" mock-gate)]
      (is (= (get out "allowed") true)))))

(deftest test-domain-gate-blocks
  (testing "domain gate blocks when violated"
    (let [mock-gate (fn [did _gate-name]
                      {"allowed" (not= did "blocked") "reason" "test gate"})
          out (agent/check-domain-gate "blocked" "G1" mock-gate)]
      (is (= (get out "allowed") false)))))

(deftest test-minor-aggregate-only-enforcement
  (testing "minor learner data passes G6 validation (no forbidden keys)"
    ;; Verbatim from test_agent.py: result is True
    (let [data   {"sessionDuration" 45 "completionFraction" 0.5}
          result (agent/validate-minor-aggregate-only "minor" data)]
      (is (= result true)))))

(deftest test-minor-rejects-performance-data
  (testing "minor learner rejected if performance data present (G6)"
    ;; Verbatim from test_agent.py: result is False
    (let [data   {"sessionDuration" 45 "performancePercentile" 85}
          result (agent/validate-minor-aggregate-only "minor" data)]
      (is (= result false)))))
