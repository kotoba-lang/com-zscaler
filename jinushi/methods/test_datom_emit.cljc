(ns jinushi.methods.test-datom-emit
  "jinushi 地主 — Datom-emit tests (canonical EAVT, ADR-2605312345)."
  (:require [clojure.test :refer [deftest is run-tests]]
            [clojure.string :as str]
            [clojure.java.io :as io]
            [jinushi.methods.analyze :as a]
            [jinushi.methods.datom-emit :as d]))

(def actor-dir (-> *file* io/file .getParentFile .getParentFile))
(def seed (io/file actor-dir "data" "seed-parcels.kotoba.edn"))
(defn data [] (a/load-file* seed))
(defn out [] (let [dt (data)] (d/emit dt (a/analyze dt) 1)))

(deftest test-g1-no-person-or-worker-dimension
  (let [o (out)]
    (is (not (str/includes? o ":person")) "G1: no :person dimension in the Datom log")
    (is (not (str/includes? o ":worker")) "G1: no :worker dimension in the Datom log")))

(deftest test-ground-add-datoms
  (let [o (out)]
    (is (str/includes? o "[:owner.o.gov.jp.mlit :owner/name \"国土交通省\" 1 :add]")
        "owner node emitted as a ground :add datom")
    (is (str/includes? o ":parcel/area-m2") "parcel area emitted")
    (is (str/includes? o ":parcel/centroid-lat") "coarse centroid emitted as scalar")
    ;; centroid must be coarse (no high-precision dwelling fix): the seed uses ≤2 decimals.
    (is (not (re-find #":parcel/centroid-lat -?\d+\.\d{4,}" o))
        "G1: centroids are coarse region centroids, never a precise dwelling fix")))

(deftest test-derived-flagged-transient
  (let [o (out)]
    (is (str/includes? o ":jinushi/hhi") "concentration HHI emitted")
    (is (str/includes? o ":jinushi/world-coverage-frac") "acquisition coverage emitted")
    ;; every :jinushi/* derived line must carry the transient flag (G2: aggregate, not a fact)
    (doseq [line (str/split-lines o)
            :when (and (str/includes? line ":jinushi/") (str/includes? line ":derived"))]
      (is (str/includes? line ":bond/is-transient")
          (str "derived line missing transient flag: " line)))))

(deftest test-return-routed-not-written-back
  (let [o (out)]
    (is (str/includes? o ":jinushi/return-candidate")
        "commons-return candidate routed as a derived/advisory datom")
    ;; G3: jinushi never asserts a transfer/mint/donation on the LandRegistry.
    (is (not (str/includes? o ":land/transfer")) "G3: no transfer asserted")
    (is (not (str/includes? o ":land/mint")) "G3: no mint asserted")))

(deftest test-deterministic
  (let [dt (data)
        r (a/analyze dt)]
    (is (= (d/emit dt r 7) (d/emit dt r 7)) "emit is deterministic (byte-identical on re-emit)")))

(when (= *file* (System/getProperty "babashka.file"))
  (let [{:keys [fail error]} (run-tests 'jinushi.methods.test-datom-emit)]
    (System/exit (+ (or fail 0) (or error 0)))))
