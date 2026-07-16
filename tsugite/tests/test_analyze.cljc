(ns tsugite.tests.test-analyze
  "tsugite 継ぎ手 — analyzer tests (ADR-2606073800). 1:1 Clojure port of tests/test_analyze.py.

  Verifies the constitutional invariants empirically:
    - graph loads (nodes + 縁), seed is non-trivial, core kinds present, no dangling 縁
    - G1: collective scale only — every :people is :aggregate, and NO individual/locator attr
    - edge-primary (N1): continuity-need is the integral of incident inbound pressure 縁
      × disclosed vitality weight — recomputed independently here and asserted equal; and NO
      stored per-node :bond/* / :tsugite/score-of-people key exists (edge-primary only, G2)
    - the top continuity-need bearer is a high-pressure people or a CR/SE/DE tongue (lens sanity)
    - protection + transmission-fragility are non-empty; a revitalized tongue carries a buffer

  NOTE on scope: the Python test_analyze additionally exercises the `datom_emit` sibling
  (test_datom_emit_ground_and_transient + test_determinism). Those two assertions depend on
  the unported `datom_emit` module, so they are intentionally omitted here (the datom_emit
  port is a separate unit, mirroring the rasen/inochi precedent). All the PURE analyze
  assertions — including the G1 no-person-tracking test — are ported 1:1."
  (:require [clojure.test :refer [deftest is testing run-tests]]
            [clojure.string :as str]
            [clojure.set]
            [clojure.java.io :as io]
            [tsugite.methods.analyze :as analyze]))

(def actor-dir (-> *file* io/file .getParentFile .getParentFile))
(def seed (io/file actor-dir "data" "seed-peoples-graph.kotoba.edn"))

(defn load-seed [] (analyze/load-file* seed))

(deftest test-load-nontrivial
  (let [{:keys [nodes edges]} (load-seed)]
    (is (>= (count nodes) 25) (str "expected a real seed, got " (count nodes) " nodes"))
    (is (>= (count edges) 30) (str "expected a real 縁 web, got " (count edges) " edges"))
    (let [kinds (set (map #(get % ":organism/kind") (vals nodes)))]
      (is (clojure.set/subset? #{":people" ":language" ":pressure" ":haven"} kinds)
          (str "missing core kinds: " kinds)))
    (doseq [e edges]
      (is (contains? nodes (get e ":en/from")) (str "dangling from: " (get e ":en/from")))
      (is (contains? nodes (get e ":en/to")) (str "dangling to: " (get e ":en/to"))))))

(deftest test-g1-aggregate-only-no-person-tracking
  (testing "G1: collective scale only — every :people is :aggregate, no individual/locator attr."
    (let [{:keys [nodes]} (load-seed)
          banned [":person/id" ":geo/lat" ":geo/lon" ":location/current" ":biometric"
                  ":individual" ":name/full" ":phone" ":passport"]]
      (doseq [[nid n] nodes]
        (doseq [b banned]
          (is (not (contains? n b)) (str "G1 violation: person-tracking attr " b " on " nid)))
        (when (= ":people" (get n ":organism/kind"))
          (is (= ":aggregate" (get n ":people/scope"))
              (str "G1 violation: people node " nid " is not :aggregate")))))))

(deftest test-edge-primary-continuity-integral
  (testing "N1: continuity-need MUST equal the independent integral of incident pressure 縁."
    (let [{:keys [nodes edges]} (load-seed)
          res (analyze/analyze nodes edges)
          pressure #{":displaces" ":erases"}
          expect (reduce
                  (fn [m e]
                    (if (contains? pressure (get e ":en/kind"))
                      (let [dst (get e ":en/to")
                            w (get analyze/vitality-weight
                                   (get-in nodes [dst ":lang/vitality"]) 0.6)]
                        (update m dst (fnil + 0.0)
                                (* (double (get e ":en/peril-load")) w)))
                      m))
                  {} edges)]
      (doseq [[nid v] expect]
        (is (< (Math/abs (- (get-in res ["continuity" nid]) v)) 1e-9)
            (str nid ": " (get-in res ["continuity" nid]) " != " v)))
      ;; there is NO stored per-node :bond/* / :tsugite/score-of-people key on any node
      (doseq [n (vals nodes)]
        (is (not (some #(or (str/starts-with? % ":bond/") (= % ":tsugite/score-of-people"))
                       (keys n))))))))

(deftest test-continuity-top-is-imperiled
  (testing "top continuity-need bearer is a high-pressure people or a CR/SE/DE tongue."
    (let [{:keys [nodes edges]} (load-seed)
          res (analyze/analyze nodes edges)
          top (key (apply max-key val (get res "continuity")))
          vit (get-in nodes [top ":lang/vitality"])
          kind (get-in nodes [top ":organism/kind"])]
      (is (or (= ":people" kind)
              (contains? #{":critically-endangered" ":severely-endangered"
                           ":definitely-endangered"} vit))
          (str "top continuity node " top " (vitality " vit ") — lens is mis-weighted")))))

(deftest test-protection-and-fragility-nonempty
  (let [{:keys [nodes edges]} (load-seed)
        res (analyze/analyze nodes edges)]
    (is (seq (get res "protection")) "no protection buffer computed")
    (is (seq (get res "fragility")) "no transmission fragility computed")
    ;; a revitalized language (Māori/Hawaiian) must carry a protection buffer
    (is (some #(str/starts-with? % "ppl.lang.") (keys (get res "protection"))))))
