(ns itonami.methods.digest
  "itonami 営み — R4 daily operations digest + Murakumo narration (ADR-2606082300).
  1:1 Clojure port of `methods/digest.py`.

  FUSES the four cells (ingest → analyze → optimize → inspect) into ONE operator-facing daily
  digest + a Murakumo-narrated summary.

    build-digest       — fuse line OEE + routed findings + energy proposal + quality/vision
    narration-prompt   — the prompt that would be sent to Murakumo (LiteLLM 127.0.0.1:4000)
    fallback-narration — deterministic OFFLINE narration (no external LLM)

  CONSTITUTIONAL: G7 Murakumo-only narration (fixed NARRATION-BACKEND, deterministic fallback,
  never an external LLM); G1 recommends not actuates; G2 station/line scale; G3 read-time.

  House style: Python ':…' keyword strings stay strings; string-keyed data; pure fns;
  file I/O only at #?(:clj) edges. Reuses analyze/optimize/inspect/plan/trend siblings."
  (:require [clojure.string :as str]
            [itonami.methods.analyze :as analyze]
            [itonami.methods.optimize :as optimize]
            [itonami.methods.inspect :as vis]
            [itonami.methods.plan :as P]
            [itonami.methods.trend :as T]
            #?(:clj [clojure.java.io :as io])))

;; Narration routes ONLY through the Murakumo fleet (ADR-2605215000). Fixed by construction.
(def NARRATION-BACKEND "murakumo")
(def MURAKUMO-GATEWAY "http://127.0.0.1:4000")

(defn- label [stations sid] (if sid (get-in stations [sid ":station/label"] sid) "—"))

(defn build-digest
  ([stations ticks detections] (build-digest stations ticks detections nil))
  ([stations ticks detections history]
   (let [res (analyze/analyze stations ticks)
         opt (optimize/optimize stations ticks res)
         req (vis/inspection-request stations res detections)
         rec (vis/reconcile detections res)
         qtarget (get req "station")
         tplan (P/line-plan stations res)
         trelief (P/relief-plan stations res tplan)
         drift (when (seq history)
                 (let [regs (T/regressions (T/analyze-trends history))]
                   {"n" (count regs)
                    "top" (when (seq regs)
                            (let [r (first regs)]
                              {"scope" (nth r 0)
                               "kpi" (last (str/split (nth r 1) #"/"))
                               "rel_change" (nth r 2)}))}))]
     {"line" (get res "_line")
      "recommend" (get res "_recommend")
      "energy" (get opt "idle_powerdown")
      "bottleneck" (get opt "bottleneck_relief")
      "quality" {"station" qtarget
                 "label" (label stations qtarget)
                 "scrap_rate" (get-in res [qtarget "scrap_rate"])
                 "top_defect" (get-in rec [qtarget "top_defect"])
                 "inspect_sample_rate" (get req "sample_rate")}
      "throughput" {"bottleneck" (get tplan "throughput_bottleneck")
                    "label" (label stations (get tplan "throughput_bottleneck"))
                    "units_per_day_good" (get tplan "units_per_day_good")
                    "uplift_frac" (get trelief "uplift_frac")}
      "drift" drift
      "_stations" stations})))

(defn- facts-bottleneck [d]
  (label (get d "_stations") (get-in d ["bottleneck" "bottleneck"])))

(defn facts
  [d]
  (let [st (get d "_stations")]
    {"line_oee" (get-in d ["line" "oee"])
     "energy_reduction" (get-in d ["energy" "energy_reduction_frac"])
     "bottleneck" (label st (get-in d ["bottleneck" "bottleneck"]))
     "oee_uplift" (get-in d ["bottleneck" "oee_uplift_frac"])
     "quality_label" (get-in d ["quality" "label"])
     "scrap_rate" (get-in d ["quality" "scrap_rate"])
     "top_defect" (let [td (or (get-in d ["quality" "top_defect"]) ":none")]
                    (if (str/starts-with? td ":") (subs td 1) td))
     "throughput_label" (get-in d ["throughput" "label"])
     "units_per_day" (get-in d ["throughput" "units_per_day_good"])
     "throughput_uplift" (get-in d ["throughput" "uplift_frac"])
     "two_lens" (not= (get-in d ["throughput" "label"]) (facts-bottleneck d))
     "drift_n" (if (get d "drift") (get-in d ["drift" "n"]) 0)
     "drift_top" (if (get d "drift") (get-in d ["drift" "top"]) nil)}))

(defn- fmt-f [x n]
  (-> (java.math.BigDecimal. (double x))
      (.setScale (int n) java.math.RoundingMode/HALF_EVEN) (.toPlainString)))
(defn- fmt-pct [x] (str (fmt-f (* 100.0 (double x)) 1) "%"))
(defn- fmt-pct0 [x] (str (fmt-f (* 100.0 (double x)) 0) "%"))
(defn- fmt0 [x] (fmt-f x 0))
(defn- fmt-pct-signed [x]
  (let [s (fmt-f (* 100.0 (double x)) 1)]
    (str (if (and (not (str/starts-with? s "-")) (>= (double x) 0)) "+" "") s "%")))

(defn narration-prompt
  "The prompt sent to Murakumo (G7). Facts only; the model writes 2-3 plain sentences."
  [d]
  (let [f (facts d)]
    (str
     "You are itonami, a factory-operations brain. Narrate today's line digest in 2-3 plain "
     "sentences for a line lead. State facts only; recommend, never command; describe only "
     "stations and the line as a whole, never an individual. Facts:\n"
     "- line OEE: " (fmt-pct (get f "line_oee")) "\n"
     "- OEE bottleneck: " (get f "bottleneck") " (relieving it lifts line OEE +"
     (fmt-pct (get f "oee_uplift")) ")\n"
     "- throughput bottleneck: " (get f "throughput_label") " (~"
     (fmt0 (get f "units_per_day")) " good units/day; availability recovery +"
     (fmt-pct (get f "throughput_uplift")) ")\n"
     "- energy: powering down idle windows recovers ~"
     (fmt-pct (get f "energy_reduction")) " of line energy\n"
     "- quality: " (get f "quality_label") " scrap " (fmt-pct (get f "scrap_rate"))
     ", top defect " (get f "top_defect") " (route to vision inspection)\n"
     (if (get f "drift_top")
       (str "- multi-day drift: " (get f "drift_n") " degrading series; worst "
            (get-in f ["drift_top" "scope"]) " " (get-in f ["drift_top" "kpi"]) " "
            (fmt-pct-signed (get-in f ["drift_top" "rel_change"])) "\n")
       ""))))

(defn fallback-narration
  "Deterministic offline narration (no external LLM) — used when Murakumo is unreachable."
  [d]
  (let [f (facts d)
        lens (if (get f "two_lens")
               (str "The OEE bottleneck is " (get f "bottleneck") ", while the throughput "
                    "bottleneck is a different station, " (get f "throughput_label") " (~"
                    (fmt0 (get f "units_per_day")) " good units/day)")
               (str "The bottleneck is " (get f "bottleneck") " on both OEE and throughput (~"
                    (fmt0 (get f "units_per_day")) " good units/day)"))]
    (str
     "Line OEE is " (fmt-pct (get f "line_oee")) ". " lens "; relieving its availability lifts "
     "line OEE by about " (fmt-pct (get f "oee_uplift")) " and throughput by "
     (fmt-pct (get f "throughput_uplift")) ". "
     "Powering down idle windows could recover roughly " (fmt-pct (get f "energy_reduction"))
     " of line energy. Quality attention: " (get f "quality_label") " at "
     (fmt-pct (get f "scrap_rate")) " scrap (top defect " (get f "top_defect")
     ") — route to vision inspection."
     (if (get f "drift_top")
       (str " Multi-day drift: " (get f "drift_n") " series degrading, worst is "
            (get-in f ["drift_top" "scope"]) " " (get-in f ["drift_top" "kpi"]) " ("
            (fmt-pct-signed (get-in f ["drift_top" "rel_change"])) ") — investigate the trend.")
       ""))))

(defn narrate
  "Narrate via Murakumo if a caller is supplied (G7); else deterministic fallback.
  `murakumo-call` is an injected fn(prompt)->str that MUST hit the Murakumo gateway."
  ([d] (narrate d nil))
  ([d murakumo-call]
   (let [prompt (narration-prompt d)]
     (if (some? murakumo-call)
       (let [result (try {"backend" NARRATION-BACKEND "text" (murakumo-call prompt) "prompt" prompt}
                         (catch #?(:clj Exception :cljs :default) _ ::fail))]
         (if (= result ::fail)
           {"backend" "fallback-deterministic" "text" (fallback-narration d) "prompt" prompt}
           result))
       {"backend" "fallback-deterministic" "text" (fallback-narration d) "prompt" prompt}))))

(defn report-md
  [d narration]
  (let [f (facts d)
        L (transient []) P (fn [s] (conj! L s))]
    (P "# itonami 営み — daily operations digest\n")
    (P (str "> _Narration backend: " (get narration "backend") " (Murakumo-only, G7). "
            "Recommends, never actuates (G1); station-scale, no worker dimension (G2)._\n"))
    (P (str "\n**" (get narration "text") "**\n"))
    (P "\n| metric | value |")
    (P "|---|---|")
    (P (str "| line OEE | " (fmt-pct (get f "line_oee")) " |"))
    (P (str "| OEE bottleneck | " (get f "bottleneck") " (+" (fmt-pct (get f "oee_uplift")) " if relieved) |"))
    (P (str "| throughput bottleneck | " (get f "throughput_label") " · ~" (fmt0 (get f "units_per_day"))
            " good units/day · +" (fmt-pct (get f "throughput_uplift")) " if relieved |"))
    (P (str "| energy reduction (idle power-down) | " (fmt-pct (get f "energy_reduction")) " |"))
    (P (str "| quality target | " (get f "quality_label") " · scrap " (fmt-pct (get f "scrap_rate"))
            " · top defect " (get f "top_defect") " |"))
    (when (get f "drift_top")
      (P (str "| multi-day drift | " (get f "drift_n") " degrading · worst "
              (get-in f ["drift_top" "scope"]) " " (get-in f ["drift_top" "kpi"]) " "
              (fmt-pct-signed (get-in f ["drift_top" "rel_change"])) " |")))
    (P (str "\n---\n_itonami 営み R4 · ADR-2606082300 · Murakumo-only narration · "
            "recommends-not-actuates · station-scale._\n"))
    (str/join "\n" (persistent! L))))

(defn- fmt-g [v]
  (let [d (double v)]
    (if (and (== d (Math/rint d)) (< (Math/abs d) 1e15))
      (str (long d))
      (let [s (format "%.6g" d)]
        (if (str/includes? s ".") (-> s (str/replace #"0+$" "") (str/replace #"\.$" "")) s)))))

(defn emit
  "Transient EAVT digest datoms (computed on read, never durable — G3)."
  ([d narration] (emit d narration 1))
  ([d narration tx]
   (let [f (facts d)
         L (transient [";; itonami R4 daily digest — TRANSIENT (:bond/is-transient true), G1/G3/G7." "["])]
     (conj! L (str "[:line.sarutahiko-a :ops/digest-line-oee " (fmt-g (get f "line_oee"))
                   " " tx " :derived] ;; :bond/is-transient true"))
     (conj! L (str "[:line.sarutahiko-a :ops/digest-energy-reduction " (fmt-g (get f "energy_reduction"))
                   " " tx " :derived] ;; :bond/is-transient true"))
     (conj! L (str "[:line.sarutahiko-a :ops/digest-throughput-bottleneck " (get-in d ["throughput" "bottleneck"])
                   " " tx " :derived] ;; :bond/is-transient true"))
     (conj! L (str "[:line.sarutahiko-a :ops/digest-units-per-day " (fmt-g (get f "units_per_day"))
                   " " tx " :derived] ;; :bond/is-transient true"))
     (when (some? (get f "drift_top"))
       (conj! L (str "[:line.sarutahiko-a :ops/digest-drift-count " (get f "drift_n")
                     " " tx " :derived] ;; :bond/is-transient true")))
     (conj! L (str "[:line.sarutahiko-a :ops/digest-narration-backend :"
                   (str/replace (get narration "backend") "-" ".") " " tx " :derived] ;; :bond/is-transient true"))
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
           det-path (if (some #{"--detections"} argv)
                      (io/file (nth argv (inc (.indexOf argv "--detections"))))
                      (io/file here "data" "seed-vision-detections.kotoba.edn"))
           hist-path (if (some #{"--history"} argv)
                       (io/file (nth argv (inc (.indexOf argv "--history"))))
                       (io/file here "data" "seed-ops-history.kotoba.edn"))
           outdir (if (some #{"--out"} argv)
                    (io/file (nth argv (inc (.indexOf argv "--out"))))
                    (io/file here "out"))
           tx (if (some #{"--tx"} argv) (Long/parseLong (nth argv (inc (.indexOf argv "--tx")))) 1)
           {:keys [stations ticks]} (analyze/load-file* seed)
           detections (vis/load-detections (slurp det-path))
           history (when (.exists hist-path) (T/load-history (slurp hist-path)))
           d (build-digest stations ticks detections history)
           narration (narrate d)]
       (.mkdirs outdir)
       (spit (io/file outdir "daily-digest.md") (report-md d narration))
       (spit (io/file outdir "itonami-digest.kotoba.edn") (emit d narration tx))
       0)))
