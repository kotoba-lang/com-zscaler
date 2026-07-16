#!/usr/bin/env bb
;; uzu 渦 — colony self-reflection (digest) tests.
;; Run: bb --classpath 20-actors 20-actors/uzu/methods/test_digest.cljc
(ns uzu.methods.test-digest
  (:require [uzu.methods.uzu-edn :as ue]
            [uzu.methods.metabolism :as metab]
            [uzu.methods.measure :as measure]
            [uzu.methods.digest :as dg]
            [clojure.string :as str]
            [clojure.test :refer [deftest is run-tests]]))

(def seed (ue/classify (ue/load-edn "20-actors/uzu/kotoba/seed.edn")))
(def lives (mapv #(metab/live % (:tape seed)) (:organisms seed)))
(def field (measure/field {:flows (:flows seed) :edges (:edges seed)}))
(def d (dg/colony lives field))

(deftest reflects-survival
  (is (= 3 (:n d)))
  (is (= 1 (:n-alive d)) "exactly kurage self-maintains on the shipped seed")
  (is (= 2 (:n-dead d)))
  (is (< (Math/abs (- 0.333 (:survival-rate d))) 0.01)))

(deftest fittest-is-the-survivor
  (is (= "kurage" (get-in d [:fittest :id])) "the best-fitted meaning is the survivor")
  (is (true? (get-in d [:fittest :alive?]))))

(deftest energy-economy-is-coherent
  (let [e (:energy d)]
    (is (pos? (:drawn e)) "the colony drew energy from the world")
    (is (pos? (:spent e)) "and spent energy living")
    (is (< (Math/abs (- (:net e) (- (:drawn e) (:spent e)))) 0.01) "net = drawn − spent")))

(deftest field-reflection-physical-only-dissipation
  ;; only physical flows have a waste-heat reading (G2/G3 — meaning/economy aren't joules)
  (is (pos? (get-in d [:field :physical-waste-W])) "physical flows dissipate to heat")
  (is (true? (:closed? (:field d)))))

(deftest report-is-narration-free-text
  (let [r (dg/report d)]
    (is (str/includes? r "self-maintained"))
    (is (str/includes? r "kurage"))
    (is (string? r))))

(deftest datoms-are-colony-level-eavt
  (let [ds (dg/datoms d)
        attrs (set (map #(nth % 2) ds))]
    (is (every? #(= 4 (count %)) ds))
    (is (every? #(= "uzu:digest/colony" (second %)) ds) "colony-level only")
    (is (contains? attrs ":uzu.digest/survival-rate"))
    (is (contains? attrs ":uzu.digest/energy-net"))
    (is (contains? attrs ":uzu.digest/physical-waste-W"))))

(let [{:keys [fail error]} (run-tests 'uzu.methods.test-digest)]
  (when (pos? (+ fail error)) (System/exit 1)))
