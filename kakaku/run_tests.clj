#!/usr/bin/env bb
;; kakaku 価格 — bb-native test runner (Clojure / babashka; no shell). ADR-2606072802.
;; Per the repo-wide rule (root CLAUDE.md §"Operational code = clj/bb"): first-party
;; tooling is clj/bb, NOT shell. New actors ship run_tests.clj, not run_tests.sh; this
;; replaces the former run_tests.sh (ADR-2606160842 py->clj port wave).
;;
;;   bb 20-actors/kakaku/run_tests.clj      ; run from anywhere
;;
;; py/test_autorun.clj -- fixed (2 failures) and wired in below:
;;   - append-only-and-tamper's tamper mechanism string-replaced a hardcoded magic price
;;     (":kakaku.obs/spread 700") that no longer exists in the current seed (the seed's
;;     offers for jan_4901777300443 changed; the observed spread is now 0) -- the
;;     replace silently no-op'd, so nothing was ever actually tampered and verify-chain
;;     correctly reported :ok true. Made robust to seed drift by corrupting the tx's own
;;     stored :tx/cid directly instead of a data-dependent magic number.
;;   - cid-golden-stable's pin moved for the same reason (the seed genuinely changed) --
;;     re-verified via deterministic-resume-safe (2 independent runs over the current
;;     seed agree) before updating the literal.
(require '[babashka.classpath :as cp]
         '[babashka.fs :as fs]
         '[clojure.test :as t])

;; this file is 20-actors/kakaku/run_tests.clj → classpath root is its grandparent (20-actors/)
(cp/add-classpath (str (fs/parent (fs/parent (fs/absolutize *file*)))))

;; kakaku.py.test-agent-parity (python3-subprocess-vs-clj LIVE parity) is gone: it always
;; compared against a py/agent.py that no longer exists anywhere in this repo, so every run
;; silently no-op'd via its own graceful-skip fallback. py/{agent,autorun,ingest,kotoba}.clj*
;; were each actor's real implementation, just misnamed -- moved to methods/ (agent/autorun/
;; ingest/kotoba), namespaces renamed X.py.* -> X.methods.*.
(def suites '[kakaku.methods.test-kakaku-edn
              kakaku.kotoba.test-ingest-mcp
              kakaku.methods.test-agent
              kakaku.methods.test-autorun
              kakaku.methods.test-ingest
              kakaku.viz.test-build-viz])

(apply require suites)

(let [r (apply t/run-tests suites)]
  (println "==> kakaku:" (select-keys r [:test :pass :fail :error]))
  (System/exit (if (or (pos? (:fail r)) (pos? (:error r))) 1 0)))
