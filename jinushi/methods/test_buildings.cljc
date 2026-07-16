(ns jinushi.methods.test-buildings
  "jinushi 地主 — building-ownership KG + company-linkage tests."
  (:require [clojure.test :refer [deftest is run-tests]]
            [clojure.string :as str]
            [clojure.java.io :as io]
            [jinushi.methods.buildings :as b]))

(def actor-dir (-> *file* io/file .getParentFile .getParentFile))
(def repo-root (-> actor-dir .getParentFile .getParentFile))
(def data-dir (io/file repo-root "80-data" "jinushi-land"))
(defn snap [] (b/load-snapshot data-dir))

(deftest test-analyze-by-owner
  (let [a (b/analyze (snap))
        c (:concentration a)]
    (is (pos? (:owner-count c)) "owners present")
    (is (pos? (:building-count c)) "buildings present")
    (is (seq (:top-by-buildings c)) "top owners ranked")
    (is (apply >= (map :buildings (:top-by-buildings c))) "top sorted by #buildings desc")))

(deftest test-company-linkage
  ;; the point of this layer: owner legal entities carry LEI + Wikidata QID → join to the corp KGs.
  (let [links (:company-links (b/analyze (snap)))]
    (is (seq links) "some owners have an LEI company-link")
    (is (every? :lei links) "every link carries an LEI")
    (is (every? :wikidata links) "every link carries the owner Wikidata QID")))

(deftest test-datoms-ground-and-edges
  (let [s (snap) o (b/datoms s (b/analyze s) 1)]
    (is (re-find #"\[:owner\.Q\d+ :owner\.org/wikidata" o) "owner-org nodes emitted")
    (is (re-find #"\[:owner\.Q\d+ :owner\.org/lei" o) "owner LEI emitted (company join key)")
    (is (re-find #"\[:building\.Q\d+ :building/owner :owner\.Q\d+" o) "building→owner ownership edge emitted")
    (doseq [line (str/split-lines o)
            :when (and (str/includes? line ":jinushi/building-") (str/includes? line ":derived"))]
      (is (str/includes? line ":bond/is-transient") "concentration is transient (G2)"))))

(deftest test-gate-is-provenance-not-person-exclusion
  ;; reframed gate: PUBLIC-RECORD + SYMMETRIC, not 'exclude natural persons'.
  (let [{:keys [provenance]} (snap)]
    (is (true? (:public-record provenance)) "P1 public-record provenance asserted")
    (is (= :symmetric (:reciprocity provenance)) "P2 reciprocal/symmetric (相互監視 affirmed)")
    ;; natural-person ownership is representable under the same gate (type is just a field)
    (is (every? #(contains? #{:org :natural-person} (:type (val %))) (:owners (snap)))
        "owner type is a public-record attribute, never a person-exclusion")))

(deftest test-floor-concentration
  ;; vertical-scale (ビルのフロア) 取-concentration: owners ranked by total floors controlled.
  (let [c (:concentration (b/analyze (snap)))]
    (is (pos? (:buildings-with-floors c)) "some buildings carry floor counts")
    (is (seq (:top-by-floors c)) "top owners by floors present")
    (is (apply >= (map :floors (:top-by-floors c))) "sorted by total floors desc")
    (is (every? #(pos? (:floors %)) (:top-by-floors c)) "floor leaders actually control floors")))

(deftest test-datoms-floor-concentration
  (let [s (snap) o (b/datoms s (b/analyze s) 1)]
    (is (re-find #":jinushi/top-floors-owner :owner\.Q\d+" o) "vertical floor-concentration emitted")
    (doseq [line (str/split-lines o)
            :when (and (str/includes? line ":jinushi/top-floors-owner"))]
      (is (str/includes? line ":bond/is-transient") "floor concentration is transient (G2)"))))

(deftest test-deterministic
  (let [s (snap) a (b/analyze s)]
    (is (= (b/datoms s a 5) (b/datoms s a 5)) "building-KG emit is deterministic")))

(when (= *file* (System/getProperty "babashka.file"))
  (let [{:keys [fail error]} (run-tests 'jinushi.methods.test-buildings)]
    (System/exit (+ (or fail 0) (or error 0)))))
