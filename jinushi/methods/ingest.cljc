(ns jinushi.methods.ingest
  "jinushi 地主 — REAL land-acquisition ingest from COMMITTED public-data snapshots (multi-source).

  Turns committed acquisition snapshots in the repo DATA LAYER (80-data/jinushi-land/*.kotoba.edn,
  landed via the datalad substrate ADR-2605241500) into the {:owners :parcels} shape that
  analyze/coverage consume — WITHOUT touching the network.

  MULTI-SOURCE + NO DOUBLE-COUNT (G2/G4 honesty): each snapshot declares
  `:counts-toward-world-coverage`. Only counting sources are merged into the world-coverage
  number; overlapping protected-area classes (e.g. nature reserves whose countries already
  carry national parks) are observed SEPARATELY and never summed — geometry de-dup is a future
  leg, so until then summing overlapping designations would inflate coverage dishonestly.

  WDQS / Wikimedia load discipline (operator directive 2026-06-16 「wdqs に負担をかけない」):
    - The committed snapshots are the loop's source of truth; each iteration re-ingests them with
      ZERO network I/O. A 30-min loop hitting WDQS would be abuse; it never does.
    - Live refresh is explicit/operator-only/polite (methods/fetch_wdqs.sh): one small LIMITed
      query, UA + contact, --max-time, courtesy sleep, no retry, refuses LIMIT>800.
    - Units are resolved + area normalized to m² at snapshot time (km²/hectare/decare/dunam/acre/
      m²); rows with an unresolved unit are dropped and the count disclosed, never guessed.
    - National parks / nature reserves are PUBLIC land → G1-safe (public owners, no persons, no
      coordinates; only country + area + a per-source per-country public-owner bucket)."
  (:require [clojure.string :as str]
            [jinushi.methods.analyze :as analyze]
            #?(:clj [clojure.java.io :as io])))

(defn source-slug
  "Short owner-namespace slug for a snapshot source-id (\"wikidata-national-parks\" → \"national-parks\")."
  [source-id]
  (if (str/starts-with? (or source-id "") "wikidata-")
    (subs source-id (count "wikidata-"))
    (or source-id "src")))

(defn owner-key [slug cc] (str "o.public." slug "." cc))

(defn snapshot->dataset
  "Pure: a parsed snapshot {:source-id :class :land-kind :records [{:cc :area-m2 …}]} →
  {:owners :parcels}. One PUBLIC owner bucket per (source, country); one parcel per record."
  [{:keys [source-id class land-kind records] :or {land-kind :public}}]
  (let [slug (source-slug source-id)
        kind (if (contains? analyze/owner-types land-kind) land-kind :public)
        ccs (sort (distinct (map :cc records)))
        owners (mapv (fn [cc]
                       {:owner/key (owner-key slug cc)
                        :owner/name (str (or class slug) " (" cc ")")
                        :owner/type kind})
                     ccs)
        parcels (->> records
                     (group-by :cc)
                     (sort-by key)
                     (mapcat (fn [[cc rs]]
                               (map-indexed
                                (fn [i r]
                                  {:parcel/id (format "WD-%s-%s-%04d" slug cc (inc i))
                                   :parcel/country cc
                                   :parcel/region ""
                                   :parcel/area-m2 (:area-m2 r)
                                   :parcel/owner (owner-key slug cc)
                                   :parcel/source :wikidata})
                                rs)))
                     vec)]
    {:owners owners :parcels parcels}))

(defn merge-datasets
  "Combine many {:owners :parcels} (owners deduped by :owner/key, parcels concatenated)."
  [& datasets]
  {:owners (->> (mapcat :owners datasets)
                (reduce (fn [m o] (assoc m (:owner/key o) o)) {})
                vals vec)
   :parcels (vec (mapcat :parcels datasets))})

(defn counting-dataset
  "Merge ONLY the snapshots flagged :counts-toward-world-coverage → the world-coverage dataset."
  [snaps]
  (apply merge-datasets (map snapshot->dataset (filter :counts-toward-world-coverage snaps))))

(defn sanitize
  "Data-quality gate (G4): drop parcels whose area exceeds their COUNTRY's total area — a parcel
  cannot be larger than its country. These are Wikidata P2046 unit errors (e.g. a value off by
  1000×) or ocean-spanning marine megaparks that inflate LAND coverage. Without this, a handful
  of outliers can over-report coverage by millions of km². `country-area` is {cc → km²}; a
  country with no documented area is left uncapped (cannot judge). Returns
  {:dataset {:owners :parcels} :dropped n :dropped-detail [{:cc :area-km2}]}."
  [{:keys [owners parcels]} country-area]
  (let [ceil-m2 (fn [cc] (* 1.0e6 (get country-area cc 1.0e18)))
        over?   (fn [p] (> (:parcel/area-m2 p) (ceil-m2 (:parcel/country p))))
        keep    (filterv (complement over?) parcels)
        dropped (filterv over? parcels)]
    {:dataset {:owners owners :parcels keep}
     :dropped (count dropped)
     :dropped-detail (->> dropped
                          (map #(hash-map :cc (:parcel/country %) :area-km2 (/ (:parcel/area-m2 %) 1.0e6)))
                          (sort-by :area-km2 >) vec)}))

(defn source-summary
  "Per-source honesty row (counting + non-counting alike)."
  [{:keys [source-id class land-kind counts-toward-world-coverage records dropped-unknown-unit]}]
  {:source-id source-id
   :class class
   :land-kind land-kind
   :counts? (boolean counts-toward-world-coverage)
   :records (count records)
   :countries (count (distinct (map :cc records)))
   :area-km2 (/ (reduce + 0.0 (map :area-m2 records)) 1.0e6)
   :dropped dropped-unknown-unit})

#?(:clj
   (defn data-dir [root] (io/file root "80-data" "jinushi-land")))

#?(:clj
   (defn load-country-areas
     "Real WDQS-derived denominator {cc → km²} (country-areas.kotoba.edn), or nil if absent."
     [dir]
     (let [f (io/file dir "country-areas.kotoba.edn")]
       (when (.exists f) (:area-km2 (analyze/parse (slurp f)))))))

#?(:clj
   (defn load-all-snapshots
     "Parse every acquisition snapshot in the data dir (sorted by source-id). A snapshot is a map
     carrying :source-id — this excludes derived artifacts that share the .kotoba.edn extension
     (e.g. the emitted jinushi-land-datoms.kotoba.edn Datom log, which is a vector)."
     [dir]
     (->> (.listFiles (io/file dir))
          (filter #(str/ends-with? (.getName %) ".kotoba.edn"))
          (map #(analyze/parse (slurp %)))
          ;; LAND-area snapshots only: a map with :source-id and parcel records (km² area). The
          ;; building-ownership snapshot (:kind :ownership) is a different shape — buildings.cljc
          ;; owns it; exclude it here so the land-coverage pipeline never mis-reads it as parcels.
          (filter #(and (map? %) (:source-id %) (not= :ownership (:kind %))))
          (sort-by :source-id)
          vec)))

#?(:clj
   (defn -main [& argv]
     (let [here (or (some-> (when (and *file* (not= *file* "NO_SOURCE_PATH")) (io/file *file*))
                            .getParentFile .getParentFile)
                    (io/file "20-actors/jinushi"))
           root (or (some-> here .getParentFile .getParentFile) (io/file "."))
           dir (data-dir root)
           snaps (load-all-snapshots dir)
           areas (load-country-areas dir)
           {:keys [dataset dropped dropped-detail]} (sanitize (counting-dataset snaps) areas)
           res (analyze/analyze dataset {:country-area areas})
           cov (:coverage res)]
       (require 'jinushi.methods.coverage)
       (println ((resolve 'jinushi.methods.coverage/render) res))
       (when (pos? dropped)
         (println (format ";; data-quality: dropped %d parcel(s) with area > country area (Wikidata P2046 errors / marine): %s"
                          dropped (str/join ", " (map #(format "%s %,.0fkm²" (:cc %) (:area-km2 %)) (take 6 dropped-detail))))))
       (println)
       (println ";; ── sources (per-source honesty; only counting sources sum into world coverage) ──")
       (doseq [s (map source-summary snaps)]
         (println (format ";;  %-26s %-22s %5d recs / %2d cc / %,12.0f km²  counts=%s%s"
                          (:source-id s) (:class s) (:records s) (:countries s) (:area-km2 s)
                          (:counts? s)
                          (if (:counts? s) "" "  (observed-only; overlaps a counting source)"))))
       (println)
       (println (format ";; WORLD COVERAGE (counting sources only): %d countries, %,.0f km² = %.4g%% of world land"
                        (:countries-touched cov) (:acquired-area-km2 cov) (* 100.0 (:world-coverage-frac cov))))
       0)))
