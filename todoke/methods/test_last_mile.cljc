(ns todoke.methods.test-last-mile
  "Tests for todoke.methods.last-mile — sequencer correctness, G7 envelope, courier sizing.

  1:1 port of `20-actors/todoke/methods/test_last_mile.py`.

  The PARITY contract: the Python/Clojure sequencer and the Rust `todoke-route` crate
  must return the SAME visiting order on the shared fixtures. The Rust side pins
  [0 4 2 3 1] / length 30 in `route/src/lib.rs::sequences_collinear_stops_in_order`;
  `test-parity-collinear-matches-rust` pins the identical result here."
  (:require [clojure.test :refer [deftest is]]
            [todoke.methods.last-mile :as last-mile]))

(defn- envelope-violation? [thunk]
  (try
    (thunk)
    false
    (catch #?(:clj Exception :cljs :default) e
      (= true (:envelope-violation (ex-data e))))))

(def ^:private collinear
  [{:id 0 :x 0.0  :y 0.0 :zone "sidewalk"}
   {:id 1 :x 30.0 :y 0.0 :zone "doorpath"}
   {:id 2 :x 10.0 :y 0.0 :zone "sidewalk"}
   {:id 3 :x 20.0 :y 0.0 :zone "doorpath"}
   {:id 4 :x 5.0  :y 0.0 :zone "crosswalk"}])

(deftest test-parity-collinear-matches-rust
  (let [[order length] (last-mile/plan-last-mile collinear :sae-level 4 :commanded-mps 1.0)]
    (is (= order [0 4 2 3 1])) ; identical to the Rust crate fixture
    (is (< (Math/abs (- length 30.0)) 1e-6))))

(deftest test-two-opt-removes-crossing-on-square
  (let [sq [{:id 0 :x 0.0  :y 0.0  :zone "sidewalk"}
            {:id 1 :x 0.0  :y 10.0 :zone "sidewalk"}
            {:id 2 :x 10.0 :y 10.0 :zone "sidewalk"}
            {:id 3 :x 10.0 :y 0.0  :zone "sidewalk"}]
        [_ length] (last-mile/plan-last-mile sq :commanded-mps 1.5)]
    (is (<= length (+ 30.0 1e-6)))))

(deftest test-g7-refuses-sae-5
  (is (envelope-violation? #(last-mile/plan-last-mile collinear :sae-level 5 :commanded-mps 1.0))))

(deftest test-g7-refuses-road-zone
  (let [stops (conj collinear {:id 9 :x 40.0 :y 0.0 :zone "road"})]
    (is (envelope-violation? #(last-mile/plan-last-mile stops :commanded-mps 1.0)))))

(deftest test-g7-refuses-speed-over-cap
  (is (envelope-violation? #(last-mile/plan-last-mile collinear :commanded-mps 3.0))))

(deftest test-empty-refused
  (is (envelope-violation? #(last-mile/plan-last-mile []))))

(deftest test-courier-sizing-is-positive
  (is (> (last-mile/courier-freed-hours 1.0e7 2200 0.3) 0))
  (is (= (last-mile/displacement-cohort-size 1.0e7 0.3) 3000000)))
