(ns hoshimori.methods.datom-emit
  "hoshimori 星守 — kotoba Datom-log emitter (canonical EAVT state, ADR-2605312345).
  1:1 Clojure port of `methods/datom_emit.py` (ADR-2606073600).

  Projects the orbital graph into append-only kotoba Datoms [e a v tx op].

    GROUND (durable, op :add) — node + 縁 datoms. This IS the Datom log.
    DERIVED (transient, :bond/is-transient true) — edge-primary congestion / stewardship /
      fragility integrals; computed on READ, NOT persisted (N1/G2).

  G1: only orbital-shell / regime-aggregate band labels are emitted — NO precise predictive
  ephemeris / interception-grade state vector (the component cannot leak what it never holds).
  No :geo/lat / :geo/lon / :eph/* / :tle/* / :obj/altitude-km datom can appear: the emitter
  only writes the NODE_ATTRS / EDGE_ATTRS allow-lists below + shell/regime-aggregate derived
  readouts — there is no code path that emits a per-object positional attribute.

  House style (mirrors analyze.cljc): Python ':…' keyword strings stay strings; node ids /
  edge ids / endpoint ids are quoted strings; pure fns; file I/O only at edges. Portable .cljc."
  (:require [clojure.string :as str]
            [hoshimori.methods.analyze :as analyze]))

;; ── attribute allow-lists (mirror NODE_ATTRS / EDGE_ATTRS in datom_emit.py) ──
;; Only these keys are ever emitted as ground datoms. No positional / ephemeris key is on
;; either list, so G1 (no precise predictive ephemeris) holds by construction.
(def node-attrs
  [":organism/kind" ":organism/label" ":organism/sourcing"
   ":shell/regime" ":shell/alt-band-km" ":op/kind" ":op/jurisdiction"
   ":hazard/kind" ":service/kind"])

(def edge-attrs
  [":en/from" ":en/to" ":en/kind" ":en/orbit-load" ":en/sourcing"])

(defn- fmt-g
  "Port of Python f\"{v:g}\" for a double: 6 significant digits, trailing zeros stripped,
  trailing '.' dropped (1.0 → \"1\"). Java's %.6g matches CPython's default %g precision."
  [v]
  (let [s (format "%.6g" (double v))]
    (if (re-find #"[eE]" s)
      s
      (if (str/includes? s ".")
        (-> s (str/replace #"0+$" "") (str/replace #"\.$" ""))
        s))))

(defn fmt
  "Port of _fmt: True/False/None → true/false/nil; \":…\" / non-\"… string → as-is keyword,
  else quoted+escaped string; float → %g; else str(v).

  In this string-keyed pipeline (mirroring analyze.cljc) keyword values are kept as \":…\"
  strings, ints are longs, floats are doubles."
  [v]
  (cond
    (true? v) "true"
    (false? v) "false"
    (nil? v) "nil"
    (string? v)
    (if (str/starts-with? v ":")
      v
      (str "\"" (-> v (str/replace "\\" "\\\\") (str/replace "\"" "\\\"")) "\""))
    (double? v) (fmt-g v)
    :else (str v)))

(defn emit
  "Render the kotoba Datom log (EAVT) text, byte-identical to datom_emit.py's emit().
  `nodes` is the insertion-ordered nodes-by-id map; `edges` the 縁 vector; `res` the analyze
  result; `tx` the transaction number (default 1)."
  ([nodes edges res] (emit nodes edges res 1))
  ([nodes edges res tx]
   (let [L (transient [])]
     (conj! L ";; hoshimori 星守 — GENERATED kotoba Datom log (ADR-2606073600). DO NOT hand-edit.")
     (conj! L ";; Canonical EAVT state (ADR-2605312345). [e a v tx op].")
     (conj! L ";; GROUND op :add = durable. DERIVED :bond/is-transient = computed on read (N1/G2).")
     (conj! L ";; G1: shell/regime-aggregate only — NO precise predictive ephemeris.")
     (conj! L "[")

     ;; GROUND — node datoms (in nodes insertion order; only NODE_ATTRS, only non-nil)
     (doseq [nid (analyze/node-ids nodes)]
       (let [n (get nodes nid)]
         (doseq [a node-attrs]
           (when (and (contains? n a) (some? (get n a)))
             (conj! L (str "[" (fmt nid) " " a " " (fmt (get n a)) " " tx " :add]"))))))

     ;; GROUND — edge (縁) datoms. Edge id = "en.<from>.<kind sans-leading-colon>.<to>".
     (doseq [e edges]
       (let [eid (str "en." (get e ":en/from") "."
                      (str/replace (get e ":en/kind") #"^:+" "") "."
                      (get e ":en/to"))]
         (doseq [a edge-attrs]
           (when (and (contains? e a) (some? (get e a)))
             (conj! L (str "[" (fmt eid) " " a " " (fmt (get e a)) " " tx " :add]"))))))

     ;; DERIVED readouts (transient; integral of incident 縁, computed on read) — never stored
     (conj! L ";; ── DERIVED readouts (transient; integral of incident 縁, computed on read) ──")
     (doseq [[a* d] [[":bond/congestion-concentration" "congestion"]
                     [":bond/stewardship-buffer" "stewardship"]
                     [":bond/dependency-fragility" "fragility"]
                     [":bond/congestion-imposed" "congestion_out"]]]
       (doseq [[nid v] (sort-by (fn [[_ v]] (- v)) (#'analyze/omap-items (get res d)))]
         (conj! L (str "[" (fmt nid) " " a* " " (fmt-g v) " " tx
                       " :derived] ;; :bond/is-transient true"))))

     (conj! L "]")
     (str (str/join "\n" (persistent! L)) "\n"))))

#?(:clj
   (defn -main
     "CLI entry: analyze a seed EDN graph → out/orbit-datoms.kotoba.edn (file I/O at the edge).
     Mirrors datom_emit.py's main(argv)."
     [& argv]
     (let [argv (vec argv)
           here (-> *file* clojure.java.io/file .getParentFile .getParentFile)
           seed (if (and (seq argv) (not (str/starts-with? (first argv) "--")))
                  (clojure.java.io/file (first argv))
                  (clojure.java.io/file here "data" "seed-orbit-graph.kotoba.edn"))
           outdir (if (some #{"--out"} argv)
                    (clojure.java.io/file (nth argv (inc (.indexOf argv "--out"))))
                    (clojure.java.io/file here "out"))
           tx (if (some #{"--tx"} argv)
                (Long/parseLong (nth argv (inc (.indexOf argv "--tx"))))
                1)
           {:keys [nodes edges]} (analyze/load-file* seed)
           res (analyze/analyze nodes edges)
           out (clojure.java.io/file outdir "orbit-datoms.kotoba.edn")]
       (.mkdirs outdir)
       (spit out (emit nodes edges res tx))
       (println (str "hoshimori datom log → " out " (" (count nodes) " nodes + "
                     (count edges) " 縁, tx=" tx ")"))
       0)))
