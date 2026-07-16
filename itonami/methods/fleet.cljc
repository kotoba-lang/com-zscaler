(ns itonami.methods.fleet
  "itonami 営み — R10 multi-line FLEET rollup (ADR-2606082300).
  1:1 Clojure port of `methods/fleet.py`.

  A plant brain oversees MORE than one line. Groups stations by :station/line, runs the
  single-line analysis per line, and rolls up a plant view + RANKS lines worst-first.

    split-lines  — group (stations, ticks) by :station/line
    rollup       — per line: analyze + alert/evaluate → {oee, energy/good, scrap, critical, warn}
    rank         — order lines worst-first (critical alerts, then lowest OEE)

  CONSTITUTIONAL: G1 observe → recommend (which line to attend); G2 ranks LINES not people;
  G3 non-adjudicating (per-line KPIs read-time, datoms transient).

  House style: Python ':…' keyword strings stay strings; string-keyed data; pure fns;
  file I/O only at #?(:clj) edges. Reuses itonami.methods.analyze + itonami.methods.alert."
  (:require [clojure.string :as str]
            [itonami.methods.analyze :as analyze]
            [itonami.methods.alert :as alert]
            #?(:clj [clojure.java.io :as io])))

(defn split-lines
  "Group (stations, ticks) into {line_id (line_stations line_ticks)}.
  Preserves line first-touch order (Python defaultdict over stations.items())."
  [stations ticks]
  (let [st-order (or (:itonami.methods.analyze/order (meta stations)) (keys stations))
        [by-line-st line-order]
        (reduce (fn [[bls order] sid]
                  (let [st (get stations sid)
                        line (get st ":station/line" ":line.unknown")
                        order (if (contains? bls line) order (conj order line))]
                    [(update bls line (fnil assoc (array-map)) sid st) order]))
                [{} []] st-order)]
    (reduce
     (fn [out line]
       (let [lst (get by-line-st line)
             lt (vec (filter #(contains? lst (get % ":tick/station")) ticks))]
         (assoc out line [lst lt])))
     (array-map) line-order)))

(defn rollup
  "Per-line KPIs + alert counts, plus a plant aggregate and attention ranking."
  ([stations ticks] (rollup stations ticks nil))
  ([stations ticks thresholds]
   (let [lines (split-lines stations ticks)
         line-order (keys lines)
         per-line (reduce
                   (fn [pl line]
                     (let [[lst lt] (get lines line)
                           res (analyze/analyze lst lt)
                           alerts (alert/evaluate lst res thresholds)
                           ac (alert/counts alerts)
                           L (get res "_line")]
                       (assoc pl line
                              {"oee" (get L "oee") "energy_per_good" (get L "energy_per_good")
                               "scrap_rate" (get L "scrap_rate") "good" (get L "good")
                               "kwh" (get L "kwh") "n_stations" (get L "n_stations")
                               "critical" (get ac "critical") "warn" (get ac "warn")})))
                   (array-map) line-order)
         ranked (vec (sort-by (fn [ln] [(- (get-in per-line [ln "critical"]))
                                        (get-in per-line [ln "oee"])])
                              line-order))
         plant {"n_lines" (count per-line)
                "good" (reduce + 0.0 (map #(get-in per-line [% "good"]) line-order))
                "kwh" (reduce + 0.0 (map #(get-in per-line [% "kwh"]) line-order))
                "critical" (reduce + 0 (map #(get-in per-line [% "critical"]) line-order))
                "warn" (reduce + 0 (map #(get-in per-line [% "warn"]) line-order))
                "worst_line" (if (seq ranked) (first ranked) nil)}]
     {"per_line" per-line "ranked" ranked "plant" plant})))

(defn- inf? [x]
  #?(:clj (and (number? x) (Double/isInfinite (double x)))
     :cljs (and (number? x) (infinite? x))))

(defn- fmt-f [x n]
  (-> (java.math.BigDecimal. (double x))
      (.setScale (int n) java.math.RoundingMode/HALF_EVEN) (.toPlainString)))
(defn- fmt-pct [x] (str (fmt-f (* 100.0 (double x)) 1) "%"))
(defn- fmt0 [x] (fmt-f x 0))
(defn- fmt1 [x] (fmt-f x 1))

(defn report-md
  [fleet]
  (let [p (get fleet "plant")
        L (transient []) P (fn [s] (conj! L s))]
    (P "# itonami 営み — R10 fleet (multi-line plant) rollup\n")
    (P (str "> **G1** recommends which line to attend to; never actuates. **G2** ranks LINES, "
            "never people (no worker dimension). **G3** per-line KPIs are read-time aggregates.\n"))
    (P (str "\n**Plant**: " (get p "n_lines") " lines · " (fmt0 (get p "good")) " good units · "
            (fmt0 (get p "kwh")) " kWh · " (get p "critical") " critical / " (get p "warn")
            " warn · attend first: **" (get p "worst_line") "**\n"))
    (P "\n## Lines (worst-first — attention order)\n")
    (P "| rank | line | OEE | kWh/good | scrap | critical | warn |")
    (P "|---:|---|---:|---:|---:|---:|---:|")
    (doseq [[i ln] (map-indexed (fn [i ln] [(inc i) ln]) (get fleet "ranked"))]
      (let [r (get-in fleet ["per_line" ln])
            epg (if (inf? (get r "energy_per_good")) "∞" (fmt1 (get r "energy_per_good")))]
        (P (str "| " i " | " ln " | " (fmt-pct (get r "oee")) " | " epg " | "
                (fmt-pct (get r "scrap_rate")) " | " (get r "critical") " | " (get r "warn") " |"))))
    (P (str "\n---\n_itonami 営み R10 · ADR-2606082300 · plant-scope · "
            "recommend-not-actuate · ranks-lines-not-people._\n"))
    (str/join "\n" (persistent! L))))

(defn- fmt-g [v]
  (let [d (double v)]
    (if (and (== d (Math/rint d)) (< (Math/abs d) 1e15))
      (str (long d))
      (let [s (format "%.6g" d)]
        (if (str/includes? s ".") (-> s (str/replace #"0+$" "") (str/replace #"\.$" "")) s)))))

(defn emit
  "Transient EAVT fleet datoms (computed on read, never durable — G3)."
  ([fleet] (emit fleet 1))
  ([fleet tx]
   (let [L (transient [";; itonami R10 fleet rollup — TRANSIENT (:bond/is-transient true), G1/G3." "["])]
     (doseq [ln (get fleet "ranked")]
       (let [r (get-in fleet ["per_line" ln])]
         (conj! L (str "[" ln " :fleet/oee " (fmt-g (get r "oee")) " " tx " :derived] ;; :bond/is-transient true"))
         (conj! L (str "[" ln " :fleet/critical-alerts " (get r "critical") " " tx " :derived] ;; :bond/is-transient true"))))
     (when (get-in fleet ["plant" "worst_line"])
       (conj! L (str "[:plant :fleet/attend-first " (get-in fleet ["plant" "worst_line"])
                     " " tx " :derived] ;; :bond/is-transient true")))
     (conj! L "]")
     (str (str/join "\n" (persistent! L)) "\n"))))

#?(:clj
   (defn -main
     [& argv]
     (let [argv (vec argv)
           here (-> *file* io/file .getParentFile .getParentFile)
           seed (if (and (seq argv) (not (str/starts-with? (first argv) "--")))
                  (io/file (first argv))
                  (io/file here "data" "seed-fleet-ops.kotoba.edn"))
           outdir (if (some #{"--out"} argv)
                    (io/file (nth argv (inc (.indexOf argv "--out"))))
                    (io/file here "out"))
           tx (if (some #{"--tx"} argv) (Long/parseLong (nth argv (inc (.indexOf argv "--tx")))) 1)
           {:keys [stations ticks]} (analyze/load-file* seed)
           fleet (rollup stations ticks)]
       (.mkdirs outdir)
       (spit (io/file outdir "fleet-rollup.md") (report-md fleet))
       (spit (io/file outdir "itonami-fleet.kotoba.edn") (emit fleet tx))
       0)))
