(ns tasuke.cells.evidence-preservation.state-machine
  "Phase state machine for the 助 (tasuke) evidence_preservation cell — the G6 membrane.
  1:1 port of cells/evidence_preservation/state_machine.py (ADR-2606060900).

  Each evidence item is PRESERVED (refused if it carries plaintext PII, or lacks an encrypted
  envelope-ref) then INDEXED with a chain-of-custody sha256 so the victim can later prove it is
  unchanged. REFUSAL gate, not a clamp.

  Conventions: dataclass EvidenceState → a plain map with the SAME string field keys; `evidence/index`
  raises ex-info (Python ValueError) → caught → REFUSED phase."
  (:require [tasuke.methods.evidence :as evidence]))

;; ── EvidencePhase (enum — Python value identities preserved) ──
(def phase-init      "init")
(def phase-preserved "preserved")
(def phase-refused   "refused")

;; ── EvidenceState (dataclass → plain map, string keys + field defaults) ──
(def state-defaults
  {"phase"   phase-init
   "case_id" ""
   "count"   0
   "refusal" ""
   "rows"    []})

(defn- cell-state [state]
  (merge state-defaults (get state "cell_state" {})))

(defn preserve [state]
  (let [cs (cell-state state)
        cs (assoc cs "case_id" (get state "case_id" (get cs "case_id")))
        items (get state "items" [])]
    (try
      (let [rows (evidence/index items)
            cs (assoc cs
                      "rows" rows
                      "count" (count rows)
                      "refusal" ""
                      "phase" phase-preserved)]
        {"cell_state" cs})
      (catch clojure.lang.ExceptionInfo exc
        {"cell_state" (assoc cs
                             "refusal" (.getMessage exc)
                             "phase" phase-refused)}))))
