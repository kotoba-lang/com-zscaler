(ns mizuho.methods.-substrate
  "_substrate — the shared infra-robotics substrate primitives mizuho/methods needs.

  1:1 Clojure port of the SUBSET of 20-actors/kuni-umi/robotics/{control,safety}.py
  that the mizuho method modules (chlorination, water_supply) actually import:
  PID, ControlResult, simulate, SafetyError, assert_civilian -- plus the structural
  safety gates cells/water_supply/state_machine.cljc needs (require-member-signature /
  witness-quorum-ok), ported from the same kuni-umi/robotics/safety.py source (mirrors
  the noroshi/methods/_substrate.cljc precedent).

  The Python `_substrate.py` merely re-exports the kuni-umi/robotics engine over a
  sys.path insert; here we INLINE the needed primitives so the port is
  self-contained (requires only clojure.core + sibling mizuho.* modules), per the
  house conversion style. The Rust BFB / hard-RT deployment is irrelevant to these
  floating-point :representative twins.

  Data is value-keyed; Python ':…' keyword strings stay strings. A `Plant` is any
  value implementing the {measure, step} protocol; controllers (`PID`,
  `ClampedDoser`) implement {reset, step}. Because Python's PID/plant/doser are
  mutable objects, we model them as atoms holding a state map + a fns map so
  `simulate` can drive them imperatively exactly like the Python loop.

  Omits the Python __main__ demo (there is none for the substrate)."
  (:require [clojure.string :as str]))

;; ── SafetyError ────────────────────────────────────────────────────────────────
;; Python: class SafetyError(Exception). We model it as an ex-info-tagged exception
;; so callers can (thrown? Exception …). A helper raises it.

(defn safety-error
  "Raise the structural safety/charter refusal (Python `raise SafetyError(msg)`)."
  [msg]
  (throw (ex-info msg {:type :safety-error})))

;; ── N1 civilian-use gate (safety.py) ────────────────────────────────────────────

(def FORBIDDEN_USES
  ["weapon" "directed-energy" "munition" "fire-control" "interdiction"
   "covert-force" "surveillance-targeting"])

(defn assert-civilian
  "Closed-world civilian-use gate (N1). Raise unless `use` is explicitly permitted.

  Anything in the cross-domain forbidden anchors is rejected even if a caller
  mistakenly lists it (port of safety.py:assert_civilian)."
  [use permitted]
  (cond
    (some #(= % use) FORBIDDEN_USES)
    (safety-error
     (str "N1: use " (pr-str use) " is a forbidden-force use and can never be "
          "energised (Mission Charter §1.12 constitutional invariant)"))
    (not (some #(= % use) permitted))
    (safety-error
     (str "N1: use " (pr-str use) " is not in the civilian allowlist "
          (pr-str (vec permitted)) "; closed-world refusal (only explicitly-"
          "permitted civilian uses run)"))
    :else nil))

;; ── member-signature + witness-quorum gates (safety.py) ──────────────────────────
;; Needed by cells/water_supply/state_machine.cljc's commissioning/dispatch phases;
;; ported from the same kuni-umi/robotics/safety.py source as assert-civilian above.

(def MIN-WITNESS-SIGS 2)

(defn require-member-signature
  "No-server-key gate (G15/G7). Raise unless a member/operator signs and the
  platform holds no key."
  ([member-sig] (require-member-signature member-sig ""))
  ([member-sig server-sig]
   (when (and server-sig (not= server-sig ""))
     (safety-error
      (str "G15/G7 violation: a server/platform signature was supplied; the platform "
           "holds no key and never signs actuation (ADR-2605231525)")))
   (when (or (nil? member-sig) (= member-sig ""))
     (safety-error
      (str "G15/G7 violation: a member/operator signature is required to authorise "
           "any actuation (no-server-key)")))))

(defn witness-quorum-ok
  "Witness quorum >=2 independent robot DIDs (G8). Returns a map (does not raise)."
  [witness-sigs]
  (cond
    (< (count witness-sigs) MIN-WITNESS-SIGS)
    {"ok" false
     "reason" (str "witness quorum " (count witness-sigs) " < " MIN-WITNESS-SIGS " (G8 constitutional)")
     "escalate_council_lv6" true}
    (< (count (set witness-sigs)) MIN-WITNESS-SIGS)
    {"ok" false "reason" "duplicate witness DIDs detected (G8)" "escalate_council_lv6" true}
    :else {"ok" true "reason" "witness quorum satisfied"}))

;; ── PID (control.py) — mutable controller as an atom of state + a fns map ────────
;; Mirrors the dataclass PID with anti-windup (conditional integration). The
;; returned value is a map {:kind :pid :state (atom {…}) :gains {…}} and the
;; protocol fns reset!/step! operate on it.

(defn make-pid
  "Limited PID with anti-windup — mirrors open-ot PID_LIMITED / control.py:PID.

  Keys: :kp (req), :ki (0.0), :kd (0.0), :out-min (-inf), :out-max (+inf)."
  [& {:keys [kp ki kd out-min out-max]
      :or {ki 0.0 kd 0.0
           out-min #?(:clj Double/NEGATIVE_INFINITY :cljs (- js/Infinity))
           out-max #?(:clj Double/POSITIVE_INFINITY :cljs js/Infinity)}}]
  {:kind :pid
   :gains {:kp (double kp) :ki (double ki) :kd (double kd)
           :out-min (double out-min) :out-max (double out-max)}
   :state (atom {:integral 0.0 :prev-error nil :saturated false})})

(defn pid-reset! [pid]
  (reset! (:state pid) {:integral 0.0 :prev-error nil :saturated false}))

(defn pid-step!
  "Port of PID.step(error, dt). Conditional integration: commit the integral only
  if the unclamped command did not saturate."
  [pid error dt]
  (let [{:keys [kp ki kd out-min out-max]} (:gains pid)
        st @(:state pid)
        prev (:prev-error st)
        deriv (if (and (some? prev) (> dt 0)) (/ (- error prev) dt) 0.0)
        tentative-integral (+ (:integral st) (* error dt))
        raw (+ (* kp error) (* ki tentative-integral) (* kd deriv))
        clamped (min out-max (max out-min raw))
        saturated (not= clamped raw)]
    (reset! (:state pid)
            {:integral (if saturated (:integral st) tentative-integral)
             :prev-error error
             :saturated saturated})
    clamped))

;; ── generic reset!/step! dispatch over substrate controllers ─────────────────────
;; A controller is any map with a :kind. `simulate` calls reset! then step! each tick.
;; mizuho.methods.chlorination's ClampedDoser is also a :kind map handled there; to
;; keep the substrate self-contained, dispatch on a :reset!/:step! fn pair when the
;; controller supplies them, else fall back to the PID protocol.

(defn controller-reset! [c]
  (if-let [f (:reset! c)] (f c) (pid-reset! c)))

(defn controller-step! [c error dt]
  (if-let [f (:step! c)] (f c error dt) (pid-step! c error dt)))

;; A Plant is a map {:kind … :measure (fn [plant] …) :step! (fn [plant cmd dt] …)}.
(defn plant-measure [p] ((:measure p) p))
(defn plant-step! [p cmd dt] ((:step! p) p cmd dt))

;; ── ControlResult + simulate (control.py) ───────────────────────────────────────

(defn- round6 [x]
  ;; Python round(x, 6) — banker's rounding via BigDecimal HALF_EVEN.
  #?(:clj (-> (java.math.BigDecimal. (double x))
              (.setScale 6 java.math.RoundingMode/HALF_EVEN)
              (.doubleValue))
     :cljs (/ (js/Math.round (* (double x) 1e6)) 1e6)))

(defn simulate
  "Run a controller closed loop against a plant and report convergence.

  1:1 port of control.py:simulate. `controller` and `plant` are the substrate maps
  above (mutated through their state atoms). Returns a result map with the same
  fields control.py's ControlResult carries, string-free (Clojure keys):
    :setpoint :final-value :steady-error :converged :settling-step :max-abs-error
    :steps :trajectory  (vector of [t pv cmd])."
  [plant controller setpoint steps dt
   & {:keys [tol settle-window] :or {tol 1e-3 settle-window 10}}]
  (controller-reset! controller)
  (let [tol (double tol)
        steps (long steps)
        dt (double dt)
        ;; forward loop, mutating plant + controller exactly like the Python for-loop
        [traj errors max-abs]
        (loop [k 0, traj (transient []), errors (transient []), max-abs 0.0]
          (if (< k steps)
            (let [pv (plant-measure plant)
                  error (- setpoint pv)
                  cmd (controller-step! controller error dt)
                  ae (Math/abs (double error))]
              (conj! traj [(round6 (* k dt)) pv cmd])
              (conj! errors ae)
              (plant-step! plant cmd dt)
              (recur (inc k) traj errors (max max-abs ae)))
            [(persistent! traj) (persistent! errors) max-abs]))
        final-pv (plant-measure plant)
        steady-error (- setpoint final-pv)
        n (count errors)
        ;; settling_step: first index from which every later error < tol.
        settling-step
        (loop [i 0]
          (cond
            (>= i n) -1
            (every? #(< % tol) (subvec errors i)) i
            :else (recur (inc i))))
        tail (if (>= n settle-window) (subvec errors (- n settle-window)) errors)
        converged (boolean (and (seq tail) (every? #(< % tol) tail)))]
    {:setpoint setpoint
     :final-value (round6 final-pv)
     :steady-error (round6 steady-error)
     :converged converged
     :settling-step settling-step
     :max-abs-error (round6 max-abs)
     :steps steps
     :trajectory traj}))
