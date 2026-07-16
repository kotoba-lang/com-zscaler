(ns hakoniwa.methods.distribution
  "hakoniwa 箱庭 — ensemble → outcome DISTRIBUTION + mitooshi-shaped forecast record.
  1:1 Clojure port of `methods/distribution.py` (ADR-2606111500).

  Turns the raw replica ensemble (simulate/ensemble) into the ONLY thing hakoniwa asserts:
  a DISTRIBUTION over the population statistic — quantiles + histogram — and a forecast record
  shaped for mitooshi 見通し (ADR-2606051800) proper-scoring.

  CONSTITUTIONAL:
    G2 — DISTRIBUTION-ONLY. :forecast/point-asserted is structurally false; there is NO
      :forecast/point field. The p50 is reported as a quantile, never as \"the prediction\". 非終末論.
    G3 — NON-STEERING. :forecast/use is drawn from a RESILIENCE-only enum
      (:resilience :preparedness :robustness :research); trade / wager / position / target /
      manipulate / campaign are NOT representable — a breach raises (ex-info).
    G7 — leak-free as-of (:forecast/as-of); mitooshi scores it leak-free.

  House style: Python ':…' keyword strings stay strings; map keys in dist/meta/record are STRING
  keys preserving insertion order (byte-parity with the Python dict). Float arithmetic is IEEE
  double (== CPython). Formatting: `{v:g}` via fmt-g, `{v:.4f}`/`{v:.1f}` via HALF_EVEN
  BigDecimal on the exact double (matches Python's format). Pure fns; file I/O at #?(:clj) edge."
  (:require [clojure.string :as str]
            [hakoniwa.methods.world :as world]
            [hakoniwa.methods.simulate :as simulate]
            #?(:clj [clojure.java.io :as io])))

;; RESILIENCE-only use enum (G3). Steering/speculation uses are NOT members → unrepresentable.
(def allowed-use #{":resilience" ":preparedness" ":robustness" ":research"})
(def hist-bins 10) ;; over [0,1]

;; ── float formatting ────────────────────────────────────────────────────────
(defn- neg-zero? [^double d]
  (and (zero? d) (= (Double/doubleToRawLongBits d) (Double/doubleToRawLongBits -0.0))))

(defn- strip-g-mantissa
  "Strip trailing zeros from the mantissa of a %g result (plain or e-form)."
  [s]
  (if-let [ei (str/index-of s "e")]
    (let [mant (subs s 0 ei) exp (subs s ei)]
      (str (if (str/includes? mant ".")
             (-> mant (str/replace #"0+$" "") (str/replace #"\.$" ""))
             mant)
           exp))
    (if (str/includes? s ".")
      (-> s (str/replace #"0+$" "") (str/replace #"\.$" ""))
      s)))

(defn fmt-g
  "Mirror Python f-string {v:g}: 6 significant digits, trailing zeros stripped, exponent form
  outside [1e-4,1e6), integral floats lose the point, -0.0 → \"-0\"."
  [v]
  (let [d (double v)]
    (cond
      (Double/isNaN d) "nan"
      (Double/isInfinite d) (if (pos? d) "inf" "-inf")
      (zero? d) (if (neg-zero? d) "-0" "0")
      (and (== d (Math/rint d)) (>= (Math/abs d) 1.0) (< (Math/abs d) 1e6))
      (str (long d))
      :else (strip-g-mantissa (format "%.6g" d)))))

#?(:clj
   (defn fmt-fixed
     "Python f\"{x:.Nf}\" — fixed-point, HALF_EVEN rounding on the exact double value."
     [x n]
     (-> (java.math.BigDecimal. (double x))
         (.setScale (int n) java.math.RoundingMode/HALF_EVEN)
         .toPlainString))
   :cljs
   (defn fmt-fixed [x n] (.toFixed (double x) n)))

;; ── quantile / histogram / distribution ──────────────────────────────────────
(defn quantile
  "Linear-interpolated quantile of an already-sorted vector (mirrors distribution.quantile)."
  [sorted-vals ^double q]
  (let [n (count sorted-vals)]
    (cond
      (zero? n) 0.0
      (= n 1) (nth sorted-vals 0)
      :else
      (let [pos (* q (double (dec n)))
            lo (long pos) ;; int(pos): truncation toward zero (pos ≥ 0 here)
            frac (- pos lo)]
        (if (>= (+ lo 1) n)
          (nth sorted-vals (dec n))
          (+ (* (double (nth sorted-vals lo)) (- 1.0 frac))
             (* (double (nth sorted-vals (+ lo 1))) frac)))))))

(defn histogram
  ([vals] (histogram vals hist-bins))
  ([vals bins]
   (let [counts (reduce (fn [cs v]
                          (let [b (min (dec bins) (max 0 (long (* (double v) bins))))]
                            (update cs b inc)))
                        (vec (repeat bins 0))
                        vals)]
     counts)))

(defn distribution
  "Mirror distribution.distribution. Returns a STRING-keyed insertion-ordered map; quantiles is
  itself a STRING-keyed (\":p10\"…) ordered map."
  [results]
  (let [s (vec (sort results))
        n (count s)
        mean (if (pos? n) (/ (reduce + 0.0 s) n) 0.0)
        var (if (pos? n) (/ (reduce (fn [^double acc v] (+ acc (Math/pow (- (double v) mean) 2))) 0.0 s) n) 0.0)]
    (array-map
     "n" n
     "mean" mean
     "stdev" (Math/pow var 0.5)
     "quantiles" (array-map ":p10" (quantile s 0.10) ":p25" (quantile s 0.25)
                            ":p50" (quantile s 0.50) ":p75" (quantile s 0.75)
                            ":p90" (quantile s 0.90))
     "min" (if (pos? n) (nth s 0) 0.0)
     "max" (if (pos? n) (peek s) 0.0)
     "histogram" (histogram s))))

(defn distribution-entropy
  "Shannon entropy of the forecast histogram, and the EFFECTIVE NUMBER OF OUTCOMES it implies
  (perplexity = exp H — the Hill number of the distribution). Where the quantile spread (p90−p10)
  measures the RANGE of the forecast, this measures its SHAPE: a consensus histogram (all mass in one
  bin) reads as 1.0 effective outcome, a bimodal one as ≈2.0 (the forecast has two likely futures),
  a flat one as the full bin count (maximal uncertainty). The :normalized value (H ÷ ln bins ∈ [0,1])
  is the fraction of maximal uncertainty — the preparedness read: how many distinct futures the box
  must plan for. Distribution-SHAPE only — it asserts no point (G2, 非終末論) and does not steer
  (G3, resilience-only). Takes a histogram (bin counts); returns {:entropy :effective-outcomes
  :normalized}."
  [counts]
  (let [tot (reduce + 0.0 counts)
        nb (count counts)]
    (if (or (not (pos? tot)) (<= nb 1))
      {:entropy 0.0 :effective-outcomes (if (pos? tot) 1.0 0.0) :normalized 0.0}
      (let [ps (->> counts (map #(/ (double %) tot)) (remove zero?))
            h (- (reduce + 0.0 (map (fn [p] (* p (Math/log p))) ps)))]
        {:entropy h
         :effective-outcomes (Math/exp h)
         :normalized (/ h (Math/log nb))}))))

;; ── forecast record (G2 distribution-only, G3 resilience-use-only) ────────────
(defn forecast-record
  "mitooshi-shaped forecast record — DISTRIBUTION-ONLY (G2), resilience-USE-only (G3).
  STRING-keyed insertion-ordered map mirroring the Python dict. Raises on a non-resilience use."
  ([nodes dist meta as-of] (forecast-record nodes dist meta as-of ":preparedness"))
  ([nodes dist meta as-of use]
   (when-not (contains? allowed-use use)
     (throw (ex-info (str "G3 violation: :forecast/use " use " is not a resilience use ("
                          (vec (sort allowed-use)) "); steering/speculation is unrepresentable")
                     {:gate "G3" :use use})))
   (let [outs (world/outcomes nodes)
         target (if (seq outs)
                  (let [o (get outs (first (world/ordered-keys outs)))]
                    (get o ":sim/label" (get o ":sim/id" "outcome")))
                  "outcome")]
     (array-map
      ":forecast/actor" ":hakoniwa"
      ":forecast/target" target
      ":forecast/kind" ":distribution"
      ":forecast/point-asserted" false           ;; G2 — structural; there is no point field
      ":forecast/horizon-steps" (get meta "steps")
      ":forecast/replicas" (get meta "replicas")
      ":forecast/quantiles" (get dist "quantiles")
      ":forecast/histogram" (get dist "histogram")
      ":forecast/mean" (get dist "mean")
      ":forecast/stdev" (get dist "stdev")
      ":forecast/use" use                          ;; G3 — resilience-only enum
      ":forecast/as-of" as-of                      ;; G7 — leak-free boundary
      ":forecast/sourced-from" ":hakoniwa-synthetic-ensemble"))))

;; ── report (markdown) ─────────────────────────────────────────────────────────
(defn report-md
  "1:1 with distribution.report_md. Returns the report text."
  [nodes dist meta as-of]
  (let [L (transient [])
        f4 (fn [v] (fmt-fixed v 4))
        f1 (fn [v] (fmt-fixed v 1))]
    (conj! L "# hakoniwa 箱庭 — forward-simulation outcome DISTRIBUTION (never a point)\n")
    (conj! L (str "> **G2 — DISTRIBUTION-ONLY.** hakoniwa asserts a distribution over possible "
                  "futures, never a single foretold outcome (非終末論). **G1 — every agent is a "
                  "SYNTHETIC latent persona**, not a real person (no PII). **G3 — routed to "
                  "RESILIENCE / preparedness**, never to trading, targeting, or persuasion.\n"))
    (let [outs (world/outcomes nodes)
          target (if (seq outs)
                   (get (get outs (first (world/ordered-keys outs))) ":sim/label" "outcome")
                   "outcome")]
      (conj! L (str "**Scenario**: " target))
      (conj! L (str "**Box**: " (get meta "personas") " synthetic personas · " (get meta "edges") " 縁 · "
                    (get meta "steps") " steps × " (get meta "replicas") " replicas (seed " (get meta "seed")
                    ", jitter " (get meta "jitter") ") · as-of " as-of "\n")))
    (let [q (get dist "quantiles")]
      (conj! L "\n## Outcome distribution — town-wide mean adoption stance\n")
      (conj! L "| statistic | value |")
      (conj! L "|---|---:|")
      (conj! L (str "| mean | " (f4 (get dist "mean")) " |"))
      (conj! L (str "| stdev | " (f4 (get dist "stdev")) " |"))
      (conj! L (str "| p10 | " (f4 (get q ":p10")) " |"))
      (conj! L (str "| p25 | " (f4 (get q ":p25")) " |"))
      (conj! L (str "| **p50 (median, a quantile — NOT 'the prediction')** | " (f4 (get q ":p50")) " |"))
      (conj! L (str "| p75 | " (f4 (get q ":p75")) " |"))
      (conj! L (str "| p90 | " (f4 (get q ":p90")) " |"))
      (conj! L (str "| min / max | " (f4 (get dist "min")) " / " (f4 (get dist "max")) " |")))
    (conj! L "\n## Histogram (10 bins over [0,1])\n")
    (conj! L "| bin | range | count |")
    (conj! L "|---:|---|---:|")
    (doseq [[b c] (map-indexed vector (get dist "histogram"))]
      (conj! L (str "| " b " | [" (f1 (/ (double b) 10)) ", " (f1 (/ (double (+ b 1)) 10)) ") | " c " |")))
    (conj! L "\n## Handoff to mitooshi 見通し\n")
    (conj! L (str "_This distribution is handed to mitooshi (ADR-2606051800) as a "
                  "`:forecast/kind :distribution` record (`:forecast/point-asserted false`, "
                  "`:forecast/use :preparedness`) for leak-free proper-scoring against the realised "
                  "outcome. hakoniwa generates the ensemble; mitooshi scores the skill._\n"))
    (conj! L (str "\n---\n_hakoniwa 箱庭 · ADR-2606111500 · synthetic-persona forward simulation · "
                  "distribution-only · resilience-routed · transparent (相互監視). Live large-swarm "
                  "runs + any social emission are G8/Council-gated._\n"))
    (str/join "\n" (persistent! L))))

;; ── EDN emit (forecast record) ────────────────────────────────────────────────
(defn fmt-edn
  "Port of distribution._fmt_edn: bool/nil/keyword-literal/string/float/dict/list → EDN text."
  [v]
  (cond
    (true? v) "true"
    (false? v) "false"
    (nil? v) "nil"
    (string? v) (if (str/starts-with? v ":")
                  v
                  (str "\"" (-> v (str/replace "\\" "\\\\") (str/replace "\"" "\\\"")) "\""))
    (double? v) (fmt-g v)
    (float? v) (fmt-g v)
    (map? v) (str "{" (str/join " " (map (fn [k] (str k " " (fmt-edn (get v k)))) (world/ordered-keys v))) "}")
    (sequential? v) (str "[" (str/join " " (map fmt-edn v)) "]")
    :else (str v)))

(defn forecast-edn
  "Port of distribution.forecast_edn."
  [rec]
  (let [body (str/join "\n " (map (fn [k] (str k " " (fmt-edn (get rec k)))) (world/ordered-keys rec)))]
    (str ";; hakoniwa 箱庭 — GENERATED mitooshi-shaped forecast record (ADR-2606111500).\n"
         ";; DISTRIBUTION-ONLY (G2): no :forecast/point field exists. resilience-USE-only (G3).\n"
         "{" body "}\n")))

#?(:clj
   (defn -main
     "CLI entry (file I/O at the edge): distribution-report.md + forecast-record.kotoba.edn."
     [& argv]
     (let [argv (vec argv)
           here (-> *file* io/file .getParentFile .getParentFile)
           scenario (if (and (seq argv) (not (str/starts-with? (first argv) "--")))
                      (io/file (first argv))
                      (io/file here "data" "seed-scenario.kotoba.edn"))
           opt (fn [flag dflt cast]
                 (if (some #{flag} argv) (cast (nth argv (inc (.indexOf argv flag)))) dflt))
           outdir (if (some #{"--out"} argv) (io/file (nth argv (inc (.indexOf argv "--out")))) (io/file here "out"))
           steps (opt "--steps" simulate/default-steps #(Long/parseLong %))
           replicas (opt "--replicas" simulate/default-replicas #(Long/parseLong %))
           seed (opt "--seed" simulate/default-seed #(Long/parseLong %))
           as-of (opt "--as-of" "2026-06-11T00:00:00Z" str)
           {:keys [nodes edges]} (world/load-file* scenario)
           [results meta] (simulate/ensemble nodes edges {:steps steps :replicas replicas :seed seed})
           dist (distribution results)
           rec (forecast-record nodes dist meta as-of)]
       (.mkdirs outdir)
       (spit (io/file outdir "distribution-report.md") (report-md nodes dist meta as-of))
       (spit (io/file outdir "forecast-record.kotoba.edn") (forecast-edn rec))
       (println (str "hakoniwa distribution → " (io/file outdir "distribution-report.md")))
       (println (str "  p10/p50/p90 = " (fmt-fixed (get (get dist "quantiles") ":p10") 4) " / "
                     (fmt-fixed (get (get dist "quantiles") ":p50") 4) " / "
                     (fmt-fixed (get (get dist "quantiles") ":p90") 4) " (distribution-only, G2)"))
       0)))
