#!/usr/bin/env bb
;; kenchi 検地 — run the whole test suite with one command (clj/bb; replaces run_tests.sh).
;;   bb run_tests.clj
;; Each suite is a standalone bb script that exits non-zero on failure.
(ns run-tests
  (:require [babashka.process :refer [shell]]
            [clojure.java.io :as io]))

(def here (-> *file* io/file .getCanonicalFile .getParentFile))
(def suites ["methods/test_charter_gates.clj"])

(defn -main [& _]
  (let [fails (reduce (fn [acc s]
                        (let [{:keys [exit]} (shell {:dir here :continue true} "bb" s)]
                          (if (zero? exit) acc (conj acc s))))
                      [] suites)]
    (if (empty? fails)
      (println "── kenchi: ALL suites green ──")
      (do (println "── kenchi: FAILURES:" fails "──") (System/exit 1)))))

(-main)
