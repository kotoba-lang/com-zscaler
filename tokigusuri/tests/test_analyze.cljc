(ns tokigusuri.tests.test-analyze
  "tokigusuri 時薬 — analyzer tests (ADR-2606171300). Sibling of hokorobi tests/test_analyze.

  Verifies the constitutional invariants empirically:
    - graph loads (nodes + 縁), seed is non-trivial, no dangling 縁
    - edge-primary (N1): access-barrier-concentration is the integral of incident inbound barrier
      縁 × disclosed essentiality weight — recomputed independently here and asserted equal; and
      NO stored per-node :bond/* / :tokigusuri/monopoly-of-drug key exists (edge-primary only, G2)
    - the top access-barrier node is an essential medicine (eml-core/eml-complementary) or a
      public bearer (the disclosed essentiality weight must dominate — sanity of the lens)
    - exclusivity-barrier 取-holder concentration is non-empty and every holder is a :barrier or
      :holder node, with at least one pure :barrier source present"
  (:require [clojure.test :refer [deftest is testing run-tests]]
            [clojure.string :as str]
            [clojure.set]
            [clojure.java.io :as io]
            [tokigusuri.methods.analyze :as analyze]))

(def actor-dir (-> *file* io/file .getParentFile .getParentFile))
(def seed (io/file actor-dir "data" "seed-pharma-patent-graph.kotoba.edn"))

(defn load-seed [] (analyze/load-file* seed))

(deftest test-load-nontrivial
  (let [{:keys [nodes edges]} (load-seed)]
    (is (>= (count nodes) 25) (str "expected a real seed, got " (count nodes) " nodes"))
    (is (>= (count edges) 30) (str "expected a real 縁 web, got " (count edges) " edges"))
    (let [kinds (set (map #(get % ":organism/kind") (vals nodes)))]
      (is (clojure.set/subset? #{":drug" ":barrier" ":bearer"} kinds)
          (str "missing core kinds: " kinds)))
    ;; every edge resolves to known endpoints (no dangling 縁)
    (doseq [e edges]
      (is (contains? nodes (get e ":en/from")) (str "dangling from: " (get e ":en/from")))
      (is (contains? nodes (get e ":en/to")) (str "dangling to: " (get e ":en/to"))))))

(deftest test-edge-primary-barrier-integral
  (testing "N1: access-barrier MUST equal the independent integral of incident barrier 縁."
    (let [{:keys [nodes edges]} (load-seed)
          res (analyze/analyze nodes edges)
          barrier #{":monopolizes" ":blocks" ":evergreens" ":delays" ":gates-access"}
          expect (reduce
                  (fn [m e]
                    (if (contains? barrier (get e ":en/kind"))
                      (let [dst (get e ":en/to")
                            w (get analyze/essentiality-weight (get-in nodes [dst ":drug/essentiality"]) 0.5)]
                        (update m dst (fnil + 0.0)
                                (* (double (get e ":en/barrier-load")) w)))
                      m))
                  {} edges)]
      (doseq [[nid v] expect]
        (is (< (Math/abs (- (get-in res ["barrier" nid]) v)) 1e-9)
            (str nid ": " (get-in res ["barrier" nid]) " != " v)))
      ;; no stored per-node score key on any node (edge-primary only)
      (doseq [n (vals nodes)]
        (is (not (some #(or (str/starts-with? % ":bond/") (= % ":tokigusuri/monopoly-of-drug"))
                       (keys n))))))))

(deftest test-barrier-top-is-essential
  (testing "top access-barrier node is an essential medicine or a public bearer (weight dominates)."
    (let [{:keys [nodes edges]} (load-seed)
          res (analyze/analyze nodes edges)
          top (key (apply max-key val (get res "barrier")))
          ess (get-in nodes [top ":drug/essentiality"])
          kind (get-in nodes [top ":organism/kind"])]
      (is (or (contains? #{":eml-core" ":eml-complementary"} ess)
              (= ":bearer" kind))
          (str "top access-barrier node " top " has essentiality " ess " — lens is mis-weighted")))))

(deftest test-barrier-source-concentration-nonempty
  (let [{:keys [nodes edges]} (load-seed)
        res (analyze/analyze nodes edges)]
    (is (seq (get res "barrier_out")) "no 取-holder exclusivity-barrier concentration computed")
    ;; 取-holders are :barrier sources OR :holder (originator) nodes imposing exclusivity
    (doseq [nid (keys (get res "barrier_out"))]
      (is (contains? #{":barrier" ":holder"} (get-in nodes [nid ":organism/kind"]))))
    ;; at least one pure :barrier source is present (the primary 取-holder class)
    (is (some #(= ":barrier" (get-in nodes [% ":organism/kind"])) (keys (get res "barrier_out"))))))
