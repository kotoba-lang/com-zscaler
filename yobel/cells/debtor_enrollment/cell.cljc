(ns yobel.cells.debtor-enrollment.cell
  "DebtorEnrollmentCell — Pregel cell running eligibility DMN + cross-check creditor enrollments.

  Per ADR-2605201800 §Decision + dmn/eligibility-by-rite-type.md (FIRST hit, R12/R13 short-circuit).

  Trigger: enrollDebtor XRPC request scoped to a `status=active` rite
  Effect:
    - Validate input + verify debtor SBT (Charter §1.13 invariant)
    - Run FIRST-hit DMN eligibility-by-rite-type (R12 SBT gate + R13 §2(b) instrument gate short-circuit)
    - Cross-check creditor enrollments for matching debtor (pairing for release_settlement)
    - XChaCha20-Poly1305-envelope eligibilityProof (PII)
    - Anchor debtorEnrollment MST record (skip Base L2 anchor when eligible=false to save gas)

  Murakumo node: issachar (discernment + scholar — Gen 49:14-15, 1 Chr 12:32).

  Clojure port of cells/debtor_enrollment/cell.py (langgraph-clj, portable .cljc)."
  (:require [clojure.string :as str]
            [langgraph.graph :as g]
            [yobel.ports :as ports]))

(def prohibited-instruments-r13
  #{"liquidation" "margin_call" "seizure"})

;; ─── Node functions ──────────────────────────────────────────────────

(defn validate-input [state]
  {:input-valid (boolean (and (seq (:rite-id state))
                              (seq (:debtor-did state))))})

(defn load-rite-context [state rite-registry-port]
  (if (nil? rite-registry-port)
    {:rite-status "active"
     :rite-type "shmita_7yr"
     :rite-effective-date "2026-05-20T00:00:00Z"
     :rite-jurisdiction-scope ["ALL"]}
    (let [rite (ports/get-rite rite-registry-port (:rite-id state))]
      {:rite-status (if rite (:status rite) "unknown")
       :rite-type (if rite (:rite-type rite) "shmita_7yr")
       :rite-effective-date (if rite (:effective-date rite) "")
       :rite-jurisdiction-scope (if rite (:scope-jurisdictions rite) [])})))

(defn verify-debtor-sbt [state council-sbt-port charter-compliance-port]
  (let [debtor (or (:debtor-did state) "")
        sbt (if council-sbt-port
              (ports/balance-of-level council-sbt-port debtor)
              0)
        ;; Resolve entityType claim from CouncilSBT (default 'unknown' so R14 rejects gracefully)
        sbt-entity-type (if council-sbt-port
                          (try (ports/entity-type-of council-sbt-port debtor)
                               (catch #?(:clj Exception :cljs :default) _ "unknown"))
                          "unknown")
        community (boolean
                   (or (str/starts-with? debtor "did:web:etzhayyim.com")
                       (and charter-compliance-port
                            (ports/is-aligned charter-compliance-port debtor))))
        jurisdiction (if charter-compliance-port
                       (ports/jurisdiction-of charter-compliance-port debtor)
                       "ALL")]
    {:debtor-sbt-level sbt
     :debtor-sbt-entity-type sbt-entity-type
     :debtor-community-member community
     :debtor-jurisdiction-iso3 jurisdiction}))

(defn cross-check-creditor-enrollments
  "Find creditor enrollments matching this debtor for the rite. Coerce DebtRow → map for downstream DMN."
  [state creditor-enrollment-port]
  (if (nil? creditor-enrollment-port)
    {:matched-debts []}
    (let [matched (ports/find-debts-for-debtor creditor-enrollment-port
                                               (:rite-id state)
                                               (:debtor-did state))
          normalized (mapv (fn [d]
                             (if (contains? d :debt-id)
                               (select-keys d [:debt-id :debtor-did
                                               :principal-micro-usdc :accrued-micro-usdc
                                               :origination-date :instrument])
                               d))
                           matched)]
      {:matched-debts normalized})))

(defn- date< [a b] (neg? (compare a b)))

(defn run-eligibility-dmn
  "FIRST-hit DMN per dmn/eligibility-by-rite-type.md. R14+R12+R13 short-circuit."
  [state]
  (let [sbt (get state :debtor-sbt-level 0)
        sbt-entity-type (get state :debtor-sbt-entity-type "unknown")
        declared-entity-type (get state :debtor-entity-type "")
        community (get state :debtor-community-member false)
        rite-type (:rite-type state)
        effective (get state :rite-effective-date "")
        scope (get state :rite-jurisdiction-scope [])
        jurisdiction (get state :debtor-jurisdiction-iso3 "")
        in-scope (boolean (or (some #{"ALL"} scope)
                              (some #{jurisdiction} scope)))
        debts (get state :matched-debts [])
        pre-cycle? (fn []
                     (if (seq debts)
                       (every? #(date< (get % :origination-date "") effective) debts)
                       true))]
    (cond
      ;; R14 — global natural-person-only gate (short-circuit; highest priority).
      ;; yobel releases debt for individuals only (自然人). Legal-person debt is out of scope.
      ;; Both the declared entity type (from lexicon input) and the resolved CouncilSBT
      ;; entityType claim MUST be 'natural_person'.
      (or (not= declared-entity-type "natural_person")
          (not= sbt-entity-type "natural_person"))
      {:eligible false
       :dmn-rule-fired "R14"
       :dmn-reasons [(str "debtor is not a natural person (declared="
                          (if (seq declared-entity-type) declared-entity-type "unset")
                          ", sbt_claim=" sbt-entity-type
                          "). yobel releases debt for individuals only; legal-person debt is out of scope.")]}

      ;; R12 — global SBT gate (short-circuit)
      (< sbt 1)
      {:eligible false
       :dmn-rule-fired "R12"
       :dmn-reasons ["no Council SBT — Charter §1.13 SBT-based identity requirement not met"]}

      ;; R13 — global Charter Rider §2(b) prohibited-instrument gate (short-circuit)
      (some #(contains? prohibited-instruments-r13 (get % :instrument)) debts)
      {:eligible false
       :dmn-rule-fired "R13"
       :dmn-reasons ["instrument prohibited by Charter Rider §2(b) — yobel is one-way forgiveness only, cannot validate coercive instruments"]}

      ;; Rite-type-specific
      (= rite-type "shmita_7yr")
      (cond
        (not community)
        {:eligible false :dmn-rule-fired "R3"
         :dmn-reasons ["shmita: not a community member (Deut 15:3)"]}

        (not (pre-cycle?))
        {:eligible false :dmn-rule-fired "R2"
         :dmn-reasons ["shmita: debt originated after cycle start — not within sabbatical horizon"]}

        ;; Note: sovereign_bond / corporate_bond can no longer appear here (lexicon enum dropped them + R13/R14 short-circuit upstream)
        :else
        {:eligible true :dmn-rule-fired "R1"
         :dmn-reasons ["shmita: community member + pre-cycle debt"]})

      (= rite-type "yobel_50yr")
      (cond
        (not community)
        {:eligible false :dmn-rule-fired "R5"
         :dmn-reasons ["yobel: community membership required"]}

        (pre-cycle?)
        {:eligible true :dmn-rule-fired "R4"
         :dmn-reasons ["yobel: full Jubilee release — debt + bondage + land tenure (Lev 25:10)"]}

        :else
        {:eligible false :dmn-rule-fired "R5"
         :dmn-reasons ["yobel: post-cycle debt out of scope"]})

      (= rite-type "tokusei_rei")
      (if-not in-scope
        {:eligible false :dmn-rule-fired "R7"
         :dmn-reasons ["tokusei: outside declared jurisdiction scope"]}
        ;; sovereign_bond / corporate_bond filter removed — lexicon enum already excludes them
        {:eligible true :dmn-rule-fired "R6"
         :dmn-reasons ["tokusei: jurisdiction match (natural-person debt only per ADR-2605201800)"]})

      (= rite-type "religious_jubilee")
      (cond
        (not community)
        {:eligible false :dmn-rule-fired "R9"
         :dmn-reasons ["Catholic Holy Year: community membership required"]}

        (if (seq debts)
          (every? #(contains? #{"tithe_obligation" "other"} (get % :instrument)) debts)
          true)
        {:eligible true :dmn-rule-fired "R8"
         :dmn-reasons ["Catholic Holy Year: indulgentia plenaria for tithe / spiritual debt"]}

        :else
        {:eligible false :dmn-rule-fired "R9"
         :dmn-reasons ["Catholic Holy Year: applies to spiritual / tithe debt only — monetary debt out of scope"]})

      (= rite-type "political_amnesty")
      (if-not in-scope
        {:eligible false :dmn-rule-fired "R11"
         :dmn-reasons ["political amnesty: outside declared sovereign scope. Note: yobel political_amnesty handles MASS AMNESTY FOR INDIVIDUAL DEBTORS only — sovereign/corporate debt restructuring is out of scope (ADR-2605201800)"]}
        {:eligible true :dmn-rule-fired "R10"
         :dmn-reasons ["political amnesty: sovereign decree referenced + jurisdiction match (mass amnesty for natural-person debtors — e.g. tax delinquency pardon)"]})

      :else
      {:eligible false :dmn-rule-fired "fallthrough"
       :dmn-reasons [(str "unknown riteType: " rite-type)]})))

(defn encrypt-proof [state envelope-crypto]
  (cond
    (nil? envelope-crypto)
    {:encrypted-proof-cid "ipfs://stub-proof"}

    (not (seq (:eligibility-proof state)))
    {:encrypted-proof-cid ""}

    :else
    (let [cipher (ports/envelope envelope-crypto
                                 (:eligibility-proof state)
                                 [(:debtor-did state)]
                                 "yobel.debtor_enrollment.proof")]
      {:encrypted-proof-cid (:cid cipher)})))

(defn anchor-enrollment [state anchor-bridge anchor?]
  (let [debtor-did (:debtor-did state)
        last-seg (last (str/split debtor-did #":"))
        enrollment-id (str (:rite-id state) "-debt-"
                           (subs last-seg 0 (min 8 (count last-seg))))
        pairing (if (seq (:matched-debts state)) "paired" "unpaired")]
    (if (nil? anchor-bridge)
      {:enrollment-id enrollment-id
       :pairing-status pairing
       :enrollment-vertex-uri (str "at://" debtor-did
                                   "/com.etzhayyim.apps.etzhayyim.yobel.debtorEnrollment/"
                                   enrollment-id)}
      (let [result (ports/write-and-anchor
                    anchor-bridge
                    "com.etzhayyim.apps.etzhayyim.yobel.debtorEnrollment"
                    enrollment-id
                    {:rite-id (:rite-id state)
                     :debtor-did debtor-did
                     :eligible (get state :eligible false)
                     :dmn-rule-fired (get state :dmn-rule-fired "")
                     :dmn-reasons (get state :dmn-reasons [])
                     :encrypted-proof-cid (get state :encrypted-proof-cid "")
                     :pairing-status pairing
                     :matched-debts-count (count (get state :matched-debts []))}
                    anchor?)]
        {:enrollment-id enrollment-id
         :pairing-status pairing
         :enrollment-vertex-uri (:vertex-uri result)}))))

(defn emit-rejection [_state]
  {:enrollment-id "" :pairing-status "unpaired" :enrollment-vertex-uri ""})

;; ─── Router ──────────────────────────────────────────────────────────

(defn post-dmn-router [state]
  (cond
    (not (:input-valid state)) :emit-rejection
    (not= (:rite-status state) "active") :emit-rejection
    (:eligible state) :encrypt-proof
    ;; Ineligible: still anchor an enrollment record (transparency) but skip Base L2 anchor to save gas
    :else :anchor-enrollment-ineligible))

;; ─── Graph ───────────────────────────────────────────────────────────

(defn build-graph
  "Builds + compiles the debtor-enrollment graph. Takes ONE opts map:
  {:checkpointer :rite-registry-port :creditor-enrollment-port
   :council-sbt-port :charter-compliance-port :envelope-crypto :anchor-bridge}"
  [{:keys [checkpointer rite-registry-port creditor-enrollment-port
           council-sbt-port charter-compliance-port envelope-crypto
           anchor-bridge]}]
  (-> (g/state-graph)
      (g/add-node :validate-input validate-input)
      (g/add-node :load-rite-context #(load-rite-context % rite-registry-port))
      (g/add-node :verify-debtor-sbt #(verify-debtor-sbt % council-sbt-port charter-compliance-port))
      (g/add-node :cross-check-creditor-enrollments #(cross-check-creditor-enrollments % creditor-enrollment-port))
      (g/add-node :run-eligibility-dmn run-eligibility-dmn)
      (g/add-node :encrypt-proof #(encrypt-proof % envelope-crypto))
      (g/add-node :anchor-enrollment #(anchor-enrollment % anchor-bridge true))
      (g/add-node :anchor-enrollment-ineligible #(anchor-enrollment % anchor-bridge false))
      (g/add-node :emit-rejection emit-rejection)
      (g/set-entry-point :validate-input)
      (g/add-edge :validate-input :load-rite-context)
      (g/add-edge :load-rite-context :verify-debtor-sbt)
      (g/add-edge :verify-debtor-sbt :cross-check-creditor-enrollments)
      (g/add-edge :cross-check-creditor-enrollments :run-eligibility-dmn)
      (g/add-conditional-edges :run-eligibility-dmn post-dmn-router)
      (g/add-edge :encrypt-proof :anchor-enrollment)
      (g/add-edge :anchor-enrollment g/END)
      (g/add-edge :anchor-enrollment-ineligible g/END)
      (g/add-edge :emit-rejection g/END)
      (g/compile-graph {:checkpointer checkpointer})))
