(ns inochi.tests.test-analyze
  "inochi 命 — analyzer tests (ADR-2606073000). 1:1 Clojure port of tests/test_analyze.py.

  Verifies the constitutional invariants empirically:
    - graph loads (nodes + 縁), seed is non-trivial, no dangling 縁
    - edge-primary (N1): restoration-priority is the integral of incident inbound pressures
      × IUCN weight — recomputed independently here and asserted equal; and NO stored
      per-node :bond/* / :biosphere/score-of-species key exists (edge-primary only, G2)
    - the most-pressured CR/EN keystone (or high-pressure ecosystem) ranks at the
      restoration top (sanity of the lens)
    - pressure 取-holder concentration is non-empty and every holder is a :pressure node

  NOTE on scope: the Python test_analyze additionally exercises the `datom_emit` sibling
  (test_datom_emit_ground_and_transient + test_determinism). Those two assertions depend on
  the unported `datom_emit` module, so they are intentionally omitted here (the datom_emit
  port is a separate unit, mirroring the rasen precedent). All four PURE analyze assertions
  are ported 1:1."
  (:require [clojure.test :refer [deftest is testing run-tests]]
            [clojure.string :as str]
            [clojure.set]
            [clojure.java.io :as io]
            [inochi.methods.analyze :as analyze]))

(def actor-dir (-> *file* io/file .getParentFile .getParentFile))
(def seed (io/file actor-dir "data" "seed-biosphere-graph.kotoba.edn"))

(defn load-seed [] (analyze/load-file* seed))

(deftest test-load-nontrivial
  (let [{:keys [nodes edges]} (load-seed)]
    (is (>= (count nodes) 20) (str "expected a real seed, got " (count nodes) " nodes"))
    (is (>= (count edges) 30) (str "expected a real 縁 web, got " (count edges) " edges"))
    (let [kinds (set (map #(get % ":organism/kind") (vals nodes)))]
      (is (clojure.set/subset? #{":species" ":pressure"} kinds)
          (str "missing core kinds: " kinds)))
    ;; every edge resolves to known endpoints (no dangling 縁)
    (doseq [e edges]
      (is (contains? nodes (get e ":en/from")) (str "dangling from: " (get e ":en/from")))
      (is (contains? nodes (get e ":en/to")) (str "dangling to: " (get e ":en/to"))))))

(deftest test-edge-primary-integral
  (testing "N1: restoration-priority MUST equal the independent integral of incident pressures."
    (let [{:keys [nodes edges]} (load-seed)
          res (analyze/analyze nodes edges)
          expect (reduce
                  (fn [m e]
                    (if (= ":pressures" (get e ":en/kind"))
                      (let [dst (get e ":en/to")
                            w (get analyze/iucn-weight (get-in nodes [dst ":taxon/iucn"]) 0.5)]
                        (update m dst (fnil + 0.0)
                                (* (double (get e ":en/grasping-load")) w)))
                      m))
                  {} edges)]
      (doseq [[nid v] expect]
        (is (< (Math/abs (- (get-in res ["restoration" nid]) v)) 1e-9)
            (str nid ": " (get-in res ["restoration" nid]) " != " v)))
      ;; there is NO stored per-node score key on any node (edge-primary only)
      (doseq [n (vals nodes)]
        (is (not (some #(or (str/starts-with? % ":bond/") (= % ":biosphere/score-of-species"))
                       (keys n))))))))

(deftest test-restoration-top-is-critical
  (let [{:keys [nodes edges]} (load-seed)
        res (analyze/analyze nodes edges)
        top (key (apply max-key val (get res "restoration")))
        iucn (get-in nodes [top ":taxon/iucn"])
        kind (get-in nodes [top ":organism/kind"])]
    ;; the top bearer should be a CR/EN taxon or a high-pressure ecosystem — never LC noise
    (is (or (contains? #{":CR" ":EN" nil} iucn)
            (contains? #{":ecosystem" ":biome"} kind))
        (str "top restoration node " top " has IUCN " iucn " — lens is mis-weighted"))))

(deftest test-pressure-concentration-nonempty
  (let [{:keys [nodes edges]} (load-seed)
        res (analyze/analyze nodes edges)]
    (is (seq (get res "pressure_out")) "no 取-holder pressure concentration computed")
    ;; every pressure 取-holder is actually a :pressure node
    (doseq [nid (keys (get res "pressure_out"))]
      (is (= ":pressure" (get-in nodes [nid ":organism/kind"]))))))
