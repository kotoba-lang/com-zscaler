#!/usr/bin/env bb
;; Working Clojure port of py/agent.py (mitsuho food & agriculture actor).
(ns mitsuho.methods.agent
  "mitsuho 瑞穂 — food & agriculture langgraph actor (kotoba WASM cell).

  ADR-2605261015, R0 scaffold. Runs in-WASM on kotoba :8077. Handlers over the
  food & agriculture schema, enforcing mitsuho's constitutional gates:

    handle-parcel-attestation   baseline soil + water + biodiversity
    handle-crop-plan            seasonal crop plan with organic/pesticide gates (G7-G10)
    handle-harvest              yield + quality + witness sigs + soil carbon delta (G8)
    handle-food-lot             preserved lot nutritional attestation
    handle-settlement           USDC + TitheRouter intent

  LLM access is Murakumo-only via KotobaLLM (127.0.0.1:4000, gemma3:4b; G1). State is
  written back to the kotoba Datom log (G6). The member is the food producer (G2):
  no external non-adherent value flows. Settlement is USDC on Base L2 + ERC-4337 +
  TitheRouter 10% only — no fiat, no Stripe (G3). The platform holds no key; the
  member signs each settlement with their own passkey/smart-account (G4). Every stage
  is recorded as a Datom — no silent truncation (G2).

  This R0 build computes and returns attestations/records; it does not dispatch
  real agricultural work and does not broadcast settlements (both G5-gated; settlement
  stops at :intent).

  Run:  bb --classpath 20-actors 20-actors/mitsuho/py/agent.clj"
  (:require [clojure.string :as str]))

;; ── constants ──────────────────────────────────────────────────────────────────
(def ^:private tithe-bps 1000)  ; 10% TitheRouter auto-split (G3), basis points

;; G7 prohibited pesticide classes (neonicotinoid / glyphosate / paraquat / organochlorine)
(def ^:private prohibited-pesticides
  #{"neonicotinoid" "glyphosate" "paraquat" "organochlorine" "neonics"})

;; G8 soil carbon delta — negative triggers Council review
(def ^:private soil-carbon-floor-tons-co2eq 0.0)

;; ── LLM stub (Murakumo-only in production; absent here per ADR-2605215000) ────
;; kotoba host bindings unavailable in offline/test mode; return sentinel.
(defn- infer
  "Murakumo-only inference stub. In the clj port the kotoba host binding is absent;
  returns a sentinel so tests can assert the output shape without a live LLM."
  [_prompt]
  "LLM_NOT_AVAILABLE")

;; ── G7 seed-source validation ─────────────────────────────────────────────────
(defn validate-seed-source
  "G7: All varietals from open-source seed banks (Svalbard / NAVDANYA / national gene banks).
  Rejects patented commercial lines."
  [seed-source]
  (let [allowed-sources #{"svalbard" "navdanya" "national" "public"}
        source-lower (str/lower-case seed-source)]
    (if (some #(str/includes? source-lower %) allowed-sources)
      {:valid true  :reason "open-source seed bank attested"}
      {:valid false :reason (str "seed source '" seed-source "' not on approved list (G7)")})))

;; ── G9 pesticide validation ───────────────────────────────────────────────────
(defn validate-pesticides
  "G9: No synthetic pesticides (neonicotinoid / glyphosate / paraquat / organochlorine)."
  [pesticides]
  (let [hits (filter (fn [p]
                       (some #(str/includes? (str/lower-case p) %) prohibited-pesticides))
                     pesticides)]
    (if (seq hits)
      ;; match py's f-string list repr exactly (Python list = single quotes, ", " sep) so the
      ;; rejection_reason is byte-identical across impls (cross-lang parity caught the divergence).
      {:valid false :reason (str "prohibited pesticides: ["
                                 (str/join ", " (map #(str "'" % "'") hits))
                                 "] (G9)")}
      {:valid true  :reason "pesticide manifest approved"})))

;; ── parcel attestation — baseline soil + water + biodiversity (G7 gate) ──────
(defn handle-parcel-attestation
  "Baseline parcel attestation. G7 requires open-source seed banks available."
  [state]
  (let [parcel-id    (get state :parcel_id "")
        soil-score   (get state :soil_health_score 0.0)
        water-score  (get state :water_quality_score 0.0)
        biodiversity (get state :biodiversity_impact "unknown")]
    (merge state
           {:parcel_id           parcel-id
            :soil_health_score   soil-score
            :water_quality_score water-score
            :biodiversity_impact biodiversity
            :attestation_state   "recorded"})))

;; ── crop plan — seasonal plan with organic/pesticide gates (G7-G10) ──────────
(defn handle-crop-plan
  "Seasonal crop plan with gates G7 (seed sovereignty), G9 (no synthetics), G10 (GMO gate)."
  [state]
  (let [plan-id    (get state :crop_plan_id "")
        seed-source (get state :seed_source "")
        pesticides  (get state :pesticide_manifest [])
        organic     (get state :organic_certification false)
        seed-check  (validate-seed-source seed-source)]
    (if (not (:valid seed-check))
      (merge state {:plan_state "rejected" :rejection_reason (:reason seed-check)})
      (let [pest-check (validate-pesticides pesticides)]
        (if (not (:valid pest-check))
          (merge state {:plan_state "rejected" :rejection_reason (:reason pest-check)})
          ;; G10: GMO requires Council attestation (checked externally); R0: record
          (merge state
                 {:crop_plan_id         plan-id
                  :plan_state           "recorded"
                  :organic_certification organic}))))))

;; ── harvest — yield + witness sigs + soil carbon delta (G8) ──────────────────
(defn handle-harvest
  "Harvest attestation. G8: Annual soil-carbon assay; negative delta → Council review."
  [state]
  (let [harvest-id   (get state :harvest_id "")
        yield-kg     (get state :yield_quantity_kg 0.0)
        quality      (get state :quality_grade "ungraded")
        witnesses    (get state :witness_sigs [])
        photo-cid    (get state :ipfs_photo_cid "")
        soil-delta   (double (get state :soil_carbon_delta_tons_co2eq 0.0))
        [harvest-state review-note]
        (if (< soil-delta soil-carbon-floor-tons-co2eq)
          ["pending_council_review"
           (str "soil carbon delta " soil-delta " is negative (G8) → halt + Council review")]
          ["recorded" ""])]
    (merge state
           {:harvest_id                   harvest-id
            :yield_quantity_kg            yield-kg
            :quality_grade                quality
            :witness_sigs                 witnesses
            :ipfs_photo_cid               photo-cid
            :soil_carbon_delta_tons_co2eq soil-delta
            :harvest_state                harvest-state
            :council_review_note          review-note})))

;; ── food lot — preserved lot nutritional attestation ─────────────────────────
(defn handle-food-lot
  "Food lot preservation attestation with nutritional data."
  [state]
  (let [lot-id    (get state :food_lot_id "")
        kj-per-kg (get state :kilojoules_per_kg 0.0)
        protein   (get state :protein_g_per_100g 0.0)
        carbs     (get state :carbohydrate_g_per_100g 0.0)
        fat       (get state :fat_g_per_100g 0.0)
        shelf     (get state :shelf_life_days 0)
        handling  (get state :handling_instructions "")
        packaging (get state :packaging_type "")]
    (merge state
           {:food_lot_id              lot-id
            :kilojoules_per_kg        kj-per-kg
            :protein_g_per_100g       protein
            :carbohydrate_g_per_100g  carbs
            :fat_g_per_100g           fat
            :shelf_life_days          shelf
            :handling_instructions    handling
            :packaging_type           packaging
            :lot_state                "recorded"})))

;; ── settlement — USDC + TitheRouter intent (NOT broadcast; G3/G5) ────────────
(defn build-settlement-intent
  "Compute the USDC settlement split. 10% tithe → Public Fund; producer gets the net.
  Stops at :intent — broadcast needs a member signature (G4) + operator gate (G5)."
  ([gross-minor]
   (build-settlement-intent gross-minor nil))
  ([gross-minor buyer-sig-ref]
   (let [gross (long gross-minor)
         tithe (quot (* gross tithe-bps) 10000)]
     {:rail                 "usdc-base-l2"
      :grossMinor           gross
      :titheMinor           tithe
      :producerPayoutMinor  (- gross tithe)
      :titheRouter          "50-infra/etzhayyim-tithe-router"
      ;; member signs with their own passkey/smart-account; platform holds no key (G4)
      :state                (if buyer-sig-ref "executed" "intent")
      :buyerSigRef          (or buyer-sig-ref "")})))

;; ── main (smoke demo) ─────────────────────────────────────────────────────────
(defn main [& _]
  (let [demo-parcel (handle-parcel-attestation
                     {:parcel_id           "p1"
                      :soil_health_score   7.5
                      :water_quality_score 8.0
                      :biodiversity_impact "no-harm"})]
    (println "parcel attestation:" (:parcel_id demo-parcel))
    (println "settlement:" (build-settlement-intent 250000000))))

(when (= *file* (System/getProperty "babashka.file"))
  (main))
