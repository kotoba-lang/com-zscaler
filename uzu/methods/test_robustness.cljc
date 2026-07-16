#!/usr/bin/env bb
;; uzu 渦 — robustness / adversarial property tests (invariants over input grids).
;; Run: bb --classpath 20-actors 20-actors/uzu/methods/test_robustness.cljc
(ns uzu.methods.test-robustness
  (:require [uzu.methods.uzu-edn :as ue]
            [uzu.methods.model :as m]
            [uzu.methods.ledger :as l]
            [uzu.methods.metabolism :as metab]
            [uzu.methods.measure :as measure]
            [clojure.test :refer [deftest is run-tests]]))

(def seed (ue/classify (ue/load-edn "20-actors/uzu/kotoba/seed.edn")))
(def tape (:tape seed))
(def grid (for [n [0.0 0.25 0.5 0.75 1.0] t [0.0 0.25 0.5 0.75 1.0]] {:nutrient n :threat t}))
(defn finite? [x] (and (number? x) (Double/isFinite (double x))))

;; ── energy accounting is exact (the conserved ledger never leaks) ────────────
(deftest energy-accounting-is-exact
  (is (every? true?
        (for [e [0.5 5.0 50.0] a m/actions r m/regimes]
          (let [x (l/metabolize e l/default-costs a r)]
            (< (Math/abs (- (:e' x) (+ e (- (:gained x) (:spent x))))) 1e-9))))
      "e' = e + gained − spent for every (energy, action, regime)"))

(deftest spent-decomposes-cleanly
  (is (every? true?
        (for [a m/actions r m/regimes]
          (let [x (l/metabolize 100.0 l/default-costs a r)]
            (< (Math/abs (- (:spent x) (+ (:basal x) (:inference x) (:action-cost x) (:hazard x)))) 1e-9))))))

;; ── belief is always a proper distribution ───────────────────────────────────
(deftest belief-stays-normalized
  (is (every? (fn [obs]
                (let [q (m/update-belief m/uniform obs 0.15)
                      s (reduce + (vals q))]
                  (and (< (Math/abs (- 1.0 s)) 1e-9)
                       (every? #(<= 0.0 % 1.0) (vals q)))))
              grid)
      "posterior sums to 1 and every mass ∈ 0..1, for any observation"))

(deftest entropy-within-bounds
  (is (every? (fn [obs] (<= -1e-9 (m/entropy (m/update-belief m/uniform obs 0.15)) (+ 1e-9 (Math/log 4)))) grid)))

(deftest free-energy-is-finite-and-nonneg-ish
  ;; surprise is finite for every observation+belief (never NaN/Inf)
  (is (every? (fn [obs] (finite? (m/free-energy (m/update-belief m/uniform obs 0.15) obs 0.15))) grid)))

;; ── planning respects the energy veto, always ────────────────────────────────
(deftest choose-is-always-affordable
  (is (every? (fn [obs]
                (let [q (m/update-belief m/uniform obs 0.15)]
                  (every? (fn [e] (let [aff (l/affordable e l/default-costs)]
                                    (contains? (set aff) (m/choose q {:nutrient 1.0 :threat 0.0} aff))))
                          [0.1 1.6 3.0 10.0 100.0])))
              grid)
      "the chosen action is always within the affordable set (energy vetoes information)"))

;; ── lives are finite + deterministic for arbitrary meanings ──────────────────
(deftest fuzzed-meanings-produce-well-formed-lives
  ;; deterministic 'fuzz': a grid of preferences C — every resulting life is well-formed
  (is (every? (fn [C]
                (let [s (metab/live {:id "fz" :prefs C :temp 0.15 :energy0 12.0} tape)]
                  (and (boolean? (:alive? s))
                       (finite? (:energy s))
                       (seq (:history s))
                       (every? #(finite? (:energy %)) (:history s)))))
              (for [n [0.0 0.5 1.0] t [0.0 0.5 1.0]] {:nutrient n :threat t}))))

(deftest lives-are-deterministic
  (is (every? (fn [o] (= (:history (metab/live o tape)) (:history (metab/live o tape))))
              (:organisms seed))
      "no randomness / no wall clock ⇒ byte-identical histories"))

(deftest death-is-monotonic-for-all
  (is (every? (fn [o] (let [flags (map :alive? (:history (metab/live-epochs o tape 3)))]
                        (not-any? true? (drop-while true? flags))))
              (:organisms seed))))

;; ── measurement boundary properties hold over ALL flows ─────────────────────
(deftest totals-never-cross-class
  (is (every? (fn [subset]
                (let [t (measure/totals-by-class subset)]
                  (and (every? #{:physical :economic :informational :experiential} (keys t))
                       (not (contains? t :total)))))
              [(:flows seed) (take 3 (:flows seed)) (filter #(= :economic (:class %)) (:flows seed))])))

(deftest visual-magnitude-respects-the-unit-boundary
  (is (every? (fn [f]
                (let [vm (measure/visual-magnitude f)]
                  (case (:class f)
                    :experiential (nil? (:log10-W vm))                ;; no joules for meaning
                    :physical (not (:reference-only vm))              ;; native, not a conversion
                    (true? (:reference-only vm)))))                   ;; economic/informational = ref-only
              (:flows seed))))

(let [{:keys [fail error]} (run-tests 'uzu.methods.test-robustness)]
  (when (pos? (+ fail error)) (System/exit 1)))
