#!/usr/bin/env bb
;; chigiri 契 — G10 Mediation-First Rule: real computed-logic validator (iteration #9 of
;; the etzhayyim design+implementation loop; ADR-2605262700 R0 scaffold, disputeMediation
;; lexicon under com.etzhayyim.chigiri.*).
(ns chigiri.methods.dispute-mediation
  "dispute_mediation.cljc — chigiri's first real computed-logic method for the
  dispute_mediation cell (R2+, not yet wired — this ns is method-layer only, same
  precedent as musubi's methods.ceremony-recognition-resolver, iteration #8).

  Closes a gap CLAUDE.md's own 'Mediation-First Rule (G10) — Structural' section +
  manifest.jsonld's G10 gate text ('Cooperative mediation precedes adversarial
  arbitration; disputeMediation schema enforces ≥1 mediation round before arbitration')
  + the com.etzhayyim.chigiri.disputeMediation lexicon (00-contracts/lexicons/com/
  etzhayyim/chigiri/disputeMediation.json) have documented since R0 but only ever
  machine-checked at the schema-CONFORMANCE layer: methods/test_charter_gates.cljc's
  test-mediation-claim-encrypted only asserts claimSummaryEncryptedCid is a REQUIRED
  field — it never checks the actual G10 SEQUENCING rule the field descriptions and
  CLAUDE.md prose specify. This ns is that missing computed logic.

  Spec (1:1 from CLAUDE.md 'Mediation-First Rule (G10)' + the lexicon's own field
  descriptions — no invented policy):
    1. `currentRound` MUST be >= 1 before ANY arbitration channel may be invoked
       (CLAUDE.md bullet 1; lexicon `arbitrationChannel` doc: 'Selected arbitration
       channel WHEN ESCALATING' — i.e. arbitrationChannel names an actual channel only
       once mediation has entered round >= 1).
    2. `mediationOutcomes[]` MUST be populated with at least one completed round entry
       before `escalateToArbitration=true` (CLAUDE.md bullet 2).
    3. (lexicon-precise refinement of #2) the disputeMediation lexicon's own
       `escalateToArbitration` field description is MORE specific than CLAUDE.md's
       prose: 'May only be true when mediationOutcomes contains at least one entry with
       outcomeStatus=escalate' — a merely non-empty outcomes log is not enough; one of
       the logged rounds must itself have concluded with outcomeStatus=\"escalate\".

  This does NOT adjudicate the underlying DISPUTE (UPL / no legal advice, G14) — it is a
  purely structural sequencing gate over the record's own fields, checking ordering
  (mediation-first), never claim merits. Pure functions over plain (string-keyed) maps
  mirroring the lexicon's raw JSON shape — no file I/O, no network, no live dispute data;
  fully portable .cljc with no reader conditionals (kotoba-wasm > clojurewasm > cljs >
  nbb > (jvm/bb) runtime-priority: pure logic needs none of the JVM-only escape hatches
  chigiri.methods.registry's file loader uses).")

(defn- outcomes
  "The record's mediationOutcomes[] (empty vector if absent, never nil — so callers
  can seq/count without a nil-check)."
  [record]
  (or (get record "mediationOutcomes") []))

(defn- has-escalate-outcome?
  "true iff at least one mediationOutcomes entry has outcomeStatus=\"escalate\"
  (com.etzhayyim.chigiri.disputeMediation lexicon, #mediationOutcome/outcomeStatus)."
  [record]
  (boolean (some #(= "escalate" (get % "outcomeStatus")) (outcomes record))))

(defn- invoking-arbitration?
  "true iff arbitrationChannel names an actual channel — not nil/absent, and not the
  lexicon's explicit \"n-a\" knownValues sentinel for 'no channel selected'."
  [record]
  (let [ch (get record "arbitrationChannel")]
    (boolean (and (string? ch) (not= "n-a" ch)))))

(defn mediation-first-violations
  "Pure structural check of chigiri's G10 Mediation-First Rule against a disputeMediation
  record (string-keyed map mirroring the lexicon's raw JSON shape — 'createdAt' /
  'claimantDid' / 'currentRound' / 'mediationOutcomes' / 'escalateToArbitration' /
  'arbitrationChannel' / … as documented in disputeMediation.json). Returns a (possibly
  empty) vector of violation keywords; empty = G10-compliant. Independent checks — a
  record can trip more than one at once (e.g. round 0 AND an empty outcomes log while
  simultaneously trying to escalate)."
  [record]
  (let [current-round (get record "currentRound")
        round->=1? (and (integer? current-round) (>= current-round 1))
        escalating? (true? (get record "escalateToArbitration"))
        outcomes-populated? (seq (outcomes record))]
    (cond-> []
      ;; rule 1 — currentRound >= 1 before ANY arbitration channel may be invoked
      (and (invoking-arbitration? record) (not round->=1?))
      (conj :arbitration-requires-current-round-at-least-1)

      ;; rule 2 — mediationOutcomes[] must be populated before escalateToArbitration=true
      (and escalating? (not outcomes-populated?))
      (conj :escalation-requires-populated-mediation-outcomes)

      ;; rule 3 — (lexicon-precise) escalateToArbitration=true requires an outcome entry
      ;; whose outcomeStatus is specifically "escalate", not merely a non-empty log
      (and escalating? outcomes-populated? (not (has-escalate-outcome? record)))
      (conj :escalation-requires-escalate-outcome-status))))

(defn mediation-first-compliant?
  "true iff mediation-first-violations returns empty — the G10 sequencing gate a
  chigiri_dispute_mediation cell (R2+, not yet wired per manifest.jsonld roadmap) would
  consult before forwarding a record's escalateToArbitration=true / arbitrationChannel
  selection onward to an actual arbitration channel."
  [record]
  (empty? (mediation-first-violations record)))
