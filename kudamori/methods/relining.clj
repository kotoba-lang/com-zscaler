;; kudamori 管守 — trenchless relining (CIPP) / spot repair.
;;
;; Once a pipe is cleaned (jetting) and cleared (rootcut), a structurally defective reach is
;; REHABILITATED from the inside — no excavation. A resin-impregnated liner (cured-in-place
;; pipe, CIPP) is inverted/pulled into the host pipe and cured, forming a new structural
;; pipe-within-a-pipe. The starred discipline here is an HONEST-REFUSAL gate (sibling of
;; jetting G7 / rootcut G7):
;;
;;   * ★ a liner only works inside a host that can still carry it. If the host condition is
;;     too degraded (grade 5 = collapse-imminent / loss of line-and-level), there is no
;;     sound host to line — it needs full open-cut REPLACEMENT, not a liner that will not
;;     hold. assert-relinable! RAISES rather than planning a liner that cannot work — an
;;     honest refusal, never a false promise (the actor's no-false-safety discipline).
;;   * liner thickness scales with diameter (a larger bore needs a thicker wall to carry the
;;     same external load) and with defect severity; cure time follows from thickness.
;;
;; Pure Clojure, no deps → babashka-runnable AND kotoba-pywasm-portable. Pure compute; it
;; relines no real pipe (G1) — it plans a rehabilitation from a survey reading.
;; Per ADR-2606142030 (kudamori R0). Clojure-first (the GAP-actor wave).
(ns kudamori.methods.relining)

(def ^:const collapse-grade 5)   ; host-condition grade 5 = collapse-imminent: not relinable

(defn relinable?
  "True iff the host pipe is sound enough to line. `host-condition` is a PACP-like 1..5
   grade (1 = sound, 5 = collapse-imminent). A grade-5 host has no sound wall to carry the
   liner → NOT relinable (needs replacement)."
  [host-condition]
  (< (long host-condition) collapse-grade))

(defn assert-relinable!
  "Return `host-condition` if the host can be lined; RAISE when it is too degraded (★ grade
   5 = collapse-imminent — there is no sound host to line, it needs full replacement; an
   honest refusal, never a liner that won't hold)."
  [host-condition]
  (when-not (relinable? host-condition)
    (throw (ex-info "host too degraded to reline: needs full replacement, not a liner"
                    {:host-condition host-condition
                     :collapse-grade collapse-grade})))
  host-condition)

;; ── liner design ─────────────────────────────────────────────────────────────────
(def ^:const thickness-per-100mm 1.5)   ; base liner wall (mm) per 100mm of host bore

(defn liner-thickness-mm
  "Design liner wall thickness (mm). Scales with host diameter (a larger bore carries more
   load → thicker wall) and steps up with defect severity (1..5). Monotonic in both."
  [pipe-diameter-mm defect-severity]
  (let [base     (* thickness-per-100mm (/ (double pipe-diameter-mm) 100.0))
        sev      (max 1 (min 5 (long defect-severity)))
        sev-mult (+ 1.0 (* 0.15 (dec sev)))]   ; +15% per severity step above 1
    (* base sev-mult)))

(defn cure-time-min
  "Ambient/steam cure time (minutes) for a CIPP liner. Thicker walls cure slower; a fixed
   set-up plus a per-mm soak. Always positive for any positive thickness."
  [liner-thickness-mm]
  (+ 20.0 (* 8.0 (double liner-thickness-mm))))   ; 20 min setup + 8 min/mm

;; ── one reline plan ─────────────────────────────────────────────────────────────
(defn plan-reline
  "Plan a trenchless CIPP reline. `defect` = {:pipe-diameter-mm :defect-severity 1..5
   :host-condition 1..5}. Asserts the host is sound enough to line (★ RAISES on a
   collapse-grade host — honest refusal, replacement not a liner), then designs the liner
   thickness (scales with diameter) + cure time.
   Returns {:pipe-diameter-mm :defect-severity :host-condition :liner-thickness-mm
            :cure-time-min :method}."
  [defect]
  (let [{:keys [pipe-diameter-mm defect-severity host-condition]} defect
        _ (assert-relinable! host-condition)
        t (liner-thickness-mm pipe-diameter-mm defect-severity)]
    {:pipe-diameter-mm   pipe-diameter-mm
     :defect-severity    (long defect-severity)
     :host-condition     (long host-condition)
     :liner-thickness-mm t
     :cure-time-min      (cure-time-min t)
     :method             :cipp}))

;; ── demo ───────────────────────────────────────────────────────────────────────
(defn -main [& _args]
  (let [plan (plan-reline {:pipe-diameter-mm 300 :defect-severity 4 :host-condition 3})]
    (println "kudamori 管守 — trenchless reline plan (CIPP; ★ honest-refusal on collapse-grade host)")
    (println (format "  bore %dmm  defect-severity %d  host-condition %d"
                     (:pipe-diameter-mm plan) (:defect-severity plan) (:host-condition plan)))
    (println (format "  liner-thickness %.2f mm  cure-time %.1f min  method %s"
                     (:liner-thickness-mm plan) (:cure-time-min plan) (name (:method plan))))))
