(ns jinushi.methods.jurisdiction
  "jinushi 地主 — per-jurisdiction PUBLIC-RECORD gate for ownership ingestion.

  Grounds the reframed gate (operator directive 2026-06-16): land/building ownership is public
  record and natural-person ownership is representable — BUT only where the registry is actually
  PUBLIC-by-law + bulk-accessible + owner-names-visible AND can be republished SYMMETRICALLY. This
  registry records, per jurisdiction, whether those conditions hold, so cadastre ingestion is
  PRINCIPLED, not guesswork. Unknown jurisdictions degrade honestly to :unknown → persons are NOT
  bulk-ingested there (stay aggregate), never guessed (the tate-actor jurisdiction-honesty pattern).

  This is a CONSERVATIVE, source-cited registry of well-known regimes; everything else is :unknown.
  It judges ACCESS REGIME, not any individual — it is not itself a person record."
  (:require [clojure.string :as str]
            #?(:clj [clojure.java.io :as io])))

;; :access ∈ {:public :restricted :unknown}     — is the register open to the public by law?
;; :bulk   ∈ {:yes :priced :per-parcel :no :unknown} — can ownership be obtained in bulk?
;; :person-names ∈ {:visible :restricted :unknown}   — are natural-person owner names disclosed?
(def registry
  {"SE" {:registry "Lantmäteriet (Fastighetsregistret)" :access :public :bulk :yes :person-names :visible
         :source "https://www.lantmateriet.se/" :note "Sweden — strongly open; ownership broadly public"}
   "NO" {:registry "Kartverket — Grunnboka" :access :public :bulk :priced :person-names :visible
         :source "https://www.kartverket.no/" :note "Norway — public land register"}
   "US" {:registry "County recorder / assessor (50-state, county-level)" :access :public :bulk :yes :person-names :visible
         :source "https://www.census.gov/" :note "US — county records public; bulk varies by county (mostly yes)"}
   "GB" {:registry "HM Land Registry (E&W)" :access :public :bulk :priced :person-names :visible
         :source "https://www.gov.uk/government/organisations/land-registry" :note "priced per title; bulk via commercial/INSPIRE"}
   "NL" {:registry "Kadaster" :access :public :bulk :priced :person-names :visible
         :source "https://www.kadaster.nl/" :note "public, priced per lookup"}
   "IE" {:registry "Tailte Éireann (Land Registry)" :access :public :bulk :priced :person-names :visible
         :source "https://www.tailte.ie/" :note "public, priced"}
   "KR" {:registry "등기부등본 (real-estate register)" :access :public :bulk :per-parcel :person-names :visible
         :source "http://www.iros.go.kr/" :note "anyone may obtain per-property; not open-bulk"}
   "JP" {:registry "不動産登記 (real property register)" :access :public :bulk :per-parcel :person-names :visible
         :source "https://www.touki.or.jp/" :note "anyone may request 登記事項証明書 per parcel (fee); NO open bulk"}
   "FR" {:registry "Cadastre / fichier des propriétaires" :access :public :bulk :no :person-names :restricted
         :source "https://cadastre.data.gouv.fr/" :note "parcel geometry open (Etalab) but NO bulk OWNER data; owner names by demande individuelle"}
   "DE" {:registry "Grundbuch" :access :restricted :bulk :no :person-names :restricted
         :source "https://www.grundbuch.de/" :note "berechtigtes Interesse (legitimate interest) required — NOT public-open"}
   "AT" {:registry "Grundbuch" :access :restricted :bulk :no :person-names :restricted
         :source "https://www.justiz.gv.at/" :note "fee + legitimate interest; not open-bulk"}
   "CH" {:registry "Grundbuch / registre foncier" :access :restricted :bulk :no :person-names :restricted
         :source "https://www.ejpd.admin.ch/" :note "legitimate interest for owner data (cantonal)"}})

(defn jurisdiction
  "Lookup a jurisdiction's regime (honest degrade to :unknown for absent ISO-2)."
  [cc]
  (get registry cc {:registry :unknown :access :unknown :bulk :unknown :person-names :unknown
                    :source nil :note "not in registry — honest degrade; persons NOT bulk-ingested"}))

(defn persons-bulk-ingestable?
  "May natural-person ownership be ingested IN BULK + republished symmetrically for this
  jurisdiction? Only when access is public, names are visible, and bulk is open/priced (NOT
  per-parcel-only / restricted / unknown). Conservative: unknown ⇒ false."
  [cc]
  (let [j (jurisdiction cc)]
    (boolean (and (= :public (:access j))
                  (= :visible (:person-names j))
                  (contains? #{:yes :priced} (:bulk j))))))

(defn persons-mode
  "Classify how natural-person ownership may be handled in a jurisdiction."
  [cc]
  (let [j (jurisdiction cc)]
    (cond
      (persons-bulk-ingestable? cc) :bulk-public
      (and (= :public (:access j)) (= :per-parcel (:bulk j))) :per-parcel-only
      (= :restricted (:access j)) :restricted
      (= :restricted (:person-names j)) :names-restricted
      :else :unknown)))

#?(:clj
   (defn -main [& _argv]
     (println "jurisdiction public-record gate (land/building ownership):")
     (doseq [cc (sort (keys registry))]
       (let [j (jurisdiction cc)]
         (println (format "  %s  access=%-10s bulk=%-10s names=%-10s persons=%s"
                          cc (name (:access j)) (name (:bulk j)) (name (:person-names j))
                          (name (persons-mode cc))))))
     (println "  (any other jurisdiction → :unknown → natural persons NOT bulk-ingested, honest degrade)")
     0))
