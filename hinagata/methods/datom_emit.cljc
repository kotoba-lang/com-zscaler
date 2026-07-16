(ns hinagata.methods.datom-emit
  "hinagata 雛形 — kotoba Datom-log emitter (canonical EAVT state, ADR-2605312345).
  1:1 Clojure port of `methods/datom_emit.py` (ADR-2606111954).

  Projects the legal-template-commons graph into append-only kotoba Datoms [e a v tx op] —
  the first-class canonical state (NOT a projection cache). Two strata:

    GROUND (durable, op :add) — one datom per (entity, attribute, value): the template /
      clause / statute / jurisdiction / concept / license nodes and the :en/* 縁 (including
      every :cites-statute / :mandated-by edge that binds a clause to actual public law).
      This IS the Datom log: the template↔clause↔statute graph as durable, content-addressable
      state.

    DERIVED (transient, :bond/is-transient true) — the edge-primary groundedness / reusability /
      statute-pull integrals. Per N1/G2 these are computed on READ and are NOT stored as ground
      datoms; they are emitted in a clearly-flagged transient block (op :derived).

  Reuses hinagata.methods.analyze (read-edn / load-graph / load-file* / analyze / node-vals).
  House style: Python ':…' keyword strings stay strings; the emitted Datom text is
  byte-identical to the Python emit. Float formatting mirrors Python's `{v:g}`.

  NODE ORDERING: the Python emitter walks nodes via `for nid in nodes` (EDN read / dict
  insertion order), so insertion order IS significant — emit iterates nodes via
  analyze/node-vals, which threads the ::node-order metadata = EDN first-touch order (a plain
  hash-map loses insertion order beyond 8 keys). This makes node-datom emission byte-parity
  with the Python dict iteration order.

  G1: a COMMONS of public openly-licensed templates — never advice; statute links are DISCLOSED
  structural facts (this clause cites this article), never verdicts (N3)."
  (:require [clojure.string :as str]
            [hinagata.methods.analyze :as analyze]
            #?(:clj [clojure.java.io :as io])))

;; attributes promoted from each node/edge map into ground datoms (stable order = determinism)
(def node-attrs
  [":lt/kind" ":lt/label" ":lt/sourcing" ":lt/links"
   ":template/title" ":template/lang" ":template/license" ":template/version"
   ":template/stance" ":template/body-cid"
   ":clause/role" ":clause/optionality"
   ":statute/citation" ":statute/instrument" ":statute/jurisdiction" ":statute/url"
   ":jurisdiction/code" ":jurisdiction/system"
   ":concept/code" ":license/spdx"])

(def edge-attrs
  [":en/from" ":en/to" ":en/kind" ":en/binding-load" ":en/force" ":en/sourcing"])

(defn- fmt-g
  "Mirror Python's f-string `{v:g}` for our (moderate-magnitude) doubles: 6 significant
  digits, trailing zeros stripped, an integral value renders with no decimal point."
  [v]
  (let [d (double v)]
    (if (and (not (Double/isInfinite d)) (not (Double/isNaN d))
             (== d (Math/rint d)) (< (Math/abs d) 1e15))
      (str (long d))
      (let [s (format "%.6g" d)]
        (if (str/includes? s ".")
          (-> s (str/replace #"0+$" "") (str/replace #"\.$" ""))
          s)))))

(defn fmt
  "Port of _fmt: bool → true/false; nil → nil; \":…\" kept literal; other string → quoted
  with \\ and \" escaped; float (double) → {v:g}; else str()."
  [v]
  (cond
    (true? v) "true"
    (false? v) "false"
    (nil? v) "nil"
    (string? v) (if (str/starts-with? v ":")
                  v
                  (str "\"" (-> v (str/replace "\\" "\\\\") (str/replace "\"" "\\\"")) "\""))
    (double? v) (fmt-g v)
    :else (str v)))

(defn- lstrip-colon
  "Python str.lstrip(':') — strip leading ':' chars."
  [s]
  (str/replace s #"^:+" ""))

(defn- sort-readout
  "sorted(d.items(), key=lambda kv: (-kv[1], kv[0])) — value desc, then id asc."
  [d]
  (sort-by (fn [[nid v]] [(- (double v)) nid]) d))

(defn emit
  "Faithful 1:1 of datom_emit.emit. Returns the kotoba Datom-log EDN text (trailing newline)."
  ([nodes edges res] (emit nodes edges res 1))
  ([nodes edges res tx]
   (let [L (transient [])]
     (conj! L ";; hinagata 雛形 — GENERATED kotoba Datom log (ADR-2606111954). DO NOT hand-edit.")
     (conj! L ";; Canonical EAVT state (ADR-2605312345). [e a v tx op].")
     (conj! L ";; GROUND op :add = durable. DERIVED :bond/is-transient = computed on read (N1/G2).")
     (conj! L ";; G1: a COMMONS of public openly-licensed templates — never advice; statute links")
     (conj! L ";; are DISCLOSED structural facts (this clause cites this article), never verdicts (N3).")
     (conj! L "[")

     ;; ── GROUND: node datoms (EDN read order via ::node-order; deterministic)
     (doseq [n (analyze/node-vals nodes)]
       (let [nid (get n ":lt/id")]
         (doseq [a node-attrs]
           (when (and (contains? n a) (some? (get n a)))
             (conj! L (str "[" (fmt nid) " " a " " (fmt (get n a)) " " tx " :add]"))))))

     ;; ── GROUND: edge datoms (edge entity id is content-stable: en.<from>.<kind>.<to>)
     (doseq [e edges]
       (let [eid (str "en." (get e ":en/from") "." (lstrip-colon (get e ":en/kind")) "." (get e ":en/to"))]
         (doseq [a edge-attrs]
           (when (and (contains? e a) (some? (get e a)))
             (conj! L (str "[" (fmt eid) " " a " " (fmt (get e a)) " " tx " :add]"))))))

     ;; ── DERIVED (transient — NOT persisted; N1/G2)
     (conj! L ";; ── DERIVED readouts (transient; integral of incident 縁, computed on read) ──")
     (doseq [[nid v] (sort-readout (get res "grounded"))]
       (conj! L (str "[" (fmt nid) " :bond/groundedness " (fmt-g v) " " tx " :derived] ;; :bond/is-transient true")))
     (doseq [[nid v] (sort-readout (get res "reuse"))]
       (conj! L (str "[" (fmt nid) " :bond/reusability " (fmt-g v) " " tx " :derived] ;; :bond/is-transient true")))
     (doseq [[nid v] (sort-readout (get res "statute_pull"))]
       (conj! L (str "[" (fmt nid) " :bond/statute-pull " (fmt-g v) " " tx " :derived] ;; :bond/is-transient true")))

     (conj! L "]")
     (str (str/join "\n" (persistent! L)) "\n"))))

#?(:clj
   (defn -main
     "CLI entry: analyze a seed EDN graph → out/legal-template-datoms.kotoba.edn (file I/O at the edge)."
     [& argv]
     (let [argv (vec argv)
           here (-> *file* io/file .getParentFile .getParentFile)
           seed (if (and (seq argv) (not (str/starts-with? (first argv) "--")))
                  (io/file (first argv))
                  (io/file here "data" "seed-legal-template-graph.kotoba.edn"))
           outdir (if (some #{"--out"} argv)
                    (io/file (nth argv (inc (.indexOf argv "--out"))))
                    (io/file here "out"))
           tx (if (some #{"--tx"} argv)
                (Long/parseLong (nth argv (inc (.indexOf argv "--tx"))))
                1)
           {:keys [nodes edges]} (analyze/load-file* seed)
           res (analyze/analyze nodes edges)
           out (io/file outdir "legal-template-datoms.kotoba.edn")
           text (emit nodes edges res tx)]
       (.mkdirs outdir)
       (spit out text)
       (println (str "hinagata datom log → " out " ("
                     (count nodes) " nodes + " (count edges) " 縁, tx=" tx ")"))
       0)))
