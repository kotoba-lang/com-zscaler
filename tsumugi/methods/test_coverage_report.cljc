(ns tsumugi.methods.test-coverage-report
  "Cross-language oracle tests for tsumugi.methods.coverage-report — the influence-history
  coverage analyzer. Ported from the REAL Python coverage_report over the committed seed.

  Pins the oracle values from running the real Python compute(seed-influence-history): 79 nodes
  / 125 edges / 45 figures, 1 connected component (largest 79) / 0 isolated, all 11 eras +
  17 streams covered (none missing), density 0.020286. This is the PURE-loader leaf of
  analyze_influence (the numpy spectral layout stays unported)."
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.string :as str]
            [tsumugi.methods.coverage-report :as cr]))

(def seed-path "20-actors/tsumugi/data/seed-influence-history.kotoba.edn")
(defn- c [] (cr/compute seed-path))
(defn- close? [x y] (< (Math/abs (- (double x) (double y))) 1e-6))

(deftest counts-match-real-seed
  (let [r (c)]
    (is (= 79 (get r "nodes")))
    (is (= 125 (get r "edges")))
    (is (= 45 (get r "figures")))
    (is (close? 0.020286 (get r "density")))))

(deftest graph-is-fully-connected
  (let [r (c)]
    (is (= 1 (count (get r "components"))))
    (is (= 79 (count (first (get r "components")))))
    (is (= 0 (count (get r "isolated"))))))

(deftest backbone-fully-covered
  (let [r (c)]
    (is (= 11 (count (get r "era_covered"))))
    (is (= [] (get r "era_missing")))
    (is (= 17 (count (get r "stream_covered"))))
    (is (= [] (get r "stream_missing")))))

(deftest components-helper-unions-flows
  ;; a tiny synthetic graph: a→b, b→c (one component of 3) + isolated d
  (let [nodes {":a" {} ":b" {} ":c" {} ":d" {}}
        flows [{":flow/from" ":a" ":flow/to" ":b"} {":flow/from" ":b" ":flow/to" ":c"}]
        comps (cr/components nodes flows)]
    (is (= 3 (count (first comps))))               ; {a b c}
    (is (= 1 (count (last comps))))))              ; {d} isolated

(deftest render-is-honest-framing
  (let [md (cr/render (c))]
    (is (str/includes? md "Coverage of *all* past humanity is ~0"))
    (is (str/includes? md "fully connected"))
    (is (str/includes? md "Era coverage — 11/11"))
    (is (str/includes? md "stream coverage — 17/17"))))
