(ns asobi.tests.test-analyze
  "asobi 遊び — analyzer tests (ADR-2606073200). 1:1 Clojure port of tests/test_analyze.py.

  Verifies the constitutional invariants empirically:
    - graph loads (nodes + 縁), seed is non-trivial, no dangling 縁
    - edge-primary (N1): participation-openness is the integral of incident OPENING 縁
      × disclosed access weight — recomputed independently here and asserted equal; and NO
      stored per-node :bond/* / :asobi/popularity-of-work key exists (edge-primary only, G2)
    - the most-open node is a public-domain / open-license work (or event/venue) — never
      proprietary (sanity of the lens)
    - enclosure 取-holder concentration is non-empty and every holder is a :enclosure node

  NOTE on scope: the Python test_analyze additionally exercises the `datom_emit` sibling
  (test_datom_emit_ground_and_transient + test_determinism). Those two assertions depend on
  the unported `datom_emit` module, so they are intentionally omitted here (the datom_emit
  port is a separate unit, mirroring the rasen/inochi precedent). All four PURE analyze
  assertions are ported 1:1."
  (:require [clojure.test :refer [deftest is testing run-tests]]
            [clojure.string :as str]
            [clojure.set]
            [clojure.java.io :as io]
            [asobi.methods.analyze :as analyze]))

(def actor-dir (-> *file* io/file .getParentFile .getParentFile))
(def seed (io/file actor-dir "data" "seed-asobi-graph.kotoba.edn"))

(defn load-seed [] (analyze/load-file* seed))

(deftest test-load-nontrivial
  (let [{:keys [nodes edges]} (load-seed)]
    (is (>= (count nodes) 25) (str "expected a real seed, got " (count nodes) " nodes"))
    (is (>= (count edges) 30) (str "expected a real 縁 web, got " (count edges) " edges"))
    (let [kinds (set (map #(get % ":organism/kind") (vals nodes)))]
      (is (clojure.set/subset? #{":work" ":practice" ":enclosure"} kinds)
          (str "missing core kinds: " kinds)))
    ;; every edge resolves to known endpoints (no dangling 縁)
    (doseq [e edges]
      (is (contains? nodes (get e ":en/from")) (str "dangling from: " (get e ":en/from")))
      (is (contains? nodes (get e ":en/to")) (str "dangling to: " (get e ":en/to"))))))

(deftest test-edge-primary-openness-integral
  (testing "N1: participation-openness MUST equal the independent integral of opening 縁."
    (let [{:keys [nodes edges]} (load-seed)
          res (analyze/analyze nodes edges)
          opening #{":open-access" ":teaches" ":participates" ":hosts" ":performs"}
          expect (reduce
                  (fn [m e]
                    (if (contains? opening (get e ":en/kind"))
                      (let [dst (get e ":en/to")
                            w (get analyze/access-weight (get-in nodes [dst ":work/access"]) 0.6)]
                        (update m dst (fnil + 0.0)
                                (* (double (get e ":en/access-load")) w)))
                      m))
                  {} edges)]
      (doseq [[nid v] expect]
        (is (< (Math/abs (- (get-in res ["openness" nid]) v)) 1e-9)
            (str nid ": " (get-in res ["openness" nid]) " != " v)))
      ;; there is NO stored per-node score key on any node (edge-primary only)
      (doseq [n (vals nodes)]
        (is (not (some #(or (str/starts-with? % ":bond/") (= % ":asobi/popularity-of-work"))
                       (keys n))))))))

(deftest test-openness-top-is-open-access
  (testing "The most-open node should be a public-domain / open-license work — never proprietary."
    (let [{:keys [nodes edges]} (load-seed)
          res (analyze/analyze nodes edges)
          top (key (apply max-key val (get res "openness")))
          acc (get-in nodes [top ":work/access"])
          kind (get-in nodes [top ":organism/kind"])]
      (is (or (contains? #{":public-domain" ":open-license" nil} acc)
              (contains? #{":event" ":venue"} kind))
          (str "top openness node " top " has access " acc " — lens is mis-weighted")))))

(deftest test-enclosure-concentration-nonempty
  (let [{:keys [nodes edges]} (load-seed)
        res (analyze/analyze nodes edges)]
    (is (seq (get res "enclosure_out")) "no 取-holder enclosure concentration computed")
    ;; every enclosure 取-holder is actually a :enclosure node
    (doseq [nid (keys (get res "enclosure_out"))]
      (is (= ":enclosure" (get-in nodes [nid ":organism/kind"]))))))
