(ns hoshimori.tests.test-analyze
  "hoshimori 星守 — analyzer tests (ADR-2606073600). 1:1 Clojure port of tests/test_analyze.py.

  Verifies the constitutional invariants empirically:
    - graph loads (nodes + 縁), seed is non-trivial, no dangling 縁
    - G1 (no precise ephemeris): NO interception-grade state vector — no per-object
      lat/lon/alt/velocity/TLE attribute is present on any node
    - edge-primary (N1): congestion-concentration is the integral of incident inbound
      hazard/occupancy 縁 × disclosed regime weight — recomputed independently here and
      asserted equal; and NO stored per-node :bond/* / :hoshimori/threat-of-object key exists
    - the most-congested regime is LEO-low (megaconstellation + debris band)
    - stewardship + service-dependency fragility are non-empty (PNT-on-MEO is a top fragility)

  NOTE on scope: the Python test_analyze additionally exercises the `datom_emit` sibling
  (test_datom_emit_ground_and_transient + test_determinism). Those two assertions depend on
  the unported `datom_emit` module, so they are intentionally omitted here (the datom_emit
  port is a separate unit, mirroring the inochi/rasen precedent). All FIVE pure analyze
  assertions are ported 1:1."
  (:require [clojure.test :refer [deftest is testing run-tests]]
            [clojure.string :as str]
            [clojure.set]
            [clojure.java.io :as io]
            [hoshimori.methods.analyze :as analyze]))

(def actor-dir (-> *file* io/file .getParentFile .getParentFile))
(def seed (io/file actor-dir "data" "seed-orbit-graph.kotoba.edn"))

(defn load-seed [] (analyze/load-file* seed))

(deftest test-load-nontrivial
  (let [{:keys [nodes edges]} (load-seed)]
    (is (>= (count nodes) 25) (str "expected a real seed, got " (count nodes) " nodes"))
    (is (>= (count edges) 30) (str "expected a real 縁 web, got " (count edges) " edges"))
    (let [kinds (set (map #(get % ":organism/kind") (vals nodes)))]
      (is (clojure.set/subset? #{":shell" ":operator" ":hazard" ":service"} kinds)
          (str "missing core kinds: " kinds)))
    ;; every edge resolves to known endpoints (no dangling 縁)
    (doseq [e edges]
      (is (contains? nodes (get e ":en/from")) (str "dangling from: " (get e ":en/from")))
      (is (contains? nodes (get e ":en/to")) (str "dangling to: " (get e ":en/to"))))))

(deftest test-g1-no-precise-ephemeris
  (testing "G1: NO interception-grade state vector — no per-object lat/lon/alt/velocity attrs."
    (let [{:keys [nodes]} (load-seed)
          banned [":geo/lat" ":geo/lon" ":eph/state-vector" ":obj/altitude-km"
                  ":obj/velocity" ":tle/line1" ":tle/line2"]]
      (doseq [n (vals nodes)
              b banned]
        (is (not (contains? n b))
            (str "G1 violation: precise-ephemeris attr " b " present"))))))

(deftest test-edge-primary-congestion-integral
  (testing "N1: congestion MUST equal the independent integral of incident hazard/occupancy 縁."
    (let [{:keys [nodes edges]} (load-seed)
          res (analyze/analyze nodes edges)
          hazard #{":congests" ":imperils"}
          expect (reduce
                  (fn [m e]
                    (if (contains? hazard (get e ":en/kind"))
                      (let [dst (get e ":en/to")
                            w (get analyze/regime-weight (get-in nodes [dst ":shell/regime"]) 0.6)]
                        (update m dst (fnil + 0.0)
                                (* (double (get e ":en/orbit-load")) w)))
                      m))
                  {} edges)]
      (doseq [[nid v] expect]
        (is (< (Math/abs (- (get-in res ["congestion" nid]) v)) 1e-9)
            (str nid ": " (get-in res ["congestion" nid]) " != " v)))
      ;; there is NO stored per-node score key on any node (edge-primary only)
      (doseq [n (vals nodes)]
        (is (not (some #(or (str/starts-with? % ":bond/") (= % ":hoshimori/threat-of-object"))
                       (keys n))))))))

(deftest test-congestion-top-is-leo-low
  (testing "The most-congested regime should be LEO-low (megaconstellation + debris band)."
    (let [{:keys [nodes edges]} (load-seed)
          res (analyze/analyze nodes edges)
          top (key (apply max-key val (get res "congestion")))]
      (is (= ":leo-low" (get-in nodes [top ":shell/regime"]))
          (str "top congestion node " top " is not LEO-low — lens is mis-weighted")))))

(deftest test-stewardship-and-fragility-nonempty
  (let [{:keys [nodes edges]} (load-seed)
        res (analyze/analyze nodes edges)]
    (is (seq (get res "stewardship")) "no stewardship buffer computed")
    (is (seq (get res "fragility")) "no service-dependency fragility computed")
    ;; PNT-on-MEO must be a top fragility (GNSS critically depends on MEO)
    (is (contains? (get res "fragility") "orbit.svc.pnt"))))
