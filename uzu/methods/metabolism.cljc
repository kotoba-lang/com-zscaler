#!/usr/bin/env bb
;; uzu 渦 — the dissipative loop: perceive → infer → plan → act → metabolize → live-or-die.
(ns uzu.methods.metabolism
  "metabolism.cljc — uzu 渦 heartbeat: the coupling of information and energy into one
  dissipative cycle (ADR-2606211500).

  One beat closes the loop the design describes:
      perceive(s) → infer(μ, information) → plan(EFE, information)
                  → act + metabolize(energy) → next perception
  Inference and planning live in model.cljc (nats); metabolizing lives in ledger.cljc
  (energy units); this namespace only WIRES them — the action chosen by minimizing
  expected free energy (information) is charged to the energy ledger (the coupling f),
  and intake is drawn from the TRUE regime (the world bends back). A dead organism does
  not beat. `datoms` renders the beat history to EAVT for the append-only kotoba log
  (information; copyable; non-conserved) — kept distinct from the energy balance
  (conserved; depleting)."
  (:require [uzu.methods.model :as model]
            [uzu.methods.ledger :as ledger]
            [clojure.string :as str]))

(defn init-organism
  "Build a fresh organism state from a config map
   {:id :prefs {:nutrient :threat} :temp :energy0 :prior :costs}."
  [{:keys [id prefs temp energy0 prior costs]}]
  {:id id
   :prefs (or prefs {:nutrient 1.0 :threat 0.0})
   :temp (or temp 0.15)
   :belief (or prior model/uniform)
   :energy (double (or energy0 12.0))
   :costs (or costs ledger/default-costs)
   :age 0
   :alive? true
   :born-energy (double (or energy0 12.0))
   :history []})

(defn- round3 [x] (/ (Math/round (* (double x) 1000.0)) 1000.0))

(defn beat
  "Advance one organism through one world-step
   {:regime <true hidden regime> :signal {:nutrient :threat}}.
   A dead organism is returned unchanged (its loop has stopped)."
  [state world-step]
  (if-not (:alive? state)
    state
    (let [obs   (:signal world-step)
          temp  (:temp state)
          C     (:prefs state)
          ;; INFER (information): minimize variational free energy
          vfe   (model/free-energy (:belief state) obs temp)
          q'    (model/update-belief (:belief state) obs temp)
          ;; PLAN (information): minimize expected free energy, vetoed by affordability (energy)
          afford (ledger/affordable (:energy state) (:costs state))
          action (model/choose q' C afford)
          ;; ACT + METABOLIZE (energy): pay, and draw intake from the TRUE regime
          m     (ledger/metabolize (:energy state) (:costs state) action (:regime world-step))
          rec   {:age (:age state)
                 :regime (:regime world-step)
                 :obs obs
                 :vfe (round3 vfe)
                 :belief-of (model/most-likely q')
                 :belief-entropy (round3 (model/entropy q'))
                 :action action
                 :gained (round3 (:gained m))
                 :spent (round3 (:spent m))
                 :hazard (round3 (:hazard m))
                 :energy (round3 (:e' m))
                 :alive? (:alive? m)}]
      (-> state
          (assoc :belief q'
                 :energy (:e' m)
                 :age (inc (:age state))
                 :alive? (:alive? m)
                 :last rec)
          (update :history conj rec)))))

(defn live
  "Run an organism config across the whole world tape, returning the final state
  (with full :history). Determinism: the tape IS the world (no Math/random)."
  [organism-config tape]
  (reduce beat (init-organism organism-config) tape))

(defn live-epochs
  "Live the tape `epochs` times in sequence (seasons), carrying energy and belief across
  the seam — the organism is born once, not reset each pass. A dead organism stays dead
  (its loop does not advance), so over a NET-NEGATIVE world even a well-fitted organism
  eventually starves: self-maintenance needs a net-positive niche, not merely a good model.
  Deterministic: the repeated tape IS the world (no Math/random)."
  [organism-config tape epochs]
  (reduce beat (init-organism organism-config)
          (apply concat (repeat (max 1 epochs) tape))))

(defn summary
  "Compact survival summary for one lived organism."
  [s]
  {:id (:id s)
   :alive? (:alive? s)
   :final-energy (round3 (:energy s))
   :lifespan (count (filter :alive? (:history s)))
   :beats (count (:history s))
   :final-belief-of (model/most-likely (:belief s))
   :actions (frequencies (map :action (:history s)))})

;; ── EAVT datom emission (information log; copyable; non-conserved) ────────────
(defn datoms
  "Render one lived organism to EAVT datoms for the append-only kotoba log.
  Values are JSON-serializable (numbers / ':keyword' strings / bools). Energy and
  free-energy are DISTINCT attributes — the log never conflates the conserved energy
  balance with the informational free energy."
  [s]
  (let [org (str "uzu:organism/" (:id s))
        sum (summary s)
        org-ds [[":db/add" org ":uzu.organism/id" (:id s)]
                [":db/add" org ":uzu.organism/alive" (:alive? sum)]
                [":db/add" org ":uzu.organism/final-energy" (:final-energy sum)]
                [":db/add" org ":uzu.organism/lifespan" (:lifespan sum)]
                [":db/add" org ":uzu.organism/beats" (:beats sum)]
                [":db/add" org ":uzu.organism/final-belief" (str (:final-belief-of sum))]
                [":db/add" org ":uzu/derived" true]
                [":db/add" org ":uzu/sourcing" ":synthetic"]]
        beat-ds (mapcat
                 (fn [r]
                   (let [e (str "uzu:beat/" (:id s) "/" (:age r))]
                     [[":db/add" e ":uzu.beat/of" (:id s)]
                      [":db/add" e ":uzu.beat/age" (:age r)]
                      [":db/add" e ":uzu.beat/regime" (str (:regime r))]
                      [":db/add" e ":uzu.beat/action" (str (:action r))]
                      [":db/add" e ":uzu.beat/belief-of" (str (:belief-of r))]
                      [":db/add" e ":uzu.beat/free-energy" (:vfe r)]      ;; nats (information)
                      [":db/add" e ":uzu.beat/energy" (:energy r)]        ;; energy units (conserved)
                      [":db/add" e ":uzu.beat/gained" (:gained r)]
                      [":db/add" e ":uzu.beat/spent" (:spent r)]
                      [":db/add" e ":uzu.beat/alive" (:alive? r)]
                      [":db/add" e ":uzu/derived" true]]))
                 (:history s))]
    (vec (concat org-ds beat-ds))))
