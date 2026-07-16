(ns jinushi.methods.emit-all
  "jinushi 地主 — UNIFIED canonical kotoba Datom log across ALL sources/dimensions.

  Until now each source emitted its own Datom log; the canonical state was fragmented. This
  composes every layer the actor has acquired into ONE append-only EAVT Datom log
  (ADR-2605312345): LAND parcels (national parks, sanitized) + BUILDING ownership (+owners,
  floors) + GLEIF company linkage + NYC PLUTO parcels + OSM building stock + DVF value aggregates.
  Each datom keeps its source so the confidence model (confidence.cljc) can weight it on read.

  Read-only composition of committed, content-addressed snapshots (no network); the unified log is
  itself content-addressed (CIDv1) — the whole 不動産取得 as first-class canonical state."
  (:require [clojure.string :as str]
            [jinushi.methods.analyze :as analyze]
            [jinushi.methods.ingest :as ingest]
            [jinushi.methods.datom-emit :as land]
            [jinushi.methods.buildings :as buildings]
            [jinushi.methods.company-link :as company]
            [jinushi.methods.nyc-pluto :as pluto]
            [jinushi.methods.osm-buildings :as osm]
            [jinushi.methods.dvf-values :as dvf]
            #?(:clj [clojure.java.io :as io])))

(defn- section [title body]
  (str "\n;; ══════════════════════════════════════════════════════════════\n"
       ";; " title "\n"
       ";; ══════════════════════════════════════════════════════════════\n"
       ;; strip each layer's own opening [ / closing ] + header comments → inline its datoms
       (->> (str/split-lines body)
            (remove #(or (str/starts-with? (str/triml %) ";;")
                         (= "[" (str/trim %)) (= "]" (str/trim %))
                         (str/blank? %)))
            (str/join "\n"))))

(defn dvf-value-datoms
  "Aggregate DVF value datoms (transient; €/m² per commune — aggregate, G2)."
  [dvf-analysis tx]
  (->> (:by-commune dvf-analysis)
       (mapcat (fn [[c v]]
                 (cond-> []
                   (:appt-median-eur-m2 v)
                   (conj (str "[:commune." c " :value/appt-median-eur-m2 "
                              (land/fmt (double (:appt-median-eur-m2 v))) " " tx " :derived] ;; :bond/is-transient true source=dvf"))
                   true
                   (conj (str "[:commune." c " :value/mutations " (:mutations v) " " tx " :add] ;; source=dvf")))))
       (str/join "\n")))

#?(:clj
   (defn build [dir tx]
     (let [areas (ingest/load-country-areas dir)
           land-ds (:dataset (ingest/sanitize (ingest/counting-dataset (ingest/load-all-snapshots dir)) areas))
           bsnap (buildings/load-snapshot dir)
           gleif (company/load-gleif dir)
           rd (fn [n] (let [f (io/file dir n)] (when (.exists f) (clojure.edn/read-string (slurp f)))))
           pluto-snap (rd "nyc-pluto-parcels.kotoba.edn")
           osm-snap (rd "osm-buildings.kotoba.edn")
           dvf-recs (dvf/load-all dir)]
       (str ";; jinushi 地主 — UNIFIED canonical kotoba Datom log (ALL sources). [e a v tx op].\n"
            ";; ADR-2605312345 first-class canonical state. Each datom tagged with its source; confidence.cljc weights on read.\n"
            "[\n"
            (section "LAND (national parks, sanitized; source=wikidata)"
                     (land/emit land-ds (analyze/analyze land-ds {:country-area areas}) tx))
            (when bsnap (section "BUILDINGS ownership + floors (source=wikidata)"
                                 (buildings/datoms bsnap (buildings/analyze bsnap) tx)))
            (when (and bsnap gleif) (section "COMPANY linkage (source=gleif authoritative)"
                                             (company/datoms bsnap gleif tx)))
            (when pluto-snap (section "NYC PLUTO parcels (source=nyc-pluto gov cadastre)"
                                      (pluto/datoms (:records pluto-snap) tx)))
            (when osm-snap (section "OSM building stock (source=osm open-crowd)"
                                    (osm/datoms (:records osm-snap) tx)))
            (when (seq dvf-recs) (str (section "DVF property values (source=dvf; aggregate)" "[\n]")
                                      "\n" (dvf-value-datoms (dvf/analyze* dvf-recs) tx)))
            "\n]\n"))))

#?(:clj
   (defn -main [& _argv]
     (let [here (or (some-> (when (and *file* (not= *file* "NO_SOURCE_PATH")) (io/file *file*))
                            .getParentFile .getParentFile) (io/file "20-actors/jinushi"))
           root (or (some-> here .getParentFile .getParentFile) (io/file "."))
           dir (ingest/data-dir root)
           out (io/file dir "jinushi-unified-datoms.kotoba.edn")
           txt (build dir 1)]
       (spit out txt)
       (require 'jinushi.methods.cid)
       (println (format "unified canonical Datom log → %s\n  %d datom lines, CIDv1 %s"
                        (.getName out)
                        (count (filter #(str/starts-with? (str/triml %) "[:") (str/split-lines txt)))
                        ((resolve 'jinushi.methods.cid/file->cidv1) out)))
       0)))
