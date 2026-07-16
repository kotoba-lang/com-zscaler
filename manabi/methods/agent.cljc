(ns manabi.methods.agent
  "manabi 学び — open-curriculum education langgraph actor (kotoba WASM cell).

  ADR-2605261045, migration plan Phase 4. Faithful clj/cljc port of py/agent.py.
  Five handlers over one kotoba EAVT graph, mirroring the curriculum path:

    handle-literacy         reading + writing progression (phonemic awareness → written expression)
    handle-numeracy         counting → arithmetic → algebra → calculus
    handle-civics-charter   comparative governance + etzhayyim Charter understanding (anti-indoctrination G12)
    handle-vocational       practical skill demonstration (agriculture/construction/energy/pharma domains)
    handle-lifelong-inquiry open-ended learner-led exploration (no age-out, G11)

  LLM access is Murakumo-only via KotobaLLM (127.0.0.1:4000, gemma3:4b; G5). State is
  written back to the kotoba Datom log (G6). Anti-addiction UX enforced structurally (G3).
  Minor learners get aggregate-only attestations (G6 privacy). Anti-credentialism (G7):
  no degrees/transcripts, only skillAttestation (replayable demonstrations). Member signature
  on settlement only (G15). This R0 build computes and returns plans/records; it does not
  broadcast real settlements (G11-gated; settlement stops at :intent).")

;; ── constants ──────────────────────────────────────────────────────────────────
(def TITHE-BPS 1000) ; 10% TitheRouter auto-split (constitutional), basis points

;; ── _murakumo-infer — Murakumo-only inference (G5) ───────────────────────────
(defn murakumo-infer
  "Murakumo-only inference (G5). Returns offline sentinel when host not available.
  In WASM host: would call (llm/infer model prompt). Offline sentinel matches agent.py."
  [_prompt]
  "LLM_NOT_AVAILABLE")

;; ── validate-minor-aggregate-only — G6 privacy gate ─────────────────────────
(defn validate-minor-aggregate-only
  "G6 enforcement: minors get aggregate-only fields only.
  Returns true if data is safe for the age-bucket; false if a forbidden PII key is present."
  [age-bucket data]
  (if (not= age-bucket "minor")
    true
    (let [forbidden #{"performancePercentile" "errorLogSummary" "timeOnTaskSec"}]
      (not (some #(contains? data %) forbidden)))))

;; ── handle-literacy ───────────────────────────────────────────────────────────
(defn handle-literacy
  "Reading + writing progression (phonemic awareness → written expression)."
  [state]
  (let [stage (get state "current_stage" "intro")
        flow  ["intro" "phonemic-awareness" "decoding" "fluency" "comprehension"
               "writing-mechanics" "written-expression"]
        idx   (.indexOf flow stage)]
    (if (and (>= idx 0) (< (inc idx) (count flow)))
      (let [next-stage (nth flow (inc idx))
            prompt     (str "Learner advancing from " stage " to " next-stage " in literacy. "
                            "Suggest a guided discovery activity (not worksheets).")]
        (murakumo-infer prompt)
        (merge state
               {"current_stage"       next-stage
                "completion_fraction" (min 1.0 (+ (get state "completion_fraction" 0.0) 0.15))}))
      state)))

;; ── handle-numeracy ───────────────────────────────────────────────────────────
(defn handle-numeracy
  "Counting → arithmetic → algebra → calculus."
  [state]
  (let [stage (get state "current_stage" "counting")
        flow  ["counting" "place-value" "addition-subtraction" "multiplication-division"
               "fractions" "decimals" "algebra" "geometry" "calculus"]
        idx   (.indexOf flow stage)]
    (if (and (>= idx 0) (< (inc idx) (count flow)))
      (let [next-stage (nth flow (inc idx))
            prompt     (str "Learner advancing from " stage " to " next-stage " in numeracy. "
                            "Suggest hands-on discovery (manipulatives, real-world context).")]
        (murakumo-infer prompt)
        (merge state
               {"current_stage"       next-stage
                "completion_fraction" (min 1.0 (+ (get state "completion_fraction" 0.0) 0.11))}))
      state)))

;; ── handle-civics-charter ─────────────────────────────────────────────────────
(defn handle-civics-charter
  "Comparative governance + etzhayyim Charter understanding (anti-indoctrination G12)."
  [state]
  (let [prompt  (str "Teach Charter understanding via comparative governance lens: "
                     "democracy, monarchy, theocracy, communalism, etc. Include etzhayyim as ONE example. "
                     "No single-truth framing (G12, G14). Learner-led inquiry encouraged.")
        _result (murakumo-infer prompt)
        level   (get state "charter_understanding_level" "foundational")
        level   (if (= level "foundational") "intermediate" level)]
    (merge state
           {"charter_understanding_level" level
            "timestamp"                   "2026-06-02T10:00:00Z"})))

;; ── handle-vocational ─────────────────────────────────────────────────────────
(defn handle-vocational
  "Practical skill demonstration (agriculture/construction/energy/pharma)."
  [state]
  (let [domain  (get state "domain" "agriculture")
        prompt  (str "Learner demonstrating skill in " domain " domain. "
                     "Guide creation of replayable artifact (code, photo, project). "
                     "Output: demonstration CID for on-chain skill attestation (anti-credentialism G7).")
        _result (murakumo-infer prompt)]
    (merge state
           {"demonstration_cid" "bafyreiexample-skill-artifact-placeholder"
            "skill_domain"      domain})))

;; ── handle-lifelong-inquiry ───────────────────────────────────────────────────
(defn handle-lifelong-inquiry
  "Open-ended learner-led exploration (no age-out, G11)."
  [state]
  (let [topic (get state "inquiry_topic" "")
        topic (if (empty? topic)
                (let [prompt "Learner is beginning lifelong inquiry. Help them formulate a genuine question about something they wonder."
                      result (murakumo-infer prompt)]
                  (if (and result (pos? (count result)))
                    (subs result 0 (min 100 (count result)))
                    "open-exploration"))
                topic)
        prompt (str "Learner exploring: '" topic "'. "
                    "Guide resource discovery, synthesis, and self-paced project. "
                    "100% learner-led (G13). No age-out (G11).")]
    (murakumo-infer prompt)
    (merge state
           {"inquiry_topic"       topic
            "completion_fraction" (min 1.0 (+ (get state "completion_fraction" 0.0) 0.20))})))

;; ── build-settlement-intent — USDC + TitheRouter (G7/G15) ────────────────────
(defn build-settlement-intent
  "Build USDC settlement intent (G7: non-fiat only, G15: member signature only).
  Stops at :intent unless member signature present (G11).
  Tithe split: 10% → Public Fund via TitheRouter (constitutional automatic).

  Matches agent.py build_settlement_intent exactly:
    tithe_minor = int(gross_minor * TITHE_BPS / 10000)
    factory_payout_minor = gross_minor - tithe_minor"
  ([gross-minor]
   (build-settlement-intent gross-minor nil))
  ([gross-minor buyer-sig-ref]
   (let [gross       (long gross-minor)
         tithe       (long (/ (* gross TITHE-BPS) 10000))
         factory-pay (- gross tithe)]
     {"gross_minor"          gross
      "tithe_minor"          tithe
      "factory_payout_minor" factory-pay
      "rail"                 "usdc-base-l2"
      "state"                (if buyer-sig-ref "executed" "intent")
      "buyer_sig_ref"        buyer-sig-ref})))

;; ── check-domain-gate — domain gate enforcement ───────────────────────────────
(defn check-domain-gate
  "Domain gate enforcement (injected for testing).
  When gate-fn is provided, calls (gate-fn learner-did gate-name).
  Default: allowed=true, reason=no domain gate wired (R0)."
  ([learner-did gate-name]
   (check-domain-gate learner-did gate-name nil))
  ([learner-did gate-name gate-fn]
   (if (some? gate-fn)
     (gate-fn learner-did gate-name)
     {"allowed" true "reason" "no domain gate wired (R0)"})))
