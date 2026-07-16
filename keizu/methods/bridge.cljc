(ns keizu.methods.bridge
  "bridge.cljc — 系図 (keizu) cross-actor compose: danjo + kanae → keizu :rel/:money. ADR-2606066000.
  1:1 Clojure port of `methods/bridge.py` (same house style as weave/export).

  keizu sits atop its siblings: it can compose **danjo** cross-reference links and
  **kanae** fiscal-flow edges into its own relation graph. This bridge is a PURE mapping +
  validation step — offline only; live sibling ingest is G8-gated.

  The load-bearing property: every imported record is run through keizu's OWN gates
  (`weave.validate_rel` / `validate_money`), so a sibling CANNOT smuggle a charter violation into
  keizu. A danjo category that reads like a verdict, or a kanae edge with <2 sources, is REFUSED at
  the import boundary — defense in depth for G2 (non-adjudicating) and G3 (≥2 sources).

  House style: Python ':…' keyword strings stay strings (incl. all :*/* attrs); validation /
  closed-vocab / gate violations throw ex-info; pure fns; file I/O only at the #?(:clj) edge."
  (:require [clojure.string :as str]
            [keizu.methods.weave :as w]))

;; `_kw` is private (`kw*`) in weave.cljc — reach it via the var (Python `from weave import _kw`).
(def ^:private kw* @#'w/kw*)

;; kanae fundFlowEdge flow types → keizu money kinds (factual disclosed flows only).
(def KANAE-FLOW-TO-KIND
  {"appropriation" "budget-outlay"
   "outlay" "budget-outlay"
   "subaward" "subsidy"
   "subsidy" "subsidy"
   "grant" "grant"
   "aid" "grant"
   "transfer" "grant"
   "loan" "grant"
   "procurement" "procurement-award"
   "award" "procurement-award"})

;; danjo crossReferenceLink link types → keizu factual rel kinds. NOTE: danjo is itself
;; non-adjudicating, but the bridge re-asserts the gate (a verdict-ish category is refused).
(def DANJO-LINK-TO-KIND
  {"awardee-officer-ubo-link" "co-membership"
   "officer-edge" "co-membership"
   "appointment" "appointment"
   "advisory" "advisory-role"
   "revolving-door" "revolving-door"
   "donor-recipient" "funding-tie"
   "procurement-award" "procurement-award"
   "statement-attribution" "statement-attribution"})

(defn- err [msg] (throw (ex-info msg {})))

(defn- to-float
  "Python float(x) — tolerant of numbers and numeric strings."
  [v]
  (cond
    (nil? v) 0.0
    (number? v) (double v)
    (string? v) #?(:clj (Double/parseDouble v) :cljs (js/parseFloat v))
    :else (err "amount must be a number")))

(defn- to-int
  "Python int(x) — truncates toward zero."
  [v]
  (cond
    (nil? v) 0
    (integer? v) (long v)
    (number? v) (long v)
    (string? v) #?(:clj (long (Double/parseDouble v)) :cljs (long (js/parseFloat v)))
    :else 0))

(defn bridge-kanae-flow
  "kanae fundFlowEdge → validated keizu :money datom. Raises on an unknown flow type or a
  keizu-gate violation (G2/G3)."
  [edge]
  (let [flow (kw* (or (get edge "flowType") (get edge ":flowType") ""))]
    (when-not (contains? KANAE-FLOW-TO-KIND flow)
      (err (str "bridge: unknown kanae flowType " (pr-str flow) " — refuse to guess (sourcing-honesty)")))
    (let [sources (vec (filter #(seq (str/trim (str %)))
                               (or (get edge "sources") (get edge "sourceCids") [])))
          m {":money/id" (str "kanae:" (str (or (get edge "id") (get edge "edgeId") "?")))
             ":money/payer" (or (get edge "donor") (get edge "from") "")
             ":money/payee" (or (get edge "recipient") (get edge "to") "")
             ":money/kind" (str ":" (get KANAE-FLOW-TO-KIND flow))
             ":money/amount" (to-float (get edge "amount" 0.0))
             ":money/currency" (get edge "currency" "")
             ":money/as-of" (to-int (get edge "asOf" 0))
             ":money/sourcing" ":representative"
             ":money/sources" sources}]
      (w/validate-money m)
      m)))

(defn bridge-danjo-crossref
  "danjo crossReferenceLink → validated keizu :rel datom. A verdict-bearing category is
  refused (G2 defense in depth); an under-sourced link is refused (G3)."
  [link]
  (let [raw-kind (kw* (or (get link "linkType") (get link "category") (get link "kind") ""))]
    (when (some #(= % raw-kind) w/VERDICT-TOKENS)
      (err (str "bridge: danjo category " (pr-str raw-kind) " is a verdict — refused at import (G2)")))
    (when-not (contains? DANJO-LINK-TO-KIND raw-kind)
      (err (str "bridge: unmapped danjo link type " (pr-str raw-kind) " — refuse to guess")))
    (let [sources (vec (filter #(seq (str/trim (str %)))
                               (or (get link "sourceRecordCids") (get link "sources") [])))
          r {":rel/id" (str "danjo:" (str (or (get link "id") (get link "linkId") "?")))
             ":rel/source" (or (get link "from") (get link "source") "")
             ":rel/target" (or (get link "to") (get link "target") "")
             ":rel/kind" (str ":" (get DANJO-LINK-TO-KIND raw-kind))
             ":rel/weight" (to-float (get link "weight" 1.0))
             ":rel/as-of" (to-int (get link "asOf" 0))
             ":rel/non-adjudicating-notice" true
             ":rel/sourcing" ":representative"
             ":rel/sources" sources}]
      (w/validate-rel r)
      r)))

(defn bridge-batch
  "Compose a mixed sibling batch → keizu datoms. Each record validated; the whole batch
  fails if any record violates a keizu gate (no partial smuggling)."
  [batch]
  {"rels" (vec (map bridge-danjo-crossref (get batch "danjo" [])))
   "money" (vec (map bridge-kanae-flow (get batch "kanae" [])))})
