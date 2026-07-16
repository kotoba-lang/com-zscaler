(ns mizuho.methods.substrate
  "substrate — the shared infra-robotics control substrate for mizuho/methods.
  1:1 Clojure port of the PID / Droop / simulate primitives that the Python
  `_substrate.py` re-exports from 20-actors/kuni-umi/robotics/control.py +
  safety.py (ADR-2606091800). The leading-underscore Python module name munges
  badly in SCI, so the ns is the clean `mizuho.methods.substrate`.

  This is the deterministic floating-point :representative twin of an open-ot
  field-tier control loop — the Rust BFB under a certified safety PLC owns the
  hard-RT deployment; this module never touches hardware.

  Holds:
    PID    — limited PID with conditional-integration anti-windup (PID_LIMITED)
    Droop  — proportional frequency/voltage droop (DROOP_P_F)
    simulate — the closed-loop runner + ControlResult
    assert-civilian / SafetyError — the N1 closed-world civilian-use gate

  CONTROLLER PROTOCOL (what simulate calls): a controller is a stateful object
  exposing (ctrl-reset! ctrl) and (ctrl-step! ctrl error dt) → command. PID is the
  reference; chlorination's ClampedDoser wraps a PID with the same contract.
  Implemented here as a small protocol so a plant/controller can carry mutable
  state in atoms while keeping the public fns pure-at-the-edges.

  House style: Python ':…' keyword strings stay strings; kebab keyword keys in
  the returned result map; pure-at-edges; portable .cljc.

  ALL float arithmetic matches Python exactly: round(x, n) reproduced via
  HALF_EVEN on the exact BigDecimal of the double (Java String.format is
  HALF_UP, so it is NOT used)."
  #?(:clj (:import [java.math BigDecimal RoundingMode])))

;; ── Python round(x, n) — HALF_EVEN on the exact double ───────────────────────
#?(:clj
   (defn py-round-n
     "Python round(x, n) → nearest, HALF_EVEN on the exact double value, as a double."
     [x n]
     (-> (BigDecimal. (double x))
         (.setScale (int n) RoundingMode/HALF_EVEN)
         (.doubleValue)))
   :cljs
   (defn py-round-n [x n]
     (let [f (Math/pow 10 n)
           y (* (double x) f)
           r (Math/round y)
           ;; banker's rounding tie-break
           r (if (and (== (Math/abs (- y (Math/floor y))) 0.5)
                      (odd? (long r)))
               (- r 1) r)]
       (/ r f))))

;; ── SafetyError ──────────────────────────────────────────────────────────────
;; Python raises _substrate.SafetyError. In Clojure a charter/gate refusal is an
;; ex-info carrying {:error :safety-error …}; safety-error? recognises it.
(defn safety-error
  "Construct the structural-safety/charter refusal (Python SafetyError)."
  ([msg] (safety-error msg {}))
  ([msg data] (ex-info msg (assoc data :error :safety-error))))

(defn safety-error?
  "True iff ex is a substrate SafetyError (ex-info tagged :safety-error)."
  [ex]
  (and (instance? #?(:clj clojure.lang.ExceptionInfo :cljs cljs.core/ExceptionInfo) ex)
       (= :safety-error (:error (ex-data ex)))))

;; ── N1 closed-world civilian-use gate (safety.py assert_civilian) ────────────
;; FORBIDDEN_USES — cross-domain force anchors; rejected even if a caller lists them.
(def forbidden-uses
  ["weapon" "directed-energy" "munition" "fire-control"
   "interdiction" "covert-force" "surveillance-targeting"])

(defn assert-civilian
  "Closed-world civilian-use gate (N1). RAISE (SafetyError) unless `use` is
  explicitly in the domain `permitted` allowlist and not a forbidden anchor."
  [use permitted]
  (when (some #(= use %) forbidden-uses)
    (throw (safety-error
            (str "N1: use " (pr-str use) " is a forbidden-force use and can never be energised "
                 "(Mission Charter §1.12 constitutional invariant)")
            {:use use})))
  (when-not (some #(= use %) permitted)
    (throw (safety-error
            (str "N1: use " (pr-str use) " is not in the civilian allowlist " (pr-str (vec permitted)) "; "
                 "closed-world refusal (only explicitly-permitted civilian uses run)")
            {:use use})))
  nil)

;; ── controller protocol ──────────────────────────────────────────────────────
(defprotocol Controller
  (ctrl-reset! [ctrl] "Clear controller state.")
  (ctrl-step! [ctrl error dt] "Advance one step; return the (clamped) command."))

;; ── PID (control.py PID — PID_LIMITED, conditional-integration anti-windup) ──
;; State (integral / prev-error / saturated) lives in an atom so simulate can
;; reset+step in the Python iteration order while the constructor stays pure.
(defrecord PID [kp ki kd out-min out-max state])

(defn pid
  "Limited PID with anti-windup — mirrors open-ot PID_LIMITED.
  Output is clamped to [out-min, out-max]; integration is held (conditional
  integration) whenever the unclamped command saturates, so the integral term
  cannot wind up while the actuator is railed."
  [& {:keys [kp ki kd out-min out-max]
      :or {kp 0.0 ki 0.0 kd 0.0
           out-min #?(:clj Double/NEGATIVE_INFINITY :cljs (- js/Infinity))
           out-max #?(:clj Double/POSITIVE_INFINITY :cljs js/Infinity)}}]
  (->PID (double kp) (double ki) (double kd) (double out-min) (double out-max)
         (atom {:integral 0.0 :prev-error nil :saturated false})))

(extend-protocol Controller
  PID
  (ctrl-reset! [p] (reset! (:state p) {:integral 0.0 :prev-error nil :saturated false}) nil)
  (ctrl-step! [p error dt]
    (let [{:keys [integral prev-error]} @(:state p)
          deriv (if (and (some? prev-error) (> dt 0))
                  (/ (- error prev-error) dt)
                  0.0)
          tentative-integral (+ integral (* error dt))
          raw (+ (* (:kp p) error) (* (:ki p) tentative-integral) (* (:kd p) deriv))
          clamped (min (:out-max p) (max (:out-min p) raw))
          saturated (not= clamped raw)]
      (swap! (:state p)
             (fn [s] (cond-> (assoc s :prev-error error :saturated saturated)
                       (not saturated) (assoc :integral tentative-integral))))
      clamped)))

;; ── Droop (control.py Droop — DROOP_P_F) ─────────────────────────────────────
(defrecord Droop [nominal droop-r p-base p-min p-max])

(defn droop
  "Proportional frequency/voltage droop — mirrors open-ot DROOP_P_F.
  P = p-base + (nominal − measured)/droop-r, clamped to [p-min, p-max]."
  [& {:keys [nominal droop-r p-base p-min p-max]
      :or {p-base 0.0
           p-min #?(:clj Double/NEGATIVE_INFINITY :cljs (- js/Infinity))
           p-max #?(:clj Double/POSITIVE_INFINITY :cljs js/Infinity)}}]
  (->Droop (double nominal) (double droop-r) (double p-base) (double p-min) (double p-max)))

(defn droop-command
  [d measured]
  (let [p (+ (:p-base d) (/ (- (:nominal d) measured) (:droop-r d)))]
    (min (:p-max d) (max (:p-min d) p))))

;; ── Plant protocol ───────────────────────────────────────────────────────────
;; A Plant exposes (measure plant) → process-var and (plant-step! plant cmd dt)
;; → advance one step (mutating its internal volume/residual atom). The concrete
;; plants live in water_supply.cljc / chlorination.cljc.
(defprotocol Plant
  (measure [plant] "Current process variable.")
  (plant-step! [plant command dt] "Advance the plant one step under `command`."))

;; ── ControlResult ────────────────────────────────────────────────────────────
;; Trajectory is a vector of [t process-var command] triples (Python list of 3-tuples).
(defrecord ControlResult
  [setpoint final-value steady-error converged settling-step
   max-abs-error steps trajectory])

(defn simulate
  "Run a PID closed loop against a plant and report convergence (control.py simulate).
  `converged` iff |error| < tol for the last `settle-window` steps. `settling-step`
  is the first index from which the error never again exceeds tol; -1 if never.
  Deterministic: same inputs ⇒ same trajectory.

  Reproduces the Python iteration order EXACTLY: per step measure → error →
  controller.step → append (round(k*dt,6), pv, cmd) → record |error| → plant.step."
  [plant controller setpoint steps dt & {:keys [tol settle-window]
                                         :or {tol 1e-3 settle-window 10}}]
  (ctrl-reset! controller)
  (let [setpoint (double setpoint)
        dt (double dt)]
    (loop [k 0
           traj (transient [])
           errors (transient [])
           max-abs 0.0]
      (if (< k steps)
        (let [pv (measure plant)
              error (- setpoint pv)
              cmd (ctrl-step! controller error dt)
              ae (Math/abs error)
              traj (conj! traj [(py-round-n (* k dt) 6) pv cmd])
              errors (conj! errors ae)]
          (plant-step! plant cmd dt)
          (recur (inc k) traj errors (max max-abs ae)))
        (let [errors (persistent! errors)
              traj (persistent! traj)
              final-pv (measure plant)
              steady-error (- setpoint final-pv)
              n (count errors)
              ;; settling-step: first index from which every later error < tol
              settling-step
              (loop [i 0]
                (cond
                  (>= i n) -1
                  (every? #(< % tol) (subvec errors i)) i
                  :else (recur (inc i))))
              tail (if (>= n settle-window) (subvec errors (- n settle-window)) errors)
              converged (boolean (and (seq tail) (every? #(< % tol) tail)))]
          (->ControlResult setpoint
                           (py-round-n final-pv 6)
                           (py-round-n steady-error 6)
                           converged
                           settling-step
                           (py-round-n max-abs 6)
                           steps
                           traj))))))
