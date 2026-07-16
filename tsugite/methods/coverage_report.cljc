(ns tsugite.methods.coverage-report
  "tsugite 継ぎ手 — peoples-continuity COVERAGE report (ADR-2606073800). 1:1 Clojure port of
  `methods/coverage_report.py`.

  Honest coverage of the peoples graph: by people kind, by language vitality, by pressure kind,
  by haven kind — with a gap map naming thin/missing buckets. Coverage of all peoples/languages
  is ~0 by design (a bounded :representative, AGGREGATE seed); this makes the covered backbone
  measurable and names the next wave.

  Pure fns; reuses tsugite.methods.analyze for the loader. Portable .cljc."
  (:require [clojure.string :as str]
            [tsugite.methods.analyze :as analyze]))

;; honest external denominators
(def denominators
  [["Forcibly displaced worldwide (UNHCR, ~)" 120000000]
   ["Stateless people (~)" 4400000]
   ["Living languages (Ethnologue, ~)" 7160]
   ["Endangered languages (~)" 3000]])

(def people-kinds [":refugee-population" ":stateless" ":displaced" ":indigenous"
                   ":diaspora" ":language-community"])
(def vitality [":vulnerable" ":definitely-endangered" ":severely-endangered"
               ":critically-endangered" ":extinct" ":safe"])
(def pressure-kinds [":armed-conflict" ":persecution" ":statelessness" ":disaster-climate"
                     ":economic" ":assimilation-policy" ":language-shift" ":education-exclusion"])
(def haven-kinds [":asylum-system" ":resettlement" ":statelessness-reduction"
                  ":mother-tongue-education" ":revitalization-program" ":cultural-archive"])
(def THIN 2)

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

(defn report
  "Render the peoples-continuity coverage-report markdown (1:1 with coverage_report.report)."
  [nodes edges]
  (let [vals* (vals nodes)
        peoples (filter #(= ":people" (get % ":organism/kind")) vals*)
        langs (filter #(= ":language" (get % ":organism/kind")) vals*)
        press (filter #(= ":pressure" (get % ":organism/kind")) vals*)
        havens (filter #(= ":haven" (get % ":organism/kind")) vals*)
        pk-c (counter (map #(get % ":people/kind") peoples))
        vit-c (counter (map #(get % ":lang/vitality") langs))
        pr-c (counter (map #(get % ":pressure/kind") press))
        hv-c (counter (map #(get % ":haven/kind") havens))
        L (transient [])]
    (conj! L "# tsugite 継ぎ手 — peoples-continuity coverage report\n")
    (conj! L (str "> Honest denominator: coverage of all peoples/languages is ~0 by design (bounded "
                  "AGGREGATE seed; G1 = no individuals). This names the covered backbone and the "
                  "next-wave gaps.\n"))
    (conj! L (str "**Seed**: " (count peoples) " peoples · " (count langs) " languages · "
                  (count press) " pressures · " (count havens) " havens · " (count edges) " 縁\n"))

    (conj! L "\n## Scale context (modelled as collectives, not individuals — by design, G1)\n")
    (conj! L "| denominator | count |")
    (conj! L "|---|---:|")
    (doseq [[name denom] denominators]
      (conj! L (str "| " name " | " (comma denom) " |")))

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
      (bucket "People-kind coverage" people-kinds pk-c)
      (bucket "Language-vitality coverage (DISCLOSED)" vitality vit-c)
      (bucket "Pressure-kind coverage" pressure-kinds pr-c)
      (bucket "Haven-kind coverage" haven-kinds hv-c))

    (let [missing (concat
                   (for [p people-kinds :when (= 0 (get pk-c p 0))] (lstrip-colon p))
                   (for [v vitality :when (= 0 (get vit-c v 0))] (lstrip-colon v))
                   (for [p pressure-kinds :when (= 0 (get pr-c p 0))] (lstrip-colon p))
                   (for [h haven-kinds :when (= 0 (get hv-c h 0))] (lstrip-colon h)))]
      (conj! L "\n## Gap map — next-wave targets\n")
      (if (seq missing)
        (conj! L (str "Missing buckets: " (str/join ", " missing) "."))
        (conj! L "No fully-missing buckets in the tracked spines (thin buckets still listed above).")))
    (conj! L "\n---\n_tsugite 継ぎ手 · ADR-2606073800 · coverage honesty (G5)._\n")
    (str/join "\n" (persistent! L))))

#?(:clj
   (defn -main
     "CLI entry: render coverage-report.md from a seed EDN graph."
     [& argv]
     (let [argv (vec argv)
           here (-> *file* clojure.java.io/file .getParentFile .getParentFile)
           seed (if (and (seq argv) (not (str/starts-with? (first argv) "--")))
                  (clojure.java.io/file (first argv))
                  (clojure.java.io/file here "data" "seed-peoples-graph.kotoba.edn"))
           outdir (if (some #{"--out"} argv)
                    (clojure.java.io/file (nth argv (inc (.indexOf argv "--out"))))
                    (clojure.java.io/file here "out"))
           {:keys [nodes edges]} (analyze/load-file* seed)]
       (.mkdirs outdir)
       (spit (clojure.java.io/file outdir "coverage-report.md") (report nodes edges))
       (println (str "tsugite coverage → " (clojure.java.io/file outdir "coverage-report.md")))
       0)))
