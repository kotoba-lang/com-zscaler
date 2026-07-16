#!/usr/bin/env bb
;; uzu 渦 — measure real-world energy flows in HONEST, SEPARATE units (never collapsed).
(ns uzu.methods.measure
  "measure.cljc — uzu 渦 real-world energy-flow measurement (ADR-2606211500).

  The organism engine (model/ledger/metabolism) is abstract; this namespace GROUNDS the
  'energy' idea in measured real-world flows — physical energy, the economy, information,
  and human attention/meaning (consciousness/philosophy) — treated as ONE open, coupled,
  dissipative system (the human + device + web + power-grid + food-web + social-meaning
  loop the design describes), and prepares it for visualization (viz.cljc).

  THE LOAD-BEARING DESIGN RULE (the design's central caveat, enforced here in code +
  tests): the four flow classes are measured in FOUR DIFFERENT UNITS and are NEVER summed
  across classes — information and energy are coupled but are not the same quantity in the
  same unit. `totals-by-class` sums only within a class; there is no cross-class total.
    :physical       watts (W)           — actual thermodynamic power; conserved
    :economic       USD / yr            — money as 'economic free energy'; a different flow
    :informational  bit / s             — bits/compute; free energy in nats, not joules
    :experiential   index ∈ 0..1        — attention / affect / meaning; SUBJECT-DEPENDENT,
                                          explicitly NOT joules (assigning J/meaning would be
                                          the 'philosophy soup' the design rejects)

  For VISUAL LAYOUT ONLY, a flow may carry a DISCLOSED, CONTESTABLE conversion to a common
  log scale (`visual-magnitude`). That conversion is flagged `:reference-only` and is never
  treated as a unit identity. The :experiential class has NO physical conversion BY DESIGN
  — refusing to convert meaning into joules is itself the honest statement.

  Magnitudes are real public aggregates (each flow carries a :source), :sourcing
  :representative. Live ingest from the observatory siblings (kasa compute/energy, kanjō
  financials, shionome capital flows, busshi commodities, hikari grid, spirit-in-physics
  霊性) is a G7-gated operator step — the loop here reads a local seed and does no network."
  (:require [clojure.string :as str]
            #?(:clj [clojure.edn :as edn])
            #?(:clj [clojure.java.io :as io])))

(def unit-classes
  "The four flow classes and their (incommensurable) units."
  {:physical      {:unit "W"      :doc "thermodynamic power — conserved, dissipates to heat"}
   :economic      {:unit "USD/yr" :doc "money flow — economic free energy; not joules"}
   :informational {:unit "bit/s"  :doc "information flux — free energy in nats; not joules"}
   :experiential  {:unit "index"  :doc "attention/affect/meaning — subject-dependent; not joules"}})

;; ── disclosed, contestable conversion constants (reference-only, viz layout) ──
;; These let the viz place incommensurable flows on ONE log axis. They are NOT claims
;; that the units are identical. Edit them and the boundary stays honest because every
;; conversion is flagged :reference-only and the data model keeps the native unit.
(def reference-conversions
  {;; energy intensity of GDP: ~620 EJ/yr primary energy ÷ ~$105T/yr GWP ≈ 5.9 MJ/$
   :economic->physical-W-per-USD-yr {:factor 0.187   ;; (620e18 J/yr / 105e12 $/yr) / 3.15e7 s = ~0.187 W per $/yr
                                     :basis "IEA primary energy 2023 ÷ World Bank GWP 2023"
                                     :reference-only true}
   ;; empirical compute energy: ~460 TWh/yr datacenters ÷ global IP traffic ⇒ J/bit (Landauer floor kT·ln2≈3e-21 J/bit)
   :informational->physical-W-per-bit-s {:factor 4.0e-9 ;; ~ datacenter+network J/bit, ~1e12× the Landauer bound
                                         :basis "IEA datacenter 2022 ÷ Cisco/Statista IP traffic; Landauer floor kT·ln2"
                                         :reference-only true}
   ;; experiential: NO physical conversion. Refusing to convert meaning→joules is the design.
   :experiential->physical {:factor nil
                            :basis "REFUSED BY DESIGN — meaning is subject-dependent; J/attention is the philosophy soup"
                            :reference-only true}})

;; ── seed loading + classification ─────────────────────────────────────────────
(defn classify
  "Split the flat seed vector by :type → {:flows [...] :edges [...]}."
  [rows]
  {:flows (vec (filter #(= (:type %) :flow) rows))
   :edges (vec (filter #(= (:type %) :circulation) rows))})

#?(:clj
   (defn load-edn [path]
     (with-open [r (io/reader path)] (edn/read-string (slurp r)))))

;; ── within-class accounting (NEVER cross-class) ──────────────────────────────
(defn totals-by-class
  "Sum flow magnitudes WITHIN each unit class only. Returns {class {:unit :total :n}}.
  There is deliberately no grand total — summing across classes is undefined."
  [flows]
  (->> flows
       (group-by :class)
       (map (fn [[cls fs]]
              [cls {:unit (get-in unit-classes [cls :unit])
                    :total (reduce + (map #(double (:magnitude %)) fs))
                    :n (count fs)}]))
       (into {})))

(defn dissipation
  "Entropy-production proxy for a physical flow: the fraction of input free energy that
  becomes waste heat (1 − useful efficiency). Only meaningful for :physical flows that
  disclose an :efficiency; returns nil otherwise (honest about scope)."
  [flow]
  (when (and (= (:class flow) :physical) (:efficiency flow))
    (let [eff (double (:efficiency flow))]
      {:waste-W (* (double (:magnitude flow)) (- 1.0 eff))
       :useful-W (* (double (:magnitude flow)) eff)
       :efficiency eff})))

;; ── visual magnitude (log scale; reference-only cross-class placement) ────────
(defn- safe-log10 [x] (Math/log10 (max 1e-30 (double x))))

(defn visual-magnitude
  "A log10 visual size for a flow, on a shared axis for layout ONLY. Physical flows
  use raw watts; non-physical flows are placed via their disclosed reference conversion
  to a physical-equivalent (flagged reference-only). :experiential flows are placed on
  their OWN normalized axis (no physical conversion) — returns {:axis :experiential}."
  [flow]
  (let [m (double (:magnitude flow))]
    (case (:class flow)
      :physical {:axis :physical :log10-W (safe-log10 m)}
      :economic {:axis :physical-equiv :reference-only true
                 :log10-W (safe-log10 (* m (get-in reference-conversions
                                                    [:economic->physical-W-per-USD-yr :factor])))}
      :informational {:axis :physical-equiv :reference-only true
                      :log10-W (safe-log10 (* m (get-in reference-conversions
                                                        [:informational->physical-W-per-bit-s :factor])))}
      :experiential {:axis :experiential :index m
                     :note "no physical conversion — subject-dependent meaning"})))

;; ── the coupled circulation loop ──────────────────────────────────────────────
(defn circulation-closed?
  "Is the circulation a closed loop (every node has an out-edge)? An open dissipative
  system still circulates; an unclosed graph means a measured flow leaks out unmodeled."
  [flows edges]
  (let [nodes (set (map :id flows))
        sources (set (map :from edges))]
    (every? sources nodes)))

;; ── the assembled field (what viz.cljc renders) ──────────────────────────────
(defn field
  "Assemble the measured energy field from classified seed rows. Pure (no I/O).
  Returns {:flows :edges :totals :dissipation :closed? :visual}."
  [{:keys [flows edges]}]
  {:flows flows
   :edges edges
   :totals (totals-by-class flows)
   :dissipation (->> flows (keep #(some->> (dissipation %) (assoc {} :id (:id %) :class (:class %) :d))) vec)
   :closed? (circulation-closed? flows edges)
   :visual (mapv (fn [f] (assoc (select-keys f [:id :label :class :magnitude :unit :source])
                                :visual (visual-magnitude f))) flows)})

;; ── EAVT datom emission for the measured field ───────────────────────────────
(defn datoms
  "Render measured flows to EAVT datoms. Each flow keeps its NATIVE unit; the cross-class
  visual magnitude is flagged :uzu.flow/reference-only so the log never claims unit identity."
  [flows]
  (vec (mapcat
        (fn [f]
          (let [e (str "uzu:flow/" (:id f))
                vm (visual-magnitude f)]
            (cond-> [[":db/add" e ":uzu.flow/id" (:id f)]
                     [":db/add" e ":uzu.flow/label" (:label f)]
                     [":db/add" e ":uzu.flow/class" (str (:class f))]
                     [":db/add" e ":uzu.flow/unit" (or (:unit f) (get-in unit-classes [(:class f) :unit]))]
                     [":db/add" e ":uzu.flow/magnitude" (double (:magnitude f))]
                     [":db/add" e ":uzu.flow/source" (or (:source f) "representative")]
                     [":db/add" e ":uzu/derived" true]
                     [":db/add" e ":uzu/sourcing" ":representative"]]
              (:log10-W vm) (conj [":db/add" e ":uzu.flow/visual-log10-W" (/ (Math/round (* 1000.0 (:log10-W vm))) 1000.0)])
              (:reference-only vm) (conj [":db/add" e ":uzu.flow/reference-only" true]))))
        flows)))
