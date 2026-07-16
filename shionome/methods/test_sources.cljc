(ns shionome.methods.test-sources
  "Cross-language oracle tests for the 潮目 public-source registry seed integrity.
  1:1 port of methods/test_sources.py. ADR-2606072200. Reads registry/sources.seed.json."
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.string :as str]
            [cheshire.core :as json]))

(def reg-path "20-actors/shionome/registry/sources.seed.json")
(defn- reg [] (json/parse-string (slurp reg-path)))
(def DENY ["bloomberg" "refinitiv" "eikon" "factset" "capital iq" "capiq"
           "morningstar direct" "pitchbook" "四季報"])

(deftest registry-loads (is (contains? (reg) "sources")))

(deftest at-least-ten-sources (is (>= (count (get (reg) "sources")) 10)))

(deftest every-source-unverified-seed-g8
  (doseq [s (get (reg) "sources")]
    (is (= "unverified-seed" (get s "verificationStatus")) (get s "sourceId"))))

(deftest every-source-has-required-fields
  (doseq [s (get (reg) "sources")]
    (doseq [k ["sourceId" "title" "jurisdiction" "authority" "datasetUrl" "mapsTo"]]
      (is (and (contains? s k) (get s k)) [(get s "sourceId") k]))))

(deftest no-commercial-terminal-source
  (let [r (reg)
        comment (str/lower-case (str (get r "_comment" "")))
        blob (str/replace (str/lower-case (json/generate-string r)) comment "")]
    (doseq [d DENY]
      (is (not (str/includes? blob d)) (str "prohibited terminal " d)))))

(deftest source-ids-unique
  (let [ids (map #(get % "sourceId") (get (reg) "sources"))]
    (is (= (count ids) (count (set ids))))))

(deftest mapsto-targets-known-kinds
  (doseq [s (get (reg) "sources")]
    (doseq [m (get s "mapsTo")]
      (is (or (str/starts-with? m "flow:") (str/starts-with? m "snap:")) m))))

(deftest covers-multiple-asset-classes
  (is (>= (count (set (map #(get % "sourceKind") (get (reg) "sources")))) 6)))
