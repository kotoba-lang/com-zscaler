(ns tsugite.methods.datom-emit
  "tsugite 継ぎ手 — kotoba Datom-log emitter (canonical EAVT state, ADR-2605312345).
  1:1 Clojure port of `methods/datom_emit.py` (ADR-2606073800).

  Projects the peoples-continuity graph into append-only kotoba Datoms [e a v tx op] — the
  first-class canonical state (NOT a projection cache). Two strata:

    GROUND (durable, op :add) — one datom per (entity, attribute, value): the people /
      language / pressure / haven nodes and the :en/* 縁. This IS the Datom log.

    DERIVED (transient, :bond/is-transient true) — the edge-primary continuity / protection /
      fragility / pressure integrals. Per N1/G2 these are computed on READ and are NOT stored
      as ground datoms; they are emitted in a clearly-flagged transient block so a reader can
      materialise them without mistaking them for persisted state.

  G1 (constitutional): only collective-aggregate nodes are emitted — NO individual records /
  location / biometric. Every :people carries :people/scope :aggregate; emit carries no
  individual / locator / biometric attribute.

  Reuses tsugite.methods.analyze (load-graph / read-edn / analyze). House style: Python ':…'
  keyword strings stay strings (incl. all :*/* and :db/add-shaped attrs); the emitted Datom
  text is byte-identical to the Python emit. Float formatting mirrors Python's `{v:g}` (6
  significant digits, trailing zeros stripped, integral floats lose the point).

  NODE ORDERING (byte-parity): the Python `for nid in nodes` walks the dict in EDN read order.
  tsugite.methods.analyze/load-graph returns an array-map (order lost beyond 8 keys), so this
  ns re-derives the first-touch node-id order from the parsed forms and threads it via
  ::node-order metadata on the nodes map (load-file*), falling back to (keys nodes)."
  (:require [clojure.string :as str]
            [tsugite.methods.analyze :as analyze]
            #?(:clj [clojure.java.io :as io])))

;; attributes promoted from each node/edge map into ground datoms (stable order = determinism)
(def node-attrs
  [":organism/kind" ":organism/label" ":organism/sourcing"
   ":people/kind" ":people/scope" ":lang/family" ":lang/vitality"
   ":lang/revitalizing?" ":pressure/kind" ":haven/kind"])

(def edge-attrs
  [":en/from" ":en/to" ":en/kind" ":en/peril-load" ":en/sourcing"])

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
  "First-touch node-id order: ::node-order metadata if present (load-file*), else (keys nodes)."
  [nodes]
  (or (::node-order (meta nodes)) (keys nodes)))

(defn- order-of
  "First-touch insertion order of an analyze accumulation map (analyze's ::order meta on the
  accumulation maps), else (keys). The keyword is qualified to tsugite.methods.analyze."
  [d]
  (or (:tsugite.methods.analyze/order (meta d)) (keys d)))

(defn- ranked-items
  "res[k].items() in first-touch order, stably sorted by -value (mirrors Python's
  `sorted(res[k].items(), key=lambda kv: -kv[1])` over an insertion-ordered dict)."
  [d]
  (sort-by (fn [nid] (- (double (get d nid)))) (order-of d)))

(defn emit
  "Faithful 1:1 of datom_emit.emit. Returns the kotoba Datom-log EDN text (trailing newline)."
  ([nodes edges res] (emit nodes edges res 1))
  ([nodes edges res tx]
   (let [L (transient [])]
     (conj! L ";; tsugite 継ぎ手 — GENERATED kotoba Datom log (ADR-2606073800). DO NOT hand-edit.")
     (conj! L ";; Canonical EAVT state (ADR-2605312345). [e a v tx op].")
     (conj! L ";; GROUND op :add = durable. DERIVED :bond/is-transient = computed on read (N1/G2).")
     (conj! L ";; G1: collective-aggregate only — NO individual records / location / biometric.")
     (conj! L "[")

     ;; ── GROUND: node datoms (insertion / EDN read order → deterministic)
     (doseq [nid (node-order nodes)]
       (let [n (get nodes nid)]
         (doseq [a node-attrs]
           (let [v (get n a)]
             (when (and (contains? n a) (not (nil? v)))
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

     ;; ── DERIVED (transient — NOT persisted; N1/G2)
     (conj! L ";; ── DERIVED readouts (transient; integral of incident 縁, computed on read) ──")
     (doseq [nid (ranked-items (get res "continuity"))]
       (conj! L (str "[" (fmt nid) " :bond/continuity-need " (fmt-g (get-in res ["continuity" nid]))
                     " " tx " :derived] ;; :bond/is-transient true")))
     (doseq [nid (ranked-items (get res "protection"))]
       (conj! L (str "[" (fmt nid) " :bond/protection-buffer " (fmt-g (get-in res ["protection" nid]))
                     " " tx " :derived] ;; :bond/is-transient true")))
     (doseq [nid (ranked-items (get res "fragility"))]
       (conj! L (str "[" (fmt nid) " :bond/transmission-fragility " (fmt-g (get-in res ["fragility" nid]))
                     " " tx " :derived] ;; :bond/is-transient true")))
     (doseq [nid (ranked-items (get res "pressure_out"))]
       (conj! L (str "[" (fmt nid) " :bond/pressure-imposed " (fmt-g (get-in res ["pressure_out" nid]))
                     " " tx " :derived] ;; :bond/is-transient true")))

     (conj! L "]")
     (str (str/join "\n" (persistent! L)) "\n"))))

(defn load-graph*
  "Like analyze/load-graph but attaches ::node-order (first-touch node-id order from the
  parsed forms) to the returned :nodes map, so emit walks nodes in EDN read order."
  [forms]
  (let [{:keys [nodes edges]} (analyze/load-graph forms)
        order (->> forms
                   (filter map?)
                   (filter #(contains? % ":organism/id"))
                   (mapv #(get % ":organism/id"))
                   (distinct)
                   (vec))]
    {:nodes (with-meta nodes {::node-order order}) :edges edges}))

#?(:clj
   (defn load-file*
     "Read + parse a peoples EDN graph file → {:nodes :edges} with ::node-order metadata."
     [path]
     (load-graph* (analyze/read-edn (slurp (str path))))))

#?(:clj
   (defn -main
     "CLI entry: analyze a seed EDN graph → out/peoples-datoms.kotoba.edn (file I/O at the edge)."
     [& argv]
     (let [argv (vec argv)
           here (-> *file* io/file .getParentFile .getParentFile)
           seed (if (and (seq argv) (not (str/starts-with? (first argv) "--")))
                  (io/file (first argv))
                  (io/file here "data" "seed-peoples-graph.kotoba.edn"))
           outdir (if (some #{"--out"} argv)
                    (io/file (nth argv (inc (.indexOf argv "--out"))))
                    (io/file here "out"))
           tx (if (some #{"--tx"} argv)
                (Long/parseLong (nth argv (inc (.indexOf argv "--tx"))))
                1)
           {:keys [nodes edges]} (load-file* seed)
           res (analyze/analyze nodes edges)
           out (io/file outdir "peoples-datoms.kotoba.edn")]
       (.mkdirs outdir)
       (spit out (emit nodes edges res tx))
       (println (str "tsugite datom log → " out " (" (count nodes) " nodes + " (count edges)
                     " 縁, tx=" tx ")"))
       0)))
