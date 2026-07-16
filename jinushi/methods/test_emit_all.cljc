(ns jinushi.methods.test-emit-all
  "jinushi 地主 — unified canonical Datom log tests."
  (:require [clojure.test :refer [deftest is run-tests]]
            [clojure.string :as str]
            [clojure.java.io :as io]
            [jinushi.methods.emit-all :as ea]))

(def actor-dir (-> *file* io/file .getParentFile .getParentFile))
(def repo-root (-> actor-dir .getParentFile .getParentFile))
(def data-dir (io/file repo-root "80-data" "jinushi-land"))
(defn log [] (ea/build data-dir 1))

(deftest test-all-sources-fused
  (let [o (log)]
    (is (re-find #":parcel/area-m2" o) "LAND parcels present")
    (is (re-find #":building/owner" o) "BUILDING ownership present")
    (is (re-find #":owner.org/gleif-name" o) "GLEIF company linkage present")
    (is (re-find #"parcel.us-ny" o) "NYC PLUTO parcels present")
    (is (re-find #"building.osm" o) "OSM building stock present")
    (is (re-find #":value/appt-median-eur-m2" o) "DVF value aggregates present")))

(deftest test-source-tagged
  (let [o (log)]
    (is (re-find #"source=dvf" o) "datoms carry their source (for confidence weighting)")
    (is (re-find #"source=gleif authoritative" o) "GLEIF section tagged authoritative")))

(deftest test-g1-no-person
  (is (not (str/includes? (log) ":person")) "G1: no person dimension in the unified log")
  (is (not (str/includes? (log) ":worker")) "no worker dimension"))

(deftest test-well-formed-and-deterministic
  (let [o (log)]
    (is (str/starts-with? (str/triml o) ";;") "header comment first")
    (is (str/includes? o "[\n") "opens the datom vector")
    (is (str/ends-with? (str/trim o) "]") "closes the datom vector")
    (is (= o (ea/build data-dir 1)) "deterministic")))

(when (= *file* (System/getProperty "babashka.file"))
  (let [{:keys [fail error]} (run-tests 'jinushi.methods.test-emit-all)]
    (System/exit (+ (or fail 0) (or error 0)))))
