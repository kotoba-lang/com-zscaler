(ns itonami.methods.trend
  "itonami 営み — R7 KPI trend / drift detector (ADR-2606082300).
  1:1 Clojure port of `methods/trend.py` (which imports `from analyze import read_edn`).

  Reads durable daily ops-KPI snapshots (:opsday/*) and surfaces DRIFT — an OEE slowly
  degrading, scrap creeping up, energy per unit rising — before any single day looks alarming.
  The continuous-monitoring half of what FOX promises, made charter-clean.

    load-history   — read durable :opsday/* snapshots (the as-of series; ground state)
    analyze-trends — per (scope, KPI): first/last, relative change, least-squares slope, and a
                     polarity-aware direction {:improving :flat :degrading} + regression flag

  CONSTITUTIONAL:
    G1 — surfaces drift and RECOMMENDS attention; never actuates.
    G2 — scope is line/station only; there is no :worker/* series (anti-labor-surveillance).
      A trajectory of a person is unrepresentable — load-history REJECTS such a series.
    G3 — non-adjudicating. Directions are computed read-time aggregates over disclosed snapshots.

  Requires the merged itonami.methods.analyze ns. House style: ':…' strings literal; pure fns;
  I/O at #?(:clj) edges; float formatting EXACT (HALF_EVEN over the exact double; fmt-g for {v:g})."
  (:require [clojure.string :as str]
            [itonami.methods.analyze :as analyze]
            #?(:clj [clojure.java.io :as io])))

;; KPI polarity: true = higher is better, false = lower is better. ORDER matters (dict-iteration).
(def KPI-POLARITY-ORDER [":opsday/oee" ":opsday/scrap-rate" ":opsday/energy-per-good"])
(def KPI-POLARITY
  {":opsday/oee" true ":opsday/scrap-rate" false ":opsday/energy-per-good" false})
(def FLAT-REL-THRESHOLD 0.05)   ; |relative change| below this over the window → :flat

;; ── float formatting (Python f-string parity: HALF_EVEN over the exact double) ──

(defn- fmt-f
  [x n]
  (-> (java.math.BigDecimal. (double x))
      (.setScale (int n) java.math.RoundingMode/HALF_EVEN)
      (.toPlainString)))

(defn- fmt-pct-signed
  "Python `{x:+.1%}` — sign always shown, x*100 then .1f, then %."
  [x]
  (let [s (fmt-f (* 100.0 (double x)) 1)]
    (str (if (str/starts-with? s "-") s (str "+" s)) "%")))

(defn- fmt-g
  [v]
  (let [d (double v)]
    (if (and (== d (Math/rint d)) (< (Math/abs d) 1e15))
      (str (long d))
      (let [s (format "%.6g" d)]
        (if (str/includes? s ".")
          (-> s (str/replace #"0+$" "") (str/replace #"\.$" ""))
          s)))))

(defn load-history
  "Read durable :opsday/* snapshots. RAISES (G2) if any record carries a person/worker series."
  [text]
  (let [forms (analyze/read-edn text)
        recs (filter #(and (map? %) (contains? % ":opsday/day")) forms)]
    (doseq [r recs]
      (doseq [bad [":worker/" ":person/" ":operator/"]]
        (when (some #(str/starts-with? (str %) bad) (keys r))
          (throw (ex-info (str "G2 violation: ops history carries a person/worker series (" bad ")")
                          {:bad bad})))))
    (vec recs)))

(defn- slope
  [xs ys]
  (let [n (count xs)]
    (if (< n 2)
      0.0
      (let [mx (/ (reduce + xs) (double n))
            my (/ (reduce + ys) (double n))
            den (reduce + (map (fn [x] (let [d (- x mx)] (* d d))) xs))]
        (if (== den 0)
          0.0
          (/ (reduce + (map (fn [x y] (* (- x mx) (- y my))) xs ys)) den))))))

(defn- direction
  [first* last* higher-better]
  (let [base (if (not= first* 0) (Math/abs (double first*)) 1.0)
        rel (/ (- last* first*) base)]
    (if (< (Math/abs rel) FLAT-REL-THRESHOLD)
      ":flat"
      (let [improving (if higher-better (> rel 0) (< rel 0))]
        (if improving ":improving" ":degrading")))))

(defn analyze-trends
  "Per scope, per KPI: first/last/rel_change/slope/direction/regression. Scope map is
  insertion-ordered (defaultdict by_scope parity)."
  [records]
  (let [;; group by scope preserving first-touch order
        by-scope (reduce
                  (fn [acc r]
                    (let [sc (get r ":opsday/scope")
                          had? (contains? acc sc)
                          acc' (update acc sc (fnil conj []) r)]
                      (if had? acc' (vary-meta acc' update ::order (fnil conj []) sc))))
                  ^{::order []} {}
                  records)
        scopes (or (::order (meta by-scope)) (keys by-scope))]
    (reduce
     (fn [out scope]
       (let [recs (sort-by #(get % ":opsday/day") (get by-scope scope))
             days (mapv #(double (get % ":opsday/day")) recs)
             kpis (reduce
                   (fn [m attr]
                     (let [higher-better (get KPI-POLARITY attr)
                           ys (mapv #(double (get % attr)) (filter #(contains? % attr) recs))]
                       (if (< (count ys) 2)
                         m
                         (let [first* (first ys)
                               last* (peek ys)
                               base (if (not= first* 0) (Math/abs first*) 1.0)
                               dir (direction first* last* higher-better)]
                           (assoc m attr
                                  {"first" first* "last" last*
                                   "rel_change" (/ (- last* first*) base)
                                   "slope" (slope (vec (take (count ys) days)) ys)
                                   "direction" dir
                                   "regression" (= dir ":degrading")})))))
                   ^{::korder []} {}
                   KPI-POLARITY-ORDER)
             ;; preserve KPI insertion order (only present KPIs, in KPI-POLARITY-ORDER)
             kpis (vary-meta kpis assoc ::korder
                             (filterv #(contains? kpis %) KPI-POLARITY-ORDER))]
         (-> out
             (assoc scope kpis)
             (vary-meta update ::sorder (fnil conj []) scope))))
     ^{::sorder []} {}
     scopes)))

(defn- scope-order [trends] (or (::sorder (meta trends)) (keys trends)))
(defn- kpi-order [kpis] (or (::korder (meta kpis)) (keys kpis)))

(defn regressions
  "Flat list of [scope kpi rel-change] for every degrading series, sorted by -|rel-change|
  (stable — Python sorted is stable, ties keep insertion order)."
  [trends]
  (let [rows (for [scope (scope-order trends)
                   attr (kpi-order (get trends scope))
                   :let [t (get-in trends [scope attr])]
                   :when (get t "regression")]
               [scope attr (get t "rel_change")])]
    (vec (sort-by (fn [r] (- (Math/abs (double (nth r 2))))) rows))))

(defn- short-attr [attr] (last (str/split attr #"/")))

(defn report-md
  [trends]
  (let [regs (regressions trends)
        L (transient [])
        P (fn [s] (conj! L s))]
    (P "# itonami 営み — R7 KPI trend / drift report (as-of trajectory)\n")
    (P (str "> **G1** surfaces drift + recommends attention, never actuates. **G2** line/"
            "station scope only — no worker trajectory. **G3** directions are read-time over "
            "disclosed daily snapshots; the snapshots are the durable as-of facts.\n"))
    (P (str "\n**" (count regs) " degrading series** (attention, worst rel-change first):\n"))
    (doseq [[scope attr rel] regs]
      (P (str "- " scope " · " (short-attr attr) " · " (fmt-pct-signed rel) " over window")))

    (P "\n## All series\n")
    (P "| scope | KPI | first → last | direction |")
    (P "|---|---|---|---|")
    (doseq [scope (sort (scope-order trends))]
      (doseq [attr (kpi-order (get trends scope))]
        (let [t (get-in trends [scope attr])]
          (P (str "| " scope " | " (short-attr attr) " | "
                  (fmt-g (get t "first")) " → " (fmt-g (get t "last")) " | "
                  (if (str/starts-with? (get t "direction") ":") (subs (get t "direction") 1) (get t "direction")) " |")))))
    (P (str "\n---\n_itonami 営み R7 · ADR-2606082300 · trajectory-not-snapshot · "
            "drift-surfacing · recommend-not-actuate · station-scale._\n"))
    (str/join "\n" (persistent! L))))

(defn emit
  "Transient EAVT trend datoms (computed on read, never durable — G3)."
  ([trends] (emit trends 1))
  ([trends tx]
   (let [L (transient [";; itonami R7 KPI trends — TRANSIENT (:bond/is-transient true), G1/G3." "["])]
     (doseq [scope (sort (scope-order trends))]
       (doseq [attr (kpi-order (get trends scope))]
         (let [t (get-in trends [scope attr])
               short (short-attr attr)]
           (conj! L (str "[" scope " :trend/" short "-direction " (get t "direction")
                         " " tx " :derived] ;; :bond/is-transient true"))
           (when (get t "regression")
             (conj! L (str "[" scope " :trend/" short "-regression true "
                           tx " :derived] ;; :bond/is-transient true"))))))
     (conj! L "]")
     (str (str/join "\n" (persistent! L)) "\n"))))

#?(:clj
   (defn -main
     [& argv]
     (let [argv (vec argv)
           here (-> *file* io/file .getParentFile .getParentFile)
           seed (if (and (seq argv) (not (str/starts-with? (first argv) "--")))
                  (io/file (first argv))
                  (io/file here "data" "seed-ops-history.kotoba.edn"))
           outdir (if (some #{"--out"} argv)
                    (io/file (nth argv (inc (.indexOf argv "--out"))))
                    (io/file here "out"))
           tx (if (some #{"--tx"} argv)
                (Long/parseLong (nth argv (inc (.indexOf argv "--tx")))) 1)
           records (load-history (slurp seed))
           trends (analyze-trends records)
           regs (regressions trends)]
       (.mkdirs outdir)
       (spit (io/file outdir "trend-report.md") (report-md trends))
       (spit (io/file outdir "itonami-trends.kotoba.edn") (emit trends tx))
       (println (str "itonami R7: " (count records) " snapshots, " (count (scope-order trends))
                     " scopes, " (count regs) " degrading series → " outdir))
       (doseq [[scope attr rel] regs]
         (println (str "  ↓ " scope " " (short-attr attr) " " (fmt-pct-signed rel))))
       0)))
