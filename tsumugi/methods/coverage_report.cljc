(ns tsumugi.methods.coverage-report
  "tsumugi 紡ぎ — influence-history COVERAGE report (ADR-2606061500).
  Clojure port of methods/coverage_report.py (1:1).

  Given the diachronic influence seed, computes honest coverage — by denominator, ERA,
  civilizational TRADITION-stream, and graph connectedness — and emits a gap map. Headline:
  coverage of *all* past humanity is ~0 by design (a bounded :representative sample); this
  measures the useful backbone + names what is thin/missing.

  Depends only on the PURE loader of analyze_influence (`load` / read-edn — numpy is NOT used by
  load or by this module; the numpy spectral-layout part of analyze_influence stays unported).
  Reuses the shared analyze-scale EDN reader. stdlib only; file I/O at the #?(:clj) edge."
  (:require [clojure.string :as str]
            [tsumugi.methods.analyze-scale :as asc]
            #?(:clj [clojure.java.io :as io])))

(def ERA-SPINE [":bronze-age" ":iron-age" ":axial" ":2nd-temple" ":late-antiquity"
                ":early-medieval" ":medieval" ":reformation" ":enlightenment"
                ":modern" ":contemporary"])
(def MAJOR-STREAMS [":abrahamic" ":jewish" ":christian" ":reformed" ":islamic"
                    ":hellenic" ":buddhist" ":mahayana" ":zen" ":hindu" ":vedic"
                    ":daoist" ":confucian" ":jain" ":zoroastrian" ":shinto"
                    ":secular-philosophy"])
(def DENOMINATORS [["MIT Pantheon notable people" 88937]
                   ["Wikidata humans (~)" 10500000]
                   ["All humans ever (~Population Reference Bureau)" 117000000000]])
(def ^:private THIN 2)

(defn load-graph
  "Read the influence seed → [nodes flows] (mirror of analyze_influence.load; pure)."
  [path]
  (reduce (fn [[nodes flows] m]
            (cond
              (not (map? m)) [nodes flows]
              (contains? m ":organism/id") [(assoc nodes (get m ":organism/id") m) flows]
              (contains? m ":flow/id") [nodes (conj flows m)]
              :else [nodes flows]))
          [{} []] (asc/read-edn (slurp (str path)))))

(defn components
  "Connected components over the influence flows (union-find), node-id lists sorted by size desc."
  [nodes flows]
  (let [ids (vec (keys nodes))
        idx (zipmap ids (range))
        n (count ids)
        par (long-array n)]
    (dotimes [i n] (aset par i (long i)))
    (letfn [(find [x] (loop [x x]
                        (let [p (aget par x)]
                          (if (= p x) x (do (aset par x (aget par p)) (recur (aget par x)))))))]
      (doseq [f flows]
        (let [fr (get idx (get f ":flow/from")) to (get idx (get f ":flow/to"))]
          (when (and fr to) (aset par (find (long fr)) (long (find (long to)))))))
      (->> ids
           (group-by (fn [k] (find (long (get idx k)))))
           vals
           (sort-by count >)
           vec))))

(defn compute [seed]
  (let [[nodes flows0] (load-graph seed)
        flows (filterv #(and (contains? nodes (get % ":flow/from"))
                             (contains? nodes (get % ":flow/to"))) flows0)
        nvs (vals nodes)
        sub (frequencies (map #(get % ":hist/subkind") nvs))
        era (frequencies (map #(get % ":hist/era") nvs))
        stream (frequencies (mapcat #(get % ":hist/tradition" []) nvs))
        deg (frequencies (mapcat (fn [f] [(get f ":flow/from") (get f ":flow/to")]) flows))
        isolated (filterv #(zero? (get deg % 0)) (keys nodes))
        comps (components nodes flows)
        n (count nodes)
        ec (fn [m k] (get m k 0))]
    {"nodes" n "edges" (count flows) "figures" (get sub ":figure" 0)
     "subkind" sub "era" era "stream" stream
     "isolated" isolated "components" comps
     "density" (if (> n 1) (/ (double (count flows)) (* n (dec n))) 0.0)
     "era_covered" (filterv #(> (ec era %) 0) ERA-SPINE)
     "era_missing" (filterv #(= (ec era %) 0) ERA-SPINE)
     "era_thin" (filterv #(< 0 (ec era %) THIN) ERA-SPINE)
     "stream_covered" (filterv #(> (ec stream %) 0) MAJOR-STREAMS)
     "stream_missing" (filterv #(= (ec stream %) 0) MAJOR-STREAMS)
     "stream_thin" (filterv #(< 0 (ec stream %) THIN) MAJOR-STREAMS)}))

(defn render [c]
  (let [bare (fn [s] (subs (str s) 1))
        lines (concat
               ["# tsumugi 紡ぎ — Influence-History Coverage Report" ""
                "> Honest coverage of the influence backbone. **Coverage of *all* past humanity is ~0"
                "> by design** (a bounded `:representative` sample); this measures the useful coverage"
                "> (major influence streams of recorded thought) and names what is thin/missing." ""
                (str "- nodes **" (get c "nodes") "** · edges **" (get c "edges") "** · figures **"
                     (get c "figures") "** · graph density **"
                     (#?(:clj format :cljs (fn [_ x] (.toFixed x 1))) "%.1f" (* 100.0 (get c "density")))
                     "%** of directed pairs")
                (str "- connected components **" (count (get c "components")) "** (largest **"
                     (if (seq (get c "components")) (count (first (get c "components"))) 0)
                     "**) · isolated nodes **" (count (get c "isolated")) "**")]
               ["" (str "## Era coverage — " (count (get c "era_covered")) "/" (count ERA-SPINE) " buckets populated") ""]
               (map (fn [e] (let [cnt (get (get c "era") e 0)]
                              (str "- `" (bare e) "`: " cnt "  ("
                                   (cond (= cnt 0) "—" (< cnt THIN) "⚠ thin" :else "ok") ")"))) ERA-SPINE)
               ["" (str "## Civilizational-stream coverage — " (count (get c "stream_covered")) "/" (count MAJOR-STREAMS) " streams have ≥1 node") ""]
               (map (fn [s] (let [cnt (get (get c "stream") s 0)]
                              (str "- `" (bare s) "`: " cnt "  ("
                                   (cond (= cnt 0) "—" (< cnt THIN) "⚠ thin" :else "ok") ")"))) MAJOR-STREAMS)
               ["" "## Honest headline"
                (str "- Backbone: **" (count (get c "stream_covered")) "/" (count MAJOR-STREAMS) " streams** · **"
                     (count (get c "era_covered")) "/" (count ERA-SPINE) " eras** · **"
                     (if (and (= 1 (count (get c "components"))) (empty? (get c "isolated"))) "fully connected" "fragmented") "**.")
                "- All-humanity coverage remains effectively 0 (by design)."])]
    (str (str/join "\n" lines) "\n")))

#?(:clj
   (defn -main
     "CLI entry: mirrors coverage_report.main — [seed.edn] [--out OUTDIR]. ADR-2606261200."
     [& argv]
     (let [argv (vec argv)
           args (vec (remove #(str/starts-with? % "--") argv))
           here (let [f  (when (and *file* (not (str/blank? *file*))) (io/file *file*))
                      pp (some-> f .getAbsoluteFile .getParentFile .getParentFile)]
                  (if (and pp (.isDirectory (io/file pp "data"))) pp (io/file "20-actors" "tsumugi")))
           seed (if (seq args) (io/file (first args)) (io/file here "data" "seed-influence-history.kotoba.edn"))
           out  (if (some #{"--out"} argv) (io/file (nth argv (inc (.indexOf argv "--out")))) (io/file here "out"))
           c    (compute (str seed))]
       (.mkdirs out)
       (spit (io/file out "coverage-report.md") (render c))
       (println (str "[tsumugi/coverage] nodes " (get c "nodes") " · edges " (get c "edges")
                     " → " (.getPath (io/file out "coverage-report.md")))))))
