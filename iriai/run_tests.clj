#!/usr/bin/env bb
;; iriai 入会 — bb-native test runner (Clojure / babashka; no shell). ADR-2606072802.
;;
;; Per the repo-wide rule (root CLAUDE.md §"Operational code = clj/bb"): first-party
;; tooling is clj/bb, NOT shell. New actors ship run_tests.clj, not run_tests.sh.
;;
;;   bb 20-actors/iriai/run_tests.clj      ; run from anywhere
;;
;; Classpath root (the absolute 20-actors/ dir) is derived from THIS file's location, so
;; the iriai.methods.* namespaces resolve and the *file*-relative seed lookups in the
;; suites work without a wrapper or --classpath flag.
(require '[babashka.classpath :as cp]
         '[babashka.fs :as fs]
         '[clojure.test :as t])

;; this file is 20-actors/iriai/run_tests.clj → classpath root is its parent's parent (20-actors/)
(cp/add-classpath (str (fs/parent (fs/parent (fs/absolutize *file*)))))

(def suites
  '[iriai.methods.test-infra
    iriai.methods.test-fund
    iriai.methods.test-manage
    iriai.methods.test-twin
    iriai.methods.test-maintain
    iriai.methods.test-forecast
    iriai.methods.test-gates
    iriai.methods.test-kotoba
    iriai.methods.test-kotoba-bridge
    iriai.methods.test-social
    iriai.methods.test-identity
    iriai.methods.test-autorun
    iriai.test-cell])

(apply require suites)

(let [{:keys [fail error]} (apply t/run-tests suites)]
  (if (zero? (+ fail error))
    (println "── iriai 入会: ALL suites green ──")
    (do (println "── iriai 入会: FAILURES above ──")
        (System/exit 1))))
