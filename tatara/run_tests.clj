#!/usr/bin/env bb
;; tatara 鑪 — bb-native test runner (Clojure / babashka; no shell).
;;
;;   bb 20-actors/tatara/run_tests.clj      ; run from anywhere
;;
;; The classpath root (the absolute 20-actors/ dir) is derived from THIS file's
;; own location, so `tatara.methods.*` resolves — and so the test namespaces'
;; `(-> *file* io/file .getParentFile …)` seed lookups resolve to absolute paths —
;; without a wrapper shell or an external --classpath flag.
(require '[babashka.classpath :as cp]
         '[babashka.fs :as fs]
         '[clojure.test :as t])

;; this file is 20-actors/tatara/run_tests.clj → classpath root is its grandparent (20-actors/)
(cp/add-classpath (str (fs/parent (fs/parent (fs/absolutize *file*)))))

(def suites
  '[tatara.methods.test-analyze
    tatara.methods.test-kotoba
    tatara.methods.test-autorun
    tatara.methods.test-lexicons
    tatara.methods.test-crosscheck
    tatara.methods.test-maturity
    tatara.methods.test-seed-integrity
    tatara.methods.test-viz
    tatara.methods.test-compose
    tatara.methods.test-robustness])

(apply require suites)

(let [{:keys [fail error]} (apply t/run-tests suites)]
  (if (zero? (+ fail error))
    (println "── tatara: ALL suites green ──")
    (do (println "── tatara: FAILURES above ──")
        (System/exit 1))))
