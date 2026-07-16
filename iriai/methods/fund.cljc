#!/usr/bin/env bb
;; iriai 入会 — the lifeline-commons FUNDING (資金) model (clj-native, pure stdlib).
(ns iriai.methods.fund
  "iriai 入会 — the FUNDING (資金) layer (ADR-2606272200).

  Turns each infra cell that needs action (:provision / :reinforce / :redundancy)
  into a charter-clean FUNDING PROPOSAL on the existing non-profit rails — the
  subaru 昴 §1.16-in-kind precedent (ADR-2606162355) + the tanemaki 種蒔き
  give-only steward pattern (ADR-2606122001) pointed at the lifelines.

  THE FUNDING FLOW (all four lifelines, identical algebra):
    donation (USDC/Base/ERC-4337, or non-custodial fiat→USDC on-ramp, ADR-2606111800)
      → TitheRouter 10% auto-split → Public Fund
      → grant / milestone-escrow / in-kind (decided by 1 SBT = 1 vote, NOT by iriai)
      → producer actor builds + operates
      → delivery is §1.16 SOCIAL-SECURITY IN-KIND (covenantal-universal): cash ≡ 0 to
        the consumer; the lifeline is a COMMONS right, never billed, never disconnected.
    + Displacement-Dividend coupling (ADR-2606032130): the build/ops labour freed by
      OSS robotics funds a tenure-weighted in-kind cohort pool (no live displacement
      without a funded cohort).

  IMPUTED VALUE (transparency only): the market-equivalent worth of the in-kind
  delivery (what a household would otherwise PAY a commercial utility), AGGREGATE,
  method-versioned, never per-person — it proves the §1.16 income is HIGH while cash ≡ 0.
  It is NOT a price and NOT a bill (G2).

  G2 — COMMONS, never a market: instruments are {:grant :milestone-escrow :in-kind}
  ONLY (equity/debt/revenue-share/subscription/tariff are UNREPRESENTABLE). cash-to-
  consumer is the const 0. G3 — STEWARD, not sovereign: there is NO :fund route; every
  proposal is advisory:true / binds-fund:false, decided by 1 SBT = 1 vote."
  (:require [clojure.string :as str]
            [iriai.methods.infra :as infra]))

;; ── disclosed reference (market-equivalent imputed value; aggregate, transparency-only) ──
(def ref-annual-usd-per-capita
  "Disclosed market-equivalent reference: what one person's annual lifeline service would
  cost at a commercial utility (USD/person/yr). Used ONLY to impute the §1.16 in-kind
  income value (aggregate, method-versioned). NOT a price, NOT a bill (G2)."
  {:water 100.0 :electric 450.0 :gas 400.0 :telecom 120.0 :road 150.0})

(def fundable-verdicts #{:provision :reinforce :redundancy})

;; the build-cost / instrument heuristics are DISCLOSED + synthetic at R0
(defn- instrument-for
  "Charter-clean instrument (G2: give-only). Greenfield reach-gap → milestone-escrow
  (staged build, Council-attested tranches); disaster restore → grant (fast); resilience
  redundancy → in-kind (donated equipment/compute)."
  [verdict]
  (case verdict
    :provision  :milestone-escrow
    :reinforce  :grant
    :redundancy :in-kind
    :grant))

(defn imputed-annual-usd
  "Market-equivalent annual value of delivering this lifeline IN-KIND to the cell's
  served population (aggregate, transparency-only; cash to consumer ≡ 0)."
  [c]
  (let [served (long (or (:served-pop c) 0))
        gap (infra/gap-pop c)
        ;; provision reaches the gap; reinforce/redundancy steward those already served
        reach (case (:verdict (infra/verdict c)) :provision (+ served gap) (max served 0))
        rate (double (or (ref-annual-usd-per-capita (:lifeline c)) 0.0))]
    (* reach rate)))

(defn proposal
  "Build a charter-clean funding PROPOSAL for one fundable cell, or nil if the cell
  needs no funding (maintain/monitor/await-consent). advisory:true / binds-fund:false:
  the Public Fund (1 SBT = 1 vote) decides, never iriai (G3)."
  [c]
  (let [vd (:verdict (infra/verdict c))]
    (when (fundable-verdicts vd)
      {"region" (:region c)
       "region_name" (:region-name c)
       "lifeline" (:lifeline c)
       "source" (or (infra/source-actor (:lifeline c)) "?")
       "verdict" vd
       "instrument" (instrument-for vd)
       "imputed_annual_usd" (imputed-annual-usd c)
       "reach_pop" (case vd :provision (+ (long (or (:served-pop c) 0)) (infra/gap-pop c))
                            (long (or (:served-pop c) 0)))
       ;; charter-locked accounting invariants (structural, not negotiable)
       "funding_source" "donation->tithe-10%->public-fund"
       "delivery" "§1.16-social-security-in-kind"
       "cash_to_consumer" 0
       "displacement_dividend_coupled" true
       "advisory" true
       "binds_fund" false
       "decided_by" "1-sbt-1-vote"})))

(defn plan
  "Funding plan over all cells: one proposal per fundable cell + aggregate totals.
  Aggregate imputed value is the §1.16 reach value (HIGH income) while cash ≡ 0."
  [cells]
  (let [props (vec (keep proposal cells))]
    {"proposals" props
     "count" (count props)
     "by_instrument" (frequencies (map #(get % "instrument") props))
     "imputed_annual_usd_total" (reduce + 0.0 (map #(get % "imputed_annual_usd") props))
     "reach_pop_total" (reduce + 0 (map #(get % "reach_pop") props))
     "cash_to_consumer_total" 0}))

;; ── datom emission (append-only EAVT; flagged) ─────────────────────────────────
(defn- add [e a v] [":db/add" e a v])
(defn- round2 [x] (/ (Math/round (* (double x) 100.0)) 100.0))

(defn datoms
  "Append-only EAVT datoms for the funding proposals. NO :iriai.fund/tariff /
  :iriai.fund/subscription / :iriai.fund/equity / :iriai.fund/price /
  :iriai.fund/disconnect-for-nonpayment attribute is ever emitted (G2): a commons,
  never a market. binds-fund is structurally false (G3)."
  [{:strs [proposals]}]
  (vec
   (mapcat
    (fn [p]
      (let [e (str "iriai-fund:" (get p "region") ":" (name (get p "lifeline")))]
        [(add e ":iriai.fund/lifeline" (str (get p "lifeline")))
         (add e ":iriai.fund/instrument" (str (get p "instrument")))
         (add e ":iriai.fund/imputed-annual-usd" (round2 (get p "imputed_annual_usd")))
         (add e ":iriai.fund/reach-pop" (long (get p "reach_pop")))
         (add e ":iriai.fund/funding-source" (str (get p "funding_source")))
         (add e ":iriai.fund/delivery" (str (get p "delivery")))
         (add e ":iriai.fund/cash-to-consumer" 0)
         (add e ":iriai.fund/displacement-dividend-coupled" true)
         (add e ":iriai.fund/advisory" true)
         (add e ":iriai.fund/binds-fund" false)
         (add e ":iriai.fund/decided-by" "1-sbt-1-vote")
         (add e ":iriai/sourcing" ":synthetic")
         (add e ":iriai/derived" true)]))
    proposals)))

(defn render-datoms [pl]
  (str "[\n " (str/join "\n " (map pr-str (datoms pl))) "\n]\n"))

(defn render-report [pl]
  (let [props (->> (get pl "proposals")
                   (sort-by #(- (get % "imputed_annual_usd"))))]
    (str
     "# iriai 入会 — lifeline-commons FUNDING (資金) plan\n\n"
     "Every provision/reinforce/redundancy cell funded on the existing non-profit rails: "
     "**donation → TitheRouter 10% → Public Fund → grant/milestone-escrow/in-kind**, decided "
     "by **1 SBT = 1 vote** (NOT by iriai — G3). Delivery is **§1.16 social-security IN-KIND** "
     "(covenantal-universal): **cash ≡ 0 to the consumer**, the lifeline is a commons right, "
     "never billed, never disconnected for non-payment (G2). The subaru 昴 in-kind precedent "
     "(ADR-2606162355) + Displacement-Dividend coupling (ADR-2606032130).\n\n"
     "**" (get pl "count") "** proposals · imputed §1.16 income value (aggregate, market-equivalent) "
     "**$" (round2 (get pl "imputed_annual_usd_total")) "/yr** · reach **" (get pl "reach_pop_total")
     "** people · **cash to consumer $0**.\n\n"
     "| region | lifeline | verdict | instrument | imputed §1.16 value/yr | reach-pop |\n"
     "|---|---|---|---|---|---|\n"
     (str/join "\n"
               (for [p props]
                 (str "| " (get p "region_name")
                      " | " (name (get p "lifeline"))
                      " | " (name (get p "verdict"))
                      " | " (name (get p "instrument"))
                      " | $" (round2 (get p "imputed_annual_usd"))
                      " | " (get p "reach_pop") " |")))
     "\n\n_Imputed value = market-equivalent worth of the in-kind delivery (what a household "
     "would otherwise PAY) — aggregate, transparency-only; it proves the income is HIGH while "
     "cash ≡ 0 (ADR-2605301020). It is NOT a price and NOT a bill._\n"
     "_Every proposal is advisory:true / binds-fund:false — the Public Fund (1 SBT = 1 vote) decides._\n")))

;; ── CLI (bb) ───────────────────────────────────────────────────────────────────
#?(:clj
   (defn -main [& args]
     (let [seed (or (first args) "20-actors/iriai/kotoba/seed.edn")
           rows (clojure.edn/read-string (slurp seed))
           cs (vec (filter #(= (:type %) :lifeline-cell) rows))
           pl (plan cs)]
       (println (render-report pl))
       (println (str "-- " (get pl "count") " funding proposals --")))))

#?(:clj
   (when (= *file* (System/getProperty "babashka.file"))
     (apply -main *command-line-args*)))
