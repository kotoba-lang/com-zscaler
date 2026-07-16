(ns tatara.methods.test-seed-integrity
  "tatara 鑪 — seed ↔ ontology integrity invariants (ADR-2606171800).

  Makes the seed self-validating as it grows: every plant/hub/flow attribute is ontology-declared,
  required identity+geo attrs are present, ids are unique, coordinates are in range, flow endpoints
  resolve, sourcing values are legal — and the load-bearing analysis invariant that EACH SECTOR
  USES EXACTLY ONE capacity unit (the precondition the per-sector capacity rollup depends on).
  Plus the G4 boundary at the data layer: no :worker/* / :person/* attribute appears."
  (:require [clojure.test :refer [deftest is run-tests]]
            [clojure.java.io :as io]
            [clojure.edn :as edn]
            [clojure.set]
            [tatara.methods.analyze :as az]))

(def actor-dir (-> *file* io/file .getParentFile .getParentFile))
(def seed (io/file actor-dir "data" "seed-plant-graph.kotoba.edn"))
(def ontology (io/file (.getParentFile (.getParentFile actor-dir))
                       "00-contracts" "schemas" "manufacturing-plant-ontology.kotoba.edn"))

(defn- idents-for [ns-name]
  (->> (edn/read-string (slurp ontology))
       (keep :db/ident)
       (filter #(= ns-name (namespace %)))
       set))

(def required-plant #{:plant/id :plant/name :plant/operator :plant/country
                      :plant/lat :plant/lon :plant/sector :plant/sourcing})
(def valid-sourcing #{:authoritative :representative :synthesized})

(defn- g [] (az/classify (az/load-edn seed)))

(deftest test-every-plant-attr-is-ontology-declared
  ;; :db/id is the Datomic/Datascript tx-data framing key the repo-wide edn-datomize pass adds
  ;; to every seed record (ADR-agnostic scaffolding, not a domain fact ABOUT the plant) — it is
  ;; deliberately NOT part of the manufacturing-plant ontology, so it is excluded here the same
  ;; way it is excluded from persisted EAVT datoms in kotoba.cljc's non-domain-keys.
  (let [declared (idents-for "plant")]
    (is (pos? (count declared)))
    (doseq [p (:plants (g))]
      (doseq [k (keys p)]
        (when (not= :db/id k)
          (is (contains? declared k) (str (:plant/id p) ": undeclared attr " k)))))))

(deftest test-required-plant-attrs-present
  (doseq [p (:plants (g))]
    (doseq [k required-plant]
      (is (contains? p k) (str (:plant/id p) ": missing required " k)))))

(deftest test-ids-are-unique
  (let [{:keys [plants hubs flows]} (g)]
    (is (apply distinct? (map :plant/id plants)))
    (is (apply distinct? (map :hub/id hubs)))
    (is (apply distinct? (map :flow/id flows)))))

(deftest test-coordinates-in-range
  (let [{:keys [plants hubs chokepoints]} (g)]
    (doseq [e (concat plants hubs chokepoints)]
      (let [lat (or (:plant/lat e) (:hub/lat e) (:chokepoint/lat e))
            lon (or (:plant/lon e) (:hub/lon e) (:chokepoint/lon e))]
        (is (<= -90.0 (double lat) 90.0))
        (is (<= -180.0 (double lon) 180.0))))))

(deftest test-chokepoint-nodes-cover-every-flow-via  ;; ── referential completeness ──
  (let [{:keys [flows chokepoints]} (g)
        ck-kw (set (map :chokepoint/keyword chokepoints))
        via-used (set (mapcat :flow/via flows))]
    (is (apply distinct? (map :chokepoint/id chokepoints)))
    (is (apply distinct? (map :chokepoint/keyword chokepoints)))
    (doseq [v via-used]
      (is (contains? ck-kw v) (str "flow via :" (name v) " has no :chokepoint geographic node")))))

(deftest test-each-sector-uses-exactly-one-capacity-unit  ;; ── load-bearing rollup invariant ──
  (let [by-sector (group-by :plant/sector (:plants (g)))]
    (doseq [[sector ps] by-sector]
      (is (= 1 (count (distinct (map :plant/capacity-unit ps))))
          (str sector ": mixed capacity units would corrupt the per-sector capacity rollup")))))

(deftest test-flow-endpoints-resolve
  (let [{:keys [plants hubs flows]} (g)
        plant-ids (set (map :plant/id plants))
        hub-ids (set (map :hub/id hubs))
        node-ids (clojure.set/union plant-ids hub-ids)]
    (doseq [f flows]
      (is (contains? node-ids (:flow/from f)) (str (:flow/id f) ": dangling :flow/from"))
      (is (contains? hub-ids (:flow/to f)) (str (:flow/id f) ": :flow/to must be a hub")))))

(deftest test-sourcing-values-are-legal
  (let [{:keys [plants hubs flows]} (g)]
    (doseq [e (concat plants hubs flows)]
      (let [s (or (:plant/sourcing e) (:hub/sourcing e) (:flow/sourcing e))]
        (is (contains? valid-sourcing s) (str "illegal sourcing: " s))))))

(deftest test-no-per-worker-attribute-in-seed  ;; ── G4 at the data layer ──
  (doseq [p (:plants (g))]
    (doseq [k (keys p)]
      (is (not (#{"worker" "person"} (namespace k)))
          (str (:plant/id p) ": forbidden per-individual attr " k)))))

#?(:clj
   (defn -main [& _]
     (let [{:keys [fail error]} (run-tests 'tatara.methods.test-seed-integrity)]
       (System/exit (if (zero? (+ fail error)) 0 1)))))
