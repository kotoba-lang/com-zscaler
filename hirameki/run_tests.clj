#!/usr/bin/env bb
;; hirameki — bb-native test runner (Clojure / babashka; no shell). ADR-2606072802.
;; Per the repo-wide rule (root CLAUDE.md §"Operational code = clj/bb"): first-party
;; tooling is clj/bb, NOT shell. New actors ship run_tests.clj, not run_tests.sh.
;;
;;   bb 20-actors/hirameki/run_tests.clj      ; run from anywhere
;;
;; No explicit --classpath: bb.edn's own :paths/:deps must resolve (test_cid.cljc needs the
;; multiformats-clj git dep declared there; an explicit --classpath override bypasses bb.edn
;; entirely and drops it, breaking that suite even though 20-actors is already in :paths).
;; Each suite is run as its own `bb <file>` subprocess from the repo root, exactly mirroring
;; the former run_tests.sh.
(require '[babashka.process :refer [shell]]
         '[babashka.fs :as fs])

;; this file is 20-actors/hirameki/run_tests.clj → repo root is 2 levels up from its dir
(def repo-root (-> *file* fs/absolutize fs/parent fs/parent fs/parent))

(def suites
  ["20-actors/hirameki/methods/test_hirameki_edn.cljc"
   "20-actors/hirameki/methods/test_analyze.cljc"
   "20-actors/hirameki/methods/test_cid.cljc"
   "20-actors/hirameki/methods/test_dataset.cljc"
   "20-actors/hirameki/methods/test_ingest.cljc"
   "20-actors/hirameki/methods/test_kotoba.cljc"
   "20-actors/hirameki/methods/test_autorun.cljc"])

(let [fails (reduce (fn [acc s]
                       (println (str "== " s " =="))
                       (let [{:keys [exit]} (shell {:dir (str repo-root) :continue true} "bb" s)]
                         (if (zero? exit) acc (do (println (str "FAILED: " s)) (conj acc s)))))
                     [] suites)]
  (System/exit (if (empty? fails) 0 1)))
