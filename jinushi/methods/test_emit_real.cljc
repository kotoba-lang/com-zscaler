(ns jinushi.methods.test-emit-real
  "jinushi 地主 — real-acquisition Datom-log emission tests."
  (:require [clojure.test :refer [deftest is run-tests]]
            [clojure.string :as str]
            [clojure.java.io :as io]
            [jinushi.methods.ingest :as ingest]
            [jinushi.methods.emit-real :as er]))

(def actor-dir (-> *file* io/file .getParentFile .getParentFile))
(def repo-root (-> actor-dir .getParentFile .getParentFile))
(def data-dir (io/file repo-root "80-data" "jinushi-land"))
(defn areas [] (ingest/load-country-areas data-dir))
(defn log [] (er/real-datom-log (ingest/load-all-snapshots data-dir) (areas) 1))

(deftest test-g1-no-person-or-worker
  (let [o (log)]
    (is (not (str/includes? o ":person")) "G1: real Datom log carries no :person dimension")
    (is (not (str/includes? o ":worker")) "G1: real Datom log carries no :worker dimension")))

(deftest test-ground-and-derived
  (let [o (log)]
    (is (re-find #"\[:owner\.[^ ]+ :owner/name" o) "ground public-owner :add datoms present")
    (is (re-find #"\[:parcel\.[^ ]+ :parcel/area-m2" o) "ground parcel area datoms present")
    (is (str/includes? o ":jinushi/world-coverage-frac") "derived world-coverage emitted")
    ;; every :jinushi/* derived line is transient (G2 — aggregate, not a fact)
    (doseq [line (str/split-lines o)
            :when (and (str/includes? line ":jinushi/") (str/includes? line ":derived"))]
      (is (str/includes? line ":bond/is-transient") (str "derived line missing transient flag: " line)))))

(deftest test-only-counting-sources
  ;; the real log is built from the counting dataset → national parks only (nature reserves
  ;; are observed-only and must not appear).
  (let [o (log)]
    (is (str/includes? o "national-parks") "national-park owners present in the real log")
    (is (not (str/includes? o "nature-reserves")) "observed-only nature reserves excluded (no double-count)")))

(deftest test-deterministic
  (let [snaps (ingest/load-all-snapshots data-dir) a (areas)]
    (is (= (er/real-datom-log snaps a 7) (er/real-datom-log snaps a 7)) "real Datom-log emit is deterministic")))

(when (= *file* (System/getProperty "babashka.file"))
  (let [{:keys [fail error]} (run-tests 'jinushi.methods.test-emit-real)]
    (System/exit (+ (or fail 0) (or error 0)))))
