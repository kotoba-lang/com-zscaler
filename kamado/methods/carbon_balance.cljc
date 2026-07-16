(ns kamado.methods.carbon-balance
  "kamado 竈 — net-atmospheric-carbon balance for a refining pathway.
  1:1 Clojure port of `methods/carbon_balance.py` (ADR-2606051500).

  The EMPIRICAL answer to two questions:
    (1) Is petroleum refining actually a multi-generational harm?
    (2) Can robotics / process-control make it harmless?

  A per-tonne-of-finished-hydrocarbon carbon ledger over the carbon's WHOLE life —
  origin → process → fate — because the harm lives in the carbon atoms, not in the unit
  operations. For 1 t of finished hydrocarbon product:

      net_delta = origin_credit + process_emissions + fate_release      [tCO2e / t]

  The headline finding falls straight out of the arithmetic: a fossil→combusted pathway is
  ~+3.5 tCO2e/t, of which the process slice automation can touch is ~0.4 (~11%). You cannot
  robotics your way to net≤0 — the +C_PROD fate and the 0 fossil origin are in the carbon,
  not the plant. The ONLY pathway to D3 (net atmospheric Δ ≤ 0) is to change the feedstock
  to closed-loop carbon. That is kamado's entire thesis, and it is just a sum.

  House style: Python ':…' keyword strings stay strings; pure fns; closed-vocab violations
  → ex-info (the Python ValueError edge); round() = HALF_EVEN via exact BigDecimal.(double);
  {:g}/{:+.2f} float formatting matches Python f-strings. Portable .cljc."
  (:require [clojure.string :as str]))

;; ── physical constants (well-established, public) ────────────────────────────
;; A finished liquid hydrocarbon fuel is ~85% carbon by mass; full combustion of
;; 1 t therefore releases ~3.1 tCO2 (gasoline ≈3.10, diesel ≈3.17, jet ≈3.15).
(def C-PROD 3.10)  ;; tCO2 worth of carbon embodied in 1 t of finished hydrocarbon

;; Process scope-1/2 burn per tonne of product, by energy source.
(def PROCESS
  {":fossil-powered" 0.40    ;; refinery burns its own fuel gas + grid fossil power
   ":grid-mixed" 0.22
   ":hikari-renewable" 0.04})  ;; PV/wind/green-H2 process heat (ADR-2605261100)

;; Robotics/advanced-process-control trims the process slice (tighter combustion, less
;; flaring, fewer upsets) — a real but BOUNDED reduction. It never touches origin/fate.
(def APC-PROCESS-REDUCTION 0.30)  ;; ≤30% of the process slice, optimistic

(def RECYCLE-CREDIT 0.85)  ;; recycled-carbon origin credit (waste C diverted from a burn)

;; D3 tolerance: a pathway is "closed-loop / charter-passing" iff net ≤ this small band.
(def D3-TOLERANCE 0.15)  ;; tCO2e/t — allows residual non-CO2 / measurement slack

;; ── a Pathway is a plain map (the frozen dataclass) ──────────────────────────
(defn ->pathway
  "Construct a Pathway map. apc/representable default like the Python dataclass."
  [name feedstock energy fate & {:keys [apc representable]
                                 :or {apc false representable true}}]
  {:name name :feedstock feedstock :energy energy :fate fate
   :apc apc :representable representable})

;; ── round() = HALF_EVEN over the EXACT binary double (Python round(x, n)) ─────
(defn- round-half-even
  "Python round(x, n): banker's rounding over the exact double value."
  [x n]
  #?(:clj (-> (java.math.BigDecimal. (double x))
              (.setScale (int n) java.math.RoundingMode/HALF_EVEN)
              (.doubleValue))
     :cljs (let [f (Math/pow 10 n) y (* (double x) f)
                 r (Math/round y)
                 ;; JS Math.round is HALF_UP; correct ties to even
                 r (if (= 0.5 (- y (Math/floor y)))
                     (let [fl (Math/floor y)] (if (even? fl) fl (inc fl)))
                     r)]
             (/ r f))))

(defn origin-credit [feedstock]
  (cond
    (= feedstock ":fossil-virgin-crude") 0.0       ;; carbon out of geological storage → no credit
    (or (= feedstock ":biogenic") (= feedstock ":captured-co2")) (- C-PROD)  ;; drawn from atmosphere
    (= feedstock ":recycled-carbon") (* (- C-PROD) RECYCLE-CREDIT)
    :else (throw (ex-info (str "unknown feedstock class '" feedstock "'")
                          {:feedstock feedstock}))))

(defn process-emissions [energy apc]
  (let [base (get PROCESS energy)]
    (when (nil? base)
      ;; Python PROCESS[energy] raises KeyError
      (throw (ex-info (str "'" energy "'") {:energy energy})))
    (if apc (* base (- 1 APC-PROCESS-REDUCTION)) base)))

(defn fate-release [fate]
  (cond
    (= fate ":combusted-fuel") C-PROD               ;; the carbon is returned to the atmosphere
    (= fate ":durable-material") 0.0                ;; locked (G12: end-of-life route required)
    :else (throw (ex-info (str "unknown fate '" fate "'") {:fate fate}))))

(defn balance [p]
  (let [o (origin-credit (:feedstock p))
        pr (process-emissions (:energy p) (:apc p))
        f (fate-release (:fate p))
        net (+ o pr f)]
    {"origin" (round-half-even o 3)
     "process" (round-half-even pr 3)
     "fate" (round-half-even f 3)
     "net" (round-half-even net 3)
     "passes_d3" (<= net D3-TOLERANCE)}))

;; ── the pathway set: a fossil BASELINE (avoided, not buildable) + the buildable set ──
(def PATHWAYS
  [(->pathway "fossil diesel, fossil-powered (BASELINE — NOT buildable, G1)"
              ":fossil-virgin-crude" ":fossil-powered" ":combusted-fuel"
              :apc false :representable false)
   (->pathway "fossil diesel + full robotic APC (still NOT buildable — shows APC limit)"
              ":fossil-virgin-crude" ":fossil-powered" ":combusted-fuel"
              :apc true :representable false)
   (->pathway "biogenic alkane diesel, hikari-powered, combusted"
              ":biogenic" ":hikari-renewable" ":combusted-fuel" :apc true)
   (->pathway "captured-CO2 e-fuel (green-H2 FT), combusted"
              ":captured-co2" ":hikari-renewable" ":combusted-fuel" :apc true)
   (->pathway "recycled-carbon naphtha, hikari-powered, combusted"
              ":recycled-carbon" ":hikari-renewable" ":combusted-fuel" :apc true)
   (->pathway "biogenic naphtha → durable polymer (carbon locked)"
              ":biogenic" ":hikari-renewable" ":durable-material" :apc true)])

;; ── float formatting helpers (Python f-strings) ──────────────────────────────
(defn- fmt-f
  "Python f\"{x:.Nf}\" — fixed-point, HALF_EVEN over the exact binary value."
  [x n]
  #?(:clj (-> (java.math.BigDecimal. (double x))
              (.setScale (int n) java.math.RoundingMode/HALF_EVEN)
              (.toPlainString))
     :cljs (.toFixed (double x) n)))

(defn- fmt-sf
  "Python f\"{x:+.Nf}\" — always a leading sign."
  [x n]
  (let [s (fmt-f x n)]
    (if (str/starts-with? s "-") s (str "+" s))))

(defn- fmt-g
  "Python str(float) / f\"{x}\" for a float — shortest repr that round-trips (general).
  For the small whole/short decimals here (3.1, 0.15) this matches Python's repr."
  [x]
  #?(:clj (let [d (double x)]
            (if (== d (Math/rint d))
              (str (long d) ".0")             ;; never reached for the constants used
              (let [s (str d)] s)))           ;; Java Double.toString ≡ Python repr for these
     :cljs (str x)))

(defn render []
  (let [L (transient [])
        P #(conj! L %)]
    (P "# kamado 竈 — net-atmospheric-carbon ledger (tCO2e per tonne product)")
    (P "")
    (P (str "C_PROD=" (fmt-g C-PROD) " · D3 tolerance ≤" (fmt-g D3-TOLERANCE)
            " · APC trims ≤" (int (* APC-PROCESS-REDUCTION 100)) "% of process only"))
    (P "")
    (P "| pathway | feedstock | origin | process | fate | NET | D3? | buildable |")
    (P "|---|---|---:|---:|---:|---:|:---:|:---:|")
    (doseq [p PATHWAYS]
      (let [b (balance p)]
        (P (str "| " (:name p) " | `" (:feedstock p) "` | " (fmt-sf (get b "origin") 2)
                " | " (fmt-sf (get b "process") 2) " | " (fmt-sf (get b "fate") 2)
                " | **" (fmt-sf (get b "net") 2) "** | " (if (get b "passes_d3") "✅" "❌")
                " | " (if (:representable p) "yes" "— (G1)") " |"))))
    (P "")
    (let [base (balance (nth PATHWAYS 0))
          apc (balance (nth PATHWAYS 1))
          share (* (/ (- (get base "net") (get apc "net")) (get base "net")) 100)]
      (P (str "- fossil baseline NET = **" (fmt-sf (get base "net") 2) "** tCO2e/t → strongly "
              "positive, one-way stock→flow = **genuinely multi-generational**."))
      (P (str "- full robotic APC on the SAME fossil pathway only moves it to **"
              (fmt-sf (get apc "net") 2) "** — a **" (fmt-f share 0) "%** cut, all from the "
              "process slice; origin+fate (~89%) is untouched."))
      (P (str "- → robotics/control makes fossil refining *cleaner*, never *harmless*. The only "
              "pathways that reach net≤0 are the ones that change the **feedstock** (G1).")))
    (str/join "\n" (persistent! L))))

#?(:clj
   (defn -main [& _argv]
     (println (render))
     0))
