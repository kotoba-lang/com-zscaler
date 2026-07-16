#!/usr/bin/env bb
;; tsubasa 翼 — autonomous fetch-leg tests (read-only, fail-open, member-refused).
;; Run:  bb --classpath 20-actors 20-actors/tsubasa/methods/test_fetch.cljc
(ns tsubasa.methods.test-fetch
  (:require [tsubasa.methods.fetch :as f]
            [clojure.test :refer [deftest is run-tests]]))

;; A stubbed fetch-fn stands in for the network — the test proves the ACTOR can fetch +
;; ingest autonomously (no operator, no key) without touching the network in CI.
(def ^:private public-payload
  [{"origin" "JFK" "destination" "NRT" "carrier" "UA" "fareMinor" 71000 "baggageMinor" 3500
    "co2Kg" 1040.0 "durationMin" 830 "cabin" "economy" "bookUrl" "https://united.com/b?aff=x"}
   {"origin" "JFK" "destination" "NRT" "carrier" "ZZ" "co2Kg" 0}          ; bad co2 → dropped (G4)
   {"origin" "JFK" "destination" "NRT" "carrier" "QQ" "commissionMinor" 9 ; poisoned → dropped (G1)
    "co2Kg" 900.0}])

(deftest autonomous-fetch-and-ingest-produces-authoritative-rows
  (let [r (f/fetch-and-ingest "https://example.org/fares.json"
                              {:as-of "2026-06-21" :fetch-fn (fn [_] public-payload)})]
    (is (= 1 (:accepted r)))                                  ; only the clean row
    (is (= 2 (count (:rejected r))))
    (is (= #{:nonpositive-co2 :forbidden-key} (set (map :reason (:rejected r)))))  ; G4 + G1 at ingest
    (let [row (first (:rows r))]
      (is (= :authoritative (:fare/sourcing row)))
      (is (= "https://example.org/fares.json" (:fare/source row)))        ; provenance
      (is (not (clojure.string/includes? (:fare/book-url row) "aff="))))))  ; affiliate-stripped (G1)

(deftest fetch-is-fail-open-on-dead-source
  ;; a nil fetch (network down / 404) degrades to an empty batch — never throws (no heartbeat block)
  (let [r (f/fetch-and-ingest "https://dead.example" {:fetch-fn (fn [_] nil)})]
    (is (= 0 (:accepted r)))
    (is (= [] (:rows r)))
    (is (= :no-payload (:note r)))))

(deftest non-sequential-payload-is-fail-open
  (let [r (f/fetch-and-ingest "https://x" {:fetch-fn (fn [_] {"error" "rate-limited"})})]
    (is (= 0 (:accepted r)))
    (is (= :no-payload (:note r)))))

#?(:clj
   (when (= *file* (System/getProperty "babashka.file"))
     (let [{:keys [fail error]} (run-tests 'tsubasa.methods.test-fetch)]
       (System/exit (if (zero? (+ fail error)) 0 1)))))
