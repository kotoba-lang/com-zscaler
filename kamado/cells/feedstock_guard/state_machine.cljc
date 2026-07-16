(ns kamado.cells.feedstock-guard.state-machine
  "1:1 port of cells/feedstock_guard/state_machine.py (ADR-2606051500) — the defining kamado 竈 G1
  cell: a refining run becomes an admissible synthesis ONLY after the feedstock-class screen passes
  (the third enforcement point of the :feedstock/class invariant, after schema + lexicon const).
  Pure phase-progression init → screened → balanced → admitted with a float-light carbon balance.

  Invariants: G1 closed-loop-carbon-only (fossil-virgin-crude raises BEFORE any synthesis record —
  kamado cannot operate a fossil-fed refinery) · G2/D3 net-atmospheric-carbon Δ ≤ tolerance.
  GuardState dataclass → string-keyed map under \"cell_state\"; ValueError → (throw (ex-info ...))."
  (:require [clojure.string :as str]))

(def allowed-feedstock #{"biogenic" "captured-co2" "recycled-carbon" "existing-inventory-decommission"})
(def allowed-energy #{"hikari-renewable" "grid-mixed"})   ; surface-fidelity constant (unused by run)
(def allowed-fate #{"combusted-fuel" "durable-material"})

(def ^:private C-PROD 3.10)
(def ^:private process-by-energy {"hikari-renewable" 0.04 "grid-mixed" 0.22})
(def ^:private D3-TOLERANCE 0.15)

(def ^:private defaults
  {"phase" "init" "feedstock" "biogenic" "energy" "hikari-renewable" "fate" "combusted-fuel"
   "screened" false "net_delta" 0.0 "passes_d3" false "payload" {}})

(defn- state* [state] (merge defaults (get state "cell_state" {})))

(defn- norm [v] (if (string? v) (str/replace (or v "") #"^:+" "") (str v)))

(defn- pyround [x n]
  (-> (java.math.BigDecimal. (double x))
      (.setScale (int n) java.math.RoundingMode/HALF_EVEN)
      .doubleValue))

(defn transition-to-screened
  "G1: feedstock-class screen. Raises on any fossil feedstock (or unknown product fate)."
  [state]
  (let [cs0 (state* state)
        feedstock (norm (get state "feedstock" (get cs0 "feedstock")))]
    (when-not (contains? allowed-feedstock feedstock)
      (throw (ex-info (str "G1 violation: feedstock-class '" feedstock "' is not representable; only "
                           allowed-feedstock " permitted. kamado refines closed-loop carbon ONLY — "
                           "fossil-virgin-crude is excluded by construction (no synthesis run produced). "
                           "Robotics cannot neutralize fossil carbon; only the feedstock can.")
                      {:kamado/violation :g1 :feedstock feedstock})))
    (let [fate (norm (get state "fate" (get cs0 "fate")))]
      (when-not (contains? allowed-fate fate)
        (throw (ex-info (str "unknown product fate '" fate "'") {:kamado/fate fate})))
      {"cell_state" (assoc cs0
                           "feedstock" feedstock
                           "energy" (norm (get state "energy" (get cs0 "energy")))
                           "fate" fate
                           "screened" true
                           "phase" "screened")})))

(defn transition-to-balanced
  "G2/D3: compute the net atmospheric carbon Δ (mirror of carbon_balance.balance)."
  [state]
  (let [cs (state* state)]
    (when-not (get cs "screened")
      (throw (ex-info "carbon balance requires a passed feedstock screen first (G1)" {:kamado/violation :g1})))
    (let [feedstock (get cs "feedstock")
          origin (cond
                   (contains? #{"biogenic" "captured-co2"} feedstock) (- C-PROD)
                   (= feedstock "recycled-carbon") (* (- C-PROD) 0.85)
                   :else 0.0)
          process (get process-by-energy (get cs "energy") 0.22)
          fate (if (= (get cs "fate") "combusted-fuel") C-PROD 0.0)
          net-delta (pyround (+ origin process fate) 3)]
      {"cell_state" (assoc cs
                           "net_delta" net-delta
                           "passes_d3" (<= net-delta D3-TOLERANCE)
                           "phase" "balanced")})))

(defn transition-to-admitted
  "A synthesis run is admissible only if it is closed-loop AND passes D3."
  [state]
  (let [cs (state* state)]
    (when-not (get cs "passes_d3")
      (throw (ex-info (str "G2 violation: net atmospheric Δ " (format "%+.2f" (get cs "net_delta"))
                           " tCO2e/t > " D3-TOLERANCE "; design does not pass D3 (use renewable energy "
                           "+ closed-loop carbon / lock the carbon).")
                      {:kamado/violation :g2 :net-delta (get cs "net_delta")})))
    {"cell_state" (assoc cs
                         "payload" {"feedstockClass" (get cs "feedstock") "energy" (get cs "energy")
                                    "productFate" (get cs "fate") "netDeltaTco2ePerT" (get cs "net_delta")
                                    "passesD3" true "screened" true}
                         "phase" "admitted")}))
