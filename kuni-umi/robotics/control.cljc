(ns kuni-umi.robotics.control
  "control — closed-loop controllers + runner (the open-ot field-tier reference).
  1:1 cljc port of `robotics/control.py`.

  The deterministic floating-point :representative twin of the field-tier control
  loops that ship as fixed-point Rust WASM Function Blocks. Controllers are
  immutable maps; `controller-step` returns `{:ctrl <updated> :out <command>}`
  (the Python `.step(error, dt)` mutated `self._integral/_prev_error`). Never
  touches hardware.

  Parity note: same float64 ops in the same order as the Python; `round6`/`round4`
  mirror Python `round(x, n)` (round-half-to-even) for the reported fields."
  (:require [kuni-umi.robotics.plant :as plant]))

;; ── Python round(x, n) parity (round-half-to-even) ────────────────

(defn round-half-even
  "Mirror Python `round(x, n)`: round-half-to-even at n decimal places."
  [x n]
  (let [scale (Math/pow 10.0 n)]
    (/ (Math/rint (* (double x) scale)) scale)))

(def ^:private inf Double/POSITIVE_INFINITY)
(def ^:private -inf Double/NEGATIVE_INFINITY)

;; ── PID (limited, anti-windup) — mirrors open-ot PID_LIMITED ───────

(defn pid
  "Limited PID with anti-windup. Port of the `PID` dataclass."
  [& {:keys [kp ki kd out-min out-max]
      :or   {ki 0.0 kd 0.0 out-min -inf out-max inf}}]
  {:ctrl/type :pid
   :kp (double kp) :ki (double ki) :kd (double kd)
   :out-min (double out-min) :out-max (double out-max)
   :integral 0.0 :prev-error nil :saturated false})

(defn- pid-reset [p]
  (assoc p :integral 0.0 :prev-error nil :saturated false))

(defn- pid-step
  "Port of `PID.step` — conditional-integration anti-windup. Returns
  {:ctrl updated-pid :out clamped}."
  [{:keys [kp ki kd out-min out-max integral prev-error] :as p} error dt]
  (let [deriv (if (and (some? prev-error) (> dt 0))
                (/ (- error prev-error) dt)
                0.0)
        tentative-integral (+ integral (* error dt))
        raw (+ (* kp error) (* ki tentative-integral) (* kd deriv))
        clamped (min out-max (max out-min raw))
        saturated (not= clamped raw)]
    {:ctrl (assoc p
                  :integral (if saturated integral tentative-integral)
                  :prev-error error
                  :saturated saturated)
     :out clamped}))

;; ── Droop (proportional) — mirrors open-ot DROOP_P_F ──────────────

(defn droop
  "Proportional frequency/voltage droop. Port of the `Droop` dataclass."
  [& {:keys [nominal droop-r p-base p-min p-max]
      :or   {p-base 0.0 p-min -inf p-max inf}}]
  {:nominal (double nominal) :droop-r (double droop-r)
   :p-base (double p-base) :p-min (double p-min) :p-max (double p-max)})

(defn droop-command
  "P = P_base + (nominal − measured)/R, clamped to [p_min, p_max].
  Port of `Droop.command`."
  [{:keys [nominal droop-r p-base p-min p-max]} measured]
  (let [p (+ p-base (/ (- nominal measured) droop-r))]
    (min p-max (max p-min p))))

;; ── DroopPI (primary droop + secondary PI) ────────────────────────

(defn droop-pi
  "Primary droop (instantaneous) + secondary PI (zero steady-state error).
  Port of the `DroopPI` class."
  [d p]
  {:ctrl/type :droop-pi :droop d :pid p})

;; ── Unified controller interface (mirrors Python .reset/.step) ─────

(defn controller-reset
  "Port of `<controller>.reset()`."
  [ctrl]
  (case (:ctrl/type ctrl)
    :pid       (pid-reset ctrl)
    :droop-pi  (assoc ctrl :pid (pid-reset (:pid ctrl)))))

(defn controller-step
  "Port of `<controller>.step(error, dt)`. Returns {:ctrl updated :out command}."
  [ctrl error dt]
  (case (:ctrl/type ctrl)
    :pid
    (pid-step ctrl error dt)

    :droop-pi
    (let [{:keys [droop pid]} ctrl
          measured (- (:nominal droop) error)
          {pid' :ctrl pid-out :out} (pid-step pid error dt)
          cmd (+ (droop-command droop measured) pid-out)
          clamped (min (:p-max droop) (max (:p-min droop) cmd))]
      {:ctrl (assoc ctrl :pid pid') :out clamped})))

;; ── simulate — PID closed loop against a plant ────────────────────

(defn simulate
  "Run a closed loop against a plant and report convergence. Port of `simulate`.
  Returns a result map mirroring `ControlResult` (trajectory = vector of
  [t process-var command]). Deterministic: same inputs ⇒ same trajectory."
  [plant0 controller setpoint steps dt
   & {:keys [tol settle-window] :or {tol 1e-3 settle-window 10}}]
  (let [setpoint (double setpoint)
        dt (double dt)
        ctrl0 (controller-reset controller)
        ;; main loop: thread plant + controller + accumulators
        final
        (reduce
         (fn [{:keys [plant ctrl traj errors max-abs]} k]
           (let [pv (plant/measure plant)
                 error (- setpoint pv)
                 {ctrl' :ctrl cmd :out} (controller-step ctrl error dt)]
             {:plant   (plant/step plant cmd dt)
              :ctrl    ctrl'
              :traj    (conj traj [(round-half-even (* k dt) 6) pv cmd])
              :errors  (conj errors (Math/abs (double error)))
              :max-abs (max max-abs (Math/abs (double error)))}))
         {:plant plant0 :ctrl ctrl0 :traj [] :errors [] :max-abs 0.0}
         (range steps))
        plant' (:plant final)
        errors (:errors final)
        final-pv (plant/measure plant')
        steady-error (- setpoint final-pv)
        ;; settling_step: first index from which every later error < tol
        n (count errors)
        ev (vec errors)
        settling-step (loop [i 0]
                        (cond
                          (>= i n) -1
                          (every? #(< % tol) (subvec ev i)) i
                          :else (recur (inc i))))
        tail (if (>= n settle-window) (subvec ev (- n settle-window)) ev)
        converged (boolean (and (seq tail) (every? #(< % tol) tail)))]
    {:setpoint setpoint
     :final-value (round-half-even final-pv 6)
     :steady-error (round-half-even steady-error 6)
     :converged converged
     :settling-step settling-step
     :max-abs-error (round-half-even (:max-abs final) 6)
     :steps steps
     :trajectory (:traj final)}))
