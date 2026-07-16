(ns jinushi.methods.test-ingest
  "jinushi 地主 — multi-source real-snapshot ingest tests (offline; reads COMMITTED snapshots)."
  (:require [clojure.test :refer [deftest is run-tests]]
            [clojure.string :as str]
            [clojure.java.io :as io]
            [jinushi.methods.analyze :as a]
            [jinushi.methods.ingest :as ing]))

(def actor-dir (-> *file* io/file .getParentFile .getParentFile))
(def repo-root (-> actor-dir .getParentFile .getParentFile))
(def data-dir (io/file repo-root "80-data" "jinushi-land"))
(defn snaps [] (ing/load-all-snapshots data-dir))

(def tiny
  {:source-id "wikidata-national-parks" :class "Q46169 national park" :land-kind :public
   :counts-toward-world-coverage true
   :records [{:cc "NO" :area-m2 1.605e9 :unit-src "Q712226"}
             {:cc "NO" :area-m2 1.10e9  :unit-src "Q35852"}
             {:cc "FI" :area-m2 5.0e7   :unit-src "Q3396758"}]})

(deftest test-snapshot-to-dataset
  (let [{:keys [owners parcels]} (ing/snapshot->dataset tiny)]
    (is (= 2 (count owners)) "one public owner bucket per country")
    (is (every? #(= :public (:owner/type %)) owners) "national-park owners are PUBLIC land")
    (is (every? #(str/includes? (:owner/key %) "national-parks") owners) "owner key carries the source slug")
    (is (= 3 (count parcels)) "one parcel per record")
    (is (apply distinct? (map :parcel/id parcels)) "parcel ids unique + deterministic")
    (is (every? #(= :wikidata (:parcel/source %)) parcels) "source attribution preserved")))

(deftest test-multi-source-loads
  (let [ss (snaps)]
    (is (>= (count ss) 2) "≥2 committed snapshots (national parks + nature reserves)")
    (is (some #(= "wikidata-national-parks" (:source-id %)) ss))
    (is (some #(= "wikidata-nature-reserves" (:source-id %)) ss))))

(deftest test-no-double-count-overlap-excluded
  ;; G2/G4: only :counts-toward-world-coverage sources merge into world coverage; the overlapping
  ;; nature-reserve source must NOT contribute any parcel to the counting dataset.
  (let [ss (snaps)
        cd (ing/counting-dataset ss)]
    (is (not-any? #(str/includes? (:parcel/owner %) "nature-reserves") (:parcels cd))
        "overlapping nature-reserve parcels are excluded from the world-coverage dataset")
    (is (every? #(str/includes? (:parcel/owner %) "national-parks") (:parcels cd))
        "the counting dataset is the national-park (non-overlapping) source only")))

(deftest test-source-summary-honest
  (let [by-id (into {} (map (juxt :source-id identity) (map ing/source-summary (snaps))))]
    (is (true?  (:counts? (by-id "wikidata-national-parks"))) "national parks count toward coverage")
    (is (false? (:counts? (by-id "wikidata-nature-reserves"))) "nature reserves are observed-only")
    (is (>= (:countries (by-id "wikidata-national-parks")) 20) "national parks span ≥20 countries")))

(deftest test-units-resolved-no-drops
  ;; decare/dunam/acre/hectare/km²/m² all resolved at snapshot time → honest, 0 dropped.
  (let [by-id (into {} (map (juxt :source-id identity) (snaps)))]
    (is (= 0 (:dropped-unknown-unit (by-id "wikidata-national-parks"))) "national-park units fully resolved")
    (is (= 0 (:dropped-unknown-unit (by-id "wikidata-nature-reserves"))) "nature-reserve units fully resolved")
    (is (= (:record-count (by-id "wikidata-national-parks"))
           (count (:records (by-id "wikidata-national-parks")))) "record-count matches records")))

(deftest test-g1-public-no-person
  (let [{:keys [owners parcels]} (ing/snapshot->dataset (first (filter :counts-toward-world-coverage (snaps))))]
    (is (every? #(= :public (:owner/type %)) owners) "every acquired owner is PUBLIC")
    (is (not-any? :parcel/centroid parcels) "no per-parcel coordinate from this source (G1)")
    (is (every? #(pos? (:parcel/area-m2 %)) parcels) "every acquired area is positive")))

(deftest test-sanitize-drops-over-country-area
  ;; G4 data-quality: a parcel larger than its country is a Wikidata P2046 error / marine megapark.
  (let [areas {"NO" 385000.0 "JP" 364500.0}   ;; km²
        ds {:owners [] :parcels [{:parcel/country "NO" :parcel/area-m2 1.8e12}   ;; 1.8M km² > NO → drop
                                 {:parcel/country "JP" :parcel/area-m2 2.0e9}    ;; 2000 km² < JP → keep
                                 {:parcel/country "XX" :parcel/area-m2 9.9e15}]} ;; no area for XX → uncapped keep
        {:keys [dataset dropped dropped-detail]} (ing/sanitize ds areas)]
    (is (= 1 dropped) "the over-country parcel is dropped")
    (is (= "NO" (:cc (first dropped-detail))) "drop detail names the country")
    (is (= 2 (count (:parcels dataset))) "in-country + uncapped parcels kept")))

(deftest test-sanitize-real-snapshot
  (let [areas (ing/load-country-areas data-dir)
        {:keys [dropped]} (ing/sanitize (ing/counting-dataset (snaps)) areas)]
    (is (number? dropped) "sanitize runs on the real dataset")
    (is (some? areas) "real country-area denominator is present")))

(deftest test-real-coverage-above-synthetic-floor
  (let [cd (ing/counting-dataset (snaps))
        res (a/analyze cd)
        cov (:coverage res)]
    (is (>= (:countries-touched cov) 20) "real coverage touches ≥20 countries")
    (is (> (:world-coverage-frac cov) 0.005) "real public-land coverage exceeds the 0.056% synthetic floor")
    (is (every? #(= :wikidata (:parcel/source %)) (:parcels res)) "every analyzed parcel carries its real source")))

(when (= *file* (System/getProperty "babashka.file"))
  (let [{:keys [fail error]} (run-tests 'jinushi.methods.test-ingest)]
    (System/exit (+ (or fail 0) (or error 0)))))
