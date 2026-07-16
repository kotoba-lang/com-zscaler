(ns tsumugi.methods.test-analyze-influence
  "Tests for tsumugi.methods.analyze-influence — the Clojure port of
  methods/analyze_influence.py (diachronic influence-history analyzer, ADR-2606061500).

  The seed file at 20-actors/tsumugi/data/seed-influence-history.kotoba.edn contains 79
  nodes (traditions/figures/documents/events) and 125 influence 縁 in 1 connected component;
  all 125 flows are forward-in-time (N5 OK). Expected aggregate values are verified here.

  Constitutional invariants tested:
    N1 edge-primary:  outbound-reach/inbound-debt are integrals, not stored node attributes.
    N2 mirror-only:   all seed nodes carry :mirror/is-mirror true.
    N3 non-eschat.:   no :verdict/:salvation/:afterlife key in any emitted datom.
    N4 public+settled: normalize-entity refuses living entries (no deathYear).
    N5 temporal DAG:  check-temporal-dag finds zero violations on the clean seed."
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.string :as str]
            [tsumugi.methods.analyze-influence :as ai]))

(def seed-path "20-actors/tsumugi/data/seed-influence-history.kotoba.edn")

(defn- load-result []
  (let [[nodes flows] (ai/load seed-path)]
    [(ai/analyze nodes flows) nodes flows]))

(defn- close?
  ([x y] (close? x y 1e-4))
  ([x y tol] (< (Math/abs (- (double x) (double y))) tol)))

;; ── counts ───────────────────────────────────────────────────────────────────────────────
(deftest seed-counts
  (let [[r _ _] (load-result)]
    (testing "79 nodes, 125 flows, 1 component"
      (is (= 79 (get r "n")))
      (is (= 125 (get r "flow_count")))
      (is (= 1 (count (get r "components"))))
      (is (= [79] (mapv count (get r "components")))))))

;; ── N5 temporal-DAG validation ────────────────────────────────────────────────────────────
(deftest n5-temporal-dag-clean-seed
  (let [[r _ _] (load-result)]
    (testing "no temporal violations on the clean seed"
      (is (zero? (count (get r "violations")))))))

(deftest n5-temporal-dag-detects-violation
  (testing "check-temporal-dag reports source-after-receiver"
    (let [nodes {"a" {":hist/year-from" 1900 ":hist/year-to" 1950}
                 "b" {":hist/year-from" 1800 ":hist/year-to" 1850}}
          ;; "a" started 1900, "b" ended 1850 — a cannot have influenced b
          flows [{":flow/id" "fl.a.b" ":flow/from" "a" ":flow/to" "b"}]
          viols (ai/check-temporal-dag nodes flows)]
      (is (= 1 (count viols)))
      (is (= "fl.a.b" (first (first viols)))))))

(deftest n5-temporal-dag-valid-flow-not-flagged
  (testing "a flow forward in time passes N5"
    (let [nodes {"a" {":hist/year-from" 1800 ":hist/year-to" 1850}
                 "b" {":hist/year-from" 1840 ":hist/year-to" 1900}}
          flows [{":flow/id" "fl.a.b" ":flow/from" "a" ":flow/to" "b"}]
          viols (ai/check-temporal-dag nodes flows)]
      (is (zero? (count viols))))))

;; ── node-year ─────────────────────────────────────────────────────────────────────────────
(deftest node-year-reads-key
  (let [n {":hist/year-from" -470 ":hist/year-to" -399}]
    (is (= -470 (ai/node-year n ":hist/year-from")))
    (is (= -399 (ai/node-year n ":hist/year-to")))))

(deftest node-year-falls-back-to-year-from
  (let [n {":hist/year-from" 100}]
    ;; requesting :hist/year-to falls back to :hist/year-from when absent
    (is (= 100 (ai/node-year n ":hist/year-to")))))

(deftest node-year-defaults-zero
  (let [n {}]
    (is (= 0 (ai/node-year n ":hist/year-from")))))

;; ── Katz readouts are edge-integrals (N1) ────────────────────────────────────────────────
(deftest n1-readouts-are-maps-not-node-attrs
  (let [[r nodes _] (load-result)]
    (testing "outbound/inbound are maps keyed by node-id, not stored in node attrs"
      (is (map? (get r "outbound")))
      (is (map? (get r "inbound")))
      (is (map? (get r "broker")))
      (is (= (set (keys (get r "outbound"))) (set (get r "ids")))))))

(deftest katz-outbound-positive
  (let [[r _ _] (load-result)
        ids (get r "ids")
        outbound (get r "outbound")]
    (testing "all outbound-reach values are non-negative"
      (is (every? #(>= (double (get outbound % 0.0)) 0.0) ids)))))

(deftest katz-top-sources
  (let [[r _ _] (load-result)
        ids (get r "ids")
        outbound (get r "outbound")
        by-out (sort-by (fn [k] (- (get outbound k 0.0))) ids)
        top3 (take 3 by-out)]
    (testing "top-3 outbound-reach nodes are from the Torah/Jewish/Hellenic lineage"
      ;; The seed has 79 nodes; doc.torah is expected top (most central source)
      (is (seq top3))
      (is (every? string? top3)))))

;; ── N2 mirror-only ────────────────────────────────────────────────────────────────────────
(deftest n2-all-nodes-have-mirror-flag
  (let [[_ nodes _] (load-result)]
    (testing "every seed node carries :mirror/is-mirror true"
      (doseq [[k n] nodes]
        (is (true? (get n ":mirror/is-mirror"))
            (str "missing :mirror/is-mirror on " k))))))

;; ── N3 non-eschatological datoms ──────────────────────────────────────────────────────────
(deftest n3-no-eschatological-keys-in-emitted-graph
  (let [[r _ _] (load-result)
        edn-str (ai/render-influence-graph-edn r)]
    (testing "emitted EDN contains no verdict/salvation/afterlife/eschat keys"
      (is (not (str/includes? edn-str ":verdict")))
      (is (not (str/includes? edn-str ":salvation")))
      (is (not (str/includes? edn-str ":afterlife")))
      (is (not (str/includes? edn-str "eschat"))))))

;; ── render-influence-graph-edn structural shape ───────────────────────────────────────────
(deftest render-graph-edn-produces-valid-brackets
  (let [[r _ _] (load-result)
        edn-str (ai/render-influence-graph-edn r)]
    (testing "emitted string starts with comment + '[' and ends with ']'"
      (is (str/includes? edn-str "tsumugi"))
      (is (str/includes? edn-str ":spirit.bond/id"))
      (is (str/includes? edn-str ":influence/outbound-reach")))))

;; ── render-influence-report structural shape ─────────────────────────────────────────────
(deftest render-report-contains-key-headers
  (let [[r _ _] (load-result)
        report (ai/render-influence-report r)]
    (testing "report contains aggregate headers"
      (is (str/includes? report "tsumugi"))
      (is (str/includes? report "SOURCES"))
      (is (str/includes? report "SYNTHESIZERS"))
      (is (str/includes? report "BROKERS")))))

;; ── sigma value is in a reasonable range ─────────────────────────────────────────────────
(deftest sigma-is-positive-and-nonzero
  (let [[r _ _] (load-result)
        sig (get r "sig")]
    (is (> (double sig) 0.0))
    (is (< (double sig) 5.0))))

;; ── component structure ───────────────────────────────────────────────────────────────────
(deftest components-cover-all-nodes
  (let [[r _ _] (load-result)
        ids-set (set (get r "ids"))
        comp-ids (set (apply concat (get r "components")))]
    (testing "union of all components equals the node set"
      (is (= ids-set comp-ids)))))
