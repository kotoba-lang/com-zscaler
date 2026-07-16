#!/usr/bin/env bb
;; iriai 入会 — physical-simulation DIGITAL TWIN (degradation + operating-margin), clj-native.
(ns iriai.methods.twin
  "iriai 入会 — the physical-simulation DIGITAL TWIN (ADR-2606280900).

  The condition layer beneath maintenance: each DEPLOYED asset (transformer / water
  main / gas main / fibre span / road pavement+bridge) carries a physical state that
  DEGRADES over time. The twin advances that state with REAL engineering models and
  reports, per asset, a CONDITION index (0..1) + REMAINING-USEFUL-LIFE (RUL, years) +
  an OPERATING MARGIN (the short-timescale 'does it hold load within the safe
  envelope' check) + a structural SAFETY flag.

  This is the same digital-twin discipline the infra-robotics substrate proved on the
  electric microgrid (ADR-2606091800/2606101430: a clj/cljc :representative twin whose
  WASM control artifact runs device-in-the-loop under Wasmtime, the certified PLC being
  the field SSoT). Here the twin runs AHEAD of reality so maintenance is condition-based
  + predictive (project forward), never a fixed calendar guess. SIMULATION ONLY (G5) —
  the twin computes; it never energizes/flows/ignites/opens a road. Deterministic: the
  caller supplies age/Δt (no wall clock, no Math/random) → resume-safe.

  Degradation models (disclosed, per lifeline):
    :electric  IEEE C57.91 transformer thermal aging — load → hot-spot θh → FAA = exp(15000/383 − 15000/(θh+273)) → loss-of-life
    :water     Hazen-Williams C-factor decline C(t)=C0−k·t (roughness growth) → hydraulic capacity
    :gas       wall-thickness corrosion w(t)=w0−cr·t → leak-probability (safety floor)
    :telecom   fibre attenuation creep α(t)=α0+β·t → link-budget margin
    :road      pavement PCI(t)=PCI0−a·t^b deterioration (+ bridge load-rating)"
  (:require [clojure.string :as str]))

;; ── disclosed model constants ──────────────────────────────────────────────────
(def ^:private dtheta-or 55.0)   ; rated top-oil rise over ambient (°C), IEEE C57.91
(def ^:private dtheta-hs 25.0)   ; rated hot-spot rise over top-oil (°C)
(def ^:private loss-ratio 8.0)   ; R = load-loss / no-load-loss
(def ^:private theta-safe 140.0) ; insulation safe ceiling (°C)
(def ^:private hw-c-min 60.0)    ; Hazen-Williams floor (unusable below)
(def ^:private gas-leak-thresh 0.7)
(def ^:private pci-fail 25.0)    ; PCI below = structural risk
(def ^:private pci-renew 40.0)   ; PCI renewal trigger

(defn- clamp01 [x] (max 0.0 (min 1.0 (double x))))
(defn- pos [x] (max 0.0 (double x)))

;; ── per-lifeline physics ───────────────────────────────────────────────────────
(defn- electric-twin
  "Transformer thermal aging (IEEE C57.91, simplified). Drivers: :load-factor (pu),
  :ambient-c. condition = 1 − loss-of-life; RUL from current aging-acceleration FAA."
  [{:keys [age-years design-life load-factor ambient-c]}]
  (let [k (double (or load-factor 0.7))
        amb (double (or ambient-c 30.0))
        dl (double (or design-life 30.0))
        top-oil (* dtheta-or (Math/pow (/ (+ 1.0 (* loss-ratio k k)) (+ 1.0 loss-ratio)) 0.8))
        hs (* dtheta-hs (Math/pow k 1.6))
        theta-h (+ amb top-oil hs)
        faa (Math/exp (- (/ 15000.0 383.0) (/ 15000.0 (+ theta-h 273.0))))
        equiv-aging (* faa (double age-years))
        lol (/ equiv-aging dl)
        margin (/ (- theta-safe theta-h) theta-safe)]
    {:condition (clamp01 (- 1.0 lol))
     :rul (- (/ dl faa) (double age-years))
     :op-margin margin
     :safety (if (or (> theta-h theta-safe) (>= lol 1.0)) :unsafe :ok)
     :driver (str "θh=" (Math/round theta-h) "°C FAA=" (/ (Math/round (* faa 100.0)) 100.0))}))

(defn- water-twin
  "Pipe Hazen-Williams roughness decline. Drivers: :c0, :c-decline-per-yr. condition from
  C vs the unusable floor; op-margin = hydraulic carrying-capacity headroom."
  [{:keys [age-years c0 c-decline-per-yr]}]
  (let [c0 (double (or c0 130.0))
        k (double (or c-decline-per-yr 1.2))
        c (- c0 (* k (double age-years)))
        span (- c0 hw-c-min)]
    {:condition (clamp01 (/ (- c hw-c-min) span))
     :rul (- (/ span k) (double age-years))
     :op-margin (- (/ c c0) 0.6)            ; need ≥60% of design capacity
     :safety (if (<= c hw-c-min) :unsafe :ok)
     :driver (str "C=" (Math/round c) "/" (Math/round c0))}))

(defn- gas-twin
  "Gas-main corrosion → leak probability. Drivers: :wall-mm0, :corrosion-mm-per-yr,
  :wall-min-mm. SAFETY FLOOR: leak-prob > 0.7 (or wall ≤ min) → :unsafe."
  [{:keys [age-years wall-mm0 corrosion-mm-per-yr wall-min-mm]}]
  (let [w0 (double (or wall-mm0 8.0))
        cr (double (or corrosion-mm-per-yr 0.08))
        wmin (double (or wall-min-mm 3.0))
        w (- w0 (* cr (double age-years)))
        span (- w0 wmin)
        cond (clamp01 (/ (- w wmin) span))
        leak-prob (clamp01 (- 1.0 cond))]
    {:condition cond
     :rul (- (/ span cr) (double age-years))
     :op-margin cond
     :safety (if (or (<= w wmin) (> leak-prob gas-leak-thresh)) :unsafe :ok)
     :driver (str "wall=" (/ (Math/round (* w 10.0)) 10.0) "mm leak-p=" (/ (Math/round (* leak-prob 100.0)) 100.0))}))

(defn- telecom-twin
  "Fibre attenuation creep vs link budget. Drivers: :atten0-db, :atten-creep-db-per-yr,
  :budget-db. SAFETY: budget exhausted (margin ≤ 0) → :unsafe (service loss)."
  [{:keys [age-years atten0-db atten-creep-db-per-yr budget-db]}]
  (let [a0 (double (or atten0-db 6.0))
        beta (double (or atten-creep-db-per-yr 0.15))
        budget (double (or budget-db 28.0))
        atten (+ a0 (* beta (double age-years)))
        margin (- budget atten)
        usable (- budget a0)]
    {:condition (clamp01 (/ margin usable))
     :rul (- (/ usable beta) (double age-years))
     :op-margin (/ margin budget)
     :safety (if (<= margin 0.0) :unsafe :ok)
     :driver (str "α=" (/ (Math/round (* atten 10.0)) 10.0) "dB margin=" (/ (Math/round (* margin 10.0)) 10.0) "dB")}))

(defn- road-twin
  "Pavement PCI deterioration PCI(t)=PCI0−a·t^b (+ bridge :load-rating). Drivers:
  :pci0, :pci-a, :pci-b. SAFETY: PCI < 25 (or load-rating < 1.0) → :unsafe."
  [{:keys [age-years pci0 pci-a pci-b load-rating]}]
  (let [p0 (double (or pci0 100.0))
        a (double (or pci-a 0.6))
        b (double (or pci-b 2.2))
        age (double age-years)
        pci (- p0 (* a (Math/pow age b)))
        lr (double (or load-rating 1.5))
        ;; age at which PCI hits the renewal trigger
        age-at-renew (Math/pow (/ (- p0 pci-renew) a) (/ 1.0 b))]
    {:condition (clamp01 (/ pci 100.0))
     :rul (- age-at-renew age)
     :op-margin (/ (- pci pci-fail) (- 100.0 pci-fail))
     :safety (if (or (< pci pci-fail) (< lr 1.0)) :unsafe :ok)
     :driver (str "PCI=" (Math/round pci) " load-rating=" lr)}))

(defn assess-asset
  "Run the twin for one deployed asset → {:id :lifeline :region :name :age :design-life
  :condition :rul :op-margin :safety :driver}. Dispatches on :lifeline."
  [a]
  (let [phys (case (:lifeline a)
               :electric (electric-twin a)
               :water    (water-twin a)
               :gas      (gas-twin a)
               :telecom  (telecom-twin a)
               :road     (road-twin a)
               {:condition 0.5 :rul 0.0 :op-margin 0.0 :safety :ok :driver "?"})]
    (merge {:id (:id a) :lifeline (:lifeline a) :region (:region a) :name (:name a)
            :age (:age-years a) :design-life (:design-life a)}
           (update phys :rul #(/ (Math/round (* (double %) 10.0)) 10.0))
           {:condition (/ (Math/round (* (double (:condition phys)) 1000.0)) 1000.0)
            :op-margin (/ (Math/round (* (double (:op-margin phys)) 1000.0)) 1000.0)})))

(defn project
  "Predictive: the asset's twin state Δ years into the future (advance :age-years).
  This is the run-ahead that makes maintenance PREVENTIVE — see a failure before it
  happens. Pure simulation (G5)."
  [a delta-years]
  (assess-asset (update a :age-years #(+ (double (or % 0)) (double delta-years)))))

(defn assess
  "Twin over all deployed assets. Returns rows + a condition/safety summary."
  [assets]
  (let [rows (mapv assess-asset assets)]
    {"assets" rows
     "count" (count rows)
     "unsafe" (count (filter #(= :unsafe (:safety %)) rows))
     "mean-condition" (let [n (count rows)]
                        (if (pos? n) (/ (Math/round (* (/ (reduce + 0.0 (map :condition rows)) n) 1000.0)) 1000.0) 1.0))
     "by-lifeline" (into {} (map (fn [[lf rs]] [lf (count rs)]) (group-by :lifeline rows)))}))

;; ── datom emission (append-only EAVT; flagged; SIMULATION ONLY) ────────────────
(defn- add [e a v] [":db/add" e a v])

(defn datoms
  "Append-only EAVT datoms for the twin condition snapshot. SIMULATION ONLY — NO
  :iriai/actuate / :iriai.twin/energize attribute (G5). No per-person data (G1)."
  [{:strs [assets]}]
  (vec
   (mapcat
    (fn [r]
      (let [e (str "iriai-asset:" (:id r))]
        [(add e ":iriai.asset/lifeline" (str (:lifeline r)))
         (add e ":iriai.asset/age-years" (double (or (:age r) 0)))
         (add e ":iriai.twin/condition" (double (:condition r)))
         (add e ":iriai.twin/rul-years" (double (:rul r)))
         (add e ":iriai.twin/op-margin" (double (:op-margin r)))
         (add e ":iriai.twin/safety" (str (:safety r)))
         (add e ":iriai/sourcing" ":synthetic")
         (add e ":iriai/derived" true)]))
    assets)))

(defn render-report [assessment]
  (let [rows (->> (get assessment "assets") (sort-by :condition))]
    (str
     "# iriai 入会 — physical-simulation DIGITAL TWIN (asset condition)\n\n"
     "Per DEPLOYED asset: a REAL degradation model (transformer IEEE C57.91 thermal aging · "
     "pipe Hazen-Williams decline · gas-main corrosion→leak · fibre attenuation creep · "
     "pavement PCI decay) → condition (0..1) + remaining-useful-life (RUL, yrs) + operating "
     "margin + structural safety. **SIMULATION ONLY (G5)** — the twin computes; it never "
     "energizes/flows/ignites/opens a road. Run-ahead `project` makes maintenance PREVENTIVE.\n\n"
     "**" (get assessment "count") "** assets · mean condition **" (get assessment "mean-condition")
     "** · **" (get assessment "unsafe") "** unsafe.\n\n"
     "| asset | lifeline | age | condition | RUL(yr) | op-margin | safety | driver |\n"
     "|---|---|---|---|---|---|---|---|\n"
     (str/join "\n"
               (for [r rows]
                 (str "| " (:name r) " | " (name (:lifeline r)) " | " (:age r)
                      " | " (:condition r) " | " (:rul r) " | " (:op-margin r)
                      " | " (name (:safety r)) " | " (:driver r) " |")))
     "\n\n_Twin feeds iriai.maintain (the lifecycle gate). A condition MAP, never a person record._\n")))

;; ── CLI (bb) ───────────────────────────────────────────────────────────────────
#?(:clj
   (defn -main [& args]
     (let [seed (or (first args) "20-actors/iriai/kotoba/seed.edn")
           assets (vec (filter #(= (:type %) :asset) (clojure.edn/read-string (slurp seed))))]
       (println (render-report (assess assets)))
       (println (str "-- " (count assets) " assets simulated --")))))

#?(:clj
   (when (= *file* (System/getProperty "babashka.file"))
     (apply -main *command-line-args*)))
