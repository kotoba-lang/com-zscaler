;; madomori 窓守 — façade-climbing (suction) adhesion safety (★ G7 — factor-of-safety).
;;
;; A suction/adhesion climbing robot holds itself (and its payload) against gravity
;; on a vertical façade. The available adhesion force must exceed the borne load by
;; a required factor-of-safety, which depends on the surface type (glass seals best,
;; porous stone worst). `adhesion-safe?` RAISES when the margin is below the required
;; factor — adhesion failure on a façade is a fall, so an unsafe plan must surface,
;; never be silently forced (mirrors slotting/assign-slot! G7 discipline).
;;
;; Pure Clojure, no deps → babashka-runnable AND kotoba-pywasm-portable.
;; Per ADR-2606142020 (madomori R0).
(ns madomori.methods.adhesion)

(def ^:const gravity-mps2 9.81)

;; ── surface adhesion efficiency (G7) ─────────────────────────────────────────
;; Fraction of nominal suction force a surface actually delivers (sealing quality).
;; Glass: near-perfect seal; metal: good; stone: porous → much weaker.
(def surface-efficiency
  {:glass 0.95
   :metal 0.80
   :stone 0.45})

;; ── borne load ───────────────────────────────────────────────────────────────
(defn load-n
  "Gravitational load (N) the adhesion system must hold for `mass-kg`."
  [mass-kg]
  (* mass-kg gravity-mps2))

;; ── effective adhesion ───────────────────────────────────────────────────────
(defn effective-adhesion-n
  "Effective adhesion force (N) on a surface = nominal suction × surface efficiency.
   RAISES on an unknown surface type — an unmodeled surface must surface (G7)."
  [suction-force-n surface]
  (let [eff (surface-efficiency surface)]
    (when (nil? eff)
      (throw (ex-info "unknown surface type — adhesion unmodeled (G7)" {:surface surface})))
    (* suction-force-n eff)))

;; ── factor of safety ─────────────────────────────────────────────────────────
(defn factor-of-safety
  "Adhesion factor-of-safety = effective adhesion / borne load. >1 means held."
  [suction-force-n surface mass-kg]
  (let [load (load-n mass-kg)]
    (when-not (pos? load) (throw (ex-info "mass must be positive" {:m mass-kg})))
    (/ (effective-adhesion-n suction-force-n surface) load)))

;; ── the gate (★ G7 — RAISES) ─────────────────────────────────────────────────
(defn adhesion-safe?
  "★ G7 adhesion gate. Returns true iff the achieved factor-of-safety meets or
   exceeds `required-fos`; otherwise RAISES (it never returns false — an unsafe
   adhesion margin must surface, not be silently accepted). A fall is the failure
   mode, so this is a hard refusal like the wind gate."
  [{:keys [suction-force-n surface mass-kg required-fos]
    :or {required-fos 2.5}}]
  (let [fos (factor-of-safety suction-force-n surface mass-kg)]
    (when (< fos required-fos)
      (throw (ex-info "ADHESION FACTOR-OF-SAFETY below required margin (G7)"
                      {:fos fos :required-fos required-fos :surface surface})))
    true))

(defn assess
  "Non-raising adhesion summary for a report. `:safe?` is the caught boolean of
   `adhesion-safe?` — never throws."
  [robot surface]
  (let [args {:suction-force-n (:suction-force-n robot)
              :surface surface
              :mass-kg (:mass-kg robot)
              :required-fos (:required-fos robot 2.5)}
        fos (factor-of-safety (:suction-force-n robot) surface (:mass-kg robot))]
    {:surface surface
     :surface-efficiency (surface-efficiency surface)
     :load-n (load-n (:mass-kg robot))
     :effective-adhesion-n (effective-adhesion-n (:suction-force-n robot) surface)
     :factor-of-safety fos
     :required-fos (:required-fos robot 2.5)
     :safe? (try (adhesion-safe? args) (catch Exception _ false))}))
