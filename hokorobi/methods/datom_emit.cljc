(ns hokorobi.methods.datom-emit
  "hokorobi 綻び — kotoba Datom-log emitter (canonical EAVT state, ADR-2605312345).
  1:1 Clojure port of `methods/datom_emit.py` (ADR-2606073400).

  Projects the systemic finance-risk graph into append-only kotoba Datoms [e a v tx op].

    GROUND (durable, op :add) — node + 縁 datoms. This IS the Datom log.
    DERIVED (transient, :bond/is-transient true) — edge-primary systemic-risk / resilience
      integrals; computed on READ, NOT persisted (N1/G2).

  Reuses the already-ported analyzer (hokorobi.methods.analyze) for load/analyze/read-edn.

  House style (mirrors analyze.cljc): Python ':…' keyword strings stay literal strings;
  datoms are emitted as exact text (byte-for-byte the Python emit); float _fmt mirrors
  Python's f-string `{v:g}`; pure fns, file I/O only at the #?(:clj) edge."
  (:require [clojure.string :as str]
            [hokorobi.methods.analyze :as analyze]))

;; ── attribute emit order (NODE_ATTRS / EDGE_ATTRS) — exact Python list order ───────────────
(def node-attrs
  [":organism/kind" ":organism/label" ":organism/sourcing"
   ":inst/sector" ":inst/sii" ":inst/jurisdiction"
   ":risk/kind" ":bearer/kind"])

(def edge-attrs
  [":en/from" ":en/to" ":en/kind" ":en/risk-load" ":en/sourcing"])

;; ── Python `f"{v:g}"` faithful port ───────────────────────────────────────────────────────
;; %g (default precision 6 significant digits): strip trailing zeros, use exponent form for
;; very large/small magnitudes (here always fixed-point). Mirrors CPython float repr-via-%g.
(defn- fmt-g
  "Render a double as Python's `{v:g}` would: up to 6 significant digits, trailing zeros and
  trailing decimal point stripped, scientific notation only outside [1e-4, 1e16)."
  [v]
  (let [d (double v)]
    (cond
      (zero? d) "0"
      (Double/isNaN d) "nan"
      (Double/isInfinite d) (if (pos? d) "inf" "-inf")
      :else
      (let [neg (neg? d)
            a (Math/abs d)
            exp (long (Math/floor (Math/log10 a)))]
        (if (or (< exp -4) (>= exp 6))
          ;; scientific form — not exercised by this seed, but kept faithful to %g
          (let [s (format "%.5e" a)
                [mant e] (str/split s #"e")
                mant (if (str/includes? mant ".")
                       (str/replace (str/replace mant #"0+$" "") #"\.$" "")
                       mant)
                ei (Integer/parseInt e)]
            (str (when neg "-") mant "e" (if (neg? ei) "-" "+")
                 (format "%02d" (Math/abs ei))))
          ;; fixed form with (6 - 1 - exp) fractional digits, then strip trailing zeros
          (let [decimals (max 0 (- 5 exp))
                s (format (str "%." decimals "f") a)
                s (if (str/includes? s ".")
                    (str/replace (str/replace s #"0+$" "") #"\.$" "")
                    s)]
            (str (when neg "-") s)))))))

(defn fmt
  "Port of _fmt(v): bool → true/false; nil → nil; keyword-string ':…' stays literal; other
  string → JSON-escaped double-quoted; float → %g; else str(v)."
  [v]
  (cond
    (true? v) "true"
    (false? v) "false"
    (nil? v) "nil"
    (string? v) (if (str/starts-with? v ":")
                  v
                  (str "\"" (-> v
                                (str/replace "\\" "\\\\")
                                (str/replace "\"" "\\\""))
                       "\""))
    (or (float? v) (double? v)) (fmt-g v)
    :else (str v)))

;; ── derived-readout ordering: mirror Python's `sorted(d.items(), key=lambda kv: -kv[1])` over
;; an insertion-ordered dict — a STABLE sort by -value, ties keep first-touch order. The
;; analyzer returns ::order-carrying ordered-maps; expose their items in first-touch order.
(defn- omap-items
  [d]
  (let [order (::analyze/order (meta d))]
    (if order
      (map (fn [k] [k (get d k)]) order)
      (seq d))))

(defn- by-neg-value
  "Stable sort of [k v] pairs by -v (ties keep insertion order), mirroring the Python sort."
  [items]
  (sort-by (fn [[_ v]] (- v)) items))

(defn emit
  "Render the kotoba Datom log (EAVT) text, byte-identical to datom_emit.py's emit().
  nodes is an insertion-ordered map id→node; edges a vector of edge maps; res the analyze
  result {\"systemic\" .. \"resilience\" .. \"risk_out\" ..}."
  ([nodes edges res] (emit nodes edges res 1))
  ([nodes edges res tx]
   (let [L (transient [])]
     (conj! L ";; hokorobi 綻び — GENERATED kotoba Datom log (ADR-2606073400). DO NOT hand-edit.")
     (conj! L ";; Canonical EAVT state (ADR-2605312345). [e a v tx op].")
     (conj! L ";; GROUND op :add = durable. DERIVED :bond/is-transient = computed on read (N1/G2).")
     (conj! L "[")

     ;; node datoms — iterate nodes in insertion order, attrs in NODE_ATTRS order
     (doseq [nid (analyze/node-ids nodes)]
       (let [n (get nodes nid)]
         (doseq [a node-attrs]
           (let [av (get n a)]
             (when (and (contains? n a) (not (nil? av)))
               (conj! L (str "[" (fmt nid) " " a " " (fmt av) " " tx " :add]")))))))

     ;; edge datoms — id = en.<from>.<kind-without-colon>.<to>
     (doseq [e edges]
       (let [eid (str "en." (get e ":en/from") "."
                      (let [k (get e ":en/kind")]
                        (if (str/starts-with? k ":") (subs k 1) k))
                      "." (get e ":en/to"))]
         (doseq [a edge-attrs]
           (let [av (get e a)]
             (when (and (contains? e a) (not (nil? av)))
               (conj! L (str "[" (fmt eid) " " a " " (fmt av) " " tx " :add]")))))))

     ;; derived readouts (transient)
     (conj! L ";; ── DERIVED readouts (transient; integral of incident 縁, computed on read) ──")
     (doseq [[nid v] (by-neg-value (omap-items (get res "systemic")))]
       (conj! L (str "[" (fmt nid) " :bond/systemic-risk-concentration " (fmt-g v) " " tx
                     " :derived] ;; :bond/is-transient true")))
     (doseq [[nid v] (by-neg-value (omap-items (get res "resilience")))]
       (conj! L (str "[" (fmt nid) " :bond/resilience-buffer " (fmt-g v) " " tx
                     " :derived] ;; :bond/is-transient true")))
     (doseq [[nid v] (by-neg-value (omap-items (get res "risk_out")))]
       (conj! L (str "[" (fmt nid) " :bond/risk-imposed " (fmt-g v) " " tx
                     " :derived] ;; :bond/is-transient true")))

     (conj! L "]")
     (str (str/join "\n" (persistent! L)) "\n"))))

#?(:clj
   (defn -main
     "CLI entry: load seed EDN → analyze → emit Datom log → out/finrisk-datoms.kotoba.edn.
     File I/O only at this edge (mirrors datom_emit.py main)."
     [& argv]
     (let [argv (vec argv)
           here (-> *file* clojure.java.io/file .getParentFile .getParentFile)
           seed (if (and (seq argv) (not (str/starts-with? (first argv) "--")))
                  (clojure.java.io/file (first argv))
                  (clojure.java.io/file here "data" "seed-finrisk-graph.kotoba.edn"))
           outdir (if (some #{"--out"} argv)
                    (clojure.java.io/file (nth argv (inc (.indexOf argv "--out"))))
                    (clojure.java.io/file here "out"))
           tx (if (some #{"--tx"} argv)
                (Integer/parseInt (nth argv (inc (.indexOf argv "--tx"))))
                1)
           {:keys [nodes edges]} (analyze/load-file* seed)
           res (analyze/analyze nodes edges)]
       (.mkdirs outdir)
       (let [out (clojure.java.io/file outdir "finrisk-datoms.kotoba.edn")]
         (spit out (emit nodes edges res tx))
         (println (str "hokorobi datom log → " out " (" (count nodes) " nodes + "
                       (count edges) " 縁, tx=" tx ")")))
       0)))
