(ns jinushi.methods.test-company-link
  "jinushi 地主 — authoritative GLEIF company-linkage tests."
  (:require [clojure.test :refer [deftest is run-tests]]
            [clojure.string :as str]
            [clojure.java.io :as io]
            [jinushi.methods.buildings :as b]
            [jinushi.methods.company-link :as cl]))

(def actor-dir (-> *file* io/file .getParentFile .getParentFile))
(def repo-root (-> actor-dir .getParentFile .getParentFile))
(def data-dir (io/file repo-root "80-data" "jinushi-land"))
(defn bsnap [] (b/load-snapshot data-dir))
(defn gleif [] (cl/load-gleif data-dir))

(deftest test-links-authoritative
  (let [links (cl/link-records (bsnap) (gleif))]
    (is (seq links) "some building owners resolve to GLEIF companies")
    (is (every? :lei links) "every link carries an LEI")
    (is (every? :gleif-name links) "every link carries an authoritative GLEIF legal name")
    (is (every? #(get-in % [:joins :kabuto]) links) "every link carries the kabuto/uchiwake/kanjō join key (LEI)")
    (is (apply >= (map :buildings links)) "links sorted by #buildings desc")))

(deftest test-coverage
  (let [c (cl/coverage (bsnap) (gleif))]
    (is (pos? (:owners-gleif-linked c)) "owners GLEIF-linked")
    (is (<= (:owners-gleif-linked c) (:owners-with-lei c)) "linked ≤ owners-with-lei")
    (is (<= (:owners-with-lei c) (:owners-total c)) "with-lei ≤ total")
    (is (pos? (:buildings-linked c)) "buildings linked")
    (is (seq (:by-jurisdiction c)) "jurisdiction breakdown present")))

(deftest test-datoms-emit-gleif-facts
  (let [o (cl/datoms (bsnap) (gleif) 1)]
    (is (re-find #"\[:owner\.Q\d+ :owner\.org/gleif-name" o) "GLEIF legal name emitted")
    (is (re-find #"\[:owner\.Q\d+ :owner\.org/gleif-lei" o) "GLEIF LEI emitted")
    (is (re-find #"\[:owner\.Q\d+ :owner\.org/jurisdiction" o) "jurisdiction emitted")
    (is (re-find #"\[:owner\.Q\d+ :link/corp-lei" o) "cross-actor corp bridge edge emitted")
    (is (not (str/includes? o ":person")) "no person dimension")))

(deftest test-gleif-is-legal-entities-only
  ;; GLEIF registers legal persons; every resolved company has a legal name + jurisdiction.
  (let [g (gleif)]
    (is (every? :legal-name (vals g)) "every GLEIF row has a legal name")
    (is (every? :jurisdiction (vals g)) "every GLEIF row has a jurisdiction")))

(deftest test-deterministic
  (let [bs (bsnap) g (gleif)]
    (is (= (cl/datoms bs g 3) (cl/datoms bs g 3)) "company-link emit is deterministic")))

(when (= *file* (System/getProperty "babashka.file"))
  (let [{:keys [fail error]} (run-tests 'jinushi.methods.test-company-link)]
    (System/exit (+ (or fail 0) (or error 0)))))
