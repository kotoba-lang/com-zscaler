#!/usr/bin/env bash
# utsushie 写し絵 — run the whole cljc test suite with one command.
# cljc-native (ADR-2606261200): the canonical impl is methods/render_plan.cljc; the gate
# invariants (U1–U6) live in methods/render_plan.cljc + lex/video.edn (touch one, touch both).
set -euo pipefail
cd "$(dirname "$0")/../.."   # repo root
exec bb -cp "20-actors" -e '
(require (quote clojure.test)
         (quote utsushie.methods.test-render-plan)
         (quote utsushie.methods.test-charter-gates))
(let [r (apply clojure.test/run-tests
                (quote [utsushie.methods.test-render-plan
                        utsushie.methods.test-charter-gates]))]
  (System/exit (if (zero? (+ (:fail r) (:error r))) 0 1)))'
