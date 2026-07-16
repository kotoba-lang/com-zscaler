(ns fuchi.methods.allocate
  "allocate.cljc — 扶持 (fuchi) maintainer sustenance allocation. 1:1 Clojure port of
  `methods/allocate.py` (ADR-2606052300).

  THE HEART of the actor and the charter-clean inverse of an investment fund's cap-table.
  For a cohort of covenant-bound maintainers (信者) it computes each one's tenure weight
  (the Displacement-Dividend curve), priority share, rank, and in-kind sustenance floor.

  The invariants that make this charter-clean and NOT an investment vehicle:
    * cash-usd-micros is structurally 0 for every allocation (cash≡0, N1);
    * the instrument is one of {in-kind-grant, sustenance, tooling-access, compute-access} —
      equity / debt / convertible / revenue-share / carry / dividend / exit are an ex-info,
      never an allocation (G1);
    * the maintainer's work product is commons (owns-payoff is structurally false, G5).

  House style: Python ':…' keyword strings stay strings; pure fns; deterministic; round()
  is HALF_EVEN via exact BigDecimal. Portable .cljc."
  (:require [clojure.string :as str]))

(def TENURE-CAP-YEARS 40.0)
(def HAZARD-MIN 1.0)
(def HAZARD-MAX 2.0)
(def HORIZON-YEARS 5.0)

;; G1 — sustenance instruments only (mirror of the ontology :alloc/instrument :db/allowed).
(def ALLOWED-INSTRUMENTS ["in-kind-grant" "sustenance" "tooling-access" "compute-access"])
;; The investment-fund vocabulary that is UNREPRESENTABLE here (defensive denylist).
(def FORBIDDEN-INSTRUMENTS
  #{"equity" "debt" "convertible" "revenue-share" "profit-claim"
    "carry" "dividend" "loan" "interest" "warrant" "option" "exit"})

(defn- ->str [v] (str (or v "")))

(defn- lstrip-colon [s]
  (let [s (str s)] (if (str/starts-with? s ":") (subs s 1) s)))

(defn assert-instrument
  "G1 INVARIANT — only a sustenance instrument is allocatable. Anything resembling an
  investment/return vehicle is an ex-info (not an investment fund; Charter-Rider §2(b))."
  [instrument]
  (let [instr (-> (->str instrument) lstrip-colon str/lower-case)]
    (cond
      (contains? FORBIDDEN-INSTRUMENTS instr)
      (throw (ex-info (str "G1: instrument '" instr "' is an investment/return vehicle — "
                           "UNREPRESENTABLE (扶持 is sustenance, not a fund; Charter-Rider §2(b))")
                      {:instrument instr}))
      (not (some #{instr} ALLOWED-INSTRUMENTS))
      (throw (ex-info (str "G1: instrument '" instr "' not in " (vec ALLOWED-INSTRUMENTS))
                      {:instrument instr}))
      :else instr)))

;; ── round() — Python's banker's rounding (HALF_EVEN), exact via BigDecimal ──
(defn round-half-even
  "Python round(x) on a double: round to nearest integer, ties to even. Returns a long."
  [x]
  #?(:clj (-> (BigDecimal. (double x)) (.setScale 0 java.math.RoundingMode/HALF_EVEN) .longValueExact)
     :cljs (let [f (js/Math.floor x) d (- x f)]
             (cond (< d 0.5) f
                   (> d 0.5) (inc f)
                   :else (if (even? f) f (inc f))))))

;; ── Maintainer / Allocation records ────────────────────────────────────────
(defn make-maintainer
  "Maintainer dataclass equivalent (keyword-keyed map). covenant default 'vowed'."
  [{:keys [did tenure-months hazard-permille maintains
           prior-imputed-usd-micros-yr covenant owns-payoff]
    :or {tenure-months 0 hazard-permille 1000 maintains []
         prior-imputed-usd-micros-yr 0 covenant "vowed" owns-payoff false}}]
  {:did did :tenure-months (long tenure-months) :hazard-permille (long hazard-permille)
   :maintains (vec maintains) :prior-imputed-usd-micros-yr (long prior-imputed-usd-micros-yr)
   :covenant covenant :owns-payoff (boolean owns-payoff)})

(defn make-allocation
  "Allocation dataclass equivalent. __post_init__ structural proofs at construction:
  cash≡0, no-server-key, valid instrument."
  [{:keys [maintainer-did instrument weight share priority-rank floor-usd-micros-yr
           cash-usd-micros server-held-key]
    :or {cash-usd-micros 0 server-held-key false}}]
  (when (not= cash-usd-micros 0)
    (throw (ex-info "cash≡0 INVARIANT (G2/N4): 扶持 never disburses cash" {})))
  (when server-held-key
    (throw (ex-info "no-server-key INVARIANT (G9): allocation is member/Council-signed" {})))
  (assert-instrument instrument)
  {:maintainer-did maintainer-did :instrument instrument :weight weight :share share
   :priority-rank priority-rank :floor-usd-micros-yr floor-usd-micros-yr
   :cash-usd-micros 0 :server-held-key false})

(defn- capped-tenure-years [tenure-months]
  (min (/ (double tenure-months) 12.0) TENURE-CAP-YEARS))

(defn- hazard [hazard-permille]
  (let [h (/ (double hazard-permille) 1000.0)]
    (when-not (<= HAZARD-MIN h HAZARD-MAX)
      (throw (ex-info (str "hazard out of [1.0,2.0]: " h) {:hazard h})))
    h))

(defn tenure-weight
  "w = ln(1 + min(tenure_years, cap)) * hazard. Log compresses the gradient (40y ≈ 2× 5y)."
  [m]
  (* (Math/log1p (capped-tenure-years (:tenure-months m)))
     (hazard (:hazard-permille m))))

(defn floor-decay
  "decay(t) = clamp(1 - t/HORIZON, 0, 1); the floor tapers over 5 years."
  [elapsed-months]
  (let [t (/ (double elapsed-months) 12.0)]
    (max 0.0 (min 1.0 (- 1.0 (/ t HORIZON-YEARS))))))

(defn- round6 [x]
  ;; Python round(x, 6) — HALF_EVEN at 6 decimal places, returned as a double.
  #?(:clj (-> (BigDecimal. (double x)) (.setScale 6 java.math.RoundingMode/HALF_EVEN) .doubleValue)
     :cljs (/ (round-half-even (* x 1e6)) 1e6)))

(defn allocate
  "Allocate tenure-weighted in-kind sustenance over a maintainer cohort.

  Only `vowed` maintainers join the share pool (covenant gate, G4); `outreach` get a minimal
  floor (share 0). cash structurally 0. Raises if any maintainer owns-payoff (G5) or if
  instrument is an investment vehicle (G1)."
  ([cohort stage-ceiling-usd-micros-yr]
   (allocate cohort stage-ceiling-usd-micros-yr 0 "sustenance"))
  ([cohort stage-ceiling-usd-micros-yr elapsed-months instrument]
   (let [instr (assert-instrument instrument)]
     (when (some :owns-payoff cohort)
       (throw (ex-info "G5: a maintainer cannot own the payoff — work product is commons" {})))
     (let [vowed (filterv #(= (:covenant %) "vowed") cohort)
           total-w (reduce + 0.0 (map tenure-weight vowed))
           decay (floor-decay elapsed-months)
           ;; sorted(vowed, key=tenure_weight, reverse=True) — Python stable sort
           ranked (sort-by tenure-weight (fn [a b] (compare b a)) vowed)
           rank-of (into {} (map-indexed (fn [i m] [(:did m) (inc i)]) ranked))
           out (mapv
                (fn [m]
                  (if (= (:covenant m) "vowed")
                    (let [w (tenure-weight m)
                          share (if (> total-w 0) (/ w total-w) 0.0)
                          rank (get rank-of (:did m))
                          floor0 (min (:prior-imputed-usd-micros-yr m) stage-ceiling-usd-micros-yr)
                          floor (round-half-even (* floor0 decay))]
                      (make-allocation
                       {:maintainer-did (:did m) :instrument instr
                        :weight (round6 w) :share (round6 share)
                        :priority-rank rank :floor-usd-micros-yr floor
                        :cash-usd-micros 0 :server-held-key false}))
                    ;; outreach — minimal floor, no share (pre-vow)
                    (let [floor (round-half-even
                                 (* (min (:prior-imputed-usd-micros-yr m) stage-ceiling-usd-micros-yr)
                                    decay 0.25))]
                      (make-allocation
                       {:maintainer-did (:did m) :instrument instr
                        :weight 0.0 :share 0.0
                        :priority-rank (inc (count vowed)) :floor-usd-micros-yr floor
                        :cash-usd-micros 0 :server-held-key false}))))
                cohort)]
       ;; out.sort(key=lambda a: (a.priority_rank, -a.weight)) — Python stable sort
       (vec (sort-by (juxt :priority-rank (comp - :weight)) out))))))

(defn cohort-from-seed
  "Build a cohort from seed :maintainer/* maps (edn keyword-string-keyed)."
  [records]
  (let [kw (fn [v] (-> (->str v) lstrip-colon (str/split #"/") last str/lower-case))]
    (mapv
     (fn [r]
       (make-maintainer
        {:did (get r ":maintainer/did" "?")
         :tenure-months (long (get r ":maintainer/tenure-months" 0))
         :hazard-permille (long (get r ":maintainer/hazard-permille" 1000))
         :maintains (vec (or (get r ":maintainer/maintains") []))
         :prior-imputed-usd-micros-yr (long (get r ":maintainer/prior-imputed-usd-micros-yr" 0))
         :covenant (kw (get r ":maintainer/covenant" ":vowed"))
         :owns-payoff (boolean (get r ":maintainer/owns-payoff" false))}))
     records)))
