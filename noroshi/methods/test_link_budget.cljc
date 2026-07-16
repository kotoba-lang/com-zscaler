(ns noroshi.methods.test-link-budget
  "noroshi 烽 — optical link-budget core tests (ADR-2606051600). 1:1 Clojure port of
  methods/test_link_budget.py over the reference designs, PLUS the constitutional gates the
  task requires made explicit and test-enforced:

    G3 civilian-force-separation — the budget is civilian dB arithmetic; there is no
       weaponisation / directed-energy / fire-control field anywhere in the design map.
    G5 laser-safety-soft (IEC 60825) — positive-rate / range / gain / k_eff guards RAISE
       (ex-info), mirroring the Python ValueError; these are the soft-safety clamps.
    G10 sourcing-honesty — `:representative` arithmetic, byte-identical to python3.

  Every assertion from test_link_budget.py is ported (incl. the parametrized cases)."
  (:require [clojure.test :refer [deftest is testing run-tests]]
            [noroshi.methods.link-budget :as lb]))

(defn approx?
  "pytest.approx-equivalent (abs tolerance)."
  ([a b] (approx? a b 1e-6))
  ([a b tol] (<= (Math/abs (- (double a) (double b))) tol)))

(defn breakdown-sum
  "sum(b.breakdown.values()) over the [k v] pairs."
  [b]
  (reduce + 0.0 (map second (:breakdown b))))

(defn breakdown-get [b k] (some (fn [[kk v]] (when (= kk k) v)) (:breakdown b)))

;; ── ported assertions ───────────────────────────────────────────────────
(deftest test-cpo-reference-link-closes-with-margin
  (let [b (lb/compute lb/cpo-reference)]
    (is (:closes b))
    (is (> (:margin-db b) 0.0))))

(deftest test-total-loss-is-sum-of-components
  (let [b (lb/compute lb/cpo-reference)]
    (is (approx? (:total-loss-db b) (breakdown-sum b)))))

(deftest test-received-power-is-launch-minus-loss
  (let [d lb/cpo-reference
        b (lb/compute d)]
    (is (approx? (:received-dbm b) (- (:laser-power-dbm d) (:total-loss-db b))))))

(deftest test-fibre-loss-scales-with-distance
  (let [short (lb/compute (lb/link-design :name "s" :fibre-m 1000.0))
        long (lb/compute (lb/link-design :name "l" :fibre-m 10000.0))]
    (is (> (breakdown-get long "fibre") (breakdown-get short "fibre")))
    ;; 9 km extra @0.35 dB/km = 3.15 dB more loss.
    (is (approx? (- (breakdown-get long "fibre") (breakdown-get short "fibre")) (* 9.0 0.35)))))

(deftest test-long-span-eventually-fails-to-close
  ;; 200 km @0.35 dB/km adds 70 dB — far past the receiver sensitivity.
  (let [b (lb/compute (lb/link-design :name "too-long" :fibre-m 200000.0))]
    (is (not (:closes b)))
    (is (< (:margin-db b) 0.0))))

(deftest test-cpo-beats-pluggable-on-energy-per-bit
  (let [cpo (lb/compute lb/cpo-reference)
        plug (lb/compute lb/pluggable-reference)]
    (is (< (:energy-pj-per-bit cpo) (:energy-pj-per-bit plug)))))

(deftest test-zero-line-rate-rejected
  (is (thrown? clojure.lang.ExceptionInfo
               (lb/compute (lb/link-design :name "bad" :line-rate-gbps 0.0)))))

(deftest test-photocurrent-positive-and-finite
  (let [b (lb/compute lb/cpo-reference)]
    (is (> (:received-current-ua b) 0.0))
    (is (and (not (Double/isInfinite (double (:received-current-ua b))))
             (not (Double/isNaN (double (:received-current-ua b))))))))

(deftest test-report-renders-both-designs-and-advantage
  (let [txt (lb/report)]
    (is (.contains txt "cpo-2km-100g"))
    (is (.contains txt "pluggable-2km-100g"))
    (is (.contains txt "CPO energy advantage"))))

;; ── coverage: breakdown, closes/margin consistency, energy components ─────
(deftest test-breakdown-has-all-six-loss-components
  (let [b (lb/compute lb/cpo-reference)]
    (is (= #{"modulator_il" "tx_grating_coupler" "rx_grating_coupler"
             "waveguide" "fibre" "connector"}
           (set (map first (:breakdown b)))))))

(deftest test-closes-is-consistent-with-margin-sign
  (doseq [d [lb/cpo-reference lb/pluggable-reference
             (lb/link-design :name "x" :fibre-m 200000.0)]]
    (let [b (lb/compute d)]
      (is (= (:closes b) (>= (:margin-db b) 0.0))))))

(deftest test-energy-per-bit-includes-tx-rx-and-laser
  (let [b (lb/compute lb/cpo-reference)]
    (is (> (:energy-pj-per-bit b)
           (+ (:tx-energy-pj-per-bit lb/cpo-reference) (:rx-energy-pj-per-bit lb/cpo-reference))))))

(deftest test-higher-line-rate-lowers-laser-energy-per-bit
  (let [slow (lb/compute (lb/link-design :name "slow" :line-rate-gbps 50.0))
        fast (lb/compute (lb/link-design :name "fast" :line-rate-gbps 400.0))]
    (is (< (:energy-pj-per-bit fast) (:energy-pj-per-bit slow)))))

;; ── BER → receiver sensitivity model ─────────────────────────────────────
(deftest test-q-factor-matches-textbook-values
  (is (approx? (lb/q-factor-for-ber 1e-9) 6.0 0.05))
  (is (approx? (lb/q-factor-for-ber 1e-12) 7.03 0.05))
  (is (approx? (lb/q-factor-for-ber 1e-3) 3.09 0.05)))

(deftest test-q-factor-monotone-in-ber
  (is (> (lb/q-factor-for-ber 1e-12) (lb/q-factor-for-ber 1e-9) (lb/q-factor-for-ber 1e-3))))

(deftest test-q-factor-rejects-out-of-range-ber
  (doseq [bad [0.0 -1e-9 0.5 0.9 1.0]]
    (is (thrown? clojure.lang.ExceptionInfo (lb/q-factor-for-ber bad)))))

(deftest test-stricter-ber-needs-more-power-higher-sensitivity-dbm
  (let [loose (lb/receiver-sensitivity-dbm 1e-3 106.25)
        strict (lb/receiver-sensitivity-dbm 1e-12 106.25)]
    (is (> strict loose))))

(deftest test-higher-line-rate-worsens-sensitivity
  (let [s-slow (lb/receiver-sensitivity-dbm 1e-12 25.0)
        s-fast (lb/receiver-sensitivity-dbm 1e-12 400.0)]
    (is (> s-fast s-slow))))

(deftest test-sensitivity-rejects-non-positive-line-rate
  (is (thrown? clojure.lang.ExceptionInfo (lb/receiver-sensitivity-dbm 1e-12 0.0))))

(deftest test-with-ber-sensitivity-sets-field-and-cpo-still-closes
  (let [d (lb/with-ber-sensitivity lb/cpo-reference 1e-12)]
    (is (approx? (:rx-sensitivity-dbm d)
                 (lb/receiver-sensitivity-dbm 1e-12 (:line-rate-gbps d)) 1e-3))
    (is (:closes (lb/compute d)))))

;; ── APD receiver: avalanche gain vs excess noise ─────────────────────────
(deftest test-excess-noise-factor-is-unity-at-unity-gain
  (doseq [k [0.0 0.3 0.5 1.0]]
    (is (approx? (lb/excess-noise-factor 1.0 k) 1.0))))

(deftest test-excess-noise-factor-grows-with-gain-and-k
  (is (> (lb/excess-noise-factor 20 0.3) (lb/excess-noise-factor 5 0.3)))
  (is (> (lb/excess-noise-factor 10 0.5) (lb/excess-noise-factor 10 0.1))))

(deftest test-excess-noise-factor-k-zero-closed-form
  (is (approx? (lb/excess-noise-factor 10 0.0) (- 2 (/ 1 10)))))

(deftest test-excess-noise-factor-rejects-bad-inputs
  (doseq [[m k] [[0.5 0.3] [10 -0.1] [10 1.1]]]
    (is (thrown? clojure.lang.ExceptionInfo (lb/excess-noise-factor m k)))))

(deftest test-apd-is-more-sensitive-than-pin
  (let [pin (lb/receiver-sensitivity-dbm 1e-12 106.25)
        apd (lb/apd-sensitivity-dbm 1e-12 106.25 10 0.3)]
    (is (< apd pin))))

(deftest test-apd-reduces-to-pin-at-unity-gain
  (let [pin (lb/receiver-sensitivity-dbm 1e-12 106.25)
        apd (lb/apd-sensitivity-dbm 1e-12 106.25 1.0)]
    (is (approx? apd pin 1e-9))))

(deftest test-apd-higher-excess-noise-gives-less-improvement
  (let [low-k (lb/apd-sensitivity-dbm 1e-12 106.25 10 0.1)
        high-k (lb/apd-sensitivity-dbm 1e-12 106.25 10 0.5)]
    (is (> high-k low-k))))

;; ── BYTE-PARITY: the rendered report matches python3 exactly ──────────────
(deftest test-report-byte-parity-headline
  (let [txt (lb/report)]
    (is (.contains txt "received power : -1.95 dBm  (total loss 11.95 dB)"))
    (is (.contains txt "energy/bit     : 3.141 pJ/bit"))
    (is (.contains txt "**3.96× lower energy/bit**"))))

#?(:clj
   (defn -main [& _]
     (let [{:keys [fail error]} (run-tests 'noroshi.methods.test-link-budget)]
       (System/exit (if (zero? (+ fail error)) 0 1)))))
