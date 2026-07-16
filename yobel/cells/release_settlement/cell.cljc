(ns yobel.cells.release-settlement.cell
  "ReleaseSettlementCell — Pregel cell executing per-pair debt release with one-way invariant + tax warning DMN.

  Per ADR-2605201800 §Decision + Charter Rider v2 §2(b) one-way enforcement.

  Trigger: joined (creditorEnrollment, debtorEnrollment) pair with eligible=true ∧ both anchored
  Effect:
    - Decrypt creditor debt item (per-pair wrapped key)
    - Run tax warning DMN (COLLECT hit, per-jurisdiction)
    - One-way boundary check: releasedMicroUsdc ≤ principal + accrued (§2(b) invariant)
    - Dispatch by releaseMethod (voluntary_bookkeeping / base_l2_transfer / court_order /
      sovereign_decree / ecclesiastical_indulgence)
    - Anchor release MST record + emit audit event

  Murakumo node: asher (blessed / abundance — Gen 49:20, Deut 33:24-25).

  Clojure port of cells/release_settlement/cell.py (langgraph-clj, portable .cljc)."
  (:require [clojure.string :as str]
            [langgraph.graph :as g]
            [yobel.ports :as ports]))

(def release-methods
  "Enum values stay strings unchanged from the Python/lexicon."
  #{"voluntary_bookkeeping"
    "base_l2_transfer"
    "court_order"
    "sovereign_decree"
    "ecclesiastical_indulgence"})

;; ─── Node functions ──────────────────────────────────────────────────

(defn load-pair
  "Decrypt debt item from creditor enrollment, populate principal + accrued amounts."
  [state creditor-enrollment-port envelope-crypto]
  (if (or (nil? creditor-enrollment-port) (nil? envelope-crypto))
    {:debt-principal-micro-usdc 0 :debt-accrued-micro-usdc 0}
    (let [debt (ports/get-debt-for-release creditor-enrollment-port
                                           (:rite-id state)
                                           (:creditor-did state)
                                           (:debt-id state)
                                           envelope-crypto)]
      (if (nil? debt)
        {:debt-principal-micro-usdc 0 :debt-accrued-micro-usdc 0}
        {:debt-principal-micro-usdc (:principal-micro-usdc debt)
         :debt-accrued-micro-usdc (or (:accrued-micro-usdc debt) 0)}))))

(defn- floor-div
  "Python `//` (floor-division) semantics for integers."
  [n d]
  (let [q (quot n d)
        r (rem n d)]
    (if (and (not (zero? r)) (neg? (* r d)))
      (dec q)
      q)))

(def ^:private severity-bump {"info" 0 "caution" 1 "high" 2})
(def ^:private rank->severity {0 "info" 1 "caution" 2 "high"})

(defn tax-warning-dmn
  "COLLECT-hit DMN per dmn/tax-warning-by-jurisdiction.md."
  [state]
  (let [released-usdc (floor-div (or (:released-micro-usdc state) 0) 1000000)
        debtor-jur (str/upper-case (or (:debtor-did state) ""))
        creditor-jur (str/upper-case (or (:creditor-did state) ""))
        method (:release-method state)
        add (fn [acc msg level]
              (-> acc
                  (update :warnings conj msg)
                  (update :rank max (severity-bump level))))
        acc {:warnings [] :rank 0}
        acc (if (and (str/includes? debtor-jur "USA") (>= released-usdc 1))
              (let [acc (add acc "US IRC §61(a)(11): cancellation-of-debt income generally taxable. Exclusions: §108(a)(1)(A-E). File Form 982 + creditor Form 1099-C." "caution")]
                (if (or (= method "ecclesiastical_indulgence")
                        (= method "voluntary_bookkeeping"))
                  (add acc "US IRC §170(c)(1): religious-org gifts may have different treatment than commercial debt forgiveness." "caution")
                  acc))
              acc)
        acc (cond-> acc
              (and (str/includes? debtor-jur "JPN") (>= released-usdc 1))
              (add "日本所得税法 §36(1) + §44-2: 債務免除益は原則として一時所得または雑所得。資力喪失中の免除は §44-2 適用で非課税の余地。" "caution")

              (and (str/includes? debtor-jur "DEU") (>= released-usdc 1))
              (add "Deutsches EStG §15 + §17: Schuldenerlass kann Betriebseinnahme darstellen. §3 Nr. 66 Sanierungsklausel applies in restructuring context only." "caution")

              (and (str/includes? debtor-jur "GBR") (>= released-usdc 1))
              (add "UK ITTOIA 2005 §249: release of debt deemed income if previously deductible. ESC C16 / SP D32 may apply." "caution")

              (and (str/includes? debtor-jur "FRA") (>= released-usdc 1))
              (add "Code général des impôts art. 39-1: abandon de créance commercial = recette imposable; religieux voluntary release: position fiscale incertaine." "caution")

              (and (str/includes? debtor-jur "ISR")
                   (contains? #{"shmita_7yr" "yobel_50yr"} (:rite-type state)))
              (add "Israel: prozbul (Hillel) historically routes around shmita debt cancellation; modern Pkudat Mas Hachnasa does not auto-recognize religious shmita as tax-exempt." "caution"))
        acc (cond
              (>= released-usdc 1000000)
              (add acc "Releases ≥ $1M USDC trigger anti-abuse / disguised-gift rules in many jurisdictions. Coordinate with vendor:lawfirm.etzhayyim.com before settlement." "high")

              (>= released-usdc 100)
              (add acc "Release amount may exceed gift tax annual exclusion in many jurisdictions. Verify jurisdiction-specific gift tax rules." "info")

              :else acc)
        acc (if (and (str/includes? debtor-jur "USA")
                     (str/includes? creditor-jur "USA")
                     (>= released-usdc 600))
              (add acc "US IRS Form 1099-C threshold (≥ $600). Creditor may have reporting obligation independent of yobel rite." "info")
              acc)]
    {:tax-warnings (:warnings acc)
     :tax-severity (rank->severity (:rank acc))
     :consult-legal-delegate (>= (:rank acc) 1)}))

(defn one-way-boundary-check
  "releasedMicroUsdc ≤ debt.principalMicroUsdc + debt.accruedMicroUsdc (Charter Rider §2(b))."
  [state]
  (let [released (or (:released-micro-usdc state) 0)
        cap (+ (or (:debt-principal-micro-usdc state) 0)
               (or (:debt-accrued-micro-usdc state) 0))]
    (cond
      (neg? released)
      {:one-way-compliant false
       :one-way-violation (str "negative release amount " released)}

      (> released cap)
      {:one-way-compliant false
       :one-way-violation (str "release " released " > debt cap " cap " (§2(b) one-way invariant)")}

      :else
      {:one-way-compliant true :one-way-violation ""})))

(defn execute-release
  "Dispatch by releaseMethod."
  [state tithe-router-port base-l2-paymaster lawfirm-invoke]
  (let [method (:release-method state)]
    (cond
      (= method "voluntary_bookkeeping")
      {:settlement-ok true :base-l2-tx-hash "" :lawfirm-invoke-uri ""}

      (= method "base_l2_transfer")
      (if (nil? base-l2-paymaster)
        {:settlement-ok true :base-l2-tx-hash "0xstub-tx-hash" :lawfirm-invoke-uri ""}
        (try
          (let [tx (ports/release-usdc base-l2-paymaster
                                       (:creditor-did state)
                                       (:debtor-did state)
                                       (:released-micro-usdc state)
                                       ;; rite releases are tithe-neutral per ADR-2605172100 + 2605192130
                                       true
                                       (:rite-id state))]
            {:settlement-ok true :base-l2-tx-hash (:hash tx) :lawfirm-invoke-uri ""})
          (catch #?(:clj Exception :cljs :default) e
            {:settlement-ok false
             :settlement-error (or (ex-message e) (str e))
             :base-l2-tx-hash ""
             :lawfirm-invoke-uri ""})))

      (= method "court_order")
      (if (nil? lawfirm-invoke)
        {:settlement-ok true :base-l2-tx-hash "" :lawfirm-invoke-uri "at://stub/lawfirm/court-order"}
        (let [result (ports/invoke lawfirm-invoke "runCourtOrderFiling" state)]
          {:settlement-ok (:ok result) :base-l2-tx-hash "" :lawfirm-invoke-uri (:invoke-uri result)}))

      (= method "sovereign_decree")
      (if (nil? lawfirm-invoke)
        {:settlement-ok true :base-l2-tx-hash "" :lawfirm-invoke-uri "at://stub/lawfirm/sovereign-decree"}
        (let [result (ports/invoke lawfirm-invoke "recordSovereignDecreeApplication" state)]
          {:settlement-ok (:ok result) :base-l2-tx-hash "" :lawfirm-invoke-uri (:invoke-uri result)}))

      (= method "ecclesiastical_indulgence")
      {:settlement-ok true :base-l2-tx-hash "" :lawfirm-invoke-uri ""}

      :else
      {:settlement-ok false :settlement-error (str "unknown release_method: " method)})))

(defn anchor-release
  [state anchor-bridge]
  (if (nil? anchor-bridge)
    {:release-vertex-uri (str "at://did:web:yobel.etzhayyim.com/com.etzhayyim.apps.etzhayyim.yobel.release/"
                              (or (:release-id state) ""))}
    (let [result (ports/write-and-anchor
                  anchor-bridge
                  "com.etzhayyim.apps.etzhayyim.yobel.release"
                  (:release-id state)
                  {:release-id (:release-id state)
                   :rite-id (:rite-id state)
                   :debt-id (:debt-id state)
                   :debtor-did (:debtor-did state)
                   :creditor-did (:creditor-did state)
                   :release-method (:release-method state)
                   :released-micro-usdc (:released-micro-usdc state)
                   :released-at (:released-at state)
                   :base-l2-tx-hash (or (:base-l2-tx-hash state) "")
                   :lawfirm-invoke-uri (or (:lawfirm-invoke-uri state) "")
                   :tax-warnings (or (:tax-warnings state) [])
                   :tax-severity (or (:tax-severity state) "info")}
                  true)]
      {:release-vertex-uri (:vertex-uri result)})))

(defn emit-audit-event
  [state audit-witness-emit]
  (if (nil? audit-witness-emit)
    state
    (do
      (ports/emit audit-witness-emit
                  "yobel.release_finalized"
                  {:rite-id (:rite-id state)
                   :release-id (:release-id state)
                   :released-micro-usdc (:released-micro-usdc state)
                   :release-method (:release-method state)
                   :severity (or (:tax-severity state) "info")
                   :base-l2-tx-hash (or (:base-l2-tx-hash state) "")})
      state)))

(defn emit-violation
  "One-way invariant violation — log to audit + reject release."
  [state]
  {:settlement-ok false
   :settlement-error (or (:one-way-violation state) "boundary violation")
   :release-vertex-uri ""})

;; ─── Graph ───────────────────────────────────────────────────────────

(defn boundary-router
  [state]
  (if-not (:one-way-compliant state)
    :emit-violation
    :execute-release))

(defn build-graph
  "Builds + compiles the release-settlement graph.

  opts: {:checkpointer .. :creditor-enrollment-port .. :envelope-crypto ..
         :tithe-router-port .. :base-l2-paymaster .. :lawfirm-invoke ..
         :anchor-bridge .. :audit-witness-emit ..}"
  [{:keys [checkpointer creditor-enrollment-port envelope-crypto tithe-router-port
           base-l2-paymaster lawfirm-invoke anchor-bridge audit-witness-emit]}]
  (-> (g/state-graph)
      (g/add-node :load-pair
                  (fn [s] (load-pair s creditor-enrollment-port envelope-crypto)))
      (g/add-node :tax-warning-dmn tax-warning-dmn)
      (g/add-node :one-way-boundary-check one-way-boundary-check)
      (g/add-node :execute-release
                  (fn [s] (execute-release s tithe-router-port base-l2-paymaster lawfirm-invoke)))
      (g/add-node :anchor-release
                  (fn [s] (anchor-release s anchor-bridge)))
      (g/add-node :emit-audit-event
                  (fn [s] (emit-audit-event s audit-witness-emit)))
      (g/add-node :emit-violation emit-violation)
      (g/set-entry-point :load-pair)
      (g/add-edge :load-pair :tax-warning-dmn)
      (g/add-edge :tax-warning-dmn :one-way-boundary-check)
      (g/add-conditional-edges :one-way-boundary-check boundary-router)
      (g/add-edge :execute-release :anchor-release)
      (g/add-edge :anchor-release :emit-audit-event)
      (g/add-edge :emit-audit-event g/END)
      (g/add-edge :emit-violation g/END)
      (g/compile-graph {:checkpointer checkpointer})))
