(ns ake.methods.test--edn
  "test__edn.cljc — 朱 (ake) EDN reader. 1:1 Clojure port of `methods/test__edn.py`.
  `parse-edn` is pure over a string and runs on every platform; the `load-edn` file edge is
  #?(:clj). Pins the atom-level reads the whole seed/ontology load depends on."
  (:require [clojure.test :refer [deftest is run-tests]]
            [ake.methods._edn :as edn]
            #?(:clj [clojure.java.io :as io])))

(deftest test-reads-true-false-nil
  (is (= [true false nil] (edn/parse-edn "[true false nil]"))))

(deftest test-reads-int-and-float-and-negative
  (is (= [1 2.5 -3 0.65] (edn/parse-edn "[1 2.5 -3 0.65]"))))

(deftest test-keyword-stays-a-colon-string-and-bareword-falls-through-to-string
  (is (= ":edit/op" (edn/parse-edn ":edit/op")))
  (is (= "org.corp.x" (edn/parse-edn "org.corp.x"))))

(deftest test-reads-escaped-string
  (is (= "a \"q\" b" (edn/parse-edn "\"a \\\"q\\\" b\""))))

(deftest test-reads-nested-map-and-vector-with-comments-and-commas
  (let [src "; a leading comment
    {:edit/id \"e1\", :edit/tags [:a :b],
     :edit/ok true :edit/n 3}"]
    (is (= {":edit/id" "e1" ":edit/tags" [":a" ":b"] ":edit/ok" true ":edit/n" 3}
           (edn/parse-edn src)))))

#?(:clj
   (deftest test-load-edn-reads-a-file
     (let [f (java.io.File/createTempFile "ake-edn" ".edn")]
       (try
         (spit f "{:k [1 2.5 true nil \"s\"]}")
         (is (= {":k" [1 2.5 true nil "s"]} (edn/load-edn (.getPath f))))
         (finally (.delete f))))))

;; ── tx-data reconstitution (edn-datomize, Phase 4 fan-out) ──────────────────────
;; The shim that lets lex/*.edn + data/*.kotoba.edn be rewritten as Datomic/Datascript
;; tx-data on disk while every existing ake.methods.* call site keeps reading the
;; original bare, string-keyed map shape unchanged.

(deftest test-tx-data-detects-the-wrap-map-preserve-shape
  (is (true? (edn/tx-data? [{":db/id" -1 ":lex.editProposal/id" "x"}])))
  (is (false? (edn/tx-data? {":id" "x"})))               ;; untransformed bare map
  (is (false? (edn/tx-data? [{":id" "x"}])))              ;; vector but no :db/id
  (is (false? (edn/tx-data? [{":db/id" -1} {":db/id" -2}]))))  ;; not single-entity

(deftest test-reconstitute-is-a-no-op-on-an-untransformed-bare-map
  (let [bare {":edit/batch" [{":edit/id" "e1"}]}]
    (is (= bare (edn/reconstitute bare "data.seed-edit-graph")))))

(deftest test-reconstitute-strips-db-id-and-namespace-back-to-bare-keys
  (let [tx [{":db/id" -1 ":lex.editProposal/lexicon" 1 ":lex.editProposal/id" "com.etzhayyim.ake.editProposal"}]]
    (is (= {":lexicon" 1 ":id" "com.etzhayyim.ake.editProposal"}
           (edn/reconstitute tx "lex.editProposal")))))

(deftest test-reconstitute-unblobs-a-pr-str-nested-value-back-to-string-keyed-structure
  ;; mirrors what edn-datomize.bb actually writes for a nested :defs value: standard Clojure
  ;; pr-str syntax (real keywords, #:ns{} shorthand disabled at write time).
  (let [blob "{:main {:type \"record\", :properties {:id {:type \"string\"}}}}"
        tx [{":db/id" -1 ":lex.editProposal/defs" blob}]]
    (is (= {":defs" {":main" {":type" "record" ":properties" {":id" {":type" "string"}}}}}
           (edn/reconstitute tx "lex.editProposal")))))

(deftest test-reconstitute-does-not-strip-a-key-namespaced-BEFORE-the-transform
  ;; wrap-map-preserve keeps an already-namespaced key (e.g. :edit/batch, ns \"edit\") verbatim —
  ;; it is a DIFFERENT namespace from any file's ns-prefix (\"data.seed-edit-graph\"), so an
  ;; exact-prefix match correctly leaves it alone instead of guessing-and-mis-stripping it to
  ;; \":batch\". This is the bug an earlier, guess-based (\"strip after the last /\") design had.
  (let [blob "[{:edit/id \"e1\", :edit/op :assert}]"
        tx [{":db/id" -1 ":edit/batch" blob}]]
    (is (= {":edit/batch" [{":edit/id" "e1" ":edit/op" ":assert"}]}
           (edn/reconstitute tx "data.seed-edit-graph")))))

(deftest test-reconstitute-ns-prefix-mismatch-leaves-keys-namespaced
  ;; passing the WRONG ns-prefix is a caller bug, not silently "close enough" — pin that it
  ;; does not accidentally strip under a prefix that doesn't match.
  (let [tx [{":db/id" -1 ":lex.editProposal/id" "x"}]]
    (is (= {":lex.editProposal/id" "x"} (edn/reconstitute tx "lex.editTriage")))))

#?(:clj (defn -main [& _] (run-tests 'ake.methods.test--edn)))
