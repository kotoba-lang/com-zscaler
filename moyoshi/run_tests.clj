#!/usr/bin/env bb
;; moyoshi 催し — bb-native test runner (Clojure / babashka; no shell).
;;
;; Per the repo-wide rule (root CLAUDE.md §"Operational code = clj/bb"; ADR-2606072802
;; new actors ship run_tests.clj, NOT run_tests.sh): first-party tooling is clj/bb.
;;
;;   bb 20-actors/moyoshi/run_tests.clj      ; run from anywhere
;;
;; The classpath root (the absolute 20-actors/ dir) is derived from THIS file's own
;; location, so `moyoshi.methods.*` / `moyoshi.tests.*` resolve — and the test's
;; `*file*`-relative data/ lookup resolves to an absolute path — without a wrapper
;; shell or an external --classpath flag.
(require '[babashka.classpath :as cp]
         '[babashka.fs :as fs]
         '[clojure.test :as t])

;; this file is 20-actors/moyoshi/run_tests.clj → classpath root is its grandparent (20-actors/)
(cp/add-classpath (str (fs/parent (fs/parent (fs/absolutize *file*)))))

(def suites
  '[moyoshi.tests.test-moyoshi
    moyoshi.tests.test-r2
    moyoshi.tests.test-r3])

(apply require suites)

(let [{:keys [fail error]} (apply t/run-tests suites)]
  (if (zero? (+ fail error))
    (println "── moyoshi 催し: ALL suites green ──")
    (do (println "── moyoshi 催し: FAILURES above ──")
        (System/exit 1))))
