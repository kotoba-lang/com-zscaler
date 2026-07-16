;; madomori 窓守 — kotoba Datom-log emitter (canonical EAVT state, ADR-2605312345).
;;
;; Projects the building-façade graph into append-only kotoba Datoms [e a v tx op].
;;   GROUND (op :add, durable) — face / pane-grid / robot node datoms + anchor 縁.
;;     This IS the Datom log.
;;   DERIVED (op :derived, transient :bond/is-transient true) — coverage length,
;;     consumable budget, sway amplitude, permit + adhesion-safe gate readouts;
;;     computed on READ, NOT persisted (N1/G2 pattern, mirrors asobi/kuramori).
;;
;; `emit-day` extends this to the FULL façade-campaign-DAY pipeline (multi-face
;; route, anchor-rig, water-recovery, defect→tatekata handoff) so the canonical log
;; captures the whole day, not just the base run (mirrors kuramori/datom_emit).
;;
;; G3 (privacy-by-construction) is STRUCTURAL here: no imagery / interior /
;; person / biometric attribute is emittable. The robot's only imagery datom is
;; `:mado.robot/imagery-on-device` = true — imagery off-device is unrepresentable.
;; Every day-line is a geometric/structural quantity (face grids, distances, litres,
;; kN, a structural defect descriptor) — asserted in tests.
;;
;; Pure Clojure, no deps → babashka-runnable AND kotoba-pywasm-portable.
;; Per ADR-2606142020 (madomori R0).
(ns madomori.methods.datom-emit
  (:require [clojure.string :as str]
            [madomori.methods.analyze :as az]))

(defn fmt
  "Format a value as an EDN Datom field: keywords bare, strings quoted, bools/nil literal."
  [v]
  (cond
    (true? v) "true"
    (false? v) "false"
    (nil? v) "nil"
    (keyword? v) (str v)
    (string? v) (if (str/starts-with? v ":")
                  v
                  (str \" (str/escape v {\\ "\\\\" \" "\\\""}) \"))
    (float? v) (format "%g" v)
    :else (str v)))

(defn datom [e a v tx op]
  (str "[" (fmt e) " " (str a) " " (fmt v) " " tx " " (str op) "]"))

(defn- base-lines
  "Inner GROUND + DERIVED datom lines for the base `run` result (no header/brackets)."
  [seed res tx]
  (let [face (:face seed)
        robot (:robot seed)]
    (concat
     ;; GROUND — the building face
     [(datom (:id face) :mado.face/surface (:surface face) tx :add)
      (datom (:id face) :mado.face/height-m (double (:height-m face 0)) tx :add)
      (datom (:id face) :mado.face/rows (:rows face 0) tx :add)
      (datom (:id face) :mado.face/cols (:cols face 0) tx :add)
      (datom (:id face) :mado.face/panes (* (:rows face 0) (:cols face 0)) tx :add)
      ;; GROUND — panes as a grid attribute (one course-count per face; per-pane nodes
      ;;          are not exploded in R0 — the coverage plan derives them on read)
      (datom (:id face) :mado.pane/h-m (double (:pane-h-m face 0)) tx :add)
      (datom (:id face) :mado.pane/w-m (double (:pane-w-m face 0)) tx :add)
      ;; GROUND — robot envelope
      (datom (:id robot) :mado.robot/mode (:mode robot) tx :add)
      (datom (:id robot) :mado.robot/mass-kg (double (:mass-kg robot 0)) tx :add)
      (datom (:id robot) :mado.robot/suction-force-n (double (:suction-force-n robot 0)) tx :add)
      (datom (:id robot) :mado.robot/required-fos (double (:required-fos robot 0)) tx :add)
      ;; G3 — the ONLY imagery datom; imagery off-device is unrepresentable
      (datom (:id robot) :mado.robot/imagery-on-device true tx :add)]
     ;; GROUND — anchor 縁 (face → anchor; ≥2 independent = fall-arrest redundancy, G5)
     (mapcat (fn [a]
               [(datom (str "en." (:id face) ".anchored-by." (:id a)) :en/from (:id face) tx :add)
                (datom (str "en." (:id face) ".anchored-by." (:id a)) :en/to (:id a) tx :add)
                (datom (str "en." (:id face) ".anchored-by." (:id a)) :en/kind :anchored-by tx :add)
                (datom (:id a) :mado.anchor/independent (boolean (:independent a)) tx :add)])
             (:anchors seed))
     ;; DERIVED — transient readouts (computed on read; not durable)
     [";; ── DERIVED readouts (transient; computed on read) ──"
      (datom (:id face) :bond/coverage-complete
             (boolean (get-in res [:coverage :coverage :complete?])) tx :derived)
      (datom (:id face) :bond/path-length-m
             (double (get-in res [:coverage :length-m])) tx :derived)
      (datom (:id face) :bond/water-l
             (double (get-in res [:coverage :budget :water-l])) tx :derived)
      (datom (:id face) :bond/agent-ml
             (double (get-in res [:coverage :budget :agent-ml])) tx :derived)
      (datom (:id face) :bond/sway-amplitude-m
             (double (get-in res [:envelope :sway-amplitude-m])) tx :derived)
      (datom (:id face) :bond/wind-permitted
             (boolean (get-in res [:envelope :permitted?])) tx :derived)
      (datom (:id robot) :bond/adhesion-fos
             (double (get-in res [:adhesion :factor-of-safety])) tx :derived)
      (datom (:id robot) :bond/adhesion-safe
             (boolean (get-in res [:adhesion :safe?])) tx :derived)
      (datom (:id (:facility seed)) :bond/go (boolean (:go? res)) tx :derived)])))

(defn- day-lines
  "Inner datom lines for the FULL façade-campaign-DAY pipeline artifacts — so the
   canonical log captures the whole day (multi-face route, anchor-rig, water-recovery,
   defect→tatekata handoff) not just the base run. GROUND :add for operations;
   DERIVED for metrics. ★ G3 — every line is a geometric/structural quantity ONLY
   (face counts, metres, litres, kN, a structural defect descriptor); NO imagery /
   person / interior / biometric / camera attribute is representable."
  [res tx]
  (let [day (:day res)
        face-id (get-in res [:face :id])]
    (concat
     [";; ── run-day operations (GROUND) ──"]
     ;; defect→tatekata repair-order handoff 縁 (same shape as kuramori)
     (when-let [h (:handoff day)]
       (let [e (str "en.handoff." (:from-actor h) "." (:to-actor h) "." (:id h))]
         [(datom e :handoff/from-actor (:from-actor h) tx :add)
          (datom e :handoff/to-actor (:to-actor h) tx :add)
          (datom e :handoff/kind (:kind h) tx :add)]))
     ;; multi-face campaign route order (positional only — face ids, no imagery)
     (when-let [camp (:campaign day)]
       (for [fid (:order camp)]
         (datom (str "campaign." (name fid)) :mado.campaign/face-sequenced fid tx :add)))
     ;; DERIVED day metrics — geometric/structural quantities only
     [";; ── run-day metrics (DERIVED) ──"]
     (when-let [camp (:campaign day)]
       [(datom "campaign" :bond/faces-sequenced (count (:order camp)) tx :derived)
        (datom "campaign" :bond/reposition-distance-m (double (:reposition-distance camp)) tx :derived)
        (datom "campaign" :bond/campaign-coverage-length-m (double (:coverage-length-m camp)) tx :derived)])
     (when-let [cov (:coverage day)]
       [(datom face-id :bond/coverage-path-length-m (double (:length-m cov)) tx :derived)])
     (when-let [env (:envelope day)]
       [(datom face-id :bond/wind-work-permitted (boolean (:permitted? env)) tx :derived)])
     (when-let [adh (:adhesion day)]
       [(datom face-id :bond/adhesion-factor-of-safety (double (:factor-of-safety adh)) tx :derived)])
     (when-let [rig (:rig day)]
       [(datom face-id :bond/anchor-rig-ok (boolean (:rig-ok? rig)) tx :derived)
        (datom face-id :bond/anchor-adequate-count (:adequate-anchors rig) tx :derived)])
     (when-let [water (:water day)]
       [(datom face-id :bond/water-recovery-fraction (double (:recovery-fraction water)) tx :derived)
        (datom face-id :bond/water-recovery-compliant (boolean (:compliant? water)) tx :derived)]))))

(defn- wrap
  "Wrap inner datom lines in the generated header + EDN vector brackets.
   Byte-compatible with the R0 `emit` output: 4 header comments, `[`, datom lines,
   `]`, joined by newlines with a trailing newline."
  [lines]
  (str ";; madomori 窓守 — GENERATED kotoba Datom log (ADR-2606142020). DO NOT hand-edit.\n"
       ";; Canonical EAVT state (ADR-2605312345). [e a v tx op].\n"
       ";; GROUND op :add = durable. DERIVED :bond/is-transient = computed on read (N1/G2).\n"
       ";; G3 STRUCTURAL: only the on-device imagery flag is emittable.\n"
       "[\n" (str/join "\n" (remove nil? lines)) "\n]\n"))

(defn emit
  "Emit the façade Datom log as an EDN string. `seed` is the loaded map,
   `res` the analyze/run result, `tx` the transaction number. Output is unchanged
   from the R0 emitter."
  [seed res tx]
  (wrap (base-lines seed res tx)))

(defn emit-day
  "Emit the FULL day Datom log — base GROUND/DERIVED PLUS the run-day pipeline
   artifacts (multi-face route, anchor-rig, water-recovery, defect→tatekata handoff).
   `day-res` = az/run-day. ★ G3 — geometric/structural quantities only."
  [seed day-res tx]
  (wrap (concat (base-lines seed day-res tx) (day-lines day-res tx))))

(defn -main [& args]
  (let [path (or (first args) "20-actors/madomori/data/facade.edn")
        seed (az/load-seed path)
        day-res (az/run-day seed)]
    (print (emit-day seed day-res 1))
    (flush)))
