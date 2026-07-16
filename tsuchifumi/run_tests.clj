#!/usr/bin/env bb
;; tsuchifumi 土踏み — bb-native test runner (Clojure / babashka; no shell). ADR-2606072802.
;; Per the repo-wide rule (root CLAUDE.md §"Operational code = clj/bb"): first-party
;; tooling is clj/bb, NOT shell. New actors ship run_tests.clj, not run_tests.sh.
;;
;;   bb 20-actors/tsuchifumi/run_tests.clj      ; run from anywhere
;;
;; Each suite is run as its own `bb --classpath 20-actors <file>` subprocess from the repo
;; root, exactly mirroring the former run_tests.sh.
(require '[babashka.process :refer [shell]]
         '[babashka.fs :as fs])

;; this file is 20-actors/tsuchifumi/run_tests.clj → repo root is 2 levels up from its dir
(def repo-root (-> *file* fs/absolutize fs/parent fs/parent fs/parent))

(def suites
  ["20-actors/tsuchifumi/methods/test_ontology.cljc"
   "20-actors/tsuchifumi/methods/test_analyze.cljc"
   "20-actors/tsuchifumi/methods/test_sysdyn.cljc"
   "20-actors/tsuchifumi/methods/test_risk.cljc"
   "20-actors/tsuchifumi/methods/test_coscientist.cljc"
   "20-actors/tsuchifumi/methods/test_social.cljc"
   "20-actors/tsuchifumi/methods/test_kotoba.cljc"
   "20-actors/tsuchifumi/methods/test_autorun.cljc"
   "20-actors/tsuchifumi/methods/test_viz.cljc"])

(let [fails (reduce (fn [acc s]
                       (println (str "== " s " =="))
                       (let [{:keys [exit]} (shell {:dir (str repo-root) :continue true}
                                                    "bb" "--classpath" "20-actors" s)]
                         (if (zero? exit) acc (do (println (str "FAILED: " s)) (conj acc s)))))
                     [] suites)]
  (System/exit (if (empty? fails) 0 1)))
