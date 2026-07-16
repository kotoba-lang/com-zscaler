(ns mizuho.methods.chlorination
  "chlorination — mizuho residual-disinfection dosing loop (R0 :representative).
  1:1 Clojure port of methods/chlorination.py (ADR-2606091800).

  Proves the dosing loop holds a safe free-chlorine residual in distribution: the
  residual decays (demand + time), a secondary-PI doser raises it back to a target
  (default 0.5 mg/L), and — critically — the dose is STRUCTURALLY CLAMPED so the
  modeled residual can NEVER exceed the regulatory ceiling MAX-RESIDUAL-MGL = 4.0
  mg/L (WHO guideline / US-EPA MRDL).

  mizuho constitutional gates:
    G4 — a plain PI over a lumped residual model, never commercial UV/dosing firmware.
    G6 (anti-paternalism, no mandatory fluoridation): chlorine disinfection
        (\"disinfect\") runs WITHOUT per-member consent; FLUORIDE (\"fluoridate\")
        REFUSES (SafetyError) unless per-member-consent=true.
    G7 — Murakumo-only inference (not used in this deterministic loop).
    G10 — live dosing consent-gated; offline sim only; cell.py .solve() Council-gated.

  House style: Python ':…' keyword strings stay strings; kebab keyword keys in the
  result record; pure-at-edges; portable .cljc. round() via HALF_EVEN exact BigDecimal.

  The HARD CLAMP (≤4 mg/L) is enforced in TWO places (defence in depth):
    1. ClampedDoser.step — caps the dose so max-dose·dt ≤ ceiling − current.
    2. ResidualChlorinePlant.step — structural ceiling on the residual itself.
  Neither depends on gains — no choice of kp/ki can drive the residual over the limit."
  (:require [mizuho.methods.substrate :as sub]))

;; WHO guideline value / US-EPA maximum residual disinfectant level for free
;; chlorine. A modeled residual can NEVER exceed this — enforced by a structural
;; clamp on the doser command, not merely by tuning.
(def max-residual-mgl 4.0)

;; Agents mizuho can model dosing for. "disinfect" = free chlorine (community-wide,
;; no per-member consent). "fluoridate" = fluoride (personal supplementation;
;; requires per-member consent under G6 anti-paternalism).
(def permitted-agents ["disinfect" "fluoridate"])

;; ── ResidualChlorinePlant (free-chlorine residual dynamics) ──────────────────
;;   dC/dt = dose_command − k_decay·C
;; with a structural hard ceiling at MAX-RESIDUAL-MGL.
(defrecord ResidualChlorinePlant [k-decay state])

(defn residual-chlorine-plant
  [& {:keys [residual-mgl k-decay] :or {residual-mgl 0.0 k-decay 0.05}}]
  (->ResidualChlorinePlant (double k-decay) (atom {:residual (double residual-mgl)})))

(extend-protocol sub/Plant
  ResidualChlorinePlant
  (measure [plant] (:residual @(:state plant)))
  (plant-step! [plant command dt]
    ;; command = dose rate (mg/L per second). Decay is first-order.
    (swap! (:state plant)
           (fn [s]
             (let [c (:residual s)
                   dcdt (- command (* (:k-decay plant) c))
                   c (+ c (* dcdt (double dt)))
                   c (if (< c 0.0) 0.0 c)
                   ;; Structural hard ceiling: the modeled residual can NEVER
                   ;; exceed the regulatory MRDL, regardless of controller command.
                   c (if (> c max-residual-mgl) max-residual-mgl c)]
               (assoc s :residual c))))
    nil))

;; ── ClampedDoser (a PI doser STRUCTURALLY clamped to the ceiling) ────────────
;; Wraps a substrate PID and implements the Controller contract. Each step caps
;; the dose so that, even instantaneously added, the residual cannot cross the
;; ceiling: max_dose·dt ≤ ceiling − current. The clamp is independent of gains.
(defrecord ClampedDoser [plant pid dt])

(defn clamped-doser [plant pid dt]
  (->ClampedDoser plant pid (double dt)))

(extend-protocol sub/Controller
  ClampedDoser
  (ctrl-reset! [d] (sub/ctrl-reset! (:pid d)))
  (ctrl-step! [d error dt]
    (let [raw0 (sub/ctrl-step! (:pid d) error dt)
          raw (if (< raw0 0.0) 0.0 raw0)
          ;; Hard structural clamp: do not dose more than would reach the ceiling.
          headroom (- max-residual-mgl (sub/measure (:plant d)))
          max-dose-rate (if (> dt 0) (max 0.0 (/ headroom (double dt))) 0.0)]
      (min raw max-dose-rate))))

;; ── DosingResult ─────────────────────────────────────────────────────────────
(defrecord DosingResult
  [agent target-residual-mgl final-residual-mgl max-residual-mgl
   residual-held ceiling-respected settling-seconds representative])

(defn commission-dosing
  "Run the dosing acceptance test. RAISES before any run on a gate violation.
  G6 (anti-paternalism): chlorine disinfection runs without per-member consent;
  fluoride REFUSES unless `per-member-consent=true`. The structural clamp
  guarantees the modeled residual never exceeds MAX-RESIDUAL-MGL."
  [& {:keys [agent target-residual-mgl per-member-consent k-decay kp ki steps dt]
      :or {agent "disinfect" target-residual-mgl 0.5 per-member-consent false
           k-decay 0.05 kp 0.4 ki 0.15 steps 4000 dt 0.1}}]
  (when-not (some #(= agent %) permitted-agents)
    (throw (sub/safety-error
            (str "dosing agent " (pr-str agent) " is not permitted; allowlist "
                 (pr-str (vec permitted-agents)))
            {:agent agent})))
  (when (and (= agent "fluoridate") (not per-member-consent))
    (throw (sub/safety-error
            (str "G6: fluoride dosing requires per_member_consent=True (no mandatory "
                 "fluoridation; anti-paternalism). Chlorine disinfection needs no consent.")
            {:gate "G6" :agent agent})))
  (when (> target-residual-mgl max-residual-mgl)
    (throw (sub/safety-error
            (str "target residual " target-residual-mgl " mg/L exceeds the regulatory "
                 "ceiling " max-residual-mgl " mg/L (WHO/EPA); structurally refused")
            {:gate "ceiling" :target target-residual-mgl})))
  (let [plant (residual-chlorine-plant :residual-mgl 0.0 :k-decay k-decay)
        pid (sub/pid :kp kp :ki ki :out-min 0.0 :out-max max-residual-mgl)
        doser (clamped-doser plant pid dt)
        res (sub/simulate plant doser target-residual-mgl steps dt :tol 1e-3)
        ;; max residual ever modeled across the whole trajectory (pv = 2nd of each triple).
        max-residual (reduce (fn [m [_ pv _]] (max m pv)) 0.0 (:trajectory res))
        settling-step (:settling-step res)
        settling-seconds (if (>= settling-step 0) (* settling-step (double dt)) -1.0)]
    (->DosingResult
     agent
     target-residual-mgl
     (sub/py-round-n (:final-value res) 4)
     (sub/py-round-n max-residual 4)
     (:converged res)
     (<= max-residual (+ max-residual-mgl 1e-9))
     (sub/py-round-n settling-seconds 3)
     true)))

(defn to-datoms
  "Project a dosing acceptance result into kotoba EAVT-shaped datoms. Aggregate-only."
  [result source-id]
  {":water.dosing/source-id" source-id
   ":water.dosing/agent" (:agent result)
   ":water.dosing/target-residual-mgl" (:target-residual-mgl result)
   ":water.dosing/final-residual-mgl" (:final-residual-mgl result)
   ":water.dosing/max-residual-mgl" (:max-residual-mgl result)
   ":water.dosing/ceiling-mgl" max-residual-mgl
   ":water.dosing/residual-held" (:residual-held result)
   ":water.dosing/ceiling-respected" (:ceiling-respected result) ;; G: hard clamp held
   ":water.dosing/settling-seconds" (:settling-seconds result)
   ":water.dosing/representative" (:representative result)        ;; G10
   ":water.dosing/server-held-key" false                         ;; no-server-key
   ":water.dosing/dry-run" true})                                ;; G10: R0 offline only

(def ^{::order true} member-order
  [:max-residual-mgl :permitted-agents :residual-chlorine-plant :clamped-doser
   :commission-dosing :to-datoms])
