(ns jinushi.methods.coverage
  "jinushi 地主 — world land-ACQUISITION coverage report + ingest worklist.

  Answers the standing question 「全世界の不動産の取得 coverage は?」 as a RUNNABLE metric,
  not a guess: how much of the world's land area jinushi has data on, broken down per country,
  plus a self-pruning ingest worklist of KNOWN countries with zero acquired parcels (a covered
  country drops off the worklist automatically — staleness is structurally impossible).

  G1 — a coverage MAP, never a target list: the worklist names COUNTRIES (jurisdictions) to
    pursue registry data from, never parcels/persons to seize. G2 — non-adjudicating: every
    number is a read-time aggregate of disclosed records."
  (:require [clojure.string :as str]
            [jinushi.methods.analyze :as analyze]
            #?(:clj [clojure.java.io :as io])))

(defn- pct [x] (format "%.4g%%" (* 100.0 (double x))))
(defn- km2 [x] (format "%,.1f" (double x)))

(defn worklist
  "KNOWN countries (analyze/country-land-area-km2) with ZERO acquired parcels → ingest worklist.
  Self-pruning: a country present in :by-country is covered and never appears here."
  [res]
  (let [touched (set (keys (:by-country res)))]
    (->> (keys analyze/country-land-area-km2)
         (remove touched)
         (map (fn [cc] {:country cc :land-area-km2 (get analyze/country-land-area-km2 cc)}))
         (sort-by :land-area-km2 >)
         vec)))

(defn report
  "Pure: analyze result → {:lines [str…] :worklist […] :coverage {…}} (markdown-ish text)."
  [res]
  (let [cov (:coverage res)
        con (:concentration res)
        wl (worklist res)
        lines
        (concat
         ["# jinushi 地主 — 全世界 不動産取得 (land-acquisition) coverage"
          ""
          (format "- 取得国数 (countries touched): **%d**" (:countries-touched cov))
          (format "- 取得面積 (acquired land): **%s km²**" (km2 (:acquired-area-km2 cov)))
          ;; HONEST (G4): this measures the COUNTING source (national parks = protected PUBLIC
          ;; land), not all land ownership. It is the share of world land that is national-park
          ;; land we have data on — NOT a claim that 4.8% of all land ownership is mapped.
          (format "- 国立公園(保護公有地)/世界陸地 (national-park land ÷ world land): **%s** (of %s km²)"
                  (pct (:world-coverage-frac cov)) (km2 (:world-land-area-km2 cov)))
          "  _(this is protected-public-land coverage; private/urban/agricultural land is sample-scale only — see building/value layers)_"
          (format "- 取-集中 (land HHI over owners by area): **%.0f**  (top holder share %s)"
                  (:hhi con)
                  (if-let [th (:top-holder con)] (pct (:share th)) "n/a"))
          ""
          "## 国別取得 (per-country acquisition)"]
         (for [[cc v] (sort-by (comp - :area-km2 val) (:per-country cov))]
           (format "- %s: %s km²%s"
                   cc (km2 (:area-km2 v))
                   (if-let [nf (:national-frac v)]
                     (format " (≈ %s of national land)" (pct nf))
                     " (national fraction unknown — area not documented)")))
         [""
          "## 取得ワークリスト (known countries with zero parcels — G1: jurisdictions, not targets)"]
         (if (seq wl)
           (for [w wl] (format "- [ ] %s (%s km² national land)" (:country w) (km2 (:land-area-km2 w))))
           ["- (none — all known countries have ≥1 acquired parcel)"])
         [""
          "## RETURN-to-commons 候補 (advisory; aggregate private holders; G1/G3)"]
         (if (seq (:return-candidates res))
           (for [r (:return-candidates res)]
             (format "- %s (%s) — %s of world-data land area → Council review" (:name r) (:owner r) (pct (:share r))))
           ["- (none above threshold)"]))]
    {:lines (vec lines)
     :worklist wl
     :coverage cov}))

(defn render [res] (str/join "\n" (:lines (report res))))

#?(:clj
   (defn -main [& argv]
     (let [here (or (some-> (when (and *file* (not= *file* "NO_SOURCE_PATH")) (io/file *file*)) .getParentFile .getParentFile) (io/file "20-actors/jinushi"))
           seed (if (and (seq argv) (not (str/starts-with? (first argv) "--")))
                  (io/file (first argv))
                  (io/file here "data" "seed-parcels.kotoba.edn"))
           outdir (io/file here "out")
           res (analyze/analyze (analyze/load-file* seed))
           out (io/file outdir "coverage.md")]
       (.mkdirs outdir)
       (spit out (render res))
       (println (render res))
       0)))
