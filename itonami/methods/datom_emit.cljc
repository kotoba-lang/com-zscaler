(ns itonami.methods.datom-emit
  "itonami 営み — kotoba Datom-log emitter (canonical EAVT state, ADR-2605312345).
  1:1 Clojure port of `methods/datom_emit.py` (ADR-2606082300).

  Projects factory-operations observations into append-only kotoba Datoms [e a v tx op].

    GROUND (durable, op :add) — :station/* node datoms + :tick/* scan-cycle observations.
      The scan-cycle observation IS the canonical state (kotoba-os: scan-cycle = Datom txn).
    DERIVED (transient, :bond/is-transient true) — per-station OEE / energy / quality KPIs
      + routed recommendations; computed on READ, NOT persisted (G3 — KPIs are not facts,
      they are read-time aggregates of the disclosed ticks).

  Reuses itonami.methods.analyze (load-file* / read-edn / load-graph / analyze). House style:
  Python ':…' keyword strings stay strings; the emitted Datom text is byte-identical to the
  Python emit. Float formatting mirrors Python's `{v:g}`; +∞ renders as `:inf`.

  ORDERING (byte-parity): stations iterate in dict insertion order (Python `stations.items()`),
  preserved here via the analyze loader's array-map / ::order. The KPI block walks
  `sorted(s for s in res if not s.startswith('_'))`; the routed-findings block walks the
  `_recommend` map in insertion order (bottleneck, energy_target, idle_energy_target,
  quality_target)."
  (:require [clojure.string :as str]
            [itonami.methods.analyze :as analyze]
            #?(:clj [clojure.java.io :as io])))

(def station-attrs
  [":station/label" ":station/line" ":station/takt-s"
   ":station/rated-kw" ":station/sourcing"])
(def tick-attrs
  [":tick/station" ":tick/t" ":tick/state" ":tick/cycles"
   ":tick/good" ":tick/scrap" ":tick/kwh" ":tick/interval-s"])
(def derived-kpis
  ["oee" "availability" "performance" "quality"
   "energy_per_good" "idle_energy_frac" "scrap_rate"])
(def kpi-attr
  {"oee" ":ops/oee" "availability" ":ops/availability"
   "performance" ":ops/performance" "quality" ":ops/quality"
   "energy_per_good" ":ops/energy-per-good" "idle_energy_frac" ":ops/idle-energy-frac"
   "scrap_rate" ":ops/scrap-rate"})

;; routed-findings keys in Python defaultdict / dict insertion order (matches _recommend build)
(def ^:private recommend-order
  ["bottleneck" "energy_target" "idle_energy_target" "quality_target"])

(defn- inf?
  [x]
  #?(:clj (and (number? x) (Double/isInfinite (double x)) (pos? (double x)))
     :cljs (and (number? x) (infinite? x) (pos? x))))

(defn- fmt-g
  "Mirror Python's f-string `{v:g}` for our (moderate-magnitude) doubles: 6 significant
  digits, trailing zeros stripped, an integral value renders with no decimal point (1.0 → \"1\")."
  [v]
  (let [d (double v)]
    (if (and (== d (Math/rint d)) (< (Math/abs d) 1e15))
      (str (long d))
      (let [s (format "%.6g" d)]
        (if (str/includes? s ".")
          (-> s (str/replace #"0+$" "") (str/replace #"\.$" ""))
          s)))))

(defn fmt
  "Port of _fmt: bool → true/false; nil → nil; +∞ → :inf; \":…\" kept literal; other string →
  quoted with \\ and \" escaped; float (double) → {v:g}; else str()."
  [v]
  (cond
    (true? v) "true"
    (false? v) "false"
    (nil? v) "nil"
    (inf? v) ":inf"
    (string? v) (if (str/starts-with? v ":")
                  v
                  (str "\"" (-> v (str/replace "\\" "\\\\") (str/replace "\"" "\\\"")) "\""))
    (double? v) (fmt-g v)
    :else (str v)))

(defn- station-order
  "Dict insertion order of the stations map (array-map preserves ≤8; ::order if tagged)."
  [stations]
  (or (:itonami.methods.analyze/order (meta stations)) (keys stations)))

(defn emit
  "Faithful 1:1 of datom_emit.emit. Returns the kotoba Datom-log EDN text (trailing newline)."
  ([stations ticks res] (emit stations ticks res 1))
  ([stations ticks res tx]
   (let [L (transient [])]
     (conj! L ";; itonami 営み — GENERATED kotoba Datom log (ADR-2606082300). DO NOT hand-edit.")
     (conj! L ";; Canonical EAVT state (ADR-2605312345). [e a v tx op].")
     (conj! L ";; GROUND op :add = durable (station + scan-cycle ticks).")
     (conj! L ";; DERIVED :bond/is-transient = KPIs computed on read (G3 — aggregates, not facts).")
     (conj! L "[")

     ;; ── GROUND: station node datoms (insertion order)
     (doseq [sid (station-order stations)]
       (let [st (get stations sid)]
         (doseq [a station-attrs]
           (let [v (get st a)]
             (when (and (contains? st a) (not (nil? v)))
               (conj! L (str "[" (fmt sid) " " a " " (fmt v) " " tx " :add]")))))))

     ;; ── GROUND: scan-cycle tick datoms (eid = tick.<sid-stripped>.<t>)
     (doseq [tk ticks]
       (let [sid (get tk ":tick/station")
             t (get tk ":tick/t")
             eid (str "tick." (let [s (str sid)] (if (str/starts-with? s ":") (subs s 1) s))
                      "." t)]
         (doseq [a tick-attrs]
           (let [v (get tk a)]
             (when (and (contains? tk a) (not (nil? v)))
               (conj! L (str "[" (fmt eid) " " a " " (fmt v) " " tx " :add]")))))))

     ;; ── DERIVED KPIs (transient; sorted non-underscore station id)
     (conj! L ";; ── DERIVED KPIs (transient; aggregate of scan-cycle ticks, computed on read) ──")
     (doseq [sid (sort (filter #(not (str/starts-with? % "_")) (keys res)))]
       (let [r (get res sid)]
         (doseq [k derived-kpis]
           (conj! L (str "[" (fmt sid) " " (get kpi-attr k) " " (fmt (get r k)) " " tx " :derived] "
                         ";; :bond/is-transient true")))))

     ;; ── routed findings (transient; to human/Council — G1)
     (let [rec (get res "_recommend" {})]
       (conj! L ";; ── routed findings (transient; to human/Council, never a write-back — G1) ──")
       (doseq [kind (filter #(contains? rec %) recommend-order)]
         (let [payload (get rec kind)]
           (conj! L (str "[:line.sarutahiko-a :ops/routed-" kind " " (fmt (get payload "station"))
                         " " tx " :derived] ;; :bond/is-transient true")))))

     (conj! L "]")
     (str (str/join "\n" (persistent! L)) "\n"))))

#?(:clj
   (defn -main
     "CLI entry: analyze a seed EDN ops log → out/itonami-datoms.kotoba.edn (file I/O at the edge)."
     [& argv]
     (let [argv (vec argv)
           here (-> *file* io/file .getParentFile .getParentFile)
           seed (if (and (seq argv) (not (str/starts-with? (first argv) "--")))
                  (io/file (first argv))
                  (io/file here "data" "seed-factory-ops.kotoba.edn"))
           outdir (if (some #{"--out"} argv)
                    (io/file (nth argv (inc (.indexOf argv "--out"))))
                    (io/file here "out"))
           tx (if (some #{"--tx"} argv)
                (Long/parseLong (nth argv (inc (.indexOf argv "--tx"))))
                1)
           {:keys [stations ticks]} (analyze/load-file* seed)
           res (analyze/analyze stations ticks)
           out (io/file outdir "itonami-datoms.kotoba.edn")]
       (.mkdirs outdir)
       (spit out (emit stations ticks res tx))
       (println (str "itonami datom log → " out " (" (count stations) " stations + " (count ticks)
                     " ticks, tx=" tx ")"))
       0)))
