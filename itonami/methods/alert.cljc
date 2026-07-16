(ns itonami.methods.alert
  "itonami 営み — R9 operational threshold alerts (ADR-2606082300).
  1:1 Clojure port of `methods/alert.py`.

  The alarm half of a factory-operations HMI, made charter-clean. Checks the current KPIs
  against ABSOLUTE configured thresholds and raises graded {info/warn/critical} flags.

  THE CHARTER DISTINCTION (G1): an itonami alert is ADVISORY. It raises a flag; it NEVER trips
  an e-stop, halts the line, or writes anything to the OT bus. No :estop/:halt/:trip token is
  representable in the output.

  CONSTITUTIONAL: G1 advisory only; G2 line/station scope; G3 non-adjudicating (thresholds are
  documented config, caller-overridable).

  House style: Python ':…' keyword strings stay strings; string-keyed data; pure fns;
  file I/O only at #?(:clj) edges. Reuses itonami.methods.analyze."
  (:require [clojure.string :as str]
            [itonami.methods.analyze :as analyze]
            #?(:clj [clojure.java.io :as io])))

;; Documented thresholds (G5 — :representative config, caller-overridable; NOT measured facts).
;; polarity true = higher-better (alert when BELOW); false = lower-better (alert when ABOVE).
(def DEFAULT-THRESHOLDS
  {"oee"              {"polarity" true  "warn" 0.60 "critical" 0.45}
   "scrap_rate"       {"polarity" false "warn" 0.05 "critical" 0.15}
   "energy_per_good"  {"polarity" false "warn" 40.0 "critical" 80.0}
   "idle_energy_frac" {"polarity" false "warn" 0.10 "critical" 0.25}})

;; iteration order of the threshold dict (Python dict insertion order)
(def ^:private threshold-order ["oee" "scrap_rate" "energy_per_good" "idle_energy_frac"])

(defn- inf? [x]
  #?(:clj (and (number? x) (Double/isInfinite (double x)))
     :cljs (and (number? x) (infinite? x))))

(defn severity
  "Return \"critical\" | \"warn\" | nil for a value against a (warn, critical) threshold."
  [value spec]
  (cond
    (or (nil? value) (inf? value)) nil
    (get spec "polarity")
    (cond (< value (get spec "critical")) "critical"
          (< value (get spec "warn")) "warn"
          :else nil)
    :else
    (cond (> value (get spec "critical")) "critical"
          (> value (get spec "warn")) "warn"
          :else nil)))

(defn evaluate
  "Compare each scope's KPIs to thresholds → graded alert list (worst-first)."
  ([stations res] (evaluate stations res nil))
  ([stations res thresholds]
   (let [th (or thresholds DEFAULT-THRESHOLDS)
         th-order (if thresholds (keys th) threshold-order)
         res-order (or (:itonami.methods.analyze/order (meta res)) (keys res))
         scopes (cons [":line.sarutahiko-a" (get res "_line")]
                      (for [s res-order :when (not (str/starts-with? s "_"))] [s (get res s)]))
         alerts (for [[scope kpis] scopes
                      kpi th-order
                      :when (contains? kpis kpi)
                      :let [spec (get th kpi)
                            sev (severity (get kpis kpi) spec)]
                      :when sev]
                  {"scope" scope "kpi" kpi "value" (get kpis kpi)
                   "threshold" (if (= sev "critical") (get spec "critical") (get spec "warn"))
                   "severity" sev
                   "label" (get-in stations [scope ":station/label"] scope)})
         order {"critical" 0 "warn" 1}]
     (vec (sort-by (fn [a] [(get order (get a "severity")) (get a "scope") (get a "kpi")]) alerts)))))

(defn counts
  [alerts]
  {"critical" (count (filter #(= (get % "severity") "critical") alerts))
   "warn" (count (filter #(= (get % "severity") "warn") alerts))
   "total" (count alerts)})

(defn- fmt-f [x n]
  (-> (java.math.BigDecimal. (double x))
      (.setScale (int n) java.math.RoundingMode/HALF_EVEN) (.toPlainString)))

(defn- fmt-3g
  "Python `{x:.3g}` — 3 significant digits."
  [x]
  (let [d (double x)]
    (if (== d (Math/rint d))
      (let [s (format "%.3g" d)]
        ;; integral 3g like 2.00 → strip but keep magnitude; Python {2:.3g}=2.00 -> '2.00'? actually '2'
        (if (str/includes? s ".")
          (let [s2 (-> s (str/replace #"0+$" "") (str/replace #"\.$" ""))] s2)
          s))
      (let [s (format "%.3g" d)]
        (if (str/includes? s ".")
          (-> s (str/replace #"0+$" "") (str/replace #"\.$" ""))
          s)))))

(defn report-md
  [alerts]
  (let [c (counts alerts)
        L (transient []) P (fn [s] (conj! L s))]
    (P "# itonami 営み — R9 operational alerts (advisory)\n")
    (P (str "> **G1 — ADVISORY ONLY.** An itonami alert raises a flag for a human / Council; it "
            "NEVER stops the line, commands the safety system, or sends anything to the OT bus. "
            "Safety interlocks live in the PLC / safety system, not here. Thresholds are "
            "documented config (G5), not facts; station/line scope only (G2).\n"))
    (P (str "\n**" (get c "critical") " critical · " (get c "warn") " warn** ("
            (get c "total") " total)\n"))
    (P "| severity | scope | KPI | value | threshold |")
    (P "|---|---|---|---:|---:|")
    (doseq [a alerts]
      (P (str "| " (get a "severity") " | " (get a "label") " | " (get a "kpi") " | "
              (fmt-3g (get a "value")) " | " (fmt-3g (get a "threshold")) " |")))
    (P (str "\n---\n_itonami 営み R9 · ADR-2606082300 · advisory · informs-only · "
            "documented-thresholds · station-scale._\n"))
    (str/join "\n" (persistent! L))))

(defn emit
  "Transient EAVT alert datoms (computed on read, never durable — G3). ADVISORY only."
  ([alerts] (emit alerts 1))
  ([alerts tx]
   (let [L (transient
            [";; itonami R9 alerts — TRANSIENT (:bond/is-transient true), G1/G3. ADVISORY only." "["])]
     (doseq [a alerts]
       (conj! L (str "[" (get a "scope") " :alert/" (str/replace (get a "kpi") "_" "-")
                     " :" (get a "severity") " " tx " :derived] ;; :bond/is-transient true")))
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
           outdir (if (some #{"--out"} argv)
                    (io/file (nth argv (inc (.indexOf argv "--out"))))
                    (io/file here "out"))
           tx (if (some #{"--tx"} argv) (Long/parseLong (nth argv (inc (.indexOf argv "--tx")))) 1)
           {:keys [stations ticks]} (analyze/load-file* seed)
           res (analyze/analyze stations ticks)
           alerts (evaluate stations res)]
       (.mkdirs outdir)
       (spit (io/file outdir "alerts.md") (report-md alerts))
       (spit (io/file outdir "itonami-alerts.kotoba.edn") (emit alerts tx))
       0)))
