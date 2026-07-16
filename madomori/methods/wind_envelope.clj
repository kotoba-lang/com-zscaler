;; madomori 窓守 — rope/BMU sway + wind safety envelope (★ G5 — the fall-equivalent gate).
;;
;; The whole point of madomori is to remove a human from the rope/cradle, so the
;; safety envelope is the heart of the actor:
;;
;;   * tether/pendulum sway model — a unit hanging on a rope of length L (the height
;;     paid out below the suspension point) swings in wind. We model the quasi-static
;;     deflection of a pendulum under a horizontal wind force: amplitude grows with
;;     wind² and with rope length, and shrinks with the suspended mass.
;;   * `work-permitted?` — a HARD wind work-stop threshold. Above it, operation must
;;     RAISE / refuse (this is the fall-equivalent gate). AND a descent may only be
;;     PLANNED with ≥2 independent fall-arrest anchors — a single-anchor plan RAISES.
;;
;; Pure Clojure, no deps → babashka-runnable AND kotoba-pywasm-portable.
;; Per ADR-2606142020 (madomori R0).
(ns madomori.methods.wind-envelope)

(def ^:const air-density-kgm3 1.225)   ; ρ at sea level
(def ^:const gravity-mps2 9.81)

;; ── wind work-stop threshold (★ G5) ──────────────────────────────────────────
(def ^:const default-wind-stop-mps
  "Hard wind work-stop threshold (m/s). At or above this, façade rope/cradle work
   must STOP — per ADR-2606142020 G5 this is the fall-equivalent gate and is NOT
   tunable up by a planner. (≈10 m/s ≈ Beaufort 5, the common industry stop.)"
  10.0)

;; ── pendulum sway amplitude ──────────────────────────────────────────────────
(defn wind-force-n
  "Horizontal aerodynamic drag (N) on the suspended unit at a wind speed.
   F = ½ ρ v² Cd A. Defaults: Cd 1.2 (bluff body), A 0.6 m² frontal area."
  ([wind-mps] (wind-force-n wind-mps {}))
  ([wind-mps {:keys [cd area-m2] :or {cd 1.2 area-m2 0.6}}]
   (when (neg? wind-mps) (throw (ex-info "wind speed must be non-negative" {:v wind-mps})))
   (* 0.5 air-density-kgm3 (* wind-mps wind-mps) cd area-m2)))

(defn sway-amplitude-m
  "Quasi-static horizontal sway (m) of a unit of `mass-kg` hanging on a rope whose
   paid-out length below the suspension point is `rope-len-m`, under `wind-mps`.
   Small-angle pendulum: x ≈ L · tan(θ), tan(θ) = F_wind / (m g). Grows with wind²
   and rope length; shrinks with mass. RAISES on non-positive mass or negative rope."
  ([rope-len-m mass-kg wind-mps] (sway-amplitude-m rope-len-m mass-kg wind-mps {}))
  ([rope-len-m mass-kg wind-mps opts]
   (when (neg? rope-len-m) (throw (ex-info "rope length must be non-negative" {:l rope-len-m})))
   (when (not (pos? mass-kg)) (throw (ex-info "mass must be positive" {:m mass-kg})))
   (let [f (wind-force-n wind-mps opts)
         tan-theta (/ f (* mass-kg gravity-mps2))]
     (* rope-len-m tan-theta))))

;; ── fall-arrest redundancy (★ G5) ────────────────────────────────────────────
(defn independent-anchors
  "Count of independent fall-arrest anchors in an `anchors` seq
   (each {:independent bool})."
  [anchors]
  (count (filter :independent anchors)))

(defn fall-arrest-redundant?
  "True iff there are ≥2 independent fall-arrest anchors (no single point of failure)."
  [anchors]
  (>= (independent-anchors anchors) 2))

;; ── the gate (★ G5 — RAISES) ─────────────────────────────────────────────────
(defn work-permitted?
  "★ G5 fall-equivalent gate. Returns true iff a descent is permitted; otherwise
   RAISES (it never returns false — a refusal must surface, not be papered over):
     * wind at/above the work-stop threshold → RAISE (wind work-stop);
     * fewer than 2 independent anchors → RAISE (no fall-arrest redundancy).
   `wind` = {:speed-mps :gust-mps :stop-threshold-mps}; gusts are checked too."
  [{:keys [speed-mps gust-mps stop-threshold-mps] :as wind} anchors]
  (let [thr (or stop-threshold-mps default-wind-stop-mps)
        peak (max (or speed-mps 0) (or gust-mps 0))]
    (when (>= peak thr)
      (throw (ex-info "WIND WORK-STOP — façade work refused above threshold (G5)"
                      {:peak-mps peak :threshold-mps thr :wind wind})))
    (when-not (fall-arrest-redundant? anchors)
      (throw (ex-info "FALL-ARREST REDUNDANCY — a descent needs ≥2 independent anchors (G5)"
                      {:independent (independent-anchors anchors)})))
    true))

(defn assess
  "Non-raising envelope summary for a report: sway at the worst case + permit status.
   `:permitted?` is the boolean result of `work-permitted?` caught — never throws."
  [face wind robot anchors]
  (let [rope-len (:height-m face 0)
        peak (max (:speed-mps wind 0) (:gust-mps wind 0))
        sway (sway-amplitude-m rope-len (:mass-kg robot 1.0) peak)
        permitted? (try (work-permitted? wind anchors) (catch Exception _ false))]
    {:rope-len-m rope-len
     :peak-wind-mps peak
     :stop-threshold-mps (or (:stop-threshold-mps wind) default-wind-stop-mps)
     :sway-amplitude-m sway
     :independent-anchors (independent-anchors anchors)
     :fall-arrest-redundant? (fall-arrest-redundant? anchors)
     :permitted? permitted?}))
