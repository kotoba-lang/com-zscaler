#!/usr/bin/env bb
;; tsubasa 翼 — OpenFlights real-source parser tests (CSV → coverage rows).
;; Run:  bb --classpath 20-actors 20-actors/tsubasa/methods/test_openflights.cljc
(ns tsubasa.methods.test-openflights
  (:require [tsubasa.methods.openflights :as of]
            [tsubasa.methods.analyze :as a]
            [clojure.test :refer [deftest is run-tests]]))

;; tiny inline fixtures in the real airports.dat / airlines.dat shape (no network)
(def ^:private airports-csv
  (str "1,\"Tokyo Haneda\",\"Tokyo\",\"Japan\",\"HND\",\"RJTT\",35.55,139.78,35,9,\"N\",\"Asia/Tokyo\",\"airport\",\"OurAirports\"\n"
       "2,\"Heathrow\",\"London\",\"United Kingdom\",\"LHR\",\"EGLL\",51.47,-0.46,83,0,\"E\",\"Europe/London\",\"airport\",\"OurAirports\"\n"
       "3,\"No IATA Field\",\"Nowhere\",\"Narnia\",\"\\N\",\"XXXX\",0,0,0,0,\"U\",\"\\N\",\"airport\",\"OurAirports\"\n"))

(def ^:private airlines-csv
  (str "1,\"All Nippon Airways\",\\N,\"NH\",\"ANA\",\"ALL NIPPON\",\"Japan\",\"Y\"\n"
       "2,\"Defunct Air\",\\N,\"DD\",\"DEF\",\"DEAD\",\"Nowhere\",\"N\"\n"
       "3,\"No Code Air\",\\N,\"\\N\",\"NOC\",\"NONE\",\"Nowhere\",\"Y\"\n"))

(deftest csv-line-handles-quotes-and-commas
  (is (= ["1" "Tokyo, Big" "x"] (of/parse-csv-line "1,\"Tokyo, Big\",x"))))

(deftest airports-parsed-with-region-and-authoritative
  (let [aps (of/parse-airports airports-csv)]
    (is (= 2 (count aps)))                                   ; the \N-IATA row dropped
    (let [hnd (first (filter #(= "HND" (:airport/iata %)) aps))]
      (is (= :east-asia (:airport/region hnd)))              ; Japan → :east-asia
      (is (= :authoritative (:airport/sourcing hnd)))
      (is (= "openflights:airports.dat" (:airport/source hnd))))))

(deftest airlines-only-active-with-real-iata
  (let [cas (of/parse-airlines airlines-csv)]
    (is (= 1 (count cas)))                                   ; defunct (N) + no-code (\N) dropped
    (is (= "NH" (:carrier/iata (first cas))))
    (is (= :authoritative (:carrier/sourcing (first cas))))))

(deftest unknown-country-is-honest-not-guessed
  (let [row (first (of/parse-airports
                    "9,\"Mystery\",\"X\",\"Atlantis\",\"MYS\",\"ZZZZ\",0,0,0,0,\"N\",\"\\N\",\"airport\",\"X\"\n"))]
    (is (= :unknown (:airport/region row)))))

(deftest merge-coverage-dedups-and-feeds-analyze
  (let [seed [{:type :airport :airport/iata "HND" :airport/region :east-asia}  ; already in seed
              {:type :carrier :carrier/iata "NH"}]
        merged (of/merge-coverage seed (of/parse-airports airports-csv) (of/parse-airlines airlines-csv))
        iatas (map :airport/iata (filter #(= :airport (:type %)) merged))]
    ;; LHR added from OpenFlights; HND not duplicated (seed wins)
    (is (= 1 (count (filter #{"HND"} iatas))))
    (is (some #{"LHR"} iatas))
    ;; merged rows still drive the coverage report (real source raised coverage)
    (let [cov (a/coverage merged)]
      (is (>= (get cov "airports_have") 2)))))

#?(:clj
   (when (= *file* (System/getProperty "babashka.file"))
     (let [{:keys [fail error]} (run-tests 'tsubasa.methods.test-openflights)]
       (System/exit (if (zero? (+ fail error)) 0 1)))))
