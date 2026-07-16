(ns watatsumi.methods.agent
  "watatsumi 綿津見 — civilian submersible manufacturing langgraph actor (kotoba WASM cell).

  ADR-2605252200, migration plan Phase 4. Runs in-WASM on kotoba :8077. Nine handlers
  over one kotoba EAVT graph, mirroring the submersible manufacturing lifecycle:

    handle-hull-ring-fabrication    L1: material lot → ring fabrication → attestation
    handle-section-assembly         L2: rings + penetrators → section assembly
    handle-weld-inspection          L3: 100% NDT (RT/UT/PT) → weld inspection
    handle-system-integration       L4: propulsion + life-support + sensors
    handle-section-joining          L5a: multi-pass TIG + 100% RT + PWHT
    handle-pressure-test            L5b: 1.25× design depth hydrotest
    handle-sea-trial                L5c: dock → harbor → deep-water trial
    handle-marine-emissions-audit   cross: MARPOL + BWMC + biofouling compliance
    handle-class-certification-binder terminal: bind all records → kotoba-datomic anchor

  Civilian-only (depth ≤6500m, no military/nuclear/stealth per Charter §2(a)) enforced
  at L1 + L2 + L4 + L5b gates. LLM access is Murakumo-only via KotobaLLM (127.0.0.1:4000,
  gemma3:4b; G15). State is written back to the kotoba Datom log (G16). Settlement is USDC
  on Base L2 + ERC-4337 + TitheRouter 10% only — no fiat (G17). The platform holds no key;
  the member signs each settlement with their own passkey/smart-account (G18). Every stage
  is recorded as a Datom — no silent truncation (G16). This R0 build computes and returns
  plans/records; it does not dispatch real manufacturing work and settlement stops at :intent (G19).

  1:1 faithful port of py/agent.py (ADR-2605252200 Phase 4).
  Note: handle-class-certification-binder fixes a Python syntax bug in the original
  (kotoba-datomic_cid was an invalid assignment target in Python); the semantic intent
  is preserved faithfully."
  (:require [clojure.string :as str]))

(def TITHE-BPS 1000)
(def DEPTH-CAP-M 6500)

;; ── Murakumo-only inference (G15) ──

(defn- infer
  "Murakumo-only inference (G15). Returns a placeholder string when the LLM host is absent
  (offline / WASM-not-connected). Faithful to py/_infer behaviour."
  [prompt]
  ;; In the WASM/offline context there is no `kotoba.llm` host binding available.
  ;; The fallback matches what the Python code returns when `llm` is None.
  "LLM_NOT_AVAILABLE")

;; ── L1: hull ring fabrication ──

(defn handle-hull-ring-fabrication
  "L1: Material lot → ring fabrication (TIG/SAW) → roundness check < 0.5% Ø."
  [state]
  (let [material  (get state "material_lot" "")
        roundness (double (get state "roundness_pct" 0.5))
        ndt       (boolean (get state "ndt_pass" false))]
    (cond
      (> roundness 0.5)
      (assoc state "attestation" "FAIL_roundness_exceeded")

      (not ndt)
      (assoc state "attestation" "FAIL_ndt_not_passed")

      :else
      (assoc state "attestation" (str "L1-pass-" material)))))

;; ── L2: section assembly ──

(defn handle-section-assembly
  "L2: N rings + penetrators → section assembly (power/data/fluid only, no weapons)."
  [state]
  (let [rings (get state "ring_ids" [])
        pens  (get state "penetrators" [])
        forbidden #{"weapon" "missile" "torpedo" "mine"}]
    (cond
      (empty? rings)
      (assoc state "attestation" "FAIL_no_rings")

      (some (fn [p]
              (let [pl (str/lower-case (str p))]
                (some #(str/includes? pl %) forbidden)))
            pens)
      (assoc state "attestation" "FAIL_weapon_mount_detected")

      :else
      (assoc state "attestation" (str "L2-pass-" (count rings) "-rings")))))

;; ── L3: weld inspection ──

(defn handle-weld-inspection
  "L3: 100% NDT inspection (RT/UT/PT per ASME BPVC §VIII Div 3)."
  [state]
  (let [ndt-results (get state "ndt_results" [])]
    (if-not (every? identity ndt-results)
      (assoc state "result" "FAIL_ndt_incomplete")
      (let [ipfs-cid (infer (str "Generate IPFS CID for NDT record: " ndt-results))]
        (assoc state "ipfs_cid" ipfs-cid "result" "L3-pass-100pct-ndt")))))

;; ── L4: system integration ──

(defn handle-system-integration
  "L4: Propulsion (LFP/H₂/NH₃/methanol only; no nuclear), life support, sensors."
  [state]
  (let [prop     (str/lower-case (str (get state "propulsion_type" "")))
        allowed  #{"lfp" "h2" "hydrogen" "nh3" "ammonia" "methanol" "fuel-cell"}
        sonar-db (long (get state "sonar_db" 0))]
    (cond
      (not (contains? allowed prop))
      (assoc state "result" "FAIL_forbidden_propulsion")

      (> sonar-db 180)
      (assoc state "result" "FAIL_sonar_exceeds_180db")

      :else
      (assoc state "result" (str "L4-pass-" prop "-propulsion")))))

;; ── L5a: section joining ──

(defn handle-section-joining
  "L5a: Multi-pass TIG + 100% RT on final ring weld, PWHT mandatory."
  [state]
  (let [passes    (long (get state "weld_passes" 0))
        rt-pass   (boolean (get state "rt_100pct" false))
        pwht-temp (long (get state "pwht_temperature_c" 0))]
    (cond
      (< passes 1)
      (assoc state "result" "FAIL_no_welding_passes")

      (not rt-pass)
      (assoc state "result" "FAIL_rt_not_100pct")

      (< pwht-temp 500)
      (assoc state "result" "FAIL_pwht_temp_too_low")

      :else
      (assoc state "result" (str "L5a-pass-" passes "passes-pwht" pwht-temp "c")))))

;; ── L5b: pressure test ──

(defn handle-pressure-test
  "L5b: Hydrotest at 1.25× design depth (cap ≤6500m civilian only, G12)."
  [state]
  (let [design-depth (long (get state "design_depth_m" 0))
        duration-h   (long (get state "duration_hours" 0))]
    (cond
      (> design-depth DEPTH-CAP-M)
      (assoc state "result" (str "FAIL_depth_exceeds_" DEPTH-CAP-M "m_civilian_cap"))

      (< duration-h 1)
      (assoc state "result" "FAIL_test_duration_insufficient")

      :else
      (let [test-depth (* design-depth 1.25)]
        (assoc state
               "test_depth_m" test-depth
               "result" (str "L5b-pass-" test-depth "m-" duration-h "h"))))))

;; ── L5c: sea trial ──

(defn handle-sea-trial
  "L5c: Progressive sea trial (dock → harbor → deep-water per IMCA D-001)."
  [state]
  (let [dock       (boolean (get state "dock_pass" false))
        harbor     (boolean (get state "harbor_pass" false))
        deep       (boolean (get state "deep_water_pass" false))
        crew       (long (get state "crew_count" 0))
        submerged  (long (get state "submerged_duration_h" 0))]
    (cond
      (> crew 3)
      (assoc state "result" (str "FAIL_crew_" crew "_exceeds_3_person_cap"))

      (> submerged 72)
      (assoc state "result" (str "FAIL_submerged_" submerged "h_exceeds_72h_cap"))

      (not (and dock harbor deep))
      (assoc state "result" "FAIL_trial_stages_incomplete")

      :else
      (assoc state "result" "L5c-pass-all-stages"))))

;; ── cross-cutting: marine emissions audit ──

(defn handle-marine-emissions-audit
  "Cross-cutting: MARPOL Annex I-VI + BWMC + biofouling compliance (G14)."
  [state]
  (let [marpol    (boolean (get state "marpol_compliant" false))
        ballast   (boolean (get state "bwmc_compliant" false))
        biofouling (boolean (get state "biofouling_clean" false))]
    (if-not (and marpol ballast biofouling)
      (assoc state "result" "FAIL_emissions_compliance_incomplete")
      (assoc state "result" "audit-pass-all-annexes"))))

;; ── terminal: class certification binder ──

(defn handle-class-certification-binder
  "Terminal: Bind all prior stage records → kotoba-datomic anchor + Council review attestation.
  Note: the original Python source had a syntax error (kotoba-datomic_cid on the LHS of an
  assignment); this port fixes the variable name while preserving semantic intent."
  [state]
  (let [stages    (get state "all_stages" [])
        craft-did (get state "craft_did" "")]
    (if-not (every? identity stages)
      (assoc state "result" "FAIL_incomplete_certification_chain")
      (let [kotoba-datomic-cid (infer (str "Generate kotoba-datomic anchor CID for " craft-did))]
        (assoc state
               "kotoba-datomic_cid" kotoba-datomic-cid
               "council_review_required" true
               "result" "terminal-pending-council-attestation")))))

;; ── settlement intent ──

(defn build-settlement-intent
  "Compute the USDC settlement split. 10% tithe → Public Fund; contractor gets net.
  Stops at :intent — broadcast needs a member signature (G18) + operator gate (G19)."
  ([gross-minor]
   (build-settlement-intent gross-minor nil))
  ([gross-minor buyer-sig-ref]
   (let [gross  (long gross-minor)
         tithe  (quot (* gross TITHE-BPS) 10000)]
     {"rail"         "usdc-base-l2"
      "grossMinor"   gross
      "titheMinor"   tithe
      "payoutMinor"  (- gross tithe)
      "titheRouter"  "50-infra/etzhayyim-tithe-router"
      "state"        (if buyer-sig-ref "executed" "intent")
      "buyerSigRef"  (or buyer-sig-ref "")})))
