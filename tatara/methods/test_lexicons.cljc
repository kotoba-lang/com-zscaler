(ns tatara.methods.test-lexicons
  "tatara 鑪 — lexicon ↔ manifest ↔ ontology ↔ seed parity (ADR-2606171800).

  Structurally forbids drift between the four declarations of the same vocabulary:
  - every lexiconNamespace the manifest declares has a lexicon file whose `id` matches;
  - the lexicon enums (sector / capacityUnit / via-chokepoint) match the seed's actual values;
  - no lexicon input field is a per-worker / per-person field (G4 — the boundary lives in the
    write surface too, not just the store)."
  (:require [clojure.test :refer [deftest is run-tests]]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.edn :as edn]
            [cheshire.core :as j]
            [tatara.methods.analyze :as az]))

(def actor-dir (-> *file* io/file .getParentFile .getParentFile))
(def repo-root (-> actor-dir .getParentFile .getParentFile))   ; …/<worktree>
(def lex-dir (io/file repo-root "00-contracts" "lexicons" "com" "etzhayyim" "tatara"))
(def manifest (io/file actor-dir "manifest.edn"))
(def seed (io/file actor-dir "data" "seed-plant-graph.kotoba.edn"))

(defn- lex [name] (j/parse-string (slurp (io/file lex-dir (str name ".json")))))

(deftest test-manifest-namespaces-have-matching-lexicon-files
  (let [m (:actor/manifest (clojure.edn/read-string (slurp manifest)))
        declared (get m "lexiconNamespaces")]
    (is (= 4 (count declared)))
    (doseq [ns declared]
      (let [short (last (str/split ns #"\."))
            f (io/file lex-dir (str short ".json"))]
        (is (.exists f) (str "missing lexicon file for " ns))
        (is (= ns (get (j/parse-string (slurp f)) "id")))))))

(deftest test-lexicon-enums-match-seed-values
  (let [rows (az/load-edn seed)
        plants (filter :plant/id rows)
        flows (filter :flow/id rows)
        sector-enum (set (get-in (lex "registerPlant")
                                 ["defs" "main" "input" "schema" "properties" "sector" "enum"]))
        unit-enum (set (get-in (lex "registerPlant")
                               ["defs" "main" "input" "schema" "properties" "capacityUnit" "enum"]))
        via-enum (set (get-in (lex "recordFlow")
                              ["defs" "main" "input" "schema" "properties" "via" "items" "enum"]))]
    ;; every sector / unit used in the seed is a legal enum value
    (is (every? sector-enum (map (comp name :plant/sector) plants)))
    (is (every? unit-enum (map (comp name :plant/capacity-unit) plants)))
    ;; every chokepoint used in a flow is a legal via enum value
    (is (every? via-enum (map name (mapcat :flow/via flows))))))

(deftest test-chokepoint-lexicon-enum-matches-seed-and-flow-via
  (let [rows (az/load-edn seed)
        chokepoints (filter :chokepoint/id rows)
        kw-enum (set (get-in (lex "registerChokepoint")
                             ["defs" "main" "input" "schema" "properties" "keyword" "enum"]))
        via-enum (set (get-in (lex "recordFlow")
                              ["defs" "main" "input" "schema" "properties" "via" "items" "enum"]))]
    ;; every seed chokepoint keyword is a legal registerChokepoint enum value
    (is (every? kw-enum (map (comp name :chokepoint/keyword) chokepoints)))
    ;; the chokepoint keyword enum and the flow via enum are the SAME shared vocabulary
    (is (= kw-enum via-enum))))

(deftest test-no-per-worker-field-in-any-lexicon  ;; ── load-bearing G4 gate ──
  (doseq [name ["registerPlant" "registerHub" "recordFlow"]]
    (let [props (get-in (lex name) ["defs" "main" "input" "schema" "properties"])]
      (doseq [k (keys props)]
        (is (not (re-find #"(?i)worker|person|employee\b|biometric|shift|pace" k))
            (str name ": forbidden per-individual field: " k)))
      ;; the only employment field is the aggregate SIZE
      (when (= name "registerPlant")
        (is (contains? props "headcountEst"))))))

(deftest test-required-fields-cover-ontology-identity-and-geo
  (let [req (set (get-in (lex "registerPlant") ["defs" "main" "input" "schema" "required"]))]
    (is (every? req ["plantId" "operator" "lat" "lon" "sector" "sourcing"]))))

#?(:clj
   (defn -main [& _]
     (let [{:keys [fail error]} (run-tests 'tatara.methods.test-lexicons)]
       (System/exit (if (zero? (+ fail error)) 0 1)))))
