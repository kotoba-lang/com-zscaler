#!/usr/bin/env bb
;; uzu 渦 — energy ledger: the ENERGY half (conserved, depletes; NOT information).
(ns uzu.methods.ledger
  "ledger.cljc — uzu 渦 metabolic energy ledger (ADR-2606211500).

  This is the ENERGY side of the information-energy coupled organism, kept in a
  ledger SEPARATE from the information log (model.cljc / kotoba.cljc) so the two are
  coupled but never share a unit. Energy here is a CONSERVED, DEPLETING quantity:
  every beat the organism pays for merely existing (basal upkeep = the dissipation
  that keeps the information structure from decaying), pays to think (inference), and
  pays to act; it draws free energy back from the environment (`intake`). When the
  balance reaches zero the organism is dead — self-maintenance is not assumed, it is
  earned. That mortality is the selection pressure that makes a well-fitted generative
  model (model.cljc) matter: an organism whose beliefs/preferences misread the world
  spends without drawing and dies.

  Coupling map f: an action choice (information, from model/choose) ⇒ an energy cost
  here. Intake depends on the TRUE regime (the world), not the belief — you can only
  eat what is actually there, however you read it."
  (:require [clojure.string :as str]))

(def default-costs
  "The metabolic price list (energy units). basal+inference are paid every live beat;
  action cost is paid for the chosen action."
  {:basal     1.0    ;; upkeep: the cost of maintaining the structure against decay
   :inference 0.5    ;; the cost of one belief update — thinking is not free
   :action    {:rest 0.2 :forage 2.0 :flee 2.0 :explore 1.2}
   :hazard    {:hostile 8.0}   ;; environmental damage in a regime, unless mitigated
   :hazard-mitigated-by {:hostile :flee}})

(def intake-table
  "Free energy actually drawn from the environment, by TRUE regime × action. Foraging
  in an abundant world feeds; fleeing never feeds; a hostile world barely feeds."
  {:abundant {:forage 7.0 :rest 1.0 :explore 1.5 :flee 0.0}
   :benign   {:forage 4.0 :rest 0.8 :explore 1.0 :flee 0.0}
   :scarce   {:forage 1.2 :rest 0.5 :explore 0.5 :flee 0.0}
   :hostile  {:forage 2.0 :rest 0.0 :explore 0.3 :flee 0.0}})

(defn action-cost [costs action] (get-in costs [:action action] 0.0))

(defn intake [regime action] (get-in intake-table [regime action] 0.0))

(defn hazard
  "Environmental damage suffered this beat: the regime's hazard, unless the action
  mitigates it (e.g. fleeing a hostile regime)."
  [costs regime action]
  (let [h (get-in costs [:hazard regime] 0.0)
        mit (get-in costs [:hazard-mitigated-by regime])]
    (if (= action mit) 0.0 h)))

(defn alive? [e] (> (double e) 0.0))

(defn affordable
  "Which actions the organism can pay for given current energy `e`. basal+inference
  are unavoidable; an action is affordable when basal+inference+its-cost ≤ e. Always
  returns at least [:rest] — you rest even while starving (and may still die)."
  [e costs]
  (let [fixed (+ (:basal costs) (:inference costs))
        ok (->> (:action costs)
                (filter (fn [[_ c]] (<= (+ fixed c) (double e))))
                (mapv key))]
    (if (seq ok) ok [:rest])))

(defn metabolize
  "One energy step. Pays basal+inference+action cost and hazard, adds environmental
  intake (from the TRUE regime). Returns the full energy accounting + survival.
    e'  = e + intake − (basal + inference + action-cost + hazard)"
  [e costs action regime]
  (let [b (:basal costs) inf (:inference costs)
        ac (action-cost costs action)
        hz (hazard costs regime action)
        gain (intake regime action)
        spent (+ b inf ac hz)
        e' (- (+ (double e) gain) spent)]
    {:e (double e) :e' e' :gained gain :spent spent
     :basal b :inference inf :action-cost ac :hazard hz
     :alive? (alive? e')}))
