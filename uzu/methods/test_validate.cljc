#!/usr/bin/env bb
;; uzu 渦 — seed-integrity validator tests.
;; Run: bb --classpath 20-actors 20-actors/uzu/methods/test_validate.cljc
(ns uzu.methods.test-validate
  (:require [uzu.methods.uzu-edn :as ue]
            [uzu.methods.validate :as v]
            [clojure.edn :as edn]
            [clojure.test :refer [deftest is run-tests]]))

(def ont (ue/ontology-map (edn/read-string (slurp "20-actors/uzu/kotoba/ontology.uzu.edn"))))
(def seed (ue/classify (ue/load-edn "20-actors/uzu/kotoba/seed.edn")))

(deftest shipped-seed-is-valid
  (let [r (v/validate seed ont)]
    (is (true? (:ok r)) (str "shipped seed must validate clean; errors=" (:errors r)))
    (is (empty? (:errors r)))
    (is (empty? (:warnings r)) (str "no warnings expected; got " (:warnings r)))
    (is (= 11 (get-in r [:stats :flows])))
    (is (= 4 (get-in r [:stats :classes])))))

(deftest catches-out-of-range-signal
  (let [bad (update seed :tape conj {:type :world-step :step 99 :regime :benign
                                     :signal {:nutrient 1.7 :threat 0.1}})]
    (is (false? (:ok (v/validate bad ont))))))

(deftest catches-bad-regime
  (let [bad (update seed :tape conj {:type :world-step :step 99 :regime :paradise
                                     :signal {:nutrient 0.5 :threat 0.1}})]
    (is (false? (:ok (v/validate bad ont))))))

(deftest catches-mixed-units-within-a-class
  ;; the I2 invariant: a class that mixes units makes totals-by-class meaningless
  (let [bad (update seed :flows conj {:type :flow :id "rogue" :label "rogue"
                                      :class :physical :magnitude 1.0 :unit "kW"})]
    (is (false? (:ok (v/validate bad ont))))))

(deftest catches-dangling-circulation-edge
  (let [bad (update seed :edges conj {:type :circulation :from "nowhere" :to "gdp" :cross-class true})]
    (is (false? (:ok (v/validate bad ont))))))

(deftest catches-wrong-cross-class-flag
  ;; gdp→fx is economic→economic (within-class); flagging it cross-class is an error
  (let [bad (update seed :edges conj {:type :circulation :from "gdp" :to "fx" :cross-class true})]
    (is (false? (:ok (v/validate bad ont))))))

(deftest catches-nonpositive-magnitude
  (let [bad (update seed :flows conj {:type :flow :id "zero" :label "zero"
                                      :class :economic :magnitude 0.0 :unit "USD/yr"})]
    (is (false? (:ok (v/validate bad ont))))))

(let [{:keys [fail error]} (run-tests 'uzu.methods.test-validate)]
  (when (pos? (+ fail error)) (System/exit 1)))
