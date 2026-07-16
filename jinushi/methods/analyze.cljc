(ns jinushi.methods.analyze
  "jinushi 地主 — world land-ownership ACQUISITION (取得) + normalization engine.

  The data-acquisition feeder of the etzhayyim land-sovereignty stack (Tree-of-Life land
  doctrine, ADR-2605192100 §1.11 + 2605192245). It ingests PUBLIC land-ownership records
  (parcels + owners), NORMALIZES them onto the kotoba Datom log, and runs edge-primary
  land-取-concentration routed to RETURN-to-commons.

  This closes the world-real-estate ACQUISITION coverage gap: the on-chain LandRegistry
  records only DONATED land (waqf-inalienable), and starts at 0; jinushi is the upstream
  observational mirror that measures HOW MUCH of the world's land we have data on, WHO holds
  it, and WHERE the 取-concentration is — the map that tells the registry what to seek.

  CONSTITUTIONAL (read before any change):
    G1 — a RETURN/commons MAP, NEVER a per-person holdings dossier or occupancy target list.
      Owners are PUBLIC entities or AGGREGATE buckets; natural-person land is folded into a
      single :aggregate owner with no person name; parcel centroids are coarse region
      centroids, never a dwelling fix. return-candidates are ADVISORY + aggregate, never a
      seizure list and never a natural person.
    G2 — non-adjudicating. Registry records (owner, area) are DISCLOSED facts; concentration
      and coverage are read-time aggregates (:bond/is-transient), never jinushi verdicts.
    G3 — acquisition only: jinushi NEVER asserts a transfer / mint / donation. The on-chain
      LandRegistry is the only place a parcel changes hands, and only via member donation
      (no-server-key). jinushi cannot move land.
    G4 — sourcing honesty: R0 seed is :representative synthetic; live registry pull is
      operator/Council-gated.

  Clojure-native actor (no Python twin): real keywords + clojure.edn. File I/O only at the
  #?(:clj) edge; the analysis core is pure + portable .cljc."
  (:require [clojure.string :as str]
            [clojure.edn :as edn]
            #?(:clj [clojure.java.io :as io])))

;; ── documented constants ────────────────────────────────────────────────────
;; World terrestrial land area ≈ 1.4894e8 km² (FAO/CIA WF; excludes inland water/Antarctica
;; ice as habitable land is debated — documented value, overridable). Coverage is honest:
;; sparse data ⇒ a tiny fraction, which is the TRUE acquisition coverage today.
(def world-land-area-km2 148940000)

;; per-country total land area (km²) — only for seeded/known countries; absent ⇒ coverage
;; is reported as "country touched, national fraction unknown" rather than guessed (G4).
(def country-land-area-km2
  {;; seed / large reference economies
   "JP" 364500   "US" 9147420  "BR" 8358140  "AU" 7682300
   "DE" 348560   "KE" 569140   "CN" 9388210  "IN" 2973190
   "RU" 16376870 "CA" 9093510
   ;; documented land areas (km²) for countries appearing in the real WDQS acquisition
   "ES" 498800   "NO" 365268   "UA" 579320  "PL" 304255
   "HU" 90530    "HR" 55960    "LT" 62674   "IE" 68890
   "IL" 21640    "DK" 42430    "KH" 176520  "AZ" 82658
   "UZ" 425400   "PY" 397300   "GT" 107160  "GH" 227540
   "JM" 10830    "HT" 27560    "MK" 25220   "MD" 32890
   "FJ" 18270    "WS" 2830})

;; corporate / form suffix variants → canonical token (for owner_name_norm dedup).
(def ^:private suffix-canon
  {"株式会社" "kk" "有限会社" "yk" "合同会社" "gk"
   "inc" "inc" "inc." "inc" "incorporated" "inc"
   "ltd" "ltd" "ltd." "ltd" "limited" "ltd"
   "co" "co" "co." "co" "company" "co"
   "llc" "llc" "l.l.c." "llc" "corp" "corp" "corp." "corp"
   "s.a." "sa" "sa" "sa" "gmbh" "gmbh" "eg" "eg" "e.g." "eg"})

(def owner-types #{:public :private :ngo :cooperative :unknown})

;; ── digest (G1: deterministic record-id) ─────────────────────────────────────
(defn sha256-hex
  "Lowercase hex SHA-256 of a UTF-8 string."
  [s]
  #?(:clj (let [md (java.security.MessageDigest/getInstance "SHA-256")
                bs (.digest md (.getBytes ^String s "UTF-8"))]
            (apply str (map #(format "%02x" (bit-and % 0xff)) bs)))
     :cljs (throw (ex-info "sha256-hex: :clj only" {}))))

;; ── normalization ────────────────────────────────────────────────────────────
(defn normalize-owner-name
  "Lower-cased, punctuation-folded, suffix-canonicalized owner name for dedup. Deterministic;
  the DISPLAY name is preserved separately."
  [name]
  (let [s (-> (or name "") str/trim str/lower-case
              (str/replace #"[，、,]" " ")
              (str/replace #"\s+" " "))
        toks (str/split s #"\s+")
        canon (map #(let [t (str/replace % #"[.　]" (fn [m] (if (= m ".") "." "")))]
                      (get suffix-canon t t))
                   toks)]
    (str/trim (str/join " " (remove str/blank? canon)))))

(defn owner-type-of [o]
  (let [t (:owner/type o)]
    (if (contains? owner-types t) t :unknown)))

(defn record-id
  "Stable parcel record-id = sha256(country|parcel-id|owner-name-norm|source) (design §4)."
  [{:keys [:parcel/country :parcel/id :parcel/source]} owner-name-norm]
  (sha256-hex (str/join "|" [(str/upper-case (or country "")) id owner-name-norm (or source "")])))

;; ── load ─────────────────────────────────────────────────────────────────────
(defn parse [edn-text] (edn/read-string edn-text))

;; data/seed-parcels.kotoba.edn is stored as Datomic/Datascript tx-data
;; (`[{:db/id -1 :data.seed-parcels/owners "…blob…" :data.seed-parcels/parcels "…blob…"}]`,
;; Phase 4 edn-datomize) rather than the original bare `{:owners […] :parcels […]}` map.
;; tx-data?/unblob/reconstitute detect that shape and rebuild the original bare map so
;; every downstream consumer (analyze/coverage/datom-emit + all test namespaces, which all
;; funnel through this single load-file* chokepoint) keeps working unchanged.
(defn tx-data? [content]
  (and (vector? content) (seq content) (map? (first content)) (contains? (first content) :db/id)))

(defn- unblob [v]
  (if (string? v)
    (try (let [parsed (edn/read-string v)] (if (coll? parsed) parsed v))
         (catch #?(:clj Exception :cljs :default) _ v))
    v))

(defn- reconstitute-entity [tx-data]
  (into {} (map (fn [[k v]] [(keyword (name k)) (unblob v)]))
        (dissoc (first tx-data) :db/id)))

#?(:clj
   (defn load-file* [f]
     (let [content (parse (slurp (io/file f)))]
       (if (tx-data? content)
         (reconstitute-entity content)
         content))))

;; ── analyze ──────────────────────────────────────────────────────────────────
(defn- safe-div [n d] (if (zero? d) 0.0 (/ (double n) (double d))))

(defn analyze
  "Pure: {:owners [...] :parcels [...]} → normalized acquisition view.

  Returns:
    :owners-by-key   key → {:name :type :aggregate? :area-m2 :parcel-ids}
    :parcels         normalized parcels (record-id + owner-name-norm attached), deduped by record-id
    :by-country      cc → {:parcels n :area-m2 a :owner-types {type → area-m2}}
    :concentration   {:total-area-m2 :hhi :top-holder {:key :share} :owner-count}
    :coverage        {:countries-touched :acquired-area-km2 :world-land-area-km2
                      :world-coverage-frac :per-country {cc → {:area-km2 :national-frac|nil}}}
    :return-candidates [{:owner :name :type :share}]  (advisory; aggregate; G1)

  opts :country-area — a {cc → km²} map overriding the built-in country-land-area-km2 table
  (the real WDQS-derived denominator, 80-data/jinushi-land/country-areas.kotoba.edn). When given,
  national fractions resolve for every covered country, not just the hand-coded reference set."
  ([data] (analyze data {}))
  ([{:keys [owners parcels]} {:keys [country-area]}]
  (let [carea (or country-area country-land-area-km2)
        owners-idx (into {} (map (juxt :owner/key identity)) owners)
        ;; attach record-id + owner-name-norm, dedup by record-id (last wins / area-merge)
        normed (reduce
                (fn [acc p]
                  (let [o (get owners-idx (:parcel/owner p))
                        nm-norm (normalize-owner-name (:owner/name o))
                        rid (record-id p nm-norm)]
                    (assoc acc rid (assoc p
                                          :record/id rid
                                          :owner/name-norm nm-norm
                                          :owner/type (owner-type-of o)
                                          :owner/aggregate (boolean (:owner/aggregate o))))))
                {} parcels)
        parcels* (vec (vals normed))
        total-area (reduce + 0.0 (map :parcel/area-m2 parcels*))
        ;; owners-by-key fold
        owners-by-key
        (reduce (fn [acc p]
                  (let [k (:parcel/owner p)]
                    (-> acc
                        (update-in [k :area-m2] (fnil + 0.0) (:parcel/area-m2 p))
                        (update-in [k :parcel-ids] (fnil conj []) (:parcel/id p))
                        (assoc-in [k :name] (:owner/name (owners-idx k)))
                        (assoc-in [k :type] (owner-type-of (owners-idx k)))
                        (assoc-in [k :aggregate?] (boolean (:owner/aggregate (owners-idx k)))))))
                {} parcels*)
        ;; by-country
        by-country
        (reduce (fn [acc p]
                  (let [cc (str/upper-case (:parcel/country p))
                        a (:parcel/area-m2 p)]
                    (-> acc
                        (update-in [cc :parcels] (fnil inc 0))
                        (update-in [cc :area-m2] (fnil + 0.0) a)
                        (update-in [cc :owner-types (:owner/type p)] (fnil + 0.0) a))))
                {} parcels*)
        ;; concentration: HHI over owners by AREA share (0..10000)
        shares (into {} (map (fn [[k v]] [k (safe-div (:area-m2 v) total-area)])) owners-by-key)
        hhi (reduce + 0.0 (map (fn [s] (* 10000.0 s s)) (vals shares)))
        [top-k top-s] (apply max-key val (assoc shares ::none 0.0))
        ;; coverage
        per-country (into {}
                          (map (fn [[cc v]]
                                 (let [acq-km2 (/ (:area-m2 v) 1.0e6)
                                       nat (get carea cc)]
                                   [cc {:area-km2 acq-km2
                                        :national-frac (when nat (safe-div acq-km2 nat))}]))
                               by-country))
        acquired-km2 (reduce + 0.0 (map :area-km2 (vals per-country)))
        ;; return-candidates: PRIVATE non-aggregate holders with ≥10% world-data area share
        ;; → advisory commons-return review (G1: aggregate entity, never a person/seizure).
        ret (->> owners-by-key
                 (filter (fn [[_ v]] (and (= :private (:type v)) (not (:aggregate? v)))))
                 (map (fn [[k v]] {:owner k :name (:name v) :type (:type v) :share (get shares k)}))
                 (filter #(>= (:share %) 0.10))
                 (sort-by :share >)
                 vec)]
    {:owners-by-key owners-by-key
     :parcels parcels*
     :by-country by-country
     :concentration {:total-area-m2 total-area
                     :hhi hhi
                     :top-holder (when (not= top-k ::none) {:key top-k :share top-s})
                     :owner-count (count owners-by-key)}
     :coverage {:countries-touched (count by-country)
                :acquired-area-km2 acquired-km2
                :world-land-area-km2 world-land-area-km2
                :world-coverage-frac (safe-div acquired-km2 world-land-area-km2)
                :per-country per-country}
     :return-candidates ret})))

(defn owner-type-concentration
  "Global holder-TYPE breakdown of the acquired land: the worldwide area held by each :owner/type
  (:public :private :ngo :cooperative :unknown) with each type's share of the total. This is the
  STRUCTURAL 'how much of what we have mapped sits in PRIVATE hands vs already-commons (public /
  cooperative / ngo)' view — the commons-return-relevant roll-up that the per-owner HHI (single
  holders) and the per-country :by-country breakdown do not aggregate worldwide. A read-time
  aggregate MAP, advisory only (G1/G2/G3 — owners are types, never persons; jinushi proposes
  RETURN-to-commons but only the on-chain LandRegistry moves land). Takes an `analyze` result;
  returns [{:type :area-m2 :share} …] sorted by area desc."
  [analysis]
  (let [by-type (reduce (fn [m [_ cc]]
                          (reduce (fn [m [t a]] (update m t (fnil + 0.0) a)) m (:owner-types cc)))
                        {} (:by-country analysis))
        total (reduce + 0.0 (vals by-type))]
    (->> by-type
         (map (fn [[t a]] {:type t :area-m2 a :share (if (pos? total) (/ a total) 0.0)}))
         (sort-by (fn [{:keys [area-m2 type]}] [(- area-m2) (str type)]))
         vec)))

#?(:clj
   (defn -main [& argv]
     (let [here (or (some-> (when (and *file* (not= *file* "NO_SOURCE_PATH")) (io/file *file*)) .getParentFile .getParentFile) (io/file "20-actors/jinushi"))
           seed (if (and (seq argv) (not (str/starts-with? (first argv) "--")))
                  (io/file (first argv))
                  (io/file here "data" "seed-parcels.kotoba.edn"))
           res (analyze (load-file* seed))
           cov (:coverage res)]
       (println (format "jinushi 取得 coverage: %d countries touched, %.3f km² acquired = %.6g%% of world land (%d owners, HHI %.0f)"
                        (:countries-touched cov)
                        (:acquired-area-km2 cov)
                        (* 100.0 (:world-coverage-frac cov))
                        (:owner-count (:concentration res))
                        (:hhi (:concentration res))))
       0)))
