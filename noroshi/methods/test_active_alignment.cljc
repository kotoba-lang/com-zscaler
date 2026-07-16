(ns noroshi.methods.test-active-alignment
  "noroshi 烽 — active-alignment + laser-safety core tests (ADR-2606051600). 1:1 Clojure port
  of methods/test_active_alignment.py, PLUS the constitutional gates the task requires made
  explicit and test-enforced:

    G3 civilian-force-separation / N1 — `enable-laser` REFUSES weaponisation / directed-energy
       / dazzle / fire-control + any unknown use; the laser cannot be energised for them.
    G5 laser-safety-soft (IEC 60825) — a hazardous-class laser (2/3R/3B/4) needs an enclosure
       interlock + safety attestation; the gate RAISES (ex-info) exactly where Python raises
       LaserSafetyError.

  The Hooke-Jeeves trajectory is verified bit-identical to python3 via the byte-parity
  headline test (same converged coords + objective + probe count in the rendered report).

  Every assertion from test_active_alignment.py is ported (incl. the parametrized cases)."
  (:require [clojure.test :refer [deftest is testing run-tests]]
            [noroshi.methods.active-alignment :as aa]))

(defn approx?
  "pytest.approx-equivalent (abs tolerance)."
  [a b tol] (<= (Math/abs (- (double a) (double b))) tol))

;; ── laser-safety interlock (the safety-critical gate) ────────────────────────
(deftest test-class1-civilian-use-energises
  (is (nil? (aa/enable-laser (aa/laser-spec :laser-class "1" :use "alignment"))))) ; no raise

(deftest test-weaponisation-uses-are-unrepresentable
  (doseq [use ["weapon" "directed-energy" "dazzle" "fire-control"]]
    (testing use
      (is (thrown? clojure.lang.ExceptionInfo
                   (aa/enable-laser (aa/laser-spec :laser-class "1" :use use)))))))

(deftest test-unknown-use-refused
  (is (thrown? clojure.lang.ExceptionInfo
               (aa/enable-laser (aa/laser-spec :laser-class "1" :use "mystery")))))

(deftest test-hazardous-class-without-interlock-refused
  (is (thrown? clojure.lang.ExceptionInfo
               (aa/enable-laser (aa/laser-spec :laser-class "4" :use "soldering"
                                               :enclosure-interlock false)))))

(deftest test-hazardous-class-with-interlock-but-no-attestation-refused
  (is (thrown? clojure.lang.ExceptionInfo
               (aa/enable-laser (aa/laser-spec :laser-class "3B" :use "trimming"
                                               :enclosure-interlock true)))))

(deftest test-hazardous-class-fully-attested-energises
  (is (nil? (aa/enable-laser (aa/laser-spec :laser-class "4" :use "soldering"
                                            :enclosure-interlock true
                                            :safety-attestation-ref "attest:noroshi-lsm-001"))))) ; no raise

;; ── IEC 60825 class independent recompute (ground truth, not a self-report) ──
(deftest test-classify-laser-class-boundaries
  (is (= "1" (aa/classify-laser-class 0.1 650.0)))
  (is (= "2" (aa/classify-laser-class 0.8 650.0)))            ; visible, Class-2 range
  (is (= "3R" (aa/classify-laser-class 0.8 1550.0)))          ; SAME power, non-visible → no Class 2
  (is (= "3R" (aa/classify-laser-class 3.0 650.0)))
  (is (= "3B" (aa/classify-laser-class 100.0 650.0)))
  (is (= "4" (aa/classify-laser-class 1000.0 650.0))))

(deftest test-classify-laser-class-visibility-boundary
  (is (= "2" (aa/classify-laser-class 0.9 400.0)))
  (is (= "2" (aa/classify-laser-class 0.9 700.0)))
  (is (= "3R" (aa/classify-laser-class 0.9 399.0)))
  (is (= "3R" (aa/classify-laser-class 0.9 701.0))))

;; ── active alignment converges to the unknown peak ───────────────────────────
(deftest test-align-finds-true-peak
  (let [model (aa/coupler-model :opt-x-um 2.3 :opt-y-um -1.7)
        res (aa/align model (aa/laser-spec))]
    (is (:converged res))
    (is (approx? (:x-um res) (:opt-x-um model) 0.1))
    (is (approx? (:y-um res) (:opt-y-um model) 0.1))))

(deftest test-aligned-coupling-near-peak-efficiency
  (let [model (aa/coupler-model :peak-efficiency 0.80)
        res (aa/align model (aa/laser-spec))]
    (is (approx? (:efficiency res) 0.80 0.01))
    (is (< (:loss-db res) 1.0))))                       ; < 1 dB insertion loss at the peak

(deftest test-align-refuses-before-probing-when-use-forbidden
  (is (thrown? clojure.lang.ExceptionInfo
               (aa/align (aa/coupler-model) (aa/laser-spec :use "weapon")))))

(deftest test-align-handles-offset-peak-far-from-start
  (let [model (aa/coupler-model :opt-x-um -6.0 :opt-y-um 5.5 :mode-radius-um 6.0)
        res (aa/align model (aa/laser-spec) :start-x-um 0.0 :start-y-um 0.0)]
    (is (approx? (:x-um res) -6.0 0.15))
    (is (approx? (:y-um res) 5.5 0.15))))

(deftest test-align-budget-exhaustion-is-bounded-and-flagged
  ;; A tiny probe budget cannot converge → terminates, not-converged, within the budget.
  (let [model (aa/coupler-model :opt-x-um 8.0 :opt-y-um -8.0)
        res (aa/align model (aa/laser-spec) :step-um 4.0 :tol-um 1e-6 :max-probes 12)]
    ;; Termination is bounded: a started iteration may add up to 4 probes after the budget check.
    (is (<= (:probes res) (+ 12 4)))
    (is (= (:converged res) false))))

(deftest test-loss-db-is-monotonic-in-efficiency
  (is (< (aa/loss-db 0.9) (aa/loss-db 0.5) (aa/loss-db 0.1))))

(deftest test-loss-db-handles-zero-efficiency-without-crash
  (let [v (aa/loss-db 0.0)]
    (is (and (not (Double/isInfinite (double v))) (not (Double/isNaN (double v)))
             (> v 100.0)))))                            ; clamped, large, finite

;; ── two-stage coarse acquisition + fine refinement ───────────────────────────
(deftest test-two-stage-acquires-a-far-narrow-peak-that-single-stage-misses
  (let [model (aa/coupler-model :opt-x-um 60.0 :opt-y-um -50.0 :mode-radius-um 2.0)
        single (aa/align model (aa/laser-spec))]
    (is (< (:efficiency single) 0.01))                  ; gradient underflow → single-stage stalls at origin
    (let [two (aa/align-two-stage model (aa/laser-spec))]
      (is (:converged two))
      (is (approx? (:efficiency two) (:peak-efficiency model) 0.01))
      (is (approx? (:x-um two) 60.0 0.1))
      (is (approx? (:y-um two) -50.0 0.1)))))

(deftest test-coarse-scan-lands-inside-the-lobe
  (let [model (aa/coupler-model :opt-x-um 30.0 :opt-y-um 20.0 :mode-radius-um 3.0)
        [x y eff probes] (aa/coarse-scan model (aa/laser-spec) :span-um 40.0)]
    (is (> eff 0.0))                                    ; acquired some coupling
    (is (> probes 1))))

(deftest test-coarse-scan-respects-laser-safety-before-probing
  (is (thrown? clojure.lang.ExceptionInfo
               (aa/coarse-scan (aa/coupler-model) (aa/laser-spec :use "weapon")))))

(deftest test-coarse-scan-rejects-non-positive-span-or-step
  (is (thrown? clojure.lang.ExceptionInfo
               (aa/coarse-scan (aa/coupler-model) (aa/laser-spec) :span-um 0.0)))
  (is (thrown? clojure.lang.ExceptionInfo
               (aa/coarse-scan (aa/coupler-model) (aa/laser-spec) :step-um -1.0))))

(deftest test-two-stage-still-converges-on-an-easy-peak
  (let [model (aa/coupler-model :opt-x-um 2.3 :opt-y-um -1.7)
        res (aa/align-two-stage model (aa/laser-spec))]
    (is (:converged res))
    (is (approx? (:efficiency res) (:peak-efficiency model) 0.01))))

;; ── spiral acquisition (expanding-square, early-stop) ────────────────────────
(deftest test-spiral-uses-far-fewer-probes-than-raster
  (let [model (aa/coupler-model :opt-x-um 10.0 :opt-y-um 8.0 :mode-radius-um 3.0)
        [_ _ _ sp] (aa/spiral-search model (aa/laser-spec))
        [_ _ _ rp] (aa/coarse-scan model (aa/laser-spec))]
    (is (< sp rp))))                                    ; early-stop on first signal beats exhaustive raster

(deftest test-spiral-respects-laser-safety
  (is (thrown? clojure.lang.ExceptionInfo
               (aa/spiral-search (aa/coupler-model) (aa/laser-spec :use "weapon")))))

(deftest test-spiral-rejects-non-positive-span-or-step
  (is (thrown? clojure.lang.ExceptionInfo
               (aa/spiral-search (aa/coupler-model) (aa/laser-spec) :span-um -1.0)))
  (is (thrown? clojure.lang.ExceptionInfo
               (aa/spiral-search (aa/coupler-model) (aa/laser-spec) :step-um 0.0))))

(deftest test-two-stage-spiral-converges-with-fewer-probes-than-raster
  (let [model (aa/coupler-model :opt-x-um 10.0 :opt-y-um 8.0 :mode-radius-um 3.0)
        spiral (aa/align-two-stage model (aa/laser-spec) :acquire "spiral")
        raster (aa/align-two-stage model (aa/laser-spec) :acquire "raster")]
    (is (:converged spiral))
    (is (approx? (:efficiency spiral) (:peak-efficiency model) 0.01))
    (is (< (:probes spiral) (:probes raster)))))

(deftest test-two-stage-spiral-still-acquires-a-far-narrow-peak
  (let [model (aa/coupler-model :opt-x-um 60.0 :opt-y-um -50.0 :mode-radius-um 2.0)
        res (aa/align-two-stage model (aa/laser-spec) :acquire "spiral")]
    (is (:converged res))
    (is (approx? (:efficiency res) (:peak-efficiency model) 0.01))))

(deftest test-align-two-stage-rejects-bad-acquire-mode
  (is (thrown? clojure.lang.ExceptionInfo
               (aa/align-two-stage (aa/coupler-model) (aa/laser-spec) :acquire "zigzag"))))

(deftest test-report-renders
  (let [txt (aa/report)]
    (is (.contains txt "active alignment"))
    (is (.contains txt "IEC 60825"))))

;; ── BYTE-PARITY + Hooke-Jeeves trajectory parity: the rendered report matches
;;    python3 exactly (same converged coords, same objective, same probe count) ──
(deftest test-report-byte-parity-headline
  (let [txt (aa/report)]
    (is (.contains txt "found offset     : (2.3125, -1.6875) µm  in 50 probes (converged)"))
    (is (.contains txt "coupling         : η = 0.79999  → insertion loss 0.9692 dB"))
    (is (.contains txt "true peak offset : (2.3, -1.7) µm"))))

#?(:clj
   (defn -main [& _]
     (let [{:keys [fail error]} (run-tests 'noroshi.methods.test-active-alignment)]
       (System/exit (if (zero? (+ fail error)) 0 1)))))
