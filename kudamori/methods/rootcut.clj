;; kudamori 管守 — mechanical root / obstruction cutting.
;;
;; Root intrusion through pipe joints is the single most common foul-sewer obstruction.
;; A steerable rotary cutter (the crawler's cutting head) shears the root mass out of the
;; bore. The starred safety discipline here mirrors jetting's G7 over-pressure gate:
;;
;;   * ★ G7 — no pipe over-TORQUE. The cutting torque the head must apply to clear a dense
;;     root mass rises with root density; if the required torque exceeds the pipe material's
;;     safe torque limit, cutting would crack/collapse the pipe. That is unrepresentable —
;;     assert-cut-torque! RAISES, it is NEVER clamped-and-proceeded (the actor's G7
;;     no-damage discipline; never damage the host the robot is sent to keep).
;;   * passes-needed — denser roots take more cutting passes (a single pass clears only a
;;     bounded fraction of the root mass).
;;
;; Pure Clojure, no deps → babashka-runnable AND kotoba-pywasm-portable. Pure compute; it
;; cuts no real pipe (G1) — it plans a cut from a survey reading.
;; Per ADR-2606142030 (kudamori R0). Clojure-first (the GAP-actor wave).
(ns kudamori.methods.rootcut)

;; ── pipe material safe cutting-torque limits (N·m; conservative R0 values) ───────
;; A weaker / smaller-walled material tolerates less reaction torque before the bore
;; cracks. Same material set jetting.clj rates for pressure (G7 sibling).
(def material-torque-limit-nm
  {:vcp          120.0    ; vitrified clay — brittle, modest limit
   :pvc           80.0    ; plastic — flexes, lowest limit
   :concrete     180.0
   :ductile-iron 400.0})  ; ductile metal — highest

(defn torque-limit-for
  "Safe cutting-torque limit (N·m) for a pipe material. Unknown material → RAISE (we do
   not guess a limit for a material we cannot place — G7 conservative discipline)."
  [material]
  (or (get material-torque-limit-nm material)
      (throw (ex-info "unknown pipe material — no torque limit" {:material material}))))

;; ── cut model ────────────────────────────────────────────────────────────────────
(def ^:const cut-fraction-per-pass 0.45)   ; fraction of remaining root mass a pass clears

(defn passes-needed
  "Cutting passes to reduce a root mass of `root-density` (0..1) below a negligible
   residual. Denser roots → more passes (each pass clears `cut-fraction-per-pass` of what
   remains). density 0 → 0 passes; the result rises monotonically with density."
  [root-density]
  (let [d (max 0.0 (min 1.0 (double root-density)))]
    (if (<= d 0.0)
      0
      ;; passes to drive d·(1−f)^n below 0.02 → n = ceil( ln(0.02/d) / ln(1−f) )
      (let [residual 0.02]
        (if (<= d residual)
          1
          (long (Math/ceil (/ (Math/log (/ residual d))
                              (Math/log (- 1.0 cut-fraction-per-pass))))))))))

(defn required-torque-nm
  "Estimated reaction torque (N·m) the cutting head must apply: denser root masses and
   larger bores both raise it. A small-diameter, weak pipe choked with dense roots is the
   over-torque case (G7). Linear in root density, scales with bore diameter."
  [root-density pipe-diameter-mm]
  (let [d (max 0.0 (min 1.0 (double root-density)))]
    ;; base coefficient × density × (bore / 100mm reference); a near-full bore of dense
    ;; roots in a 300mm main pushes well past a weak PVC pipe's limit, while a ductile-iron
    ;; main absorbs an ordinary cut.
    (* 150.0 d (/ (double pipe-diameter-mm) 100.0))))

(defn cut-torque-safe?
  "True iff the required cutting torque stays at/below the pipe material's safe limit."
  [root-density pipe-diameter-mm material]
  (<= (required-torque-nm root-density pipe-diameter-mm) (torque-limit-for material)))

(defn assert-cut-torque!
  "Return the required torque if safe; RAISE on over-torque (★ G7 — cutting torque above
   the pipe's safe limit would crack/collapse the pipe and is unrepresentable; never
   clamp-and-proceed)."
  [root-density pipe-diameter-mm material]
  (let [tau (required-torque-nm root-density pipe-diameter-mm)]
    (when-not (cut-torque-safe? root-density pipe-diameter-mm material)
      (throw (ex-info "cut over-torque: would damage pipe (G7)"
                      {:root-density root-density
                       :pipe-diameter-mm pipe-diameter-mm
                       :material material
                       :required-torque-nm tau
                       :torque-limit-nm (torque-limit-for material)})))
    tau))

;; ── one root-cut plan ─────────────────────────────────────────────────────────────
(defn plan-cut
  "Plan a mechanical root cut. `intrusion` = {:root-density 0..1 :pipe-diameter-mm
   :pipe-material}; `cutter` = {:id …} (the crawler's cutting head). Asserts the required
   torque is safe (G7, RAISES on over-torque — a small/weak pipe choked with dense roots),
   then returns the pass count + torque estimate.
   Returns {:cutter :material :root-density :pipe-diameter-mm :passes-needed
            :required-torque-nm :torque-limit-nm}."
  [intrusion cutter]
  (let [{:keys [root-density pipe-diameter-mm pipe-material]} intrusion
        tau (assert-cut-torque! root-density pipe-diameter-mm pipe-material)]
    {:cutter            (:id cutter)
     :material          pipe-material
     :root-density      (double root-density)
     :pipe-diameter-mm  pipe-diameter-mm
     :passes-needed     (passes-needed root-density)
     :required-torque-nm tau
     :torque-limit-nm   (torque-limit-for pipe-material)}))

;; ── demo ───────────────────────────────────────────────────────────────────────
(defn -main [& _args]
  (let [cutter {:id "cut-head-01"}
        plan   (plan-cut {:root-density 0.6 :pipe-diameter-mm 300 :pipe-material :ductile-iron} cutter)]
    (println "kudamori 管守 — root-cut plan (★ G7 no over-torque)")
    (println (format "  material %s  bore %dmm  root-density %.2f"
                     (name (:material plan)) (:pipe-diameter-mm plan) (:root-density plan)))
    (println (format "  passes-needed %d  required-torque %.1f N·m  (limit %.1f N·m)"
                     (:passes-needed plan) (:required-torque-nm plan) (:torque-limit-nm plan)))))
