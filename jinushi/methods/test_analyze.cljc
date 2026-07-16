(ns jinushi.methods.test-analyze
  "jinushi 地主 — acquisition/normalization engine tests."
  (:require [clojure.test :refer [deftest is run-tests]]
            [clojure.string :as str]
            [clojure.java.io :as io]
            [jinushi.methods.analyze :as a]))

(def actor-dir (-> *file* io/file .getParentFile .getParentFile))
(def seed (io/file actor-dir "data" "seed-parcels.kotoba.edn"))
(defn load-seed [] (a/load-file* seed))
(defn res [] (a/analyze (load-seed)))

(deftest test-normalize-owner-name
  (is (= "mitsui fudosan co ltd" (a/normalize-owner-name "Mitsui Fudosan Co., Ltd."))
      "suffix canon folds 'Co., Ltd.' punctuation → 'co ltd' deterministically")
  (is (= (a/normalize-owner-name "ACME Inc.") (a/normalize-owner-name "acme inc"))
      "case + punctuation fold to one norm")
  (is (= "" (a/normalize-owner-name nil)) "nil owner name → empty norm"))

(deftest test-record-id-deterministic
  (let [p {:parcel/id "JP-13-0001" :parcel/country "jp" :parcel/source :registry-api}]
    (is (= (a/record-id p "国土交通省") (a/record-id p "国土交通省")) "record-id is deterministic")
    (is (= 64 (count (a/record-id p "x"))) "record-id is a 64-hex sha256")
    (is (not= (a/record-id p "国土交通省") (a/record-id p "別人")) "owner change → different id")))

(deftest test-coverage-honest
  (let [r (res) cov (:coverage r)]
    (is (= 6 (:countries-touched cov)) "seed touches 6 countries")
    (is (< 0.0 (:world-coverage-frac cov) 0.01)
        "acquisition coverage is a tiny, honest fraction of world land (sparse data)")
    (is (= a/world-land-area-km2 (:world-land-area-km2 cov)) "world land area is the documented constant")
    ;; per-country national fraction only where land area is documented (G4 honesty)
    (is (contains? (:per-country cov) "JP"))
    (is (number? (:national-frac (get-in cov [:per-country "JP"]))))))

(deftest test-concentration
  (let [c (:concentration (res))]
    (is (< 0.0 (:hhi c) 10000.0) "HHI in (0,10000]")
    (is (= "o.corp.br.agro" (get-in c [:top-holder :key])) "largest single area = Agro latifundio")
    (is (= 11 (:owner-count c)) "11 owners in seed")))

(deftest test-return-candidates-aggregate-only
  (let [ret (:return-candidates (res))]
    (is (every? #(>= (:share %) 0.10) ret) "only ≥10% world-data share owners surface")
    (is (every? #(= :private (:type %)) ret) "candidates are PRIVATE holders")
    ;; G1: never an aggregate natural-person bucket (no person seizure list)
    (is (not-any? #(str/starts-with? (str (:owner %)) "agg.natural") ret)
        "G1: aggregate natural-person buckets are NEVER return-candidates")))

(deftest test-g1-no-person-dimension
  ;; G1: natural-person land is folded to an :owner/aggregate bucket; no parcel carries a
  ;; :person/* attr and the owner-type stays within the disclosed taxonomy.
  (let [r (res)]
    (is (every? #(contains? a/owner-types (:owner/type %)) (:parcels r))
        "every parcel owner-type is in the disclosed taxonomy")
    (is (some :owner/aggregate (:parcels r)) "natural-person land present as aggregate")
    (is (not-any? (fn [p] (some #(str/starts-with? (str (key %)) ":person") p)) (:parcels r))
        "no :person/* attr on any parcel")))

(deftest test-dedup-by-record-id
  ;; re-feeding the same parcel twice must collapse to one record (upsert by record-id).
  (let [data (load-seed)
        dup (update data :parcels #(vec (concat % %)))
        r (a/analyze dup)]
    (is (= (count (:parcels (res))) (count (:parcels r)))
        "duplicate parcels dedupe to the same count (record-id upsert)")))

(when (= *file* (System/getProperty "babashka.file"))
  (let [{:keys [fail error]} (run-tests 'jinushi.methods.test-analyze)]
    (System/exit (+ (or fail 0) (or error 0)))))
