(ns ake.cells.revision-log.state-machine
  "Phase state machine for the 朱 (ake) revision_log cell — the append-only history projection.
  Clojure 1:1 port of cells/revision_log/state_machine.py (ADR-2606052100).

  The 'view history' tab, as a cell. A cleared promotion APPENDS one revision; the cell guarantees
  the history only ever GROWS (G5 — never overwrite, never delete; 非終末論). Reuses
  methods/revision.cljc so the append/read semantics are a single source of truth."
  (:require [clojure.string :as str]
            [ake.methods.revision :as revision]))

(def phase-init "init")
(def phase-appended "appended")
(def phase-refused "refused")

(defn default-state []
  {"phase" phase-init
   "history" []
   "edit" {}
   "as_of" 0
   "refusal" ""
   "payload" {}})

(defn- state-of [d]
  (merge (default-state) (get d "cell_state" {})))

(defn append [state]
  (let [s0 (state-of state)
        history (vec (get state "history" (get s0 "history")))
        edit (into {} (get state "edit" (get s0 "edit")))
        as-of (long (get state "as_of" (get s0 "as_of")))
        before (count history)
        new-history (revision/append-revision history edit as-of)
        cs (assoc s0 "history" new-history "edit" edit "as_of" as-of)]
    (if (not= (count new-history) (inc before))   ;; defensive: append must only ever grow by one
      {"cell_state" (assoc cs
                           "refusal" "G5: revision log must grow by exactly one (append-only)"
                           "phase" phase-refused)}
      (let [entity (get edit ":edit/target-entity" "?")
            attr (-> (str (get edit ":edit/target-attr" "")) (str/replace #"^:+" "") (str/split #"/") last)
            cur (revision/current new-history entity attr)]
        {"cell_state" (assoc cs
                             "payload" {"entity" entity
                                        "attr" attr
                                        "current" (get (or cur {}) ":revision/value" "")
                                        "sourcing" (get (or cur {}) ":revision/sourcing" ":representative")
                                        "revisions" (count (revision/history-of new-history entity attr))
                                        "asOf" as-of}
                             "refusal" ""
                             "phase" phase-appended)}))))

(defn solve
  "R0 scaffold: .solve() raises until Council activation (G8)."
  [_input-state]
  (throw (ex-info "ake R0 scaffold: revision_log appends offline; live projection into the served registry is Council Lv6+ + operator gated (G8)."
                  {:scaffold true})))
