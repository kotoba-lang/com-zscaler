#!/usr/bin/env bash
# suji — clj/bb test suite via canonical auto-discovery (etzhayyim.tools.discovery,
# ADR-2606131500). The old `bb test:suji` task was removed when `test:actors` superseded
# the per-actor task lists, leaving this shim pointing at a non-existent task; repointed
# to filter the auto-discovered actor test namespaces down to suji's (zero bb.edn churn).
set -euo pipefail
cd "$(dirname "$0")/../.."
exec bb -e '(require (quote etzhayyim.tools.discovery) (quote clojure.test) (quote clojure.string))
(let [all ((requiring-resolve (quote etzhayyim.tools.discovery/actor-test-nss)))
      mine (filter #(clojure.string/starts-with? (str %) "suji.") all)]
  (when (empty? mine) (println "no suji test namespaces discovered") (System/exit 1))
  (apply require mine)
  (let [r (apply clojure.test/run-tests mine)]
    (System/exit (if (pos? (+ (:fail r) (:error r))) 1 0))))'
