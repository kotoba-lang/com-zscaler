(ns shirabe.tests.test-kotoba
  "shirabe — kotoba Datomic write-path tests ([:db/add] shape / G2 / G6 / commit-DAG). kotoba-clj."
  (:require [clojure.test :refer [deftest is]]
            [clojure.string :as str]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [shirabe.methods.session :as session]
            [shirabe.methods.kotoba :as kotoba]))

(defn- unblob
  "The fixture is stored as Datomic/Datascript tx-data (see ../schema.edn); non-scalar
  attrs are pr-str'd blobs. Parse back to the original nested value."
  [v]
  (if (string? v)
    (try (let [parsed (edn/read-string v)] (if (coll? parsed) parsed v))
         (catch Exception _ v))
    v))

(defn- reconstitute-entity
  "Reconstitute the fixture's original bare map ({:question .. :asof .. :search ..
  :answer .. :live-reverify ..}) from its tx-data entity, so downstream key lookups
  (:question fx / :asof fx / (get-in fx [:search \"*\"])) keep working unchanged."
  [tx-data]
  (into {} (map (fn [[k v]] [(keyword (name k)) (unblob v)]))
        (dissoc (first tx-data) :db/id)))

(def fx (reconstitute-entity (edn/read-string (slurp (io/resource "shirabe/data/fixtures/shimada-aoyama.kotoba.edn")))))
(defn fetcher [_] (get-in fx [:search "*"]))
(def infer (with-meta (fn [_] (:answer fx)) {:model-id "fixture:gemma4@127.0.0.1:11434"}))
(def s (session/research (:question fx) fetcher infer (:asof fx)))

(deftest datoms-shape
  (let [ds (kotoba/session-datoms s)]
    (is (every? #(= :db/add (first %)) ds) "append-only :db/add tx-data (kotoba Datomic)")
    (is (some (fn [[_ _ a _]] (= a :shirabe.session/question)) ds))
    (is (some (fn [[_ _ a _]] (= a :shirabe.src/url)) ds))
    (is (not (some (fn [[_ _ a _]] (= a :shirabe.session/member)) ds)) "G6: no identity bound unsigned")))

(deftest member-bound-when-signed
  (let [m "did:web:etzhayyim.com:member:abc"
        ds (kotoba/session-datoms s m)]
    (is (some (fn [[_ _ a v]] (and (= a :shirabe.session/member) (= v m))) ds) "member bound when signed (G6)")))

(deftest g2-rejects-non-murakumo-model
  (let [bad (assoc-in s [:result :model] "gpt-4@api.openai.com:443")]
    (is (thrown? Exception (kotoba/session-datoms bad)) "persisting a non-Murakumo model host raises (G2)")))

(deftest commit-dag-roundtrip
  (let [tmp (str (System/getProperty "java.io.tmpdir") "/shirabe-test-" (Math/abs (hash s)) ".edn")
        _ (io/delete-file tmp true)
        ds (kotoba/session-datoms s)
        cid (kotoba/append-tx (kotoba/make-tx ds {:tx-id 1 :as-of 1 :prev-cid ""}) tmp)]
    (is (str/starts-with? cid "b") "tx content-addressed")
    (is (= cid (kotoba/head-cid tmp)) "head is the appended tx")
    (is (true? (kotoba/verify-chain tmp)) "commit-DAG verifies (tamper-evident)")
    (io/delete-file tmp true)))
