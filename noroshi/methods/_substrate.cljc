(ns noroshi.methods._substrate
  "_substrate — the slice of the shared infra-robotics substrate noroshi's fibre_loop needs.
  Faithful Clojure port of the relevant parts of 20-actors/kuni-umi/robotics/{control,plant,safety}.py
  (re-exported by methods/_substrate.py). Self-contained; no external requires.

  Provides: PID (limited PID w/ anti-windup), simulate (closed-loop runner), and the
  structural safety gates (assert-civilian / require-member-signature / witness-quorum-ok)
  plus the SafetyError contract. A `plant` here is any map-backed value with `measure`/`step`
  fns passed in; fibre_loop supplies its own CableLayPlant via the `plant-fns` protocol below."
  (:require [clojure.string :as str]))

;; ── safety gates (kuni-umi/robotics/safety.py) ──────────────────────────────────
(def MIN-WITNESS-SIGS 2)

(def FORBIDDEN-USES
  ["weapon" "directed-energy" "munition" "fire-control" "interdiction"
   "covert-force" "surveillance-targeting"])

(defn safety-error
  "Raised when a structural safety / charter gate refuses an operation."
  [msg] (ex-info msg {:type :safety-error}))

(defn assert-civilian
  "Closed-world civilian-use gate (N1). Raise unless `use` is explicitly permitted."
  [use permitted]
  (when (some #{use} FORBIDDEN-USES)
    (throw (safety-error
            (str "N1: use '" use "' is a forbidden-force use and can never be energised "
                 "(Mission Charter §1.12 constitutional invariant)"))))
  (when-not (some #{use} permitted)
    (throw (safety-error
            (str "N1: use '" use "' is not in the civilian allowlist " (vec permitted) "; "
                 "closed-world refusal (only explicitly-permitted civilian uses run)")))))

(defn require-member-signature
  "No-server-key gate (G15/G7). Raise unless a member/operator signs and the platform holds no key."
  ([member-sig] (require-member-signature member-sig ""))
  ([member-sig server-sig]
   (when (and server-sig (not= server-sig ""))
     (throw (safety-error
             (str "G15/G7 violation: a server/platform signature was supplied; the platform "
                  "holds no key and never signs actuation (ADR-2605231525)"))))
   (when (or (nil? member-sig) (= member-sig ""))
     (throw (safety-error
             (str "G15/G7 violation: a member/operator signature is required to authorise "
                  "any actuation (no-server-key)"))))))

(defn witness-quorum-ok
  "Witness quorum ≥2 independent robot DIDs (G8). Returns a map (does not raise)."
  [witness-sigs]
  (cond
    (< (count witness-sigs) MIN-WITNESS-SIGS)
    {"ok" false
     "reason" (str "witness quorum " (count witness-sigs) " < " MIN-WITNESS-SIGS " (G8 constitutional)")
     "escalate_council_lv6" true}
    (< (count (set witness-sigs)) MIN-WITNESS-SIGS)
    {"ok" false "reason" "duplicate witness DIDs detected (G8)" "escalate_council_lv6" true}
    :else {"ok" true "reason" "witness quorum satisfied"}))

;; ── round helper (Python round HALF_EVEN) ───────────────────────────────────────
(defn- py-round [x n]
  #?(:clj (-> (java.math.BigDecimal. (double x))
              (.setScale (int n) java.math.RoundingMode/HALF_EVEN) (.doubleValue))
     :default (let [f (Math/pow 10 n)] (/ (Math/round (* x f)) f))))

;; ── PID (limited PID with anti-windup) ──────────────────────────────────────────
;; Stateful via an atom holding {:integral :prev-error :saturated}.
(defn make-pid
  [& {:keys [kp ki kd out_min out_max]
      :or {kp 0.0 ki 0.0 kd 0.0 out_min ##-Inf out_max ##Inf}}]
  (atom {:kp kp :ki ki :kd kd :out_min out_min :out_max out_max
         :integral 0.0 :prev-error nil :saturated false}))

(defn pid-reset! [pid]
  (swap! pid assoc :integral 0.0 :prev-error nil :saturated false))

(defn pid-step!
  [pid error dt]
  (let [{:keys [kp ki kd out_min out_max integral prev-error]} @pid
        deriv (if (and (some? prev-error) (> dt 0)) (/ (- error prev-error) dt) 0.0)
        tentative (+ integral (* error dt))
        raw (+ (* kp error) (* ki tentative) (* kd deriv))
        clamped (min out_max (max out_min raw))
        saturated (not= clamped raw)]
    (swap! pid assoc
           :saturated saturated
           :integral (if saturated integral tentative)
           :prev-error error)
    clamped))

;; ── simulate (closed-loop runner) ───────────────────────────────────────────────
;; `plant` is an atom; `measure-fn` reads its PV; `step-fn` advances it (mutating the atom).
(defn simulate
  "Run a PID closed loop against a plant and report convergence (mirrors control.simulate).
  plant: atom of plant state. measure-fn: (plant)->pv. step-fn: (plant cmd dt)-> mutates plant."
  [plant measure-fn step-fn pid setpoint steps dt & {:keys [tol settle_window] :or {tol 1e-3 settle_window 10}}]
  (pid-reset! pid)
  (let [errors (object-array steps)
        max-abs (atom 0.0)]
    (dotimes [k steps]
      (let [pv (measure-fn @plant)
            error (- setpoint pv)
            cmd (pid-step! pid error dt)]
        (aset errors k (Math/abs (double error)))
        (swap! max-abs max (Math/abs (double error)))
        (step-fn plant cmd dt)))
    (let [final-pv (measure-fn @plant)
          steady-error (- setpoint final-pv)
          errs (vec errors)
          ;; settling_step: first index from which every later error < tol.
          settling-step (loop [i 0]
                          (cond
                            (>= i (count errs)) -1
                            (every? #(< % tol) (subvec errs i)) i
                            :else (recur (inc i))))
          tail (if (>= (count errs) settle_window) (subvec errs (- (count errs) settle_window)) errs)
          converged (boolean (and (seq tail) (every? #(< % tol) tail)))]
      {"setpoint" setpoint
       "final_value" (py-round final-pv 6)
       "steady_error" (py-round steady-error 6)
       "converged" converged
       "settling_step" settling-step
       "max_abs_error" (py-round @max-abs 6)
       "steps" steps})))
