#!/usr/bin/env bb
;; tsubasa 翼 — live-ingest adapter tests (charter bounds enforced).
;; Run:  bb --classpath 20-actors 20-actors/tsubasa/methods/test_ingest.cljc
(ns tsubasa.methods.test-ingest
  (:require [tsubasa.methods.ingest :as ing]
            [tsubasa.methods.analyze :as a]
            [clojure.string :as str]
            [clojure.test :refer [deftest is run-tests]]))

(def ^:private clean-raw
  {:origin "JFK" :destination "NRT" :carrier "UA" :fareMinor 71000 :baggageMinor 3500
   :co2Kg 1040.0 :durationMin 830 :cabin "economy"
   :bookUrl "https://www.united.com/book?o=JFK&d=NRT&aff=skyscan&utm_source=x"})

(deftest clean-source-allowed-paid-terminal-refused   ; G8 bound
  (is (= :public (ing/assert-clean-source :public)))
  (is (= :member-principal (ing/assert-clean-source :member-principal)))
  (is (thrown? #?(:clj clojure.lang.ExceptionInfo :cljs js/Error)
               (ing/assert-clean-source :paid-terminal)))
  (is (thrown? #?(:clj clojure.lang.ExceptionInfo :cljs js/Error)
               (ing/assert-clean-source :amadeus))))

(deftest normalize-clean-fare-is-authoritative-and-affiliate-stripped
  (let [r (ing/normalize-fare clean-raw "https://www.united.com" "2026-06-21")]
    (is (= :fare (:type r)))
    (is (= :authoritative (:fare/sourcing r)))            ; live = authoritative
    (is (= "https://www.united.com" (:fare/source r)))    ; provenance cited (G8)
    (is (= "2026-06-21" (:fare/ingested-at r)))
    (is (= 1040.0 (:fare/co2-kg r)))                       ; G4 carried
    ;; G1: aff + utm_ stripped from the airline's own link
    (is (not (str/includes? (:fare/book-url r) "aff=")))
    (is (not (str/includes? (:fare/book-url r) "utm_")))
    (is (str/includes? (:fare/book-url r) "o=JFK"))))      ; functional params kept

(deftest g1-g5-poisoned-rows-rejected
  ;; a commission/affiliate/searcher-bearing row is DROPPED, never ingested
  (is (= :forbidden-key (:reject (ing/normalize-fare (assoc clean-raw :commissionMinor 500) "s" "t"))))
  (is (= :forbidden-key (:reject (ing/normalize-fare (assoc clean-raw :affiliateLink "x") "s" "t"))))
  (is (= :forbidden-key (:reject (ing/normalize-fare (assoc clean-raw :searcherId "u1") "s" "t")))))

(deftest g4-emissions-mandatory
  (is (= :no-co2 (:reject (ing/normalize-fare (dissoc clean-raw :co2Kg) "s" "t"))))
  (is (= :nonpositive-co2 (:reject (ing/normalize-fare (assoc clean-raw :co2Kg 0) "s" "t")))))

(deftest g8-provenance-mandatory
  (is (= :no-source (:reject (ing/normalize-fare clean-raw "" "t")))))

(deftest missing-od-rejected
  (is (= :missing-od-carrier (:reject (ing/normalize-fare (dissoc clean-raw :carrier) "s" "t")))))

(deftest ingest-batch-fail-open-and-feeds-analyze
  (let [payload [clean-raw
                 (assoc clean-raw :carrier "XX" :commissionMinor 9)   ; poisoned → dropped
                 (-> clean-raw (assoc :carrier "DL") (dissoc :co2Kg))  ; no co2 → dropped
                 (assoc clean-raw :carrier "B6" :co2Kg 1100.0)]        ; clean
        {:keys [rows accepted rejected]} (ing/ingest payload {:source "https://x" :as-of "t" :source-kind :public})]
    (is (= 2 accepted))
    (is (= 2 (count rejected)))
    (is (= #{:forbidden-key :no-co2} (set (map :reason rejected))))
    (is (every? #(= :authoritative (:fare/sourcing %)) rows))
    ;; ingested rows feed the existing analyze pipeline unchanged
    (let [an (a/analyze rows)
          jfk-nrt (first (filter #(= "JFK-NRT" (get % "route")) (get an "routes")))]
      (is (= 2 (get jfk-nrt "carrier_count"))))))

(deftest ingest-refuses-paid-terminal-batch   ; G8 at the batch level
  (is (thrown? #?(:clj clojure.lang.ExceptionInfo :cljs js/Error)
               (ing/ingest [clean-raw] {:source "x" :as-of "t" :source-kind :paid-terminal}))))

(deftest no-forbidden-attr-in-ingested-rows
  (let [{:keys [rows]} (ing/ingest [clean-raw] {:source "x" :as-of "t" :source-kind :public})
        attrs (->> rows (mapcat keys) (map (comp str/lower-case name)) set)]
    (doseq [bad ["commission" "affiliate" "merchant" "searcher" "person" "urgency"]]
      (is (not-any? #(str/includes? % bad) attrs)))))

#?(:clj
   (when (= *file* (System/getProperty "babashka.file"))
     (let [{:keys [fail error]} (run-tests 'tsubasa.methods.test-ingest)]
       (System/exit (if (zero? (+ fail error)) 0 1)))))
