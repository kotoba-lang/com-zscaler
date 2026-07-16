(ns tasuke.methods.test-lexicons
  "Well-formedness + SSoT-consistency tests for the 助 (tasuke) lexicons and ontology.
  1:1 port of `methods/test_lexicons.py` (pytest → clojure.test)."
  (:require [clojure.test :refer [deftest is run-tests]]
            [clojure.string :as str]
            #?(:clj [clojure.java.io :as io])
            [tasuke.methods.edn :as tedn]))

;; ROOT/20-actors/tasuke via *file* (…/tasuke/methods/test_lexicons.cljc → up 2 = tasuke)
#?(:clj (def ^:private actor-dir (-> *file* io/file .getParentFile .getParentFile)))
#?(:clj (def ^:private repo-dir (-> actor-dir .getParentFile .getParentFile)))
#?(:clj (def ^:private lex-dir (io/file actor-dir "lex")))
#?(:clj (def ^:private ontology-file (io/file repo-dir "00-contracts" "schemas" "cybercrime-victim-support-ontology.kotoba.edn")))

(def ^:private LEXICONS
  ["victimIntake" "evidenceItem" "policeReportDraft" "platformRequest" "recoveryPlan" "supportCase"])

(deftest test-all-lexicons-parse-and-are-namespaced
  (doseq [name LEXICONS]
    (let [d (tedn/load-edn (io/file lex-dir (str name ".edn")))
          rec (get-in d [":defs" ":main" ":record"])]
      (is (str/starts-with? (get d ":id") "com.etzhayyim.tasuke."))
      (is (= "object" (get rec ":type")))
      (is (and (map? (get rec ":properties")) (seq (get rec ":properties")))))))

(deftest test-required-fields-are-declared-properties
  (doseq [name LEXICONS]
    (let [d (tedn/load-edn (io/file lex-dir (str name ".edn")))
          rec (get-in d [":defs" ":main" ":record"])
          props (set (map #(str/replace % #"^:+" "") (keys (get rec ":properties"))))]
      (doseq [req (get rec ":required" [])]
        (is (contains? props req) (str name ": required " (pr-str req) " not a declared property"))))))

(deftest test-lexicon-count-is-six
  (is (= 6 (count LEXICONS))))

(deftest test-ontology-parses-with-closed-vocab
  (let [o (tedn/load-edn ontology-file)]
    (is (= "com.etzhayyim.tasuke.cybercrime-victim-support" (get o ":ontology/id")))
    (doseq [key [":ontology/scam-kinds" ":ontology/doc-kinds" ":ontology/doc-authors"
                 ":ontology/support-roles" ":ontology/referral-windows" ":ontology/evidence-kinds"]]
      (is (and (sequential? (get o key)) (seq (get o key)))))))

(deftest test-ontology-sourcing-grades-present
  (let [o (tedn/load-edn ontology-file)
        idents (set (map #(get % ":db/ident") (filter map? (get o ":schema"))))]
    (is (contains? idents ":registry/sourcing"))))

#?(:clj (defn -main [& _] (run-tests 'tasuke.methods.test-lexicons)))
