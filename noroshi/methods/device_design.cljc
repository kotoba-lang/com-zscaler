(ns noroshi.methods.device-design
  "noroshi (烽) generative photonic-device design core — the chip face's design
  entry point (ADR-2606051600, matures the `device_design` cell from a pure
  `.edn` scaffold to real, tested logic).

  NL-intent (device kind + force-class, + optional name/route/line-rate) ->
  civilian-gate (G3/N1, the device-design analogue of `active-alignment`'s
  `enable-laser`) -> an open-EDA ModelOp plan (G1) via `methods/pic-layout` ->
  a photonicDevice-shaped record (mirrors `kotoba/schema.edn`'s `:pdev/*`
  attributes; `:representative true`, G10 — no measured silicon exists).

  Two shapes of plan: `:cpo-module`/`:pic-link` (an assembled multi-component
  PIC) delegate to `pic-layout/transmitter-plan`; a single discrete component
  (`:modulator`/`:grating-coupler`/`:photodetector`/`:laser`/`:waveguide`) gets
  a minimal one-op placement plan (no route) — the plan is honest about scale,
  never inflating a single part into a fake assembly.

  House style: kebab keyword keys; pure fns; no I/O; closed-vocab/gate
  violations -> ex-info. Portable .cljc."
  (:require [clojure.string :as str]
            [noroshi.methods.pic-layout :as pic]))

;; ── closed vocabularies (mirror kotoba/schema.edn :pdev/kind + :pdev/force-class) ──
(def known-kinds
  "The open-PDK component vocabulary a device-design intent's :kind must be in."
  #{"modulator" "grating-coupler" "photodetector" "laser" "waveguide" "pic-link" "cpo-module"})

(def civilian-force-class
  "The only permitted force-class (G3/N1) — mirrors kotoba/schema.edn's
  :pdev/force-class const."
  "civilian-comms")

(def assembly-kinds
  "Kinds that assemble a full transmitter PIC (multi-component, routed) rather
  than a single discrete part."
  #{"cpo-module" "pic-link"})

;; ── civilian gate (G1/G3/N1) ──────────────────────────────────────────────────
(defn civilian-gate
  "Refuse an unknown device kind (G1 — outside the open-PDK vocabulary) or a
  non-civilian force-class (G3/N1) before any EDA plan is generated. Raises
  ex-info exactly at the violated invariant — the device-design analogue of
  `active-alignment/enable-laser`. Returns intent unchanged on success (gate
  only, like enable-laser)."
  [{:keys [kind force-class] :as intent}]
  (when-not (contains? known-kinds kind)
    (throw (ex-info (str "G1: device kind " (pr-str kind) " is not in the known "
                         "open-PDK component vocabulary " known-kinds)
                    {:noroshi/violation :unknown-kind :kind kind})))
  (when (not= force-class civilian-force-class)
    (throw (ex-info (str "G3/N1: force-class " (pr-str force-class) " is not civilian-comms; "
                         "weaponisation is structurally unrepresentable (Mission Charter §1.12)")
                    {:noroshi/violation :n1 :force-class force-class})))
  intent)

;; ── EDA plan generation (G1 open-EDA; delegates the assembled case to pic-layout) ──
(defn- single-component-plan
  "A minimal one-op ModelOp plan for a single discrete component: a `place`, no
  route. Honest about scale — never inflates one part into a fake assembly."
  [kind name]
  (let [op (pic/model-op "place" name :kind kind :x-um 0.0 :y-um 0.0)]
    {:name name :ops [op] :total-waveguide-um 0.0 :components [name]}))

(defn design-plan
  "NL-intent -> an open-EDA ModelOp plan (G1). Civilian-gated first (G3/N1)."
  [{:keys [kind name route-um] :as intent}]
  (civilian-gate intent)
  (let [plan-name (or name (str "noroshi-" kind "-design"))]
    (if (contains? assembly-kinds kind)
      (pic/transmitter-plan plan-name (or route-um 1500.0))
      (single-component-plan kind plan-name))))

;; ── photonicDevice record (the kotoba :pdev/* datom shape) ───────────────────
(defn device-record
  "The photonicDevice-shaped record this cell emits (mirrors kotoba/schema.edn's
  :pdev/* attributes). :representative true (G10) — no measured silicon exists."
  [{:keys [kind line-rate-gbps energy-pj-bit eda]} plan]
  {:id (:name plan)
   :kind kind
   :platform "silicon-photonics"
   :force-class civilian-force-class
   :line-rate-gbps (or line-rate-gbps 106.25)
   :energy-pj-bit (or energy-pj-bit 1.2)
   :process "open-pdk"
   :eda (or eda "gdsfactory")
   :representative true
   :plan plan})

(defn report
  "Render the offline device-design report (a worked example + the civilian
  gate's refusal behavior)."
  []
  (let [intent {:kind "cpo-module" :force-class civilian-force-class :name "noroshi-example-device"}
        plan (design-plan intent)
        dev (device-record intent plan)
        lines ["# noroshi 烽 — generative photonic-device design (NL intent -> open-EDA ModelOp plan)"
               ""
               (str "- kind             : " (:kind dev))
               (str "- force-class      : " (:force-class dev) "  (G3/N1 civilian-comms only)")
               (str "- process / eda    : " (:process dev) " / " (:eda dev) "  (G1 open PDK/EDA)")
               (str "- components       : " (str/join ", " (:components plan)))
               (str "- representative   : " (:representative dev) "  (G10 — no measured silicon exists)")
               ""
               "## civilian gate (G3/N1)"
               "- kind in the known open-PDK vocabulary + force-class = 'civilian-comms' -> plan generated"
               "- any unknown kind, or a non-civilian force-class -> REFUSED (structurally unrepresentable, N1)"
               ""
               (str "> R0 design-only — no tapeout, no measured device, no live fab (G8). Layout is "
                    "the deterministic ModelOp plan (G1); a real GDS write only via the optional "
                    "gdsfactory backend, itself G8-gated (see methods/pic-layout).")]]
    (str/join "\n" lines)))

#?(:clj
   (defn -main
     "CLI entry: print the offline device-design report."
     [& _argv]
     (println (report))
     0))
