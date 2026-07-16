#!/usr/bin/env bb
;; umisachi — bb-native test runner (Clojure / babashka; no shell). Auto-generated
;; (repo-wide rule, root CLAUDE.md §"Operational code = clj/bb"): first-party tooling is
;; clj/bb, not shell. Discovers every test_*.{cljc,clj} namespace already authored under
;; this actor's tree and runs it via clojure.test — this actor had test suites but no
;; runner wired up (vitals reflex was reading as :absent).
;;
;;   bb 20-actors/umisachi/run_tests.clj      ; run from anywhere
(require '[babashka.classpath :as cp]
         '[babashka.fs :as fs]
         '[clojure.test :as t])

;; this file is 20-actors/umisachi/run_tests.clj -> classpath root is its grandparent (20-actors/)
(cp/add-classpath (str (fs/parent (fs/parent (fs/absolutize *file*)))))

(def suites
  '[umisachi.tests.test-analyze
    umisachi.tests.test-autorun
    umisachi.tests.test-coverage
    umisachi.tests.test-datom-emit
    umisachi.tests.test-ingest
    umisachi.tests.test-integration
    umisachi.tests.test-kotoba
    umisachi.tests.test-plan
    umisachi.tests.test-social])

(apply require suites)

(let [{:keys [fail error]} (apply t/run-tests suites)]
  (if (zero? (+ fail error))
    (println "-- umisachi: ALL suites green --")
    (do (println "-- umisachi: FAILURES above --")
        (System/exit 1))))
