(ns maps.methods.test-kotoba-local
  "Tests for the in-memory EAVT/AVET Datom reference store (kotoba_local.cljc) —
  the offline substrate the maps3d Datomic BPMN engine + the spatial getChunk
  hot-path rely on. Previously had no test file; this covers ingest, EAVT
  materialize, AVET probes (cell + wire), filters, limits, and introspection."
  (:require [clojure.test :refer [deftest is]]
            [maps.methods.kotoba-local :as kl]))

(defn- batch [& ents] {"entities" (vec ents)})

(defn- feature [id label lat lon cell]
  {"id" id
   "claims" [{"pred" "feature/label" "value" label}
             {"pred" "feature/name" "value" (str "n-" id)}
             {"pred" "feature/lat" "value" (str lat)}
             {"pred" "feature/lon" "value" (str lon)}
             {"pred" "feature.cell/r10" "value" cell}]})

;; ── ingest + EAVT ─────────────────────────────────────────────────────────────

(deftest test-ingest-counts-entities-with-id
  (let [s (kl/new-store)
        n (kl/ingest-batch s (batch (feature "f1" ":building" 35.6 139.7 "c-1")
                                    {"id" "f2" "claims" []}        ; claim-less still counts
                                    {"claims" [{"pred" "x" "value" "y"}]}))] ; no id → skipped
    (is (= 2 n))))

(deftest test-entity-roundtrips-claims
  (let [s (kl/new-store)]
    (kl/ingest-batch s (batch (feature "f1" ":building" 35.6 139.7 "c-1")))
    (let [e (kl/entity s "f1")
          preds (set (map #(get % "pred") (get e "claims")))]
      (is (= "f1" (get e "id")))
      (is (contains? preds "feature/label"))
      (is (contains? preds "feature.cell/r10")))))

(deftest test-entity-absent-returns-nil
  (is (nil? (kl/entity (kl/new-store) "nope"))))

(deftest test-ingest-skips-nil-pred-or-value
  (let [s (kl/new-store)]
    (kl/ingest-batch s (batch {"id" "f1" "claims" [{"pred" "feature/label" "value" nil}
                                                   {"pred" nil "value" "x"}
                                                   {"pred" "feature/name" "value" "ok"}]}))
    (let [preds (set (map #(get % "pred") (get (kl/entity s "f1") "claims")))]
      (is (= #{"feature/name"} preds)))))

;; ── AVET: query-by-cells (getChunk hot-path) ─────────────────────────────────

(deftest test-query-by-cells-groups-by-cell-and-label
  (let [s (kl/new-store)]
    (kl/ingest-batch s (batch (feature "f1" ":building" 35.6 139.7 "c-1")
                              (feature "f2" ":building" 35.7 139.8 "c-1")
                              (feature "f3" ":station"  35.5 139.6 "c-2")))
    (let [out (kl/query-by-cells s ["c-1" "c-2"] 10)]
      (is (= 2 (count (get-in out ["c-1" ":building"]))))
      (is (= 1 (count (get-in out ["c-2" ":station"])))))))

(deftest test-query-by-cells-label-filter
  (let [s (kl/new-store)]
    (kl/ingest-batch s (batch (feature "f1" ":building" 35.6 139.7 "c-1")
                              (feature "f3" ":station"  35.5 139.6 "c-1")))
    (let [out (kl/query-by-cells s ["c-1"] 10 [":station"])]
      (is (nil? (get-in out ["c-1" ":building"])))      ; filtered out
      (is (= 1 (count (get-in out ["c-1" ":station"])))))))

(deftest test-query-by-cells-respects-limit
  (let [s (kl/new-store)]
    (kl/ingest-batch s (apply batch (for [i (range 5)]
                                      (feature (str "f" i) ":building" 35.6 139.7 "c-1"))))
    (let [out (kl/query-by-cells s ["c-1"] 10 nil 2)]
      (is (= 2 (count (get-in out ["c-1" ":building"])))))))

;; ── AVET: wire-level avet-query ──────────────────────────────────────────────

(deftest test-avet-query-returns-matching-subjects
  (let [s (kl/new-store)]
    (kl/ingest-batch s (batch (feature "f1" ":building" 35.6 139.7 "c-1")
                              (feature "f2" ":station"  35.7 139.8 "c-1")))
    (let [hits (kl/avet-query s "feature.cell/r10" ["c-1"])]
      (is (= 2 (count hits)))
      (is (= #{"f1" "f2"} (set (map #(get % "id") hits)))))))

(deftest test-avet-query-with-filter-pred
  (let [s (kl/new-store)]
    (kl/ingest-batch s (batch (feature "f1" ":building" 35.6 139.7 "c-1")
                              (feature "f2" ":station"  35.7 139.8 "c-1")))
    (let [hits (kl/avet-query s "feature.cell/r10" ["c-1"] "feature/label" [":station"])]
      (is (= 1 (count hits)))
      (is (= "f2" (get (first hits) "id"))))))

(deftest test-avet-query-empty-when-no-match
  (is (= [] (kl/avet-query (kl/new-store) "feature.cell/r10" ["ghost"]))))

;; ── introspection ─────────────────────────────────────────────────────────────

(deftest test-feature-count-and-cells-at-res
  (let [s (kl/new-store)]
    (kl/ingest-batch s (batch (feature "f1" ":building" 35.6 139.7 "c-1")
                              (feature "f2" ":station"  35.7 139.8 "c-2")
                              {"id" "t1" "claims" [{"pred" "transit/route" "value" "r"}]}))
    (is (= 2 (kl/feature-count s)))                      ; t1 has no feature/label
    (is (= #{"c-1" "c-2"} (kl/cells-at-res s 10)))))
