(ns jinushi.methods.company-link
  "jinushi 地主 — AUTHORITATIVE company linkage: building owner → GLEIF legal entity → corp KGs.

  Closes the 企業情報との紐付け loop: a building owner that carries an LEI (P1278) is resolved
  against the GLEIF public register (api.gleif.org) to its AUTHORITATIVE legal identity (legal
  name / jurisdiction / status). The LEI is the cross-actor join key into kabuto 兜 (supply-chain),
  uchiwake 内訳 (BOM), kanjō 勘定 (financial disclosure); the Wikidata QID joins keizu 系図 /
  tsumugi 紡ぎ. So 'who owns this building' resolves to a real, registry-grounded company.

  Legal persons only (GLEIF registers legal entities, never natural persons) — so the authoritative
  company layer is corporate by construction; natural-person owners (public-record, per the
  jurisdiction gate) carry no LEI and simply have no GLEIF row."
  (:require [clojure.string :as str]
            [jinushi.methods.buildings :as buildings]
            [jinushi.methods.datom-emit :as de]
            #?(:clj [clojure.java.io :as io])))

(defn link-records
  "Pure: buildings snapshot + gleif {lei → {:legal-name …}} → cross-actor link records, one per
  building-owner whose LEI resolves in GLEIF."
  [buildings-snap gleif]
  (let [bld (buildings/analyze buildings-snap)
        owners (:owners buildings-snap)]
    (->> (:by-owner bld)
         (keep (fn [[qid v]]
                 (let [lei (:lei v) g (get gleif lei)]
                   (when (and lei g)
                     {:owner qid :wikidata qid :wikidata-label (:label v)
                      :lei lei :gleif-name (:legal-name g) :jurisdiction (:jurisdiction g)
                      :status (:status g) :buildings (:count v)
                      :joins {:kabuto lei :uchiwake lei :kanjo lei :keizu qid}}))))
         (sort-by :buildings >) vec)))

(defn coverage
  "Linkage coverage over the building owners."
  [buildings-snap gleif]
  (let [owners (:owners buildings-snap)
        with-lei (filter :lei (vals owners))
        links (link-records buildings-snap gleif)]
    {:owners-total (count owners)
     :owners-with-lei (count with-lei)
     :owners-gleif-linked (count links)
     :buildings-linked (reduce + 0 (map :buildings links))
     :by-jurisdiction (into (sorted-map) (frequencies (map :jurisdiction links)))}))

(defn datoms
  "Authoritative-company datoms: attach GLEIF facts to owner nodes + a :link/corp bridge edge
  (LEI → corporate KGs). Ground :add (public corporate-registry facts)."
  ([buildings-snap gleif] (datoms buildings-snap gleif 1))
  ([buildings-snap gleif tx]
   (let [L (transient []) links (link-records buildings-snap gleif)]
     (conj! L ";; jinushi 地主 — authoritative company linkage (GLEIF). [e a v tx op].")
     (conj! L ";; legal entities only; LEI is the join key to kabuto/uchiwake/kanjō; QID to keizu/tsumugi.")
     (conj! L "[")
     (doseq [r links]
       (let [e (str "owner." (:owner r))]
         (conj! L (str "[:" e " :owner.org/gleif-lei " (de/fmt (:lei r)) " " tx " :add]"))
         (when (:gleif-name r)   (conj! L (str "[:" e " :owner.org/gleif-name " (de/fmt (:gleif-name r)) " " tx " :add]")))
         (when (:jurisdiction r) (conj! L (str "[:" e " :owner.org/jurisdiction " (de/fmt (:jurisdiction r)) " " tx " :add]")))
         (when (:status r)       (conj! L (str "[:" e " :owner.org/status " (de/fmt (:status r)) " " tx " :add]")))
         ;; cross-actor bridge: this LEI is the join into the corporate KGs
         (conj! L (str "[:" e " :link/corp-lei " (de/fmt (:lei r)) " " tx " :add] ;; → kabuto/uchiwake/kanjō"))))
     (conj! L "]")
     (str (str/join "\n" (persistent! L)) "\n"))))

#?(:clj
   (defn load-gleif [dir]
     (let [f (io/file dir "gleif-companies.kotoba.edn")]
       (when (.exists f) (:companies (clojure.edn/read-string (slurp f)))))))

#?(:clj
   (defn -main [& _argv]
     (let [here (or (some-> (when (and *file* (not= *file* "NO_SOURCE_PATH")) (io/file *file*))
                            .getParentFile .getParentFile) (io/file "20-actors/jinushi"))
           root (or (some-> here .getParentFile .getParentFile) (io/file "."))
           dir (io/file root "80-data" "jinushi-land")
           bsnap (buildings/load-snapshot dir) gleif (load-gleif dir)]
       (if (or (nil? bsnap) (nil? gleif))
         (println "missing buildings or gleif snapshot")
         (let [cov (coverage bsnap gleif) links (link-records bsnap gleif)
               out (io/file dir "company-link-datoms.kotoba.edn")]
           (spit out (datoms bsnap gleif 1))
           (println (format "company linkage: %d/%d building owners → GLEIF (%d buildings); by jurisdiction %s"
                            (:owners-gleif-linked cov) (:owners-total cov) (:buildings-linked cov)
                            (pr-str (:by-jurisdiction cov))))
           (doseq [r (take 8 links)]
             (println (format "  %-34s LEI %s  %s  (%d buildings)" (:gleif-name r) (:lei r) (:jurisdiction r) (:buildings r))))
           (println (str "→ " out))))
       0)))
