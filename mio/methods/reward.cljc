#!/usr/bin/env bb
;; 澪 mio — reward-proposal emitter (the verified→reward arc; the economic half of PoUF).
(ns mio.methods.reward
  "reward.cljc — 澪 mio advisory REWARD-PROPOSAL emitter (Energy Order Protocol).

  Completes the Proof-of-Useful-Flow arc: a VERIFIED ordered-flow claim earns. The
  reward is **moyai reciprocity credit** (ADR-2606062101) — non-monetary, decaying,
  non-transferable, cash≡0, no governance/benefit weight — proportional to the
  verified useful-flow-score (kWh-equiv ORDERED, never CONSUMED). mio PROPOSES; the
  fund DISPOSES.

  Hard invariants (proven by tests):
    G1  reward only for :verified claims (consumption never earns — already filtered
        by mio.analyze; reward never touches an unverified claim).
    G2  reward is moyai reciprocity credit, NEVER cash/equity/money. No :cash / :usd /
        :money / :equity attribute exists — the reward is unrepresentable as currency.
    G7  no-server-key: every proposal is advisory + drafted-unsent + binds-fund=false.
        mio cannot move funds or vote; issuance is 1 SBT=1 vote + TitheRouter."
  (:require [clojure.string :as str]))

;; moyai reciprocity credit: non-monetary, decaying, non-transferable (ADR-2606062101).
(def reward-kind :moyai-reciprocity-credit)

(defn proposals
  "Verified claims → advisory reward proposals (drafted-unsent). The moyai credit is
  1:1 with the verified useful-flow-score (kWh-equiv ordered → reciprocity credit;
  the decay happens later in the moyai ledger, not here)."
  [analysis]
  (->> (get analysis "claims")
       (filter #(= :verified (get % "verdict")))
       (mapv (fn [r]
               {:proposal/id (str "reward-" (get r "id"))
                :proposal/claim-id (get r "id")
                :proposal/source-actor (get r "source_actor")
                :proposal/flow-class (get r "flow_class")
                :proposal/useful-flow-score (get r "useful_flow_score")
                :proposal/reward-kind reward-kind
                :proposal/moyai-credit (get r "useful_flow_score")
                :proposal/advisory true
                :proposal/binds-fund false
                :proposal/drafted-unsent true
                :proposal/decision "1 SBT = 1 vote + TitheRouter"}))))

(defn by-actor
  "Transparent per-source-actor allocation of proposed moyai credit."
  [proposals]
  (->> proposals
       (group-by :proposal/source-actor)
       (map (fn [[actor ps]] [actor (reduce + 0.0 (map :proposal/moyai-credit ps))]))
       (into (sorted-map))))

(defn totals [proposals]
  {:proposal-count (count proposals)
   :total-moyai-credit (reduce + 0.0 (map :proposal/moyai-credit proposals))})

;; ── datoms (ledger-ready; reward is moyai credit, never currency) ────────────

(defn- add [e a v] [":db/add" e a v])
(defn- round3 [x] (/ (Math/round (* (double x) 1000.0)) 1000.0))

(defn datoms
  [proposals]
  (vec (mapcat
        (fn [p]
          (let [e (str "mio-reward:" (:proposal/claim-id p))]
            [(add e ":mio.reward/source-actor" (:proposal/source-actor p))
             (add e ":mio.reward/flow-class" (str (:proposal/flow-class p)))
             (add e ":mio.reward/moyai-credit" (round3 (:proposal/moyai-credit p)))
             (add e ":mio.reward/kind" (str reward-kind))
             (add e ":mio.reward/advisory" true)
             (add e ":mio.reward/binds-fund" false)
             (add e ":mio/derived" true)]))
        proposals)))

(defn render-datoms [proposals]
  (str "[\n " (str/join "\n " (map pr-str (datoms proposals))) "\n]\n"))

;; ── markdown reward-proposal sheet (advisory; 1 SBT=1 vote disposes) ─────────

(defn render-report
  [analysis]
  (let [ps (proposals analysis)
        ba (by-actor ps)
        t (totals ps)]
    (str
     "# 澪 mio — REWARD PROPOSAL sheet (advisory)\n\n"
     "ADVISORY / DRAFTED-UNSENT. mio PROPOSES; **1 SBT = 1 vote + TitheRouter DISPOSE** "
     "(no-server-key — mio cannot move funds or vote). Reward is **moyai reciprocity "
     "credit** (non-monetary, decaying, non-transferable, cash≡0; ADR-2606062101) "
     "proportional to the VERIFIED useful-flow-score — ORDERED flow, never CONSUMED. "
     "Only verified claims appear here.\n\n"
     "## Proposed moyai credit by actor\n\n"
     "| source actor | proposals | moyai credit |\n|---|---|---|\n"
     (str/join "\n"
               (for [[actor credit] ba]
                 (str "| " actor " | "
                      (count (filter #(= actor (:proposal/source-actor %)) ps))
                      " | " (round3 credit) " |")))
     "\n\n**total: " (:proposal-count t) " proposals · "
     (round3 (:total-moyai-credit t)) " moyai credit (advisory, drafted-unsent)**\n")))

;; ── CLI (bb) ────────────────────────────────────────────────────────────────

#?(:clj
   (defn -main [& args]
     (let [seed (or (first args) "20-actors/mio/kotoba/seed.edn")
           rows (clojure.edn/read-string (slurp seed))
           cs (vec (filter #(= (:type %) :claim) rows))
           ;; load + verify via analyze
           a ((requiring-resolve 'mio.methods.analyze/analyze) cs)
           ps (proposals a)]
       (println (render-report a))
       (println (str "-- " (count ps) " advisory reward proposals, "
                     (round3 (:total-moyai-credit (totals ps))) " moyai credit --")))))

#?(:clj
   (when (= *file* (System/getProperty "babashka.file"))
     (apply -main *command-line-args*)))
