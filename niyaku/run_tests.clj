#!/usr/bin/env bb
;; niyaku 荷役 — bb-native test runner (Clojure / babashka; no shell). ADR-2606072802.
;; Per the repo-wide rule (root CLAUDE.md §"Operational code = clj/bb"): first-party
;; tooling is clj/bb, NOT shell. New actors ship run_tests.clj, not run_tests.sh; this
;; replaces the former run_tests.sh.
;;
;;   bb 20-actors/niyaku/run_tests.clj      ; run from anywhere
;;
;; methods/isaac_sway_sim.cljc's resolve-py-src captured *file* lazily inside its own
;; function body — only reliably bound during the file's own top-level compilation, the
;; same bug class fixed for himotoki/keizu this session. Fixed by capturing it once in a
;; top-level def (this-file) instead.
(require '[babashka.classpath :as cp]
         '[babashka.fs :as fs]
         '[clojure.test :as t])

;; this file is 20-actors/niyaku/run_tests.clj → classpath root is its grandparent (20-actors/)
(cp/add-classpath (str (fs/parent (fs/parent (fs/absolutize *file*)))))

(def suites '[niyaku.cells.test-state-machine
              niyaku.methods.test-agv-transfer
              niyaku.methods.test-crane-dynamics
              niyaku.methods.test-isaac-sway-sim
              niyaku.methods.test-stow-plan
              niyaku.methods.test-terminal-cycle
              niyaku.methods.test-agv-transfer-parity
              niyaku.methods.test-crane-dynamics-parity
              niyaku.methods.test-stow-plan-parity
              niyaku.methods.test-terminal-cycle-parity])

(apply require suites)

(let [{:keys [fail error]} (apply t/run-tests suites)]
  (System/exit (if (zero? (+ fail error)) 0 1)))
