(ns fuchi.cells.allocation-compute.state-machine
  "Phase state machine for 扶持 (fuchi) allocation_compute — the G1/G2/G5 allocator.
  1:1 port of cells/allocation_compute/state_machine.py (ADR-2606052300). Computes a tenure-weighted
  in-kind allocation; REFUSES any investment/return instrument (G1), structurally fixes cash 0 (G2)
  + owns-payoff false (G5). weight = round(log1p(min(tenure/12, 40)) × hazard/1000, 6)."
  (:require [clojure.string :as str]))

(def allowed-instruments #{"in-kind-grant" "sustenance" "tooling-access" "compute-access"})
(def forbidden-instruments
  #{"equity" "debt" "convertible" "revenue-share" "profit-claim" "carry" "dividend" "loan" "interest" "warrant" "option" "exit"})
(def tenure-cap-years 40.0)

(def state-defaults
  {"phase" "init" "did" "" "instrument" "sustenance" "tenure_months" 0 "hazard_permille" 1000
   "owns_payoff" false "weight" 0.0 "refusal" "" "payload" {}})
(defn- cell-state [state] (merge state-defaults (get state "cell_state" {})))
(defn- kw [v] (-> (str (or v "")) (str/replace #"^:+" "") (str/split #"/") last str/lower-case))
(defn- to-int [v] (long (or v 0)))
(defn- pyround6 [x] (/ (Math/round (* (double x) 1000000.0)) 1000000.0))

(defn transition-to-computed [state]
  (let [cs (cell-state state)
        cs (assoc cs "did" (get state "did" (get cs "did"))
                  "instrument" (kw (get state "instrument" (get cs "instrument")))
                  "tenure_months" (to-int (get state "tenure_months" (get cs "tenure_months")))
                  "hazard_permille" (to-int (get state "hazard_permille" (get cs "hazard_permille")))
                  "owns_payoff" (boolean (get state "owns_payoff" (get cs "owns_payoff"))))
        refuse (fn [msg] {"cell_state" (assoc cs "refusal" msg "phase" "refused")})]
    (cond
      (contains? forbidden-instruments (get cs "instrument"))
      (refuse (str "G1: instrument " (pr-str (get cs "instrument")) " is an investment vehicle — UNREPRESENTABLE"))
      (not (contains? allowed-instruments (get cs "instrument")))
      (refuse (str "G1: instrument " (pr-str (get cs "instrument")) " not a sustenance instrument"))
      (get cs "owns_payoff")
      (refuse "G5: a maintainer cannot own the payoff (work product is commons)")
      :else
      (let [years (min (/ (get cs "tenure_months") 12.0) tenure-cap-years)
            hazard (/ (get cs "hazard_permille") 1000.0)
            weight (pyround6 (* (Math/log1p years) hazard))]
        {"cell_state" (assoc cs "weight" weight "refusal" "" "phase" "computed"
                             "payload" {"maintainerDid" (get cs "did") "instrument" (get cs "instrument")
                                        "weight" weight "cashUsdMicros" 0 "serverHeldKey" false})}))))

(defn solve [_input-state]
  (throw (ex-info "fuchi R0 scaffold: activate allocation_compute via Council ADR (post-2606052300 ratification)" {:scaffold true})))
