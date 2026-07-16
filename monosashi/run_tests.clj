#!/usr/bin/env bb
;; monosashi 物差し — bb-native test runner (Clojure / babashka; no shell, per ADR-2606072802).
;;
;;   bb 20-actors/monosashi/run_tests.clj      ; run from anywhere
;;
;; The classpath root (the absolute 20-actors/ dir) is derived from THIS file's own location, so
;; `monosashi.methods.*` resolves and the test namespaces' `*file*`-relative seed lookups resolve
;; to absolute paths — without a wrapper shell or an external --classpath flag.
(require '[babashka.classpath :as cp]
         '[babashka.fs :as fs]
         '[clojure.test :as t])

;; this file is 20-actors/monosashi/run_tests.clj → classpath root is its grandparent (20-actors/)
(cp/add-classpath (str (fs/parent (fs/parent (fs/absolutize *file*)))))

(def suites
  '[monosashi.tests.test-score
    monosashi.tests.test-social])

(apply require suites)

(let [{:keys [fail error]} (apply t/run-tests suites)]
  (if (zero? (+ fail error))
    (println "── monosashi: ALL suites green ──")
    (do (println "── monosashi: FAILURES above ──")
        (System/exit 1))))
