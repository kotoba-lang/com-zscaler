(ns hakoniwa.tests.test-ingest
  "test_ingest.py — hakoniwa 箱庭 real-entity ingest + LLM-persona swarm tests (R1).
  1:1 Clojure port of tests/test_ingest.py (ADR-2606111500). Network-free (offline fixture)."
  (:require [clojure.test :refer [deftest is run-tests]]
            [clojure.string :as str]
            [clojure.edn :as edn]
            #?(:clj [clojure.java.io :as io])
            [hakoniwa.methods.world :as w]
            [hakoniwa.methods.simulate :as s]
            [hakoniwa.methods.distribution :as d]
            [hakoniwa.methods.ingest :as ing]
            [hakoniwa.methods.cid :as cidlib]
            [hakoniwa.methods.murakumo :as m]))

(def ^:private actor-dir (-> *file* io/file .getParentFile .getParentFile))

;; data/ingest-sources.edn is now Datomic/Datascript tx-data — `[{:db/id -1 :ingest/... }]`
;; (edn-datomize pass). Reconstitute hakoniwa's house-style string-keyed map (mirrors
;; hakoniwa.methods.world/read-edn's `atom-of`, which keeps ":ns/name" tokens as literal
;; STRINGS rather than clojure keywords, for byte-parity with the Python port) so
;; ing/build-box's `(get sources ":ingest/...")` lookups below keep working unchanged.
(defn- edn->hakoniwa-strkeys
  "Recursively turn every keyword (map key or bare value) into its ':ns/name' string form."
  [v]
  (cond
    (map? v) (into {} (map (fn [[k vv]] [(if (keyword? k) (str k) k) (edn->hakoniwa-strkeys vv)])) v)
    (vector? v) (mapv edn->hakoniwa-strkeys v)
    (seq? v) (mapv edn->hakoniwa-strkeys v)
    (keyword? v) (str v)
    :else v))

(defn- unblob [v]
  (if (string? v)
    (try (let [parsed (edn/read-string v)]
           (if (coll? parsed) (edn->hakoniwa-strkeys parsed) v))
         (catch #?(:clj Exception :cljs :default) _ v))
    v))

(defn- reconstitute-sources
  "tx-data -> hakoniwa's original bare, string-keyed :ingest/* map."
  [tx-data]
  (into {} (map (fn [[k v]] [(str k) (unblob v)]))
        (dissoc (first tx-data) :db/id)))

(def ^:private sources
  (reconstitute-sources (edn/read-string (slurp (io/file actor-dir "data" "ingest-sources.edn")))))
(def ^:private fixture (ing/parse-json (slurp (io/file actor-dir "tests" "fixtures" "wikidata_entities.json"))))

(deftest test-ingest-keeps-real-entities
  (let [[nodes _ prov] (ing/build-box sources fixture)
        ents (filter (fn [n] (and (= ":entity" (get n ":sim/kind"))
                                  (str/starts-with? (str (get n ":entity/public-ref" "")) "wd:")))
                     (vals nodes))]
    (is (>= (count ents) 4))
    (doseq [e ents] (is (= ":authoritative" (get e ":sim/sourcing"))))
    (is (>= (get prov "n_entities_kept") 4))))

(deftest test-g1-drops-natural-person
  (let [src (assoc sources ":ingest/entities"
                   (conj (vec (get sources ":ingest/entities")) {":qid" "Q42" ":role" ":person"}))
        [nodes _ prov] (ing/build-box src fixture)]
    (doseq [n (vals nodes)]
      (is (not= "wd:Q42" (get n ":entity/public-ref"))))
    (is (some (fn [dd] (= "Q42" (get dd "qid"))) (get prov "dropped")))
    (is (>= (get prov "n_entities_dropped") 1))))

(deftest test-emitted-box-is-synthetic-and-loads
  (let [[nodes edges _] (ing/build-box sources fixture)]
    (w/assert-synthetic nodes)
    (let [personas (filter (fn [n] (= ":persona" (get n ":sim/kind"))) (vals nodes))]
      (is (and (seq personas) (every? (fn [p] (true? (get p ":persona/synthetic"))) personas))))
    (let [edn (ing/to-edn nodes edges)
          tmp (str (System/getProperty "java.io.tmpdir") "/hakoniwa-ingest-" (System/nanoTime) ".edn")]
      (spit tmp edn)
      (try
        (let [{n2 :nodes e2 :edges} (w/load-file* tmp)]
          (is (= (count n2) (count nodes)))
          (let [[results _] (s/ensemble n2 e2 {:steps 10 :replicas 32 :seed 7})
                dist (d/distribution results)]
            (is (<= 0.0 (get-in dist ["quantiles" ":p50"]) 1.0))))
        (finally (io/delete-file tmp true))))))

(deftest test-box-content-address-deterministic
  (let [[n1 e1 _] (ing/build-box sources fixture)
        a (cidlib/cidv1-raw (ing/to-edn n1 e1))
        [n2 e2 _] (ing/build-box sources fixture)
        b (cidlib/cidv1-raw (ing/to-edn n2 e2))]
    (is (= a b))
    (is (str/starts-with? a "bafkrei"))))

(deftest test-swarm-ensemble-runs-with-kernel-step
  (let [[nodes edges _] (ing/build-box sources fixture)
        [results meta] (s/swarm-ensemble nodes edges {:steps 10 :replicas 24 :seed 7})]
    (is (= 24 (count results)))
    (is (every? (fn [r] (<= 0.0 r 1.0)) results))
    (is (= [":kernel"] (get meta "swarm_via")))
    (let [dist (d/distribution results)]
      (is (>= (- (get dist "max") (get dist "min")) 0.0)))))

(deftest test-swarm-step-fn-is-clamped
  (let [[nodes edges _] (ing/build-box sources fixture)
        rogue (fn [_st _nm _su _an] {"stance" 99.0 "via" ":rogue"})
        [results _] (s/swarm-ensemble nodes edges {:steps 4 :replicas 4 :seed 1 :step-fn rogue})]
    (is (every? (fn [r] (<= 0.0 r 1.0)) results))))

(deftest test-murakumo-persona-step-signature-matches-swarm
  (let [r (m/persona-step 0.5 0.8 0.6 0.4 false)]
    (is (= #{"stance" "via"} (set (keys r))))
    (is (<= 0.0 (get r "stance") 1.0))
    (is (= ":kernel-fallback" (get r "via")))))

#?(:clj (defn -main [& _] (run-tests 'hakoniwa.tests.test-ingest)))
