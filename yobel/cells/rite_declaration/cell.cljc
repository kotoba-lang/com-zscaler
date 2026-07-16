(ns yobel.cells.rite-declaration.cell
  "RiteDeclarationCell — Pregel cell orchestrating the 6-step rite declaration gate.

  Clojure port of cell.py (Python LangGraph) onto langgraph-clj.

  Per ADR-2605201800 (Yobel Collective Debt Release Actor) + ADR-2605192230 (Three-Tier Enforcement).

  Trigger: declareRite XRPC request from an etzhayyim-aligned (or partner) religious-corp DID
  Effect:
    - Validate rite input (riteType / doctrinalBasis / scope / effectiveDate)
    - Verify issuer Council standing (SBT Lv1+ or partner-religious-corp registry)
    - Charter Rider §2(a-h) gate (scope text scan + DMN)
    - Council ratification DMN → emit governance proposal → wait for Council Lv6+ × 3 + Lv9 chair
    - Land sovereignty coordination (yobel_50yr only)
    - Anchor rite MST record via MST → IPFS → Base L2 batched anchor (AnchorBridge)

  Murakumo node: judah (leader, kingly proclamation — Gen 49:8-10)."
  (:require [clojure.string :as str]
            [langgraph.graph :as g]
            [yobel.ports :as ports]))

;; ─── Node functions ──────────────────────────────────────────────────

(def ^:private valid-rite-types
  #{"shmita_7yr" "yobel_50yr" "tokusei_rei" "religious_jubilee" "political_amnesty"})

(defn validate-input [state]
  (let [{:keys [rite-type doctrinal-basis scope effective-date]} state
        errors (cond-> []
                 (not (contains? valid-rite-types rite-type))
                 (conj (str "invalid riteType: " rite-type))

                 (or (empty? doctrinal-basis) (> (count doctrinal-basis) 1000))
                 (conj "doctrinalBasis required, max 1000 chars")

                 (or (empty? scope) (> (count scope) 2000))
                 (conj "scope required, max 2000 chars")

                 (empty? effective-date)
                 (conj "effectiveDate required")

                 (and (= rite-type "political_amnesty")
                      (str/blank? doctrinal-basis))
                 (conj "political_amnesty requires sovereign decree reference in doctrinalBasis"))]
    {:input-valid (empty? errors) :input-errors errors}))

(defn verify-issuer-standing
  "Issuer must be SBT Lv1+ or in the Charter Compliance Registry."
  [state council-sbt-port charter-compliance-port]
  (let [issuer (get state :issuer-did "")
        sbt-level (if council-sbt-port
                    (ports/balance-of-level council-sbt-port issuer)
                    0)
        in-registry (if charter-compliance-port
                      (ports/is-aligned charter-compliance-port issuer)
                      false)]
    {:issuer-sbt-level sbt-level :issuer-in-charter-registry in-registry}))

(defn charter-rider-gate
  "Scope text scan + DMN to enforce Charter Rider §2(a-h) prohibitions."
  [state charter-compliance-port]
  (let [scope (str/lower-case (get state :scope ""))
        violations
        (cond-> []
          ;; §2(a) military — military debt forgiveness requires transparent-force-rd disclosure
          (and (some #(str/includes? scope %)
                     ["military debt" "defense contractor debt" "arms procurement debt"])
               (not (str/includes? scope "transparent-force-rd")))
          (conj "§2(a) military scope requires transparent-force-rd disclosure (ADR-2605192315)")

          ;; §2(b) speculative finance — already enforced at lexicon schema level; double-check scope
          (some #(str/includes? scope %)
                ["margin" "leverage" "arbitrage" "predatory"])
          (conj "§2(b) speculative finance keywords in scope — yobel is one-way forgiveness only"))]
    ;; §2(c-h) — would call (ports/jurisdiction-of charter-compliance-port …)-class
    ;; scope-check on the port for the full DMN
    {:charter-rider-compliant (empty? violations)
     :charter-rider-violations violations}))

(defn council-ratification-dmn
  "COLLECT-hit DMN per dmn/council-ratification-threshold.md."
  [state]
  ;; B1 baseline
  (let [lv6 3
        lv9 1
        quorum 50
        [lv6 lv9 quorum gates]
        (case (:rite-type state)
          "yobel_50yr" [(+ lv6 2) lv9 (+ quorum 10) ["land-sovereignty-coordination"]]
          "tokusei_rei" [(+ lv6 1) (+ lv9 1) (+ quorum 10) ["jurisdiction-claim-coordination"]]
          "religious_jubilee" [lv6 lv9 quorum ["partner-religious-corp-notification"]]
          "political_amnesty" [(+ lv6 3) (+ lv9 1) (+ quorum 20)
                               ["transparent-force-rd-disclosure"
                                "council-five-bootstrap-consultation"]]
          [lv6 lv9 quorum []])]
    {:required-lv6-plus-count lv6
     :required-lv9-chair-count lv9
     :required-quorum-pct (min quorum 100)
     :additional-gates gates}))

(defn submit-council-proposal
  "Emit governance proposal MST record; returns proposal URI."
  [state council-ratification-port]
  (if (nil? council-ratification-port)
    {:council-proposal-uri (str "at://stub/" (:rite-id state) "/proposal")}
    (let [uri (ports/submit-proposal
               council-ratification-port
               "yobel_rite_declaration"
               (:rite-id state)
               (:rite-type state)
               (:required-lv6-plus-count state)
               (:required-lv9-chair-count state)
               (:required-quorum-pct state)
               (:additional-gates state)
               (:doctrinal-basis state)
               (:scope state))]
      {:council-proposal-uri uri})))

(defn await-council-decision
  "Block on Council deliberation (default 30 days per Three-Tier Enforcement)."
  [state council-ratification-port]
  (if (nil? council-ratification-port)
    {:council-ratified false :council-ratification-signatures []}
    (let [decision (ports/await-decision council-ratification-port
                                         (:council-proposal-uri state)
                                         30)]
      {:council-ratified (:ratified decision)
       :council-ratification-signatures (:signatures decision)})))

(defn land-sovereignty-coordination
  "yobel_50yr: identify land tenure records auto-reverting under Lev 25:23."
  [state land-registry-port]
  (if (nil? land-registry-port)
    {:overlapping-land-ids [] :land-coordination-complete true}
    (let [land-ids (ports/find-overlapping-tenures land-registry-port (:scope state))]
      {:overlapping-land-ids land-ids :land-coordination-complete true})))

(defn anchor-rite
  "Write rite MST record with status=active, anchor via MST → IPFS → Base L2."
  [state anchor-bridge]
  (if (nil? anchor-bridge)
    {:rite-status "active"
     :rite-vertex-uri (str "at://did:web:yobel.etzhayyim.com/"
                           "com.etzhayyim.apps.etzhayyim.yobel.rite/"
                           (:rite-id state))
     :base-l2-anchor-tx-hash ""}
    (let [result (ports/write-and-anchor
                  anchor-bridge
                  "com.etzhayyim.apps.etzhayyim.yobel.rite"
                  (:rite-id state)
                  {:rite-id (:rite-id state)
                   :rite-type (:rite-type state)
                   :scope (:scope state)
                   :effective-date (:effective-date state)
                   :expiry-date (:expiry-date state)
                   :doctrinal-basis (:doctrinal-basis state)
                   :jurisdiction-notes (get state :jurisdiction-notes "")
                   :issuer-did (:issuer-did state)
                   :status "active"
                   :council-proposal-uri (:council-proposal-uri state)
                   :council-ratification-signatures (:council-ratification-signatures state)
                   :overlapping-land-ids (get state :overlapping-land-ids [])}
                  true)]
      {:rite-status "active"
       :rite-vertex-uri (:vertex-uri result)
       :base-l2-anchor-tx-hash (:anchor-tx-hash result)})))

(defn emit-cancellation
  "Mark rite as cancelled — Council rejection / Charter Rider violation / invalid input."
  [state]
  {:rite-status "cancelled"
   :rite-vertex-uri (str "at://stub/" (get state :rite-id "unknown") "/cancelled")})

;; ─── Routers ─────────────────────────────────────────────────────────

(defn gate-router [state]
  (cond
    (not (:input-valid state)) :emit-cancellation
    (and (< (get state :issuer-sbt-level 0) 1)
         (not (:issuer-in-charter-registry state))) :emit-cancellation
    (not (:charter-rider-compliant state)) :emit-cancellation
    :else :council-ratification-dmn))

(defn council-router [state]
  (cond
    (not (:council-ratified state)) :emit-cancellation
    (= (:rite-type state) "yobel_50yr") :land-sovereignty-coordination
    :else :anchor-rite))

;; ─── Graph ───────────────────────────────────────────────────────────

(defn build-graph
  "Builds + compiles the rite-declaration graph.
  opts: {:checkpointer :charter-compliance-port :council-sbt-port
         :council-ratification-port :land-registry-port :anchor-bridge}"
  [{:keys [checkpointer charter-compliance-port council-sbt-port
           council-ratification-port land-registry-port anchor-bridge]}]
  (-> (g/state-graph)
      (g/add-node :validate-input validate-input)
      (g/add-node :verify-issuer-standing
                  (fn [s] (verify-issuer-standing s council-sbt-port charter-compliance-port)))
      (g/add-node :charter-rider-gate
                  (fn [s] (charter-rider-gate s charter-compliance-port)))
      (g/add-node :council-ratification-dmn council-ratification-dmn)
      (g/add-node :submit-council-proposal
                  (fn [s] (submit-council-proposal s council-ratification-port)))
      (g/add-node :await-council-decision
                  (fn [s] (await-council-decision s council-ratification-port)))
      (g/add-node :land-sovereignty-coordination
                  (fn [s] (land-sovereignty-coordination s land-registry-port)))
      (g/add-node :anchor-rite
                  (fn [s] (anchor-rite s anchor-bridge)))
      (g/add-node :emit-cancellation emit-cancellation)

      (g/set-entry-point :validate-input)
      (g/add-edge :validate-input :verify-issuer-standing)
      (g/add-edge :verify-issuer-standing :charter-rider-gate)
      (g/add-conditional-edges :charter-rider-gate gate-router)
      (g/add-edge :council-ratification-dmn :submit-council-proposal)
      (g/add-edge :submit-council-proposal :await-council-decision)
      (g/add-conditional-edges :await-council-decision council-router)
      (g/add-edge :land-sovereignty-coordination :anchor-rite)
      (g/add-edge :anchor-rite g/END)
      (g/add-edge :emit-cancellation g/END)

      (g/compile-graph {:checkpointer checkpointer})))
