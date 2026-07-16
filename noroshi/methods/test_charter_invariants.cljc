(ns noroshi.methods.test-charter-invariants
  "Structural charter-invariant tests for noroshi (烽) — ADR-2606051600 (items 3+4).
  1:1 Clojure port of methods/test_charter_invariants.py (pytest → clojure.test).

  Asserts the constitution structurally over the parsed lexicons / kotoba schema /
  ontology + the code constants. Reads EDN via the inlined `_edn` reader behind #?(:clj …)."
  (:require [clojure.test :refer [deftest is run-tests]]
            [clojure.string :as str]
            [clojure.set :as set]
            [noroshi.methods._edn :as edn]
            [noroshi.methods.active-alignment :as aa]))

;; …/noroshi/methods/test_charter_invariants.cljc → parents[1] = noroshi actor root.
#?(:clj
   (def ^:private actor-root (-> (java.io.File. *file*) .getParentFile .getParentFile)))
#?(:clj
   (def ^:private lex-dir (java.io.File. actor-root "lex")))
;; ROOT/00-contracts/schemas/… : actor-root parents[1] = 20-actors → up 1 = ROOT.
#?(:clj
   (def ^:private ontology-file
     (-> actor-root .getParentFile .getParentFile
         (java.io.File. "00-contracts") (java.io.File. "schemas")
         (java.io.File. "photonic-convergence-ontology.kotoba.edn"))))

(def ^:private forbidden-use-values #{"weapon" "directed-energy" "dazzle" "fire-control" "person"})

#?(:clj
   (defn- props [lex-name]
     (let [d (edn/load-edn (java.io.File. lex-dir (str lex-name ".edn")))]
       (get-in d [":defs" ":main" ":record" ":properties"]))))

;; ── G3/N1 civilian force-separation ────────────────────────────────────────────
(deftest test-photonic-device-force-class-is-civilian-const
  (is (= (get-in (props "photonicDevice") [":forceClass" ":const"]) "civilian-comms")))

(deftest test-packaging-use-enum-excludes-weaponisation
  (let [uses (set (get-in (props "packagingJob") [":use" ":enum"]))]
    (is (seq uses))
    (is (empty? (set/intersection uses forbidden-use-values)))))

(deftest test-code-permitted-uses-disjoint-from-forbidden
  (is (empty? (set/intersection (set aa/permitted-uses) (set aa/forbidden-uses))))
  (is (not (some #{"weapon"} aa/permitted-uses)))
  (is (not (some #{"directed-energy"} aa/permitted-uses))))

;; ── G4/N2 sensing-not-surveillance ──────────────────────────────────────────────
(deftest test-sense-estimate-target-class-is-object-const
  (let [tc (get-in (props "senseEstimate") [":targetClass"])]
    (is (= (get tc ":const") "object"))
    (is (not (str/includes? (str (get tc ":enum" [])) "person")))))

(deftest test-ontology-target-classes-are-object-only
  (let [onto (edn/load-edn ontology-file)]
    (is (= (get onto ":ontology/target-classes") [":object"]))
    (is (not (some #{":weaponizable"} (get onto ":ontology/force-classes"))))))

;; ── G7 no-server-key ─────────────────────────────────────────────────────────────
(deftest test-packaging-job-server-held-key-const-false
  (is (= (get-in (props "packagingJob") [":serverHeldKey" ":const"]) false)))

(deftest test-packaging-job-dry-run-const-true
  (is (= (get-in (props "packagingJob") [":dryRun" ":const"]) true)))

;; ── G3/N1 ISAC waveform is civilian ─────────────────────────────────────────────
(deftest test-isac-waveform-civilian-const-true
  (is (= (get-in (props "isacWaveform") [":civilian" ":const"]) true)))

;; ── G1/N5 open-EDA + G10 sourcing-honesty ────────────────────────────────────────
(deftest test-device-process-is-open-pdk-const
  (is (= (get-in (props "photonicDevice") [":process" ":const"]) "open-pdk")))

(deftest test-device-eda-enum-is-open-source-only
  (let [eda (set (get-in (props "photonicDevice") [":eda" ":enum"]))]
    (is (= eda #{"gdsfactory" "meep" "klayout" "openlane"}))
    (doseq [proprietary ["cadence" "synopsys" "lumerical" "ansys"]]
      (is (not (contains? eda proprietary))))))

(deftest test-device-representative-const-true
  (is (= (get-in (props "photonicDevice") [":representative" ":const"]) true)))

#?(:clj (defn -main [& _] (run-tests 'noroshi.methods.test-charter-invariants)))
