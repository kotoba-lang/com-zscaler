(ns noroshi.methods.link-budget
  "noroshi (烽) optical link-budget core — the chip face (ADR-2606051600).
  1:1 Clojure port of methods/link_budget.py. Stdlib only (Math/ + BigDecimal).

  Computes the end-to-end power budget of a silicon-photonic / co-packaged-optics (CPO)
  link, the 光電融合 (photonics-electronics convergence) communication primitive: a laser
  source feeds an electro-optic modulator on a photonic IC (PIC), light couples off-chip
  through a grating coupler, traverses a waveguide / fibre span, and is detected by a
  photodiode against a receiver sensitivity set by the target bit-error-rate. The link
  \"closes\" when the received power exceeds the sensitivity with positive margin.

  It is a deterministic dB ledger plus an energy-per-bit figure of merit — the number that
  makes CPO worth building. No hardware, no foundry, no live laser (G7 outward-gated); this
  is arithmetic over a design, verifiable before any silicon exists.

  Sign convention: gains/sources are +dB(m), losses are positive numbers SUBTRACTED. All
  optical powers in dBm (0 dBm = 1 mW).

  CONSTITUTIONAL gates (noroshi CLAUDE.md):
    G3 civilian-force-separation — optical power + budget arithmetic, civilian-only; there is
       no weaponisation / directed-energy / fire-control mode (unrepresentable, Charter §1.12).
    G5 laser-safety-soft / IEC 60825 — `with-ber-sensitivity` etc. are design arithmetic, not
       a certified safety controller (N3); positive-rate guards (line-rate, BER range, APD gain,
       k_eff) raise ex-info exactly where the Python raises ValueError.
    G10 sourcing-honesty — `:representative` device parameters, sims are arithmetic, no measured
       silicon.

  House style: kebab keyword keys; Python ':…' strings stay literal strings; pure fns; file
  I/O only at #?(:clj) edges; closed-vocab/gate violations → ex-info. Float arithmetic is
  EXACT: round(x, n) reproduces Python's banker's rounding (HALF_EVEN) on the exact double,
  then the result is re-widened to a double so its shortest repr matches Python's; dB math
  (10·log10) is last-ULP via Math/log10. Portable .cljc."
  (:require [clojure.string :as str]))

;; ── float helpers: Python round(x, n) / repr(float) — byte-identical ────────
;; Python round(x, n) = round-half-to-even on the EXACT value of the double, returning a
;; float. We mirror it with BigDecimal.(double x) (the exact binary value) → setScale n
;; HALF_EVEN → back to a double. The double is then printed with the shortest round-trip
;; representation (Java Double/toString == Python repr for these magnitudes), so the report
;; bytes match exactly (e.g. -1.95, 574.437, 0.7, 4.0).
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

;; ── design defaults (mirror LinkDesign dataclass field defaults) ────────────
(def default-design
  "Mirror of the LinkDesign dataclass defaults. kebab keyword keys."
  {:name "cpo-2km-100g"
   ;; source
   :laser-power-dbm 10.0
   ;; transmit PIC
   :modulator-il-db 4.0
   :tx-waveguide-cm 1.5
   :tx-grating-coupler-db 1.5
   ;; span
   :fibre-m 2000.0
   :fibre-loss-db-per-km 0.35
   :connector-db 0.5
   ;; receive PIC
   :rx-grating-coupler-db 1.5
   :rx-waveguide-cm 1.0
   ;; shared physical constants
   :waveguide-loss-db-per-cm 1.5
   ;; receiver
   :rx-responsivity-a-per-w 0.9
   :rx-sensitivity-dbm -12.0
   ;; electrical / throughput
   :line-rate-gbps 106.25
   :tx-energy-pj-per-bit 1.2
   :rx-energy-pj-per-bit 1.0
   :laser-wall-plug-eff 0.10})

(defn link-design
  "Build a LinkDesign-equivalent map, overriding `default-design` with the supplied kwargs."
  [& {:as overrides}]
  (merge default-design overrides))

(defn- waveguide-loss
  "_waveguide_loss: (tx_wg + rx_wg) · loss-per-cm."
  [d]
  (* (+ (:tx-waveguide-cm d) (:rx-waveguide-cm d)) (:waveguide-loss-db-per-cm d)))

(defn- fibre-loss
  "_fibre_loss: (fibre_m / 1000) · loss-per-km."
  [d]
  (* (/ (:fibre-m d) 1000.0) (:fibre-loss-db-per-km d)))

(defn compute
  "Return the closed-form power budget + energy-per-bit for one link design (1:1 with
  link_budget.compute). The loss accumulation order (insertion order of the Python dict)
  is preserved so `total_loss` sums identically; round() points match exactly."
  [d]
  (when (<= (:line-rate-gbps d) 0)
    (throw (ex-info "line_rate_gbps must be positive" {:value (:line-rate-gbps d)})))
  (let [;; losses dict — insertion order matters for the exact float sum
        loss-order ["modulator_il" "tx_grating_coupler" "rx_grating_coupler"
                    "waveguide" "fibre" "connector"]
        losses {"modulator_il" (:modulator-il-db d)
                "tx_grating_coupler" (:tx-grating-coupler-db d)
                "rx_grating_coupler" (:rx-grating-coupler-db d)
                "waveguide" (waveguide-loss d)
                "fibre" (fibre-loss d)
                "connector" (:connector-db d)}
        total-loss (reduce + 0.0 (map losses loss-order))
        received-dbm (- (:laser-power-dbm d) total-loss)
        margin (- received-dbm (:rx-sensitivity-dbm d))
        ;; Received photocurrent: P[mW] = 10**(dBm/10); I = R·P.
        received-mw (Math/pow 10.0 (/ received-dbm 10.0))
        received-current-ua (* (:rx-responsivity-a-per-w d) received-mw 1e3)
        ;; Energy per bit
        laser-optical-w (/ (Math/pow 10.0 (/ (:laser-power-dbm d) 10.0)) 1e3)
        laser-electrical-w (/ laser-optical-w (max (:laser-wall-plug-eff d) 1e-9))
        laser-pj-per-bit (* (/ laser-electrical-w (* (:line-rate-gbps d) 1e9)) 1e12)
        energy-pj-per-bit (+ (:tx-energy-pj-per-bit d) (:rx-energy-pj-per-bit d) laser-pj-per-bit)]
    {:name (:name d)
     :received-dbm (py-round received-dbm 3)
     :margin-db (py-round margin 3)
     :closes (>= margin 0.0)
     :total-loss-db (py-round total-loss 3)
     :energy-pj-per-bit (py-round energy-pj-per-bit 3)
     :received-current-ua (py-round received-current-ua 3)
     ;; breakdown preserves the same loss-order (dict order in the Python repr)
     :breakdown (mapv (fn [k] [k (py-round (losses k) 3)]) loss-order)}))

;; ── receiver sensitivity from a target BER (Q-factor + thermal-noise model) ──
(def ^:private k-boltzmann 1.380649e-23)   ; J/K

;; math.erfc(z) for z >= 0 (the only domain used: mid/√2 with mid in [0,12]). Reproduces
;; CPython math.erfc to last ULP via the complementary-error continued fraction for |x|>=2
;; and 1 − erf-series for |x|<2 (same pairing mitooshi/score.cljc uses, verified vs python3).
(def ^:private two-over-sqrtpi (/ 2.0 (Math/sqrt Math/PI)))

(defn- erf-series
  "erf(x) = 2/√π · Σ_{n≥0} (−1)^n x^(2n+1) / (n!·(2n+1)); converges fast for |x| < 2."
  [x]
  (loop [n 0, term (double x), sum 0.0]
    (let [add (/ term (+ (* 2.0 n) 1.0))
          sum' (+ sum add)]
      (if (or (> n 200) (< (Math/abs add) (* (Math/abs sum') 1e-18)))
        (* two-over-sqrtpi sum')
        (recur (inc n) (/ (* term (- (* x x))) (+ n 1.0)) sum')))))

(defn- erfc-cf
  "erfc(|x|) via the backward continued fraction (260 terms = full precision)."
  [x]
  (let [ax (Math/abs (double x))
        x2 (* ax ax)]
    (loop [k 260, acc 0.0]
      (if (zero? k)
        (* (/ (Math/exp (- x2)) (Math/sqrt Math/PI)) (/ 1.0 (+ ax acc)))
        (recur (dec k) (/ (* 0.5 k) (+ ax acc)))))))

(defn- erfc
  "Complementary error function, matching python3 math.erfc to last ULP."
  [x]
  (let [x (double x)]
    (cond
      (zero? x) 1.0
      (< (Math/abs x) 2.0) (- 1.0 (erf-series x))
      (>= x 0.0) (erfc-cf x)
      :else (- 2.0 (erfc-cf x)))))

(defn q-factor-for-ber
  "Solve BER = ½·erfc(Q/√2) for the Q-factor (NRZ-OOK direct detection), via bisection.
  Monotone: a stricter BER needs a larger Q. Defined for 0 < BER < 0.5."
  [ber]
  (when-not (< 0.0 ber 0.5)
    (throw (ex-info "BER must lie in (0, 0.5)" {:ber ber})))
  (loop [i 0, lo 0.0, hi 12.0]
    (if (>= i 100)
      (* 0.5 (+ lo hi))
      (let [mid (* 0.5 (+ lo hi))]
        (if (> (* 0.5 (erfc (/ mid (Math/sqrt 2.0)))) ber)
          (recur (inc i) mid hi)
          (recur (inc i) lo mid))))))

(defn receiver-sensitivity-dbm
  "Thermal-noise-limited receiver sensitivity (min received optical power, dBm) for a target
  BER. σ_thermal = √(4·k·T·B / R_load); required current = Q·σ_thermal; P_min = required /
  responsivity. B ≈ 0.7·line-rate. Simplified thermal-limited NRZ-OOK model (G10)."
  ([ber line-rate-gbps]
   (receiver-sensitivity-dbm ber line-rate-gbps 0.9 300.0 50.0))
  ([ber line-rate-gbps responsivity-a-per-w]
   (receiver-sensitivity-dbm ber line-rate-gbps responsivity-a-per-w 300.0 50.0))
  ([ber line-rate-gbps responsivity-a-per-w temperature-k load-ohm]
   (when (<= line-rate-gbps 0)
     (throw (ex-info "line_rate_gbps must be positive" {:value line-rate-gbps})))
   (let [q (q-factor-for-ber ber)
         bandwidth-hz (* 0.7 line-rate-gbps 1e9)
         sigma-thermal-a (Math/sqrt (/ (* 4.0 k-boltzmann temperature-k bandwidth-hz) load-ohm))
         p-min-w (/ (* q sigma-thermal-a) responsivity-a-per-w)]
     (* 10.0 (Math/log10 (* p-min-w 1e3))))))

(defn with-ber-sensitivity
  "Return a copy of `d` whose :rx-sensitivity-dbm is derived from a target BER (not assumed)."
  [d ber]
  (let [sens (receiver-sensitivity-dbm ber (:line-rate-gbps d) (:rx-responsivity-a-per-w d))]
    (assoc d :rx-sensitivity-dbm (py-round sens 3))))

;; ── APD (avalanche photodiode) receiver: avalanche gain vs excess noise ─────
(defn excess-noise-factor
  "McIntyre excess-noise factor F(M) = k·M + (1−k)·(2 − 1/M). F(1)=1; grows with M and k_eff."
  ([gain-m] (excess-noise-factor gain-m 0.3))
  ([gain-m k-eff]
   (when (< gain-m 1)
     (throw (ex-info "APD gain M must be ≥ 1" {:gain-m gain-m})))
   (when-not (<= 0.0 k-eff 1.0)
     (throw (ex-info "k_eff (ionization ratio) must lie in [0,1]" {:k-eff k-eff})))
   (+ (* k-eff gain-m) (* (- 1.0 k-eff) (- 2.0 (/ 1.0 gain-m))))))

(defn apd-sensitivity-dbm
  "APD receiver sensitivity (dBm) — the PIN thermal-limited value improved by M/√F(M).
  HONEST (G10/N4): thermal-limited bound only."
  ([ber line-rate-gbps] (apd-sensitivity-dbm ber line-rate-gbps 10.0 0.3 0.9 300.0 50.0))
  ([ber line-rate-gbps gain-m] (apd-sensitivity-dbm ber line-rate-gbps gain-m 0.3 0.9 300.0 50.0))
  ([ber line-rate-gbps gain-m k-eff] (apd-sensitivity-dbm ber line-rate-gbps gain-m k-eff 0.9 300.0 50.0))
  ([ber line-rate-gbps gain-m k-eff responsivity-a-per-w temperature-k load-ohm]
   (let [pin (receiver-sensitivity-dbm ber line-rate-gbps responsivity-a-per-w temperature-k load-ohm)
         improvement-db (* 10.0 (Math/log10 (/ gain-m (Math/sqrt (excess-noise-factor gain-m k-eff)))))]
     (- pin improvement-db))))

;; ── reference designs ───────────────────────────────────────────────────────
(def cpo-reference
  (link-design :name "cpo-2km-100g"
               :laser-power-dbm 10.0 :modulator-il-db 4.0
               :tx-grating-coupler-db 1.5 :rx-grating-coupler-db 1.5
               :tx-waveguide-cm 1.5 :rx-waveguide-cm 1.0
               :fibre-m 2000.0 :tx-energy-pj-per-bit 1.2 :rx-energy-pj-per-bit 1.0))

(def pluggable-reference
  (link-design :name "pluggable-2km-100g"
               :laser-power-dbm 10.0 :modulator-il-db 5.0
               :tx-grating-coupler-db 2.0 :rx-grating-coupler-db 2.0
               :tx-waveguide-cm 2.0 :rx-waveguide-cm 2.0
               :fibre-m 2000.0
               :tx-energy-pj-per-bit 6.0 :rx-energy-pj-per-bit 5.5))

(defn- breakdown-repr
  "Python repr of the breakdown dict: {'modulator_il': 4.0, 'tx_grating_coupler': 1.5, …}."
  [breakdown]
  (str "{"
       (str/join ", "
                 (map (fn [[k v]] (str "'" k "': " (py-float-repr v))) breakdown))
       "}"))

(defn report
  "Render a human-readable link-budget comparison (1:1 with link_budget.report)."
  ([] (report [cpo-reference pluggable-reference]))
  ([designs]
   (let [budgets (mapv compute designs)
         L (transient ["# noroshi 烽 — optical link budget (光電融合 / CPO)" ""])]
     (doseq [b budgets]
       (let [verdict (if (:closes b) "CLOSES" "FAILS (insufficient margin)")]
         (conj! L (str "## " (:name b)))
         (conj! L (str "- received power : " (py-float-repr (:received-dbm b))
                       " dBm  (total loss " (py-float-repr (:total-loss-db b)) " dB)"))
         (conj! L (str "- link margin    : " (py-float-repr (:margin-db b)) " dB  → " verdict))
         (conj! L (str "- photocurrent   : " (py-float-repr (:received-current-ua b)) " µA"))
         (conj! L (str "- energy/bit     : " (py-float-repr (:energy-pj-per-bit b)) " pJ/bit"))
         (conj! L (str "- loss breakdown : " (breakdown-repr (:breakdown b))))
         (conj! L "")))
     (when (and (>= (count budgets) 2) (> (:energy-pj-per-bit (budgets 1)) 0))
       (let [ratio (/ (:energy-pj-per-bit (budgets 1)) (:energy-pj-per-bit (budgets 0)))
             ratio-s (-> (java.math.BigDecimal. (double ratio))
                         (.setScale 2 java.math.RoundingMode/HALF_EVEN)
                         .toPlainString)]
         (conj! L (str "**CPO energy advantage**: " (:name (budgets 0)) " costs "
                       (py-float-repr (:energy-pj-per-bit (budgets 0))) " pJ/bit vs "
                       (:name (budgets 1)) " "
                       (py-float-repr (:energy-pj-per-bit (budgets 1)))
                       " pJ/bit — **" ratio-s "× lower energy/bit**."))))
     (conj! L "")
     (conj! L (str "> R0 design arithmetic only. No foundry tapeout, no measured device, no live "
                   "laser (G7 outward-gated). `:representative` device parameters."))
     (str/join "\n" (persistent! L)))))

#?(:clj
   (defn -main
     "CLI entry: print the offline link-budget report (1:1 with `python3 link_budget.py`)."
     [& _argv]
     (println (report))
     0))
