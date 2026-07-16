#!/usr/bin/env bb
(ns umisachi.tests.test-kotoba
  "umisachi 海幸 — kotoba commit-DAG writer invariants (ADR-2606074200 / 2605312345).

  The marine-bounty content-addressed Datom-log writer: EAVT graph + derived :bond/* readouts,
  Family-A canonical form. Pins the b752d9f3… empty-tx invariant (umisachi joins
  kabuto/watatsuna/watari/kanjo), GROUND :add vs derived :bond/derived, append/verify/tamper,
  and G1 (no catch-target coordinate leaks into the durable log).

  Run:  bb --classpath 20-actors 20-actors/umisachi/tests/test_kotoba.clj"
  (:require [umisachi.methods.kotoba :as k]
            [umisachi.methods.analyze :as a]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.test :refer [deftest is run-tests]]))

(def ^:private this-file *file*)
(defn- seed-path []
  (-> this-file io/file .getAbsoluteFile .getParentFile .getParentFile
      (io/file "data" "seed-umisachi-graph.kotoba.edn") .getAbsolutePath))
(defn- graph [] (a/load-file (seed-path)))

;; ── pinned anchors (captured 2026-06-17) ──
(def ^:private family-a-empty "b752d9f3cc07ff707113bea25a08516b36f76bed8a6ff3bc0c91b45a4924e6b14")
(def ^:private fixed-cid "b121d48a73d0746b627b8810cf952fc3032ef8ac6dccab31dcb452f1d1da8b4a1")

(deftest empty-tx-joins-family-A
  ;; umisachi's commit-DAG uses the Clojure {:datoms :prev} form → the shared b752d9f3… anchor.
  (is (= family-a-empty (k/tx-cid [])))
  (is (= family-a-empty (k/tx-cid [] ""))))

(deftest fixed-datoms-cid-is-pinned-cross-process
  (is (= fixed-cid (k/tx-cid [[:db/add :stock.tuna :stock/status :overfished]
                              [:db/add :stock.tuna :organism/kind :stock]] "bPREV"))))

(deftest graph-datoms-are-five-element-eavt-add
  (let [[nodes edges] (graph)
        gd (k/graph-datoms nodes edges)]
    (is (= 227 (count gd)))
    (is (every? #(and (vector? %) (= 4 (count %)) (= :db/add (first %))) gd))
    ;; node entities are :organism/id keywords; edge entities are en.<from>.<kind>.<to>
    (is (some #(= :organism/kind (nth % 2)) gd))
    (is (some #(str/starts-with? (name (nth % 1)) "en.") gd))))

(deftest derived-are-bond-readouts-flagged-and-sorted
  (let [dd (k/derived-datoms (apply a/analyze (graph)))
        attrs (set (map #(nth % 2) (remove #(= :bond/derived (nth % 2)) dd)))]
    (is (= 50 (count dd)))
    (is (= #{:bond/provisioning-nourishment :bond/depletion-load :bond/depletion-imposed} attrs))
    (is (some #(= [:db/add :bond/derived] [(first %) (nth % 2)]) dd) "derived rows carry :bond/derived true")))

(deftest g1-no-catch-target-coordinate-in-the-durable-log
  ;; the durable :add graph carries NO coordinate-shaped ATTRIBUTE key (analyze's coord-key?
  ;; refuses such node keys at load; this asserts none leak through the emitter into the
  ;; content-addressed log). Checks (nth % 2) = the attribute, NOT the entity id — an entity
  ;; like :market.domestic-regu*lat*ed would false-match a naive substring scan of the id.
  (let [[nodes edges] (graph)
        gd (k/graph-datoms nodes edges)
        coord-key? (fn [a] (let [n (str/lower-case (name a))]
                             (some #(= n %) ["lat" "lon" "lng" "latitude" "longitude"
                                             "coord" "coords" "geohash" "geometry"])))]
    (is (not-any? #(coord-key? (nth % 2)) gd))))

(deftest append-read-verify-roundtrip-and-tamper
  (let [tmp (java.io.File/createTempFile "umi-kot-" ".kotoba.edn") path (.getAbsolutePath tmp)]
    (try
      (.delete tmp)
      (let [d1 [[:db/add :stock.a :organism/kind :stock]]
            tx1 (k/make-tx d1 :tx-id 1 :as-of 1 :prev-cid "")
            _ (k/append-tx tx1 path)
            head1 (k/head-cid path)
            tx2 (k/make-tx [[:db/add :stock.b :organism/kind :stock]] :tx-id 2 :as-of 2 :prev-cid head1)
            _ (k/append-tx tx2 path)]
        (is (= 2 (count (k/read-log path))))
        (is (= head1 (:tx/prev tx2)) "prev threaded")
        (is (true? (:ok (k/verify-chain path))))
        ;; tamper: corrupting a datom breaks the recomputed CID
        (spit path (str ";; hdr\n" (pr-str (assoc tx1 :tx/datoms [[:db/add :x :y :z]])) "\n" (pr-str tx2) "\n"))
        (is (false? (:ok (k/verify-chain path)))))
      (finally (.delete (io/file path))))))

(when (= *file* (System/getProperty "babashka.file"))
  (let [{:keys [fail error]} (run-tests 'umisachi.tests.test-kotoba)]
    (System/exit (if (zero? (+ fail error)) 0 1))))
