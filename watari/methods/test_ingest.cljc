(ns watari.methods.test-ingest
  "test_ingest.py — watari 渡り offline AIS/ADS-B normalizer tests. ADR-2606041827.
  1:1 Clojure port of methods/test_ingest.py (pytest assert → clojure.test/is).

  Covers offline normalization (public-broadcast → :craft/:craft.fix records, all
  :representative) AND the G7 outward gate: a live network fetch is REFUSED unless the operator
  attestation env var is set. Fixtures load via *file*-relative paths behind #?(:clj …)."
  (:require [clojure.test :refer [deftest is run-tests]]
            [clojure.set]
            #?(:clj [clojure.java.io :as io])
            [watari.methods.ingest :as ingest]))

#?(:clj (def ^:private batch-path
          (-> *file* io/file .getAbsoluteFile .getParentFile .getParentFile
              (io/file "data" "ingest" "sample-batch.json"))))

#?(:clj (defn- batch [] (ingest/load-json batch-path)))

(deftest test-normalize-emits-vessel-and-aircraft-craft
  (let [[craft fixes] (ingest/normalize (batch))
        kinds (set (map #(get % ":craft/kind") (vals craft)))]
    (is (and (contains? kinds ":vessel") (contains? kinds ":aircraft")))
    (is (>= (count fixes) (count craft)))))   ;; at least one fix per craft

(deftest test-normalized-records-are-representative
  (let [[craft fixes] (ingest/normalize (batch))]
    (is (every? #(= ":representative" (get % ":craft/sourcing")) (vals craft)))
    (is (every? #(= ":representative" (get % ":craft.fix/sourcing")) fixes))))

(deftest test-fix-carries-source-tag
  (let [[_ fixes] (ingest/normalize (batch))
        sources (set (map #(get % ":craft.fix/source") fixes))]
    (is (clojure.set/subset? sources #{":ais" ":adsb"}))))   ;; only public-broadcast sources

(deftest test-g4-no-person-fields-in-normalized-output
  (let [[craft fixes] (ingest/normalize (batch))]
    (doseq [rec (concat (vals craft) fixes)]
      (doseq [k (keys rec)]
        (is (not (.contains ^String k ":person")))))))   ;; craft, never a person (G4)

(deftest test-g1-drops-military-callsign-aircraft
  ;; "RCH" = USAF Air Mobility Command "Reach" — a well-known military callsign prefix.
  (let [[craft fix] (ingest/aircraft-fix
                      ["ae1234" "RCH123  " "United States" 0 0 -77.0 38.9 3000.0 false
                       400.0 90.0 0.0 nil 3100.0 nil false 0]
                      "2026-06-04T12:00:00Z")]
    (is (nil? craft))
    (is (nil? fix))))

(deftest test-g1-drops-military-ship-name
  (let [[craft fix] (ingest/vessel-fix
                     {"MMSI" 111222333 "ShipName" "USS Constitution"
                      "Latitude" 42.37 "Longitude" -71.05 "Sog" 0.0 "Cog" 0.0}
                     "2026-06-04T12:00:00Z")]
    (is (nil? craft))
    (is (nil? fix))))

(deftest test-g1-allows-civilian-callsign-and-shipname
  ;; the existing sample fixtures (ANA12/DLH715 aircraft, EVER GIVEN/MSC GULSUN vessels)
  ;; must still normalize -- the G1 screen must not over-block civilian traffic.
  (let [[craft _fixes] (ingest/normalize (batch))]
    (is (>= (count craft) 4) "all 4 sample civilian craft still pass through the G1 screen")))

(deftest test-g7-live-fetch-refused-without-operator-gate
  ;; default mode (no WATARI_OPERATOR_GATE) must REFUSE --live
  (is (thrown? #?(:clj Exception :cljs js/Error) (ingest/main ["ingest.py" "--live"]))
      "--live must refuse without the operator gate"))

#?(:clj
   (do
     (defn -main [& _] (run-tests 'watari.methods.test-ingest))
     (when (= *file* (System/getProperty "babashka.file")) (-main))))
