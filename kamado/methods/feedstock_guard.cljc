(ns kamado.methods.feedstock-guard
  "kamado 竈 — feedstock-class guard (G1 enforcement point #3 of 3).
  1:1 Clojure port of `methods/feedstock_guard.py` (ADR-2606051500).

  The structural invariant, mirrored on nusa's `:thc-class` guard: a refining feedstock
  MUST be closed-loop carbon. `:fossil-virgin-crude` is NOT a representable value — the
  schema enum, the lexicon `const`, and this guard all refuse it, so kamado cannot, by
  construction, operate a fossil-fed refinery (which is what makes \"automate a fossil
  refinery\" impossible rather than merely discouraged).

  This is the honest answer made structural: since robotics cannot neutralize fossil carbon
  (carbon_balance.cljc), the only harm-free path is to forbid the fossil feedstock.

  House style: Python ':…' keyword strings stay strings; pure fns; closed-vocab/gate
  violations → ex-info (the Python ValueError edge). Portable .cljc."
  (:require [clojure.string :as str]))

;; G1: the ONLY representable feedstock classes. Anything else is a charter violation.
(def ALLOWED-FEEDSTOCK
  [":biogenic" ":captured-co2" ":recycled-carbon" ":existing-inventory-decommission"])

;; G3: the ONLY representable intervention kinds on an EXISTING fossil asset. Life-extension
;; of a fossil unit (:expand / :restart-fossil / :revamp-throughput) is unrepresentable.
(def ALLOWED-INTERVENTION
  [":decommission" ":remediate" ":convert" ":monitor"])

(defn- norm
  "Python _norm: (v or '').lstrip(':') if isinstance(v,str) else str(v)."
  [v]
  (cond
    (string? v) (let [v (if (str/blank? v) "" v)]
                  ;; (v or "") — empty string is falsy in Python → ""
                  (if (= v "") "" (str/replace v #"^:+" "")))
    (nil? v) ""                         ;; (None or "") → ""
    (false? v) ""                       ;; (False or "") → ""
    :else (str v)))

(defn- in-vec? [coll x] (some #(= % x) coll))

(defn- repr-str
  "Python repr() of the offending value for the error message: 'x' for a string, else str."
  [v]
  (cond
    (string? v) (str "'" v "'")
    (nil? v) "None"
    (true? v) "True"
    (false? v) "False"
    :else (str v)))

(defn- vec->py-tuple
  "Render a vector of ':…' strings as a Python tuple repr, e.g. (':biogenic', ':captured-co2')."
  [coll]
  (str "(" (str/join ", " (map #(str "'" % "'") coll)) ")"))

(defn screen-feedstock
  "G1: refuse any feedstock that is not closed-loop carbon. Returns the keyword string."
  ([feedstock] (screen-feedstock feedstock ""))
  ([feedstock ctx]
   (let [fk (if (and (string? feedstock) (str/starts-with? feedstock ":"))
              feedstock
              (str ":" (norm feedstock)))]
     (when-not (in-vec? ALLOWED-FEEDSTOCK fk)
       (throw (ex-info
               (str "G1 violation" (when (seq ctx) (str " (" ctx ")")) ": feedstock-class "
                    (repr-str feedstock) " is not representable. kamado refines closed-loop "
                    "carbon ONLY " (vec->py-tuple ALLOWED-FEEDSTOCK) "; `:fossil-virgin-crude` "
                    "and any fossil-extracted feedstock are excluded by construction (robotics "
                    "cannot neutralize fossil carbon — only the feedstock can).")
               {:gate "G1" :feedstock feedstock :ctx ctx})))
     fk)))

(defn screen-intervention
  "G3: an EXISTING fossil asset may only be wound down/converted, never extended."
  ([kind] (screen-intervention kind ""))
  ([kind ctx]
   (let [ik (if (and (string? kind) (str/starts-with? kind ":"))
              kind
              (str ":" (norm kind)))]
     (when-not (in-vec? ALLOWED-INTERVENTION ik)
       (throw (ex-info
               (str "G3 violation" (when (seq ctx) (str " (" ctx ")")) ": intervention "
                    (repr-str kind) " is not representable. kamado may only "
                    (vec->py-tuple ALLOWED-INTERVENTION) " an existing fossil asset "
                    "(§2(d) — decommission/transition only; never expand/restart/extend a "
                    "fossil unit).")
               {:gate "G3" :kind kind :ctx ctx})))
     ik)))
