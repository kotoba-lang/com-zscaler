(ns noroshi.methods.isac-sim
  "noroshi (烽) ISAC simulator — the sensing-communication-fusion face (ADR-2606051600).
  1:1 Clojure port of methods/isac_sim.py. Stdlib only (Math/ + BigDecimal + complex helper).

  ISAC = Integrated Sensing And Communication (a.k.a. JCAS, joint communication-and-sensing):
  one waveform that simultaneously carries data AND illuminates the environment, so the same
  photonic / RF front-end that runs the link also senses range + radial velocity. The 烽火台
  (beacon-watchtower) metaphor: the fire both CARRIES a coded message (communication) and is
  SEEN at a distance (sensing) — one emission, two functions.

  Implements the OFDM-radar reciprocal-processing model (Sturm & Wiesbeck): the transmitter
  knows its own data symbols X[n,m], divides them out of the echo to get a pure delay-Doppler
  grid, and recovers a target by a 2-D periodogram, plus the comms-vs-sensing power-split
  tradeoff that makes JCAS a DESIGN choice.

  CIVILIAN sensing only (collision-avoidance / presence / range-rate). The target is an OBJECT
  with a range and a velocity — never a person, never a pattern-of-life (watari G4). Fire-control
  / weapon-cue sensing is structurally absent (N1). Deterministic + offline: no hardware, no
  live emission (G7).

  CONSTITUTIONAL gates (noroshi CLAUDE.md):
    G3 civilian-force-separation — the schema has range_m + velocity_mps only; no :weaponizable,
       no fire-control / directed-energy / targeting mode (unrepresentable, Charter §1.12).
    G4 sensing-not-surveillance — a SenseEstimate is an OBJECT's range+velocity+bins, never a
       :person / biometric / pattern-of-life field.
    G10 sourcing-honesty — `:representative` arithmetic/DSP; no measured silicon; honest aliasing.

  House style: kebab keyword keys; Python ':…' strings stay literal strings; pure fns; file I/O
  only at #?(:clj) edges; bad inputs (closed-vocab / range guard) → ex-info (== Python ValueError).
  Float arithmetic EXACT: round(x,n) / {:.Nf} reproduce Python banker's rounding (HALF_EVEN) on the
  exact double; complex ops via noroshi.methods.complex (cmath byte-for-byte); the periodogram
  reproduces the Python double-loop summation order EXACTLY. Portable .cljc."
  (:require [clojure.string :as str]
            [noroshi.methods.complex :as cx]
            #?(:clj [noroshi.methods.mt19937 :as mt])))

(def C-LIGHT 299792458.0)
(def TWO-PI (* 2.0 Math/PI))

;; ── float formatting helpers (Python round / format / repr) ──────────────────
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

(defn fmt-f
  "Python f-string {:.Nf}: fixed-point with HALF_EVEN rounding at N decimals."
  [x n]
  #?(:clj
     (-> (java.math.BigDecimal. (double x))
         (.setScale (int n) java.math.RoundingMode/HALF_EVEN)
         .toPlainString)
     :cljs
     (.toFixed (double x) n)))

(defn py-float-repr
  "Python repr() of a float: shortest round-trip decimal, integral floats keep `.0`."
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

;; ── IsacWaveform (dataclass defaults + derived properties) ───────────────────
(def default-waveform
  "Mirror of the IsacWaveform frozen dataclass defaults. kebab keyword keys."
  {:n-sub 64           ; subcarriers  → range processing dimension
   :n-sym 16           ; OFDM symbols → Doppler processing dimension
   :subcarrier-hz 1.0e6   ; Δf = 1 MHz  → bandwidth B = n_sub·Δf = 64 MHz
   :symbol-s 1.2e-6       ; OFDM symbol duration incl. cyclic prefix
   :carrier-hz 28.0e9})   ; f_c (mmWave; sets velocity↔Doppler scale)

(defn waveform
  "Build an IsacWaveform-equivalent map, overriding defaults with the supplied kwargs."
  [& {:as overrides}]
  (merge default-waveform overrides))

(defn bandwidth-hz [wf] (* (:n-sub wf) (:subcarrier-hz wf)))
(defn wavelength-m [wf] (/ C-LIGHT (:carrier-hz wf)))
(defn range-resolution-m
  "ΔR = c / (2·B)."
  [wf] (/ C-LIGHT (* 2.0 (bandwidth-hz wf))))
(defn velocity-resolution-mps
  "Δv = λ / (2·M·T)."
  [wf] (/ (wavelength-m wf) (* 2.0 (:n-sym wf) (:symbol-s wf))))
(defn max-unambiguous-range-m
  "R_max = c / (2·Δf)."
  [wf] (/ C-LIGHT (* 2.0 (:subcarrier-hz wf))))

;; ── Target / SenseEstimate records (as plain kebab-keyed maps) ───────────────
(defn target
  "Target(range_m, velocity_mps, rcs=1.0). rcs = reflectivity α² (linear); civilian object."
  ([range-m velocity-mps] (target range-m velocity-mps 1.0))
  ([range-m velocity-mps rcs]
   {:range-m (double range-m) :velocity-mps (double velocity-mps) :rcs (double rcs)}))

(defn sense-estimate
  [range-m velocity-mps range-bin doppler-bin peak-magnitude]
  {:range-m range-m :velocity-mps velocity-mps
   :range-bin range-bin :doppler-bin doppler-bin :peak-magnitude peak-magnitude})

;; ── core DSP ─────────────────────────────────────────────────────────────────
(defn- qpsk-symbol
  "Deterministic unit-magnitude QPSK data symbol (no RNG → reproducible tests).
  _qpsk_symbol(n,m) = cmath.exp(1j·(π/4 + quadrant·π/2)), quadrant = (n·3 + m·5) mod 4."
  [n m]
  (let [quadrant (mod (+ (* n 3) (* m 5)) 4)]
    (cx/cis (+ (/ Math/PI 4) (* quadrant (/ Math/PI 2))))))

(defn qpsk-symbol-magnitude
  "abs(_qpsk_symbol(n,m)) — exposed for the unit-magnitude invariant test."
  [n m]
  (cx/cabs (qpsk-symbol n m)))

(defn- validate-waveform
  "Reject a degenerate waveform (div-by-zero / empty grid). == Python ValueError."
  [wf]
  (when (or (< (:n-sub wf) 1) (< (:n-sym wf) 1))
    (throw (ex-info "waveform needs at least 1 subcarrier and 1 symbol" {:wf wf})))
  (when (or (<= (:subcarrier-hz wf) 0) (<= (:symbol-s wf) 0) (<= (:carrier-hz wf) 0))
    (throw (ex-info "subcarrier spacing, symbol duration, and carrier must be positive" {:wf wf}))))

(defn- echo-grid
  "Reciprocal delay-Doppler grid D[n,m] = echo / data
   = α·e^{-j2π nΔf τ}·e^{+j2π mT f_d}. Vector of n-sub rows, each a vector of n-sym complexes."
  [wf tgt]
  (let [tau (/ (* 2.0 (:range-m tgt)) C-LIGHT)
        f-d (/ (* 2.0 (:velocity-mps tgt)) (wavelength-m wf))
        alpha (Math/sqrt (max (:rcs tgt) 0.0))]
    (mapv (fn [n]
            (mapv (fn [m]
                    (let [x (qpsk-symbol n m)
                          ;; echo = x * alpha * exp(-j2π nΔf τ) * exp(+j2π mT f_d)
                          echo (cx/mul
                                (cx/mul (cx/mul x (cx/c alpha 0.0))
                                        (cx/cis (* (- TWO-PI) n (:subcarrier-hz wf) tau)))
                                (cx/cis (* TWO-PI m (:symbol-s wf) f-d)))]
                      (cx/cdiv echo x)))      ; divide out the known data
                  (range (:n-sym wf))))
          (range (:n-sub wf)))))

(defn- periodogram
  "2-D range-Doppler periodogram magnitude P[k,l] over the reciprocal grid (range +j, Doppler −j).
  Reproduces the Python nested-loop accumulation order EXACTLY: for each (k,l) acc starts at 0j,
  then n outer / m inner, acc += grid[n][m]·rk·exp(−j2π m l / M)."
  [wf grid]
  (let [n-sub (:n-sub wf) n-sym (:n-sym wf)]
    (mapv (fn [k]
            (mapv (fn [l]
                    (let [acc
                          (reduce
                           (fn [acc n]
                             (let [rk (cx/cis (/ (* TWO-PI n k) n-sub))
                                   row (nth grid n)]
                               (reduce
                                (fn [acc m]
                                  (cx/add acc
                                          (cx/mul (cx/mul (nth row m) rk)
                                                  (cx/cis (/ (* (- TWO-PI) m l) n-sym)))))
                                acc
                                (range n-sym))))
                           cx/zero
                           (range n-sub))]
                      (cx/cabs acc)))
                  (range n-sym)))
          (range n-sub))))

(defn- bin->estimate
  [wf k l mag]
  (let [tau (/ k (* (:n-sub wf) (:subcarrier-hz wf)))
        f-d (/ l (* (:n-sym wf) (:symbol-s wf)))]
    (sense-estimate (/ (* C-LIGHT tau) 2.0) (/ (* (wavelength-m wf) f-d) 2.0) k l mag)))

(defn- argmax-bin
  "max(((k,l) …), key=mags[k][l]) — Python max ties keep the FIRST (k outer, l inner) encountered."
  [n-sub n-sym mags]
  (loop [best-k 0 best-l 0 best-v (get-in mags [0 0]) k 0]
    (if (>= k n-sub)
      [best-k best-l]
      (let [[bk bl bv]
            (loop [l 0 bk best-k bl best-l bv best-v]
              (if (>= l n-sym)
                [bk bl bv]
                (let [v (get-in mags [k l])]
                  (if (> v bv)
                    (recur (inc l) k l v)
                    (recur (inc l) bk bl bv)))))]
        (recur bk bl bv (inc k))))))

(defn estimate-target
  "Recover (range, velocity) from one target via the 2-D OFDM-radar periodogram.
  Peak bin (k,l) maps to τ = k/(N·Δf), f_d = l/(M·T)."
  [wf tgt]
  (validate-waveform wf)
  (let [mags (periodogram wf (echo-grid wf tgt))
        [k l] (argmax-bin (:n-sub wf) (:n-sym wf) mags)]
    (bin->estimate wf k l (get-in mags [k l]))))

(defn- combined-grid
  "Sum the per-target reciprocal grids (the grid is linear in the targets)."
  [wf targets]
  (let [grids (mapv #(echo-grid wf %) targets)
        n-sub (:n-sub wf) n-sym (:n-sym wf)]
    (mapv (fn [n]
            (mapv (fn [m]
                    (cx/csum (map (fn [g] (get-in g [n m])) grids)))
                  (range n-sym)))
          (range n-sub))))

(defn- extract-peaks
  "CLEAN peak extraction: pick the global max, suppress a ±guard cell (toroidal), repeat.
  Stops after `top-n` picks (if set) and/or once the remaining max < `threshold` (if set)."
  [wf mags & {:keys [top-n guard threshold] :or {guard 1}}]
  (let [n-sub (:n-sub wf) n-sym (:n-sym wf)
        cap (if (nil? top-n) (* n-sub n-sym) (min top-n (* n-sub n-sym)))]
    (loop [i 0
           work (mapv vec mags)         ; mutable working copy [k][l]
           picks []]
      (if (>= i cap)
        picks
        (let [[k l] (argmax-bin n-sub n-sym work)]
          (if (and (some? threshold) (< (get-in mags [k l]) threshold))
            picks
            (let [picks' (conj picks (bin->estimate wf k l (get-in mags [k l])))
                  ;; suppress a ±guard cell (toroidal)
                  work'
                  (reduce
                   (fn [w dk]
                     (reduce
                      (fn [w dl]
                        (assoc-in w [(mod (+ k dk) n-sub) (mod (+ l dl) n-sym)] -1.0))
                      w
                      (range (- guard) (inc guard))))
                   work
                   (range (- guard) (inc guard)))]
              (recur (inc i) work' picks'))))))))

(defn estimate-targets
  "Multi-target sensing: ONE combined echo → CLEAN top-N peak extraction.
  top-n defaults to (count targets); guard defaults to 1."
  [wf targets & {:keys [top-n guard] :or {guard 1}}]
  (validate-waveform wf)
  (if (empty? targets)
    []
    (let [top-n (if (nil? top-n) (count targets) top-n)
          mags (periodogram wf (combined-grid wf targets))]
      (extract-peaks wf mags :top-n top-n :guard guard))))

#?(:clj
   (defn- add-noise
     "Add deterministic complex-Gaussian noise (seeded → reproducible). σ per real/imag component.
     grid[n][m] + complex(rng.gauss(0,σ), rng.gauss(0,σ)); MT19937 advances n outer, m inner,
     and within a cell the REAL part is drawn before the IMAG part (Python arg eval order)."
     [wf grid sigma seed]
     (let [rng (mt/make seed)
           n-sub (:n-sub wf) n-sym (:n-sym wf)]
       (mapv (fn [n]
               (mapv (fn [m]
                       (let [re (mt/gauss! rng 0.0 sigma)
                             im (mt/gauss! rng 0.0 sigma)]
                         (cx/add (get-in grid [n m]) (cx/c re im))))
                     (range n-sym)))
             (range n-sub)))))

(defn detect-cfar
  "Detect targets in noise with a constant-false-alarm threshold (simplified CA-CFAR).
  Adds seeded complex-Gaussian noise to the combined echo, forms the periodogram, estimates the
  noise floor as the MEAN magnitude (cell-averaging CFAR), declares detections only where a CLEAN
  peak exceeds threshold_factor × mean. Deterministic for a given seed."
  [wf targets & {:keys [noise-sigma threshold-factor seed guard]
                 :or {noise-sigma 0.0 threshold-factor 4.0 seed 0 guard 1}}]
  (validate-waveform wf)
  (when (< noise-sigma 0)
    (throw (ex-info "noise_sigma must be ≥ 0" {:noise-sigma noise-sigma})))
  (when (<= threshold-factor 0)
    (throw (ex-info "threshold_factor must be positive" {:threshold-factor threshold-factor})))
  (if (and (empty? targets) (== noise-sigma 0))
    []
    (let [n-sub (:n-sub wf) n-sym (:n-sym wf)
          grid0 (if (seq targets)
                  (combined-grid wf targets)
                  (mapv (fn [_] (mapv (fn [_] cx/zero) (range n-sym))) (range n-sub)))
          grid (if (> noise-sigma 0)
                 #?(:clj (add-noise wf grid0 noise-sigma seed)
                    :cljs (throw (ex-info "noise requires :clj (MT19937)" {})))
                 grid0)
          mags (periodogram wf grid)
          n-cells (* n-sub n-sym)
          ;; sum(v for row in mags for v in row) — row-major fold, matching Python order
          total (reduce (fn [s row] (reduce + s row)) 0.0 mags)
          mean-floor (/ total n-cells)
          threshold (* threshold-factor mean-floor)]
      (extract-peaks wf mags :top-n nil :guard guard :threshold threshold))))

(defn detection-probability
  "Monte-Carlo Pd: fraction of `trials` seeds in which CFAR detects the target's true bin.
  Seeds are 0..trials-1 (reproducible). The true bin is the noiseless periodogram peak."
  [wf tgt noise-sigma & {:keys [threshold-factor trials] :or {threshold-factor 4.0 trials 16}}]
  (when (< trials 1)
    (throw (ex-info "trials must be ≥ 1" {:trials trials})))
  (let [truth (estimate-target wf tgt)
        true-bin [(:range-bin truth) (:doppler-bin truth)]
        hits (reduce
              (fn [hits seed]
                (let [dets (detect-cfar wf [tgt] :noise-sigma noise-sigma
                                        :threshold-factor threshold-factor :seed seed)
                      bins (set (map (fn [d] [(:range-bin d) (:doppler-bin d)]) dets))]
                  (if (contains? bins true-bin) (inc hits) hits)))
              0
              (range trials))]
    (/ (double hits) trials)))

(defn pd-vs-snr
  "Sweep the noise level → [(σ, Pd)] — the detector's operating curve."
  [wf tgt sigmas & {:keys [threshold-factor trials] :or {threshold-factor 4.0 trials 16}}]
  (mapv (fn [s] [s (detection-probability wf tgt s :threshold-factor threshold-factor :trials trials)])
        sigmas))

;; ── communication ↔ sensing power-split (the JCAS tradeoff) ──────────────────
(defn jcas-operating-point
  "One point on the JCAS tradeoff: split total power ρ:(1−ρ) between comms and sensing.
  Returns {:power-split :capacity-gbps :range-std-m :velocity-std-mps}."
  [wf power-split & {:keys [tx-power-w channel-gain-db noise-psd-dbm-hz]
                     :or {tx-power-w 1.0 channel-gain-db -90.0 noise-psd-dbm-hz -174.0}}]
  (when-not (<= 0.0 power-split 1.0)
    (throw (ex-info "power_split ρ must lie in [0,1]" {:power-split power-split})))
  (validate-waveform wf)
  (let [b (bandwidth-hz wf)
        noise-w (* (Math/pow 10 (/ (- noise-psd-dbm-hz 30) 10)) b)
        gain (Math/pow 10 (/ channel-gain-db 10))
        ;; Communication: flat-channel Shannon over the whole band, fed ρ of the power.
        snr-comm (/ (* (max power-split 1e-12) tx-power-w gain) noise-w)
        capacity-bps (* b (/ (Math/log (+ 1.0 snr-comm)) (Math/log 2.0)))   ; math.log2
        ;; Sensing: (1−ρ) of the power, full N·M coherent processing gain.
        n-mn (* (:n-sub wf) (:n-sym wf))
        snr-sense (* (/ (* (max (- 1.0 power-split) 1e-12) tx-power-w gain) noise-w) n-mn)
        crlb-scale (/ 1.0 (Math/sqrt (* 2.0 (max snr-sense 1e-12))))]
    {:power-split power-split
     :capacity-gbps (/ capacity-bps 1e9)
     :range-std-m (* (range-resolution-m wf) crlb-scale)
     :velocity-std-mps (* (velocity-resolution-mps wf) crlb-scale)}))

;; ── report ───────────────────────────────────────────────────────────────────
(defn report
  "Render the ISAC face out/ artifact: a recovered target + the JCAS tradeoff sweep.
  Byte-identical to link/isac_sim.report()."
  ([] (report (waveform)))
  ([wf]
   (let [tgt (target (* 4 (range-resolution-m wf)) (* 3 (velocity-resolution-mps wf)))
         est (estimate-target wf tgt)
         L (transient
            ["# noroshi 烽 — ISAC (JCAS) sensing + communication"
             ""
             "## waveform"
             (str "- bandwidth        : " (fmt-f (/ (bandwidth-hz wf) 1e6) 1) " MHz  ("
                  (:n-sub wf) " subcarriers × " (fmt-f (/ (:subcarrier-hz wf) 1e3) 0) " kHz)")
             (str "- range resolution : " (fmt-f (range-resolution-m wf) 3) " m   (R_max "
                  (fmt-f (/ (max-unambiguous-range-m wf) 1e3) 2) " km)")
             (str "- velocity res.    : " (fmt-f (velocity-resolution-mps wf) 3) " m/s")
             ""
             "## sensing recovery (civilian object — never a person, N1/G4)"
             (str "- true   : R = " (fmt-f (:range-m tgt) 3) " m, v = " (fmt-f (:velocity-mps tgt) 3) " m/s")
             (str "- est.   : R = " (fmt-f (:range-m est) 3) " m, v = " (fmt-f (:velocity-mps est) 3)
                  " m/s  (bins k=" (:range-bin est) ", l=" (:doppler-bin est) ")")
             ""
             "## JCAS power-split tradeoff (ρ = fraction to COMMS)"
             "| ρ | capacity (Gb/s) | range σ (m) | velocity σ (m/s) |"
             "|---|---|---|---|"])]
     (doseq [rho [0.1 0.3 0.5 0.7 0.9]]
       (let [op (jcas-operating-point wf rho)]
         (conj! L (str "| " (fmt-f rho 1) " | " (fmt-f (:capacity-gbps op) 3) " | "
                       (fmt-f (:range-std-m op) 4) " | " (fmt-f (:velocity-std-mps op) 4) " |"))))
     (let [swf (waveform :n-sub 16 :n-sym 8)
           stgt (target (* 4 (range-resolution-m swf)) (* 2 (velocity-resolution-mps swf)))]
       (conj! L "")
       (conj! L "## CA-CFAR detection probability vs noise (Pd, seeded Monte-Carlo)")
       (conj! L "| noise σ | Pd |")
       (conj! L "|---|---|")
       (doseq [[sigma pd] (pd-vs-snr swf stgt [0.0 1.0 2.0 4.0 8.0] :trials 8)]
         (conj! L (str "| " (fmt-f sigma 1) " | " (fmt-f pd 2) " |"))))
     (conj! L "")
     (conj! L (str "> One waveform, two functions: more comms power ⇒ higher data rate but coarser "
                   "sensing; Pd degrades as noise rises (constant-false-alarm threshold)."))
     (conj! L (str "> R0 simulation only — no live emission, no hardware (G7). Sensing is civilian "
                   "collision-avoidance/presence; fire-control / targeting is structurally absent (N1)."))
     (str/join "\n" (persistent! L)))))

#?(:clj
   (defn -main
     "CLI entry: print the offline ISAC report (1:1 with `python3 isac_sim.py`)."
     [& _argv]
     (println (report))
     0))
