#!/usr/bin/env bb
;; 澪 mio — analyze → datoms → coverage (clj-native, pure stdlib).
(ns mio.methods.analyze
  "澪 mio — the Proof-of-Useful-Flow verification core (Energy Order Protocol).

  OBSERVATION + VERIFICATION ONLY. Given a set of flow-improvement CLAIMS (each
  submitted by a suite actor — 撓 tawami / 燠 okibi / 樋 toi / 委 yudane — or
  hikari), decide which reach :verified and account the org-wide 'Flowrate' (the
  verified useful-flow total — the Proof-of-Useful-Flow analogue of hashrate).

  The §9-verification problem is the DEFINING gate. A claim reaches :verified only
  if it carries all five verification facts AND its verification-confidence clears
  the threshold:
    1. :baseline-method     present (a counterfactual to measure against)
    2. :additionality       ≥ additionality-min (would NOT have happened anyway)
    3. :measurement-source  a TRUSTED measurement (self-report alone cannot reach it)
    4. :double-count-key    unique (a collision with an earlier claim is rejected)
    5. :leakage             ≤ leakage-max (not offset by emissions elsewhere)

  Hard invariants (proven by tests):
    G1  reward NEVER derives from CONSUMED energy — only from ORDERED flow. No
        :consumed-reward attribute exists; useful-flow-score is 0 unless :verified,
        and only :verified claims route to :reward. This is the PoW → PoUF pivot.
    G3  an order-delta is a FACT, never a trade/price signal or a point forecast
        (no :trade / :signal / price-forecast-point attribute is ever emitted).
    G5  intention claims are content-free — no per-person intent text is read or
        emitted; the report is a RESILIENCE/REWARD map, NEVER a market signal."
  (:require [clojure.string :as str]))

;; ── thresholds + the §9 measurement-confidence table ─────────────────────────

(def verified-threshold  0.5)   ;; verification-confidence ≥ this (with gates) → :verified
(def additionality-min   0.3)   ;; below this → the saving would have happened anyway
(def leakage-max         0.5)   ;; above this → the improvement is offset elsewhere

(def measurement-weight
  "Confidence weight per measurement source. self-report ALONE (0.3) cannot reach
  the verified threshold — a structural defence against unverifiable claims (§9)."
  {:third-party-audit 1.0
   :zk-proof          0.95
   :signed-meter      0.9
   :dual-sensor       0.9
   :satellite         0.85
   :self-report       0.3})

;; ── pure analytics ───────────────────────────────────────────────────────────

(defn verification-confidence
  "0..1 — the §9 confidence that the ordered flow is REAL:
     measurement-weight × additionality × (1 − leakage).
  A claim with no trusted measurement, no additionality, or full leakage → 0."
  [c]
  (let [mw   (get measurement-weight (:measurement-source c) 0.0)
        add  (double (or (:additionality c) 0))
        leak (double (or (:leakage c) 0))]
    (* mw add (- 1.0 leak))))

(defn verdict
  "The §9 verdict for a claim. `dup?` = its :double-count-key was already seen.
  Order of rejection is meaningful (a double-count is rejected before anything
  else; leakage before evidence gaps)."
  [c dup?]
  (let [base (:baseline-method c)
        add  (double (or (:additionality c) 0))
        mw   (get measurement-weight (:measurement-source c) 0.0)
        leak (double (or (:leakage c) 0))]
    (cond
      dup?                          :rejected-double-count
      (> leak leakage-max)          :rejected-leakage
      (str/blank? (str base))       :insufficient-evidence
      (< add additionality-min)     :insufficient-evidence
      (zero? mw)                    :insufficient-evidence
      (>= (verification-confidence c) verified-threshold) :verified
      :else                         :insufficient-evidence)))

(defn route
  "Where a claim's outcome is routed. Only :verified flow earns; everything else
  is returned to the submitting actor for :review."
  [vd]
  (if (= vd :verified) :reward :review))

(defn analyze-claim
  "Per-claim derived observation map (string-keyed, agent-facing). `dup?` is
  supplied by `analyze` (it needs the cross-claim double-count-key view)."
  [c dup?]
  (let [vd   (verdict c dup?)
        conf (verification-confidence c)
        od   (double (or (:order-delta-kwh c) 0))]
    {"id" (:id c)
     "name" (:name c)
     "flow_class" (:flow-class c)
     "source_actor" (:source-actor c)
     "order_delta_kwh" od
     "verification_confidence" conf
     ;; G1: useful-flow-score (the reward basis) is 0 unless the order is VERIFIED.
     ;; It is ORDER × confidence — never CONSUMPTION.
     "useful_flow_score" (if (= vd :verified) (* od conf) 0.0)
     "verdict" vd
     "route" (route vd)
     "sourcing" (or (:sourcing c) :representative)
     "source" (:source c)}))

(defn analyze
  "Full analysis: per-claim verdicts (with cross-claim double-count detection) +
  per-flow-class aggregates + the org-wide Flowrate totals."
  [claims]
  (let [rows (:rows
              (reduce
               (fn [{:keys [seen rows]} c]
                 (let [k    (:double-count-key c)
                       dup? (boolean (and k (contains? seen k)))]
                   {:seen (if k (conj seen k) seen)
                    :rows (conj rows (analyze-claim c dup?))}))
               {:seen #{} :rows []}
               claims))
        by-class (group-by #(get % "flow_class") rows)
        classes (vec (for [[cls crows] (sort-by (comp name key) by-class)]
                       {"flow_class" cls
                        "count" (count crows)
                        "verified_count" (count (filter #(= (get % "verdict") :verified) crows))
                        "total_order_delta_kwh" (reduce + 0.0 (map #(get % "order_delta_kwh") crows))
                        "total_useful_flow_score" (reduce + 0.0 (map #(get % "useful_flow_score") crows))}))
        verified (filter #(= (get % "verdict") :verified) rows)]
    {"claims" rows
     "classes" classes
     "totals" {"total_claims" (count rows)
               "verified_claims" (count verified)
               ;; the headline metric — the org's "Flowrate" (PoW → PoUF).
               "verified_flowrate_score" (reduce + 0.0 (map #(get % "useful_flow_score") verified))}}))

;; ── datom emission (append-only EAVT; every derived datom flagged) ───────────

(defn- add [e a v] [":db/add" e a v])
(defn- round3 [x] (/ (Math/round (* (double x) 1000.0)) 1000.0))

(defn datoms
  "Append-only EAVT datom VECTORS for the derived observations (the persistable
  form). Every datom carries :mio/derived true + the row's :mio/sourcing;
  :authoritative rows additionally carry the cited :mio/source. No
  :trade / :signal / :consumed-reward / forecast-point attribute is ever emitted
  (G1/G3)."
  [{:strs [claims classes totals]}]
  (let [cdatoms (mapcat
                 (fn [r]
                   (let [e (str "mio-claim:" (get r "id"))
                         src (get r "source")]
                     (cond-> [(add e ":mio.claim/name" (get r "name"))
                              (add e ":mio.claim/flow-class" (str (get r "flow_class")))
                              (add e ":mio.claim/source-actor" (get r "source_actor"))
                              (add e ":mio.obs/order-delta-kwh" (round3 (get r "order_delta_kwh")))
                              (add e ":mio.obs/verification-confidence" (round3 (get r "verification_confidence")))
                              (add e ":mio.obs/useful-flow-score" (round3 (get r "useful_flow_score")))
                              (add e ":mio.obs/verdict" (str (get r "verdict")))
                              (add e ":mio.obs/route" (str (get r "route")))
                              (add e ":mio/sourcing" (str (get r "sourcing")))
                              (add e ":mio/derived" true)]
                       src (conj (add e ":mio/source" src)))))
                 claims)
        kdatoms (mapcat
                 (fn [k]
                   (let [e (str "mio-class:" (name (get k "flow_class")))]
                     [(add e ":mio.class/claim-count" (get k "count"))
                      (add e ":mio.class/verified-count" (get k "verified_count"))
                      (add e ":mio.class/total-order-delta-kwh" (round3 (get k "total_order_delta_kwh")))
                      (add e ":mio.class/total-useful-flow-score" (round3 (get k "total_useful_flow_score")))
                      (add e ":mio/derived" true)]))
                 classes)
        e "mio-ledger:flowrate"
        ldatoms [(add e ":mio.ledger/total-claims" (get totals "total_claims"))
                 (add e ":mio.ledger/verified-claims" (get totals "verified_claims"))
                 (add e ":mio.ledger/verified-flowrate-score" (round3 (get totals "verified_flowrate_score")))
                 (add e ":mio/derived" true)]]
    (vec (concat cdatoms kdatoms ldatoms))))

(defn render-datoms
  "EDN string of the derived-observation datoms (see `datoms`)."
  [assessment]
  (str "[\n " (str/join "\n " (map pr-str (datoms assessment))) "\n]\n"))

;; ── coverage (which suite actors are submitting) ─────────────────────────────

(def ^:private universe
  "Representative claim count expected per flow class (a coverage yardstick).
  Drives the gap worklist — under-represented classes mean an idle suite actor."
  {:waste-heat 4 :flexibility 4 :compute-routing 4 :renewable-absorb 3 :peak-shave 3 :intention 3})

(defn coverage
  [claims]
  (let [by-class (group-by :flow-class claims)
        rows (for [[cls target] (sort-by (comp name key) universe)
                   :let [have (count (get by-class cls []))]]
               {"flow_class" cls "have" have "target" target
                "gap" (max 0 (- target have))})]
    {"by_class" (vec rows)
     "total_have" (count claims)
     "total_target" (reduce + (vals universe))
     "total_gap" (reduce + (map #(get % "gap") rows))}))

;; ── markdown Proof-of-Useful-Flow ledger (NOT a market signal) ───────────────

(defn render-report
  [analysis coverage-map]
  (let [rows (->> (get analysis "claims")
                  (sort-by #(- (get % "useful_flow_score"))))
        cov (get coverage-map "by_class")
        totals (get analysis "totals")
        auth (count (filter #(= (get % "sourcing") :authoritative) rows))
        total (count rows)]
    (str
     "# 澪 mio — PROOF OF USEFUL FLOW ledger\n\n"
     "OBSERVATION + VERIFICATION ONLY. This is a **resilience/reward map, NEVER a "
     "market/price signal** and NEVER a trade. The basis of value is **ORDERED "
     "flow, not CONSUMED energy** (Hashrate → Flowrate): reward derives only from "
     "the verified useful-flow score, never from kWh burned. Provenance: **" auth "/"
     total " claims :authoritative** (a cited measurement via operator-triggered G7 "
     "ingest); the remainder are :representative.\n\n"
     "## Org Flowrate (verified useful-flow total)\n\n"
     "- **verified Flowrate = " (round3 (get totals "verified_flowrate_score")) " kWh-equiv**\n"
     "- verified claims: " (get totals "verified_claims") " / " (get totals "total_claims") "\n\n"
     "## Claim ledger (useful-flow score, highest first)\n\n"
     "| claim | class | actor | order Δ kWh | confidence | useful-flow | verdict | route |\n"
     "|---|---|---|---|---|---|---|---|\n"
     (str/join "\n"
               (for [r rows]
                 (str "| " (get r "name")
                      " | " (name (get r "flow_class"))
                      " | " (get r "source_actor")
                      " | " (round3 (get r "order_delta_kwh"))
                      " | " (round3 (get r "verification_confidence"))
                      " | " (round3 (get r "useful_flow_score"))
                      " | " (name (get r "verdict"))
                      " | " (name (get r "route")) " |")))
     "\n\n## Coverage (which suite actors are submitting)\n\n"
     "| flow class | have | target | gap |\n|---|---|---|---|\n"
     (str/join "\n"
               (for [c cov]
                 (str "| " (name (get c "flow_class")) " | " (get c "have")
                      " | " (get c "target") " | " (get c "gap") " |")))
     "\n\n_verdict → verified (→ :reward, advisory/1 SBT=1 vote) · insufficient-evidence · "
     "rejected-double-count · rejected-leakage. The five §9 verification facts "
     "(baseline / additionality / measurement / double-count-key / leakage) are the "
     "only path to reward._\n")))

;; ── CLI (bb) ────────────────────────────────────────────────────────────────

#?(:clj
   (defn -main [& args]
     (let [seed (or (first args) "20-actors/mio/kotoba/seed.edn")
           rows (clojure.edn/read-string (slurp seed))
           cs (vec (filter #(= (:type %) :claim) rows))
           a (analyze cs)
           cov (coverage cs)]
       (println (render-report a cov))
       (println (str "-- " (count cs) " claims, "
                     (get-in a ["totals" "verified_claims"]) " verified, Flowrate "
                     (round3 (get-in a ["totals" "verified_flowrate_score"])) " kWh-equiv, "
                     (get cov "total_gap") " gap --")))))

#?(:clj
   (when (= *file* (System/getProperty "babashka.file"))
     (apply -main *command-line-args*)))
