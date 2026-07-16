(ns shionome.cells.regime-observer.state-machine
  "Phase state machine for the 潮目 (shionome) regime_observer cell.

  1:1 port of `cells/regime_observer/state_machine.py`. Derives the FACTUAL
  cross-asset regime (risk-on / risk-off / mixed / indeterminate) from net flow
  into risk vs safe buckets. DESCRIPTIVE, never advice (G2, トレードはしない).
  Self-contained, pure.

  Conventions (mimamori/methods/bond.cljc house style):
    - dataclass RegimeState → a plain map with kebab keyword keys
    - Python \":…\" string identities stay strings (regime labels, risk tags)
    - RegimePhase enum value identities (\"init\"/\"observed\") stay strings
    - transitions are pure fns; closed-vocab violations → ex-info"
  (:require [clojure.string :as str]))

;; ── RegimePhase (enum — Python value identities preserved) ────────

(def regime-phases
  "The closed RegimePhase vocabulary. Keyed by the idiomatic Clojure enum
  keyword; the value is the Python `RegimePhase.<X>.value` string identity."
  {:init     "init"
   :observed "observed"})

(def regime-phase-init     (:init regime-phases))      ;; "init"
(def regime-phase-observed (:observed regime-phases))  ;; "observed"

;; ── risk-tag closed vocab (Python ":…"-equivalent bare strings) ──

(def risk-tags
  "The closed bucket risk-tag vocabulary (cs.risk_tag value domain)."
  #{"risk" "safe" "neutral"})

;; ── RegimeState (dataclass → plain map, kebab keys, field defaults) ──

(def regime-state
  "RegimeState default value — the @dataclass field defaults as a plain map."
  {:phase            regime-phase-init   ;; RegimePhase.INIT.value
   :net              {}                  ;; bucket -> net flow
   :risk-tag         {}                  ;; bucket -> "risk"/"safe"/"neutral"
   :regime           ""
   :risk-net         0.0
   :safe-net         0.0
   :no-trade-notice  true})

(defn make-regime-state
  "Construct a RegimeState map from a partial cell-state map, filling the
  dataclass defaults (RegimeState(**state.get(\"cell_state\", {}))). Unknown
  keys → ex-info (closed RegimeState surface — RegimeState(**...) would
  TypeError on an unexpected kwarg)."
  [cs]
  (let [cs (or cs {})
        allowed (set (keys regime-state))
        extra (remove allowed (keys cs))]
    (when (seq extra)
      (throw (ex-info (str "unknown RegimeState field(s): " (vec extra))
                      {:shionome/closed-vocab true :extra (vec extra)})))
    (merge regime-state cs)))

;; ── helpers ──────────────────────────────────────────────────────

(defn- round4
  "round(x, 4) — banker's-free decimal round to 4 places, matching Python's
  round() closely enough for the regime figures (which are observation, not a
  signal). Returns a double."
  [x]
  (/ (Math/round (* (double x) 10000.0)) 10000.0))

(defn- sum-tagged
  "Σ v over (bucket→v) `net` where (risk-tag bucket) == `tag`."
  [net risk-tag tag]
  (reduce-kv (fn [acc b v]
               (if (= (get risk-tag b) tag)
                 (+ acc (double v))
                 acc))
             0.0
             net))

(defn classify-regime
  "The FACTUAL regime label from (risk-net, safe-net). Pure; the 1:1 branch
  ladder of the Python `if/elif`. Returns one of
  \"indeterminate\"/\"risk-on\"/\"risk-off\"/\"mixed\"."
  [risk-net safe-net]
  (cond
    (and (== risk-net 0.0) (== safe-net 0.0)) "indeterminate"
    (and (> risk-net 0) (<= safe-net 0))      "risk-on"
    (and (< risk-net 0) (>= safe-net 0))      "risk-off"
    :else                                      "mixed"))

;; ── transition ───────────────────────────────────────────────────

(defn transition-to-observed
  "Port of `transition_to_observed(state)`. Pure: takes the wrapper state map
  {:cell-state … :net … :risk-tag …} and returns {:cell-state <next>}.

  net/risk-tag from the top-level state override the cell-state's own, matching
  `cs.net = state.get(\"net\", cs.net)` (the Python override semantics)."
  [state]
  (let [state (or state {})
        cs    (make-regime-state (:cell-state state))
        net      (get state :net (:net cs))
        risk-tag (get state :risk-tag (:risk-tag cs))
        risk-net (sum-tagged net risk-tag "risk")
        safe-net (sum-tagged net risk-tag "safe")
        label    (classify-regime risk-net safe-net)
        cs (assoc cs
                  :net net
                  :risk-tag risk-tag
                  :risk-net (round4 risk-net)
                  :safe-net (round4 safe-net)
                  :regime label
                  :no-trade-notice true
                  :phase regime-phase-observed)]
    {:cell-state cs}))

(defn solve
  "R0 scaffold: .solve() raises until Council activation (ADR-2606072200 §Decision, G8)."
  [_input-state]
  (throw (ex-info "shionome R0 scaffold: activate regime_observer via Council ADR (post-2606072200 ratification)"
                  {:scaffold true})))
