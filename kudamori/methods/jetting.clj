;; kudamori 管守 — hydro-jetting / in-pipe cleaning.
;;
;;   * pressure safety (★ G7) — the nozzle pressure must stay within the pipe
;;     material's working-pressure rating; over-pressure that would damage the pipe
;;     RAISES, it is never silently clamped-and-proceeded;
;;   * debris-removal estimate — volume of debris cleared per the pipe geometry +
;;     debris fraction + jetting effectiveness;
;;   * water-reuse fraction (G2 eco) — recovered jetting water reused, and the
;;     residual effluent is NEVER discharged untreated (handed off to mizuho 水穂).
;;
;; Pure Clojure, no deps → babashka-runnable AND kotoba-pywasm-portable.
;; Per ADR-2606142030 (kudamori R0).
(ns kudamori.methods.jetting)

;; ── pipe material working-pressure ratings (bar; conservative R0 values) ──────
(def material-rating-bar
  {:vcp          150.0    ; vitrified clay
   :pvc          100.0
   :concrete     130.0
   :ductile-iron 250.0})

(defn rating-for
  "Working-pressure rating (bar) for a pipe material. Unknown material → RAISE (we do
   not guess a rating for a material we cannot place — G7 conservative discipline)."
  [material]
  (or (get material-rating-bar material)
      (throw (ex-info "unknown pipe material — no pressure rating" {:material material}))))

(defn jet-pressure-safe?
  "True iff `nozzle-pressure-bar` is at or below the material's rating. Pure check."
  [nozzle-pressure-bar material]
  (<= nozzle-pressure-bar (rating-for material)))

(defn assert-jet-pressure!
  "Return the pressure if safe; RAISE on over-pressure (★ G7 — jetting above the pipe
   rating would damage the pipe and is unrepresentable; never clamp-and-proceed)."
  [nozzle-pressure-bar material]
  (when-not (jet-pressure-safe? nozzle-pressure-bar material)
    (throw (ex-info "jet over-pressure: would damage pipe (G7)"
                    {:nozzle-pressure-bar nozzle-pressure-bar
                     :material material :rating (rating-for material)})))
  nozzle-pressure-bar)

;; ── debris-removal estimate ──────────────────────────────────────────────────
(defn pipe-volume-m3
  "Internal volume (m³) of a circular pipe of inner diameter `id-mm` and `length-m`."
  [id-mm length-m]
  (let [r (/ (/ id-mm 1000.0) 2.0)]
    (* Math/PI r r length-m)))

(def ^:const jet-effectiveness 0.95)   ; fraction of present debris a pass clears

(defn debris-removed-m3
  "Estimated debris volume (m³) removed cleaning a segment: pipe volume × the debris
   fraction occupying it × jetting effectiveness."
  [segment debris-frac]
  (* (pipe-volume-m3 (:id-mm segment) (:length-m segment))
     (max 0.0 (min 1.0 (double debris-frac)))
     jet-effectiveness))

;; ── water balance (G2 eco) ───────────────────────────────────────────────────
(defn water-used-l
  "Jetting water consumed (litres) = flow (L/min) × jetting minutes."
  [flow-lpm minutes]
  (* flow-lpm (max 0.0 (double minutes))))

(defn water-reused-l [used-l reuse-frac]
  (* used-l (max 0.0 (min 1.0 (double reuse-frac)))))

(defn water-balance
  "Full water balance for a jetting run. The residual (used − reused) is effluent that
   MUST be handed to mizuho 水穂 for treatment — never discharged untreated (G2).
   Returns {:used-l :reused-l :effluent-l :reuse-frac :handoff :mizuho}."
  [flow-lpm minutes reuse-frac]
  (let [used (water-used-l flow-lpm minutes)
        reused (water-reused-l used reuse-frac)]
    {:used-l used
     :reused-l reused
     :effluent-l (- used reused)
     :reuse-frac (double reuse-frac)
     :handoff :mizuho}))            ; untreated effluent → mizuho, never discharged

;; ── one cleaning run over a segment ──────────────────────────────────────────
(defn clean-segment
  "Plan a jetting run over `segment`: assert pressure safe (G7, raises on over-pressure),
   estimate debris removed, and compute the water balance (G2). `jet` is the robot's
   nozzle config {:nozzle-pressure-bar :flow-lpm :water-reuse-frac}."
  [segment jet debris-frac minutes]
  (assert-jet-pressure! (:nozzle-pressure-bar jet) (:material segment))
  {:segment (:id segment)
   :material (:material segment)
   :pressure-bar (:nozzle-pressure-bar jet)
   :rating-bar (rating-for (:material segment))
   :debris-removed-m3 (debris-removed-m3 segment debris-frac)
   :water (water-balance (:flow-lpm jet) minutes (:water-reuse-frac jet))})
