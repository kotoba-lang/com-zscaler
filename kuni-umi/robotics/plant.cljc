(ns kuni-umi.robotics.plant
  "plant — deterministic physical-plant simulation (the kami-genesis stand-in).
  1:1 cljc port of `robotics/plant.py`.

  A plant is a plain map carrying its params + mutable-in-python state; here the
  state is threaded immutably: `measure` reads the process variable, `step`
  returns an UPDATED plant map (the Python `step(...)` mutated `self`). Dispatch
  is on `:plant/type` so `control/simulate` is plant-agnostic.

  Two shared `:representative` lumped-parameter models (first-order lag + a
  swing-equation microgrid) — intentionally simple but *real* dynamics: the
  closed-loop controllers in control.cljc must actually stabilise them, which is
  what the acceptance tests assert.")

;; ── FirstOrderPlant: τ·ẋ = −x + K·u (+ disturbance d) ─────────────

(defn first-order-plant
  "Generic first-order lag. Port of the `FirstOrderPlant` dataclass defaults."
  [& {:keys [gain tau disturbance x]
      :or   {gain 1.0 tau 1.0 disturbance 0.0 x 0.0}}]
  {:plant/type  :first-order
   :gain        (double gain)
   :tau         (double tau)
   :disturbance (double disturbance)
   :x           (double x)})

;; ── MicrogridPlant: islanded swing equation + battery SoC ─────────

(defn microgrid-plant
  "Islanded microgrid frequency dynamics (swing equation) + battery SoC.
  Port of the `MicrogridPlant` dataclass defaults."
  [& {:keys [f-nom inertia-h damping-d s-base p-load battery-kwh soc f]
      :or   {f-nom 50.0 inertia-h 4.0 damping-d 1.5 s-base 200.0
             p-load 100.0 battery-kwh 500.0 soc 0.6 f 50.0}}]
  {:plant/type  :microgrid
   :f-nom       (double f-nom)
   :inertia-h   (double inertia-h)
   :damping-d   (double damping-d)
   :s-base      (double s-base)
   :p-load      (double p-load)
   :battery-kwh (double battery-kwh)
   :soc         (double soc)
   :f           (double f)})

(defn set-load
  "Apply a load step to a microgrid plant (the disturbance the controller must
  reject). Port of `MicrogridPlant.set_load`."
  [plant p-load-kw]
  (assoc plant :p-load (double p-load-kw)))

;; ── measure / step (multimethods on :plant/type) ──────────────────

(defmulti measure
  "Return the current process variable (the controlled quantity)."
  :plant/type)

(defmethod measure :first-order [plant] (:x plant))
(defmethod measure :microgrid   [plant] (:f plant))

(defmulti step
  "Advance the plant by `dt` seconds under actuator `command`; returns the
  UPDATED plant map (Python mutated self)."
  (fn [plant _command _dt] (:plant/type plant)))

(defmethod step :first-order
  [{:keys [x gain disturbance tau] :as plant} command dt]
  (let [dxdt (/ (+ (- x) (* gain command) disturbance) tau)]
    (assoc plant :x (+ x (* dxdt dt)))))

(defmethod step :microgrid
  [{:keys [f f-nom inertia-h damping-d s-base p-load soc battery-kwh] :as plant} command dt]
  (let [imbalance-pu (/ (- command p-load) s-base)
        dfdt (/ (- (* imbalance-pu f-nom) (* damping-d (- f f-nom)))
                (* 2.0 inertia-h))
        f'   (+ f (* dfdt dt))
        net-kwh (* (- command p-load) (/ dt 3600.0))
        soc' (min 1.0 (max 0.0 (+ soc (/ net-kwh battery-kwh))))]
    (assoc plant :f f' :soc soc')))
