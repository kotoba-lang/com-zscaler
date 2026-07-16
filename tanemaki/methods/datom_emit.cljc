(ns tanemaki.methods.datom-emit
  "tanemaki 種蒔き — kotoba Datom-log emitter (canonical EAVT state, ADR-2605312345).
  1:1 Clojure port of `methods/datom_emit.py` (ADR-2606122001).

  Projects the fund-stewardship graph into append-only kotoba Datoms [e a v tx op] — first-class
  canonical state. GROUND (durable, :add) = the org / screen / criterion / source / instrument /
  milestone nodes and their :en/* 縁 (incl. every :screened finding — the PUBLIC DD trail — and
  the disclosed rubric weights). DERIVED (transient, :bond/is-transient) = dd-fit / evidence-
  coverage / route, computed on read (N1/G4), never persisted — there is no stored org score.

  Reuses tanemaki.methods.analyze (load-file* / read-edn / load-graph / analyze). House style:
  Python ':…' keyword strings stay strings; the emitted Datom text is byte-identical to the
  Python emit. Float formatting mirrors Python's `{v:g}` (6 significant digits, trailing zeros
  stripped, integral floats lose the point).

  NODE ORDERING (byte-parity): the Python `for nid in nodes` walks the dict in EDN read order.
  tanemaki.methods.analyze/load-graph preserves that first-touch node order via ::order metadata
  on the :nodes map, so this ns walks nodes in EDN read order. The DERIVED block walks
  `sorted(res[\"orgs\"])` — a plain alphabetical sort of the org ids."
  (:require [clojure.string :as str]
            [tanemaki.methods.analyze :as analyze]
            #?(:clj [clojure.java.io :as io])))

;; attributes promoted from each node/edge map into ground datoms (stable order = determinism)
(def node-attrs
  [":fs/kind" ":fs/label" ":fs/sourcing" ":fs/links"
   ":org/form" ":org/synthetic" ":org/mission-axis"
   ":screen/code" ":screen/basis"
   ":criterion/code" ":criterion/weight" ":criterion/axis"
   ":source/actor" ":source/nature"
   ":instrument/kind" ":milestone/evidence"])

(def edge-attrs
  [":en/from" ":en/to" ":en/kind" ":en/finding" ":en/weight"
   ":en/evidence" ":en/sourcing"])

(defn- fmt-g
  "Mirror Python's f-string `{v:g}` for our (moderate-magnitude) doubles: 6 significant
  digits, trailing zeros stripped, an integral value renders with no decimal point (1.0 → \"1\")."
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

(defn- node-order
  "First-touch node-id order: ::order metadata if present (load-graph), else (keys nodes)."
  [nodes]
  (or (:tanemaki.methods.analyze/order (meta nodes)) (keys nodes)))

(defn emit
  "Faithful 1:1 of datom_emit.emit. Returns the kotoba Datom-log EDN text (trailing newline)."
  ([nodes edges res] (emit nodes edges res 1))
  ([nodes edges res tx]
   (let [L (transient [])]
     (conj! L ";; tanemaki 種蒔き — GENERATED kotoba Datom log (ADR-2606122001). DO NOT hand-edit.")
     (conj! L ";; Canonical EAVT state (ADR-2605312345). [e a v tx op].")
     (conj! L ";; GROUND op :add = durable (incl. the PUBLIC :screened DD trail + disclosed rubric weights).")
     (conj! L ";; DERIVED :bond/is-transient = computed on read (N1/G4) — no stored org score, no decision.")
     (conj! L ";; G1: tanemaki is a steward, never a sovereign; every grant is decided by 1 SBT = 1 vote.")
     (conj! L "[")

     ;; ── GROUND: node datoms (insertion / EDN read order → deterministic)
     (doseq [nid (node-order nodes)]
       (let [nd (get nodes nid)]
         (doseq [a node-attrs]
           (let [v (get nd a)]
             (when (and (contains? nd a) (not (nil? v)))
               (conj! L (str "[" (fmt nid) " " a " " (fmt v) " " tx " :add]")))))))

     ;; ── GROUND: edge datoms (edge entity id is content-stable: en.<from>.<kind>.<to>)
     (doseq [e edges]
       (let [eid (str "en." (get e ":en/from") "."
                      (let [k (get e ":en/kind")] (if (str/starts-with? k ":") (subs k 1) k))
                      "." (get e ":en/to"))]
         (doseq [a edge-attrs]
           (let [v (get e a)]
             (when (and (contains? e a) (not (nil? v)))
               (conj! L (str "[" (fmt eid) " " a " " (fmt v) " " tx " :add]")))))))

     ;; ── DERIVED (transient — NOT persisted; N1/G4) — sorted org id
     (conj! L ";; ── DERIVED readouts (transient; integral of incident 縁, computed on read) ──")
     (doseq [oid (sort (keys (get res "orgs")))]
       (let [r (get-in res ["orgs" oid])]
         (conj! L (str "[" (fmt oid) " :bond/dd-fit " (fmt-g (get r "dd_fit"))
                       " " tx " :derived] ;; :bond/is-transient true"))
         (conj! L (str "[" (fmt oid) " :bond/evidence-coverage " (fmt-g (get r "evidence_coverage"))
                       " " tx " :derived] ;; :bond/is-transient true"))
         (conj! L (str "[" (fmt oid) " :bond/route " (get r "route")
                       " " tx " :derived] ;; :bond/is-transient true"))))

     (conj! L "]")
     (str (str/join "\n" (persistent! L)) "\n"))))

#?(:clj
   (defn -main
     "CLI entry: analyze a seed EDN graph → out/stewardship-datoms.kotoba.edn (file I/O at the edge)."
     [& argv]
     (let [argv (vec argv)
           here (-> *file* io/file .getParentFile .getParentFile)
           seed (if (and (seq argv) (not (str/starts-with? (first argv) "--")))
                  (io/file (first argv))
                  (io/file here "data" "seed-stewardship-graph.kotoba.edn"))
           outdir (if (some #{"--out"} argv)
                    (io/file (nth argv (inc (.indexOf argv "--out"))))
                    (io/file here "out"))
           tx (if (some #{"--tx"} argv)
                (Long/parseLong (nth argv (inc (.indexOf argv "--tx"))))
                1)
           {:keys [nodes edges]} (analyze/load-file* seed)
           res (analyze/analyze nodes edges)
           out (io/file outdir "stewardship-datoms.kotoba.edn")]
       (.mkdirs outdir)
       (spit out (emit nodes edges res tx))
       (println (str "tanemaki datom log → " out " (" (count nodes) " nodes + " (count edges)
                     " 縁, tx=" tx ")"))
       0)))
