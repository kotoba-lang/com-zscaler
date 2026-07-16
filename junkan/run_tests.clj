#!/usr/bin/env bb
;; junkan — bb-native test runner (Clojure / babashka; no shell). Auto-generated
;; (repo-wide rule, root CLAUDE.md §"Operational code = clj/bb"): first-party tooling is
;; clj/bb, not shell. Discovers every test_*.{cljc,clj} namespace already authored under
;; this actor's tree and runs it via clojure.test — this actor had test suites but no
;; runner wired up (vitals reflex was reading as :absent).
;;
;;   bb 20-actors/junkan/run_tests.clj      ; run from anywhere
(require '[babashka.classpath :as cp]
         '[babashka.fs :as fs]
         '[clojure.test :as t])

;; this file is 20-actors/junkan/run_tests.clj -> classpath root is its grandparent (20-actors/)
(cp/add-classpath (str (fs/parent (fs/parent (fs/absolutize *file*)))))

(def suites
  '[junkan.methods.test-analyze
    junkan.methods.test-autorun
    junkan.methods.test-charter-gates
    junkan.methods.test-consumer-culture
    junkan.methods.test-country-region-actors
    junkan.methods.test-demography
    junkan.methods.test-history
    junkan.methods.test-junkan-edn
    junkan.methods.test-kotoba
    junkan.methods.test-query
    junkan.methods.test-scorecard
    junkan.methods.test-validate
    junkan.methods.test-waste-sanitation])

(apply require suites)

(let [{:keys [fail error]} (apply t/run-tests suites)]
  (if (zero? (+ fail error))
    (println "-- junkan: ALL suites green --")
    (do (println "-- junkan: FAILURES above --")
        (System/exit 1))))
