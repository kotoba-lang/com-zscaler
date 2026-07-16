(ns itonami.tests.test-ingest
  "itonami 営み — R3 SCADA/OT scan-cycle ingest tests (ADR-2606082300).
  1:1 Clojure port of tests/test_ingest.py (pytest → clojure.test)."
  (:require [clojure.test :refer [deftest is run-tests]]
            [clojure.string :as str]
            #?(:clj [clojure.java.io :as io])
            [itonami.methods.analyze :as analyze]
            [itonami.methods.ingest :as ingest]))

(def ^:private actor-dir (-> *file* io/file .getParentFile .getParentFile))
(def ^:private stream (io/file actor-dir "data" "seed-scancycle-stream.kotoba.edn"))
(def ^:private ops (io/file actor-dir "data" "seed-factory-ops.kotoba.edn"))

(defn- datoms [] (ingest/parse-scan-datoms (slurp stream)))

(deftest test-parse-scan-datoms
  (let [d (datoms)]
    (is (= (* 12 7) (count d)) (str "expected 12 reports × 7 attrs, got " (count d)))
    (doseq [dm d]
      (is (str/starts-with? (nth dm 1) ":scan/"))
      (is (= ":add" (nth dm 4))))))

(deftest test-fold-reconstructs-ticks
  (let [ticks (ingest/fold-to-ticks (datoms))]
    (is (= 6 (count ticks)))
    (is (= #{":st.cab-weld" ":st.paint"} (set (map #(get % ":tick/station") ticks))))))

(deftest test-energy-wh-to-kwh-conversion
  (let [ticks (ingest/fold-to-ticks (datoms))
        cab0 (first (filter #(and (= (get % ":tick/station") ":st.cab-weld") (= (get % ":tick/t") 0)) ticks))]
    (is (= 50.0 (get cab0 ":tick/kwh")))
    (is (= 3600.0 (get cab0 ":tick/interval-s")))
    (is (= (get cab0 ":tick/cycles") (+ (get cab0 ":tick/good") (get cab0 ":tick/scrap"))))))

(deftest test-ingested-cab-weld-matches-ops-seed
  (let [ingested (into {} (for [tk (ingest/fold-to-ticks (datoms))
                                :when (= (get tk ":tick/station") ":st.cab-weld")]
                            [(get tk ":tick/t") tk]))
        {:keys [ticks]} (analyze/load-file* ops)
        seed-cab (into {} (for [tk ticks :when (= (get tk ":tick/station") ":st.cab-weld")]
                            [(get tk ":tick/t") tk]))]
    (is (= (set (keys ingested)) (set (keys seed-cab))))
    (doseq [t (keys seed-cab)]
      ;; Python `==` compares numerically (50.0 == 50); :tick/state is a string so use a
      ;; mixed comparator that mirrors Python's == for both numbers and strings.
      (doseq [k [":tick/good" ":tick/scrap" ":tick/cycles" ":tick/kwh" ":tick/state" ":tick/interval-s"]]
        (let [a (get-in ingested [t k]) b (get-in seed-cab [t k])]
          (is (if (and (number? a) (number? b)) (== a b) (= a b))
              (str "t=" t " " k ": " a " != " b)))))))

(deftest test-most-severe-state-wins-no-hidden-stop
  (let [paint1 (first (filter #(and (= (get % ":tick/station") ":st.paint") (= (get % ":tick/t") 1))
                              (ingest/fold-to-ticks (datoms))))]
    (is (= ":idle" (get paint1 ":tick/state")))))

(deftest test-ingested-ticks-feed-analyze
  (let [ticks (ingest/fold-to-ticks (datoms))
        {:keys [stations]} (analyze/load-file* ops)
        res (analyze/analyze stations ticks)]
    (doseq [sid (filter #(not (str/starts-with? % "_")) (keys res))]
      (is (and (<= 0.0 (get-in res [sid "oee"])) (<= (get-in res [sid "oee"]) (+ 1.0 1e-9)))))
    (is (= 45.0 (get-in res [":st.paint" "idle_kwh"])))))

(deftest test-to-tick-edn-round-trips
  (let [ticks (ingest/fold-to-ticks (datoms))
        edn (ingest/to-tick-edn ticks)
        forms (filter #(and (map? %) (contains? % ":tick/station")) (analyze/read-edn edn))
        a (into {} (for [t ticks] [[(get t ":tick/station") (get t ":tick/t")] t]))
        b (into {} (for [t forms] [[(get t ":tick/station") (get t ":tick/t")] t]))]
    (is (= (count forms) (count ticks)))
    (is (= (set (keys a)) (set (keys b))))
    (doseq [k (keys a)]
      ;; Python `==` compares numerically across int/float (50.0 == 50)
      (is (== (get-in a [k ":tick/good"]) (get-in b [k ":tick/good"])))
      (is (== (get-in a [k ":tick/kwh"]) (get-in b [k ":tick/kwh"]))))))

(deftest test-live-ingest-is-gated
  (is (thrown-with-msg? #?(:clj Exception :cljs js/Error) #"G6"
                        (ingest/ingest-live "opc.tcp://plc:4840")))
  (is (thrown-with-msg? #?(:clj Exception :cljs js/Error) #"Council"
                        (ingest/ingest-live "opc.tcp://plc:4840"))))

(deftest test-g2-rejects-person-attr
  (let [bad [["scan.x" ":scan/station" ":st.a" 1 ":add"]
             ["scan.x" ":worker/id" "w-7" 2 ":add"]]]
    (is (thrown-with-msg? #?(:clj Exception :cljs js/Error) #"G2"
                          (ingest/fold-to-ticks bad)))))

(deftest test-determinism
  (let [a (ingest/to-tick-edn (ingest/fold-to-ticks (datoms)))
        b (ingest/to-tick-edn (ingest/fold-to-ticks (datoms)))]
    (is (= a b))))
