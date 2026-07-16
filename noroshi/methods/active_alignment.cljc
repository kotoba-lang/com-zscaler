(ns noroshi.methods.active-alignment
  "noroshi (烽) active-alignment + laser-safety core — the packaging-robotics face
  (ADR-2606051600). 1:1 Clojure port of methods/active_alignment.py. Stdlib only
  (Math/ + BigDecimal).

  A photonic packaging robot aligns an optical fibre to an on-chip grating coupler by
  *active alignment* — it sweeps the fibre tip while measuring coupled power and climbs to
  the peak — and it may only energise the alignment laser through a hard **laser-safety
  interlock** (IEC 60825 class gate) and a **civilian-use** gate. Kept pure + deterministic
  so it can be unit-tested before any robot or laser exists.

  Two responsibilities, in priority order:

    1. enable-laser  — REFUSE to energise unless (a) the intended use is civilian (N1:
                       weaponisation / directed-energy / dazzle is structurally unrepresentable)
                       and (b) for any class above Class 1, a physical enclosure interlock +
                       safety attestation are present. Best-effort soft-safety, NOT an IEC
                       60825 certified safety controller (R5/Lv7+).
    2. align         — Hooke-Jeeves pattern search over fibre (dx,dy) maximising coupling
                       efficiency (a Gaussian of misalignment), converging to the unknown
                       peak within tolerance.
    3. classify-laser-class — (NEW, this maturity pass) ground-truth IEC 60825 class
                       recompute from power-mw + wavelength-nm, independent of any
                       caller-claimed class string. `cells/active_alignment/state_machine`
                       calls this to refuse a laser whose claimed class understates its
                       recomputed hazard — see this ns's `representative-ael-mw` docstring
                       for the G10 sourcing-honesty scope of this simplified model.

  No hardware, no live laser, no live actuation (G7 outward-gated).

  CONSTITUTIONAL gates (noroshi CLAUDE.md):
    G3 civilian-force-separation — `enable-laser` REFUSES any non-civilian / weaponisation
       use; PERMITTED-USES / FORBIDDEN-USES are the closed vocabulary (N1, Charter §1.12).
    G5 laser-safety-soft / IEC 60825 — any class above Class 1 needs an enclosure interlock
       + safety attestation before energising; the gate raises ex-info exactly where the
       Python raises LaserSafetyError. Soft-safety, NOT a certified controller (N3).

  House style: kebab keyword keys; Python ':…' strings stay literal strings; pure fns; file
  I/O only at #?(:clj) edges; closed-vocab/gate violations → ex-info. Float arithmetic is
  EXACT: round(x, n) reproduces Python's banker's rounding (HALF_EVEN) on the exact double,
  then the result is re-widened to a double so its shortest repr matches Python's; the
  Gaussian / log10 transcendentals are last-ULP via Math/. The Hooke-Jeeves trajectory is
  bit-identical: the four exploratory neighbours are probed in the same order
  ((step,0) (-step,0) (0,step) (0,-step)), the first STRICTLY-improving (>) move is accepted
  and breaks the inner loop, and the step is halved only when no neighbour improves — so the
  coordinate visit order, accept/reject decisions, and step-halving sequence all match.
  Portable .cljc."
  (:require [clojure.string :as str]))

;; ── float helpers: Python round(x, n) / repr(float) — byte-identical ────────
;; Python round(x, n) = round-half-to-even on the EXACT value of the double, returning a
;; float. BigDecimal.(double x) (the exact binary value) → setScale n HALF_EVEN → back to a
;; double. The double prints with the shortest round-trip representation (Java Double/toString
;; == Python repr for these magnitudes), so the report bytes match exactly.
#?(:clj
   (defn py-round
     "Python round(x, n) → a double rounded HALF_EVEN at n decimal places."
     [x n]
     (-> (java.math.BigDecimal. (double x))
         (.setScale (int n) java.math.RoundingMode/HALF_EVEN)
         .doubleValue))
   :cljs
   (defn py-round [x n]
     (let [f (Math/pow 10 n)] (/ (Math/round (* (double x) f)) f))))

(defn py-float-repr
  "Python repr() of a float: shortest round-trip decimal, integral floats keep a trailing
  `.0` (4.0 not 4). Java's Double/toString already yields the shortest round-trip string and
  keeps the `.0`, matching CPython's float_repr for the magnitudes used here."
  [x]
  (let [d (double x)]
    #?(:clj
       (cond
         (and (Double/isInfinite d) (pos? d)) "inf"
         (and (Double/isInfinite d) (neg? d)) "-inf"
         (Double/isNaN d) "nan"
         :else (Double/toString d))
       :cljs
       (let [s (str d)] (if (re-find #"[.eE]" s) s (str s ".0"))))))

;; ── closed vocabularies (mirror the module-level Python tuples) ──────────────
;; Civilian photonic-fab uses only. Weaponisation is unrepresentable (N1).
(def permitted-uses
  "PERMITTED_USES — the closed civilian photonic-fab use vocabulary."
  ["alignment" "comms" "soldering" "trimming" "inspection"])

(def forbidden-uses
  "FORBIDDEN_USES — weaponisation / directed-energy can never be energised (N1)."
  ["weapon" "directed-energy" "dazzle" "fire-control"])

;; IEC 60825 laser classes; anything above Class 1 is potentially hazardous → interlock required.
(def hazardous-classes
  "HAZARDOUS_CLASSES — classes above Class 1 requiring an enclosure interlock."
  ["2" "3R" "3B" "4"])

;; ── IEC 60825 class independent recompute (ground truth, not a self-report) ──
(def visible-range-nm
  "The visible band [400, 700] nm — Class 2 exists only for a visible wavelength
  (protected by the human blink reflex)."
  [400.0 700.0])

(defn- visible-wavelength? [wavelength-nm]
  (let [[lo hi] visible-range-nm] (<= lo wavelength-nm hi)))

(def representative-ael-mw
  "Representative accessible-emission-limit (AEL) power boundaries (mW, CW),
  used to classify a laser by IEC 60825-1 class. These are ROUGH, PUBLICLY-CITED
  order-of-magnitude figures — NOT verified citations to the standard's actual
  wavelength- and exposure-time-dependent AEL tables (G10 sourcing-honesty). A
  real safety classification MUST use the licensed IEC 60825-1 tables for the
  laser's actual wavelength and exposure duration; this is a simplified,
  representative model that also ignores the 1M/2M divergent-beam sub-classes."
  {:class-1 0.39 :class-2 1.0 :class-3r 5.0 :class-3b 500.0})

(defn classify-laser-class
  "Classify a CW laser's IEC 60825 class from `power-mw` and `wavelength-nm`
  against `representative-ael-mw` (G10 — see its docstring). Class 2 requires a
  visible wavelength; a non-visible laser at Class-2-range power classifies as
  Class 3R instead (no blink-reflex protection outside the visible band).

  This is a GROUND-TRUTH RECOMPUTE from physical parameters, independent of any
  caller-claimed class — the laser-safety analogue of `netops.registry/route-
  endpoints-missing?`'s independent structural check elsewhere in this
  workspace: never trust a self-reported safety class when the underlying
  physical quantities are available to recompute it."
  [power-mw wavelength-nm]
  (let [{:keys [class-1 class-2 class-3r class-3b]} representative-ael-mw
        visible? (visible-wavelength? wavelength-nm)]
    (cond
      (<= power-mw class-1) "1"
      (and visible? (<= power-mw class-2)) "2"
      (<= power-mw class-3r) "3R"
      (<= power-mw class-3b) "3B"
      :else "4")))

;; ── LaserSpec (mirror the frozen dataclass field defaults) ───────────────────
(defn laser-spec
  "Build a LaserSpec-equivalent map, overriding the dataclass defaults with kwargs.
  kebab keyword keys; defaults: class \"1\", use \"alignment\", no interlock, no attestation."
  [& {:as overrides}]
  (merge {:laser-class "1"           ; IEC 60825 class
          :use "alignment"           ; must be in permitted-uses
          :enclosure-interlock false ; physical beam enclosure / door interlock present
          :safety-attestation-ref ""}; operator safety attestation
         overrides))

(defn enable-laser
  "Raise unless the laser may be energised. No return value (gate only).

  Civilian-use gate first (N1), then the IEC 60825 interlock gate for any hazardous class.
  Raises ex-info (the LaserSafetyError analogue) exactly where the Python raises."
  [spec]
  (let [use (:use spec)
        cls (:laser-class spec)]
    (when (or (some #{use} forbidden-uses) (not (some #{use} permitted-uses)))
      (throw (ex-info (str "N1: use " (pr-str use)
                           " is not a permitted civilian photonic-fab use; "
                           "weaponisation / directed-energy can never be energised (Mission Charter §1.12)")
                      {:error "LaserSafetyError" :gate "N1" :use use})))
    (when (some #{cls} hazardous-classes)
      (when-not (:enclosure-interlock spec)
        (throw (ex-info (str "IEC 60825: a Class-" cls " laser requires a physical enclosure "
                             "interlock before energising (soft-safety gate; not a certified safety controller)")
                        {:error "LaserSafetyError" :gate "IEC60825" :laser-class cls})))
      (when (str/blank? (:safety-attestation-ref spec))
        (throw (ex-info (str "IEC 60825: a Class-" cls " laser requires an operator safety "
                             "attestation reference before energising")
                        {:error "LaserSafetyError" :gate "IEC60825" :laser-class cls}))))))

;; ── coupling model + active-alignment search ─────────────────────────────────
(defn coupler-model
  "Gaussian coupling vs lateral misalignment from the (unknown) optimal fibre offset.
  Mirror of the CouplerModel frozen dataclass defaults. kebab keyword keys."
  [& {:as overrides}]
  (merge {:peak-efficiency 0.80   ; η0 at perfect alignment (grating-coupler ~ -1 dB)
          :mode-radius-um 5.0     ; 1/e alignment tolerance (µm)
          :opt-x-um 2.3           ; unknown true peak offset the robot must FIND
          :opt-y-um -1.7}
         overrides))

(defn efficiency
  "model.efficiency(dx, dy): η0 · exp(-r²/w²), r² = (dx-optx)² + (dy-opty)²."
  [model dx-um dy-um]
  (let [r2 (+ (Math/pow (- dx-um (:opt-x-um model)) 2)
              (Math/pow (- dy-um (:opt-y-um model)) 2))]
    (* (:peak-efficiency model)
       (Math/exp (/ (- r2) (Math/pow (:mode-radius-um model) 2))))))

(defn loss-db
  "CouplerModel.loss_db(efficiency): -10·log10(max(efficiency, 1e-12))."
  [eff]
  (* -10.0 (Math/log10 (max eff 1e-12))))

;; ── Hooke-Jeeves pattern search ──────────────────────────────────────────────
(defn align
  "Hooke-Jeeves pattern search for peak coupling. Raises (via enable-laser) before any probe.

  Probes the four axis-aligned neighbours ((step,0) (-step,0) (0,step) (0,-step)) at the
  current step; moves to the FIRST STRICTLY-improving one (eff > best, break), else halves
  the step. Terminates when the step shrinks below tol-um (converged) or the probe budget is
  exhausted. Deterministic — same model + start ⇒ same trajectory.

  Returns an AlignmentResult-equivalent map (kebab keys):
    {:x-um :y-um :efficiency :loss-db :probes :converged}."
  [model laser
   & {:keys [start-x-um start-y-um step-um tol-um max-probes]
      :or {start-x-um 0.0 start-y-um 0.0 step-um 4.0 tol-um 0.05 max-probes 2000}}]
  (enable-laser laser)                                  ; safety gate BEFORE energising / probing
  (loop [x start-x-um
         y start-y-um
         best (efficiency model start-x-um start-y-um)
         step step-um
         probes 1]
    (if (and (> step tol-um) (< probes max-probes))
      ;; one outer iteration: probe the four neighbours in order, accept first improver.
      (let [moves [[step 0.0] [(- step) 0.0] [0.0 step] [0.0 (- step)]]
            ;; fold over moves, threading [probes best x y improved?]; once improved, no
            ;; further probe is taken (break) — exactly the Python for/break semantics.
            [probes' best' x' y' improved]
            (reduce (fn [[p b cx cy _] [dx dy]]
                      (let [p2 (inc p)
                            eff (efficiency model (+ cx dx) (+ cy dy))]
                        (if (> eff b)
                          (reduced [p2 eff (+ cx dx) (+ cy dy) true])
                          [p2 b cx cy false])))
                    [probes best x y false]
                    moves)]
        (recur x' y' best'
               (if improved step (/ step 2.0))
               probes'))
      {:x-um (py-round x 4) :y-um (py-round y 4)
       :efficiency (py-round best 6) :loss-db (py-round (loss-db best) 4)
       :probes probes :converged (<= step tol-um)})))

(defn coarse-scan
  "Coarse acquisition: raster the fibre over ±span at ~mode-radius spacing → best
  [x y eff probes]. Raises (via enable-laser) before any probe."
  [model laser & {:keys [span-um step-um] :or {span-um 70.0 step-um nil}}]
  (enable-laser laser)
  (let [step (if (nil? step-um) (:mode-radius-um model) step-um)]
    (when (or (<= step 0) (<= span-um 0))
      (throw (ex-info "span_um and step_um must be positive"
                      {:error "ValueError" :span-um span-um :step-um step})))
    (let [n (int (/ span-um step))]
      (loop [i (- n)
             best-x 0.0 best-y 0.0
             best-eff (efficiency model 0.0 0.0)
             probes 1]
        (if (> i n)
          [best-x best-y best-eff probes]
          (let [[bx by be pr]
                (loop [j (- n) bx best-x by best-y be best-eff pr probes]
                  (if (> j n)
                    [bx by be pr]
                    (let [x (* i step) y (* j step)
                          pr2 (inc pr)
                          eff (efficiency model x y)]
                      (if (> eff be)
                        (recur (inc j) x y eff pr2)
                        (recur (inc j) bx by be pr2)))))]
            (recur (inc i) bx by be pr)))))))

(defn spiral-search
  "Acquisition by an expanding-square spiral that STOPS on first signal → best
  [x y eff probes]. Bounded by ±span. Raises (via enable-laser) before any probe."
  [model laser & {:keys [span-um step-um detect-floor]
                  :or {span-um 70.0 step-um nil detect-floor 1e-6}}]
  (enable-laser laser)
  (let [step (if (nil? step-um) (:mode-radius-um model) step-um)]
    (when (or (<= step 0) (<= span-um 0))
      (throw (ex-info "span_um and step_um must be positive"
                      {:error "ValueError" :span-um span-um :step-um step})))
    (let [max-ring (int (/ span-um step))
          best0 [0.0 0.0 (efficiency model 0.0 0.0)]]
      (if (> (nth best0 2) detect-floor)
        [(nth best0 0) (nth best0 1) (nth best0 2) 1]
        ;; Expanding-square spiral: run lengths 1,1,2,2,3,3,… cycling R, U, L, D.
        (let [dirs [[1 0] [0 1] [-1 0] [0 -1]]]
          (loop [run 1 di 0 ix 0 iy 0 best best0 probes 1]
            (if (<= run (+ (* 2 max-ring) 1))
              ;; do two legs (the `for _ in range(2)` loop)
              (let [step-leg
                    (fn step-leg [leg-i di ix iy best probes]
                      ;; returns either [:return [x y eff probes]] or
                      ;; [:continue di ix iy best probes] after `leg-i` legs done.
                      (if (>= leg-i 2)
                        [:continue di ix iy best probes]
                        (let [[dx dy] (nth dirs (mod di 4))
                              ;; inner run of `run` steps
                              inner
                              (loop [k 0 ix ix iy iy best best probes probes]
                                (if (>= k run)
                                  [:done ix iy best probes]
                                  (let [ix2 (+ ix dx) iy2 (+ iy dy)]
                                    (if (> (max (Math/abs (int ix2)) (Math/abs (int iy2))) max-ring)
                                      [:return [(nth best 0) (nth best 1) (nth best 2) probes]]
                                      (let [x (* ix2 step) y (* iy2 step)
                                            probes2 (inc probes)
                                            eff (efficiency model x y)
                                            best2 (if (> eff (nth best 2)) [x y eff] best)]
                                        (if (> eff detect-floor)
                                          [:return [(nth best2 0) (nth best2 1) (nth best2 2) probes2]]
                                          (recur (inc k) ix2 iy2 best2 probes2)))))))]
                          (case (first inner)
                            :return inner
                            :done (let [[_ ix2 iy2 best2 probes2] inner]
                                    (step-leg (inc leg-i) (inc di) ix2 iy2 best2 probes2))))))
                    res (step-leg 0 di ix iy best probes)]
                (case (first res)
                  :return (second res)
                  :continue (let [[_ di2 ix2 iy2 best2 probes2] res]
                              (recur (inc run) di2 ix2 iy2 best2 probes2))))
              [(nth best 0) (nth best 1) (nth best 2) probes])))))))

(defn align-two-stage
  "Coarse acquisition → Hooke-Jeeves fine refinement. Robust to a far / narrow-lobe start.
  `acquire` ∈ {\"raster\" \"spiral\"}. Total probe count sums both stages."
  [model laser & {:keys [span-um coarse-step-um fine-tol-um acquire]
                  :or {span-um 70.0 coarse-step-um nil fine-tol-um 0.05 acquire "raster"}}]
  (let [step (if (nil? coarse-step-um) (:mode-radius-um model) coarse-step-um)
        [cx cy _ cprobes]
        (cond
          (= acquire "spiral") (spiral-search model laser :span-um span-um :step-um step)
          (= acquire "raster") (coarse-scan model laser :span-um span-um :step-um step)
          :else (throw (ex-info "acquire must be 'raster' or 'spiral'"
                                {:error "ValueError" :acquire acquire})))
        fine (align model laser :start-x-um cx :start-y-um cy :step-um step :tol-um fine-tol-um)]
    {:x-um (:x-um fine) :y-um (:y-um fine) :efficiency (:efficiency fine)
     :loss-db (:loss-db fine) :probes (+ cprobes (:probes fine)) :converged (:converged fine)}))

;; ── report (the packaging-robotics face out/ artifact) ───────────────────────
(defn report
  "Render the packaging-robotics face out/ artifact (1:1 with active_alignment.report)."
  ([] (report (coupler-model)))
  ([model]
   (let [safe (laser-spec :laser-class "1" :use "alignment")
         res (align model safe)
         lines ["# noroshi 烽 — photonic active alignment (fibre ↔ grating coupler)"
                ""
                (str "- true peak offset : (" (py-float-repr (:opt-x-um model)) ", "
                     (py-float-repr (:opt-y-um model)) ") µm  (unknown to the robot)")
                (str "- found offset     : (" (py-float-repr (:x-um res)) ", "
                     (py-float-repr (:y-um res)) ") µm  in " (:probes res) " probes "
                     "(" (if (:converged res) "converged" "budget-exhausted") ")")
                (str "- coupling         : η = " (py-float-repr (:efficiency res))
                     "  → insertion loss " (py-float-repr (:loss-db res)) " dB")
                ""
                "## laser-safety interlock (IEC 60825 + N1 civilian-use)"
                "- Class 1 alignment laser              → energise OK"
                "- Class 4 without enclosure interlock  → REFUSED"
                "- use = 'directed-energy' / 'weapon'   → REFUSED (structurally unrepresentable, N1)"
                ""
                (str "> R0 simulation only — no robot, no live laser, no live actuation (G7). "
                     "A live fleet displaces human alignment technicians ⇒ G2-coupled to the "
                     "Displacement Dividend (ADR-2606032130).")]]
     (str/join "\n" lines))))

#?(:clj
   (defn -main
     "CLI entry: print the offline alignment report (1:1 with `python3 active_alignment.py`)."
     [& _argv]
     (println (report))
     0))
