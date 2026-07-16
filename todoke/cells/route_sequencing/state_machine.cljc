(ns todoke.cells.route-sequencing.state-machine
  "Phase state machine for the todoke 届け route_sequencing (順路) cell.

  1:1 port of `cells/route_sequencing/state_machine.py` (ADR-2606042300).

  This cell turns an accepted deliveryJob into a safety-validated lastMileRoute. The route
  math is the SAME algorithm as the Rust `todoke-route` crate, reached through
  `todoke.methods.last-mile` (one model, two runtimes — ADR-2606033600). The G7 safety
  envelope is enforced here as a hard refusal: a plan that exceeds the sidewalk-speed cap,
  enters a vehicular road (N2), or assumes SAE level > 4 (N2) is REFUSED rather than yielding
  a route. (Python clamps nothing — the cell records the refusal, mirroring the Rust `Err`.)

  Transitions are pure and unit-tested; the cell's .solve() raises until Council activation.

  Conventions (shionome/cells house style):
    - dataclass RouteState → a plain map with kebab keyword keys
    - RoutePhase enum value identities (\"init\"/…/\"route_emitted\") stay strings
    - transitions are pure fns; the EnvelopeViolation is an ex-info with `:envelope-violation`
      true, exactly as `todoke.methods.last-mile/envelope-violation` produces."
  (:require [clojure.string :as str]
            [todoke.methods.last-mile :as last-mile]))

;; ── RoutePhase (enum — Python value identities preserved) ────────

(def route-phases
  "The closed RoutePhase vocabulary. Keyed by the idiomatic Clojure enum
  keyword; the value is the Python `RoutePhase.<X>.value` string identity."
  {:init             "init"
   :job-loaded       "job_loaded"
   :envelope-checked "envelope_checked"
   :sequenced        "sequenced"
   :route-emitted    "route_emitted"})

(def route-phase-init             (:init route-phases))             ;; "init"
(def route-phase-job-loaded       (:job-loaded route-phases))       ;; "job_loaded"
(def route-phase-envelope-checked (:envelope-checked route-phases)) ;; "envelope_checked"
(def route-phase-sequenced        (:sequenced route-phases))        ;; "sequenced"
(def route-phase-route-emitted    (:route-emitted route-phases))    ;; "route_emitted"

;; ── RouteState (dataclass → plain map, kebab keys, field defaults) ──

(def route-state
  "RouteState default value — the @dataclass field defaults as a plain map."
  {:phase        route-phase-init   ;; RoutePhase.INIT.value
   :job-id       "did:web:todoke.etzhayyim.com/job/demo-0001"
   :sae-level    4
   :commanded-mps 1.5
   :stops        []   ;; list of maps {:id :x :y :zone}
   :order        []
   :length-m     0.0
   :envelope-ok  false
   :refusal      ""
   :payload      {}})

(defn make-route-state
  "Construct a RouteState map from a partial cell-state map, filling the
  dataclass defaults (RouteState(**state.get(\"cell_state\", {}))). Unknown
  keys → ex-info (closed RouteState surface — RouteState(**...) would
  TypeError on an unexpected kwarg)."
  [cs]
  (let [cs (or cs {})
        allowed (set (keys route-state))
        extra (remove allowed (keys cs))]
    (when (seq extra)
      (throw (ex-info (str "unknown RouteState field(s): " (vec extra))
                      {:todoke/closed-vocab true :extra (vec extra)})))
    (merge route-state cs)))

;; ── helpers ──────────────────────────────────────────────────────

(defn- round3
  "round(x, 3) for the route length figure."
  [x]
  (/ (Math/round (* (double x) 1000.0)) 1000.0))

(defn- to-stops
  "Normalize raw stop maps into the {:id :x :y :zone} shape last-mile expects,
  mirroring `_to_stops` (int id, float x/y, lower-cased zone string)."
  [raw]
  (mapv (fn [s]
          {:id   (int (:id s))
           :x    (double (:x s))
           :y    (double (:y s))
           :zone (str/lower-case (str (:zone s)))})
        raw))

(defn- plan
  "Call last-mile/plan-last-mile with the cell's positional → keyword args."
  [stops sae-level commanded-mps]
  (last-mile/plan-last-mile (to-stops stops) :sae-level sae-level :commanded-mps commanded-mps))

;; ── transitions (pure; each returns {:cell-state … :next-node …}) ──

(defn transition-to-job-loaded
  "Port of `transition_to_job_loaded`. Loads the job into the cell state.
  Top-level state keys override the cell-state defaults (the Python
  `state.get(\"k\", cs.k)` override semantics)."
  [state]
  (let [state (or state {})
        cs (make-route-state (:cell-state state))
        cs (assoc cs
                  :phase route-phase-job-loaded
                  :job-id (get state :job-id (:job-id cs))
                  :stops (vec (get state :stops []))
                  :sae-level (int (get state :sae-level (:sae-level cs)))
                  :commanded-mps (double (get state :commanded-mps (:commanded-mps cs))))]
    {:cell-state cs :next-node "check_envelope"}))

(defn transition-to-envelope-checked
  "Port of `transition_to_envelope_checked`. G7 dry-run: re-uses the same
  plan-last-mile envelope gate the emit step uses. On an EnvelopeViolation the
  cell records the refusal (it does NOT raise — silent clamping would hide a
  G7 breach; refusal IS the behaviour)."
  [state]
  (let [cs (make-route-state (:cell-state state))
        cs (assoc cs :phase route-phase-envelope-checked)]
    (try
      (plan (:stops cs) (:sae-level cs) (:commanded-mps cs))
      {:cell-state (assoc cs :envelope-ok true :refusal "") :next-node "sequence"}
      (catch clojure.lang.ExceptionInfo e
        (if (:envelope-violation (ex-data e))
          {:cell-state (assoc cs :envelope-ok false :refusal (.getMessage e))
           :next-node "refused"}
          (throw e))))))

(defn transition-to-sequenced
  "Port of `transition_to_sequenced`. Sequencing a refused job is itself a G7
  violation (raises). Otherwise computes the order + length."
  [state]
  (let [cs (make-route-state (:cell-state state))]
    (when-not (:envelope-ok cs)
      (throw (last-mile/envelope-violation
              (str "G7: cannot sequence a refused job (" (:refusal cs) ")"))))
    (let [[order length] (plan (:stops cs) (:sae-level cs) (:commanded-mps cs))
          cs (assoc cs
                    :phase route-phase-sequenced
                    :order order
                    :length-m (round3 length))]
      {:cell-state cs :next-node "emit_route"})))

(defn transition-to-route-emitted
  "Port of `transition_to_route_emitted`. Builds the lastMileRoute payload,
  surfacing the N2 SAE-ceiling invariant on the record."
  [state]
  (let [cs (make-route-state (:cell-state state))
        cs (assoc cs
                  :phase route-phase-route-emitted
                  :payload {:last-mile-route
                            {:jobId (:job-id cs)
                             :order (:order cs)
                             :lengthM (:length-m cs)
                             :saeLevel (:sae-level cs)
                             :commandedMps (:commanded-mps cs)
                             :envelopeOk (:envelope-ok cs)
                             ;; N2 invariant surfaced on the record
                             :saeWithinCeiling (<= (:sae-level cs) 4)}})]
    {:cell-state cs :next-node "end"}))
