(ns jinushi.methods.normalize-wdqs
  "jinushi 地主 — PROCESS step: raw WDQS responses → normalized acquisition snapshots.

  The datalad-substrate split (operator directive 2026-06-16): the operator FETCHES raw WDQS
  JSON into the repo DATA LAYER (80-data/jinushi-land/*.raw.json, gitignored → git-annex/IPFS
  cold tier) via methods/fetch_wdqs.sh; this method PROCESSES it later into the committed
  snapshots — deterministically, in code, with the canonical unit map + parse here (not ad-hoc).

  Honesty (G2/G4): area is normalized to m² via the documented unit table; a row whose unit is
  not in the table, or whose country is not ISO-2, or whose area is ≤ 0 (bad data), is DROPPED
  and counted — never guessed. The WDQS 60 s cap often truncates the stream, so the parser
  salvages every COMPLETE (area, unit, cc) triple and ignores a trailing partial row."
  (:require [clojure.string :as str]
            #?(:clj [clojure.java.io :as io])
            #?(:clj [clojure.pprint :as pp])))

;; ── canonical unit → m² (documented; the single source of truth for area normalization) ──
(def unit->m2
  {"Q712226"  1e6           ;; square kilometre
   "Q35852"   1e4           ;; hectare
   "Q3396758" 1e3           ;; decare
   "Q216795"  1e3           ;; dunam (metric)
   "Q81292"   4046.8564224  ;; acre
   "Q25343"   1.0           ;; square metre
   "Q232291"  2589988.110336 ;; square mile
   "Q935614"  1600.0        ;; rai (Thai)
   "Q1399890" 4200.8334})   ;; feddan (Egyptian)

(def unit-label
  {"Q712226" "km2" "Q35852" "hectare" "Q3396758" "decare" "Q216795" "dunam" "Q81292" "acre"
   "Q25343" "m2" "Q232291" "sq-mile" "Q935614" "rai" "Q1399890" "feddan"})

(def ^:private triple-re
  ;; salvage every COMPLETE (area, unit, cc) binding from a (possibly truncated) WDQS JSON stream
  #"(?s)\"area\"\s*:\s*\{[^{}]*?\"value\"\s*:\s*\"([^\"]+)\"[^{}]*?\}.*?\"unit\"\s*:\s*\{[^{}]*?\"value\"\s*:\s*\"([^\"]+)\"[^{}]*?\}.*?\"cc\"\s*:\s*\{[^{}]*?\"value\"\s*:\s*\"([^\"]+)\"[^{}]*?\}")

(defn parse-triples
  "Raw WDQS JSON text (possibly truncated) → seq of [area-str unit-qid cc] triples. Drops any
  trailing-partial row + the WDQS server-timeout trace it sometimes appends."
  [raw]
  (let [cut (let [i (.indexOf raw "SPARQL-QUERY")] (if (neg? i) (count raw) i))]
    (map (comp vec rest) (re-seq triple-re (subs raw 0 cut)))))

(defn unit-qid [u] (last (str/split u #"/")))

(defn normalize
  "Pure: seq of raw WDQS texts → {:records [{:cc :area-m2 :unit-src}] :dropped-unknown-unit n
  :dropped-nonpositive n}. Dedups identical records; sorts by (cc, area)."
  [raw-texts]
  (let [triples (mapcat parse-triples raw-texts)
        tagged (map (fn [[a u cc]]
                      (let [uq (unit-qid u) f (unit->m2 uq)]
                        {:cc cc :unit-src uq :unit-known (boolean f)
                         :cc-ok (boolean (re-matches #"[A-Z]{2}" cc))
                         :area-m2 (when f (* (Double/parseDouble a) f))}))
                    triples)
        unit-bad (count (remove :unit-known tagged))
        good (filter #(and (:unit-known %) (:cc-ok %)) tagged)
        nonpos (count (remove #(pos? (:area-m2 %)) good))
        recs (->> good (filter #(pos? (:area-m2 %)))
                  (map #(select-keys % [:cc :area-m2 :unit-src]))
                  distinct (sort-by (juxt :cc :area-m2)) vec)]
    {:records recs :dropped-unknown-unit unit-bad :dropped-nonpositive nonpos}))

(defn snapshot
  "Assemble a committed-snapshot map from a source spec + normalized records."
  [{:keys [source-id class counts retrieved note]} normd]
  (merge {:source "wikidata:WDQS" :source-id source-id :class class :land-kind :public
          :counts-toward-world-coverage counts :retrieved retrieved
          :units-kept unit-label :record-count (count (:records normd)) :note note}
         (select-keys normd [:dropped-unknown-unit :dropped-nonpositive])
         {:records (:records normd)}))

;; ── source manifest: which raw files (in the data layer) compose each committed snapshot ──
(def sources
  [{:source-id "wikidata-national-parks" :class "Q46169 national park" :counts true
    :retrieved "2026-06-16"
    :raw ["national-parks.raw.json" "national-parks-major.raw.json"
          "national-parks-major2.raw.json" "national-parks-major3.raw.json"
          "national-parks-major4.raw.json" "national-parks-major5.raw.json"
          "national-parks-major6.raw.json" "national-parks-major7.raw.json"]
    :note "National parks = PUBLIC land. PRIMARY world-coverage source. Polite country-bound WDQS fetches merged (initial set + major-country extensions). All units resolved; non-positive bad-data dropped."}
   {:source-id "wikidata-nature-reserves" :class "Q179049 nature reserve" :counts false
    :retrieved "2026-06-16"
    :raw ["nature-reserves.raw.json"]
    :note "Nature reserves = protected PUBLIC land. counts-toward-world-coverage=FALSE: countries overlap national parks; observed-only (geometry de-dup is a future leg)."}])

#?(:clj
   (defn -main [& _argv]
     (let [here (or (some-> (when (and *file* (not= *file* "NO_SOURCE_PATH")) (io/file *file*))
                            .getParentFile .getParentFile)
                    (io/file "20-actors/jinushi"))
           root (or (some-> here .getParentFile .getParentFile) (io/file "."))
           dir (io/file root "80-data" "jinushi-land")]
       (doseq [{:keys [source-id raw] :as spec} sources]
         (let [present (filter #(.exists %) (map #(io/file dir %) raw))]
           (if (empty? present)
             (println (str "skip " source-id " — no raw files present (operator fetch first via fetch_wdqs.sh)"))
             (let [normd (normalize (map slurp present))
                   snap (snapshot spec normd)
                   out (io/file dir (str source-id ".kotoba.edn"))]
               (spit out (str ";; jinushi 地主 — COMMITTED real acquisition snapshot (ADR-2605241500 datalad substrate).\n"
                              ";; GENERATED by methods/normalize_wdqs.cljc from raw WDQS in the data layer. DO NOT hand-edit.\n"
                              ";; Loop re-ingests this with ZERO network I/O; WDQS hit only by methods/fetch_wdqs.sh.\n"
                              (with-out-str (pp/pprint snap))))
               (println (format "%s: %d recs / %d cc (dropped unit %d, nonpos %d) → %s"
                                source-id (:record-count snap) (count (distinct (map :cc (:records snap))))
                                (:dropped-unknown-unit snap) (:dropped-nonpositive snap) (.getName out)))))))
       0)))
