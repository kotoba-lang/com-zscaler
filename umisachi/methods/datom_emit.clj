#!/usr/bin/env bb
(ns umisachi.methods.datom-emit
  "umisachi 海幸 — kotoba Datom-log emitter (canonical EAVT state, ADR-2605312345).

  Flattens the marine-bounty graph to append-only EAVT assertions [e a v tx op]. GROUND
  op :add is durable; DERIVED readouts (:bond/*) are :derived — the edge-primary integrals
  of analyze, computed on read (N1/G2), emitted only as a transient convenience view.

  Run:  bb --classpath 20-actors 20-actors/umisachi/methods/datom_emit.clj -> out/umisachi-datoms.kotoba.edn"
  (:require [umisachi.methods.analyze :as a]
            [clojure.java.io :as io]
            [clojure.string :as str]))

(def ^:private this-file *file*)
(defn- actor-root [] (-> this-file io/file .getAbsoluteFile .getParentFile .getParentFile))

(def node-attrs
  [:organism/kind :organism/label :organism/sourcing
   :stock/species :stock/status :stock/region
   :fishery/method :fishery/scale
   :logistics/kind :market/kind :market/access
   :pressure/kind :pressure/links])

(def edge-attrs [:en/kind :en/load :en/sourcing])

(defn- edge-id [e]
  (keyword (str "en." (name (:en/from e)) "." (name (:en/kind e)) "." (name (:en/to e)))))

(defn- node-datoms [nodes tx]
  (for [[nid n] nodes
        a node-attrs
        :when (some? (get n a))]
    (format "[%s %s %s %d :add]" (pr-str nid) (pr-str a) (pr-str (get n a)) tx)))

(defn- edge-datoms [edges tx]
  (for [e edges
        :let [eid (edge-id e)]
        a edge-attrs
        :when (some? (get e a))]
    (format "[%s %s %s %d :add]" (pr-str eid) (pr-str a) (pr-str (get e a)) tx)))

(defn- derived-datoms [res tx]
  (let [rows (fn [m attr]
               (for [[nid v] (sort-by (comp - val) m)]
                 (format "[%s %s %.4f %d :derived] ;; :bond/is-transient true"
                         (pr-str nid) attr (double v) tx)))]
    (concat
     [";; ── DERIVED readouts (transient; integral of incident 縁, computed on read) ──"]
     (rows (:nourishment res) ":bond/provisioning-nourishment")
     (rows (:depletion res) ":bond/depletion-load")
     (rows (:depletion-out res) ":bond/depletion-imposed"))))

(defn emit [nodes edges res tx]
  (str/join
   "\n"
   (concat
    [";; umisachi 海幸 — GENERATED kotoba Datom log (ADR-2606074200). DO NOT hand-edit."
     ";; Canonical EAVT state (ADR-2605312345). [e a v tx op]."
     ";; GROUND op :add = durable. DERIVED :bond/* = computed on read (N1/G2)."
     "["]
    (node-datoms nodes tx)
    (edge-datoms edges tx)
    (derived-datoms res tx)
    ["]" ""])))

(defn main [& argv]
  (let [seed (or (first (remove #(str/starts-with? % "--") argv))
                 (str (io/file (actor-root) "data" "seed-umisachi-graph.kotoba.edn")))
        tx (if-let [i (some (fn [[idx v]] (when (= v "--tx") idx))
                            (map-indexed vector argv))]
             (Integer/parseInt (nth argv (inc i))) 1)
        [nodes edges] (a/load-file seed)
        res (a/analyze nodes edges)
        out (io/file (actor-root) "out")]
    (.mkdirs out)
    (spit (io/file out "umisachi-datoms.kotoba.edn") (emit nodes edges res tx))
    (println (format "umisachi datom log → out/umisachi-datoms.kotoba.edn (%d nodes + %d 縁, tx=%d)"
                     (count nodes) (count edges) tx))))

(when (= *file* (System/getProperty "babashka.file"))
  (apply main *command-line-args*))
