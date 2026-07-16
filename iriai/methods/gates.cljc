#!/usr/bin/env bb
;; iriai 入会 — constitutional gate assertions (clj-native, pure stdlib).
(ns iriai.methods.gates
  "iriai 入会 — the constitutional GATES (ADR-2606272200), as throwing assertions +
  structural checks. The charter-clean inversion of a for-profit utility: a lifeline
  (ライフライン) is a COMMONS right of use (入会権), never a metered product, never
  leverage, never withheld.

  The strongest gates are STRUCTURAL — a forbidden act has no attribute to express it,
  so it is unrepresentable (the kaname/subaru/kafun pattern), and `forbidden-absent?`
  proves the emitted datoms never contain one. The remaining gates throw `ex-info`.

  G1  commons-MAP-not-shutoff-list : coverage/resilience map; a lifeline is NEVER withheld.
                                     :iriai/shutoff :iriai/disconnect :iriai.region/deny
                                     :iriai.person/* unrepresentable.
  G2  commons-not-a-market         : cash≡0 to consumer; :iriai.fund/tariff :iriai.fund/price
                                     :iriai.fund/subscription :iriai.fund/equity
                                     :iriai.fund/disconnect-for-nonpayment unrepresentable;
                                     instruments {:grant :milestone-escrow :in-kind} only.
  G3  steward-not-sovereign        : no :fund / :iriai.manage/decide / :iriai.manage/dispatch;
                                     every proposal advisory:true / binds-fund:false / 1-SBT-1-vote.
  G4  non-profit-rails-only        : donation→tithe→public-fund; no custodial-fiat / ad / for-profit-capture.
  G5  assessment-r0-only-never-acts: actuation-class :intent only; no :iriai/actuate; live act = producer + Council.
  G6  no-server-key                : server-held-key false; member-CACAO attribution; local-only heartbeat.
  G7  kotoba-eavt-native           : datoms flagged :iriai/derived + :iriai/sourcing.
  G8  synthetic-seed               : R0 seed :synthetic; real region/utility data = operator/Council step."
  (:require [clojure.string :as str]))

(def forbidden-attrs
  "Attributes that must NEVER appear in any emitted datom — the structural negative
  space that makes the charter inversions unrepresentable (test-enforced)."
  [;; G1 — a lifeline is never withheld as leverage
   ":iriai/shutoff" ":iriai/disconnect" ":iriai.region/deny" ":iriai.cell/withhold"
   ":iriai.person/" ":iriai.person/connection" ":iriai.person/location"
   ;; G2 — commons, not a market
   ":iriai.fund/tariff" ":iriai.fund/price" ":iriai.fund/subscription"
   ":iriai.fund/meter-bill" ":iriai.fund/equity" ":iriai.fund/debt"
   ":iriai.fund/revenue-share" ":iriai.fund/disconnect-for-nonpayment"
   ;; G3 — steward, not sovereign
   ":iriai/fund" ":iriai.manage/decide" ":iriai.manage/dispatch"
   ;; G5 — assessment only, never acts (infra + twin + maintenance)
   ":iriai/actuate" ":iriai.infra/energize" ":iriai.infra/open-valve"
   ":iriai.infra/ignite" ":iriai.infra/activate-link"
   ":iriai.twin/energize" ":iriai.maint/dispatch-crew" ":iriai.maint/actuate"
   ;; G2 — upkeep is a commons, never billed (maintenance side)
   ":iriai.maint/consumer-bill" ":iriai.maint/tariff"])

(def fundable-instruments
  "G2: give-only instrument algebra. Anything outside this set is unrepresentable."
  #{:grant :milestone-escrow :in-kind})

(defn forbidden-absent?
  "STRUCTURAL G1/G2/G3/G5: true iff none of the forbidden attributes appear in an EDN
  datom string. The proof that the charter inversions are unrepresentable."
  [edn-str]
  (not-any? #(str/includes? edn-str %) forbidden-attrs))

;; ── throwing assertions (defence-in-depth over the structural guarantee) ───────
(defn check-instrument
  "G2: a funding instrument must be give-only. Throws on equity/debt/revenue-share/etc."
  [instrument]
  (when-not (fundable-instruments (keyword (name instrument)))
    (throw (ex-info "G2 violation: a lifeline commons funds GIVE-ONLY (grant/milestone-escrow/in-kind); equity/debt/revenue-share/subscription unrepresentable"
                    {:gate "G2" :instrument instrument :allowed fundable-instruments})))
  instrument)

(defn check-cash-zero
  "G2: cash to the lifeline consumer is structurally 0 (§1.16 in-kind). Throws otherwise."
  [cash-to-consumer]
  (when-not (zero? cash-to-consumer)
    (throw (ex-info "G2 violation: a lifeline is delivered §1.16 IN-KIND; cash to consumer ≡ 0 (never billed)"
                    {:gate "G2" :cash cash-to-consumer})))
  cash-to-consumer)

(defn check-advisory
  "G3: iriai is a STEWARD — every proposal/decision is advisory + binds-fund false.
  The Public Fund (1 SBT = 1 vote) decides, never iriai. Throws otherwise."
  [{:strs [advisory binds_fund decided_by]}]
  (when (or (not (true? advisory)) (true? binds_fund))
    (throw (ex-info "G3 violation: iriai is a steward, not a sovereign — proposals are advisory:true / binds-fund:false, decided by 1 SBT = 1 vote"
                    {:gate "G3" :advisory advisory :binds-fund binds_fund :decided-by decided_by})))
  true)

(defn check-actuation-intent
  "G5: iriai never acts — every management decision stops at :intent (compute-only R0).
  Live actuation is the producer cell under Council Lv7+. Throws otherwise."
  [actuation-class]
  (when-not (= :intent actuation-class)
    (throw (ex-info "G5 violation: iriai is assessment + R0 design only — actuation-class must be :intent; live act is the producer actor's cell under Council Lv7+ + operator-DID + member-sig"
                    {:gate "G5" :actuation-class actuation-class})))
  actuation-class)

(defn check-keyless
  "G6: no-server-key — iriai holds no signing key; server-held-key is structurally false.
  Autonomous writes are member-CACAO-attributed. Throws otherwise."
  [server-held-key]
  (when-not (false? server-held-key)
    (throw (ex-info "G6 violation: no-server-key — iriai holds no key; server-held-key must be false; writes are member-CACAO-attributed"
                    {:gate "G6" :server-held-key server-held-key})))
  server-held-key)

(def ^:private safety-verdicts #{:corrective-repair :decommission})

(defn check-safety-floor
  "G9 (maintenance): an UNSAFE asset is never deferred for cost — its maintenance
  verdict must be :corrective-repair or :decommission, never :ok / :inspect /
  :preventive-service / :refurbish (mirrors mizuho's chlorination clamp + kamado's
  purge-to-entry gate + kafun's refuse-precedes-routing). Throws otherwise."
  [safety verdict]
  (when (and (= :unsafe safety) (not (safety-verdicts verdict)))
    (throw (ex-info "G9 violation: an UNSAFE asset must route to :corrective-repair or :decommission — safety is never deferred for cost"
                    {:gate "G9" :safety safety :verdict verdict})))
  verdict)
