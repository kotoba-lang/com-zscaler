(ns noroshi.methods.test-pic-layout
  "noroshi 烽 — photonic-IC layout generator tests (ADR-2606051600). 1:1 Clojure port of
  methods/test_pic_layout.py. Every assertion ported, PLUS:

    G1/G8 open-EDA — try-build-gds is the honest gated stub (never a proprietary EDA tool);
       the report frames open-EDA / no-NDA-PDK.
    G3/G10 — civilian layout arithmetic; byte-identical to python3.

  The layout→budget loop is exercised through link-budget/compute (the closed loop)."
  (:require [clojure.test :refer [deftest is testing run-tests]]
            [noroshi.methods.link-budget :as lb]
            [noroshi.methods.pic-layout :as pl]))

(defn route-count [plan] (count (filter #(= "route" (:op %)) (:ops plan))))
(defn routes [plan] (filterv #(= "route" (:op %)) (:ops plan)))

;; ── ported assertions ───────────────────────────────────────────────────
(deftest test-transmitter-plan-has-expected-components
  (let [plan (pl/transmitter-plan)]
    (is (= #{"laser0" "mzm0" "gc0"} (set (:components plan))))
    (is (= 2 (route-count plan)))))

(deftest test-total-waveguide-is-sum-of-routes
  (let [plan (pl/transmitter-plan "noroshi-tx-pic" 1500.0)]
    (is (= (+ 200.0 1500.0) (:total-waveguide-um plan)))))

(deftest test-layout-feeds-link-budget-and-closes
  (let [plan (pl/transmitter-plan)
        budget (lb/compute (pl/plan-to-link-design plan))]
    (is (:closes budget))))

(deftest test-longer-routing-lowers-margin
  (let [short (lb/compute (pl/plan-to-link-design (pl/transmitter-plan "noroshi-tx-pic" 500.0)))
        long (lb/compute (pl/plan-to-link-design (pl/transmitter-plan "noroshi-tx-pic" 5000.0)))]
    (is (< (:margin-db long) (:margin-db short)))))

(deftest test-gds-build-is-gated-or-built
  (let [plan (pl/transmitter-plan)
        res (pl/try-build-gds plan)]
    (is (contains? #{true false} (:built res)))
    (when-not (:built res)
      (is (or (.contains (:reason res) "gated") (.contains (:reason res) "not available"))))))

(deftest test-report-renders-open-eda-framing
  (let [txt (pl/report)]
    (is (.contains txt "ModelOp"))
    (is (or (.contains txt "open-EDA") (.contains txt "gdsfactory")))
    (is (or (.contains txt "G1")
            (.contains (.toLowerCase txt) "no proprietary eda")
            (.contains txt "NDA")))))

;; ── coverage: guards, custom base, route ports ───────────────────────────
(deftest test-non-positive-route-length-rejected
  (is (thrown? clojure.lang.ExceptionInfo (pl/transmitter-plan "noroshi-tx-pic" 0.0)))
  (is (thrown? clojure.lang.ExceptionInfo (pl/transmitter-plan "noroshi-tx-pic" -100.0))))

(deftest test-plan-to-link-design-uses-custom-base-rx-waveguide
  (let [plan (pl/transmitter-plan)
        d (pl/plan-to-link-design plan (lb/link-design :rx-waveguide-cm 3.0))]
    (is (= 3.0 (:rx-waveguide-cm d)))
    (is (= (/ (:total-waveguide-um plan) 1e4) (:tx-waveguide-cm d)))))

(deftest test-routes-carry-port-pairs
  (let [plan (pl/transmitter-plan)
        rs (routes plan)]
    (is (every? #(= 2 (count (:ports %))) rs))
    (is (= ["mzm0.o" "gc0.i"] (:ports (last rs))))))

;; ── receiver PIC + full end-to-end link ──────────────────────────────────
(deftest test-receiver-plan-has-coupler-and-photodetector
  (let [rx (pl/receiver-plan)
        rs (routes rx)]
    (is (= #{"gc_in" "pd0"} (set (:components rx))))
    (is (and (= 1 (count rs)) (= ["gc_in.o" "pd0.i"] (:ports (first rs)))))))

(deftest test-receiver-plan-rejects-non-positive-route
  (is (thrown? clojure.lang.ExceptionInfo (pl/receiver-plan "noroshi-rx-pic" 0.0))))

(deftest test-full-link-design-uses-both-waveguides
  (let [tx (pl/transmitter-plan)
        rx (pl/receiver-plan)
        d (pl/full-link-design tx rx)]
    (is (= (/ (:total-waveguide-um tx) 1e4) (:tx-waveguide-cm d)))
    (is (= (/ (:total-waveguide-um rx) 1e4) (:rx-waveguide-cm d)))))

(deftest test-full-link-closes-and-longer-rx-lowers-margin
  (let [tx (pl/transmitter-plan)
        short (lb/compute (pl/full-link-design tx (pl/receiver-plan "noroshi-rx-pic" 500.0)))
        long (lb/compute (pl/full-link-design tx (pl/receiver-plan "noroshi-rx-pic" 8000.0)))]
    (is (:closes short))
    (is (< (:margin-db long) (:margin-db short)))))

(deftest test-report-mentions-receiver-and-end-to-end
  (let [txt (pl/report)]
    (is (.contains txt "receiver plan"))
    (is (.contains txt "end-to-end"))))

;; ── BYTE-PARITY: the rendered layout coords match python3 exactly ─────────
(deftest test-report-byte-parity-headline
  (let [txt (pl/report)]
    (is (.contains txt "total waveguide  : 1700 µm (0.170 cm)"))
    (is (.contains txt "place gc0 (grating_coupler) @ (1700,0) µm"))
    (is (.contains txt "both PIC waveguides → received 1.395 dBm, margin 13.395 dB → CLOSES"))))

#?(:clj
   (defn -main [& _]
     (let [{:keys [fail error]} (run-tests 'noroshi.methods.test-pic-layout)]
       (System/exit (if (zero? (+ fail error)) 0 1)))))
