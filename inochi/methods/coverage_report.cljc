(ns inochi.methods.coverage-report
  "inochi 命 — biosphere COVERAGE report (ADR-2606073000). 1:1 Clojure port of
  `methods/coverage_report.py`.

  Honest coverage measurement of the living-world graph: how much of the target space the
  seed covers — by IUCN denominator, by realm, by biome, by kingdom — and a gap map naming
  what is thin/missing. NOT a completeness claim: coverage of *all* species is ~0 by design.

  Pure fns; reuses inochi.methods.analyze for the loader. Portable .cljc."
  (:require [clojure.string :as str]
            [inochi.methods.analyze :as analyze]))

;; honest external denominators for the SPECIES count
(def denominators
  [["IUCN Red List assessed species (~)" 169000]
   ["IUCN threatened species (~)" 47000]
   ["Described species (~)" 2160000]
   ["Estimated species on Earth (~)" 8700000]])

(def realms [":terrestrial" ":marine" ":freshwater"])
(def biomes [":tropical-forest" ":boreal-forest" ":coral-reef" ":mangrove"
             ":tundra" ":grassland" ":wetland" ":pelagic"])
(def kingdoms [":animalia" ":plantae" ":fungi" ":chromista" ":bacteria"])
(def pressure-kinds [":habitat-loss" ":overharvest" ":climate-forcing"
                     ":pollution" ":invasive" ":wildlife-trade"])
(def THIN 2) ;; a bucket with < THIN members is flagged thin

(defn- lstrip-colon [s] (if (and (string? s) (str/starts-with? s ":")) (subs s 1) s))

(defn- counter
  "Counter(seq) → map value->count, mirroring collections.Counter (nil keys allowed)."
  [coll]
  (reduce (fn [m v] (update m v (fnil inc 0))) {} coll))

(defn- comma
  "Python f'{n:,}' — group integer digits with commas (no fraction here)."
  [n]
  (let [s (str (long n))
        neg (str/starts-with? s "-")
        digits (if neg (subs s 1) s)
        rev (reverse (vec digits))
        grouped (->> rev
                     (partition-all 3)
                     (map #(apply str (reverse %)))
                     reverse
                     (str/join ","))]
    (str (when neg "-") grouped)))

(defn- sci2
  "Python f'{x:.2e}' — scientific notation, 2 fraction digits, lowercase e, signed 2+ exp."
  [x]
  (let [s (format "%.2e" (double x))]
    ;; Java %e gives e.g. \"8.62e-05\" already matching Python's two-digit exponent;
    ;; ensure exponent has at least 2 digits with sign (Java already does on macOS JDK).
    s))

(defn report
  "Render the biosphere coverage-report markdown (1:1 with coverage_report.report)."
  [nodes edges]
  (let [vals* (vals nodes)
        species (filter #(= ":species" (get % ":organism/kind")) vals*)
        ecos (filter #(contains? #{":ecosystem" ":biome"} (get % ":organism/kind")) vals*)
        press (filter #(= ":pressure" (get % ":organism/kind")) vals*)
        realm-c (counter (map #(get % ":eco/realm") ecos))
        biome-c (counter (map #(get % ":eco/biome") ecos))
        king-c (counter (map #(get % ":taxon/kingdom") species))
        iucn-c (counter (map #(get % ":taxon/iucn") species))
        pk-c (counter (map #(get % ":pressure/kind") press))
        n-sp (count species)
        L (transient [])]
    (conj! L "# inochi 命 — biosphere coverage report\n")
    (conj! L (str "> Honest denominator: coverage of all life is ~0 by design (bounded seed). "
                  "This names the at-risk backbone covered and the next-wave gaps.\n"))
    (conj! L (str "**Seed**: " n-sp " species · " (count ecos) " ecosystems/biomes · "
                  (count press) " pressures · " (count edges) " 縁\n"))

    (conj! L "\n## Species coverage vs denominators\n")
    (conj! L "| denominator | count | seed | fraction |")
    (conj! L "|---|---:|---:|---:|")
    (doseq [[name denom] denominators]
      (conj! L (str "| " name " | " (comma denom) " | " n-sp " | "
                    (sci2 (/ (double n-sp) denom)) " |")))

    (conj! L "\n## IUCN category spread (DISCLOSED facts, not verdicts)\n")
    (conj! L "| category | count |")
    (conj! L "|:--:|---:|")
    (doseq [cat [":EX" ":EW" ":CR" ":EN" ":VU" ":NT" ":LC" ":DD"]]
      (conj! L (str "| " (lstrip-colon cat) " | " (get iucn-c cat 0) " |")))

    (letfn [(bucket [title ks cnt]
              (conj! L (str "\n## " title "\n"))
              (conj! L "| bucket | count | status |")
              (conj! L "|---|---:|:--|")
              (doseq [k ks]
                (let [c (get cnt k 0)
                      status (cond (= c 0) "— **MISSING**"
                                   (< c THIN) "⚠ thin"
                                   :else "ok")]
                  (conj! L (str "| " (lstrip-colon k) " | " c " | " status " |")))))]
      (bucket "Realm coverage" realms realm-c)
      (bucket "Biome coverage" biomes biome-c)
      (bucket "Kingdom coverage" kingdoms king-c)
      (bucket "Pressure-kind coverage" pressure-kinds pk-c))

    (let [missing (concat
                   (for [b biomes :when (= 0 (get biome-c b 0))] (lstrip-colon b))
                   (for [k kingdoms :when (= 0 (get king-c k 0))] (lstrip-colon k))
                   (for [p pressure-kinds :when (= 0 (get pk-c p 0))] (lstrip-colon p)))]
      (conj! L "\n## Gap map — next-wave targets\n")
      (if (seq missing)
        (conj! L (str "Missing buckets: " (str/join ", " missing) "."))
        (conj! L "No fully-missing buckets in the tracked spines (thin buckets still listed above).")))
    (conj! L "\n---\n_inochi 命 · ADR-2606073000 · coverage honesty (G5)._\n")
    (str/join "\n" (persistent! L))))

#?(:clj
   (defn -main
     "CLI entry: render coverage-report.md from a seed EDN graph."
     [& argv]
     (let [argv (vec argv)
           here (-> *file* clojure.java.io/file .getParentFile .getParentFile)
           seed (if (and (seq argv) (not (str/starts-with? (first argv) "--")))
                  (clojure.java.io/file (first argv))
                  (clojure.java.io/file here "data" "seed-biosphere-graph.kotoba.edn"))
           outdir (if (some #{"--out"} argv)
                    (clojure.java.io/file (nth argv (inc (.indexOf argv "--out"))))
                    (clojure.java.io/file here "out"))
           {:keys [nodes edges]} (analyze/load-file* seed)]
       (.mkdirs outdir)
       (spit (clojure.java.io/file outdir "coverage-report.md") (report nodes edges))
       (println (str "inochi coverage → " (clojure.java.io/file outdir "coverage-report.md")))
       0)))
