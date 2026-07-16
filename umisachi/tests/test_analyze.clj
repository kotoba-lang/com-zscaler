#!/usr/bin/env bb
(ns umisachi.tests.test-analyze
  "umisachi 海幸 — analyzer + charter-guard invariants (ADR-2606074200).

  Run:  bb --classpath 20-actors 20-actors/umisachi/tests/test_analyze.clj"
  (:require [umisachi.methods.analyze :as a]
            [clojure.java.io :as io]
            [clojure.edn :as edn]
            [clojure.test :refer [deftest is run-tests]]))

(def ^:private this-file *file*)
(defn- seed-path []
  (-> this-file io/file .getAbsoluteFile .getParentFile .getParentFile
      (io/file "data" "seed-umisachi-graph.kotoba.edn")))

(defn- graph [] (a/load-file (seed-path)))

;; ── load ─────────────────────────────────────────────────────────────────────
(deftest seed-loads
  (let [[nodes edges] (graph)]
    (is (= (count nodes) 31))
    (is (= (count edges) 24))
    (is (every? :organism/id (vals nodes)))))

;; ── G4 — destructive methods unrepresentable as a fishery ─────────────────────
(deftest g4-refuses-factory-fishing-as-method
  (is (thrown? clojure.lang.ExceptionInfo
               (a/assert-charter-clean
                {:organism/id :x :organism/kind :fishery :fishery/method :factory-fishing}))))

(deftest g4-refuses-each-destructive-method
  (doseq [m a/forbidden-methods]
    (is (thrown? clojure.lang.ExceptionInfo
                 (a/assert-charter-clean
                  {:organism/id :x :organism/kind :fishery :fishery/method m}))
        (str "G4 must refuse " m))))

(deftest g4-allows-factory-fishing-as-disclosed-pressure
  ;; the SAME harm is representable as a :pressure node (routed to restoration), never a method
  (is (= :pressure
         (:organism/kind
          (a/assert-charter-clean
           {:organism/id :p :organism/kind :pressure :pressure/kind :factory-fishing})))))

;; ── G1 — no fishing-ground coordinates ────────────────────────────────────────
(deftest g1-refuses-coordinate-keys
  (doseq [k [:stock/lat :stock/lon :geo/coords :stock/longitude :site/gps :cell/geohash]]
    (is (thrown? clojure.lang.ExceptionInfo
                 (a/assert-charter-clean {:organism/id :x :organism/kind :stock k 1.0}))
        (str "G1 must refuse coordinate key " k))))

(deftest g1-allows-coarse-region-name
  ;; :stock/region is a coarse FAO-area NAME, not a coordinate — must be allowed
  (is (false? (boolean (#'a/coord-key? :stock/region))))
  (is (= :stock
         (:organism/kind
          (a/assert-charter-clean
           {:organism/id :x :organism/kind :stock :stock/region "FAO-61 Northwest Pacific"})))))

;; ── analyze (edge-primary integrals, computed on read) ────────────────────────
(deftest depletion-out-concentrates-on-the-pressure-holder
  (let [[nodes edges] (graph)
        res (a/analyze nodes edges)]
    ;; industrial overfishing: 0.9 (sanma) + 0.6 (sardine) + 0.4 (krill) = 1.9
    (is (< (Math/abs (- (get-in res [:depletion-out :pressure.industrial-overfishing]) 1.9)) 1e-9))
    (is (= :pressure.industrial-overfishing
           (ffirst (a/rank (:depletion-out res) nodes 1))))))

(deftest nourishment-rewards-the-commons-receiver
  (let [[nodes edges] (graph)
        res (a/analyze nodes edges)
        commons (get-in res [:nourishment :market.local-commons])
        export  (get-in res [:nourishment :market.export-sushi])]
    ;; commons (access weight 1.0) receives more nourishment per unit load than proprietary (0.1)
    (is (= :market.local-commons (ffirst (a/rank (:nourishment res) nodes 1))))
    (is (> commons (or export 0.0)))))

(deftest depletion-borne-by-the-stock
  (let [[nodes edges] (graph)
        res (a/analyze nodes edges)]
    ;; sanma bears overfishing 0.9 + warming 0.6 = 1.5
    (is (< (Math/abs (- (get-in res [:depletion :stock.sanma-pacific]) 1.5)) 1e-9))))

(deftest edge-primary-no-stored-node-score
  ;; G2/N1: a node carries NO :bond/* score in the durable graph — karma is on edges, on read
  (let [[nodes _] (graph)]
    (is (not-any? (fn [n] (some #(= "bond" (namespace %)) (keys n))) (vals nodes)))))

(deftest rank-is-sorted-desc-and-limited
  (let [[nodes edges] (graph)
        res (a/analyze nodes edges)
        r (a/rank (:depletion res) nodes 3)]
    (is (= 3 (count r)))
    (is (apply >= (map #(nth % 2) r)))))

;; ── ontology ↔ code parity (the schema is the SSoT; code must not drift) ──────
(deftest ontology-matches-code
  (let [schema-file (-> this-file io/file .getAbsoluteFile .getParentFile .getParentFile
                        .getParentFile .getParentFile
                        (io/file "00-contracts" "schemas" "umisachi-ontology.kotoba.edn"))
        onto (edn/read-string (slurp schema-file))]
    (is (= (:access/weight onto) a/access-weight) "access-weight must match the ontology SSoT")
    (is (= (:method/forbidden onto) a/forbidden-methods) "G4 forbidden methods must match the ontology SSoT")))

(when (= *file* (System/getProperty "babashka.file"))
  (let [{:keys [fail error]} (run-tests 'umisachi.tests.test-analyze)]
    (System/exit (if (zero? (+ fail error)) 0 1))))
