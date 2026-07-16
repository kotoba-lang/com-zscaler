(ns niyaku.methods.crane-dynamics
  "crane_dynamics — gantry / ship-to-shore (STS) crane anti-sway physics core.

  1:1 Clojure port of `20-actors/niyaku/methods/crane_dynamics.py`.

  The defining control problem of automated container handling is anti-sway: a quay
  crane moves a 20-40 t container suspended on cables while the trolley traverses
  30-50 m ship→shore. The suspended load is a pendulum; residual sway must settle to
  < a few cm before the spreader can land the box. Classical cart + hanging payload.

  This module is the analytic/control core: physically-correct hanging pendulum, a
  state-feedback anti-sway position controller, and a ZV input-shaper.

  Pure Clojure (clojure.core only), no external deps. Portable .cljc.

  Sign convention:
    x     : trolley position along the quay rail (m), shore-positive
    theta : load swing angle from vertical (rad); theta>0 ⇒ load lags +x
    cable : hoist cable length from trolley to load CG (m)
  Equilibrium is the load hanging straight down (theta = 0) — the STABLE point."
  (:require [clojure.string]))

;; ── GantryCrane (reduced-order single-pendulum-on-trolley model) ─────────────

(defn make-gantry-crane
  "Reduced-order single-pendulum-on-trolley model of an STS / RTG crane.

  The trolley is acceleration-commanded; the control input `u` is trolley
  acceleration (m/s²), saturated at :accel-max."
  [& {:keys [cable-length gravity sway-damping accel-max velocity-max rail-length]
      :or   {cable-length 30.0   ;; m, spreader+load below trolley
             gravity 9.81        ;; m/s²
             sway-damping 0.02   ;; dimensionless viscous damping ratio proxy
             accel-max 0.6       ;; m/s², trolley accel envelope
             velocity-max 4.0    ;; m/s, trolley max traverse speed
             rail-length 60.0}}] ;; m, usable trolley travel
  {:cable-length cable-length
   :gravity gravity
   :sway-damping sway-damping
   :accel-max accel-max
   :velocity-max velocity-max
   :rail-length rail-length})

(defn- clamp ^double [^double v ^double lim]
  (max (- lim) (min lim v)))

(defn natural-frequency
  "Undamped sway natural frequency ω = sqrt(g / L) (rad/s)."
  ^double [crane]
  (Math/sqrt (/ (double (:gravity crane)) (double (:cable-length crane)))))

(defn sway-period
  "Sway period T = 2π / ω (s) — sets the input-shaper impulse spacing."
  ^double [crane]
  (/ (* 2.0 Math/PI) (natural-frequency crane)))

;; ── dynamics ─────────────────────────────────────────────────────────────────

(defn derivatives
  "Continuous-time state derivative for state = [x, x_dot, theta, theta_dot].

  Full (non-linearised) hanging-pendulum-on-trolley with viscous sway damping.
  Trolley acceleration equals the (clamped) command `u`."
  [crane state ^double u]
  (let [[_x _x-dot theta theta-dot] state
        a (clamp u (double (:accel-max crane)))
        L (double (:cable-length crane))
        g (double (:gravity crane))
        zeta-w (* (double (:sway-damping crane)) (natural-frequency crane))
        theta-acc (- (* (- (/ g L)) (Math/sin theta))
                     (* (/ a L) (Math/cos theta))
                     (* 2.0 zeta-w (double theta-dot)))]
    [(double _x-dot) a (double theta-dot) theta-acc]))

(defn step
  "Advance one step by classic RK4 (stable for the stiff sway mode)."
  [crane state ^double u ^double dt]
  (let [add (fn [s k ^double h] (mapv (fn [^double si ^double ki] (+ si (* h ki))) s k))
        k1 (derivatives crane state u)
        k2 (derivatives crane (add state k1 (/ dt 2.0)) u)
        k3 (derivatives crane (add state k2 (/ dt 2.0)) u)
        k4 (derivatives crane (add state k3 dt) u)
        nxt (mapv (fn [^double s ^double a ^double b ^double c ^double d]
                    (+ s (* (/ dt 6.0) (+ a (* 2.0 b) (* 2.0 c) d))))
                  state k1 k2 k3 k4)]
    ;; enforce the trolley velocity envelope (servo limit)
    (assoc nxt 1 (clamp (double (nth nxt 1)) (double (:velocity-max crane))))))

;; ── anti-sway state-feedback controller ──────────────────────────────────────

(defn make-anti-sway-controller
  "PD trolley positioning + sway-rate feedback.

  u = -kp ω² (x - x_target) - kd ω x_dot + k_theta θ + (k_thetad/ω) θ_dot

  Sway terms actively bleed pendulum energy. Their sign is POSITIVE: equilibrium
  is the load hanging at θ=0; a forward push drives θ negative, so positive θ
  feedback stiffens the restoring term."
  [& {:keys [kp kd k-theta k-thetad]
      :or   {kp 0.4 kd 1.7 k-theta 5.0 k-thetad 3.0}}]
  {:kp kp :kd kd :k-theta k-theta :k-thetad k-thetad})

(defn command
  [ctrl crane state ^double x-target]
  (let [[x x-dot theta theta-dot] state
        w (natural-frequency crane)
        u (+ (* (- (double (:kp ctrl))) w w (- (double x) x-target))
             (* (- (double (:kd ctrl))) w (double x-dot))
             (* (double (:k-theta ctrl)) (double theta))
             (* (/ (double (:k-thetad ctrl)) w) (double theta-dot)))]
    (clamp u (double (:accel-max crane)))))

;; ── ZV input shaper (open-loop anti-sway) ────────────────────────────────────

(defn zv-shaper
  "Zero-Vibration (ZV) input shaper impulses [[time_s amplitude] ...].

  Two impulses spaced half a damped sway period cancel residual oscillation.
  Returns normalised amplitudes summing to 1 (Singer-Seering shaper)."
  [crane]
  (let [zeta (double (:sway-damping crane))
        w (natural-frequency crane)
        wd (* w (Math/sqrt (max 1e-9 (- 1.0 (* zeta zeta)))))
        td (/ Math/PI wd)
        k (Math/exp (/ (* (- zeta) Math/PI) (Math/sqrt (max 1e-9 (- 1.0 (* zeta zeta))))))
        a0 (/ 1.0 (+ 1.0 k))
        a1 (/ k (+ 1.0 k))]
    [[0.0 a0] [td a1]]))

;; ── high-level traverse simulation ───────────────────────────────────────────

(defn- make-traverse-result
  [reached settle-time-s residual-sway-m peak-sway-m final-x steps trajectory]
  {:reached reached
   :settle-time-s settle-time-s
   :residual-sway-m residual-sway-m
   :peak-sway-m peak-sway-m
   :final-x final-x
   :steps steps
   :trajectory trajectory})

(defn simulate-traverse
  "Drive the trolley from rest at x=0 to `x-target` under anti-sway control.

  \"Settled\" = trolley within :pos-tol-m of target AND lateral load excursion
  (L·sinθ) within :sway-tol-m AND sway rate near zero."
  [crane ^double x-target
   & {:keys [controller dt max-time-s pos-tol-m sway-tol-m record]
      :or   {dt (/ 1.0 50.0) max-time-s 120.0 pos-tol-m 0.10 sway-tol-m 0.05 record false}}]
  (when (> (Math/abs x-target) (double (:rail-length crane)))
    (throw (ex-info (str "x_target " x-target " exceeds rail_length " (:rail-length crane))
                    {:error :value})))
  (let [ctrl (or controller (make-anti-sway-controller))
        L (double (:cable-length crane))
        n (long (/ max-time-s dt))]
    (loop [i 0
           state [0.0 0.0 0.0 0.0]
           peak 0.0
           settle-time -1.0
           traj (transient [])]
      (if (< i n)
        (let [u (command ctrl crane state x-target)
              state' (step crane state u dt)
              sway (Math/abs (* L (Math/sin (double (nth state' 2)))))
              peak' (max peak sway)
              traj' (if record (conj! traj (vec state')) traj)
              settled (and (<= (Math/abs (- (double (nth state' 0)) x-target)) pos-tol-m)
                           (<= sway sway-tol-m)
                           (<= (Math/abs (double (nth state' 3))) 0.01))]
          (if (and settled (< settle-time 0.0))
            (let [settle-time' (* (inc i) dt)
                  residual (Math/abs (* L (Math/sin (double (nth state' 2)))))]
              (make-traverse-result
                true settle-time' residual peak' (double (nth state' 0)) (inc i)
                (persistent! traj')))
            (recur (inc i) state' peak' settle-time (if record traj' traj))))
        ;; loop finished without settling
        (let [residual (Math/abs (* L (Math/sin (double (nth state 2)))))]
          (make-traverse-result
            (>= settle-time 0.0)
            (if (>= settle-time 0.0) settle-time max-time-s)
            residual peak (double (nth state 0)) i
            (persistent! traj)))))))

(defn lift-cycle-time
  "Single-box cycle time estimate (s): hoist-up → traverse → hoist-down.

  The traverse term reuses the anti-sway settle time so the estimate reflects
  real sway-limited motion."
  [crane ^double traverse-m ^double hoist-up-m ^double hoist-down-m
   & {:keys [hoist-speed-mps] :or {hoist-speed-mps 1.5}}]
  (let [res (simulate-traverse crane traverse-m)
        hoist (/ (+ hoist-up-m hoist-down-m) (max 1e-6 (double hoist-speed-mps)))]
    (+ (double (:settle-time-s res)) hoist)))

(defn moves-per-hour
  "Convert a per-box cycle time to the terminal productivity KPI."
  ^double [^double cycle-time-s]
  (when (<= cycle-time-s 0)
    (throw (ex-info "cycle_time_s must be positive" {:error :value})))
  (/ 3600.0 cycle-time-s))
