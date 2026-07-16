#!/usr/bin/env nbb
(ns akashi.run-tests
  (:require ["child_process" :as child-process]
            ["path" :as path]))

(def test-expr
  "(require (quote clojure.test)
            (quote akashi.adapters.test-dry-run-fixtures)
            (quote akashi.adapters.test-regulator-bulk-fixture-parser)
            (quote akashi.adapters.test-lexicon-shape-validator)
            (quote akashi.adapters.test-edn-query)
            (quote akashi.adapters.test-public-page-scribe))
   (let [r (clojure.test/run-tests
            (quote akashi.adapters.test-dry-run-fixtures)
            (quote akashi.adapters.test-regulator-bulk-fixture-parser)
            (quote akashi.adapters.test-lexicon-shape-validator)
            (quote akashi.adapters.test-edn-query)
            (quote akashi.adapters.test-public-page-scribe))]
     (System/exit (if (zero? (+ (:fail r) (:error r))) 0 1)))")

(def script-arg
  (or (some #(when (.endsWith % "run_tests.cljs") %)
            (array-seq js/process.argv))
      (aget js/process.argv 1)))
(def script-path (.resolve path script-arg))
(def repo-root (.resolve path (.dirname path script-path) ".." ".."))
(def result
  (.spawnSync child-process "bb" #js ["--config" (.join path repo-root "bb.edn") "-e" test-expr]
              #js {:cwd repo-root :stdio "inherit"}))

(js/process.exit (or (.-status result) 1))
