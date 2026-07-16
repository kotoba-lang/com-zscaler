#!/usr/bin/env bb
;; iriai 入会 — the lifeline-commons MANAGEMENT (管理) / governance layer (clj-native).
(ns iriai.methods.manage
  "iriai 入会 — the MANAGEMENT (管理) layer (ADR-2606272200).

  Wraps every funding proposal in the religious-corp GOVERNANCE envelope and the
  Transparent-Force (§1.12) actuation discipline. iriai PROPOSES; the commons DECIDES;
  the producer actor (under Council) ACTS. Three management facets per decision:

  1. GOVERNANCE ROUTE — 1 SBT = 1 vote (PublicFundGovernance: 20% quorum / 50% of cast /
     7-day vote / 48h timelock) + Council Lv6+ attestation (= PR review during bootstrap).
     A lifeline that crosses into Transparent-FORCE territory (critical-infra build on
     contested land, cross-border) escalates to Council Lv7+.

  2. ACTUATION CLASS — compute-only R0. iriai's decision STOPS at :intent. The live act
     (energize a grid / open a valve / ignite gas / activate a link) is the producer
     actor's cell under Council Lv7+ + operator-DID + member signature (the infra-robotics
     R0 precedent, ADR-2606091800/2606101430 — cell .solve() stays gated, dry-run only).
     There is NO :iriai/actuate (G5).

  3. ACCOUNTABILITY — no-server-key: iriai holds no signing key; autonomous writes are
     attributed to a consenting member via a CACAO leash (ibuki/kaname/tsubasa pattern,
     ADR-2606111400/2605231525). Every decision is on-chain-logged + open-source (§1.12).

  A lifeline is a COMMONS (入会権): governed by the members who hold it, not by a sovereign
  operator. There is no unilateral :iriai/decide / :iriai/dispatch (G3 steward-not-sovereign)."
  (:require [clojure.string :as str]
            [iriai.methods.fund :as fund]))

(def ^:private force-lifelines
  "Lifelines whose critical-infra build can implicate Transparent-Force §1.12 (cross-border,
  contested-land, critical-grid) → escalate the governance threshold to Council Lv7+."
  #{:electric :water})

(defn- governance-threshold
  "Council attestation level for a proposal. Most provision/redundancy = Lv6+; a
  critical-infra (electric/water) provision on a vulnerable region escalates to Lv7+."
  [p]
  (if (and (force-lifelines (keyword (name (get p "lifeline"))))
           (= :provision (get p "verdict")))
    "council-lv7+"
    "council-lv6+"))

(defn decision
  "Wrap one funding proposal in the governance + actuation + accountability envelope.
  Returns the management record (advisory; the vote + Council decide, not iriai)."
  [p]
  {"region" (get p "region")
   "region_name" (get p "region_name")
   "lifeline" (get p "lifeline")
   "verdict" (get p "verdict")
   "instrument" (get p "instrument")
   ;; 1. governance route — 1 SBT = 1 vote + Council
   "governance" "1-sbt-1-vote"
   "quorum" "20%"
   "approval" "50%-of-cast"
   "timelock" "48h"
   "council_attestation" (governance-threshold p)
   ;; 2. actuation class — compute-only R0; live act is producer + Council Lv7+ + operator + member-sig
   "actuation_class" :intent
   "live_actuation_gate" "producer-cell + council-lv7+ + operator-did + member-sig"
   ;; 3. accountability — no-server-key + CACAO member-delegation leash + on-chain transparency
   "server_held_key" false
   "attribution" "member-cacao-leash"
   "transparency" "on-chain-log + open-source (§1.12)"
   "advisory" true})

(defn ledger
  "Management ledger over a funding plan: one governance decision per proposal."
  [plan]
  (let [decs (mapv decision (get plan "proposals"))]
    {"decisions" decs
     "count" (count decs)
     "by_council_level" (frequencies (map #(get % "council_attestation") decs))
     "all_intent_only" (every? #(= :intent (get % "actuation_class")) decs)
     "all_keyless" (every? #(false? (get % "server_held_key")) decs)}))

;; ── datom emission (append-only EAVT; flagged) ─────────────────────────────────
(defn- add [e a v] [":db/add" e a v])

(defn datoms
  "Append-only EAVT datoms for the governance decisions. actuation-class is the const
  :intent and server-held-key the const false (G5/G6, structural). NO
  :iriai.manage/decide / :iriai.manage/dispatch / :iriai/actuate attribute is ever
  emitted (G3/G5): iriai proposes, the commons decides, the producer acts."
  [{:strs [decisions]}]
  (vec
   (mapcat
    (fn [d]
      (let [e (str "iriai-gov:" (get d "region") ":" (name (get d "lifeline")))]
        [(add e ":iriai.manage/lifeline" (str (get d "lifeline")))
         (add e ":iriai.manage/governance" (str (get d "governance")))
         (add e ":iriai.manage/council-attestation" (str (get d "council_attestation")))
         (add e ":iriai.manage/timelock" (str (get d "timelock")))
         (add e ":iriai.manage/actuation-class" ":intent")
         (add e ":iriai.manage/live-actuation-gate" (str (get d "live_actuation_gate")))
         (add e ":iriai.manage/server-held-key" false)
         (add e ":iriai.manage/attribution" (str (get d "attribution")))
         (add e ":iriai.manage/advisory" true)
         (add e ":iriai/sourcing" ":synthetic")
         (add e ":iriai/derived" true)]))
    decisions)))

(defn render-datoms [lg]
  (str "[\n " (str/join "\n " (map pr-str (datoms lg))) "\n]\n"))

(defn render-report [lg]
  (let [decs (get lg "decisions")]
    (str
     "# iriai 入会 — lifeline-commons MANAGEMENT (管理) ledger\n\n"
     "iriai PROPOSES; the commons (1 SBT = 1 vote) DECIDES; the producer actor (under "
     "Council) ACTS. Every decision: **1 SBT = 1 vote** (20% quorum / 50% of cast / 48h "
     "timelock) + **Council attestation** (Lv6+, critical-infra → Lv7+). **Compute-only R0** "
     "— iriai stops at **:intent**; the live act (energize/flow/ignite/activate) is the "
     "producer cell under Council Lv7+ + operator-DID + member-sig (G5, §1.12). "
     "**no-server-key** — iriai holds no key; writes are member-CACAO-attributed (G6).\n\n"
     "**" (get lg "count") "** decisions · all :intent-only **" (get lg "all_intent_only")
     "** · all keyless **" (get lg "all_keyless") "** · Council levels "
     (pr-str (get lg "by_council_level")) ".\n\n"
     "| region | lifeline | verdict | governance | council | actuation | server-key |\n"
     "|---|---|---|---|---|---|---|\n"
     (str/join "\n"
               (for [d decs]
                 (str "| " (get d "region_name")
                      " | " (name (get d "lifeline"))
                      " | " (name (get d "verdict"))
                      " | " (get d "governance")
                      " | " (get d "council_attestation")
                      " | " (name (get d "actuation_class"))
                      " | " (get d "server_held_key") " |")))
     "\n\n_A lifeline is a COMMONS (入会権): governed by the members who hold it, not by a "
     "sovereign operator. iriai never unilaterally decides or dispatches (G3)._\n")))

;; ── CLI (bb) ───────────────────────────────────────────────────────────────────
#?(:clj
   (defn -main [& args]
     (let [seed (or (first args) "20-actors/iriai/kotoba/seed.edn")
           rows (clojure.edn/read-string (slurp seed))
           cs (vec (filter #(= (:type %) :lifeline-cell) rows))
           lg (ledger (fund/plan cs))]
       (println (render-report lg))
       (println (str "-- " (get lg "count") " governance decisions --")))))

#?(:clj
   (when (= *file* (System/getProperty "babashka.file"))
     (apply -main *command-line-args*)))
