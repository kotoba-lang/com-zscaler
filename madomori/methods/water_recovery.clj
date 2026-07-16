;; madomori 窓守 — runoff / detergent capture water balance (★ G2 — eco minimization).
;;
;; A façade-cleaning pass applies deionized-water + surfactant to a pane and must
;; CAPTURE the runoff rather than let detergent-laden water fall down the building.
;; `water-balance` is the mass balance of a cleaning pass: how much of the applied
;; water was recovered. `assert-compliant!` RAISES when the recovered fraction is
;; below the eco floor — a façade job that can't capture its own runoff is not
;; permitted (mirrors adhesion/adhesion-safe! and wind/work-permitted? raising-gate
;; discipline: an out-of-bounds plan must surface, never be silently forced).
;;
;; Purely fluid quantities — no imagery / person / interior data is representable
;; here (G3 stays trivially satisfied; the balance map carries only litres/ml).
;;
;; Pure Clojure, no deps → babashka-runnable AND kotoba-pywasm-portable.
;; Per ADR-2606142020 (madomori R0).
(ns madomori.methods.water-recovery)

;; ── eco recovery floor (G2) ───────────────────────────────────────────────────
;; Minimum fraction of applied water that must be captured back. Detergent-laden
;; runoff escaping the capture skirt is an environmental release, so the floor is
;; high (0.7 = 70% recovered). Like the wind work-stop, it is a hard eco floor.
(def ^:const default-min-recovery 0.7)

;; ── water mass balance (non-raising) ──────────────────────────────────────────
(defn water-balance
  "Mass balance of a cleaning pass's water (G2 eco). Given applied / captured
   litres (and the detergent dose for the audit trail), compute:
     :recovery-fraction = captured / applied   (guards applied > 0)
     :lost-l            = applied - captured   (the un-recovered runoff)
     :compliant?        = recovery-fraction ≥ threshold (default 0.7 eco floor)
   Non-raising: returns the balance map with `:compliant? false` when below the
   floor (the raising variant is `assert-compliant!`)."
  [{:keys [applied-l captured-l detergent-ml min-recovery]
    :or {min-recovery default-min-recovery}}]
  (when-not (and (number? applied-l) (pos? applied-l))
    (throw (ex-info "applied-l must be positive (cannot balance a dry pass)"
                    {:applied-l applied-l})))
  (let [recovery-fraction (/ (double captured-l) (double applied-l))
        lost-l (- (double applied-l) (double captured-l))]
    {:applied-l (double applied-l)
     :captured-l (double captured-l)
     :detergent-ml (double (or detergent-ml 0.0))
     :recovery-fraction recovery-fraction
     :lost-l lost-l
     :min-recovery min-recovery
     :compliant? (>= recovery-fraction min-recovery)}))

;; ── the eco gate (★ G2 — RAISES) ──────────────────────────────────────────────
(defn assert-compliant!
  "★ G2 water-recovery gate. Returns the balance map iff the recovered fraction
   meets or exceeds the eco floor; otherwise RAISES (it never returns a soft
   non-compliant map — a façade job that can't capture its own detergent runoff
   must surface, not be silently accepted). Raising variant of `water-balance`."
  [args]
  (let [{:keys [recovery-fraction min-recovery] :as bal} (water-balance args)]
    (when (< recovery-fraction min-recovery)
      (throw (ex-info "WATER RECOVERY below eco floor — detergent runoff not captured (G2)"
                      {:recovery-fraction recovery-fraction
                       :min-recovery min-recovery
                       :lost-l (:lost-l bal)})))
    bal))

(defn -main [& _]
  (let [ok (water-balance {:applied-l 8.0 :captured-l 6.4 :detergent-ml 64.0})
        bad (water-balance {:applied-l 8.0 :captured-l 4.0 :detergent-ml 64.0})]
    (println "madomori 窓守 — runoff/detergent water balance (★ G2 eco)")
    (println (format "  compliant pass : applied %.1fL captured %.1fL → recovery %.1f%% lost %.2fL → compliant? %s"
                     (:applied-l ok) (:captured-l ok)
                     (* 100.0 (:recovery-fraction ok)) (:lost-l ok) (:compliant? ok)))
    (println (format "  leaky pass     : applied %.1fL captured %.1fL → recovery %.1f%% lost %.2fL → compliant? %s"
                     (:applied-l bad) (:captured-l bad)
                     (* 100.0 (:recovery-fraction bad)) (:lost-l bad) (:compliant? bad)))
    (println (format "  eco floor      : %.0f%% recovery (assert-compliant! RAISES below this)"
                     (* 100.0 default-min-recovery)))
    (flush)))
