#!/usr/bin/env bb
;; kaname — bb-native test runner (Clojure / babashka; no shell). Auto-generated
;; (repo-wide rule, root CLAUDE.md §"Operational code = clj/bb"): first-party tooling is
;; clj/bb, not shell. Discovers every test_*.{cljc,clj} namespace already authored under
;; this actor's tree and runs it via clojure.test — this actor had test suites but no
;; runner wired up (vitals reflex was reading as :absent).
;;
;;   bb 20-actors/kaname/run_tests.clj      ; run from anywhere
(require '[babashka.classpath :as cp]
         '[babashka.fs :as fs]
         '[clojure.test :as t])

;; this file is 20-actors/kaname/run_tests.clj -> classpath root is its grandparent (20-actors/)
(cp/add-classpath (str (fs/parent (fs/parent (fs/absolutize *file*)))))

(def suites
  '[kaname.tests.test-bridge
    kaname.tests.test-centrality
    kaname.tests.test-coverage
    kaname.tests.test-energy-join
    kaname.tests.test-gates
    kaname.tests.test-graph
    kaname.tests.test-ie-flow
    kaname.tests.test-ingest
    kaname.tests.test-join
    kaname.tests.test-kotoba
    kaname.tests.test-leverage-concentration
    kaname.tests.test-osekkai
    kaname.tests.test-route
    kaname.tests.test-sos])

(apply require suites)

(let [{:keys [fail error]} (apply t/run-tests suites)]
  (if (zero? (+ fail error))
    (println "-- kaname: ALL suites green --")
    (do (println "-- kaname: FAILURES above --")
        (System/exit 1))))
