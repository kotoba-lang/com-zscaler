#!/usr/bin/env bb
;; run_tests.clj — 入札 (nyusatsu) clj/bb test suite (ADR-2606271700; repo rule: .clj not .sh).
;;
;; Run from the etzhayyim root with 20-actors on the classpath:
;;   bb --classpath 20-actors 20-actors/nyusatsu/run_tests.clj
;;
;; (The seed path in test_charter_invariants.cljc is resolved relative to the root cwd.)
(require '[clojure.test :as t]
         'nyusatsu.methods.test-normalize
         'nyusatsu.methods.test-ingest
         'nyusatsu.methods.test-social
         'nyusatsu.methods.test-charter-invariants)

(let [{:keys [fail error]}
      (t/run-tests 'nyusatsu.methods.test-normalize
                   'nyusatsu.methods.test-ingest
                   'nyusatsu.methods.test-social
                   'nyusatsu.methods.test-charter-invariants)]
  (System/exit (if (pos? (+ (or fail 0) (or error 0))) 1 0)))
