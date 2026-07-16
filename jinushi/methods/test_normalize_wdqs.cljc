(ns jinushi.methods.test-normalize-wdqs
  "jinushi 地主 — WDQS raw → normalized snapshot processing tests."
  (:require [clojure.test :refer [deftest is run-tests]]
            [jinushi.methods.normalize-wdqs :as nw]))

;; a minimal WDQS-shaped stream: km² row, hectare row, feddan row, an UNKNOWN-unit row, a
;; NON-POSITIVE row, a non-ISO cc row, plus a trailing partial + the server-timeout trace WDQS
;; appends when it hits its 60 s cap.
(def raw
  (str "{ \"results\" : { \"bindings\" : [\n"
       "{ \"area\":{\"type\":\"literal\",\"value\":\"2\"}, \"unit\":{\"value\":\"http://www.wikidata.org/entity/Q712226\"}, \"cc\":{\"value\":\"JP\"} },\n"
       "{ \"area\":{\"value\":\"100\"}, \"unit\":{\"value\":\"http://www.wikidata.org/entity/Q35852\"}, \"cc\":{\"value\":\"JP\"} },\n"
       "{ \"area\":{\"value\":\"1\"}, \"unit\":{\"value\":\"http://www.wikidata.org/entity/Q1399890\"}, \"cc\":{\"value\":\"EG\"} },\n"
       "{ \"area\":{\"value\":\"5\"}, \"unit\":{\"value\":\"http://www.wikidata.org/entity/Q99999\"}, \"cc\":{\"value\":\"DE\"} },\n"
       "{ \"area\":{\"value\":\"0\"}, \"unit\":{\"value\":\"http://www.wikidata.org/entity/Q712226\"}, \"cc\":{\"value\":\"FR\"} },\n"
       "{ \"area\":{\"value\":\"3\"}, \"unit\":{\"value\":\"http://www.wikidata.org/entity/Q712226\"}, \"cc\":{\"value\":\"badcc\"} },\n"
       "{ \"area\":{\"value\":\"9\"}, \"unit\":{\"value\":\"http://www.wikidata.org/entity/Q712\n"  ;; truncated partial
       "SPARQL-QUERY: queryStr=... server timeout trace ...\n"))

(deftest test-unit-conversion
  (let [{:keys [records]} (nw/normalize [raw])
        by (group-by :cc records)]
    (is (= 2.0e6 (:area-m2 (first (filter #(= "Q712226" (:unit-src %)) (by "JP"))))) "km² → 1e6 m²")
    (is (= 1.0e6 (:area-m2 (first (filter #(= "Q35852" (:unit-src %)) (by "JP"))))) "100 hectare → 1e6 m²")
    (is (= 4200.8334 (:area-m2 (first (by "EG")))) "1 feddan → 4200.8334 m²")))

(deftest test-drops-are-honest
  (let [n (nw/normalize [raw])]
    (is (= 1 (:dropped-unknown-unit n)) "unknown-unit row (Q99999) dropped + counted")
    (is (= 1 (:dropped-nonpositive n)) "non-positive (0) area dropped + counted")
    ;; badcc (non-ISO) excluded; truncated partial + SPARQL trace ignored
    (is (= #{"JP" "EG"} (set (map :cc (:records n)))) "only ISO-2, positive, known-unit rows survive")))

(deftest test-salvage-ignores-trace
  (is (= 6 (count (nw/parse-triples raw))) "6 complete triples parsed (partial 7th + trace ignored)"))

(deftest test-dedup-and-sorted
  (let [{:keys [records]} (nw/normalize [raw raw])]   ;; same stream twice
    (is (apply distinct? records) "identical records dedupe across inputs")
    (is (= records (sort-by (juxt :cc :area-m2) records)) "records sorted by (cc, area)")))

(deftest test-unit-map-complete
  (is (= (set (keys nw/unit->m2)) (set (keys nw/unit-label))) "every unit has both a factor and a label")
  (is (every? pos? (vals nw/unit->m2)) "every unit factor is positive"))

(when (= *file* (System/getProperty "babashka.file"))
  (let [{:keys [fail error]} (run-tests 'jinushi.methods.test-normalize-wdqs)]
    (System/exit (+ (or fail 0) (or error 0)))))
