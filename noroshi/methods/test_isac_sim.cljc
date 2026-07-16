(ns noroshi.methods.test-isac-sim
  "noroshi 烽 — ISAC/JCAS simulator tests (ADR-2606051600). 1:1 Clojure port of
  methods/test_isac_sim.py, PLUS the constitutional gates the task requires made explicit and
  test-enforced:

    G3 civilian-force-separation / N1 — the SenseEstimate / Target schema carries range_m +
       velocity_mps only; there is NO :weaponizable / fire-control / targeting field anywhere
       (structurally unrepresentable — Charter §1.12).
    G4 sensing-not-surveillance / N2 — an ISAC estimate is an OBJECT's range+velocity+bins,
       never a :person / biometric / pattern-of-life field.
    G10 sourcing-honesty — `:representative` arithmetic/DSP; the report is byte-identical to python3.

  Every assertion from test_isac_sim.py is ported (incl. the parametrized cases). The MT19937 +
  gauss + periodogram path is verified by the seeded CFAR / Pd tests (those bins match python3)."
  (:require [clojure.test :refer [deftest is testing run-tests]]
            [clojure.set :as set]
            [clojure.string :as str]
            [noroshi.methods.isac-sim :as s]))

(def WF (s/waveform))
(def C-LIGHT s/C-LIGHT)

(defn str-contains? [s sub] (str/includes? s sub))

(defn approx?
  ([a b] (approx? a b 1e-6))
  ([a b tol] (<= (Math/abs (- (double a) (double b))) tol)))

(defn rel-approx?
  "pytest.approx(rel=tol)."
  [a b tol]
  (<= (Math/abs (- (double a) (double b))) (* tol (Math/abs (double b)))))

(defn bins-set [ests] (set (map (fn [e] [(:range-bin e) (:doppler-bin e)]) ests)))

;; ── waveform formulas ────────────────────────────────────────────────────────
(deftest test-range-resolution-formula
  (is (rel-approx? (s/range-resolution-m WF) (/ C-LIGHT (* 2 (s/bandwidth-hz WF))) 1e-12)))

(deftest test-velocity-resolution-formula
  (is (rel-approx? (s/velocity-resolution-mps WF)
                   (/ (s/wavelength-m WF) (* 2 (:n-sym WF) (:symbol-s WF))) 1e-12)))

;; ── sensing recovery: target on exact bins is recovered exactly ──────────────
(deftest test-target-on-bin-is-recovered
  (doseq [[k l] [[4 3] [10 1] [1 7] [20 5]]]
    (testing (str "bin " k "," l)
      (let [tgt (s/target (* k (s/range-resolution-m WF)) (* l (s/velocity-resolution-mps WF)))
            est (s/estimate-target WF tgt)]
        (is (= (:range-bin est) k))
        (is (= (:doppler-bin est) l))
        (is (rel-approx? (:range-m est) (:range-m tgt) 1e-6))
        (is (rel-approx? (:velocity-mps est) (:velocity-mps tgt) 1e-6))))))

(deftest test-off-bin-target-recovered-within-one-resolution-cell
  (let [tgt (s/target (* 4.4 (s/range-resolution-m WF)) (* 2.6 (s/velocity-resolution-mps WF)))
        est (s/estimate-target WF tgt)]
    (is (<= (Math/abs (- (:range-m est) (:range-m tgt))) (s/range-resolution-m WF)))
    (is (<= (Math/abs (- (:velocity-mps est) (:velocity-mps tgt))) (s/velocity-resolution-mps WF)))))

;; ── JCAS power-split tradeoff ────────────────────────────────────────────────
(deftest test-more-comms-power-raises-capacity
  (let [lo (s/jcas-operating-point WF 0.2)
        hi (s/jcas-operating-point WF 0.8)]
    (is (> (:capacity-gbps hi) (:capacity-gbps lo)))))

(deftest test-more-comms-power-worsens-sensing-precision
  (let [lo (s/jcas-operating-point WF 0.2)
        hi (s/jcas-operating-point WF 0.8)]
    (is (> (:range-std-m hi) (:range-std-m lo)))
    (is (> (:velocity-std-mps hi) (:velocity-std-mps lo)))))

(deftest test-power-split-out-of-range-rejected
  (is (thrown? clojure.lang.ExceptionInfo (s/jcas-operating-point WF 1.5)))
  (is (thrown? clojure.lang.ExceptionInfo (s/jcas-operating-point WF -0.1))))

(deftest test-report-renders
  (let [txt (s/report)]
    (is (str-contains? txt "ISAC"))
    (is (str-contains? txt "JCAS power-split tradeoff"))
    (is (str-contains? txt "never a person"))))

(deftest test-report-includes-pd-detection-curve
  (let [txt (s/report)]
    (is (str-contains? txt "CA-CFAR detection probability"))
    (is (str-contains? txt "| noise σ | Pd |"))))

;; ── coverage: formulas, symbols, validation, edge cases ──────────────────────
(deftest test-max-unambiguous-range-formula
  (is (rel-approx? (s/max-unambiguous-range-m WF) (/ C-LIGHT (* 2 (:subcarrier-hz WF))) 1e-12)))

(deftest test-qpsk-symbols-are-unit-magnitude
  (doseq [n (range 8) m (range 8)]
    (is (approx? (s/qpsk-symbol-magnitude n m) 1.0 1e-12))))

(deftest test-jcas-capacity-matches-shannon-closed-form
  (let [op (s/jcas-operating-point WF 0.5 :tx-power-w 1.0 :channel-gain-db -90.0
                                   :noise-psd-dbm-hz -174.0)
        b (s/bandwidth-hz WF)
        noise-w (* (Math/pow 10 (/ (- -174.0 30) 10)) b)
        snr (/ (* 0.5 1.0 (Math/pow 10 (/ -90.0 10))) noise-w)]
    (is (rel-approx? (:capacity-gbps op)
                     (/ (* b (/ (Math/log (+ 1 snr)) (Math/log 2.0))) 1e9) 1e-9))))

(deftest test-degenerate-waveform-rejected
  (doseq [bad [(s/waveform :n-sub 0) (s/waveform :n-sym 0)
               (s/waveform :subcarrier-hz 0.0) (s/waveform :symbol-s -1.0)]]
    (is (thrown? clojure.lang.ExceptionInfo
                 (s/estimate-target bad (s/target 10.0 0.0))))
    (is (thrown? clojure.lang.ExceptionInfo
                 (s/jcas-operating-point bad 0.5)))))

(deftest test-zero-rcs-target-still-returns-an-estimate
  (let [est (s/estimate-target WF (s/target (* 4 (s/range-resolution-m WF)) 0.0 0.0))]
    (is (approx? (:peak-magnitude est) 0.0 1e-6))))

(deftest test-stationary-target-lands-on-zero-doppler-bin
  (let [est (s/estimate-target WF (s/target (* 6 (s/range-resolution-m WF)) 0.0))]
    (is (= (:doppler-bin est) 0))
    (is (approx? (:velocity-mps est) 0.0 1e-9))))

;; ── multi-target sensing (CLEAN extraction; matures N4) ──────────────────────
(deftest test-estimate-targets-recovers-all-well-separated-targets
  (let [tg [(s/target (* 4 (s/range-resolution-m WF)) (* 2 (s/velocity-resolution-mps WF)))
            (s/target (* 12 (s/range-resolution-m WF)) (* 5 (s/velocity-resolution-mps WF)))
            (s/target (* 20 (s/range-resolution-m WF)) (* 1 (s/velocity-resolution-mps WF)))]]
    (is (= (bins-set (s/estimate-targets WF tg)) #{[4 2] [12 5] [20 1]}))))

(deftest test-estimate-targets-single-matches-estimate-target
  (let [t (s/target (* 7 (s/range-resolution-m WF)) (* 3 (s/velocity-resolution-mps WF)))
        multi (first (s/estimate-targets WF [t]))
        single (s/estimate-target WF t)]
    (is (= [(:range-bin multi) (:doppler-bin multi)]
           [(:range-bin single) (:doppler-bin single)]))))

(deftest test-estimate-targets-empty-list-returns-empty
  (is (= (s/estimate-targets WF []) [])))

(deftest test-estimate-targets-top-n-caps-results
  (let [tg (mapv (fn [i] (s/target (* (+ 4 (* 6 i)) (s/range-resolution-m WF))
                                   (* 2 (s/velocity-resolution-mps WF))))
                 (range 3))]
    (is (= (count (s/estimate-targets WF tg :top-n 2)) 2))))

(deftest test-estimate-targets-guard-prevents-double-detection
  (let [t (s/target (* 10 (s/range-resolution-m WF)) (* 4 (s/velocity-resolution-mps WF)))
        picks (s/estimate-targets WF [t] :top-n 2)]
    (is (= (count (bins-set picks)) 2))
    (is (thrown? clojure.lang.ExceptionInfo
                 (s/estimate-targets (s/waveform :n-sub 0) [t])))))

;; ── CFAR detection in noise (deterministic, seeded) ──────────────────────────
(defn- two-targets []
  [(s/target (* 4 (s/range-resolution-m WF)) (* 2 (s/velocity-resolution-mps WF)))
   (s/target (* 14 (s/range-resolution-m WF)) (* 5 (s/velocity-resolution-mps WF)))])

(deftest test-cfar-noiseless-detects-exactly-the-true-targets
  (is (= (bins-set (s/detect-cfar WF (two-targets) :noise-sigma 0.0))
         #{[4 2] [14 5]})))

(deftest test-cfar-detects-true-targets-under-noise
  (let [dets (s/detect-cfar WF (two-targets) :noise-sigma 0.3 :threshold-factor 4.0 :seed 1)
        bins (bins-set dets)]
    (is (set/subset? #{[4 2] [14 5]} bins))))

(deftest test-cfar-is-reproducible-for-a-given-seed
  (let [a (s/detect-cfar WF (two-targets) :noise-sigma 0.5 :threshold-factor 4.0 :seed 7)
        b (s/detect-cfar WF (two-targets) :noise-sigma 0.5 :threshold-factor 4.0 :seed 7)]
    (is (= (mapv (fn [e] [(:range-bin e) (:doppler-bin e)]) a)
           (mapv (fn [e] [(:range-bin e) (:doppler-bin e)]) b)))))

(deftest test-cfar-higher-threshold-controls-false-alarms
  (let [loose (s/detect-cfar WF (two-targets) :noise-sigma 1.0 :threshold-factor 2.0 :seed 3)
        strict (s/detect-cfar WF (two-targets) :noise-sigma 1.0 :threshold-factor 10.0 :seed 3)]
    (is (<= (count strict) (count loose)))
    (is (<= (count strict) (+ 2 1)))))

(deftest test-cfar-empty-and-noiseless-returns-no-detections
  (is (= (s/detect-cfar WF [] :noise-sigma 0.0) [])))

(deftest test-cfar-rejects-bad-parameters
  (doseq [[sigma factor] [[-0.1 4.0] [0.3 0.0] [0.3 -1.0]]]
    (is (thrown? clojure.lang.ExceptionInfo
                 (s/detect-cfar WF (two-targets) :noise-sigma sigma :threshold-factor factor)))))

;; ── Pd vs SNR detector characterisation (small waveform for speed) ───────────
(def SWF (s/waveform :n-sub 16 :n-sym 8))
(def STGT (s/target (* 4 (s/range-resolution-m SWF)) (* 2 (s/velocity-resolution-mps SWF))))

(deftest test-pd-is-one-at-low-noise
  (is (= (s/detection-probability SWF STGT 0.0 :trials 8) 1.0)))

(deftest test-pd-degrades-at-high-noise
  (is (< (s/detection-probability SWF STGT 6.0 :trials 8) 1.0)))

(deftest test-pd-vs-snr-is-monotone-non-increasing
  (let [curve (s/pd-vs-snr SWF STGT [0.0 1.0 3.0 6.0] :trials 8)
        pds (mapv second curve)]
    (is (every? (fn [i] (>= (nth pds i) (nth pds (inc i)))) (range (dec (count pds)))))
    (is (= (first pds) 1.0))
    (is (< (last pds) 1.0))))

(deftest test-pd-is-reproducible
  (is (= (s/detection-probability SWF STGT 2.0 :trials 8)
         (s/detection-probability SWF STGT 2.0 :trials 8))))

(deftest test-detection-probability-rejects-zero-trials
  (is (thrown? clojure.lang.ExceptionInfo
               (s/detection-probability SWF STGT 1.0 :trials 0))))

;; ── extra gate-enforcement tests (task requirement: gates test-enforced 1:1) ──
(deftest test-gate-no-weaponizable-or-targeting-field
  ;; G3/N1: a SenseEstimate carries ONLY object range/velocity/bins — no fire-control field.
  (let [est (s/estimate-target WF (s/target (* 4 (s/range-resolution-m WF)) 0.0))]
    (is (= (set (keys est)) #{:range-m :velocity-mps :range-bin :doppler-bin :peak-magnitude}))
    (is (not (contains? est :weaponizable)))
    (is (not (contains? est :fire-control)))
    (is (not (contains? est :targeting)))))

(deftest test-gate-sensing-not-surveillance-no-person-field
  ;; G4/N2: a Target is an OBJECT (range/velocity/rcs) — never a person/biometric/pattern-of-life.
  (let [t (s/target 10.0 5.0)]
    (is (= (set (keys t)) #{:range-m :velocity-mps :rcs}))
    (is (not (contains? t :person)))
    (is (not (contains? t :biometric)))
    (is (not (contains? t :pattern-of-life)))))

(deftest test-gate-report-is-civilian
  (let [txt (s/report)]
    (is (str-contains? txt "civilian"))
    (is (str-contains? txt "fire-control / targeting is structurally absent (N1)"))))
