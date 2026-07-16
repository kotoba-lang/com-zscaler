(ns kamado.methods.test-kamado
  "Tests for kamado 竈 methods (ADR-2606051500).
  1:1 Clojure port of `methods/test_kamado.py` (clojure.test). Every assertion satisfiable
  with the analyze closure is ported — especially the fossil-virgin-crude-UNREPRESENTABLE
  (G1) + net≤0 D3 gate tests.

  Run from $W:
    bb --classpath 20-actors -e \"(require 'kamado.methods.test-kamado 'clojure.test) \\
       (clojure.test/run-tests 'kamado.methods.test-kamado)\""
  (:require [clojure.test :refer [deftest is run-tests]]
            [kamado.methods.analyze :as analyze]
            [kamado.methods.carbon-balance :as cb]
            [kamado.methods.feedstock-guard :as fg
             :refer [ALLOWED-FEEDSTOCK screen-feedstock screen-intervention]]))

(def seed-path "20-actors/kamado/data/seed-refinery-graph.kotoba.edn")

#?(:clj
   (defn- load* []
     (analyze/classify (analyze/load-edn seed-path))))

;; ── carbon_balance: the empirical thesis ──────────────────────────────────────
(deftest test-fossil-baseline-is-strongly-positive-multigenerational
  ;; A fossil→combusted pathway is ~+3.5 tCO2e/t — genuinely multi-generational.
  (let [base (cb/balance (nth cb/PATHWAYS 0))]
    (is (> (get base "net") 3.0))
    (is (not (get base "passes_d3")))))

(deftest test-robotics-apc-cannot-close-the-fossil-gap
  ;; Full robotic APC on the SAME fossil pathway barely moves the needle (process only).
  (let [base (cb/balance (nth cb/PATHWAYS 0))
        apc (cb/balance (nth cb/PATHWAYS 1))
        cut (- (get base "net") (get apc "net"))]
    (is (> cut 0))                       ;; APC helps a little
    (is (< cut 0.20))                    ;; ...but only the ~0.4 process slice, ≤30% of it
    (is (not (get apc "passes_d3")))))   ;; still nowhere near net≤0

(deftest test-closed-loop-pathways-pass-d3
  ;; Changing the feedstock to closed-loop carbon is the ONLY route to net≤0.
  (let [biogenic (cb/balance (cb/->pathway "b" ":biogenic" ":hikari-renewable" ":combusted-fuel" :apc true))
        efuel (cb/balance (cb/->pathway "e" ":captured-co2" ":hikari-renewable" ":combusted-fuel" :apc true))
        locked (cb/balance (cb/->pathway "p" ":biogenic" ":hikari-renewable" ":durable-material" :apc true))]
    (is (and (get biogenic "passes_d3") (get efuel "passes_d3") (get locked "passes_d3")))
    (is (< (get locked "net") 0))))      ;; carbon locked into a durable material is net-negative

;; ── feedstock_guard: G1 / G3 structural invariants ────────────────────────────
(deftest test-g1-fossil-virgin-crude-is-not-representable
  (is (thrown-with-msg? clojure.lang.ExceptionInfo #"G1 violation"
                        (screen-feedstock ":fossil-virgin-crude" "test"))))

(deftest test-g1-allows-only-closed-loop-carbon
  (doseq [f ALLOWED-FEEDSTOCK]
    (is (= (screen-feedstock f) f))))

(deftest test-g3-refuses-fossil-life-extension
  (doseq [bad [":expand" ":restart-fossil" ":revamp-throughput"]]
    (is (thrown-with-msg? clojure.lang.ExceptionInfo #"G3 violation"
                          (screen-intervention bad "test"))))
  (doseq [ok [":decommission" ":convert" ":remediate" ":monitor"]]
    (is (= (screen-intervention ok) ok))))

(deftest test-origin-credit-rejects-fossil-origin-silent-zero
  ;; Fossil origin gets ZERO credit (the stock→flow harm); closed-loop gets the draw-down.
  (is (= (cb/origin-credit ":fossil-virgin-crude") 0.0))
  (is (= (cb/origin-credit ":biogenic") (- cb/C-PROD)))
  (is (= (cb/origin-credit ":captured-co2") (- cb/C-PROD))))

;; ── analyze: the seed graph is charter-clean end to end ────────────────────────
#?(:clj
   (deftest test-seed-parses-and-classifies
     (let [[refineries units _outages decoms synths] (load*)]
       (is (and (seq refineries) (seq units) (seq decoms) (seq synths)))
       (is (contains? refineries "rf.jp.negishi"))
       (is (contains? synths "syn.bio-polymer")))))

#?(:clj
   (deftest test-seed-every-synthesis-feedstock-is-representable
     (let [[_ _ _ _ synths] (load*)]
       (doseq [[sid s] synths]
         (is (some #(= % (get s ":synthesis/feedstock-class")) ALLOWED-FEEDSTOCK) sid)))))

#?(:clj
   (deftest test-analyze-runs-and-all-synthesis-pass-d3
     (let [[refineries units outages decoms synths] (load*)
           a (analyze/analyze refineries units outages decoms synths)]
       (is (= (get a "syn_pass") (count synths)))
       (is (true? (get a "decom_keyless"))))))

#?(:clj
   (deftest test-analyze-raises-if-seed-smuggles-a-fossil-synthesis
     (let [[refineries units outages decoms synths] (load*)
           synths (assoc synths "syn.bad" {":synthesis/id" "syn.bad"
                                           ":synthesis/feedstock-class" ":fossil-virgin-crude"})]
       (is (thrown-with-msg? clojure.lang.ExceptionInfo #"G1 violation"
                             (analyze/analyze refineries units outages decoms synths))))))

#?(:clj (defn -main [& _] (run-tests 'kamado.methods.test-kamado)))
