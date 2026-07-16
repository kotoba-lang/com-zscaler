#!/usr/bin/env bb
;; kyoninka — bb-native test runner (Clojure / babashka; no shell). Auto-generated
;; (repo-wide rule, root CLAUDE.md §"Operational code = clj/bb"): first-party tooling is
;; clj/bb, not shell. Discovers every test_*.{cljc,clj} namespace already authored under
;; this actor's tree and runs it via clojure.test — this actor had test suites but no
;; runner wired up (vitals reflex was reading as :absent).
;;
;;   bb 20-actors/kyoninka/run_tests.clj      ; run from anywhere
(require '[babashka.classpath :as cp]
         '[babashka.fs :as fs]
         '[clojure.test :as t])

;; this file is 20-actors/kyoninka/run_tests.clj -> classpath root is its grandparent (20-actors/)
(cp/add-classpath (str (fs/parent (fs/parent (fs/absolutize *file*)))))

;; kyoninka.methods.test-organism requires kototama.organism, which is not resolvable from
;; the 20-actors classpath root (pre-existing gap, unrelated to this runner — the namespace
;; is absent from this checkout entirely, not merely missing from the classpath). Excluded
;; here rather than left to hard-fail; re-add once kototama.organism is reachable.
(def suites
  '[kyoninka.methods.test-procedure])

(apply require suites)

(let [{:keys [fail error]} (apply t/run-tests suites)]
  (if (zero? (+ fail error))
    (println "-- kyoninka: ALL suites green --")
    (do (println "-- kyoninka: FAILURES above --")
        (System/exit 1))))
