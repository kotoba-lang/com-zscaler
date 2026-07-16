(ns itonami.methods.analyze
  "itonami 営み — factory-operations KPI engine over scan-cycle observations.
  1:1 Clojure port of `methods/analyze.py` (ADR-2606082300).

  The charter-clean inversion of an \"AI Factory Brain\" / NVIDIA Factory Operations
  Blueprint (FOX): it OBSERVES the running factory (kotoba-os scan-cycle Datoms +
  sarutahiko/giemon line stations) and RECOMMENDS where to improve OEE / energy / quality —
  it never actuates the line, and it cannot represent per-worker monitoring.

  Reads a kotoba-EDN operations log: :station/* node maps + :tick/* scan-cycle observations.
  Computes, aggregate-first, the canonical manufacturing metrics:

    OEE = Availability × Performance × Quality        (per station + line rollup)
    energy/good-unit (kWh)                             (the FOX \"10% energy cut\" lever)
    idle-energy fraction                               (energy burned while not producing)
    scrap-rate                                         (routed to vision inspection / manako)

  CONSTITUTIONAL (read before any change):
    G1 — OBSERVE → RECOMMEND only. itonami NEVER actuates (no-server-key, liveActuation:false,
      consistent with kotoba-os C-carve-outs). The optimization is a proposal to a human/Council,
      never a write back to the OT bus.
    G2 — STATION / LINE scale ONLY. There is no :worker/* dimension; per-person productivity,
      pace, or presence is structurally unrepresentable (Wellbecoming §1.13, anti-labor-
      surveillance). Energy/quality gains route to EFFICIENCY, never to line-speed-up or
      labor intensification that harms the worker.
    G3 — non-adjudicating. Station states (:run/:idle/:down) and counts are DISCLOSED scan-cycle
      facts, never itonami verdicts.

  House style: Python ':…' keyword strings stay strings (incl. all :station/* / :tick/* attrs);
  pure fns; file I/O only at #?(:clj) edges. Portable .cljc.

  Float/round parity: Python f-string formats `{x:.1%}` / `{x:.0f}` / `{x:.1f}` round the
  exact binary value of the double HALF_EVEN. We mirror that with BigDecimal(double).setScale.
  Insertion-ordered accumulators carry ::order metadata so a stable sort-by ties the Python
  defaultdict iteration order. itonami iterates only dicts/lists (no order-sensitive set(...)),
  so plain ::order suffices — no siphash13/setobject port is needed."
  (:require [clojure.string :as str]))

;; ── minimal EDN reader (subset: vectors [], maps {}, :keyword, "string", num, bool, nil)
;; Mirrors analyze.py's _TOK / _tokens / _atom / _parse faithfully. Keywords are kept as
;; ":ns/name" strings (NOT clojure keywords) so the whole pipeline stays string-keyed,
;; byte-for-byte the same as the Python port.

(def ^:private tok-re
  ;; _TOK = re.compile(r'[\s,]+|;[^\n]*|(\[|\]|\{|\}|"(?:\\.|[^"\\])*"|[^\s,\[\]{}]+)')
  #"[\s,]+|;[^\n]*|(\[|\]|\{|\}|\"(?:\\.|[^\"\\])*\"|[^\s,\[\]{}]+)")

(defn tokens
  "Lazy seq of significant tokens (group 1 of each tok-re match that captured)."
  [s]
  (let [m (re-matcher tok-re s)]
    ((fn step []
       (lazy-seq
        (when (.find m)
          (let [t (.group m 1)]
            (if (nil? t)
              (step)
              (cons t (step))))))))))

(defn atom-of
  "Port of _atom: \"…\" → unescaped string; true/false/nil → bool/nil; \":…\" kept as string;
  int → long; else float; else raw string."
  [t]
  (cond
    (str/starts-with? t "\"")
    (-> (subs t 1 (dec (count t)))
        (str/replace "\\\"" "\"")
        (str/replace "\\\\" "\\"))
    (= t "true") true
    (= t "false") false
    (= t "nil") nil
    (str/starts-with? t ":") t
    :else
    (let [as-long (try (Long/parseLong t) (catch #?(:clj Exception :cljs :default) _ ::nan))]
      (if (not= as-long ::nan)
        as-long
        (let [as-dbl (try (Double/parseDouble t) (catch #?(:clj Exception :cljs :default) _ ::nan))]
          (if (not= as-dbl ::nan) as-dbl t))))))

(def ^:private end-marker ::end)

(defn- parse-step
  "Consume one form from the token vector at index i. Returns [value next-i] or
  [end-marker next-i] when a closing ] or } is hit (matching _parse's _END sentinel)."
  [toks i]
  (let [t (nth toks i)
        i (inc i)]
    (cond
      (= t "[")
      (loop [i i, out []]
        (let [[x i] (parse-step toks i)]
          (if (= x end-marker)
            [out i]
            (recur i (conj out x)))))

      (= t "{")
      (loop [i i, out {}]
        (let [[k i] (parse-step toks i)]
          (if (= k end-marker)
            [out i]
            (let [[v i] (parse-step toks i)]
              (recur i (assoc out k v))))))

      (or (= t "]") (= t "}"))
      [end-marker i]

      :else
      [(atom-of t) i])))

(defn read-edn
  "Parse the first top-level form from EDN text (matches read_edn → _parse(_tokens(text)))."
  [text]
  (let [toks (vec (tokens text))]
    (first (parse-step toks 0))))

;; ── tick states ───────────────────────────────────────────────────────────────
(def RUN ":run")            ; producing
(def IDLE ":idle")          ; powered, not producing (changeover / starved / blocked)
(def DOWN ":down")          ; fault / unplanned stop
(def producing-states #{RUN})

(defn load-graph
  "Return {:stations stations-by-id :ticks ticks} from a parsed list of EDN forms.
  (`load` is a clojure.core fn — named load-graph; the host edge reads the file.)
  Insertion order of stations preserved (array-map ≤8 keeps order; ::order tracked below
  where >8 entries are possible)."
  [forms]
  (reduce
   (fn [{:keys [stations ticks] :as acc} f]
     (cond
       (not (map? f)) acc
       (contains? f ":station/id") (assoc-in acc [:stations (get f ":station/id")] f)
       (contains? f ":tick/station") (update acc :ticks conj f)
       :else acc))
   {:stations (array-map) :ticks []}
   forms))

#?(:clj
   (defn load-file*
     "Read + parse an itonami operations EDN log file → {:stations :ticks}. File I/O at this edge."
     [path]
     (load-graph (read-edn (slurp (str path))))))

;; ── float formatting (Python f-string parity: HALF_EVEN over the exact double) ──

(defn- fmt-f
  "Python `format(x, '.Nf')` — fixed-point N decimals, HALF_EVEN over the exact double."
  [x n]
  (-> (java.math.BigDecimal. (double x))
      (.setScale (int n) java.math.RoundingMode/HALF_EVEN)
      (.toPlainString)))

(defn- fmt-pct
  "Python `{x:.1%}` — x*100 then `.1f`, then a percent sign."
  [x]
  (str (fmt-f (* 100.0 (double x)) 1) "%"))

(defn- fmt0 [x] (fmt-f x 0))   ; {x:.0f}
(defn- fmt1 [x] (fmt-f x 1))   ; {x:.1f}

;; ── numeric coercion (Python _num) ─────────────────────────────────────────────

(defn- num
  "Port of _num(v, default): float(v if v is not None else default), 0.0 on TypeError/ValueError.
  In our string-keyed model values are already long/double/nil; nil → default."
  ([v] (num v 0.0))
  ([v default]
   (let [v (if (nil? v) default v)]
     (cond
       (true? v) 1.0
       (false? v) 0.0
       (number? v) (double v)
       :else
       (try (Double/parseDouble (str v))
            (catch #?(:clj Exception :cljs :default) _ (double default)))))))

;; ── ordered (insertion-tracking) accumulator (mirror Python defaultdict iteration) ──
;; ::order metadata = vector of keys in first-touch order, so a stable sort / iteration ties
;; exactly the Python defaultdict iteration order even with >8 keys.

(defn- omap [] ^{::order []} {})

(defn- omap-update
  "update an ordered-map: apply f to the value at k (default `init`), recording k's
  first-touch position in ::order metadata."
  [m k f init]
  (let [had? (contains? m k)
        m' (update m k (fnil f init))]
    (if had?
      (with-meta m' (meta m))
      (with-meta m' (update (meta m) ::order conj k)))))

(defn- omap-keys
  [m]
  (if-let [order (::order (meta m))] order (keys m)))

(defn- omap-items
  [m]
  (if-let [order (::order (meta m))]
    (map (fn [k] [k (get m k)]) order)
    (seq m)))

(def ^:private zero-agg
  {"planned_s" 0.0 "run_s" 0.0 "idle_s" 0.0 "down_s" 0.0
   "cycles" 0.0 "good" 0.0 "scrap" 0.0 "kwh" 0.0 "idle_kwh" 0.0})

(declare line-rollup recommend)

(defn analyze
  "Port of analyze(stations, ticks). Aggregate scan-cycle ticks into per-station OEE / energy /
  quality (G2: station scale). Returns a string-keyed map: each station-id (string) → its KPI
  map, plus \"_line\" rollup and \"_recommend\" routed findings. The outer map carries ::order
  metadata = the first-touch station insertion order (Python defaultdict order)."
  [stations ticks]
  (let [agg
        (reduce
         (fn [agg tk]
           (let [sid (get tk ":tick/station")]
             (if (nil? sid)
               agg
               (let [dt (num (get tk ":tick/interval-s") 0.0)
                     state (get tk ":tick/state")
                     kwh (num (get tk ":tick/kwh"))
                     a (get agg sid zero-agg)
                     a (-> a
                           (update "planned_s" + dt)
                           (cond->
                            (= state RUN) (update "run_s" + dt)
                            (= state IDLE) (update "idle_s" + dt)
                            (= state DOWN) (update "down_s" + dt))
                           (update "cycles" + (num (get tk ":tick/cycles")))
                           (update "good" + (num (get tk ":tick/good")))
                           (update "scrap" + (num (get tk ":tick/scrap")))
                           (update "kwh" + kwh)
                           (cond->
                            (not (contains? producing-states state)) (update "idle_kwh" + kwh)))]
                 (omap-update agg sid (constantly a) zero-agg)))))
         (omap)
         ticks)

        out
        (reduce
         (fn [out sid]
           (let [a (get agg sid)
                 st (get stations sid {})
                 takt (num (get st ":station/takt-s") 0.0)
                 planned (get a "planned_s")
                 run-s (get a "run_s")
                 cycles (get a "cycles")
                 good (get a "good")
                 availability (if (> planned 0) (/ run-s planned) 0.0)
                 performance (if (> run-s 0) (/ (* takt cycles) run-s) 0.0)
                 quality (if (> cycles 0) (/ good cycles) 0.0)
                 oee (* availability performance quality)
                 r {"availability" availability "performance" performance
                    "quality" quality "oee" oee
                    "kwh" (get a "kwh") "idle_kwh" (get a "idle_kwh")
                    "energy_per_good" (if (> good 0)
                                        (/ (get a "kwh") good)
                                        #?(:clj Double/POSITIVE_INFINITY :cljs js/Infinity))
                    "idle_energy_frac" (if (> (get a "kwh") 0)
                                         (/ (get a "idle_kwh") (get a "kwh")) 0.0)
                    "scrap_rate" (if (> cycles 0) (/ (get a "scrap") cycles) 0.0)
                    "cycles" cycles "good" good "scrap" (get a "scrap")
                    "run_s" run-s "idle_s" (get a "idle_s") "down_s" (get a "down_s")
                    "planned_s" planned}]
             (omap-update out sid (constantly r) {})))
         (omap)
         (omap-keys agg))

        out (omap-update out "_line" (constantly (line-rollup out)) {})
        out (omap-update out "_recommend" (constantly (recommend out stations)) {})]
    out))

(defn- non-underscore-sids
  "[s for s in per if not s.startswith('_')] — in Python dict-iteration order."
  [per]
  (filter #(not (str/starts-with? % "_")) (omap-keys per)))

(defn line-rollup
  "Port of _line_rollup(per)."
  [per]
  (let [sids (non-underscore-sids per)]
    (if (empty? sids)
      {"oee" 0.0 "kwh" 0.0 "idle_kwh" 0.0 "good" 0.0 "scrap" 0.0 "energy_per_good" 0.0}
      (let [kwh (reduce + 0.0 (map #(get-in per [% "kwh"]) sids))
            idle-kwh (reduce + 0.0 (map #(get-in per [% "idle_kwh"]) sids))
            good (reduce + 0.0 (map #(get-in per [% "good"]) sids))
            scrap (reduce + 0.0 (map #(get-in per [% "scrap"]) sids))
            ;; line OEE is gated by its weakest station (a serial line is its bottleneck)
            line-oee (reduce min (map #(get-in per [% "oee"]) sids))]
        {"oee" line-oee "kwh" kwh "idle_kwh" idle-kwh "good" good "scrap" scrap
         "energy_per_good" (if (> good 0)
                             (/ kwh good)
                             #?(:clj Double/POSITIVE_INFINITY :cljs js/Infinity))
         "idle_energy_frac" (if (> kwh 0) (/ idle-kwh kwh) 0.0)
         "scrap_rate" (if (> (+ good scrap) 0) (/ scrap (+ good scrap)) 0.0)
         "n_stations" (count sids)}))))

(defn- argmax
  "max(sids, key=lambda s: per[s][key]) — Python max keeps the FIRST maximal element on ties
  (iterating sids in dict order)."
  [per sids k]
  (reduce (fn [best s]
            (if (> (get-in per [s k]) (get-in per [best k])) s best))
          (first sids) (rest sids)))

(defn- argmin
  "min(sids, key=...) — Python min keeps the FIRST minimal element on ties."
  [per sids k]
  (reduce (fn [best s]
            (if (< (get-in per [s k]) (get-in per [best k])) s best))
          (first sids) (rest sids)))

(defn recommend
  "Port of _recommend(per, stations). Route observed inefficiency to IMPROVEMENT (G2)."
  [per _stations]
  (let [sids (non-underscore-sids per)]
    (if (empty? sids)
      {}
      (let [bottleneck (argmin per sids "oee")
            energy-hog (argmax per sids "energy_per_good")
            idle-waste (argmax per sids "idle_kwh")
            quality-alert (argmax per sids "scrap_rate")]
        {"bottleneck" {"station" bottleneck "oee" (get-in per [bottleneck "oee"])}
         "energy_target" {"station" energy-hog
                          "energy_per_good" (get-in per [energy-hog "energy_per_good"])}
         "idle_energy_target" {"station" idle-waste
                               "idle_kwh" (get-in per [idle-waste "idle_kwh"])}
         "quality_target" {"station" quality-alert
                           "scrap_rate" (get-in per [quality-alert "scrap_rate"])}}))))

(defn- label
  "Port of _label(stations, sid)."
  [stations sid]
  (get-in stations [sid ":station/label"] sid))

(defn- inf?
  [x]
  #?(:clj (and (number? x) (Double/isInfinite (double x)) (pos? (double x)))
     :cljs (and (number? x) (infinite? x) (pos? x))))

(defn report-md
  "Port of report_md(stations, ticks, res) — byte-identical markdown."
  [stations _ticks res]
  (let [line (get res "_line")
        rec (get res "_recommend")
        sids (->> (non-underscore-sids res)
                  ;; sorted((s for s in res if not s.startswith('_')), key=lambda s: res[s]['oee'])
                  (sort-by (fn [s] (get-in res [s "oee"])) ))
        L (transient [])
        P (fn [s] (conj! L s))]
    (P "# itonami 営み — factory-operations report (aggregate-first)\n")
    (P (str "> **G1 — OBSERVE → RECOMMEND only.** itonami never actuates the line "
            "(no-server-key, liveActuation:false). **G2 — STATION/LINE scale ONLY**; "
            "per-worker monitoring is structurally unrepresentable (Wellbecoming §1.13). "
            "Gains route to EFFICIENCY, never to line-speed-up or labor intensification. "
            "States/counts are DISCLOSED scan-cycle facts, not itonami verdicts (G3).\n"))
    (P (str "**Line**: " (get line "n_stations") " stations · OEE " (fmt-pct (get line "oee"))
            " (gated by weakest station) · " (fmt0 (get line "kwh")) " kWh · "
            (fmt1 (get line "energy_per_good")) " kWh/good · "
            "idle-energy " (fmt-pct (get line "idle_energy_frac")) " · scrap "
            (fmt-pct (get line "scrap_rate")) "\n"))

    (P "\n## Per-station OEE (worst-first — the order to improve)\n")
    (P "| station | OEE | avail | perf | qual | kWh/good | idle-E% |")
    (P "|---|---:|---:|---:|---:|---:|---:|")
    (doseq [s sids]
      (let [r (get res s)
            epg (if (inf? (get r "energy_per_good")) "∞" (fmt1 (get r "energy_per_good")))]
        (P (str "| " (label stations s) " | " (fmt-pct (get r "oee")) " | "
                (fmt-pct (get r "availability")) " | "
                (fmt-pct (get r "performance")) " | " (fmt-pct (get r "quality")) " | "
                epg " | " (fmt-pct (get r "idle_energy_frac")) " |"))))

    (P "\n## Routed findings (to a human / Council — never a write-back)\n")
    (P (str "- **Bottleneck** → " (label stations (get-in rec ["bottleneck" "station"]))
            " (OEE " (fmt-pct (get-in rec ["bottleneck" "oee"]))
            ") — raise availability/performance first."))
    (P (str "- **Energy target** → " (label stations (get-in rec ["energy_target" "station"]))
            " (" (fmt1 (get-in rec ["energy_target" "energy_per_good"]))
            " kWh/good) — efficiency, not speed-up."))
    (P (str "- **Idle-energy** → " (label stations (get-in rec ["idle_energy_target" "station"]))
            " (" (fmt0 (get-in rec ["idle_energy_target" "idle_kwh"]))
            " kWh burned not producing) — "
            "schedule / power-down opportunity."))
    (P (str "- **Quality** → " (label stations (get-in rec ["quality_target" "station"]))
            " (scrap " (fmt-pct (get-in rec ["quality_target" "scrap_rate"]))
            ") — route to vision inspection "
            "(manako) + root-cause; never to the worker."))

    (P (str "\n---\n_itonami 営み · ADR-2606082300 · observe→recommend · no-actuation · "
            "station-scale (no worker surveillance) · non-adjudicating. Live OT ingest is "
            "G6/Council-gated._\n"))
    (str/join "\n" (persistent! L))))

#?(:clj
   (defn -main
     "CLI entry: analyze a seed EDN ops log → out/operations-report.md (file I/O at the edge)."
     [& argv]
     (let [argv (vec argv)
           here (-> *file* clojure.java.io/file .getParentFile .getParentFile)
           seed (if (and (seq argv) (not (str/starts-with? (first argv) "--")))
                  (clojure.java.io/file (first argv))
                  (clojure.java.io/file here "data" "seed-factory-ops.kotoba.edn"))
           outdir (if (some #{"--out"} argv)
                    (clojure.java.io/file (nth argv (inc (.indexOf argv "--out"))))
                    (clojure.java.io/file here "out"))
           {:keys [stations ticks]} (load-file* seed)
           res (analyze stations ticks)
           line (get res "_line")
           rec (get res "_recommend")]
       (.mkdirs outdir)
       (spit (clojure.java.io/file outdir "operations-report.md")
             (report-md stations ticks res))
       (println (str "itonami: " (count stations) " stations, " (count ticks) " ticks → "
                     (clojure.java.io/file outdir "operations-report.md")))
       (println (str "  line OEE " (fmt-pct (get line "oee")) " · bottleneck "
                     (get-in rec ["bottleneck" "station"]) " · energy target "
                     (get-in rec ["energy_target" "station"])))
       0)))
