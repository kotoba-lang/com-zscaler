(ns maps.methods.analyze
  "maps — static-feature substrate coverage analyzer (kotoba-native, R0).
  1:1 Clojure port of `methods/analyze.py` (ADR-2606064500).

  Reads a kotoba-EDN static-feature graph (:feature/* placed features,
  :feature.rel/* topology edges, :geo.alias/* multi-scheme region identity) and
  emits an AGGREGATE-FIRST coverage report — how many features the kotoba
  substrate holds, per label, and what slice of the Earth they actually cover.

  CONSTITUTIONAL (ADR-2606064500 gates):
    G3 — sourcing honesty: every feature must carry :feature/sourcing.
    G9 — a feature is a PLACED THING, never a person.

  House style: Python ':…' keyword strings stay strings; pure fns; file I/O
  only at edges via #?(:clj …). Portable .cljc."
  (:require [clojure.string :as str]))

;; ── minimal EDN reader (subset: [] {} :kw \"str\" num bool nil) ─────────────────
(def ^:private tok-re
  #"[\s,]+|;[^\n]*|(\[|\]|\{|\}|\"(?:\\.|[^\"\\])*\"|[^\s,\[\]{}]+)")

(defn tokens [s]
  (let [m (re-matcher tok-re s)]
    ((fn step []
       (lazy-seq
        (when (.find m)
          (let [t (.group m 1)]
            (if (nil? t) (step) (cons t (step))))))))))

(defn atom-of [t]
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
    (let [l (try (Long/parseLong t) (catch #?(:clj Exception :cljs :default) _ ::nan))]
      (if (not= l ::nan) l
        (let [d (try (Double/parseDouble t) (catch #?(:clj Exception :cljs :default) _ ::nan))]
          (if (not= d ::nan) d t))))))

(def ^:private end-marker ::end)

(defn- parse-step [toks i]
  (let [t (nth toks i) i (inc i)]
    (cond
      (= t "[")
      (loop [i i out []]
        (let [[x i] (parse-step toks i)]
          (if (= x end-marker) [out i] (recur i (conj out x)))))
      (= t "{")
      (loop [i i out {}]
        (let [[k i] (parse-step toks i)]
          (if (= k end-marker) [out i]
            (let [[v i] (parse-step toks i)]
              (recur i (assoc out k v))))))
      (or (= t "]") (= t "}")) [end-marker i]
      :else [(atom-of t) i])))

(defn read-edn [text]
  (let [toks (vec (tokens text))]
    (first (parse-step toks 0))))

#?(:clj
   (defn load-edn [path]
     (read-edn (slurp (str path)))))

;; ── classify the flat datom vector into entity buckets ────────────────────────
(defn classify [rows]
  (reduce
   (fn [[features rels aliases] r]
     (cond
       (not (map? r)) [features rels aliases]
       (contains? r ":feature/id")       [(assoc features (get r ":feature/id") r) rels aliases]
       (contains? r ":feature.rel/id")   [features (conj rels r) aliases]
       (contains? r ":geo.alias/id")     [features rels (assoc aliases (get r ":geo.alias/id") r)]
       :else [features rels aliases]))
   [{} [] {}]
   rows))

;; ── H3 res-6 cell: ~1° degree-grid stand-in (stdlib, conservative, never inflates) ──
(defn- cell-r6 [lat lon]
  (str "deg/" (Math/floor (double lat)) "/" (Math/floor (double lon))))

;; exact H3 count at res r: 2 + 120 × 7^r
(defn h3-cells-at-res [r]
  (+ 2 (* 120 (long (Math/pow 7 r)))))

(defn analyze [features rels aliases]
  (let [label-count (volatile! {})
        cells-r6    (volatile! #{})
        lats        (volatile! [])
        lons        (volatile! [])
        hot         (volatile! {})
        sourcing    (volatile! {})]
    (doseq [[_ f] features]
      (let [lab (get f ":feature/label" ":unknown")
            lat (get f ":feature/lat")
            lon (get f ":feature/lon")
            src (get f ":feature/sourcing" ":unknown")]
        (vswap! label-count update lab (fnil inc 0))
        (vswap! sourcing update src (fnil inc 0))
        (when (and (number? lat) (number? lon))
          (vswap! lats conj lat)
          (vswap! lons conj lon)
          (vswap! cells-r6 conj (cell-r6 lat lon))
          (let [k [(double (/ (Math/round (* (double lat) 100)) 100))
                   (double (/ (Math/round (* (double lon) 100)) 100))]]
            (vswap! hot update k (fnil inc 0))))))
    (let [ls @lats lo @lons
          bbox (when (seq ls)
                 {:south (apply min ls) :north (apply max ls)
                  :west  (apply min lo) :east  (apply max lo)})
          densest (when (seq @hot)
                    (apply max-key val @hot))]
      {:n-features    (count features)
       :label-count   @label-count
       :n-rels        (count rels)
       :n-aliases     (count aliases)
       :cells-r6      @cells-r6
       :cell-mode     "deg"
       :bbox          bbox
       :densest       densest
       :sourcing      @sourcing
       :n-buildings   (get @label-count ":building" 0)})))

;; ── report rendering ──────────────────────────────────────────────────────────
(defn render-report [_features a]
  (let [den (h3-cells-at-res 6)
        frac (/ (count (:cells-r6 a)) (double den))
        L (transient [])]
    (conj! L "# maps — static-feature substrate coverage report (kotoba-native)")
    (conj! L "")
    (conj! L (str "> ADR-2606064500 · **aggregate-first** · the kotoba Datom log successor to "
                  "the legacy RisingWave `vertex_spatial`. All sourcing `:representative` — "
                  "a bounded anchor seed, NOT planet-scale coverage (G3)."))
    (conj! L "")
    (conj! L (str "- features: **" (:n-features a) "**  ·  topology edges: **" (:n-rels a)
                  "**  ·  geo-aliases: **" (:n-aliases a)
                  "**  ·  buildings (3D-extrudable): **" (:n-buildings a) "**"))
    (conj! L (str "- Earth coverage @ res-6: **" (count (:cells-r6 a))
                  "** distinct cells / " (format "%,d" den)
                  " (~1° degree-grid stand-in) ≈ **" (format "%.2e" frac) "** of the planet"))
    (when-let [b (:bbox a)]
      (conj! L (str "- geographic footprint: lat [" (format "%.3f" (:south b)) ", "
                    (format "%.3f" (:north b)) "] · lon [" (format "%.3f" (:west b))
                    ", " (format "%.3f" (:east b)) "]")))
    (when-let [[k n] (:densest a)]
      (conj! L (str "- densest hot spot: **" n "** features at ≈" k " — the localized anchor")))
    (conj! L "")
    (conj! L "## Features by label (what the substrate actually holds)")
    (conj! L "")
    (conj! L "| label | count |")
    (conj! L "|---|---:|")
    (doseq [[lab cnt] (sort-by (comp - val) (:label-count a))]
      (conj! L (str "| `" lab "` | " cnt " |")))
    (conj! L "")
    (conj! L "## Sourcing (G3 honesty)")
    (conj! L "")
    (conj! L "| sourcing | count |")
    (conj! L "|---|---:|")
    (doseq [[s cnt] (sort-by (comp - val) (:sourcing a))]
      (conj! L (str "| `" s "` | " cnt " |")))
    (conj! L "")
    (conj! L "---")
    (conj! L (str "*Generated by `maps/methods/analyze.cljc` (stdlib; ~1° degree-grid, not real H3). "
                  "HONEST R0: bounded `:representative` anchor seed (Tokyo Station).*"))
    (str/join "\n" (persistent! L))))

(defn render-datoms [a]
  (let [L (transient [])]
    (conj! L ";; maps — DERIVED coverage datoms (ADR-2606064500). :derived — NOT fact.")
    (conj! L "[")
    (conj! L (str " {:coverage/feature-count " (:n-features a) " :coverage/derived true}"))
    (conj! L (str " {:coverage/cell-count-r6 " (count (:cells-r6 a))
                  " :coverage/cell-mode \"" (:cell-mode a) "\" :coverage/derived true}"))
    (when-let [b (:bbox a)]
      (conj! L (str " {:coverage/bbox-south " (:south b) " :coverage/bbox-north " (:north b)
                    " :coverage/bbox-west " (:west b) " :coverage/bbox-east " (:east b)
                    " :coverage/derived true}")))
    (when-let [[k n] (:densest a)]
      (conj! L (str " {:coverage/anchor-density " n " :coverage/anchor-lat " (first k)
                    " :coverage/anchor-lon " (second k) " :coverage/derived true}")))
    (conj! L "]")
    (str/join "\n" (persistent! L))))

#?(:clj
   (defn -main [& argv]
     (let [argv (vec argv)
           here (-> *file* clojure.java.io/file .getParentFile .getParentFile)
           seed (if (and (seq argv) (not (str/starts-with? (first argv) "--")))
                  (clojure.java.io/file (first argv))
                  (clojure.java.io/file here "data" "seed-spatial-graph.kotoba.edn"))
           outdir (if (some #{"--out"} argv)
                    (clojure.java.io/file (nth argv (inc (.indexOf argv "--out"))))
                    (clojure.java.io/file here "out"))
           rows (load-edn seed)
           [features rels aliases] (classify rows)
           a (analyze features rels aliases)]
       (.mkdirs outdir)
       (spit (clojure.java.io/file outdir "coverage-report.md")
             (render-report features a))
       (spit (clojure.java.io/file outdir "coverage.kotoba.edn")
             (render-datoms a))
       (println (str "maps: " (:n-features a) " features, " (:n-rels a) " edges, "
                     (:n-aliases a) " aliases; " (count (:cells-r6 a)) " res-6 cells"))
       0)))
