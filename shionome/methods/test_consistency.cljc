(ns shionome.methods.test-consistency
  "Cross-language oracle tests for 潮目 seed ↔ manifest cross-consistency.
  1:1 port of methods/test_consistency.py. ADR-2606072200."
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.string :as str]
            [cheshire.core :as json]
            [shionome.methods.edn :as edn]
            [shionome.methods.weave :as weave]))

(def seed-path "20-actors/shionome/data/seed-capital-flow-graph.kotoba.edn")
(def manifest-path "20-actors/shionome/manifest.edn")
(defn- g [] (weave/weave (edn/load-edn seed-path)))
(defn- manifest [] (:actor/manifest (clojure.edn/read-string (slurp manifest-path))))
(defn- bare [x] (str/replace (str x) #"^:+" ""))

(deftest seed-validates-cleanly
  (is (map? (g))))                                 ; weave raises on any gate; reaching here = clean

(deftest seed-buckets-use-known-scopes
  (doseq [b (vals (get (g) "buckets"))]
    (is (some #(= % (bare (get b ":bucket/scope"))) weave/BUCKET-SCOPES))))

(deftest seed-flows-use-known-kinds
  (doseq [f (get (g) "flows")]
    (is (some #(= % (bare (get f ":flow/kind"))) weave/FLOW-KINDS))))

(deftest seed-all-representative-g11
  (doseq [f (get (g) "flows")]
    (is (= "representative" (bare (get f ":flow/sourcing"))))))

(deftest manifest-loads-and-names-shionome
  (let [m (manifest)]
    (is (= "shionome" (get m "name")))
    (is (= "did:web:etzhayyim.com:actor:shionome" (get m "id")))))

(deftest manifest-declares-no-trade-gate
  (let [blob (json/generate-string (manifest))]
    (is (or (str/includes? blob "トレードはしない")
            (str/includes? (str/lower-case blob) "no-trade")))))

(deftest manifest-status-r0
  (is (str/starts-with? (get (manifest) "status") "R0")))
