(ns fuchi.methods.test-lexicons
  "test_lexicons.py — Lexicon well-formedness tests for 扶持 (fuchi) — all 9
  com.etzhayyim.fuchi.* lexicons. 1:1 Clojure port of methods/test_lexicons.py
  (stdlib asserts → clojure.test). The Python `_run` demo printer is omitted.

  Reads the lexicon EDN files via fuchi.methods.edn (keywords kept as \":…\"
  STRINGS, byte-for-byte the Python `load_edn` shape). File I/O at the #?(:clj) edge.

  Run from 20-actors (the bb source root)."
  (:require [clojure.test :refer [deftest is run-tests]]
            [clojure.string :as str]
            #?(:clj [clojure.java.io :as io])
            [fuchi.methods.edn :as edn]))

;; ROOT/lex via *file* (…/fuchi/methods/test_lexicons.cljc → up 2 = fuchi, then lex/)
#?(:clj (def ^:private lex-dir (io/file (-> *file* io/file .getParentFile .getParentFile) "lex")))

;; EXPECTED = {filename → lexicon id}  (insertion order preserved, mirrors the Python dict)
(def ^:private expected
  (array-map
   "maintainerCovenant.edn" "com.etzhayyim.fuchi.maintainerCovenant"
   "sustenanceEnvelope.edn" "com.etzhayyim.fuchi.sustenanceEnvelope"
   "allocationIntent.edn"   "com.etzhayyim.fuchi.allocationIntent"
   "routingPlan.edn"        "com.etzhayyim.fuchi.routingPlan"
   "governanceDecision.edn" "com.etzhayyim.fuchi.governanceDecision"
   "provisioningIntent.edn" "com.etzhayyim.fuchi.provisioningIntent"
   "voteBallot.edn"         "com.etzhayyim.fuchi.voteBallot"
   "sustenanceBooking.edn"  "com.etzhayyim.fuchi.sustenanceBooking"
   "cohortEarmark.edn"      "com.etzhayyim.fuchi.cohortEarmark"))

#?(:clj
   (defn- present-edn-files []
     (set (map #(.getName %)
               (filter #(str/ends-with? (.getName %) ".edn") (.listFiles lex-dir))))))

(deftest test-all-five-lexicons-present
  ;; assert set(EXPECTED) <= files
  #?(:clj
     (let [files (present-edn-files)]
       (is (every? files (keys expected))
           (str "missing: " (remove files (keys expected)))))))

(deftest test-each-lexicon-well-formed
  #?(:clj
     (doseq [[fname lid] expected]
       (let [lex (edn/load-edn (io/file lex-dir fname))]
         (is (= 1 (get lex ":lexicon")) fname)
         (is (= lid (get lex ":id")) fname)
         (let [rec (get-in lex [":defs" ":main"])]
           (is (= "record" (get rec ":type")) fname)
           (is (contains? rec ":record") fname)
           (is (= "object" (get-in rec [":record" ":type"])) fname)
           (is (seq (get-in rec [":record" ":required"])) fname))))))

(deftest test-namespace-prefix-is-fuchi
  (doseq [lid (vals expected)]
    (is (str/starts-with? lid "com.etzhayyim.fuchi.") lid)))

#?(:clj (defn -main [& _] (run-tests 'fuchi.methods.test-lexicons)))
