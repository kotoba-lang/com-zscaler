(ns tatekata.methods.test-agent
  "tatekata 建方 — agent gate tests (offline, no kotoba host, no network, no LLM).

  ADR-2605250715. 1:1 port of py/test_agent.py. Expected values copied VERBATIM
  from the Python test — never altered to pass."
  (:require [clojure.test :refer [deftest is]]
            [tatekata.methods.agent :as agent]))

;; ── G11 KPI caps ────────────────────────────────────────────────────

(deftest test-kpi-stories-cap
  ;; agent.kpi_caps_ok(3, 5000)["ok"] is False
  (is (false? (:ok (agent/kpi-caps-ok 3 5000)))))

(deftest test-kpi-footprint-cap
  ;; agent.kpi_caps_ok(2, 6000)["ok"] is False
  (is (false? (:ok (agent/kpi-caps-ok 2 6000)))))

(deftest test-kpi-within-caps
  ;; agent.kpi_caps_ok(2, 5000)["ok"] is True
  (is (true? (:ok (agent/kpi-caps-ok 2 5000)))))

;; ── G3 witness quorum ────────────────────────────────────────────────

(deftest test-witness-quorum-insufficient
  ;; agent.witness_quorum_ok(["did:web:giemon"])["ok"] is False
  (is (false? (:ok (agent/witness-quorum-ok ["did:web:giemon"])))))

(deftest test-witness-quorum-sufficient
  ;; dids = ["did:web:giemon.kuni-umi.etzhayyim.com",
  ;;         "did:web:mimi.kuni-umi.etzhayyim.com"]
  ;; agent.witness_quorum_ok(dids)["ok"] is True
  (let [dids ["did:web:giemon.kuni-umi.etzhayyim.com"
               "did:web:mimi.kuni-umi.etzhayyim.com"]]
    (is (true? (:ok (agent/witness-quorum-ok dids))))))

;; ── G17 phase advancement ────────────────────────────────────────────

(deftest test-advance-phase-foundation
  ;; result["state"] == "structural" and result["blocked"] is None
  (let [result (agent/advance-phase "foundation")]
    (is (= "structural" (:state result)))
    (is (nil? (:blocked result)))))

(deftest test-advance-phase-structural
  ;; result["state"] == "mep" and result["blocked"] is None
  (let [result (agent/advance-phase "structural")]
    (is (= "mep" (:state result)))
    (is (nil? (:blocked result)))))

(deftest test-advance-phase-terminal
  ;; result["state"] == "commissioning" and result["blocked"] is not None
  (let [result (agent/advance-phase "commissioning")]
    (is (= "commissioning" (:state result)))
    (is (some? (:blocked result)))))

;; ── phase handlers ───────────────────────────────────────────────────

(deftest test-foundation-excavation
  ;; agent.handle_foundation_excavation({"spec": "site grid excavation"})["plan"]["phase"] == "foundation"
  (let [result (agent/handle-foundation-excavation {"spec" "site grid excavation"})]
    (is (= "foundation" (:phase (:plan result))))))

(deftest test-structural-assembly
  ;; agent.handle_structural_assembly({"bim_ref": "freeCAD-model-ref"})["plan"]["phase"] == "structural"
  (let [result (agent/handle-structural-assembly {"bim_ref" "freeCAD-model-ref"})]
    (is (= "structural" (:phase (:plan result))))))

(deftest test-mep-installation
  ;; agent.handle_mep_installation({"mep_design": "hvac-electrical-plumbing"})["plan"]["phase"] == "mep"
  (let [result (agent/handle-mep-installation {"mep_design" "hvac-electrical-plumbing"})]
    (is (= "mep" (:phase (:plan result))))))

(deftest test-finishing-handoff
  ;; agent.handle_finishing_handoff({"phase_input": "from-mep-complete"})["plan"]["phase"] == "finishing"
  (let [result (agent/handle-finishing-handoff {"phase_input" "from-mep-complete"})]
    (is (= "finishing" (:phase (:plan result))))))

(deftest test-commissioning
  ;; agent.handle_commissioning({"phase_input": "from-finishing-complete"})["plan"]["phase"] == "commissioning"
  (let [result (agent/handle-commissioning {"phase_input" "from-finishing-complete"})]
    (is (= "commissioning" (:phase (:plan result))))))

;; ── progress record ───────────────────────────────────────────────────

(deftest test-progress-witness-quorum-fail
  ;; result.get("blocked") is True
  (let [result (agent/record-phase-progress "foundation" "did:project" ["did:giemon"])]
    (is (true? (:blocked result)))))

(deftest test-progress-witness-quorum-pass
  ;; result.get(":progress/phase") == "structural"
  (let [dids ["did:web:giemon.kuni-umi.etzhayyim.com"
               "did:web:mimi.kuni-umi.etzhayyim.com"]
        result (agent/record-phase-progress "structural" "did:project" dids
                                            "QmPhotos" "QmDepths")]
    (is (= "structural" (get result ":progress/phase")))))

;; ── G15/G16 settlement ───────────────────────────────────────────────

(deftest test-settlement-tithe-split
  ;; s["titheMinor"] == 5_000_000 and s["state"] == "intent" and s["rail"] == "usdc-base-l2"
  (let [s (agent/build-settlement-intent 50000000)]
    (is (= 5000000 (:titheMinor s)))
    (is (= "intent" (:state s)))
    (is (= "usdc-base-l2" (:rail s)))))

(deftest test-settlement-executed-with-sig
  ;; s["state"] == "executed"
  (let [s (agent/build-settlement-intent 10000000 "0xsig")]
    (is (= "executed" (:state s)))))
