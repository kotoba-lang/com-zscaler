#!/usr/bin/env bb
;; hibiki 響 — bb-native test runner (Clojure / babashka; no shell).
;;
;; Per the repo-wide rule (root CLAUDE.md §"Operational code = clj/bb"): first-party
;; tooling is clj/bb, NOT shell. This supersedes the former run_tests.sh.
;;
;;   bb 20-actors/hibiki/run_tests.clj      ; run from anywhere
;;
;; The classpath root (the absolute 20-actors/ dir) is derived from THIS file's own
;; location, so `hibiki.methods.*` resolves — and so the charter-gate suite's
;; `*file*`-relative lex/ lookup resolves to an absolute path — without a wrapper
;; shell or an external --classpath flag. Both suites are also auto-discovered
;; fleet-wide by `bb run test:actors` (ADR-2606131500); this is the targeted runner.
(require '[babashka.classpath :as cp]
         '[babashka.fs :as fs]
         '[clojure.test :as t])

;; this file is 20-actors/hibiki/run_tests.clj → classpath root is its grandparent (20-actors/)
(cp/add-classpath (str (fs/parent (fs/parent (fs/absolutize *file*)))))

(def suites
  '[hibiki.methods.test-charter-gates
    hibiki.methods.test-present-plan])

(apply require suites)

(let [{:keys [fail error]} (apply t/run-tests suites)]
  (if (zero? (+ fail error))
    (println "── hibiki: ALL suites green ──")
    (do (println "── hibiki: FAILURES above ──")
        (System/exit 1))))
