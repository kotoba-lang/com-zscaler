(ns hokorobi.tests.test-analyze
  "hokorobi 綻び — analyzer tests (ADR-2606073400). 1:1 Clojure port of tests/test_analyze.py.

  Verifies the constitutional invariants empirically:
    - graph loads (nodes + 縁), seed is non-trivial, no dangling 縁
    - edge-primary (N1): systemic-risk-concentration is the integral of incident inbound risk
      縁 × disclosed SII weight — recomputed independently here and asserted equal; and NO
      stored per-node :bond/* / :hokorobi/solvency-of-bank key exists (edge-primary only, G2)
    - the top systemic node is a G-SIB/D-SIB/large institution or a public bearer (the
      disclosed weight must dominate — sanity of the lens)
    - risk-source 取-holder concentration is non-empty and every holder is a :risk or
      :institution node, with at least one pure :risk source present

  NOTE on scope: the Python test_analyze additionally exercises the `datom_emit` sibling
  (test_datom_emit_ground_and_transient + test_determinism). Those two assertions depend on
  the unported `datom_emit` module, so they are intentionally omitted here (the datom_emit
  port is a separate unit, mirroring the rasen / inochi precedent). All four PURE analyze
  assertions are ported 1:1."
  (:require [clojure.test :refer [deftest is testing run-tests]]
            [clojure.string :as str]
            [clojure.set]
            [clojure.java.io :as io]
            [hokorobi.methods.analyze :as analyze]))

(def actor-dir (-> *file* io/file .getParentFile .getParentFile))
(def seed (io/file actor-dir "data" "seed-finrisk-graph.kotoba.edn"))

(defn load-seed [] (analyze/load-file* seed))

(deftest test-load-nontrivial
  (let [{:keys [nodes edges]} (load-seed)]
    (is (>= (count nodes) 25) (str "expected a real seed, got " (count nodes) " nodes"))
    (is (>= (count edges) 30) (str "expected a real 縁 web, got " (count edges) " edges"))
    (let [kinds (set (map #(get % ":organism/kind") (vals nodes)))]
      (is (clojure.set/subset? #{":institution" ":risk" ":bearer"} kinds)
          (str "missing core kinds: " kinds)))
    ;; every edge resolves to known endpoints (no dangling 縁)
    (doseq [e edges]
      (is (contains? nodes (get e ":en/from")) (str "dangling from: " (get e ":en/from")))
      (is (contains? nodes (get e ":en/to")) (str "dangling to: " (get e ":en/to"))))))

(deftest test-edge-primary-systemic-integral
  (testing "N1: systemic-risk MUST equal the independent integral of incident risk 縁."
    (let [{:keys [nodes edges]} (load-seed)
          res (analyze/analyze nodes edges)
          risk #{":exposes" ":interconnects" ":underfunds" ":protection-gap"}
          expect (reduce
                  (fn [m e]
                    (if (contains? risk (get e ":en/kind"))
                      (let [dst (get e ":en/to")
                            w (get analyze/sii-weight (get-in nodes [dst ":inst/sii"]) 0.6)]
                        (update m dst (fnil + 0.0)
                                (* (double (get e ":en/risk-load")) w)))
                      m))
                  {} edges)]
      (doseq [[nid v] expect]
        (is (< (Math/abs (- (get-in res ["systemic" nid]) v)) 1e-9)
            (str nid ": " (get-in res ["systemic" nid]) " != " v)))
      ;; no stored per-node score key on any node (edge-primary only)
      (doseq [n (vals nodes)]
        (is (not (some #(or (str/starts-with? % ":bond/") (= % ":hokorobi/solvency-of-bank"))
                       (keys n))))))))

(deftest test-systemic-top-is-significant
  (testing "top systemic node is a G-SIB-tier institution or a public bearer (weight dominates)."
    (let [{:keys [nodes edges]} (load-seed)
          res (analyze/analyze nodes edges)
          top (key (apply max-key val (get res "systemic")))
          sii (get-in nodes [top ":inst/sii"])
          kind (get-in nodes [top ":organism/kind"])]
      (is (or (contains? #{":g-sib" ":d-sib" ":large" nil} sii)
              (= ":bearer" kind))
          (str "top systemic node " top " has SII " sii " — lens is mis-weighted")))))

(deftest test-risk-source-concentration-nonempty
  (let [{:keys [nodes edges]} (load-seed)
        res (analyze/analyze nodes edges)]
    (is (seq (get res "risk_out")) "no 取-holder risk-source concentration computed")
    ;; 取-holders are :risk sources OR institutions propagating contagion (:interconnects)
    (doseq [nid (keys (get res "risk_out"))]
      (is (contains? #{":risk" ":institution"} (get-in nodes [nid ":organism/kind"]))))
    ;; at least one pure :risk source is present (the primary 取-holder class)
    (is (some #(= ":risk" (get-in nodes [% ":organism/kind"])) (keys (get res "risk_out"))))))
