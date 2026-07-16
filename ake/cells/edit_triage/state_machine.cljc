(ns ake.cells.edit-triage.state-machine
  "Phase state machine for the 朱 (ake) triage cell — the G2/G6 advisory membrane.
  Clojure 1:1 port of cells/edit_triage/state_machine.py (ADR-2606052100).

  Scores a screened proposal (risk + quality, the Wikipedia ORES analogue) and assigns a route,
  reusing the single source of truth in methods/triage.cljc. G2 INVARIANT: the model SCORES and the
  PURE FUNCTION `route-for` routes — neither ever emits an accept/reject decision (非裁定). G6:
  any LLM refinement of the scores is Murakumo-only. REFUSED if the proposal fails a hard gate."
  (:require [clojure.string :as str]
            [ake.methods.triage :as triage]))

(def phase-init "init")
(def phase-triaged "triaged")
(def phase-refused "refused")

(defn default-state []
  {"phase" phase-init
   "edit" {}
   "risk" ""
   "quality" 0.0
   "route" ""
   "by" "murakumo:gemma3:4b"
   "refusal" ""
   "payload" {}})

(defn- state-of [d]
  (merge (default-state) (get d "cell_state" {})))

(defn- strip-colon [v]
  (str/replace (str v) #"^:+" ""))

(defn triage [state]
  (let [s0 (state-of state)
        edit (into {} (get state "edit" (get s0 "edit")))
        by (get state "by" (get s0 "by"))
        cs (assoc s0 "edit" edit "by" by)
        t (try
            (triage/score-edit edit by)
            (catch #?(:clj clojure.lang.ExceptionInfo :cljs ExceptionInfo) e
              {:refusal #?(:clj (.getMessage e) :cljs (ex-message e))}))]
    (if (:refusal t)
      {"cell_state" (assoc cs "refusal" (:refusal t) "phase" phase-refused)}
      (let [risk (get t ":triage/risk")
            quality (get t ":triage/quality")
            route (get t ":triage/route")]
        {"cell_state" (assoc cs
                             "risk" risk
                             "quality" quality
                             "route" route
                             "payload" {"editId" (get t ":triage/edit")
                                        "risk" (strip-colon risk)
                                        "quality" quality
                                        "route" (strip-colon route)
                                        "by" by}
                             "refusal" ""
                             "phase" phase-triaged)}))))

(defn solve
  "R0 scaffold: .solve() raises until Council activation (G8)."
  [_input-state]
  (throw (ex-info "ake R0 scaffold: edit_triage scores + routes offline; any live LLM refinement is Murakumo-only (G6) and Council-gated (G8)."
                  {:scaffold true})))
