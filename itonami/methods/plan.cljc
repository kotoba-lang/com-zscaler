(ns itonami.methods.plan
  "itonami 営み — R5 throughput / line-balance planner (ADR-2606082300).
  1:1 Clojure port of `methods/plan.py`.

  Translates OEE observations into ACTUAL output (units/day) through a SECOND lens distinct from
  the R1 OEE-bottleneck: the takt-capacity bottleneck. A serial line's output rate is set by its
  slowest station's capacity (uptime ÷ takt). The OEE-worst and throughput-worst stations are
  often DIFFERENT — surfacing that gap is the operations intelligence FOX sells.

    station-capacity — per station: capacity at observed uptime vs at full availability
    line-plan        — line throughput bottleneck + units/window + units/day (documented hours)
    relief-plan      — recover the throughput-bottleneck's idle/down → new line throughput uplift

  CONSTITUTIONAL:
    G1 — RECOMMENDS a plan; never actuates the line / sets a rate on the OT bus.
    G2 — capacity relief is AVAILABILITY recovery (remove stops) WITHIN takt; never a sub-takt
      cycle / speed-up / labor intensification. Station/line scale only (no worker).
    G3 — capacity is a read-time aggregate; operating-hours/day is a DOCUMENTED assumption (G5).

  Requires the merged itonami.methods.analyze ns. House style: ':…' strings literal; pure fns;
  I/O at #?(:clj) edges; float formatting EXACT (HALF_EVEN over the exact double; fmt-g for {v:g})."
  (:require [clojure.string :as str]
            [itonami.methods.analyze :as analyze]
            #?(:clj [clojure.java.io :as io])))

;; Documented operating-hours assumption (G5): 2 shifts. NOT a measured fact.
(def OPERATING-HOURS-PER-DAY 16.0)

;; ── float formatting (Python f-string parity: HALF_EVEN over the exact double) ──

(defn- fmt-f
  [x n]
  (-> (java.math.BigDecimal. (double x))
      (.setScale (int n) java.math.RoundingMode/HALF_EVEN)
      (.toPlainString)))

(defn- fmt-pct [x] (str (fmt-f (* 100.0 (double x)) 1) "%"))   ; {x:.1%}
(defn- fmt0 [x] (fmt-f x 0))   ; {x:.0f}
(defn- fmt1 [x] (fmt-f x 1))   ; {x:.1f}

(defn- fmt-g
  [v]
  (let [d (double v)]
    (if (and (== d (Math/rint d)) (< (Math/abs d) 1e15))
      (str (long d))
      (let [s (format "%.6g" d)]
        (if (str/includes? s ".")
          (-> s (str/replace #"0+$" "") (str/replace #"\.$" ""))
          s)))))

(defn- num*
  "Port of `float(v or 0)` for takt: nil/false/0 → 0.0; else double."
  [v]
  (if (or (nil? v) (false? v)) 0.0 (double v)))

(defn- stations*
  [res]
  (filter #(not (str/starts-with? % "_"))
          (or (:itonami.methods.analyze/order (meta res)) (keys res))))

(defn- argmin
  "min(sids, key=...) — Python min keeps the FIRST minimal element on ties."
  [sids f]
  (when (seq sids)
    (reduce (fn [best s] (if (< (f s) (f best)) s best)) (first sids) (rest sids))))

(defn station-capacity
  "Per station: cycle capacity over the window at observed uptime vs at full availability.
  Returns an insertion-ordered (::order) map mirroring Python dict iteration order."
  [stations res]
  (let [sids (stations* res)]
    (reduce
     (fn [m sid]
       (let [takt (num* (get-in stations [sid ":station/takt-s"] 0))
             r (get res sid)
             cap-run (if (> takt 0) (/ (get r "run_s") takt) 0.0)
             cap-planned (if (> takt 0) (/ (get r "planned_s") takt) 0.0)]
         (-> m
             (assoc sid {"takt" takt "capacity_run" cap-run "capacity_planned" cap-planned
                         "actual_cycles" (get r "cycles") "quality" (get r "quality")})
             (vary-meta update :itonami.methods.plan/order (fnil conj []) sid))))
     ^{:itonami.methods.plan/order []} {}
     sids)))

(defn- cap-order [cap] (or (:itonami.methods.plan/order (meta cap)) (keys cap)))

(defn- window-s
  [res]
  (let [vs (map #(get-in res [% "planned_s"]) (stations* res))]
    (if (seq vs) (reduce max vs) 0.0)))

(defn line-plan
  "Line throughput = the slowest station's capacity (takt + availability limited)."
  ([stations res] (line-plan stations res OPERATING-HOURS-PER-DAY))
  ([stations res hours]
   (let [cap (station-capacity stations res)
         sids (cap-order cap)]
     (if (empty? sids)
       {}
       (let [w (window-s res)
             bn (argmin sids #(get-in cap [% "capacity_run"]))
             units-per-window (get-in cap [bn "capacity_run"])
             day-scale (if (> w 0) (/ (* hours 3600.0) w) 0.0)
             lg (get-in res ["_line" "good"])
             ls (get-in res ["_line" "scrap"])
             line-quality (if (> (+ lg ls) 0) (/ lg (+ lg ls)) 1.0)]
         {"throughput_bottleneck" bn
          "units_per_window_gross" units-per-window
          "units_per_day_gross" (* units-per-window day-scale)
          "units_per_day_good" (* units-per-window day-scale line-quality)
          "window_s" w "hours_per_day" hours "line_quality" line-quality
          "capacity" cap})))))

(defn relief-plan
  "Recover the throughput-bottleneck's idle/down → new line throughput (availability lever)."
  ([stations res] (relief-plan stations res nil OPERATING-HOURS-PER-DAY))
  ([stations res plan] (relief-plan stations res plan OPERATING-HOURS-PER-DAY))
  ([stations res plan hours]
   (let [plan (or plan (line-plan stations res hours))
         cap (get plan "capacity")
         bn (get plan "throughput_bottleneck")
         sids (cap-order cap)
         bn-recovered (get-in cap [bn "capacity_planned"])
         others (map #(get-in cap [% "capacity_run"]) (filter #(not= % bn) sids))
         new-units (if (seq others) (reduce min (cons bn-recovered others)) bn-recovered)
         cur (get plan "units_per_window_gross")
         day-scale (if (> (get plan "window_s") 0) (/ (* hours 3600.0) (get plan "window_s")) 0.0)]
     {"bottleneck" bn
      "current_units_per_window" cur
      "recovered_units_per_window" new-units
      "uplift_frac" (if (> cur 0) (/ (- new-units cur) cur) 0.0)
      "current_units_per_day" (* cur day-scale)
      "recovered_units_per_day" (* new-units day-scale)
      "lever" "availability recovery (remove idle/down stops) within takt; per-cycle pace unchanged"
      "still_takt_limited" (and (<= new-units (+ bn-recovered 1e-9))
                                (< (Math/abs (- new-units bn-recovered)) 1e-9))})))

(defn- label
  [stations sid]
  (if sid (get-in stations [sid ":station/label"] sid) "—"))

(defn report-md
  [stations res plan relief]
  (let [oee-bn (get-in res ["_recommend" "bottleneck" "station"])
        cap (get plan "capacity")
        L (transient [])
        P (fn [s] (conj! L s))]
    (P "# itonami 営み — R5 throughput / line-balance plan\n")
    (P (str "> **G1** recommends a plan, never actuates. **G2** relief = availability recovery "
            "WITHIN takt, never sub-takt speed-up / intensification. **G5** operating-hours is "
            "a documented assumption, not a fact.\n"))
    (P (str "\n**Two bottlenecks, two lenses:** the OEE-worst station is "
            "**" (label stations oee-bn) "**, but the THROUGHPUT-worst (takt-capacity) station "
            "is **" (label stations (get plan "throughput_bottleneck")) "** — relieve the right one "
            "for the right goal.\n"))
    (P (str "\n- line throughput: **" (fmt1 (get plan "units_per_window_gross")) "** units/window → "
            "**" (fmt1 (get plan "units_per_day_good")) "** good units/day "
            "(@ " (fmt0 (get plan "hours_per_day")) " h/day, quality " (fmt-pct (get plan "line_quality")) ")"))
    (P (str "- recovering " (label stations (get relief "bottleneck")) " idle/down → "
            "**" (fmt1 (get relief "recovered_units_per_window")) "** units/window "
            "(**+" (fmt-pct (get relief "uplift_frac")) "** → " (fmt1 (get relief "recovered_units_per_day")) "/day)"))
    (when (get relief "still_takt_limited")
      (P (str "  - note: " (label stations (get relief "bottleneck")) " remains takt-limited after "
              "recovery; further gain needs process (takt) change, out of itonami scope")))

    (P "\n## Station capacity (units/window)\n")
    (P "| station | takt s | at uptime | if fully available |")
    (P "|---|---:|---:|---:|")
    (doseq [sid (sort-by (fn [s] (get-in cap [s "capacity_run"])) (cap-order cap))]
      (let [c (get cap sid)]
        (P (str "| " (label stations sid) " | " (fmt0 (get c "takt")) " | " (fmt1 (get c "capacity_run")) " | "
                (fmt1 (get c "capacity_planned")) " |"))))

    (P (str "\n---\n_itonami 営み R5 · ADR-2606082300 · plan-not-actuate · "
            "availability-not-speedup · documented-hours._\n"))
    (str/join "\n" (persistent! L))))

(defn emit
  "Transient EAVT plan datoms (computed on read, never durable — G3)."
  ([plan relief] (emit plan relief 1))
  ([plan relief tx]
   (let [L (transient [";; itonami R5 throughput plan — TRANSIENT (:bond/is-transient true), G1/G3." "["])]
     (conj! L (str "[:line.sarutahiko-a :ops/throughput-bottleneck "
                   (get plan "throughput_bottleneck") " " tx " :derived] ;; :bond/is-transient true"))
     (conj! L (str "[:line.sarutahiko-a :ops/units-per-day-good "
                   (fmt-g (get plan "units_per_day_good")) " " tx " :derived] ;; :bond/is-transient true"))
     (conj! L (str "[:line.sarutahiko-a :ops/throughput-uplift-frac "
                   (fmt-g (get relief "uplift_frac")) " " tx " :derived] ;; :bond/is-transient true"))
     (conj! L "]")
     (str (str/join "\n" (persistent! L)) "\n"))))

#?(:clj
   (defn -main
     [& argv]
     (let [argv (vec argv)
           here (-> *file* io/file .getParentFile .getParentFile)
           seed (if (and (seq argv) (not (str/starts-with? (first argv) "--")))
                  (io/file (first argv))
                  (io/file here "data" "seed-factory-ops.kotoba.edn"))
           hours (if (some #{"--hours"} argv)
                   (Double/parseDouble (nth argv (inc (.indexOf argv "--hours")))) OPERATING-HOURS-PER-DAY)
           outdir (if (some #{"--out"} argv)
                    (io/file (nth argv (inc (.indexOf argv "--out"))))
                    (io/file here "out"))
           tx (if (some #{"--tx"} argv)
                (Long/parseLong (nth argv (inc (.indexOf argv "--tx")))) 1)
           {:keys [stations ticks]} (analyze/load-file* seed)
           res (analyze/analyze stations ticks)
           plan (line-plan stations res hours)
           relief (relief-plan stations res plan hours)]
       (.mkdirs outdir)
       (spit (io/file outdir "throughput-plan.md") (report-md stations res plan relief))
       (spit (io/file outdir "itonami-plan.kotoba.edn") (emit plan relief tx))
       (println (str "itonami R5: throughput bottleneck " (get plan "throughput_bottleneck") " · "
                     (fmt1 (get plan "units_per_day_good")) " good/day · relief +"
                     (fmt-pct (get relief "uplift_frac")) " → " outdir))
       0)))
