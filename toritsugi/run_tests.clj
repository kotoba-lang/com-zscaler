#!/usr/bin/env bb
;; toritsugi 取次 — bb-native test runner (Clojure / babashka; NO shell).
;; ADR-2606072802 ( enforced-forward: new actors ship run_tests.clj, not run_tests.sh ).
;;
;; Per the repo-wide rule (root CLAUDE.md §"Operational code = clj/bb over the kotoba
;; Datom log"): first-party tooling is clj/bb, NOT shell. The grandfathered
;; run_tests.sh is left untouched; this .clj is the canonical runner going forward.
;;
;;   bb 20-actors/toritsugi/run_tests.clj        ; run from anywhere (repo root or elsewhere)
;;
;; Runs the two manifest/charter suites that live under methods/:
;;   - toritsugi.methods.test-charter-gates        (G1–G15 + Governor HARD surface + BPMN G15)
;;   - toritsugi.methods.test-manifest-invariants  (manifest ↔ lexicon ↔ cell-disk parity)
;;
;; The JVM suites (governor-contract + flow, 25 tests) run via `clojure -M:test`
;; (deps.edn :test alias → cognitect test-runner over test/). This bb runner covers
;; the methods/ suites that don't need the langgraph/langchain JVM deps.
;;
;; Classpath root (the absolute 20-actors/ dir) is derived from THIS file's location,
;; so toritsugi.methods.* resolves and the *file*-relative lookups in the suites work
;; without a --classpath flag or a wrapper.
(require '[babashka.classpath :as cp]
         '[babashka.fs :as fs]
         '[clojure.test :as t])

;; this file is 20-actors/toritsugi/run_tests.clj → classpath root is its parent's parent (20-actors/)
(cp/add-classpath (str (fs/parent (fs/parent (fs/absolutize *file*)))))

(def suites
  '[toritsugi.methods.test-charter-gates
    toritsugi.methods.test-manifest-invariants])

(apply require suites)

(let [{:keys [fail error pass]} (apply t/run-tests suites)]
  (if (zero? (+ fail error))
    (println (str "── toritsugi 取次: charter-gate + manifest-invariant suites green ("
                  (or pass 0) " assertions) ──"))
    (do (println "── toritsugi 取次: FAILURES above ──")
        (System/exit 1))))
