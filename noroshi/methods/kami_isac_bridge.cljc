(ns noroshi.methods.kami-isac-bridge
  "noroshi (烽) ↔ kami-autodrive ISAC sensor bridge (ADR-2606051600 §R1a). Stdlib only.
  1:1 Clojure port of methods/kami_isac_bridge.py.

  kami-autodrive (ADR-2606010600) runs a perception→planning→control GNC loop over a world of
  moving agents. noroshi's ISAC waveform is the natural SENSOR for that loop: the same photonic/RF
  front-end that carries the link also senses the range + radial velocity of the civilian objects
  around the ego-craft, feeding collision-avoidance. This bridge drives the noroshi ISAC estimator
  from a kami-autodrive-style scenario (objects with a range and a constant radial velocity, sampled
  over frames) and produces a per-object track.

  HONEST INTEGRATION STATE (G10): the 40-engine/kami-engine submodule is unpopulated in this
  checkout, so the live Rust wiring is a WIT contract + this reference, not a compiled crate.
  Sensing is CIVILIAN object range/velocity only — never a person, never fire-control
  (G3/G4/N1/N2). No live emission (G8); deterministic offline DSP.

  House style: kebab keyword keys; Python ':…' strings stay literal strings; pure fns; file I/O at
  #?(:clj) edges. round(t, 4) reproduces Python banker's rounding HALF_EVEN; the report f-strings are
  exact. Portable .cljc."
  (:require [clojure.string :as str]
            [noroshi.methods.isac-sim :as isac]))

;; ── ScenarioObject / TrackPoint (kebab-keyed maps) ───────────────────────────
(defn scenario-object
  "A civilian object the ego-craft must sense for collision-avoidance (NEVER a person — N2/G4).
   {:object-id :range0-m :velocity-mps}."
  [object-id range0-m velocity-mps]
  {:object-id object-id :range0-m (double range0-m) :velocity-mps (double velocity-mps)})

(defn track-point
  [frame time-s range-m velocity-mps range-bin doppler-bin]
  {:frame frame :time-s time-s :range-m range-m :velocity-mps velocity-mps
   :range-bin range-bin :doppler-bin doppler-bin})

(defn track-object
  "Sense one object across `frames` snapshots → a kinematic track (range closes at its velocity).
  Defaults: frames=8, frame-dt-s=0.002. round(t,4) matches Python."
  [wf obj & {:keys [frames frame-dt-s] :or {frames 8 frame-dt-s 0.002}}]
  (loop [k 0, track []]
    (if (>= k frames)
      track
      (let [t (* k frame-dt-s)
            rng (- (:range0-m obj) (* (:velocity-mps obj) t))]   ; range closes over time
        (if (<= rng 0)
          track                                                  ; passed the ego-craft; stop sensing
          (let [est (isac/estimate-target wf (isac/target rng (:velocity-mps obj)))
                tp (track-point k (isac/py-round t 4)
                                (:range-m est) (:velocity-mps est)
                                (:range-bin est) (:doppler-bin est))]
            (recur (inc k) (conj track tp))))))))

(defn run-scenario
  "Run the ISAC sensor over a kami-autodrive-style multi-object scenario → {object-id track}.
  Preserves insertion order (array-map) so the report renders objects in scenario order."
  [objects & {:keys [wf frames frame-dt-s] :or {frames 8 frame-dt-s 0.002}}]
  (let [wf (or wf (isac/waveform))]
    (reduce (fn [m o]
              (assoc m (:object-id o)
                     (track-object wf o :frames frames :frame-dt-s frame-dt-s)))
            (array-map)
            objects)))

(defn sense-frame
  "One-shot multi-target scene sense: detect ALL objects from a single combined echo (CLEAN).
  Objects already at/behind the ego (range ≤ 0) are dropped."
  [objects & {:keys [wf]}]
  (let [wf (or wf (isac/waveform))
        targets (->> objects
                     (filter #(> (:range0-m %) 0))
                     (mapv #(isac/target (:range0-m %) (:velocity-mps %))))]
    (isac/estimate-targets wf targets)))

;; A :representative kami-autodrive scenario: two civilian objects converging with the ego.
(defn demo-scenario []
  (let [wf (isac/waveform)]
    [(scenario-object "lead-vehicle" (* 4 (isac/range-resolution-m wf))
                      (* 2 (isac/velocity-resolution-mps wf)))
     (scenario-object "cross-object" (* 10 (isac/range-resolution-m wf))
                      (* 1 (isac/velocity-resolution-mps wf)))]))

(defn report
  ([] (report (demo-scenario)))
  ([objects]
   (let [wf (isac/waveform)
         tracks (run-scenario objects :wf wf)
         L (transient
            ["# noroshi 烽 × kami-autodrive — ISAC sensor in the GNC loop"
             ""
             (str "waveform: " (isac/fmt-f (/ (isac/bandwidth-hz wf) 1e6) 0) " MHz, ΔR="
                  (isac/fmt-f (isac/range-resolution-m wf) 2) " m, "
                  "Δv=" (isac/fmt-f (isac/velocity-resolution-mps wf) 1)
                  " m/s. Civilian objects only (collision-avoidance; N1/N2).")
             ""])]
     (doseq [[oid track] tracks]
       (conj! L (str "## track: " oid "  (" (count track) " frames)"))
       (conj! L "| frame | t (s) | range (m) | velocity (m/s) | bins (k,l) |")
       (conj! L "|---|---|---|---|---|")
       (doseq [p track]
         (conj! L (str "| " (:frame p) " | " (isac/py-float-repr (:time-s p)) " | "
                       (isac/fmt-f (:range-m p) 2) " | " (isac/fmt-f (:velocity-mps p) 1)
                       " | (" (:range-bin p) "," (:doppler-bin p) ") |")))
       (conj! L ""))
     (conj! L (str "> The ISAC estimate feeds kami-autodrive perception (the `IsacSensor` plant the "
                   "WIT contract `wit/kami-isac.wit` defines). HONEST: the kami-engine submodule is "
                   "unpopulated here, so this is the data bridge + interface contract, not a compiled "
                   "crate; live emission is G8-gated."))
     (str/join "\n" (persistent! L)))))

#?(:clj
   (defn -main
     "CLI entry: print the offline kami-isac track report (1:1 with `python3 kami_isac_bridge.py`)."
     [& _argv]
     (println (report))
     0))
