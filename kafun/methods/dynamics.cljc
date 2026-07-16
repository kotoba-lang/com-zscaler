#!/usr/bin/env bb
;; kafun 花粉 — remediation-READINESS system-dynamics stock-flow model.
(ns kafun.methods.dynamics
  "dynamics.cljc — kafun 花粉's OWN system-dynamics stock-flow model over remediation
  READINESS (ADR-2607102230, the SD leg of the react-loop; on ADR-2606211712).

  This is a DIFFERENT stock from `etzhayyim.ie-flow.dynamics` (that one models a SaaS-shaped
  customers/trust/data-asset/model-quality/reserves stock — the wrong domain for forest
  stands) and from tsuchifumi's `sysdyn.cljc` (a separate risk-domain E/A/I/B model). kafun's
  stock is the READINESS of the two named pipeline bottlenecks
  (`remediate/remediation-bottlenecks`): :await-sapling-supply (L1-1 無花粉苗木) and
  :await-consent (land sovereignty). Readiness accumulates toward a threshold exactly like a
  bathtub filling before it overflows — a legitimate Forrester/Meadows pattern (continuous
  accumulation crossing a discrete gate).

  CRITICAL — this module does NOT re-implement the verdict gate. `step-system` re-scores the
  SAME fixed stand snapshot through the UNCHANGED `remediate/verdict` once readiness crosses the
  :ok/granted threshold, so G1 (撲滅=restoration) / G4 (carbon) / G5 (never-acts) hold
  STRUCTURALLY for every forecast the same way they hold for a live assessment — there is no
  separate code path a forecast could take to bypass a hard refusal.

  kafun MODELS the readiness rate (:supply-rate / :consent-rate); it never supplies sapling or
  grants consent itself (G5) — `inputs` is a hypothetical EXTERNAL rate, exactly like
  `remediate/blocker-relax` is a hypothetical unblock, never an actuation. PURE — no I/O, no
  randomness; a run is reproducible and content-addressable."
  (:require [kafun.methods.remediate :as rem]))

(defn- clamp01 [x] (-> x double (max 0.0) (min 1.0)))

(def ready-threshold
  "Readiness level at which a bottleneck flips from stalled to :ok/granted (bathtub-full)."
  1.0)

(defn readiness-snapshot
  "The FIXED `stands` re-scored with :sapling-supply / :consent flipped to ready wherever THIS
  stock's readiness level has crossed the threshold. Pure — returns a NEW seq; the fixed
  population itself is never mutated (G5). Shared by `step-system` and by callers (e.g. the
  react-loop) that need the CURRENT bottleneck view under accumulated readiness rather than the
  pristine raw stands (whose :await-* verdicts never change on their own)."
  [{:keys [supply-level consent-level] :or {supply-level 0.0 consent-level 0.0}} stands]
  (mapv (fn [s]
          (cond-> s
            (and (>= (double supply-level) ready-threshold) (= (:sapling-supply s) :none))
            (assoc :sapling-supply :ok)
            (and (>= (double consent-level) ready-threshold) (not (:consent s)))
            (assoc :consent true)))
        stands))

(defn step-system
  "One time-step of the remediation-READINESS stock-flow. `stock` carries :supply-level
  :consent-level ∈ [0,1] (accumulated readiness toward the L1-1 sapling-supply / consent
  thresholds) and :cumulative-unblocked (how many of `stands` presently reach
  :reforest-priority under this stock). `inputs` carries THIS step's external readiness
  RATES :supply-rate :consent-rate — a hypothetical, kafun supplies neither (G5). `stands` is
  the FIXED assessment population (never mutated; a snapshot is scored, ADR-2606211712 G2/G5).
  Returns the next stock. Pure, deterministic."
  [{:keys [supply-level consent-level] :or {supply-level 0.0 consent-level 0.0}}
   {:keys [supply-rate consent-rate] :or {supply-rate 0.0 consent-rate 0.0}}
   stands]
  (let [supply' (clamp01 (+ (double supply-level) (double supply-rate)))
        consent' (clamp01 (+ (double consent-level) (double consent-rate)))
        snapshot (readiness-snapshot {:supply-level supply' :consent-level consent'} stands)
        unblocked (count (filter #(= :reforest-priority (:verdict (rem/verdict %))) snapshot))]
    {:supply-level supply'
     :consent-level consent'
     :cumulative-unblocked unblocked}))

(defn simulate
  "Evolve `initial` stock through the seq of per-step `inputs` against the FIXED `stands`.
  Returns the trajectory (length = (inc (count inputs))), `initial` first — `(reductions
  step-system …)` fixed over the stands. Pure."
  [initial inputs stands]
  (vec (reductions (fn [stock in] (step-system stock in stands)) initial inputs)))

(defn counterfactual
  "Run two trajectories from the same `initial` stock over `stands` — `baseline-inputs` vs
  `intervention-inputs` — and return both plus the terminal Δ per stock dimension (the
  hypothetical readiness-rate lift; pure ASSESSMENT, never actuation, G5)."
  [initial baseline-inputs intervention-inputs stands]
  (let [base (simulate initial baseline-inputs stands)
        iv (simulate initial intervention-inputs stands)
        bt (peek base)
        it (peek iv)
        dims (distinct (concat (keys bt) (keys it)))]
    {:baseline base
     :intervention iv
     :delta (into {} (for [k dims]
                       [k (- (double (get it k 0)) (double (get bt k 0)))]))}))
