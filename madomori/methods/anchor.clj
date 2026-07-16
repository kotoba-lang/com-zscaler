;; madomori 窓守 — rope/anchor rigging + per-anchor descent-load verification (★ G5).
;;
;; wind_envelope/work-permitted? already gates fall-arrest REDUNDANCY (≥2 independent
;; anchors — a single point of failure is refused). This method complements it with the
;; per-anchor LOAD check: each anchor that bears the descent must itself be RATED for the
;; working load times a required factor-of-safety, AND must be physically REACHABLE.
;;
;;   * `rig-ok?` — true iff there are ≥2 independent REACHABLE anchors, each rated at or
;;     above working-load × FoS. (Redundancy AND adequacy AND reachability.)
;;   * `assert-rig!` — raising variant: a descent may NOT be rigged below the safety
;;     margin, so an insufficient rig must SURFACE, never be silently planned (matches the
;;     G5 fall-safety discipline of wind_envelope/work-permitted? + adhesion/adhesion-safe?).
;;
;; Purely structural/mechanical — anchors are {:anchor-id :rated-kn :reachable?}; there is
;; NO imagery / interior / person / biometric attribute anywhere (G3, asserted in tests).
;;
;; Pure Clojure, no deps → babashka-runnable AND kotoba-pywasm-portable.
;; Per ADR-2606142020 (madomori R0).
(ns madomori.methods.anchor)

(def ^:const default-fos
  "Required factor-of-safety on each load-bearing anchor (rated / working load).
   2.0 = each anchor must hold twice the working load before it is permitted."
  2.0)

(def ^:const required-independent
  "Independent reachable, adequately-rated anchors a descent must have (no SPOF)."
  2)

;; ── per-anchor adequacy ────────────────────────────────────────────────────────
(defn anchor-adequate?
  "True iff an anchor is REACHABLE and its rating meets working-load × FoS.
   `anchor` = {:anchor-id :rated-kn :reachable?}."
  [{:keys [rated-kn reachable?]} working-load-kn fos]
  (boolean (and reachable?
                (number? rated-kn)
                (>= rated-kn (* working-load-kn fos)))))

(defn adequate-anchors
  "The subseq of `anchors` that are reachable AND rated ≥ working-load × FoS."
  [anchors working-load-kn fos]
  (filter #(anchor-adequate? % working-load-kn fos) anchors))

;; ── the rig predicate ──────────────────────────────────────────────────────────
(defn rig-ok?
  "True iff a descent may be rigged: ≥2 independent REACHABLE anchors, each rated at or
   above `working-load-kn` × FoS. FoS defaults to 2.0. A working load must be positive."
  ([anchors working-load-kn] (rig-ok? anchors working-load-kn default-fos))
  ([anchors working-load-kn fos]
   (when-not (pos? working-load-kn)
     (throw (ex-info "working load must be positive" {:working-load-kn working-load-kn})))
   (>= (count (adequate-anchors anchors working-load-kn fos)) required-independent)))

;; ── the gate (★ G5 — RAISES) ─────────────────────────────────────────────────
(defn assert-rig!
  "★ G5 rigging gate. Returns the qualifying (adequate) anchors when `rig-ok?` is true;
   otherwise RAISES (it never returns false/nil — an under-rigged descent must surface,
   not be papered over). Causes: too few reachable anchors, any bearing anchor under-rated
   below working-load × FoS, or an anchor that is unreachable. A descent may not be rigged
   below the safety margin (matches wind_envelope + adhesion fall-safety discipline)."
  ([anchors working-load-kn] (assert-rig! anchors working-load-kn default-fos))
  ([anchors working-load-kn fos]
   (let [ok (adequate-anchors anchors working-load-kn fos)]
     (when-not (rig-ok? anchors working-load-kn fos)
       (throw (ex-info "RIGGING REFUSED — a descent needs ≥2 independent reachable anchors each rated ≥ load×FoS (G5)"
                       {:working-load-kn working-load-kn
                        :fos fos
                        :required-kn (* working-load-kn fos)
                        :anchors anchors
                        :adequate-count (count ok)
                        :required required-independent})))
     (vec ok))))

(defn assess
  "Non-raising rigging summary for a report. `:rig-ok?` is the boolean result; never throws.
   Purely structural quantities — no imagery/person data (G3)."
  [anchors working-load-kn fos]
  (let [ok (adequate-anchors anchors working-load-kn fos)]
    {:working-load-kn working-load-kn
     :fos fos
     :required-kn (* working-load-kn fos)
     :total-anchors (count anchors)
     :adequate-anchors (count ok)
     :required-independent required-independent
     :rig-ok? (try (rig-ok? anchors working-load-kn fos) (catch Exception _ false))}))

(defn -main [& _]
  (let [load-kn 4.0
        compliant [{:anchor-id :a1 :rated-kn 12.0 :reachable? true}
                   {:anchor-id :a2 :rated-kn 10.0 :reachable? true}]
        under     [{:anchor-id :b1 :rated-kn 6.0 :reachable? true}   ; < 4×2 = 8 kN
                   {:anchor-id :b2 :rated-kn 12.0 :reachable? true}]]
    (println "madomori 窓守 — anchor rigging (★ G5 per-anchor descent-load verification)")
    (println (format "  working load %.1f kN · required FoS %.1f → each anchor must hold ≥ %.1f kN"
                     load-kn default-fos (* load-kn default-fos)))
    (println "  compliant rig:" (pr-str (assess compliant load-kn default-fos)))
    (println "  under-rated rig:" (pr-str (assess under load-kn default-fos)))
    (print "  assert-rig! on the under-rated rig → ")
    (try (assert-rig! under load-kn default-fos)
         (println "(no raise — UNEXPECTED)")
         (catch clojure.lang.ExceptionInfo e
           (println "RAISED:" (.getMessage e))))
    (flush)))
