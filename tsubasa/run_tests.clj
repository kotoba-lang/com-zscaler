#!/usr/bin/env bb
;; tsubasa 翼 — bb-native test runner (Clojure / babashka; no shell). ADR-2606072802.
;;
;; Per the repo-wide rule (root CLAUDE.md §"Operational code = clj/bb"): first-party
;; tooling is clj/bb, NOT shell. This supersedes the former run_tests.sh.
;;
;;   bb 20-actors/tsubasa/run_tests.clj      ; run from anywhere
;;
;; Classpath root (the absolute 20-actors/ dir) is derived from THIS file's location, so
;; both `tsubasa.methods.*` and the py->clj `tsubasa.methods.test-agent` resolve, and the
;; *file*-relative seed lookups in the suites work without a wrapper or --classpath flag.
(require '[babashka.classpath :as cp]
         '[babashka.fs :as fs]
         '[clojure.test :as t])

;; this file is 20-actors/tsubasa/run_tests.clj → classpath root is its grandparent (20-actors/)
(cp/add-classpath (str (fs/parent (fs/parent (fs/absolutize *file*)))))

(def suites
  '[tsubasa.methods.test-analyze
    tsubasa.methods.test-kotoba
    tsubasa.methods.test-autorun
    tsubasa.methods.test-seed-integrity
    tsubasa.methods.test-ingest
    tsubasa.methods.test-digest
    tsubasa.methods.test-fetch
    tsubasa.methods.test-identity
    tsubasa.methods.test-kotoba-bridge
    tsubasa.methods.test-openflights
    tsubasa.methods.test-agent])

(apply require suites)

(let [{:keys [fail error]} (apply t/run-tests suites)]
  (if (zero? (+ fail error))
    (println "── tsubasa: ALL suites green ──")
    (do (println "── tsubasa: FAILURES above ──")
        (System/exit 1))))
