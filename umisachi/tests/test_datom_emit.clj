#!/usr/bin/env bb
(ns umisachi.tests.test-datom-emit
  "umisachi 海幸 — kotoba Datom-log emitter invariants (ADR-2606074200 / 2605312345).

  Deepens coverage of the previously-untested datom_emit.clj: EAVT shape, the
  GROUND :add vs DERIVED :derived distinction (G2/N1 — :bond/* readouts are
  transient, computed on read, NEVER asserted as durable state), determinism,
  tx-threading, and analyze↔emit derived-value parity.

  Run:  bb --classpath 20-actors 20-actors/umisachi/tests/test_datom_emit.clj"
  (:require [umisachi.methods.datom-emit :as de]
            [umisachi.methods.analyze :as a]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.test :refer [deftest is run-tests]]))

(def ^:private this-file *file*)
(defn- seed-path []
  (-> this-file io/file .getAbsoluteFile .getParentFile .getParentFile
      (io/file "data" "seed-umisachi-graph.kotoba.edn")))

(defn- graph [] (a/load-file (str (seed-path))))
(defn- emit-str
  ([tx] (let [[nodes edges] (graph)] (de/emit nodes edges (a/analyze nodes edges) tx)))
  ([] (emit-str 1)))

(defn- datom-lines [s]
  (->> (str/split-lines s)
       (filter #(str/starts-with? % "[:"))))

;; ── EAVT shape ──────────────────────────────────────────────────────────────

(deftest emits-eavt-header-and-bracketed-vector
  (let [s (emit-str)]
    (is (str/includes? s "[e a v tx op]"))
    (is (str/includes? s "Canonical EAVT state"))
    ;; opens and closes the EDN vector
    (is (some #(= "[" (str/trim %)) (str/split-lines s)))
    (is (some #(= "]" (str/trim %)) (str/split-lines s)))))

(deftest every-datom-is-five-element-eavt
  (let [s (emit-str 1)]
    (doseq [l (datom-lines s)]
      ;; strip the trailing ;; comment derived rows carry, then read as EDN
      (let [edn-part (str/trim (first (str/split l #";;")))
            v (clojure.edn/read-string edn-part)]
        (is (vector? v) l)
        (is (= 5 (count v)) l)
        (is (contains? #{:add :derived} (nth v 4)) l)
        (is (= 1 (nth v 3)) l)))))

;; ── GROUND :add vs DERIVED :derived (G2/N1) ──────────────────────────────────

(deftest ground-datoms-are-add-derived-are-derived
  (let [s (emit-str)
        lines (datom-lines s)
        add (filter #(str/ends-with? % " :add]") lines)
        der (filter #(str/includes? % ":derived]") lines)]
    (is (= 227 (count add)) "ground :add datoms")
    (is (= 25 (count der)) "derived :bond readout datoms")
    ;; every derived row is flagged transient (computed on read, never durable)
    (is (every? #(str/includes? % ":bond/is-transient true") der))))

(deftest derived-bond-attrs-never-asserted-as-ground
  ;; the G2/N1 invariant: a :bond/* readout must NEVER appear in a durable :add datom.
  (let [s (emit-str)
        add (filter #(str/ends-with? % " :add]") (datom-lines s))]
    (is (not-any? #(str/includes? % ":bond/") add)
        "no durable :add datom may carry a derived :bond/* attribute")))

(deftest derived-attrs-are-exactly-the-three-bond-readouts
  (let [s (emit-str)
        der (filter #(str/includes? % ":derived]") (datom-lines s))
        attrs (set (keep (fn [l] (re-find #":bond/[a-z-]+" l)) der))]
    (is (= #{":bond/provisioning-nourishment"
             ":bond/depletion-load"
             ":bond/depletion-imposed"} attrs))))

;; ── determinism ──────────────────────────────────────────────────────────────

(deftest emit-is-byte-deterministic
  (is (= (emit-str 1) (emit-str 1)))
  (is (= (emit-str 7) (emit-str 7))))

;; ── tx threading ─────────────────────────────────────────────────────────────

(deftest tx-is-threaded-into-every-datom
  (let [s (emit-str 7)]
    (doseq [l (datom-lines s)]
      (is (re-find #" 7 :(add|derived)\]" l) l))
    ;; and tx=1 does NOT leak when tx=7 requested
    (is (not (some #(re-find #" 1 :add\]" %) (datom-lines s))))))

;; ── analyze ↔ emit derived-value parity ──────────────────────────────────────

(deftest derived-rows-match-analyze-integrals
  (let [[nodes edges] (graph)
        res (a/analyze nodes edges)
        s (de/emit nodes edges res 1)
        der (filter #(str/includes? % ":derived]") (datom-lines s))
        ;; pull (nid, attr, value) from each derived line
        parse (fn [l] (let [v (clojure.edn/read-string (str/trim (first (str/split l #";;"))))]
                        [(nth v 0) (str (nth v 1)) (nth v 2)]))
        emitted (into {} (map (fn [[nid attr val]] [[nid attr] val]) (map parse der)))]
    ;; every analyze nourishment integral appears, 4-dp rounded, as a derived row
    (doseq [[nid v] (:nourishment res)]
      (let [got (get emitted [nid ":bond/provisioning-nourishment"])]
        (is (some? got) (str "missing nourishment row for " nid))
        (is (< (Math/abs (- got (double v))) 1e-4) (str nid " nourishment"))))
    (doseq [[nid v] (:depletion res)]
      (let [got (get emitted [nid ":bond/depletion-load"])]
        (is (some? got) (str "missing depletion row for " nid))
        (is (< (Math/abs (- got (double v))) 1e-4) (str nid " depletion"))))))

;; ── edge datoms ──────────────────────────────────────────────────────────────

(deftest edge-datoms-use-composite-en-id
  (let [s (emit-str)]
    ;; edge ids are en.<from>.<kind>.<to> and carry :en/kind / :en/load
    (is (re-find #"\[:en\.[a-z0-9.-]+ :en/kind " s))
    (is (str/includes? s ":en/sourcing"))))

(when (= *file* (System/getProperty "babashka.file"))
  (let [{:keys [fail error]} (run-tests 'umisachi.tests.test-datom-emit)]
    (System/exit (if (zero? (+ fail error)) 0 1))))
