(ns noroshi.cells.reliability-qual.state-machine
  "Phase state machine for the noroshi (烽) reliability_qual cell — the
  packaging face's Telcordia GR-468-SHAPE reliability-qualification job
  (ADR-2606051600; matures this cell from a pure `.edn` scaffold to real,
  tested logic, alongside `device_design`).

  A packaged photonic module is qualified against a SUBSET of the four
  GR-468-SHAPE test types (thermal cycling / damp heat / mechanical shock /
  fibre pull) — select which apply, record the (representative, G10) stress-
  plan criteria used, judge caller-SUPPLIED test results via
  `noroshi.methods.reliability-qual/judge-suite` (never live chamber I/O —
  G8, no chamber exists at R0), and emit a dry-run qual-plan record.

  Unlike `active_alignment`'s/`fibre_loop`'s state machines (which take a
  pre-computed numeric result as a state-dict input field and never call into
  their `methods/` sibling), this state machine DELIBERATELY calls
  `noroshi.methods.reliability-qual/judge-suite` directly — a real PASS/FAIL
  compliance ENGINE, not just a job-lifecycle gate, is the point of this
  maturity pass. See this actor's own ADR follow-up for the reasoning.

  Phase order: init -> suite_selected -> stress_planned -> acceptance_judged
  -> qual_committed. `next_node` values match this cell's own
  `cells/reliability_qual.edn` `:cell/state-graph` node ids.

  Wire-format note: `device_id` and `suite` (a vector of GR-468-SHAPE test-type
  NAME STRINGS, e.g. \"thermal-cycling\") follow the snake_case/string
  convention every sibling state machine uses. `results` is the one exception:
  its OUTER keys are test-type name strings (same convention), but each INNER
  result map is passed through as the native keyword-keyed shape
  `methods/reliability-qual`'s judge-* functions expect (`:cycles-completed`
  etc.) — test results are supplied by an operator/tooling integration, not
  derived from a JSON wire payload, so passing them through unconverted avoids
  a brittle bidirectional case-conversion layer for a G8-gated, no-live-
  chamber R0 scaffold.

  No hazardous-class laser vocabulary applies here (reliability_qual's own
  gates are G1/G6/G8/G9/G10 per its `.edn` — no G3/G5/G7); the device's own
  civilian-comms force-class was already gated upstream at `device_design`
  time, so this cell only re-checks referential integrity (a non-empty
  device_id), not civilian-ness again."
  (:require [noroshi.methods.reliability-qual :as rq]))

(def ^:private defaults
  {"phase" "init" "device_id" "" "suite" [] "results" {} "acceptance" nil "payload" {}})

(defn- state* [state] (merge defaults (get state "cell_state" {})))

(defn transition-select-suite
  "Validate device_id + suite (defaulting to the full 4-test suite when
  omitted); refuse an unknown GR-468-SHAPE test-type name."
  [state]
  (let [cs0 (state* state)
        device-id (get state "device_id" (get cs0 "device_id"))
        raw-suite (get state "suite" (get cs0 "suite"))
        suite (vec (if (empty? raw-suite) (sort (map name rq/test-types)) raw-suite))
        known (set (map name rq/test-types))
        unknown (remove known suite)]
    (when (empty? device-id)
      (throw (ex-info "reliability_qual: device_id is required"
                      {:noroshi/violation :missing-device-id})))
    (when (seq unknown)
      (throw (ex-info (str "reliability_qual: unknown GR-468-SHAPE test type(s) " (vec unknown)
                           " — not in " known)
                      {:noroshi/violation :unknown-test-type :unknown (vec unknown)})))
    {"cell_state" (assoc cs0 "device_id" device-id "suite" suite "phase" "suite_selected")
     "next_node" "stress-plan"}))

(defn transition-stress-plan
  "Record the (representative, G10) acceptance criteria used for the selected
  suite — no live chamber scheduling happens here or anywhere at R0 (G8)."
  [state]
  (let [cs (state* state)
        criteria (into {} (map (fn [tt] [tt (get rq/default-suite (keyword tt))]) (get cs "suite")))]
    {"cell_state" (assoc cs "phase" "stress_planned"
                         "payload" (assoc (get cs "payload") "criteria" criteria))
     "next_node" "acceptance"}))

(defn transition-acceptance
  "Judge the selected suite against submitted results via
  `noroshi.methods.reliability-qual/judge-suite` — a real PASS/FAIL engine,
  not a self-report. A selected test with no submitted result fails (G10: no
  fabricated coverage)."
  [state]
  (let [cs (state* state)
        results (get state "results" (get cs "results"))
        selected (set (map keyword (get cs "suite")))
        results-kw (into {} (map (fn [[k v]] [(keyword k) v])) results)
        judgment (rq/judge-suite selected results-kw)]
    {"cell_state" (assoc cs "results" results
                         "acceptance" (if (:overall-pass? judgment) "pass" "fail")
                         "phase" "acceptance_judged"
                         "payload" (assoc (get cs "payload") "judgment" judgment))
     "next_node" "emit"}))

(defn transition-emit
  "Emit the dry-run qual-plan record (the kotoba :qual/* datom shape;
  :qual/dry-run true — G8, no chamber exists at R0; :qual/representative
  true — G10)."
  [state]
  (let [cs (state* state)]
    {"cell_state" (assoc cs "phase" "qual_committed"
                         "payload" (assoc (get cs "payload") "qual"
                                          {"deviceId" (get cs "device_id")
                                           "suite" (get cs "suite")
                                           "acceptance" (get cs "acceptance")
                                           "dryRun" true
                                           "representative" true}))
     "next_node" "end"}))
