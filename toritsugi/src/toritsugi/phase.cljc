(ns toritsugi.phase
  "toritsugi lifecycle + rollout phase gating.

  Two distinct notions live here:

  1. LIFECYCLE — the concierge session's phase as a member is walked through one
     procedure (one cell at a time, one run = one operation):

       init → matched → intaked → guided → drafted → submitted → tracked
                                                          └→ refused / hold

     Each cell node advances the lifecycle by exactly one step (or refuses /
     holds). Pure data; the store session record carries the current phase.

  2. ROLLOUT — the staged R0→R3 rollout gates only the ASSESS ops (guide / draft /
     submit). Resolving a coded procedure (procedure_registry) and surfacing
     eligibility info are always on (that is the observe / wayfinding function).
     The rollout only decides how much autonomy the *guide/draft/submit* have, and
     can only add caution:

       R0 scaffold     — resolve + eligibility info only; NO guide dispatch, NO
                         submission (scaffold phase, ADR-2605312030 R0).
       R1 guide        — guide/build live (案内 + 必要書類 checklist); no draft, no
                         submit. 案内 only.
       R2 self-submit  — guide + draft + status_track live; member SELF-SUBMITS
                         (toritsugi assembles + hands back). 代行 off.
       R3 agent        — 代行 (agent-on-behalf submit) live under G14+G15+Council
                         Lv7+; STILL high-stakes → ALWAYS human sign-off (never auto).")

;; ───────────────────────── lifecycle ─────────────────────────

(def lifecycle-order
  "The success path of one concierge run (one step per cell node)."
  [:init :matched :intaked :guided :drafted :submitted :tracked])

(def terminal-phases #{:refused :hold})

(defn lifecycle-phase? [x] (or (some #{x} lifecycle-order) (terminal-phases x)))

(defn- index-of [coll x]
  (loop [i 0, s (seq coll)]
    (cond (nil? s) -1 (= (first s) x) i :else (recur (inc i) (next s)))))

(defn advance
  "Next lifecycle phase after a successful step from `phase`, or `phase` if it is
  terminal / already :tracked."
  [phase]
  (cond
    (terminal-phases phase) phase
    (= phase :tracked)      phase
    :else                   (let [i (index-of lifecycle-order phase)]
                              (if (neg? i) phase
                                  (nth lifecycle-order (min (inc i) (dec (count lifecycle-order))))))))

;; ───────────────────────── rollout ─────────────────────────

(def record-ops
  "observe / wayfinding ops — always on regardless of rollout phase."
  #{:procedure/resolve :eligibility/match})

(def assess-ops #{:guide/build :draft/assist :submit/transmit})

(def phases
  {0 {:label "R0-scaffold"   :assess #{}                                        :auto #{}}
   1 {:label "R1-guide"      :assess #{:guide/build}                            :auto #{:guide/build}}
   2 {:label "R2-self-submit" :assess #{:guide/build :draft/assist :submit/transmit}
      :auto #{:guide/build :draft/assist :submit/transmit}}
   3 {:label "R3-agent"      :assess #{:guide/build :draft/assist :submit/transmit}
      :auto #{:guide/build :draft/assist :submit/transmit}}})

(def default-phase
  "The phase used when `context` carries no :phase at all
  (toritsugi.flow: (or (get-in context [:phase]) phase/default-phase)),
  AND the fallback `gate` itself uses for an unrecognized phase NUMBER
  (`(get phases phase (get phases default-phase))`). This is directly
  reachable by any ordinary caller that simply omits :phase -- not just
  malformed/malicious input -- so it must be the MOST CONSERVATIVE
  phase that still functions, never one that silently unlocks
  government-procedure submission. This was 2 (R2-self-submit) until a
  live check found phase 2 and phase 3 share an IDENTICAL :assess/:auto
  set -- both auto-commit :submit/transmit for :member-self-submit mode
  (the governor's :high-stakes? only fires for :agent-on-behalf, so a
  clean self-submit auto-commits at either tier) -- so a caller who
  forgot :phase got a REAL government-procedure filing auto-committed
  with no toritsugi-side human checkpoint, contradicting G8 ('the
  member always confirms before any submission'). This is the same
  accidental-fail-open shape already found and fixed this session in
  the shared talent.phase template (gftd-talent-actor) and its many
  siblings across kotoba-lang. Unlike that family, toritsugi's own R1
  tier already auto-commits :guide/build by design (pure 案内/wayfinding,
  non-actuating -- not a bug, see phases above), so 1 (R1-guide) is the
  right floor here: it keeps non-actuating guidance live while making
  :draft/assist and :submit/transmit entirely unreachable
  (:phase-disabled hold) until a caller explicitly opts into R2+."
  1)

(defn record-op? [op] (contains? record-ops op))

(defn gate
  "Adjust an assess op's governor disposition for the rollout phase.
  Returns {:disposition kw :reason kw|nil}. A 代行 submit is never :auto — it
  always escalates (agent-on-behalf is a human/Council sign-off even at R3)."
  [phase {:keys [op mode]} disposition]
  (let [{:keys [assess auto]} (get phases phase (get phases default-phase))]
    (cond
      (= :hold disposition)        {:disposition :hold :reason nil}
      (not (contains? assess op))  {:disposition :hold :reason :phase-disabled}
      (and (= :commit disposition)
           (not (contains? auto op))) {:disposition :escalate :reason :phase-approval}
      (and (= :submit/transmit op) (= :agent-on-behalf mode))
      {:disposition :escalate :reason :agent-on-behalf-signoff}
      :else                        {:disposition disposition :reason nil})))

(defn verdict->disposition [v]
  (cond (:hard? v) :hold (:escalate? v) :escalate :else :commit))
