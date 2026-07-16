(ns nusa.cells.fiber-provenance.state-machine
  "Phase state machine for the nusa 幣 fiber_provenance cell.
  1:1 port of cells/fiber_provenance/state_machine.py (ADR-2606039800).

  The defining G1 cell: a cultivar becomes a fibre-use provenance record ONLY after the THC-class
  screen passes (third enforcement of :thc-class after schema + lexicon const).
  G1 — :thc-class ∈ {fiber, low-thc}, else raise BEFORE a record exists. G2 — fibre uses only,
  never a consumption mode. Conventions: dataclass → plain map (Python string keys); ValueError → ex-info."
  (:require [clojure.string :as str]))

(def allowed-thc-classes #{"fiber" "low-thc"})
(def allowed-fiber-uses
  #{"shimenawa" "haraegushi" "oonusa" "aratae" "textile" "rope" "paper" "seed-oil-food"})

(def provenance-phases {:init "init" :screened "screened" :recorded "recorded"})
(def phase-init     (:init provenance-phases))
(def phase-screened (:screened provenance-phases))
(def phase-recorded (:recorded provenance-phases))

(def state-defaults
  {"phase"     phase-init
   "cultivar"  ""
   "thc_class" "fiber"
   "fiber_use" []
   "region"    []
   "screened"  false
   "sourcing"  "representative"
   "payload"   {}})

(defn- cell-state [state] (merge state-defaults (get state "cell_state" {})))
(defn- norm [v] (str/replace (str (or v "")) #"^:+" ""))

(defn transition-to-screened
  "G1: THC-class screen. Raises on any non-fibre/low-THC cultivar; G2 rejects non-fibre uses."
  [state]
  (let [cs (cell-state state)
        cs (assoc cs "cultivar" (get state "cultivar" (get cs "cultivar")))
        cls (norm (get state "thc_class" (get cs "thc_class")))]
    (when-not (contains? allowed-thc-classes cls)
      (throw (ex-info (str "G1 violation: cultivar " (pr-str (get cs "cultivar")) " has thc-class " (pr-str cls)
                           "; only " (pr-str (vec (sort allowed-thc-classes))) " permitted. nusa is fibre/ritual-only "
                           "— recreational/high-THC is excluded by construction (no provenance record produced).")
                      {:gate "G1"})))
    (let [fiber-use (vec (get state "fiber_use" (get cs "fiber_use")))]
      (doseq [u fiber-use]
        (when-not (contains? allowed-fiber-uses (norm u))
          (throw (ex-info (str "G2 violation: fibre-use " (pr-str u) " is not a permitted fibre/ritual use "
                               (pr-str (vec (sort allowed-fiber-uses))) "; nusa emits no consumption/intoxication use.")
                          {:gate "G2"}))))
      {"cell_state" (assoc cs "thc_class" cls "fiber_use" fiber-use
                           "region" (vec (get state "region" (get cs "region")))
                           "screened" true "phase" phase-screened)})))

(defn transition-to-recorded
  "Materialize the provenance record (only reachable after the screen passes)."
  [state]
  (let [cs (cell-state state)]
    (when-not (get cs "screened")
      (throw (ex-info "provenance record requires a passed THC-class screen first (G1)" {:gate "G1"})))
    {"cell_state" (assoc cs "phase" phase-recorded
                         "payload" {"cultivar" (get cs "cultivar")
                                    "thcClass" (get cs "thc_class")
                                    "fiberUse" (get cs "fiber_use")
                                    "region" (get cs "region")
                                    "screened" true
                                    "sourcing" (get cs "sourcing")})}))

(defn solve
  "R0 scaffold: .solve() raises until Council activation (ADR-2606039800 §Decision)."
  [_input-state]
  (throw (ex-info "nusa R0 scaffold: activate fiber_provenance via Council ADR (post-2606039800 ratification)"
                  {:scaffold true})))
