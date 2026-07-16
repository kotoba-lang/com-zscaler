(ns asobi.methods.datom-emit
  "asobi 遊び — kotoba Datom-log emitter (canonical EAVT state, ADR-2605312345).
  1:1 Clojure port of `methods/datom_emit.py` (ADR-2606073200).

  Projects the freed-time play/expression graph into append-only kotoba Datoms [e a v tx op].

    GROUND (durable, op :add) — node + 縁 datoms. This IS the Datom log.
    DERIVED (transient, :bond/is-transient true) — edge-primary openness / enclosure
      integrals; computed on READ, NOT persisted (N1/G2).

  Reuses the already-ported analyzer (`asobi.methods.analyze`): load-file* / analyze. The
  emit TEXT is byte-identical to the Python emitter — float :g formatting, datom ordering,
  the ';; ── DERIVED readouts …' comment, and the ground vs transient/derived split all
  mirror datom_emit.py exactly. Pure fns; file I/O only at the #?(:clj) -main edge."
  (:require [clojure.string :as str]
            [asobi.methods.analyze :as analyze]))

;; NODE_ATTRS / EDGE_ATTRS — emission order of node & edge attributes (mirrors datom_emit.py).
(def node-attrs
  [":organism/kind" ":organism/label" ":organism/sourcing"
   ":work/medium" ":work/access" ":practice/domain" ":practice/body?"
   ":venue/kind" ":venue/open?" ":enclosure/kind" ":enclosure/links"])

(def edge-attrs
  [":en/from" ":en/to" ":en/kind" ":en/access-load" ":en/sourcing"])

;; ── Python `f"{v:g}"` (C printf %g, default precision 6): shortest of %f/%e, trailing
;; zeros (and a bare trailing '.') stripped. Mirrors CPython's float __format__('g') so
;; the emitted float text is byte-identical.
(defn- fmt-g
  [^double v]
  (cond
    (Double/isNaN v) "nan"
    (Double/isInfinite v) (if (pos? v) "inf" "-inf")
    (zero? v) (if (neg? (Double/doubleToRawLongBits v)) "-0" "0")
    :else
    (let [p 6
          neg (neg? v)
          a (Math/abs v)
          ;; decimal exponent X such that a = m × 10^X, 1 ≤ m < 10 (as %e would print).
          ;; Derive X from the %e rendering so rounding at precision p-1 is reflected.
          e-str (format (str "%." (dec p) "e") a)
          x (Long/parseLong (subs e-str (inc (str/index-of e-str "e"))))
          body
          (if (and (>= x -4) (< x p))
            ;; %f with precision (p - 1 - x)
            (let [prec (- (dec p) x)
                  s (format (str "%." (int (max 0 prec)) "f") a)]
              (if (str/includes? s ".")
                (let [s (str/replace s #"0+$" "")]
                  (str/replace s #"\.$" ""))
                s))
            ;; %e with precision (p - 1), then strip trailing zeros in mantissa + normalize exp
            (let [s e-str
                  ei (str/index-of s "e")
                  mant (subs s 0 ei)
                  expo (subs s ei)
                  mant (if (str/includes? mant ".")
                         (str/replace (str/replace mant #"0+$" "") #"\.$" "")
                         mant)
                  ;; %g exponent: at least 2 digits, sign always present (e+NN / e-NN)
                  esign (nth expo 1)
                  edigits (subs expo 2)
                  edigits (str/replace edigits #"^0+(?=\d)" "")
                  edigits (if (< (count edigits) 2)
                            (str (apply str (repeat (- 2 (count edigits)) "0")) edigits)
                            edigits)]
              (str mant "e" esign edigits)))]
      (if neg (str "-" body) body))))

(defn fmt
  "Port of _fmt: bool/nil/keyword-string/string/float → datom text token."
  [v]
  (cond
    (true? v) "true"
    (false? v) "false"
    (nil? v) "nil"
    (string? v)
    (if (str/starts-with? v ":")
      v
      (str "\"" (-> v (str/replace "\\" "\\\\") (str/replace "\"" "\\\"")) "\""))
    (and (number? v) (or (instance? Double v) (instance? Float v)))
    (fmt-g (double v))
    :else (str v)))

(defn- omap-sorted-desc
  "sorted(d.items(), key=lambda kv: -kv[1]) over a Python insertion-ordered dict:
  first-touch insertion order, then a STABLE sort by -value (ties keep insertion order)."
  [d]
  (let [order (::analyze/order (meta d))
        items (if order (map (fn [k] [k (get d k)]) order) (seq d))]
    (sort-by (fn [[_ v]] (- (double v))) items)))

(defn emit
  "Render the EAVT datom-log text (byte-identical to datom_emit.py emit)."
  ([nodes edges res] (emit nodes edges res 1))
  ([nodes edges res tx]
   (let [L (transient [])]
     (conj! L ";; asobi 遊び — GENERATED kotoba Datom log (ADR-2606073200). DO NOT hand-edit.")
     (conj! L ";; Canonical EAVT state (ADR-2605312345). [e a v tx op].")
     (conj! L ";; GROUND op :add = durable. DERIVED :bond/is-transient = computed on read (N1/G2).")
     (conj! L "[")

     ;; ground node datoms — node insertion order, NODE_ATTRS order
     (doseq [nid (analyze/node-ids nodes)]
       (let [n (get nodes nid)]
         (doseq [a node-attrs]
           (when (and (contains? n a) (some? (get n a)))
             (conj! L (str "[" (fmt nid) " " a " " (fmt (get n a)) " " tx " :add]"))))))

     ;; ground edge datoms
     (doseq [e edges]
       (let [eid (str "en." (get e ":en/from") "."
                      (let [k (get e ":en/kind")]
                        (if (str/starts-with? (str k) ":") (subs k 1) k))
                      "." (get e ":en/to"))]
         (doseq [a edge-attrs]
           (when (and (contains? e a) (some? (get e a)))
             (conj! L (str "[" (fmt eid) " " a " " (fmt (get e a)) " " tx " :add]"))))))

     (conj! L ";; ── DERIVED readouts (transient; integral of incident 縁, computed on read) ──")
     (doseq [[nid v] (omap-sorted-desc (get res "openness"))]
       (conj! L (str "[" (fmt nid) " :bond/participation-openness " (fmt-g (double v)) " " tx " :derived] ;; :bond/is-transient true")))
     (doseq [[nid v] (omap-sorted-desc (get res "enclosure"))]
       (conj! L (str "[" (fmt nid) " :bond/enclosure-load " (fmt-g (double v)) " " tx " :derived] ;; :bond/is-transient true")))
     (doseq [[nid v] (omap-sorted-desc (get res "enclosure_out"))]
       (conj! L (str "[" (fmt nid) " :bond/enclosure-imposed " (fmt-g (double v)) " " tx " :derived] ;; :bond/is-transient true")))

     (conj! L "]")
     (str (str/join "\n" (persistent! L)) "\n"))))

#?(:clj
   (defn -main
     "CLI entry: emit the seed graph as an EAVT datom log → out/asobi-datoms.kotoba.edn."
     [& argv]
     (let [argv (vec argv)
           here (-> *file* clojure.java.io/file .getParentFile .getParentFile)
           seed (if (and (seq argv) (not (str/starts-with? (first argv) "--")))
                  (clojure.java.io/file (first argv))
                  (clojure.java.io/file here "data" "seed-asobi-graph.kotoba.edn"))
           outdir (if (some #{"--out"} argv)
                    (clojure.java.io/file (nth argv (inc (.indexOf argv "--out"))))
                    (clojure.java.io/file here "out"))
           tx (if (some #{"--tx"} argv)
                (Long/parseLong (nth argv (inc (.indexOf argv "--tx"))))
                1)
           {:keys [nodes edges]} (analyze/load-file* seed)
           res (analyze/analyze nodes edges)]
       (.mkdirs outdir)
       (let [out (clojure.java.io/file outdir "asobi-datoms.kotoba.edn")]
         (spit out (emit nodes edges res tx))
         (println (str "asobi datom log → " out " ("
                       (count nodes) " nodes + " (count edges) " 縁, tx=" tx ")")))
       0)))
