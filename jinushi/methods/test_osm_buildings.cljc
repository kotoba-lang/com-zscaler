(ns jinushi.methods.test-osm-buildings
  "jinushi 地主 — OSM building-stock source tests (open-crowd tier)."
  (:require [clojure.test :refer [deftest is run-tests]]
            [clojure.string :as str]
            [clojure.java.io :as io]
            [jinushi.methods.osm-buildings :as o]
            [jinushi.methods.confidence :as c]))

(def actor-dir (-> *file* io/file .getParentFile .getParentFile))
(def repo-root (-> actor-dir .getParentFile .getParentFile))
(def snap-file (io/file repo-root "80-data" "jinushi-land" "osm-buildings.kotoba.edn"))
(defn snap [] (clojure.edn/read-string (slurp snap-file)))

(deftest test-parse-levels
  (is (= 3 (o/parse-levels "3")))
  (is (= 4 (o/parse-levels "3.5")) "3.5 rounds to 4")
  (is (= 2 (o/parse-levels "2;3")) "takes first")
  (is (= 4 (o/parse-levels "4-6")) "takes first of range")
  (is (nil? (o/parse-levels nil)))
  (is (nil? (o/parse-levels "ground"))))

(deftest test-normalize
  (let [els [{:type "way" :id 1 :tags {:building "yes" :building:levels "16" :name "X" :operator "Marriott"}}
             {:type "way" :id 2 :tags {:building "yes" :building:levels "5"}}]
        recs (o/normalize els "JP" "JP-13")]
    (is (= 2 (count recs)))
    (is (= :org (:owner-type (first recs))) "operator → :org owner-type")
    (is (= "Marriott" (:owner (first recs))))
    (is (= :unmapped (:owner-type (second recs))) "no operator → :unmapped (honest, not guessed)")
    (is (every? #(= :osm (:source %)) recs))))

(deftest test-snapshot-real
  (let [s (snap)]
    (is (= 600 (:building-count s)) "sample size")
    (is (pos? (:with-operator s)) "some operators mapped")
    (is (every? #(= :osm (:source %)) (:records s)) "every record sourced :osm")
    (is (not-any? :owner-name (:records s)) "no natural-person owner names (OSM operators are orgs)")))

(deftest test-confidence-tier
  ;; OSM is open-crowd: below curated Wikidata, well below authoritative cadastres.
  (is (< (c/trust-score :osm) (c/trust-score :wikidata)))
  (is (< (c/trust-score :osm) (c/trust-score :nyc-pluto)))
  (is (= :open-crowd (:tier (c/trust :osm)))))

(deftest test-datoms-no-person
  (let [o (o/datoms (:records (snap)) 1)]
    (is (str/includes? o ":building/floors") "floors emitted")
    (is (str/includes? o ":building/operator") "operator emitted")
    (is (not (str/includes? o ":person")) "G1: no person dimension")))

(when (= *file* (System/getProperty "babashka.file"))
  (let [{:keys [fail error]} (run-tests 'jinushi.methods.test-osm-buildings)]
    (System/exit (+ (or fail 0) (or error 0)))))
