(ns silicon.methods.fab-flow
  "silicon 珪 — 8-工程 wafer-lot fab-flow simulation (design-implementation, R0).

  Brings the silicon fab to the same runnable cljc + kotoba-Datom level as the
  other manufacturing actors (niyaku / giemon / sarutahiko). The fab is modelled
  as a deterministic process-physics flow: a wafer-lot threads through the route

      litho → deposition → etch → implant → cmp → metrology → test → packaging

  and each step applies a first-order process model that emits *measured* outputs
  and contributes to a cumulative defect density, which the test step converts to
  die yield via the Poisson (Murphy/Seeds) model  Y = exp(-D·A).

  Per ADR-2605242500 + 2605242545. Pure Clojure (clojure.core only); portable .cljc.

  HARD GATES (silicon manifest):
    G1  dual-use force-review — litho/implant recipes are dual-use; `force-review-required?`
        flags them and `assert-attested` refuses an un-attested run.
    G11 outward-gated — this is DRY-RUN design only. `dispatch-equipment!` (real
        actuation) is structurally unrepresentable at R0 and raises a :council-gate
        ex-info. No method here moves real fab equipment."
  (:require [clojure.string :as str]))

;; ── numeric helpers ─────────────────────────────────────────────────────────

(defn- round
  "Round x to n decimal places (deterministic, for stable records/tests)."
  [n x]
  (let [f (Math/pow 10.0 n)]
    (/ (Math/round (* (double x) f)) f)))

(def ^:private r2 (partial round 2))
(def ^:private r4 (partial round 4))

;; ── the route ───────────────────────────────────────────────────────────────

(def default-route
  "Canonical 8-工程 sequence (ADR-2605242545)."
  ["litho" "deposition" "etch" "implant" "cmp" "metrology" "test" "packaging"])

(def ^:private dual-use-steps
  "Steps whose equipment is export-controlled / weaponizable (Charter Rider §2(a)(c))."
  #{"litho" "implant"})

(defn force-review-required?
  "G1 — true iff `step` drives dual-use equipment needing a silen-force-attest."
  [step]
  (contains? dual-use-steps step))

;; ── per-step process-physics models ─────────────────────────────────────────
;;
;; Each model takes the running wafer-state + the step's recipe map and returns
;; {:measured {...} :defects <added defect density, cm^-2> :pass <bool>}.
;; The models are intentionally simple but physically grounded (cited in docstrings).

(defn- step-litho
  "Bossung-like CD model: CD = target + k_dose·(dose-dose0) + k_focus·focus².
  Defocus is quadratic (symmetric about best focus); dose is ~linear."
  [_state {:keys [target-cd-nm dose dose0 focus-nm k-dose k-focus]
           :or {dose0 30.0 k-dose 0.6 k-focus 0.004}}]
  (let [cd (+ target-cd-nm
              (* k-dose (- dose dose0))
              (* k-focus focus-nm focus-nm))
        cd-err (/ (Math/abs (- cd target-cd-nm)) target-cd-nm)
        ;; CD excursion seeds defects; base printability floor.
        defects (+ 0.02 (* 1.8 cd-err))]
    {:measured {:cd-nm (r2 cd)
                :cd-error-pct (r2 (* 100.0 cd-err))
                :overlay-nm (r2 (* 0.25 (Math/abs focus-nm)))}
     :defects (r4 defects)
     :pass (<= cd-err 0.10)}))

(defn- step-deposition
  "Conformal film: thickness = rate·time; within-wafer uniformity degrades with rate."
  [state {:keys [material rate-nm-min time-min] :or {material "SiO2"}}]
  (let [thk (* rate-nm-min time-min)
        unif (max 90.0 (- 99.5 (* 0.4 rate-nm-min)))]
    {:measured {:material material
                :thickness-nm (r2 thk)
                :uniformity-pct (r2 unif)}
     :defects (r4 (+ 0.01 (* 0.02 (max 0.0 (- rate-nm-min 5.0)))))
     :pass (>= unif 95.0)
     :state-delta {:film-nm thk}}))

(defn- step-etch
  "Anisotropic plasma etch: depth = rate·time; remaining film = film - depth.
  Over-etch into the layer below is a defect source."
  [state {:keys [rate-nm-min time-min selectivity] :or {selectivity 8.0}}]
  (let [film (get-in state [:wafer :film-nm] 0.0)
        depth (* rate-nm-min time-min)
        remaining (- film depth)
        over-etch (max 0.0 (- remaining))
        ler (max 1.0 (- 4.0 (* 0.2 selectivity)))]    ; line-edge roughness, nm
    {:measured {:etch-depth-nm (r2 depth)
                :remaining-film-nm (r2 (max 0.0 remaining))
                :selectivity selectivity
                :ler-nm (r2 ler)}
     :defects (r4 (+ 0.03 (* 0.05 over-etch) (* 0.04 (max 0.0 (- ler 2.0)))))
     :pass (and (>= remaining -1.0) (<= ler 3.0))
     :state-delta {:film-nm (max 0.0 remaining)}}))

(defn- step-implant
  "Ion implant: projected range Rp ≈ a·energy^0.7 (LSS-like power law);
  sheet resistance falls as dose rises (Rs ∝ 1/dose)."
  [_state {:keys [species energy-kev dose-cm2 a] :or {species "B+" a 11.0}}]
  (let [rp (* a (Math/pow energy-kev 0.7))           ; junction depth, nm
        rs (/ 4.5e15 dose-cm2)]                       ; sheet resistance, Ω/sq
    {:measured {:species species
                :junction-depth-nm (r2 rp)
                :sheet-resistance-ohm-sq (r2 rs)
                :dose-cm2 dose-cm2}
     :defects (r4 (+ 0.02 (* 0.00 energy-kev)))       ; channeling controlled
     :pass (and (> rp 10.0) (< rp 300.0))}))

(defn- step-cmp
  "Chemical-mechanical planarization: removed = rate·time; residual = film-removed."
  [state {:keys [removal-rate-nm-min time-min] :or {}}]
  (let [film (get-in state [:wafer :film-nm] 0.0)
        removed (* removal-rate-nm-min time-min)
        residual (max 0.0 (- film removed))
        dishing (* 0.5 (Math/abs (- removed (* 0.95 film))))]
    {:measured {:removed-nm (r2 removed)
                :residual-nm (r2 residual)
                :dishing-nm (r2 dishing)
                :uniformity-pct (r2 (max 90.0 (- 99.0 (* 0.05 dishing))))}
     :defects (r4 (+ 0.02 (* 0.03 dishing)))
     :pass (<= dishing 8.0)
     :state-delta {:film-nm residual}}))

(defn- step-metrology
  "In-line SPC gate: z = (D_total - μ)/σ over the cumulative defect density.
  Non-actuating measurement step — never adds defects."
  [state {:keys [mu sigma] :or {mu 0.15 sigma 0.08}}]
  (let [d (get-in state [:wafer :defect-density] 0.0)
        z (/ (- d mu) sigma)]
    {:measured {:cum-defect-density-cm2 (r4 d)
                :spc-z (r2 z)
                :in-control (<= z 3.0)}
     :defects 0.0
     :pass (<= z 3.0)}))

(defn- step-test
  "Wafer sort / yield binning. Poisson yield Y = exp(-D·A) over the cumulative
  defect density D (cm^-2) and die area A (cm²); good dies = floor(N·Y)."
  [state {:keys [die-area-cm2 die-per-wafer wafers] :or {die-area-cm2 0.25}}]
  (let [d (get-in state [:wafer :defect-density] 0.0)
        y (Math/exp (- (* d die-area-cm2)))
        total (* die-per-wafer wafers)
        good (long (Math/floor (* total y)))]
    {:measured {:yield (r4 y)
                :die-per-wafer die-per-wafer
                :wafers wafers
                :total-die total
                :good-die good}
     :defects 0.0
     :pass (>= y 0.50)
     :state-delta {:good-die good :yield y}}))

(defn- step-packaging
  "Assembly + final test: bond/encapsulate the known-good die into packages.
  A small assembly loss applies on top of wafer-sort yield."
  [state {:keys [assembly-yield] :or {assembly-yield 0.985}}]
  (let [kgd (get-in state [:wafer :good-die] 0)
        packaged (long (Math/floor (* kgd assembly-yield)))]
    {:measured {:known-good-die kgd
                :assembly-yield assembly-yield
                :packaged-units packaged}
     :defects 0.0
     :pass (pos? packaged)
     :state-delta {:packaged-units packaged}}))

(def ^:private step-impl
  {"litho" step-litho
   "deposition" step-deposition
   "etch" step-etch
   "implant" step-implant
   "cmp" step-cmp
   "metrology" step-metrology
   "test" step-test
   "packaging" step-packaging})

;; ── force-review (G1) ───────────────────────────────────────────────────────

(defn assert-attested
  "G1 — refuse a run whose route contains a dual-use step unless the run carries a
  non-blank `:silen-force-attest`. Returns the route on success; throws otherwise."
  [route attest]
  (let [dual (filter force-review-required? route)]
    (when (and (seq dual) (str/blank? (str attest)))
      (throw (ex-info (str "silen-force-attest required for dual-use steps "
                           (vec dual) " (Charter Rider §2(a)(c), G1)")
                      {:error :force-review :steps (vec dual)})))
    route))

;; ── the flow ────────────────────────────────────────────────────────────────

(defn- apply-step
  [state step recipe]
  (let [f (or (step-impl step)
              (throw (ex-info (str "unknown fab step: " step) {:error :route})))
        out (f state (get recipe step {}))
        delta (:state-delta out)
        wafer (-> (:wafer state)
                  (update :defect-density (fnil + 0.0) (:defects out))
                  (merge delta))]
    (-> state
        (assoc :wafer wafer)
        (update :steps conj
                {:step step
                 :measured (:measured out)
                 :added-defects (:defects out)
                 :pass (:pass out)
                 :dual-use (force-review-required? step)}))))

(defn run-lot
  "DRY-RUN simulate a wafer-lot through `route` under `recipe`.

  `lot` = {:lot-id str :wafers int :die-per-wafer int :die-area-cm2 num}.
  Test/packaging steps read wafer/die counts from `lot`, so they are merged into
  the recipe automatically. Returns the completed lot record:

      {:lot-id … :route … :steps [step-record …] :defect-density …
       :good-die … :packaged-units … :yield … :all-pass bool}

  G11: pure simulation. Does not, and cannot, actuate equipment."
  [lot route recipe & {:keys [silen-force-attest]}]
  (assert-attested route silen-force-attest)
  (let [recipe (-> recipe
                   (update "test" merge (select-keys lot [:die-area-cm2 :die-per-wafer]))
                   (assoc-in ["test" :wafers] (:wafers lot)))
        init {:lot-id (:lot-id lot)
              :route (vec route)
              :wafer {:defect-density 0.0}
              :steps []}
        done (reduce (fn [st step] (apply-step st step recipe)) init route)
        w (:wafer done)]
    {:lot-id (:lot-id lot)
     :route (vec route)
     :steps (:steps done)
     :defect-density (r4 (:defect-density w))
     :good-die (get w :good-die 0)
     :packaged-units (get w :packaged-units 0)
     :yield (r4 (get w :yield 0.0))
     :all-pass (every? :pass (:steps done))}))

;; ── outward gate (G11) ──────────────────────────────────────────────────────

(defn dispatch-equipment!
  "G11 — real fab actuation is OUT OF SCOPE at R0 and Council-gated. This exists
  only to make the boundary explicit and test-enforceable: it always refuses."
  [_lot-record]
  (throw (ex-info (str "real fab-equipment dispatch is Council-gated (G11, "
                       "ADR-2605242545 Phase 4); silicon R0 is design/simulation only")
                  {:error :council-gate})))

;; ── reference recipe (a ternary-PE tile lot) ────────────────────────────────

(def reference-recipe
  "A representative recipe for an iwakura ternary-PE test tile (illustrative)."
  {"litho"      {:target-cd-nm 90.0 :dose 30.0 :focus-nm 20.0}
   "deposition" {:material "SiO2" :rate-nm-min 4.0 :time-min 45.0}
   "etch"       {:rate-nm-min 6.0 :time-min 28.0 :selectivity 8.2}
   "implant"    {:species "B+" :energy-kev 20.0 :dose-cm2 1.0e13}
   ;; CMP planarizes the thin residual film left by etch; gentle removal so the
   ;; baseline lot is healthy (dishing small, no over-polish).
   "cmp"        {:removal-rate-nm-min 3.0 :time-min 3.6}
   "metrology"  {:mu 0.15 :sigma 0.08}
   "test"       {}
   "packaging"  {:assembly-yield 0.985}})

(def reference-lot
  {:lot-id "LOT-IWAKURA-PE-0001" :wafers 25 :die-per-wafer 800 :die-area-cm2 0.25})
