#!/usr/bin/env bb
;; uzu 渦 — measurement tests (incl. the never-equate-units invariant).
;; Run: bb --classpath 20-actors 20-actors/uzu/methods/test_measure.cljc
(ns uzu.methods.test-measure
  (:require [uzu.methods.uzu-edn :as ue]
            [uzu.methods.measure :as measure]
            [clojure.test :refer [deftest is run-tests]]))

(def seed (ue/classify (ue/load-edn "20-actors/uzu/kotoba/seed.edn")))
(def flows (:flows seed))
(def edges (:edges seed))
(defn flow [id] (first (filter #(= id (:id %)) flows)))

(deftest four-incommensurable-classes
  (is (= #{:physical :economic :informational :experiential} (set (keys measure/unit-classes)))))

;; ── THE invariant: never sum across unit classes ─────────────────────────────
(deftest totals-only-within-a-class
  (let [t (measure/totals-by-class flows)]
    (is (= "W" (get-in t [:physical :unit])))
    (is (= "USD/yr" (get-in t [:economic :unit])))
    (is (= "bit/s" (get-in t [:informational :unit])))
    (is (= "index" (get-in t [:experiential :unit])))
    ;; there is deliberately NO grand total key — cross-class summation is undefined
    (is (every? (fn [[_ v]] (and (:unit v) (:total v))) t))
    (is (not (contains? t :total)) "no cross-class grand total exists")))

(deftest experiential-has-no-physical-conversion
  ;; refusing to convert meaning → joules is the design (no J/attention)
  (let [vm (measure/visual-magnitude (flow "attention"))]
    (is (= :experiential (:axis vm)))
    (is (nil? (:log10-W vm)) "no physical magnitude for an experiential flow"))
  (is (nil? (get-in measure/reference-conversions [:experiential->physical :factor]))
      "the experiential→physical conversion factor is explicitly nil"))

(deftest cross-class-visual-is-reference-only
  ;; economic & informational get a visual physical-equivalent, FLAGGED reference-only
  (is (true? (:reference-only (measure/visual-magnitude (flow "gdp")))))
  (is (true? (:reference-only (measure/visual-magnitude (flow "internet")))))
  (is (not (:reference-only (measure/visual-magnitude (flow "primary")))) "physical is native, not ref-only"))

(deftest dissipation-only-for-physical-with-efficiency
  (is (some? (measure/dissipation (flow "primary"))) "primary discloses efficiency")
  (is (nil? (measure/dissipation (flow "gdp"))) "economic has no thermodynamic dissipation")
  (let [d (measure/dissipation (flow "primary"))]
    (is (< (:useful-W d) (:waste-W d)) "primary energy at 35% eff ⇒ most becomes waste heat")))

(deftest circulation-is-a-closed-loop
  (is (true? (measure/circulation-closed? flows edges))
      "every measured flow has an out-edge ⇒ the dissipative system circulates"))

(deftest datoms-keep-native-unit-and-flag-reference
  (let [ds (measure/datoms flows)
        gdp-ds (filter #(= "uzu:flow/gdp" (second %)) ds)
        attrs (into {} (map (fn [d] [(nth d 2) (nth d 3)]) gdp-ds))]
    (is (= "USD/yr" (get attrs ":uzu.flow/unit")) "gdp keeps its native economic unit")
    (is (= true (get attrs ":uzu.flow/reference-only")) "and is flagged reference-only for the visual")))

(deftest field-assembles
  (let [f (measure/field {:flows flows :edges edges})]
    (is (= 11 (count (:flows f))))
    (is (true? (:closed? f)))
    (is (= 4 (count (:totals f))))
    (is (pos? (count (:visual f))))))

(let [{:keys [fail error]} (run-tests 'uzu.methods.test-measure)]
  (when (pos? (+ fail error)) (System/exit 1)))
