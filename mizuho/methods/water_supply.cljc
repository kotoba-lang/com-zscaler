(ns mizuho.methods.water-supply
  "water-supply — mizuho potable-water control loop (R0 :representative).
  1:1 Clojure port of methods/water_supply.py (ADR-2606091800).

  Proves a community-scale supply holds pressure: a demand step (households open
  taps) drops the reservoir level, the pump's secondary-PI loop drives inflow
  until the level error integrates back to the service setpoint, and the modeled
  supply restores service pressure.

  mizuho constitutional gates: community-scale only (G3 — service population is
  hard-capped; a large municipal utility is N1, structurally unrepresentable),
  no commercial water-utility software (G4), Murakumo-only inference (G7),
  consent-gated live actuation (G10 — this module is offline sim only; cell.py
  .solve() stays Council-gated).

  House style: Python ':…' keyword strings stay strings (the to-datoms map keys);
  kebab keyword keys in the result record; pure-at-edges; portable .cljc.
  round(x, n) reproduced via HALF_EVEN on the exact double (substrate/py-round-n)."
  (:require [mizuho.methods.substrate :as sub]))

;; mizuho civilian-use allowlist (closed-world, N1). Water is for people + crops,
;; never force. "supply" (potable distribution), "treat" (disinfection/filtration),
;; "sample" (quality testing), "recycle" (greywater closed-loop), "irrigate".
(def permitted-uses ["supply" "treat" "sample" "recycle" "irrigate"])

;; G3 community-scale invariant: per-source service population is hard-capped.
;; A request above the cap is N1 (a municipal utility) and is refused structurally.
(def max-service-population 2500)

;; ── ReservoirPlant (community service-reservoir level dynamics) ──────────────
;; State (stored volume in litres + current demand) lives in an atom so the plant
;; mutates per simulate step in the Python iteration order.
;;   dV/dt = inflow(command, L/s) − demand(L/s) − leak(level)
;;   level = V / area_m2 / 1000   (1 m^3 = 1000 L spread over area_m2)
;; The gravity-fed leak rises with head (self-regulating tank).
(defrecord ReservoirPlant [area-m2 max-level-m leak-coeff state])

(defn reservoir-plant
  [& {:keys [area-m2 level-m demand-lps max-level-m leak-coeff-lps-per-m]
      :or {area-m2 20.0 level-m 3.0 demand-lps 0.0 max-level-m 6.0
           leak-coeff-lps-per-m 100.0}}]
  (->ReservoirPlant (double area-m2) (double max-level-m) (double leak-coeff-lps-per-m)
                    (atom {:volume-l (* (double level-m) (double area-m2) 1000.0)
                           :demand-lps (double demand-lps)})))

(defn set-demand!
  "Apply a demand step (the disturbance the pump loop must reject)."
  [tank demand-lps]
  (swap! (:state tank) assoc :demand-lps (double demand-lps))
  nil)

(defn pressure-bar
  "Service pressure (bar) ∝ static head (1 m water ≈ 0.0981 bar)."
  [tank]
  (* (sub/measure tank) 0.0981))

(extend-protocol sub/Plant
  ReservoirPlant
  (measure [tank]
    (/ (:volume-l @(:state tank)) (* (:area-m2 tank) 1000.0)))
  (plant-step! [tank command dt]
    ;; command = pump inflow setpoint (L/s); cannot push the tank past its rim.
    ;; Gravity-fed distribution leak rises with head -> self-regulating tank.
    (let [level (sub/measure tank)
          leak-lps (* (:leak-coeff tank) level)
          max-v (* (:max-level-m tank) (:area-m2 tank) 1000.0)]
      (swap! (:state tank)
             (fn [s]
               (let [v (+ (:volume-l s) (* (- command (:demand-lps s) leak-lps) (double dt)))
                     v (if (< v 0.0) 0.0 v)
                     v (if (> v max-v) max-v v)]
                 (assoc s :volume-l v))))
      nil)))

;; ── WaterSupplyResult ────────────────────────────────────────────────────────
(defrecord WaterSupplyResult
  [use demand-step-lps setpoint-level-m final-level-m final-pressure-bar
   level-restored settling-seconds service-population representative])

(defn commission-water-supply
  "Run the supply acceptance test. RAISES (assert-civilian + G3) before any run.
  Apply `demand-step-lps`, run a secondary-PI pump loop, and confirm the level
  returns to `setpoint-level-m`. Refuses a non-civilian use (N1) and any request
  above the community-scale service-population cap (G3)."
  [& {:keys [demand-step-lps use setpoint-level-m area-m2 service-population
             kp ki max-inflow-lps steps dt]
      :or {use "supply" setpoint-level-m 3.0 area-m2 20.0 service-population 200
           kp 10.0 ki 2.0 max-inflow-lps 2000.0 steps 4000 dt 1.0}}]
  (sub/assert-civilian use permitted-uses) ;; N1 gate before any actuation modelling
  (when (> service-population max-service-population)
    (throw (sub/safety-error
            (str "G3: service_population " service-population " exceeds the community-scale "
                 "cap " max-service-population "; a larger system is N1 (a municipal utility) "
                 "and is structurally unrepresentable in mizuho")
            {:gate "G3" :service-population service-population})))
  (let [tank (reservoir-plant :area-m2 area-m2 :level-m setpoint-level-m :demand-lps 0.0)
        _ (set-demand! tank demand-step-lps)
        ;; Pump inflow is non-negative (a community pump cannot suck the tank down).
        pid (sub/pid :kp kp :ki ki :out-min 0.0 :out-max max-inflow-lps)
        res (sub/simulate tank pid setpoint-level-m steps dt :tol 1e-3)
        settling-step (:settling-step res)
        settling-seconds (if (>= settling-step 0) (* settling-step (double dt)) -1.0)]
    (->WaterSupplyResult
     use
     demand-step-lps
     setpoint-level-m
     (sub/py-round-n (:final-value res) 4)
     (sub/py-round-n (pressure-bar tank) 4)
     (:converged res)
     (sub/py-round-n settling-seconds 3)
     service-population
     true)))

(defn to-datoms
  "Project a supply acceptance result into kotoba EAVT-shaped datoms (G6/G9).
  Aggregate-only (no per-household consumption PII)."
  [result source-id]
  {":water.supply/source-id" source-id
   ":water.supply/use" (:use result)
   ":water.supply/demand-step-lps" (:demand-step-lps result)
   ":water.supply/setpoint-level-m" (:setpoint-level-m result)
   ":water.supply/final-level-m" (:final-level-m result)
   ":water.supply/final-pressure-bar" (:final-pressure-bar result)
   ":water.supply/level-restored" (:level-restored result)
   ":water.supply/settling-seconds" (:settling-seconds result)
   ":water.supply/service-population" (:service-population result) ;; aggregate, ≤ G3 cap
   ":water.supply/representative" (:representative result)         ;; G10
   ":water.supply/server-held-key" false                          ;; no-server-key
   ":water.supply/dry-run" true})                                 ;; G10: R0 offline only

;; ::order — Python module member order (for the datom-emit / report contract).
(def ^{::order true} member-order
  [:permitted-uses :max-service-population :reservoir-plant :pressure-bar
   :commission-water-supply :to-datoms])
