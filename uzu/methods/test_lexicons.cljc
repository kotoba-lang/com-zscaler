#!/usr/bin/env bb
;; uzu 渦 — lexicon ↔ manifest ↔ ontology parity (structurally forbids vocab drift).
;; Run: bb --classpath 20-actors 20-actors/uzu/methods/test_lexicons.cljc
(ns uzu.methods.test-lexicons
  (:require [clojure.edn :as edn]
            [clojure.test :refer [deftest is run-tests]]
            [cheshire.core :as j]
            [uzu.methods.uzu-edn :as ue]))

(def manifest (edn/read-string (slurp "20-actors/uzu/manifest.edn")))
(def ont (ue/ontology-map (edn/read-string (slurp "20-actors/uzu/kotoba/ontology.uzu.edn"))))
(defn lex [n] (j/parse-string (slurp (str "00-contracts/lexicons/com/etzhayyim/uzu/" n ".json"))))

(deftest every-manifest-lex-has-a-matching-file
  (doseq [{:keys [lex/id]} (:actor/lex manifest)]
    (let [l (lex id)]
      (is (= (str "com.etzhayyim.uzu." id) (get l "id"))
          (str "lexicon file id must match manifest lex " id)))))

(deftest organism-beat-enums-match-ontology
  (let [props (get-in (lex "organismBeat") ["defs" "main" "input" "schema" "properties"])
        regime-enum (set (get-in props ["regime" "enum"]))
        action-enum (set (get-in props ["action" "enum"]))]
    (is (= (set (map name (get-in ont [:enums :regime]))) regime-enum) "regime enum parity")
    (is (= (set (map name (get-in ont [:enums :action]))) action-enum) "action enum parity")))

(deftest energy-flow-class-enum-matches-ontology
  (let [props (get-in (lex "energyFlow") ["defs" "main" "input" "schema" "properties"])
        class-enum (set (get-in props ["class" "enum"]))]
    (is (= (set (map name (get-in ont [:enums :flow-class]))) class-enum) "flow-class enum parity")))

(deftest organism-beat-keeps-the-two-ledgers-distinct
  ;; the lexicon write surface must carry BOTH energy and freeEnergy as separate fields (G1)
  (let [props (get-in (lex "organismBeat") ["defs" "main" "input" "schema" "properties"])]
    (is (contains? props "energy") "conserved energy field present")
    (is (contains? props "freeEnergy") "informational free-energy field present")
    (is (not= (get-in props ["energy" "description"]) (get-in props ["freeEnergy" "description"]))
        "the two are documented as distinct quantities")))

(deftest energy-flow-has-no-cross-class-sum-field
  ;; G2/G3: the write surface never offers a cross-class total or a joules-per-meaning field
  (let [props (get-in (lex "energyFlow") ["defs" "main" "input" "schema" "properties"])]
    (is (not (contains? props "joules")) "no joule field on a (possibly experiential) flow")
    (is (not (contains? props "total")) "no cross-class total field")))

(when (= *file* (System/getProperty "babashka.file"))
  (let [{:keys [fail error]} (run-tests 'uzu.methods.test-lexicons)]
    (when (pos? (+ fail error)) (System/exit 1))))
