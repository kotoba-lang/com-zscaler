(ns meyasu.viz.test-build-viz
  "meyasu 目安 — dashboard viz builder tests. 1:1 port of viz/test_build_viz.py: the dashboard
  payload mirrors agent/handle-fuse (single source of truth) and is charter-clean — carries the G1
  buyer-transparency intent, routes attention cards to a planner (G4), and the rendered HTML inlines
  the payload (self-contained, file://, no external fetch)."
  (:require [clojure.test :refer [deftest is]]
            [clojure.string :as str]
            [clojure.java.io :as io]
            [cheshire.core :as json]
            [meyasu.viz.build-viz-data :as b]))

(def here (io/file "20-actors/meyasu/viz"))
(def seed (io/file "20-actors/meyasu/kotoba/seed.json"))

(defn- payload []
  (b/build-payload (get (json/parse-string (slurp seed)) "items")))

(defn- by-pid [] (into {} (map (fn [c] [(get c "productId") c]) (get (payload) "cards"))))

(deftest test-one-card-per-seed-item
  (let [p (payload)]
    (is (= 3 (count (get p "cards"))))
    (is (= "buyer-transparency+supply-resilience" (get p "intent")))))   ; G1

(deftest test-attention-card-routes-to-resilience-planner
  ;; rising SD (now 0.12 → mean 0.42) + notable spread → tightening + attention
  (let [a (get (by-pid) "jan_4901777300443")]
    (is (= "tightening" (get a "trajectory")))
    (is (= true (get a "attention")))
    (is (= "danjo" (get a "routeTo")))))

(deftest test-non-attention-routes-to-buyer-planner
  (let [b2 (get (by-pid) "gtin_04901234567894")]      ; easing → not attention
    (is (= false (get b2 "attention")))
    (is (= "okaimono" (get b2 "routeTo")))))

(deftest test-forecast-band-present
  (let [a (get (by-pid) "jan_4901777300443")]
    (is (= [0.24 0.6] (get a "forecastBand")))))       ; mean 0.42 ± sd 0.18

(deftest test-html-inlines-payload-self-contained
  (let [tpl (io/file here "_template.htm")]
    (when (.exists tpl)
      (let [html (b/render-html (payload) tpl)]
        (is (not (str/includes? html "/*__PAYLOAD__*/null")))
        (is (str/includes? html "jan_4901777300443"))
        (let [js (second (str/split html #"<script>"))]
          (is (and (not (str/includes? js "http://")) (not (str/includes? js "https://")))))))))
