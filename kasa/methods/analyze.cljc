(ns kasa.methods.analyze
  "kasa 嵩 — analyze cell. 1:1 Clojure port of methods/analyze.py (ADR-2606072000).

  Reads the worldwide computing-capacity observation graph and emits AGGREGATE-FIRST
  observations of the world's annual compute MAGNITUDE and GROWTH (年間増加量):
    - per-series year-over-year growth (:compute.growth :yoy — consecutive years)
    - per-series compound annual growth rate over the span (:compute.growth :cagr)
    - domain aggregates (:compute.agg — coverage-honest, within one domain×unit, never double-count)
    - out/intel-report.md + out/compute-growth.kotoba.edn

  CONSTITUTIONAL (read before any change):
    G2 NON-ADJUDICATING / G9 PLANNING-LENS / G4 NO FORECAST: every number is either a quantity a
      public source measured/estimated, or a transparent rate-of-change of two such quantities. kasa
      reports what the world ADDED; it never ranks countries, builds a targeting list, advises an
      investment, or projects a FUTURE value (forecasting is mitooshi 見通し).
    G12 NO DOUBLE-COUNT: aggregation requires a COMMON metric+unit+SCALE and is confined to one
      domain, so memory (:dram/:nand), a subset of :semiconductor, is structurally never folded in.

  House style: Python ':…' keyword strings stay strings; pure fns; file I/O only at #?(:clj) edges.
  Python dict order = insertion order — preserved here by ordered accumulation + the same stable
  sorts. round()/{:g}/{:,.0f}/{:.1e}/{:+.1f} formatting is reproduced byte-for-byte."
  (:require [clojure.string :as str]
            [kasa.methods.kasa-edn :as kasa-edn]))

;; ── Python-faithful numeric formatting ──────────────────────────────────────

(defn py-repr-float
  "Render a double the way Python's repr() does (shortest round-tripping form).
  Clojure's pr-str of a double already produces the shortest decimal; we only need to
  convert Java's scientific form (3.0E23) to Python's (3e+23): strip a trailing .0 from
  the mantissa and rewrite the exponent as e±dd (sign always, ≥2 digits)."
  [^double v]
  (let [s (pr-str v)]
    (if-let [idx (str/index-of s "E")]
      (let [mant (subs s 0 idx)
            exp  (subs s (inc idx))
            mant (if (str/ends-with? mant ".0") (subs mant 0 (- (count mant) 2)) mant)
            neg  (str/starts-with? exp "-")
            digits (-> exp (str/replace "-" "") (str/replace "+" ""))
            digits (if (< (count digits) 2) (str "0" digits) digits)]
        (str mant "e" (if neg "-" "+") digits))
      s)))

(defn- half-even-round
  "round(x, ndigits) with Python's banker's rounding (HALF_EVEN), via exact BigDecimal."
  [^double x ndigits]
  (-> (java.math.BigDecimal. x)
      (.setScale (int ndigits) java.math.RoundingMode/HALF_EVEN)
      (.doubleValue)))

(defn round4 [x] (half-even-round (double x) 4))

(def ^:private us-locale #?(:clj java.util.Locale/US :cljs nil))

(defn- bd-fixed
  "Round a double to `scale` decimals with HALF_EVEN (Python's float-format rounding), as a
  BigDecimal carrying that exact scale (so .setScale(0) prints no decimal point)."
  [^double v scale]
  (-> (java.math.BigDecimal. v) (.setScale (int scale) java.math.RoundingMode/HALF_EVEN)))

(defn- group-thousands
  "Insert ',' every 3 digits in the integer part of a non-negative plain decimal string."
  [^String s]
  (let [neg (str/starts-with? s "-")
        s (if neg (subs s 1) s)
        [int-part frac] (str/split s #"\." 2)
        rev (str/reverse int-part)
        grouped (->> (partition-all 3 rev) (map #(apply str %)) (str/join ",") str/reverse)]
    (str (when neg "-") grouped (when frac (str "." frac)))))

(defn- fmt-comma
  "Python f\"{v:,.0f}\" — round to 0 decimals (HALF_EVEN) with thousands separators."
  [^double v]
  #?(:clj (group-thousands (.toPlainString (bd-fixed v 0)))
     :cljs (str v)))

(defn- fmt-1e
  "Python f\"{v:.1e}\" — one-decimal scientific with e±dd exponent (≥2 digits)."
  [^double v]
  #?(:clj (String/format us-locale "%.1e" (object-array [v]))
     :cljs (str v)))

(defn- fmt-signed-1
  "Python f\"{n:+.1f}\" — signed one-decimal fixed with HALF_EVEN rounding."
  [^double n]
  #?(:clj (let [bd (bd-fixed n 1)
                s (.toPlainString bd)]
            (if (or (str/starts-with? s "-") (.startsWith s "-")) s (str "+" s)))
     :cljs (str n)))

(defn- pct
  "Python f\"{x*100:+.1f}%\" — signed one-decimal percent."
  [^double x]
  (str (fmt-signed-1 (* x 100.0)) "%"))

;; ── load / shape ─────────────────────────────────────────────────────────────

(defn load
  "→ {:series {id row} :obs [rows] :sources {id row}}. Dicts preserve file insertion order."
  [rows]
  (reduce
   (fn [acc r]
     (cond
       (not (map? r)) acc
       (contains? r ":compute.series/id") (assoc-in acc [:series (get r ":compute.series/id")] r)
       (contains? r ":compute.obs/id")    (update acc :obs conj r)
       (contains? r ":compute.source/id") (assoc-in acc [:sources (get r ":compute.source/id")] r)
       :else acc))
   {:series (array-map) :obs [] :sources (array-map)}
   rows))

#?(:clj
   (defn load-file*
     "Read + parse an EDN graph file → {:series :obs :sources}. File I/O only at this edge."
     [path]
     (load (kasa-edn/read-file path))))

;; ── ordered-map helper (first-touch insertion order, mirroring Python dict) ──

(defn- omap [] ^{::order []} {})

(defn- omap-assoc [m k v]
  (let [had? (contains? m k)
        m' (assoc m k v)]
    (if had? (with-meta m' (meta m))
        (with-meta m' (update (meta m) ::order conj k)))))

(defn- omap-keys [m] (or (::order (meta m)) (keys m)))

(defn by-series-year
  "{series_id {year value}} — outer + inner maps in first-touch insertion order."
  [obs]
  (reduce
   (fn [out o]
     (let [sid (get o ":compute.obs/series")
           y (long (get o ":compute.obs/year"))
           v (double (get o ":compute.obs/value"))
           inner (omap-assoc (get out sid (omap)) y v)]
       (omap-assoc out sid inner)))
   (omap)
   obs))

(defn- growth-row [sid kind fr to value basis]
  ;; insertion order of keys matters for edn_dump; build an explicit ordered map
  (with-meta
    {":compute.growth/id" (str "growth." sid "." fr "-" to "." (subs kind 1))
     ":compute.growth/series" sid
     ":compute.growth/kind" kind
     ":compute.growth/from-year" fr
     ":compute.growth/to-year" to
     ":compute.growth/value" (round4 value)
     ":compute.growth/basis" basis
     ":compute.growth/sourcing" ":synthesized"}
    {::order [":compute.growth/id" ":compute.growth/series" ":compute.growth/kind"
              ":compute.growth/from-year" ":compute.growth/to-year" ":compute.growth/value"
              ":compute.growth/basis" ":compute.growth/sourcing"]}))

(defn derive-growth
  "→ list of :compute.growth maps (all :synthesized): per-series YoY + full-span CAGR."
  [sy]
  (reduce
   (fn [growth sid]
     (let [years (get sy sid)
           ys (sort (omap-keys years))]
       (as-> growth g
         ;; YoY for each consecutive pair
         (reduce
          (fn [g [prev cur]]
            (if (and (= cur (+ prev 1)) (not= (get years prev) 0.0))
              (conj g (growth-row sid ":yoy" prev cur
                                  (- (/ (get years cur) (get years prev)) 1.0)
                                  (str "obs[" cur "]/obs[" prev "]")))
              g))
          g
          (map vector ys (rest ys)))
         ;; CAGR over the full observed span
         (if (and (>= (count ys) 2)
                  (> (get years (first ys)) 0.0)
                  (> (get years (last ys)) 0.0))
           (let [y0 (first ys) yl (last ys)
                 span (- yl y0)
                 cagr (- (Math/pow (/ (get years yl) (get years y0)) (/ 1.0 span)) 1.0)]
             (conj g (growth-row sid ":cagr" y0 yl cagr
                                 (str "(obs[" yl "]/obs[" y0 "])^(1/" span ")-1"))))
           g))))
   []
   (omap-keys sy)))

(defn doubling-period
  "The doubling PERIOD (years) implied by a MEASURED CAGR: ln 2 / ln(1 + cagr) — the canonical
  compute-growth reading (a 100%/yr CAGR ≡ a 1-year doubling; a 59%/yr ≡ ~1.5-year doubling). This
  is the measured 年間増加量 expressed in different units (doubling-years), exactly as
  `:compute.growth/*` is documented to be 'a measured rate of change, not a forecast'. It is a PURE
  transform of the already-observed rate: it projects NO dated future value and asserts NOTHING
  about whether the rate continues (G4 — kasa records past/present actuals + measured growth, never
  a forecast; that is mitooshi 見通し). Returns nil for a non-positive CAGR (no doubling at zero or
  negative growth)."
  [cagr]
  (when (and cagr (pos? (double cagr)))
    (/ (Math/log 2.0) (Math/log (+ 1.0 (double cagr))))))

(defn series-doubling-periods
  "Attach the doubling-period to each measured :cagr growth row — the readable companion to CAGR
  (a 59% CAGR is more legible as a ~1.5-year doubling). Filters the :cagr rows out of a
  `derive-growth` result, computes each series' doubling period from its MEASURED CAGR (skipping
  non-positive CAGRs, which do not double), and returns [{:series :cagr :doubling-years} …] sorted
  by doubling-years ascending (the fastest-doubling measured series first). Descriptive only — same
  G2/G4 stance as the CAGR it restates (no forecast, no entity ranking; the series are capacity
  dimensions, not countries/companies)."
  [growth-rows]
  (->> growth-rows
       (filter #(= ":cagr" (get % ":compute.growth/kind")))
       (keep (fn [r]
               (when-let [dp (doubling-period (get r ":compute.growth/value"))]
                 {:series (get r ":compute.growth/series")
                  :cagr (get r ":compute.growth/value")
                  :doubling-years (round4 dp)})))
       (sort-by :doubling-years)
       vec))

(defn- agg-row [domain metric unit scale y sum n]
  (with-meta
    {":compute.agg/id" (str "agg." (subs domain 1) "." (subs metric 1) "." (subs unit 1) "." (subs scale 1) "." y)
     ":compute.agg/dimension" ":domain"
     ":compute.agg/key" domain
     ":compute.agg/year" y
     ":compute.agg/metric" metric
     ":compute.agg/unit" unit
     ":compute.agg/scale" scale
     ":compute.agg/sum" (round4 sum)
     ":compute.agg/n" n
     ":compute.agg/sourcing" ":synthesized"}
    {::order [":compute.agg/id" ":compute.agg/dimension" ":compute.agg/key" ":compute.agg/year"
              ":compute.agg/metric" ":compute.agg/unit" ":compute.agg/scale" ":compute.agg/sum"
              ":compute.agg/n" ":compute.agg/sourcing"]}))

(defn aggregates
  "Σ per (domain, metric, unit, scale, year) — coverage-honest, single-domain, no double-count."
  [series sy]
  (let [acc (reduce
             (fn [acc sid]
               (let [s (get series sid {})
                     domain (get s ":compute.series/domain" ":unknown")
                     metric (get s ":compute.series/metric" ":unknown")
                     unit (get s ":compute.series/unit" ":unknown")
                     scale (get s ":compute.series/scale" ":ones")
                     years (get sy sid)]
                 (reduce
                  (fn [acc y]
                    (let [v (get years y)
                          key [domain metric unit scale y]
                          a (get acc key {:sum 0.0 :n 0})]
                      (assoc acc key {:sum (+ (:sum a) v) :n (inc (:n a))})))
                  acc
                  (omap-keys years))))
             {}
             (omap-keys sy))]
    (->> (keys acc)
         ;; sorted(acc.items(), key=lambda kv: kv[0]) — Python tuple sort
         (sort (fn [[d1 m1 u1 s1 y1] [d2 m2 u2 s2 y2]]
                 (let [c (compare [d1 m1 u1 s1] [d2 m2 u2 s2])]
                   (if (zero? c) (compare y1 y2) c))))
         (mapv (fn [[domain metric unit scale y :as k]]
                 (let [a (get acc k)]
                   (agg-row domain metric unit scale y (:sum a) (:n a))))))))

;; ── value formatting (fmt_val / pct) ─────────────────────────────────────────

(def unit-label
  {":usd" "$" ":exabytes" "EB" ":units" "units" ":flops" "FLOP" ":watts" "W" ":ratio" "×"})
(def scale-suffix
  {":ones" "" ":thousands" "K" ":millions" "M" ":billions" "B"
   ":petaflops" " PFLOP/s" ":exaflops" " EFLOP/s" ":gigawatts" " GW"})

(defn fmt-val
  "Human-format a value given its series unit+scale (1:1 with fmt_val)."
  [^double v series]
  (let [unit (get series ":compute.series/unit" "")
        scale (get series ":compute.series/scale" ":ones")
        sym (get unit-label unit "")
        suf (get scale-suffix scale "")]
    (cond
      (and (= unit ":flops") (= scale ":ones")) (str (fmt-1e v) " FLOP")
      (= unit ":usd")       (str "$" (fmt-comma v) suf)
      (= unit ":exabytes")  (str (fmt-comma v) " EB")
      (= unit ":units")     (str (fmt-comma v) suf " units")
      (= unit ":flops")     (str (fmt-comma v) suf)
      (= unit ":watts")     (str (fmt-comma v) suf)
      :else (str/trim (str (fmt-comma v) " " sym suf)))))

;; ── report rendering ─────────────────────────────────────────────────────────

(defn- lstrip-colon [s] (if (and (string? s) (str/starts-with? s ":")) (subs s 1) s))

(defn report
  "Render the aggregate-first markdown report (1:1 with report())."
  [series obs sources sy growth aggs]
  (let [L (transient [])
        A (fn [s] (conj! L s))
        sids (sort (omap-keys sy))
        years-all (sort (distinct (mapcat #(omap-keys (get sy %)) (omap-keys sy))))
        pubs (->> obs
                  (filter #(contains? sources (get % ":compute.obs/source")))
                  (map #(get-in sources [(get % ":compute.obs/source") ":compute.source/publisher"]))
                  distinct sort)]
    (A "# kasa 嵩 — worldwide computing-capacity growth report")
    (A "")
    (A "> Aggregate-first, **non-adjudicating** (G2), **planning-lens** (G9 — sizes the compute")
    (A "> commons, never a country/company ranking or a targeting list), **no forecast** (G4 — past/")
    (A "> present actuals + measured growth only; future projection is mitooshi 見通し). Every figure")
    (A "> is either a quantity a public source measured/estimated, or a transparent rate-of-change of")
    (A "> two such figures (`:synthesized`, G5). Seed values are `:representative` headline figures —")
    (A "> see the honesty note.")
    (A "")
    (A "## Coverage")
    (A "")
    (A (str "- **Series**: " (count sids) " · **Observations**: " (count obs) " · **Years**: "
            (first years-all) "–" (last years-all) " · **Growth points derived**: " (count growth)))
    (A (str "- **Public sources**: " (str/join ", " (map lstrip-colon pubs)) " "
            "(public headline / open-dataset only — NO paid report / terminal, G1)"))
    (A (str "- **Sourcing**: headline figures `:representative` (rounded); frontier-training + "
            "datacenter-power rows `:estimated` (analyst/Epoch-AI estimate, with method). "
            "Authoritative dataset parse = G7 operator-gated (`ingest.py`)."))
    (A "")
    (A "## World compute snapshot — latest observed year per series")
    (A "")
    (A "| Domain | Series | Latest yr | Value | YoY | CAGR (span) |")
    (A "|---|---|--:|--:|--:|--:|")
    (let [gidx (reduce (fn [m g]
                         (let [k [(get g ":compute.growth/series") (get g ":compute.growth/kind")]]
                           (update m k (fnil conj []) g)))
                       {} growth)]
      (doseq [sid sids]
        (let [s (get series sid {})
              years (get sy sid)
              last- (apply max (omap-keys years))
              dom (lstrip-colon (get s ":compute.series/domain" ""))
              label (-> (get s ":compute.series/label" sid) (str/split #" / ") first)
              yoy (some (fn [g] (when (= (get g ":compute.growth/to-year") last-)
                                  (get g ":compute.growth/value")))
                        (get gidx [sid ":yoy"] []))
              cagr (first (map #(get % ":compute.growth/value") (get gidx [sid ":cagr"] [])))]
          (A (str "| " dom " | " label " | " last- " | " (fmt-val (get years last-) s) " | "
                  (if (some? yoy) (pct yoy) "—") " | " (if (some? cagr) (pct cagr) "—") " |"))))
      (A "")
      (A "_YoY / CAGR are `:synthesized` rates of change of disclosed/estimated figures. Values across")
      (A "series are in DIFFERENT units (revenue vs exabytes vs FLOP) and are NOT directly comparable._")
      (A "")
      (A "## Annual increase (年間増加量) — per-series year-over-year")
      (A "")
      (A (str "| Series | " (str/join " | " (map (fn [[a b]] (str a "→" b))
                                                  (map vector years-all (rest years-all)))) " |"))
      (A (str "|---|" (str/join (repeat (dec (count years-all)) "--:|"))))
      (doseq [sid sids]
        (let [label (-> (get-in series [sid ":compute.series/label"] sid) (str/split #" / ") first)
              ymap (reduce (fn [m g]
                             (assoc m [(get g ":compute.growth/from-year") (get g ":compute.growth/to-year")]
                                    (get g ":compute.growth/value")))
                           {} (get gidx [sid ":yoy"] []))
              cells (map (fn [[a b]]
                           (let [v (get ymap [a b])]
                             (if (some? v) (pct v) "—")))
                         (map vector years-all (rest years-all)))]
          (A (str "| " label " | " (str/join " | " cells) " |")))))
    (A "")
    (A "## Domain aggregates (coverage-honest — read against `n`, never a market total; G3/G12)")
    (A "")
    (A "| Domain | Metric | Year | Σ | n series |")
    (A "|---|---|--:|--:|--:|")
    (let [latest (last years-all)]
      (doseq [a aggs]
        (when (= (get a ":compute.agg/year") latest)
          (let [ser {":compute.series/unit" (get a ":compute.agg/unit")
                     ":compute.series/scale" (get a ":compute.agg/scale")}]
            (A (str "| " (lstrip-colon (get a ":compute.agg/key")) " | "
                    (lstrip-colon (get a ":compute.agg/metric")) " | "
                    (get a ":compute.agg/year") " | "
                    (fmt-val (get a ":compute.agg/sum") ser) " | "
                    (get a ":compute.agg/n") " |"))))))
    (A "")
    (A "> Σ is bounded by the series ingested in that (domain, unit) — NOT a market total. Memory")
    (A "> (:dram / :nand) is a SUBSET of :semiconductor and lives in a distinct domain key, so it is")
    (A "> structurally NEVER summed into the semiconductor total (no double-count). Absence ≠ zero.")
    (A "")
    (A "## Honesty (R0)")
    (A "")
    (A "- Bounded `:representative` seed of public headline figures (WSTS/SIA · TrendForce · IDC · JPR")
    (A "  · TOP500) + `:estimated` rows (Epoch AI frontier-training · datacenter power). \"Ingest the")
    (A "  world's compute-capacity stats\" is the **R1** goal — full open-dataset parse is **G7**")
    (A "  Council + operator gated (`ingest.py`).")
    (A "- Figures are rounded headline numbers, NOT the exact dataset row; estimates carry a method.")
    (A "- kasa does NOT forecast (future projection is mitooshi 見通し), does not rank countries, does")
    (A "  not build an export-control / targeting list, and gives no investment advice. It records how")
    (A "  much compute the world ADDED and the arithmetic of that growth.")
    (str (str/join "\n" (persistent! L)) "\n")))

;; ── EDN dump ──────────────────────────────────────────────────────────────────

(defn- edn-v
  "Python _v: keyword strings pass through; bool → true/false; str → quoted; number → repr."
  [v]
  (cond
    (string? v) (if (str/starts-with? v ":") v (str "\"" (str/replace v "\"" "\\\"") "\""))
    (boolean? v) (if v "true" "false")
    (integer? v) (str v)
    (float? v) (py-repr-float (double v))
    :else (str v)))

(defn edn-dump
  "Render derived growth + aggregates as a generated kotoba EDN vector (1:1 with edn_dump)."
  [growth aggs]
  (let [L (transient [";; kasa 嵩 — derived growth + aggregates (GENERATED by analyze.py)"
                      ";; ADR-2606072000 · all :synthesized (G5) — NEVER re-ingested as observations."
                      "["])
        row-str (fn [row]
                  (str " {" (str/join " " (map (fn [k] (str k " " (edn-v (get row k))))
                                               (or (::order (meta row)) (keys row)))) "}"))]
    (doseq [g growth] (conj! L (row-str g)))
    (doseq [a aggs] (conj! L (row-str a)))
    (conj! L "]")
    (str (str/join "\n" (persistent! L)) "\n")))

;; ── CLI entry ─────────────────────────────────────────────────────────────────

#?(:clj
   (defn -main [& argv]
     (let [argv (vec argv)
           here (-> *file* clojure.java.io/file .getParentFile .getParentFile)
           src (if (and (seq argv) (not (str/starts-with? (first argv) "--")))
                 (clojure.java.io/file (first argv))
                 (clojure.java.io/file here "data" "seed-compute-capacity.kotoba.edn"))
           {:keys [series obs sources]} (load-file* src)
           sy (by-series-year obs)
           growth (derive-growth sy)
           aggs (aggregates series sy)
           outdir (clojure.java.io/file here "out")]
       (.mkdirs outdir)
       (spit (clojure.java.io/file outdir "intel-report.md")
             (report series obs sources sy growth aggs))
       (spit (clojure.java.io/file outdir "compute-growth.kotoba.edn")
             (edn-dump growth aggs))
       (println (str "kasa analyze: " (count series) " series · " (count obs) " obs · "
                     (count sources) " sources · " (count growth) " growth · "
                     (count aggs) " aggregates"))
       (println "  → out/intel-report.md")
       (println "  → out/compute-growth.kotoba.edn")
       0)))
