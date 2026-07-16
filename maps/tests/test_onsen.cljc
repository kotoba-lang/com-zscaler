(ns maps.tests.test-onsen
  "maps — 温泉(onsen) discovery + recommendation tests (ADR-2606064500). stdlib; network-free.
  Exercises the pure stages (overpass-ql / parse-overpass / score-onsen) and the read-path
  `recommend` against the in-memory kotoba-local AVET store — no Overpass, no HTTP."
  (:require [clojure.test :refer [deftest is run-tests]]
            [clojure.string :as str]
            [maps.methods.onsen    :as onsen]
            [maps.tests.kotoba-local :as kl]))

;; ── a tiny offline Overpass JSON fixture (mirrors cheshire's string-keyed shape) ──
(def ^:private osm-fixture
  {"elements"
   [{"type" "node" "id" 1 "lat" 36.6225 "lon" 138.5966
     "tags" {"natural" "hot_spring" "name" "草津温泉" "name:en" "Kusatsu Onsen"
             "wikidata" "Q11531" "opening_hours" "24/7" "website" "https://example.jp"}}
    {"type" "way" "id" 2 "center" {"lat" 34.7986 "lon" 135.2486}
     "tags" {"amenity" "public_bath" "bath:type" "onsen" "name" "有馬温泉"}}
    {"type" "node" "id" 3 "lat" 35.0 "lon" 135.0
     "tags" {"amenity" "restaurant" "name" "蕎麦屋"}}]})   ;; NOT an onsen — must be filtered

;; ── overpass-ql (pure) ────────────────────────────────────────────────────────
(deftest test-overpass-ql-shape
  (let [ql (onsen/overpass-ql [35.0 138.0 36.0 139.0])]
    (is (str/includes? ql "[out:json]"))
    (is (str/includes? ql "natural\"=\"hot_spring"))
    (is (str/includes? ql "bath:type\"=\"onsen"))
    (is (str/includes? ql "out center tags"))
    ;; bbox interpolated as south,west,north,east
    (is (str/includes? ql "35.000000,138.000000,36.000000,139.000000"))))

;; ── parse-overpass (pure) ─────────────────────────────────────────────────────
(deftest test-parse-filters-non-onsen
  (let [feats (onsen/parse-overpass osm-fixture)]
    (is (= 2 (count feats)) "the restaurant element must be filtered out")
    (is (contains? feats "osm/node/1"))
    (is (contains? feats "osm/way/2"))
    (is (not (contains? feats "osm/node/3")))))

(deftest test-parse-way-uses-center-centroid
  (let [w (get (onsen/parse-overpass osm-fixture) "osm/way/2")]
    (is (= 34.7986 (get w ":feature/lat")))
    (is (= 135.2486 (get w ":feature/lon")))
    (is (= ":onsen" (get w ":feature/label")))))

(deftest test-parse-sourcing-honesty
  ;; G3: a real OSM element is :authoritative and carries its source DID
  (let [n (get (onsen/parse-overpass osm-fixture) "osm/node/1")]
    (is (= ":authoritative" (get n ":feature/sourcing")))
    (is (= ":osm" (get n ":feature/source")))
    (is (= "did:web:maps.etzhayyim.com:infrastructure" (get n ":feature/source-did")))
    (is (= "草津温泉" (get n ":feature/name")))))

;; ── score-onsen (pure, explainable) ───────────────────────────────────────────
(deftest test-score-authentic-beats-bare
  (let [authentic (onsen/score-onsen {"natural" "hot_spring" "name" "草津温泉" "wikidata" "Q1"})
        bare      (onsen/score-onsen {"amenity" "public_bath"})]
    (is (> (:score authentic) (:score bare)))
    (is (>= (:score authentic) 6.0))))   ;; 3 authentic + 2 notable + 1 named

(deftest test-score-why-is-auditable
  (let [{:keys [why]} (onsen/score-onsen {"natural" "hot_spring" "name" "X"
                                          "bath:open_air" "yes"})]
    (is (some #(= "authentic-hot-spring" %) why))
    (is (some #(= "open-air-rotenburo" %) why))))

(deftest test-score-no-person-fact
  ;; G9 / charter: a bath with zero public facts scores 0 — there is no hidden
  ;; per-person signal that could move it.
  (is (= 0.0 (:score (onsen/score-onsen {"leisure" "spa"})))))

;; ── recommend (read path over kotoba-local) ───────────────────────────────────
(defn- store-of [features]
  (kl/build-store-from-features features))

(deftest test-recommend-ranks-by-score
  (let [feats (onsen/parse-overpass osm-fixture)
        qfn   (kl/make-query-fn (store-of feats))
        rows  (onsen/recommend qfn)]
    (is (= 2 (count rows)))
    (is (= "osm/node/1" (:id (first rows))) "草津 (hot_spring+wikidata) outranks 有馬 (bare onsen bath)")
    (is (> (:score (first rows)) (:score (second rows))))
    (is (vector? (:why (first rows))))))

(deftest test-recommend-label-honesty
  ;; a :station feature in the same store must never surface in an onsen recommendation
  (let [feats (assoc (onsen/parse-overpass osm-fixture)
                     "st.tokyo" {":feature/id" "st.tokyo" ":feature/label" ":station"
                                 ":feature/name" "東京駅"})
        qfn   (kl/make-query-fn (store-of feats))
        rows  (onsen/recommend qfn)]
    (is (every? #(str/starts-with? (:id %) "osm/") rows))
    (is (not-any? #(= "st.tokyo" (:id %)) rows))))

(deftest test-recommend-proximity-tiebreak
  ;; two onsen with identical tags (equal base score) — :near must rank the nearer first
  (let [feats {"osm/node/near" {":feature/id" "osm/node/near" ":feature/label" ":onsen"
                                ":feature/name" "近" ":feature/lat" "35.01" ":feature/lon" "135.01"
                                ":feature/props" "{\"natural\":\"hot_spring\",\"name\":\"近\"}"}
               "osm/node/far"  {":feature/id" "osm/node/far" ":feature/label" ":onsen"
                                ":feature/name" "遠" ":feature/lat" "43.06" ":feature/lon" "141.35"
                                ":feature/props" "{\"natural\":\"hot_spring\",\"name\":\"遠\"}"}}
        qfn   (kl/make-query-fn (store-of feats))
        rows  (onsen/recommend qfn :near [35.0 135.0])]
    (is (= "osm/node/near" (:id (first rows))))
    (is (every? :distance-m rows))
    (is (< (:distance-m (first rows)) (:distance-m (second rows))))))

#?(:clj
   (when (= *ns* (find-ns 'maps.tests.test-onsen))
     (run-tests)))
