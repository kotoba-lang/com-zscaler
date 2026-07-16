(ns noroshi.methods.pic-layout
  "noroshi (烽) photonic-IC layout generator (ADR-2606051600 §R1b).
  1:1 Clojure port of methods/pic_layout.py. Stdlib only (gdsfactory optional, never required).

  Generates a silicon-photonic transmitter PIC as a neutral **ModelOp plan** — an ordered list
  of components, ports, and waveguide routes (the sumitsubo CAD `ModelOp` pattern) — and closes
  the loop back to `link-budget`: the plan's total on-chip waveguide length feeds the link
  budget, so a layout change is immediately reflected in the optical margin.

  Clean-room open-EDA (G1/N5): the layout vocabulary is GDSFactory-shaped
  (component/port/route), and IF `gdsfactory` is importable the plan is built into a real
  `Component` and a GDS is written. It is NEVER required, NEVER a proprietary tool, and the
  GDS *write* is outward-gated (G8) — at R0 the verifiable deliverable is the deterministic
  plan, not a fabricable mask. No NDA foundry PDK (G1).

  House style: kebab keyword keys; Python ':…' strings stay literal; pure fns; I/O at #?(:clj)
  edges; closed-vocab/gate → ex-info. Layout coords use Python f\"{x:.0f}\"/f\"{x:.3f}\" fixed
  formatting (HALF_EVEN on the exact double); the budget figures route through
  link-budget/py-float-repr (Python float repr). Portable .cljc."
  (:require [clojure.string :as str]
            [noroshi.methods.link-budget :as lb]))

;; ── fixed-point formatting: Python f"{x:.Nf}" (HALF_EVEN on the exact double) ─
#?(:clj
   (defn- fmt-fixed
     "Python f\"{x:.Nf}\" — fixed-point, n decimals, HALF_EVEN rounding on the exact double."
     [x n]
     (-> (java.math.BigDecimal. (double x))
         (.setScale (int n) java.math.RoundingMode/HALF_EVEN)
         .toPlainString))
   :cljs
   (defn- fmt-fixed [x n] (.toFixed (double x) n)))

;; ── ModelOp / PicPlan (frozen dataclass equivalents, kebab keyword keys) ─────
(defn model-op
  "One layout operation: place a component, or route a waveguide between two ports.
  Mirrors the ModelOp dataclass (op / name / kind / x-um / y-um / length-um / ports)."
  [op name & {:keys [kind x-um y-um length-um ports]
              :or {kind "" x-um 0.0 y-um 0.0 length-um 0.0 ports []}}]
  {:op op :name name :kind kind :x-um x-um :y-um y-um :length-um length-um :ports ports})

(defn- routes-total
  "sum(o.length_um for o in ops if o.op == \"route\")."
  [ops]
  (reduce + 0.0 (map :length-um (filter #(= "route" (:op %)) ops))))

(defn- place-names
  "[o.name for o in ops if o.op == \"place\"]."
  [ops]
  (mapv :name (filter #(= "place" (:op %)) ops)))

;; A minimal Tx PIC: laser → MZM modulator → routing waveguide → grating coupler (off-chip).
(defn transmitter-plan
  "1:1 with transmitter_plan. route-um = modulator→coupler waveguide length (must be positive)."
  ([] (transmitter-plan "noroshi-tx-pic" 1500.0))
  ([name route-um]
   (when (<= route-um 0)
     (throw (ex-info "route_um (modulator→coupler waveguide length) must be positive"
                     {:route-um route-um})))
   (let [ops [(model-op "place" "laser0" :kind "laser" :x-um 0.0 :y-um 0.0)
              (model-op "place" "mzm0" :kind "mzm" :x-um 200.0 :y-um 0.0)
              (model-op "place" "gc0" :kind "grating_coupler" :x-um (+ 200.0 route-um) :y-um 0.0)
              (model-op "route" "wg_laser_mzm" :length-um 200.0 :ports ["laser0.o" "mzm0.i"])
              (model-op "route" "wg_mzm_gc" :length-um route-um :ports ["mzm0.o" "gc0.i"])]]
     {:name name :ops ops :total-waveguide-um (routes-total ops) :components (place-names ops)})))

;; A minimal Rx PIC: grating coupler (off-chip in) → routing waveguide → photodetector.
(defn receiver-plan
  "1:1 with receiver_plan. route-um = coupler→photodetector waveguide length (must be positive)."
  ([] (receiver-plan "noroshi-rx-pic" 1000.0))
  ([name route-um]
   (when (<= route-um 0)
     (throw (ex-info "route_um (coupler→photodetector waveguide length) must be positive"
                     {:route-um route-um})))
   (let [ops [(model-op "place" "gc_in" :kind "grating_coupler" :x-um 0.0 :y-um 0.0)
              (model-op "place" "pd0" :kind "photodetector" :x-um route-um :y-um 0.0)
              (model-op "route" "wg_gc_pd" :length-um route-um :ports ["gc_in.o" "pd0.i"])]]
     {:name name :ops ops :total-waveguide-um (routes-total ops) :components (place-names ops)})))

(defn plan-to-link-design
  "Feed the plan's on-chip waveguide length into a link budget (the layout→budget loop).
  1:1 with plan_to_link_design."
  ([plan] (plan-to-link-design plan nil))
  ([plan base]
   (let [base (or base lb/default-design)
         tx-wg-cm (/ (:total-waveguide-um plan) 1e4)]   ; µm → cm
     (lb/link-design :name (str (:name plan) "-budget")
                     :tx-waveguide-cm tx-wg-cm
                     :rx-waveguide-cm (:rx-waveguide-cm base)))))

(defn full-link-design
  "Compose BOTH on-chip waveguide lengths (tx + rx PIC) into one end-to-end link design.
  1:1 with full_link_design."
  ([tx-plan rx-plan] (full-link-design tx-plan rx-plan nil))
  ([tx-plan rx-plan _base]
   (lb/link-design :name (str (:name tx-plan) "+" (:name rx-plan) "-budget")
                   :tx-waveguide-cm (/ (:total-waveguide-um tx-plan) 1e4)
                   :rx-waveguide-cm (/ (:total-waveguide-um rx-plan) 1e4))))

(defn try-build-gds
  "Build a real GDS via gdsfactory IF available; otherwise return a gated, honest stub result
  (1:1 with try_build_gds). The optional gdsfactory backend is a Python-only path; in this
  Clojure port it is never present, so this always returns the honest gated stub — matching
  the Python report on a host without gdsfactory installed (G8)."
  ([plan] (try-build-gds plan "out/noroshi-tx-pic.gds"))
  ([plan _out-path]
   {:built false
    :reason (str "gdsfactory not available (ModuleNotFoundError); GDS write gated (G8) — "
                 "the verifiable R0 artifact is the ModelOp plan, not a mask")
    :components (:components plan)}))

(defn report
  "Render the open-EDA layout report (1:1 with pic_layout.report)."
  []
  (let [tx (transmitter-plan)
        rx (receiver-plan)
        budget (lb/compute (full-link-design tx rx))
        gds (try-build-gds tx)
        L (transient ["# noroshi 烽 — photonic-IC layout (open-EDA / GDSFactory-shaped ModelOp plan)" ""])]
    (doseq [[plan role] [[tx "transmitter"] [rx "receiver"]]]
      (conj! L (str "## " role " plan: " (:name plan)))
      (conj! L (str "- components       : " (str/join ", " (:components plan))))
      (conj! L (str "- total waveguide  : " (fmt-fixed (:total-waveguide-um plan) 0) " µm ("
                    (fmt-fixed (/ (:total-waveguide-um plan) 1e4) 3) " cm)"))
      (conj! L "- ops:")
      (doseq [o (:ops plan)]
        (if (= "place" (:op o))
          (conj! L (str "  - place " (:name o) " (" (:kind o) ") @ ("
                        (fmt-fixed (:x-um o) 0) "," (fmt-fixed (:y-um o) 0) ") µm"))
          (conj! L (str "  - route " (:name o) ": " (nth (:ports o) 0) " → " (nth (:ports o) 1)
                        "  (" (fmt-fixed (:length-um o) 0) " µm)"))))
      (conj! L ""))
    (conj! L "## end-to-end layout → link budget (tx + rx waveguide, the closed loop)")
    (conj! L (str "- both PIC waveguides → received " (lb/py-float-repr (:received-dbm budget))
                  " dBm, margin " (lb/py-float-repr (:margin-db budget)) " dB → "
                  (if (:closes budget) "CLOSES" "FAILS")))
    (conj! L "")
    (conj! L "## GDS write (open-EDA backend, outward-gated G8)")
    (conj! L (str "- " (if (:built gds) (str "built " (:path gds)) (:reason gds))))
    (conj! L "")
    (conj! L (str "> R0 verifiable artifact = the deterministic ModelOp plan + the layout→budget loop. "
                  "The GDS write runs only with the open-source gdsfactory installed and is G8-gated; "
                  "no proprietary EDA, no NDA foundry PDK (G1/N5)."))
    (str/join "\n" (persistent! L))))

#?(:clj
   (defn -main
     "CLI entry: print the offline pic-layout report (1:1 with `python3 pic_layout.py`)."
     [& _argv]
     (println (report))
     0))
