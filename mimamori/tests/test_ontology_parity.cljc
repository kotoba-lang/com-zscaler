(ns mimamori.tests.test-ontology-parity
  "mishmeret ontology EDN <-> runtime validator parity -- schema and code never drift.
  1:1 Clojure port of tests/test_ontology_parity.py (stdlib asserts -> clojure.test).

  The ontology EDN is read with the actor's OWN minimal EDN reader (_edn), which
  keeps keywords as \":...\" STRINGS, so the declared :attr surface compares
  identically to the validator's string whitelists -- the same comparison the
  Python test makes. Run: bb test:mimamori.

  (No __main__ demo: the Python `t(...)`/`print` harness is replaced by deftest.)"
  (:require [clojure.test :refer [deftest is]]
            [clojure.set :as set]
            #?(:clj [clojure.java.io :as io])
            #?(:clj [clojure.string :as str])
            [mimamori.methods.-edn :as edn]
            [mimamori.methods.bond :as bond]
            [mimamori.methods.coverage-report :as cov]
            [mimamori.methods.kotoba :as kotoba]
            [mimamori.methods.shakai :as shakai]))

;; ROOT via *file*: …/20-actors/mimamori/tests/test_ontology_parity.cljc → up 3 = 20-actors,
;; up 4 = repo root → 00-contracts/schemas/mishmeret-ontology.kotoba.edn (Python HERE.parents[1]).
(def ^:private actor-dir (-> *file* io/file .getParentFile .getParentFile))
(def ^:private ontology
  (-> actor-dir .getParentFile .getParentFile
      (io/file "00-contracts" "schemas" "mishmeret-ontology.kotoba.edn")))

(defn- load-ontology []
  ;; Mirror Python: drop comment lines (lstrip starts-with ";") then _parse(_tokens(text)).
  (let [text (->> (str/split-lines (slurp ontology))
                  (remove #(str/starts-with? (str/triml %) ";"))
                  (str/join "\n"))]
    (edn/parse (edn/tokens text))))

(deftest test-edge-attrs-match-validator-whitelist
  (let [o (load-ontology)
        declared (set (map #(get % ":attr") (get o ":edge/attrs")))]
    (is (= declared bond/attr-whitelist)
        (str "drift: ontology-only=" (sort (set/difference declared bond/attr-whitelist))
             " code-only=" (sort (set/difference bond/attr-whitelist declared))))))

(deftest test-coverage-attrs-match-emitter
  (let [o (load-ontology)
        declared (set (map #(get % ":attr") (get o ":coverage/attrs")))
        seed (bond/load-seed-file (io/file actor-dir "data" "seed-mimamori-bonds.json"))
        emitted (set (map #(nth % 2) (kotoba/coverage-datoms (cov/coverage seed) 1)))]
    (is (= declared emitted))))

(deftest test-social-capital-attrs-match-emitter
  (let [o (load-ontology)
        declared (set (map #(get % ":attr") (get o ":social-capital/attrs")))
        seed (bond/load-seed-file (io/file actor-dir "data" "seed-mimamori-bonds.json"))
        eng (bond/replay seed)
        [led _] (shakai/mint-from-keeping eng [] 1)
        emitted (set (map #(nth % 2) (shakai/social-capital-datoms led 1)))]
    (is (= declared emitted))))

(deftest test-negative-space-documented
  (let [o (load-ontology)
        blocked (str/join " " (map #(get % ":ns") (get o ":unrepresentable")))]
    (is (str/includes? blocked ":mishmeret.person/*"))   ;; G2
    (is (str/includes? blocked ":police"))               ;; G1
    (is (str/includes? blocked ":db/retract"))           ;; append-only
    ;; and the doc'd enums really are the code's whole surface:
    (is (= bond/care-whitelist #{":kokoro" ":wakai" ":iyashi"}))
    (is (= bond/states #{":offered" ":active" ":declined" ":exited" ":handed-off"}))))
