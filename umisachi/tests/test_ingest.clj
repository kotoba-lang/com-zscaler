#!/usr/bin/env bb
(ns umisachi.tests.test-ingest
  "umisachi 海幸 — public-dataset → kotoba-EAVT bridge invariants (ADR-2606074200).

  Guards the charter gates the bridge enforces by construction: G1 coordinate-drop (no
  catch-target), G4 destructive-method-fishery → :pressure reclassification (never a :fishery),
  G5 sourcing-honesty, G7 live-fetch refusal, and seed-wins dedup merge. The bridged output is
  charter-clean by construction (every node passes analyze's load-time guard).

  Run:  bb --classpath 20-actors 20-actors/umisachi/tests/test_ingest.clj"
  (:require [umisachi.methods.ingest :as i]
            [umisachi.methods.analyze :as a]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.test :refer [deftest is run-tests]]))

(def ^:private fixture-json
  (str "{\"records\":[\n"
       "{\"id\":\"skipjack-wcpo\",\"kind\":\"stock\",\"species\":\"Katsuwonus pelamis\",\"status\":\"sustainable\",\"region\":\"FAO-71\",\"lat\":12.3,\"lon\":140.1},\n"
       "{\"id\":\"poleline-clean\",\"kind\":\"fishery\",\"method\":\"pole-line\",\"scale\":\"artisanal\"},\n"
       "{\"id\":\"trawler-harm\",\"kind\":\"fishery\",\"method\":\"bottom-trawl\",\"scale\":\"industrial\"},\n"
       "{\"id\":\"iuu-vessel\",\"kind\":\"fishery\",\"method\":\"iuu\",\"scale\":\"industrial\"},\n"
       "{\"id\":\"toyosu\",\"kind\":\"logistics\",\"logistics_kind\":\"market-hall\"},\n"
       "{\"id\":\"export\",\"kind\":\"market\",\"market_kind\":\"export\",\"access\":\"proprietary\"}\n"
       "]}\n"))

(defn- with-fixture [f]
  (let [tmp (java.io.File/createTempFile "umi-ingest-" ".json")]
    (try (spit tmp fixture-json) (f (.getAbsolutePath tmp))
         (finally (.delete tmp)))))

(defn- by-id [nodes id] (some #(when (= id (:organism/id %)) %) nodes))

(deftest bridge-counts-and-shapes
  (with-fixture
    (fn [path]
      (let [{:keys [nodes reclassified dropped]} (i/bridge-source path)]
        (is (= 6 (count nodes)))
        (is (= 2 reclassified) "bottom-trawl + iuu fisheries reclassified to :pressure (G4)")
        (is (= 0 dropped))
        (is (= :stock (:organism/kind (by-id nodes :stock.skipjack-wcpo))))
        (is (= "Katsuwonus pelamis" (:stock/species (by-id nodes :stock.skipjack-wcpo))))
        (is (= :sustainable (:stock/status (by-id nodes :stock.skipjack-wcpo))))))))

(deftest g1-coordinate-fields-are-dropped
  ;; the stock record carried lat/lon; the bridged node must carry NO coordinate-shaped key.
  (with-fixture
    (fn [path]
      (let [n (by-id (:nodes (i/bridge-source path)) :stock.skipjack-wcpo)
            coordish? (fn [k] (some #(= (str/lower-case (name k)) %) ["lat" "lon" "lng" "latitude" "longitude"]))]
        (is (not-any? coordish? (keys n)) "no lat/lon leaks into the bridged node")))))

(deftest g4-destructive-fishery-reclassified-to-pressure
  (with-fixture
    (fn [path]
      (let [nodes (:nodes (i/bridge-source path))]
        ;; clean method stays a :fishery
        (is (= :fishery (:organism/kind (by-id nodes :fishery.poleline-clean))))
        (is (= :pole-line (:fishery/method (by-id nodes :fishery.poleline-clean))))
        ;; bottom-trawl / iuu are NOT representable as a :fishery — they become :pressure nodes
        (is (nil? (by-id nodes :fishery.trawler-harm)) "no :fishery node for a forbidden method")
        (let [p (by-id nodes :pressure.trawler-harm)]
          (is (= :pressure (:organism/kind p)))
          (is (= :bottom-trawl (:pressure/kind p)) "the harm is disclosed as the pressure kind"))
        (is (= :iuu (:pressure/kind (by-id nodes :pressure.iuu-vessel))))))))

(deftest bridged-nodes-are-charter-clean-by-construction
  ;; every bridged node passes analyze's load-time G1/G4 guard — the bridge cannot emit a
  ;; record that the canonical log would refuse.
  (with-fixture
    (fn [path]
      (doseq [n (:nodes (i/bridge-source path))]
        (is (= n (a/assert-charter-clean n)) (str "charter-clean: " (:organism/id n)))))))

(deftest merge-graph-seed-wins-and-edges-pass-through
  (let [seed [{:organism/id :stock.skipjack-wcpo :organism/kind :stock :stock/status :sustainable
               :organism/sourcing :authoritative}
              {:en/from :fishery.x :en/kind :nourishes :en/to :market.y :en/load 0.5}]
        bridged [{:organism/id :stock.skipjack-wcpo :organism/kind :stock :stock/status :overfished
                  :organism/sourcing :representative}     ; conflicts with seed → seed must win
                 {:organism/id :market.new :organism/kind :market}]
        merged (i/merge-graph seed bridged)
        skip (some #(when (= :stock.skipjack-wcpo (:organism/id %)) %) merged)]
    (is (= :authoritative (:organism/sourcing skip)) "seed wins on :organism/id conflict")
    (is (= :sustainable (:stock/status skip)))
    (is (some #(= :market.new (:organism/id %)) merged) "new bridged node added")
    (is (some :en/from merged) "edges (no :organism/id) pass through")))

(deftest g7-live-fetch-refused-without-gate
  (is (some? (i/live-refusal ["--live"] nil)) "—live without the operator gate is refused")
  (is (str/includes? (i/live-refusal ["--live"] nil) "G7"))
  (is (nil? (i/live-refusal [] nil)) "offline (no --live) needs no gate"))

(when (= *file* (System/getProperty "babashka.file"))
  (let [{:keys [fail error]} (run-tests 'umisachi.tests.test-ingest)]
    (System/exit (if (zero? (+ fail error)) 0 1))))
