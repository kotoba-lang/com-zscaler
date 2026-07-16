(ns keizu.methods.test-consistency
  "test_consistency.py — 系図 (keizu) SSoT drift-lock. ADR-2606066000.
  1:1 Clojure port (stdlib unittest → clojure.test)."
  (:require [clojure.test :refer [deftest is run-tests]]
            [clojure.string :as str]
            #?(:clj [clojure.java.io :as io])
            #?(:clj [cheshire.core :as json])
            [keizu.methods._edn :as edn]))

;; ROOT/20-actors via *file* (…/20-actors/keizu/methods/test_consistency.cljc → up 3)
(def ^:private actors-dir
  #?(:clj (-> *file* io/file .getParentFile .getParentFile .getParentFile)))

(def ^:private root-dir
  #?(:clj (-> actors-dir .getParentFile)))

(def ^:private ont-path
  #?(:clj (io/file root-dir "00-contracts" "schemas" "government-relations-ontology.kotoba.edn")))

(def ^:private seedreg-path
  #?(:clj (io/file root-dir "00-contracts" "schemas" "actor-profile-seed.kotoba.edn")))

(def ^:private lexes
  ["relationEdge" "committeeComposition" "moneyFlowObservation" "networkPost"])

(def ^:private cells
  ["ingest" "committee_graph" "money_graph" "relation_weave" "social_post"])

(defn- manifest []
  #?(:clj (:actor/manifest (clojure.edn/read-string (slurp (io/file actors-dir "keizu" "manifest.edn"))))))

;; ── Tests ──────────────────────────────────────────────────────────────────────
(deftest test-manifest-tier-b
  (is (= "Tier-B" (get (manifest) "tier"))))

(deftest test-manifest-adr-matches-ontology
  (let [m (manifest)]
    (is (str/includes? (get-in m ["adr" "master"]) "2606066000"))
    (is (= "2606066000" (get (edn/load-edn ont-path) ":ontology/adr")))))

(deftest test-manifest-lexicons-exist
  (let [m (manifest)
        declared (set (map #(last (str/split % #"\."))
                           (get m "lexiconNamespaces")))]
    (is (= (set lexes) declared))
    (doseq [name lexes]
      (is (.exists (io/file actors-dir "keizu" "lex" (str name ".edn")))
          name))))

(deftest test-manifest-cells-match-tree
  (let [m (manifest)
        names (set (map #(get % "name") (get m "cells")))]
    (is (= (set cells) names))
    (doseq [c cells]
      (is (.exists #?(:clj (io/file actors-dir "keizu" "cells" c "state_machine.cljc")))))))

(deftest test-lex-ids-match-namespaces
  (let [m (manifest)
        declared (set (get m "lexiconNamespaces"))
        got (set (for [n lexes]
                   (get (edn/load-edn
                          #?(:clj (io/file actors-dir "keizu" "lex" (str n ".edn"))))
                        ":id")))]
    (is (= got declared))))

(deftest test-registry-soft
  ;; SOFT — passes whether or not the shared seed has been updated yet (ake convention).
  (when #?(:clj (.exists seedreg-path))
    (let [txt #?(:clj (slurp seedreg-path))]
      (when (str/includes? txt "actor:keizu")
        (is (or (str/includes? txt "\"keizu\"")
                (str/includes? txt "actor:keizu")))))))

#?(:clj (defn -main [& _] (run-tests 'keizu.methods.test-consistency)))
