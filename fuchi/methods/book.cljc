(ns fuchi.methods.book
  "book.cljc — 扶持 (fuchi) R1(c): toritate booking + kanae-renderable flow viz. 1:1 Clojure
  port of `methods/book.py` (ADR-2606052300).

  1. book-toritate — projects each IN-KIND rail into a toritate ledgerEntry using toritate's
     own category enum (housing/food/energy → subsistence-flow; compute/tooling → vocation-flow;
     care → care-flow). cashStipendUsd ≡ 0; payroll/salary/wage unrepresentable. The
     member-principal liquidity rail is NOT booked as income.
  2. flow-graph — emits a kanae-renderable Sankey-ready internal sustenance flow:
     Public Fund → 扶持 → each provider → maintainer. The liquidity leg is in-kind false.

  House style: ':…' strings stay strings; pure fns; closed-vocab → ex-info. Portable .cljc."
  (:require [fuchi.methods.live-gate :as live-gate]))

;; rail kind → toritate ledgerEntry category. liquidity-warifu is ABSENT (not a disbursement).
(def RAIL-TO-CATEGORY
  (array-map
   "housing-commons"  "subsistence-flow"
   "food-mitsuho"     "subsistence-flow"
   "energy-hikari"    "subsistence-flow"
   "compute-murakumo" "vocation-flow"
   "tooling-okaimono" "vocation-flow"
   "care-iyashi"      "care-flow"))
(def TORITATE-CATEGORIES #{"subsistence-flow" "vocation-flow" "care-flow" "liberation-flow" "grant"})
(def FORBIDDEN-CATEGORIES #{"payroll" "salary" "wage" "bonus" "commission"})

(def PUBLIC-FUND "did:web:etzhayyim.com:actor:etzhayyim-public-fund")
(def FUCHI "did:web:etzhayyim.com:actor:fuchi")

(defn make-ledger-entry
  "LedgerEntry dataclass equivalent. __post_init__: cash≡0 (G2), no payroll/wage, valid cat."
  [{:keys [alloc-id category imputed-usd-micros-yr counterparty-did cash-usd-micros]
    :or {cash-usd-micros 0}}]
  (when (not= cash-usd-micros 0)
    (throw (ex-info "cash≡0 INVARIANT (G2): toritate cashStipendUsd ≡ 0" {})))
  (when (contains? FORBIDDEN-CATEGORIES category)
    (throw (ex-info (str "category '" category "' unrepresentable (no payroll/wage)") {})))
  (when-not (contains? TORITATE-CATEGORIES category)
    (throw (ex-info (str "category '" category "' not a toritate ledgerEntry category") {})))
  {:alloc-id alloc-id :category category :imputed-usd-micros-yr imputed-usd-micros-yr
   :counterparty-did counterparty-did :cash-usd-micros 0})

(defn make-flow-edge
  [{:keys [frm to flow-class imputed-usd-micros-yr in-kind] :or {in-kind true}}]
  {:frm frm :to to :flow-class flow-class
   :imputed-usd-micros-yr imputed-usd-micros-yr :in-kind in-kind})

(defn- rail-fields
  "Mirror of _rail_fields — returns [kind imputed member-principal provider]."
  [r]
  (let [kind (or (:kind r) (get r "kind"))
        imputed (long (or (:imputed-usd-micros-yr r)
                          (get r "imputedUsdMicrosYr") (get r "imputed_usd_micros_yr") 0))
        member-principal (let [v (:member-principal r)]
                           (if (nil? v)
                             (boolean (or (get r "memberPrincipal") (get r "member_principal") false))
                             v))
        provider (or (:provider-actor r) (get r "providerActor") (get r "provider_actor") kind)]
    [kind imputed (boolean member-principal) provider]))

(defn book-toritate
  "Project in-kind rails into toritate ledgerEntry records. Skips the member-principal
  liquidity rail (not a Public-Fund disbursement)."
  [rails alloc-id maintainer-did]
  (reduce
   (fn [out r]
     (let [[kind imputed member-principal _provider] (rail-fields r)]
       (if (or member-principal (not (contains? RAIL-TO-CATEGORY kind)))
         out
         (conj out (make-ledger-entry
                    {:alloc-id alloc-id :category (get RAIL-TO-CATEGORY kind)
                     :imputed-usd-micros-yr imputed :counterparty-did maintainer-did})))))
   [] rails))

(defn flow-graph
  "Emit a kanae-renderable internal sustenance-flow graph for this allocation."
  [rails alloc-id maintainer-did]
  (let [[legs in-kind-total]
        (reduce
         (fn [[legs total] r]
           (let [[kind imputed member-principal provider] (rail-fields r)
                 in-kind (not member-principal)]
             [(conj legs
                    (make-flow-edge {:frm FUCHI :to provider :flow-class "fuchi-to-provider"
                                     :imputed-usd-micros-yr imputed :in-kind in-kind})
                    (make-flow-edge {:frm provider :to maintainer-did
                                     :flow-class "provider-to-maintainer"
                                     :imputed-usd-micros-yr imputed :in-kind in-kind}))
              (if in-kind (+ total imputed) total)]))
         [[] 0] rails)]
    (vec (concat
          (when (> in-kind-total 0)
            [(make-flow-edge {:frm PUBLIC-FUND :to FUCHI :flow-class "publicfund-to-fuchi"
                              :imputed-usd-micros-yr in-kind-total :in-kind true})])
          legs))))

(defn write-live
  "Authorize a LIVE write of the ledgerEntry projection into toritate. require-gate passes
  (R2 autonomous); cash≡0 holds on every entry."
  [entries gate & {:keys [env]}]
  (live-gate/require-gate gate env)
  (doseq [e entries]
    (when (not= (:cash-usd-micros e) 0)
      (throw (ex-info "cash≡0 INVARIANT (G2) holds in live mode too" {}))))
  {:entries (vec entries) :operator-did (:operator-did gate)
   :council-level (:council-level gate) :member-signature (:member-signature gate)
   :committed true})
