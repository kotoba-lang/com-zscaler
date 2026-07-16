(ns jinushi.methods.nyc-pluto
  "jinushi 地主 — REAL government-cadastre ingest beyond WDQS: NYC PLUTO (Socrata open data).

  Proves the multi-source design: a government open-data portal (data.cityofnewyork.us, public
  domain) yields parcel-level ownership — owner, floors, lot area, building class, BBL parcel id —
  including NATURAL-PERSON owners. US-NY is a bulk-public jurisdiction (jurisdiction.cljc), so
  natural-person ownership ingest is gate-permitted (PUBLIC-RECORD + SYMMETRIC, ADR-2606082400).

  TWO distinct layers, by design:
    INGEST GATE (jurisdiction.cljc) — US-NY bulk-public ⇒ natural persons may be ingested.
    PUBLISH PRUDENCE — even gate-permitted, the COMMITTED public artifact does not become a new
      bulk surface for NAMED individuals (Wellbecoming): legal-entity owners are NAMED (corporate
      accountability), natural-person owners are kept as an ANONYMIZED key (sha256 of the
      normalized name, first 12 hex) + :natural-person type + counts — concentration stays
      computable, names stay only in the local gitignored raw. G1 also drops precise dwelling
      coordinates entirely (BBL + borough only, never lat/lon).

  Owner legal-entity vs natural-person is a heuristic over the free-text PLUTO ownername (no LEI/
  QID in PLUTO); the heuristic is disclosed, not asserted as fact (G2)."
  (:require [clojure.string :as str]
            [jinushi.methods.analyze :as analyze]
            #?(:clj [clojure.java.io :as io])))

(def ^:private org-re
  #"(?i)\b(LLC|L\.?L\.?C|INC|CORP|CO\b|COMPANY|LP\b|LLP|LTD|TRUST|CITY|DEPT|DEPARTMENT|NYC|HOUSING|CHURCH|TEMPLE|SYNAGOGUE|ASSOC|ASSOCIATION|AUTHORITY|BANK|REALTY|HOLDINGS?|PARTNERS|FUND|MTA|STATE|UNITED STATES|BD OF|BOARD|HDFC|CORPORATION|ENTERPRISES?|MANAGEMENT|PROPERTIES|GROUP|FOUNDATION|UNIVERSITY|COLLEGE|HOSPITAL|AGENCY|COMMISSION|DISTRICT|TRANSIT|PORT)\b")

(defn org? [owner-name] (boolean (and owner-name (re-find org-re owner-name))))

(defn owner-key
  "Named for legal entities (accountability); anonymized sha256-prefix for natural persons (publish
  prudence — names live only in the local raw)."
  [owner-name org?]
  (if org?
    (str "org." (-> owner-name str/lower-case (str/replace #"[^a-z0-9]+" "-") (str/replace #"^-|-$" "")))
    (str "np." (subs (analyze/sha256-hex (str/lower-case (str/trim (or owner-name "")))) 0 12))))

(defn normalize
  "Pure: PLUTO rows (parsed Socrata JSON maps) → parcel-ownership records (no names for persons)."
  [rows]
  (->> rows
       (keep (fn [r]
               (let [nm (:ownername r) o (org? nm)]
                 (when (:bbl r)
                   {:parcel/id (:bbl r) :region "US-NY" :borough (:borough r)
                    :floors (some-> (:numfloors r) (#(try (Math/round (Double/parseDouble %)) (catch #?(:clj Exception :cljs :default) _ nil))))
                    :lot-area-sqft (some-> (:lotarea r) (#(try (Double/parseDouble %) (catch #?(:clj Exception :cljs :default) _ nil))))
                    :bldgclass (:bldgclass r)
                    :owner/type (if o :org :natural-person)
                    :owner/key (owner-key nm o)
                    :owner/name (when o nm)        ;; legal entities named; persons anonymized
                    :source :nyc-pluto}))))
       vec))

(defn analyze*
  "Owner-type breakdown + concentration over PLUTO parcels."
  [records]
  (let [by-type (reduce (fn [m r] (update m (:owner/type r) (fnil inc 0))) {} records)
        by-owner (reduce (fn [m r]
                           (-> m (update-in [(:owner/key r) :lots] (fnil inc 0))
                               (update-in [(:owner/key r) :floors] (fnil + 0) (or (:floors r) 0))
                               (assoc-in [(:owner/key r) :type] (:owner/type r))
                               (assoc-in [(:owner/key r) :name] (:owner/name r))))
                         {} records)
        top-org (->> by-owner (filter (fn [[_ v]] (= :org (:type v))))
                     (map (fn [[k v]] {:owner k :name (:name v) :lots (:lots v) :floors (:floors v)}))
                     (sort-by :lots >) (take 10) vec)]
    {:parcels (count records) :owner-types by-type
     :distinct-owners (count by-owner)
     :top-org-owners top-org}))

(defn datoms
  "EAVT parcel-ownership datoms (G1: no coordinates; legal entities named, persons anonymized)."
  ([records] (datoms records 1))
  ([records tx]
   (let [q (fn [v] (str "\"" (str/replace (str v) "\"" "'") "\""))
         L (transient [])]
     (conj! L ";; jinushi 地主 — NYC PLUTO parcel ownership (US-NY, bulk-public; public domain). [e a v tx op].")
     (conj! L ";; PUBLIC-RECORD + SYMMETRIC; legal entities named, natural persons anonymized; no coordinates (G1).")
     (conj! L "[")
     (doseq [r records]
       (let [e (str "parcel.us-ny." (:parcel/id r))]
         (conj! L (str "[:" e " :parcel/region \"US-NY\" " tx " :add]"))
         (when (:floors r) (conj! L (str "[:" e " :parcel/floors " (:floors r) " " tx " :add]")))
         (when (:bldgclass r) (conj! L (str "[:" e " :parcel/bldgclass " (q (:bldgclass r)) " " tx " :add]")))
         (conj! L (str "[:" e " :parcel/owner :owner." (:owner/key r) " " tx " :add]"))
         (conj! L (str "[:owner." (:owner/key r) " :owner/type " (:owner/type r) " " tx " :add]"))
         (when (:owner/name r) (conj! L (str "[:owner." (:owner/key r) " :owner/name " (q (:owner/name r)) " " tx " :add]")))))
     (conj! L "]")
     (str (str/join "\n" (persistent! L)) "\n"))))

#?(:clj
   (defn load-raw [dir]
     (let [f (io/file dir "nyc-pluto.raw.json")]
       (when (.exists f) (cheshire.core/parse-string (slurp f) true)))))

#?(:clj
   (defn -main [& _argv]
     (let [here (or (some-> (when (and *file* (not= *file* "NO_SOURCE_PATH")) (io/file *file*))
                            .getParentFile .getParentFile) (io/file "20-actors/jinushi"))
           root (or (some-> here .getParentFile .getParentFile) (io/file "."))
           dir (io/file root "80-data" "jinushi-land")
           rows (load-raw dir)]
       (if-not rows
         (println "no nyc-pluto.raw.json — operator fetch first (Socrata SODA API, public domain)")
         (let [recs (normalize rows) a (analyze* recs)]
           (println (format "NYC PLUTO: %d parcels, %d owners; types %s"
                            (:parcels a) (:distinct-owners a) (pr-str (:owner-types a))))
           (doseq [t (take 6 (:top-org-owners a))]
             (println (format "  %-40s %d lots, %d floors" (:name t) (:lots t) (:floors t)))))))
     0))
