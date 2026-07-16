(ns shiori.tests.test-analyze
  "shiori 栞 — analyzer tests (ADR-2606082100). 1:1 Clojure port of tests/test_analyze.py.

  Verifies the constitutional invariants empirically:
    - graph loads (nodes + 縁), seed is non-trivial, no dangling 縁
    - G1 — cohort-aggregate only: every :cohort is :aggregate, and no per-person/affect/locator
      attribute appears on any node
    - G1/§1.13 — anti-addictive: a :mitigator may never route toward an engagement-maximising
      technique (those kinds live only on the detractor/driver side)
    - edge-primary (N1): wellbecoming-burden is the integral of incident inbound :diminishes
      × disclosed severity — recomputed independently and asserted equal; and NO stored per-node
      :bond/* / :shiori/score-of-cohort key exists (edge-primary only, G2)
    - the top relief-gap cohort bears ≥1 critical/severe detractor, and gap = burden − relief
    - the top driver 取-holder is a structural PATTERN (carries :driver/kind, names no entity)
    - unrouted detractors (burdening but with no :routes-to) surface as the intervention-design gap

  NOTE on scope: the Python test_analyze additionally exercises the `datom_emit` sibling
  (test_datom_emit_ground_and_transient + test_determinism). Those two assertions depend on the
  unported `datom_emit` module, so they are intentionally omitted here (the datom_emit port is a
  separate unit, mirroring the inochi/rasen precedent). All seven PURE analyze assertions are
  ported 1:1."
  (:require [clojure.test :refer [deftest is testing run-tests]]
            [clojure.string :as str]
            [clojure.set]
            [clojure.java.io :as io]
            [shiori.methods.analyze :as analyze]))

(def actor-dir (-> *file* io/file .getParentFile .getParentFile))
(def seed (io/file actor-dir "data" "seed-wellbecoming-graph.kotoba.edn"))

(defn load-seed [] (analyze/load-file* seed))

(deftest test-load-nontrivial
  (let [{:keys [nodes edges]} (load-seed)]
    (is (>= (count nodes) 30) (str "expected a real seed, got " (count nodes) " nodes"))
    (is (>= (count edges) 30) (str "expected a real 縁 web, got " (count edges) " edges"))
    (let [kinds (set (map #(get % ":organism/kind") (vals nodes)))]
      (is (clojure.set/subset? #{":cohort" ":detractor" ":driver" ":mitigator"} kinds)
          (str "missing core kinds: " kinds)))
    (doseq [e edges]
      (is (contains? nodes (get e ":en/from")) (str "dangling from: " (get e ":en/from")))
      (is (contains? nodes (get e ":en/to")) (str "dangling to: " (get e ":en/to"))))))

(deftest test-g1-aggregate-only-no-person-scoring
  (testing "G1: cohort scale only — every :cohort is :aggregate, no individual/affect/locator attr."
    (let [{:keys [nodes]} (load-seed)
          banned [":person/id" ":affect/score" ":sentiment" ":happiness-score" ":mood"
                  ":biometric" ":individual" ":name/full" ":geo/lat" ":geo/lon" ":profile"]]
      (doseq [[nid n] nodes]
        (doseq [b banned]
          (is (not (contains? n b)) (str "G1 violation: person-scoring attr " b " on " nid)))
        (when (= ":cohort" (get n ":organism/kind"))
          (is (= ":aggregate" (get n ":cohort/scope"))
              (str "G1 violation: cohort node " nid " is not :aggregate")))))))

(deftest test-g1-anti-addictive-mitigators-are-not-engagement
  (testing "G1/§1.13: a mitigator may never be an engagement-maximising / addictive technique."
    (let [{:keys [nodes]} (load-seed)
          addictive #{":addictive-design" ":engagement-maximizing-design" ":algorithmic-feed"}]
      (doseq [[nid n] nodes]
        (when (= ":mitigator" (get n ":organism/kind"))
          (is (not (contains? addictive (get n ":mitigator/kind")))
              (str "anti-addictive violation: " nid " routes toward an engagement technique")))))))

(deftest test-edge-primary-burden-integral
  (testing "N1: wellbecoming-burden MUST equal the independent integral of incident :diminishes 縁."
    (let [{:keys [nodes edges]} (load-seed)
          res (analyze/analyze nodes edges)
          expect (reduce
                  (fn [m e]
                    (if (= ":diminishes" (get e ":en/kind"))
                      (let [dst (get e ":en/to")
                            sev (get-in nodes [(get e ":en/from") ":detractor/severity"])
                            w (get analyze/severity-weight sev 0.5)]
                        (update m dst (fnil + 0.0)
                                (* (double (get e ":en/load")) w)))
                      m))
                  {} edges)]
      (doseq [[nid v] expect]
        (is (< (Math/abs (- (get-in res ["burden" nid]) v)) 1e-9)
            (str nid ": " (get-in res ["burden" nid]) " != " v)))
      ;; G2: no stored per-cohort score key on any node (edge-primary only)
      (doseq [n (vals nodes)]
        (is (not (some #(or (str/starts-with? % ":bond/") (= % ":shiori/score-of-cohort"))
                       (keys n))))))))

(deftest test-relief-gap-top-is-underserved-and-imperiled
  (testing "top relief-gap cohort bears ≥1 critical/severe detractor; gap = burden − relief."
    (let [{:keys [nodes edges]} (load-seed)
          res (analyze/analyze nodes edges)
          top (key (apply max-key val (get res "gap")))]
      (is (< (Math/abs (- (get-in res ["gap" top])
                          (- (get-in res ["burden" top])
                             (get (get res "relief") top 0.0)))) 1e-9))
      (let [incident-sev (set (for [e edges
                                    :when (and (= ":diminishes" (get e ":en/kind"))
                                               (= top (get e ":en/to")))]
                                (get-in nodes [(get e ":en/from") ":detractor/severity"])))]
        (is (seq (clojure.set/intersection incident-sev #{":critical" ":severe"}))
            (str "top relief-gap cohort " top " bears no critical/severe detractor — lens mis-weighted"))))))

(deftest test-imposed-driver-is-structural-pattern-not-entity
  (testing "the top driver 取-holder is a structural pattern (G1 = map-not-target)."
    (let [{:keys [nodes edges]} (load-seed)
          res (analyze/analyze nodes edges)
          drivers (into {} (for [[nid v] (get res "imposed")
                                 :when (= ":driver" (get-in nodes [nid ":organism/kind"]))]
                             [nid v]))]
      (is (seq drivers) "no driver imposed-load computed")
      (let [top-drv (key (apply max-key val drivers))]
        (is (get-in nodes [top-drv ":driver/kind"])
            (str top-drv " lacks a structural :driver/kind"))
        (doseq [forbidden [":org/id" ":company" ":person/id" ":ticker"]]
          (is (not (contains? (get nodes top-drv) forbidden))
              (str "driver " top-drv " names a real entity (G1)")))))))

(deftest test-unrouted-detractors-are-design-gaps
  (testing "detractors that burden a cohort but have no :routes-to edge surface as design gaps."
    (let [{:keys [nodes edges]} (load-seed)
          res (analyze/analyze nodes edges)]
      (doseq [nid (get res "unrouted_detractors")]
        (is (= ":detractor" (get-in nodes [nid ":organism/kind"])))
        (is (= 0.0 (get (get res "route_coverage") nid 0.0)))
        (is (> (get (get res "imposed") nid 0.0) 0.0)))
      (is (some #{"wb.detr.discrimination"} (get res "unrouted_detractors"))))))
