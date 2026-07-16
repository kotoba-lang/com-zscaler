#!/usr/bin/env bb
;; kamado 竈 — structural validation of the carbon mass-balance + the net≤0 (D3) charter gate.
;; Run:  bb --classpath 20-actors 20-actors/kamado/methods/test_carbon_balance.cljc
(ns kamado.methods.test-carbon-balance
  "Structural validation of the carbon mass-balance and the net≤0 (D3) charter gate — the DEFINING
  invariant of kamado (closed-loop carbon only; fossil-virgin refining is refused, net carbon must
  be ≤ 0). The existing test_kamado checks two specific pathways' net values (the empirical
  thesis); this pins the BALANCE STRUCTURE across every pathway:
    - the mass-balance identity   net = origin + process + fate
    - the exact gate              passes_d3 ⟺ net ≤ D3-TOLERANCE
    - the closed-loop physics     a closed-loop feedstock's NEGATIVE origin credit offsets the
                                  downstream fate-release; fossil earns no credit so its fate stands
  so a regression in a component's sign, the summation, or the gate comparator is caught
  structurally — not only when it happens to cross a threshold on one hand-picked pathway."
  (:require [kamado.methods.carbon-balance :as cb]
            [clojure.test :refer [deftest is run-tests]]))

(defn- close? [a b] (< (Math/abs (- (double a) (double b))) 1e-9))

(deftest net-is-the-sum-of-origin-process-fate
  ;; conservation: the net carbon is exactly the sum of its three accounted components
  (doseq [p cb/PATHWAYS]
    (let [b (cb/balance p)]
      (is (close? (get b "net") (+ (get b "origin") (get b "process") (get b "fate")))
          (str "net ≠ origin+process+fate for feedstock " (:feedstock p))))))

(deftest passes-d3-iff-net-within-tolerance
  ;; the charter gate is exactly net ≤ D3-TOLERANCE (catches an off-by-sign / wrong comparator)
  (doseq [p cb/PATHWAYS]
    (let [b (cb/balance p)]
      (is (= (boolean (get b "passes_d3")) (<= (get b "net") cb/D3-TOLERANCE))
          (str "passes_d3 ≠ (net ≤ tolerance) for feedstock " (:feedstock p)
               " (net=" (get b "net") " tol=" cb/D3-TOLERANCE ")")))))

(deftest fossil-virgin-fails-closed-loop-passes
  ;; the defining charter result: a fossil-virgin pathway cannot close (net > tolerance), while a
  ;; closed-loop (biogenic / captured-CO2) pathway can (net ≤ tolerance)
  (let [by-feed (group-by :feedstock cb/PATHWAYS)
        net-of #(get (cb/balance %) "net")]
    (doseq [p (get by-feed ":fossil-virgin-crude")]
      (is (> (net-of p) cb/D3-TOLERANCE)
          "a fossil-virgin pathway cannot pass the net≤0 gate (no robotics/APC closes the gap)"))
    (is (some (fn [p] (<= (net-of p) cb/D3-TOLERANCE))
              (concat (get by-feed ":biogenic") (get by-feed ":captured-co2")))
        "at least one closed-loop (biogenic / captured-CO2) pathway passes the gate")))

(deftest closed-loop-origin-credit-offsets-the-fate-release
  ;; the physics of "closed loop": a closed-loop feedstock carries a ≤0 origin credit (carbon drawn
  ;; from biosphere/atmosphere) that offsets the downstream fate-release; fossil earns no credit
  (doseq [p cb/PATHWAYS]
    (let [b (cb/balance p)]
      (if (= (:feedstock p) ":fossil-virgin-crude")
        (is (close? (get b "origin") 0.0) "fossil-virgin earns no (0) origin credit")
        (is (<= (get b "origin") 0.0) "a closed-loop feedstock earns a ≤0 (credit) origin term")))))

(deftest sequestering-the-fate-drives-net-negative
  ;; the strongest closed-loop result: a biogenic feedstock whose fate is sequestered (fate ≈ 0)
  ;; yields NET-NEGATIVE carbon (drawdown), not merely neutral
  (when-let [seq-pathway (first (filter #(and (= (:feedstock %) ":biogenic")
                                              (close? (get (cb/balance %) "fate") 0.0))
                                        cb/PATHWAYS))]
    (let [b (cb/balance seq-pathway)]
      (is (< (get b "net") 0.0) "biogenic + sequestered fate → net-negative carbon (drawdown)")
      (is (true? (boolean (get b "passes_d3"))) "and it passes the D3 gate"))))

#?(:clj
   (when (= *file* (System/getProperty "babashka.file"))
     (let [{:keys [fail error]} (run-tests 'kamado.methods.test-carbon-balance)]
       (System/exit (if (zero? (+ fail error)) 0 1)))))
