(ns ake.methods.test-lexicons
  "test_lexicons.cljc — well-formedness + SSoT-consistency over the 朱 (ake) lexicons
  and ontology. 1:1 Clojure port of `methods/test_lexicons.py` (clojure.test). Every
  Python assertion ported. Lexicon / ontology EDN is read via `ake.methods._edn` (the
  #?(:clj) file edge); keywords stay \":…\" STRINGS so a lexicon `:const \"member\"` /
  `:enum [\"kg-fact\" …]` reads byte-identically to the Python `load_edn`. The __main__
  standalone runner is omitted (Clojure runs via -main / run-tests)."
  (:require [clojure.test :refer [deftest is run-tests]]
            #?(:clj [ake.methods._edn :as edn])))

#?(:clj
   (def ^:private lex-dir "20-actors/ake/lex"))

#?(:clj
   (def ^:private ontology
     "00-contracts/schemas/community-edit-ontology.kotoba.edn"))

(def ^:private lexicons
  ["editProposal" "editTriage" "editReview" "editPromotion" "revisionEntry" "councilEditReview"])

#?(:clj
   (defn- lex [name]
     (edn/reconstitute (edn/load-edn (str lex-dir "/" name ".edn")) (str "lex." name))))

#?(:clj
   (defn- record* [d]
     (get-in d [":defs" ":main" ":record"])))

;; ── d[":id"].startswith("com.etzhayyim.ake.") + record well-formed ──────────
#?(:clj
   (deftest test-all-lexicons-parse-and-are-namespaced
     (doseq [name lexicons]
       (let [d (lex name)
             rec (record* d)]
         (is (clojure.string/starts-with? (get d ":id") "com.etzhayyim.ake."))
         (is (= "object" (get rec ":type")))
         (is (and (map? (get rec ":properties")) (seq (get rec ":properties"))))))))

;; ── every :required field is a declared property ────────────────────────────
#?(:clj
   (deftest test-required-fields-are-declared-properties
     (doseq [name lexicons]
       (let [d (lex name)
             rec (record* d)
             ;; props = {k.lstrip(":") for k in rec[":properties"]}
             props (set (map #(clojure.string/replace % #"^:+" "")
                             (keys (get rec ":properties"))))]
         (doseq [req (get rec ":required" [])]
           (is (contains? props req)
               (str name ": required " (pr-str req) " not a declared property")))))))

;; ── 6 lexicons = 5 cell-emitted + 1 Council attestation ─────────────────────
(deftest test-lexicon-count-matches-manifest-intent
  (is (= 6 (count lexicons))))

;; ── ontology parses with closed vocab ───────────────────────────────────────
#?(:clj
   (deftest test-ontology-parses-with-closed-vocab
     (let [o (edn/load-edn ontology)]
       (is (= "com.etzhayyim.ake.community-edit" (get o ":ontology/id")))
       (doseq [key [":ontology/target-kinds" ":ontology/edit-ops" ":ontology/author-kinds"
                    ":ontology/triage-risks" ":ontology/triage-routes" ":ontology/sourcing-grades"]]
         (is (and (vector? (get o key)) (seq (get o key))))))))

;; ── sourcing grades = [:representative :authoritative] ──────────────────────
#?(:clj
   (deftest test-sourcing-grades-are-representative-then-authoritative
     (is (= [":representative" ":authoritative"]
            (get (edn/load-edn ontology) ":ontology/sourcing-grades")))))

#?(:clj (defn -main [& _] (run-tests 'ake.methods.test-lexicons)))
