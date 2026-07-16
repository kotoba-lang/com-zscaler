#!/usr/bin/env bb
;; chie — bb-native test runner (Clojure / babashka; no shell). Auto-generated
;; (repo-wide rule, root CLAUDE.md §"Operational code = clj/bb"): first-party tooling is
;; clj/bb, not shell. Discovers every test_*.{cljc,clj} namespace already authored under
;; this actor's tree and runs it via clojure.test — this actor had test suites but no
;; runner wired up (vitals reflex was reading as :absent).
;;
;;   bb 20-actors/chie/run_tests.clj      ; run from anywhere
(require '[babashka.classpath :as cp]
         '[babashka.fs :as fs]
         '[clojure.test :as t])

;; this file is 20-actors/chie/run_tests.clj -> classpath root is its grandparent (20-actors/)
(cp/add-classpath (str (fs/parent (fs/parent (fs/absolutize *file*)))))

(def suites
  '[chie.methods.test-autorun
    chie.methods.test-cross-axis
    chie.methods.test-datom-emit
    chie.methods.test-digest
    chie.methods.test-ingest
    chie.methods.test-kqe
    chie.methods.test-query
    chie.tests.test-analyze
    chie.tests.test-cell
    chie.tests.test-coverage
    chie.tests.test-schema
    chie.tests.test-verify])

(apply require suites)

(let [{:keys [fail error]} (apply t/run-tests suites)]
  (if (zero? (+ fail error))
    (println "-- chie: ALL suites green --")
    (do (println "-- chie: FAILURES above --")
        (System/exit 1))))
