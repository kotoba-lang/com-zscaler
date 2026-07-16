#!/usr/bin/env bb
;; tsuchifumi 土踏み — visualization generator tests (real data, self-contained HTML).
;; Run: bb --classpath 20-actors 20-actors/tsuchifumi/methods/test_viz.cljc
(ns tsuchifumi.methods.test-viz
  (:require [tsuchifumi.methods.tsuchifumi-edn :as te]
            [tsuchifumi.methods.viz :as viz]
            [clojure.string :as str]
            [clojure.test :refer [deftest is run-tests]]))

(def seed (te/load-seed "20-actors/tsuchifumi/kotoba/seed.edn"))
(defn- data [] (viz/build-data (:regions seed) (:evidence seed) (:drivers seed)))

(deftest build-data-shape
  (let [d (data)]
    (is (= #{"neglect" "baseline" "relief"} (set (keys (get d "scenarios")))))
    (is (= (count (:drivers seed)) (count (get d "leverage"))))
    (is (= (count (:regions seed)) (count (get d "relief_gap"))))
    (is (pos? (get-in d ["summary" "relief_dividend_p50"])))))

(deftest json-encoder-roundtrip-basic
  (is (= "{\"a\":1,\"b\":[2,3]}" (viz/->json {"a" 1 "b" [2 3]})))
  (is (= "true" (viz/->json true)))
  (is (str/includes? (viz/->json {"k" "緑地"}) "緑地")))

(deftest html-self-contained
  (let [html (viz/render-html (data))]
    (is (str/includes? html "<!doctype html>"))
    (is (str/includes? html "const D=") "data is embedded inline (no network)")
    (is (str/includes? html "未確立") "the epistemic-honesty banner is present (G2/G6)")
    (is (not (str/includes? html "http://")) "no external http resource")
    (is (not (str/includes? html "https://")) "no external https resource")
    (is (not (str/includes? html "<script src")) "no external script")))

(let [{:keys [fail error]} (run-tests 'tsuchifumi.methods.test-viz)]
  (when (pos? (+ fail error)) (System/exit 1)))
