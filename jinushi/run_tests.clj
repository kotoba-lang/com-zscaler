#!/usr/bin/env bb
;; jinushi — bb-native test runner (Clojure / babashka; no shell). Auto-generated
;; (repo-wide rule, root CLAUDE.md §"Operational code = clj/bb"): first-party tooling is
;; clj/bb, not shell. Discovers every test_*.{cljc,clj} namespace already authored under
;; this actor's tree and runs it via clojure.test — this actor had test suites but no
;; runner wired up (vitals reflex was reading as :absent).
;;
;;   bb 20-actors/jinushi/run_tests.clj      ; run from anywhere
(require '[babashka.classpath :as cp]
         '[babashka.fs :as fs]
         '[clojure.test :as t])

;; this file is 20-actors/jinushi/run_tests.clj -> classpath root is its grandparent (20-actors/)
(cp/add-classpath (str (fs/parent (fs/parent (fs/absolutize *file*)))))

(def suites
  '[jinushi.methods.test-analyze
    jinushi.methods.test-buildings
    jinushi.methods.test-cid
    jinushi.methods.test-company-link
    jinushi.methods.test-confidence
    jinushi.methods.test-coverage
    jinushi.methods.test-datom-emit
    jinushi.methods.test-diff
    jinushi.methods.test-digest
    jinushi.methods.test-dvf-values
    jinushi.methods.test-emit-all
    jinushi.methods.test-emit-real
    jinushi.methods.test-ingest
    jinushi.methods.test-jurisdiction
    jinushi.methods.test-normalize-wdqs
    jinushi.methods.test-nyc-pluto
    jinushi.methods.test-osm-buildings
    jinushi.methods.test-owner-type
    jinushi.methods.test-reconcile
    jinushi.methods.test-scale-ingest
    jinushi.methods.test-value-trend
    jinushi.methods.test-verify])

(apply require suites)

(let [{:keys [fail error]} (apply t/run-tests suites)]
  (if (zero? (+ fail error))
    (println "-- jinushi: ALL suites green --")
    (do (println "-- jinushi: FAILURES above --")
        (System/exit 1))))
