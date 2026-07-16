(ns silicon.methods.test-fab-flow
  "Tests for silicon.methods.fab-flow."
  (:require [clojure.test :refer [deftest is testing]]
            [silicon.methods.fab-flow :as f]))

(deftest test-route-and-dual-use
  (is (= 8 (count f/default-route)))
  (is (f/force-review-required? "litho"))
  (is (f/force-review-required? "implant"))
  (is (not (f/force-review-required? "cmp")))
  (is (not (f/force-review-required? "test"))))

(deftest test-reference-lot-runs-end-to-end
  (let [rec (f/run-lot f/reference-lot f/default-route f/reference-recipe
                       :silen-force-attest "ok: ternary-PE tile, civilian inference ASIC")]
    (is (= "LOT-IWAKURA-PE-0001" (:lot-id rec)))
    (is (= 8 (count (:steps rec))))
    (is (= f/default-route (map :step (:steps rec))))
    ;; every step produced a measured map
    (is (every? (comp map? :measured) (:steps rec)))
    ;; yield in (0,1], some good die, some packaged units
    (is (< 0.0 (:yield rec)))
    (is (<= (:yield rec) 1.0))
    (is (pos? (:good-die rec)))
    (is (<= (:packaged-units rec) (:good-die rec)))))

(deftest test-litho-bossung-symmetry
  ;; defocus is quadratic → +focus and -focus give the same CD
  (let [pos (f/run-lot f/reference-lot ["litho"]
                       (assoc-in f/reference-recipe ["litho" :focus-nm] 30.0)
                       :silen-force-attest "ok")
        neg (f/run-lot f/reference-lot ["litho"]
                       (assoc-in f/reference-recipe ["litho" :focus-nm] -30.0)
                       :silen-force-attest "ok")]
    (is (= (get-in (first (:steps pos)) [:measured :cd-nm])
           (get-in (first (:steps neg)) [:measured :cd-nm])))))

(deftest test-better-recipe-higher-yield
  ;; tighter litho focus (less defocus) → fewer defects → higher yield
  (let [bad (f/run-lot f/reference-lot f/default-route
                       (assoc-in f/reference-recipe ["litho" :focus-nm] 120.0)
                       :silen-force-attest "ok")
        good (f/run-lot f/reference-lot f/default-route
                        (assoc-in f/reference-recipe ["litho" :focus-nm] 0.0)
                        :silen-force-attest "ok")]
    (is (> (:yield good) (:yield bad)))
    (is (>= (:defect-density bad) (:defect-density good)))))

(deftest test-defect-density-monotonic
  ;; cumulative defect density only ever grows across the route
  (let [rec (f/run-lot f/reference-lot f/default-route f/reference-recipe
                       :silen-force-attest "ok")
        adds (map :added-defects (:steps rec))]
    (is (every? #(>= % 0.0) adds))
    ;; metrology + test + packaging are non-actuating → 0 added defects
    (let [by (zipmap (map :step (:steps rec)) adds)]
      (is (zero? (get by "metrology")))
      (is (zero? (get by "test")))
      (is (zero? (get by "packaging"))))))

(deftest test-force-review-gate
  ;; a route with litho/implant requires attestation
  (is (thrown? clojure.lang.ExceptionInfo
               (f/run-lot f/reference-lot ["litho" "etch"] f/reference-recipe)))
  (is (thrown? clojure.lang.ExceptionInfo
               (f/run-lot f/reference-lot ["implant"] f/reference-recipe
                          :silen-force-attest "  ")))
  ;; a non-dual-use route needs no attestation
  (is (some? (f/run-lot f/reference-lot ["deposition" "etch" "cmp"]
                        f/reference-recipe))))

(deftest test-unknown-step-raises
  (is (thrown? clojure.lang.ExceptionInfo
               (f/run-lot f/reference-lot ["frobnicate"] f/reference-recipe))))

(deftest test-dispatch-is-council-gated
  ;; G11 — real actuation is structurally refused at R0
  (let [rec (f/run-lot f/reference-lot f/default-route f/reference-recipe
                       :silen-force-attest "ok")]
    (is (thrown-with-msg? clojure.lang.ExceptionInfo #"Council-gated"
                          (f/dispatch-equipment! rec)))))

(deftest test-etch-consumes-deposited-film
  ;; deposition lays film; etch removes it; remaining is reported and ≥0
  (let [rec (f/run-lot f/reference-lot ["deposition" "etch"] f/reference-recipe)
        dep (first (:steps rec))
        etch (second (:steps rec))]
    (is (pos? (get-in dep [:measured :thickness-nm])))
    (is (>= (get-in etch [:measured :remaining-film-nm]) 0.0))))
