#!/usr/bin/env bb
;; kumi 組 — test runner (babashka; run_tests.clj not run_tests.sh, per repo
;; "new actors ship run_tests.clj" convention). Run from repo root:
;;   bb 20-actors/kumi/run_tests.clj
(require '[clojure.test :as t])

(binding [*compile-path* nil]
  (require 'kumi.methods.kumi 'kumi.tests.test-kumi))

(let [{:keys [fail error]} (t/run-tests 'kumi.tests.test-kumi)]
  (System/exit (if (pos? (+ fail error)) 1 0)))
