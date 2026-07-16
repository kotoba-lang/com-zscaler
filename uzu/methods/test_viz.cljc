#!/usr/bin/env bb
;; uzu 渦 — visualization tests (data-driven; JSON encoding; honest unit boundary).
;; Run: bb --classpath 20-actors 20-actors/uzu/methods/test_viz.cljc
(ns uzu.methods.test-viz
  (:require [uzu.methods.uzu-edn :as ue]
            [uzu.methods.metabolism :as metab]
            [uzu.methods.viz :as viz]
            [clojure.string :as str]
            [clojure.test :refer [deftest is run-tests]]))

(def seed (ue/classify (ue/load-edn "20-actors/uzu/kotoba/seed.edn")))
(def lives (mapv #(metab/live % (:tape seed)) (:organisms seed)))
(def pl (viz/payload lives (:flows seed) (:edges seed)))

(deftest json-encodes-primitives
  (is (= "true" (viz/json true)))
  (is (= "null" (viz/json nil)))
  (is (= "\"physical\"" (viz/json :physical)) "keyword ⇒ bare-name string")
  (is (= "[1,2]" (viz/json [1 2]))))

(deftest json-munges-hyphen-keys-for-js
  ;; JS reads dotted underscore keys, so map keys must be munged hyphen→underscore
  (is (str/includes? (viz/json {:log10-W 3.0}) "\"log10_W\""))
  (is (not (str/includes? (viz/json {:log10-W 3.0}) "log10-W"))))

(deftest payload-shape
  (is (= 11 (count (:flows pl))))
  (is (= 15 (count (:edges pl))))
  (is (= 3 (count (:lives pl))))
  (is (true? (:closed pl)))
  (is (= 4 (count (:totals pl)))))

(deftest payload-carries-organism-trajectories
  (let [k (first (filter #(= "kurage" (:id %)) (:lives pl)))]
    (is (true? (:alive k)))
    (is (= -1 (:death k)) "survivor has no death index")
    (is (= 13 (count (:series k))) "born-energy + 12 beats"))
  (let [m (first (filter #(= "meial" (:id %)) (:lives pl)))]
    (is (false? (:alive m)))
    (is (>= (:death m) 0) "a dead organism carries the beat index where it died")))

(deftest html-is-self-contained-and-data-driven
  (let [h (viz/html pl)]
    (is (str/starts-with? h "<!doctype html>"))
    (is (str/includes? h "const D = {") "embeds the data inline")
    (is (str/includes? h "log10_W") "embeds munged visual magnitudes")
    (is (str/includes? h "philosophy soup") "states the honest unit boundary")
    (is (not (str/includes? h "</script><script src")) "no external scripts ⇒ self-contained")))

(let [{:keys [fail error]} (run-tests 'uzu.methods.test-viz)]
  (when (pos? (+ fail error)) (System/exit 1)))
