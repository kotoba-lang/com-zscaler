(ns jinushi.methods.test-coverage
  "jinushi 地主 — world acquisition-coverage report + ingest-worklist tests."
  (:require [clojure.test :refer [deftest is run-tests]]
            [clojure.string :as str]
            [clojure.java.io :as io]
            [jinushi.methods.analyze :as a]
            [jinushi.methods.coverage :as c]))

(def actor-dir (-> *file* io/file .getParentFile .getParentFile))
(def seed (io/file actor-dir "data" "seed-parcels.kotoba.edn"))
(defn res [] (a/analyze (a/load-file* seed)))

(deftest test-worklist-self-pruning
  (let [r (res)
        wl (c/worklist r)
        wl-countries (set (map :country wl))
        touched (set (keys (:by-country r)))]
    ;; a touched country (e.g. JP, US) must NOT appear in the worklist (self-pruning)
    (is (empty? (clojure.set/intersection wl-countries touched))
        "covered countries are pruned from the ingest worklist")
    ;; known-but-untouched countries (RU/CN/CA/IN) DO appear
    (is (contains? wl-countries "RU") "RU (known, zero parcels) is on the worklist")
    (is (contains? wl-countries "CN") "CN (known, zero parcels) is on the worklist")))

(deftest test-worklist-is-jurisdictions-not-targets
  ;; G1: the worklist names COUNTRIES, never parcels/owners/persons.
  (let [wl (c/worklist (res))]
    (is (every? #(= 2 (count (:country %))) wl) "each worklist item is an ISO-2 country code")
    (is (every? #(contains? % :land-area-km2) wl) "each carries national land area, not a parcel id")
    (is (not-any? #(or (contains? % :parcel/id) (contains? % :owner/key)) wl)
        "G1: no parcel/owner identifiers in the worklist")))

(deftest test-report-renders-coverage
  (let [txt (c/render (res))]
    (is (str/includes? txt "coverage") "report mentions coverage")
    (is (str/includes? txt "取得ワークリスト") "report includes the ingest worklist section")
    (is (str/includes? txt "RETURN-to-commons") "report includes the commons-return section")
    (is (not (str/includes? txt ":person")) "G1: report carries no person dimension")))

(deftest test-coverage-numbers-present
  (let [rep (c/report (res))]
    (is (= 6 (:countries-touched (:coverage rep))))
    (is (vector? (:worklist rep)))
    (is (pos? (count (:worklist rep))) "seed leaves known countries to ingest")))

(when (= *file* (System/getProperty "babashka.file"))
  (let [{:keys [fail error]} (run-tests 'jinushi.methods.test-coverage)]
    (System/exit (+ (or fail 0) (or error 0)))))
