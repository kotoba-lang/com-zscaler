(ns mimamori.tests.test-cell
  "mimamori cell-runner entry tests — fire contract + registry consistency.
  Port of tests/test_cell.py."
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.test :refer [deftest is]]
            [mimamori.cell :as cell]))

(defn- tmplog []
  (str (java.nio.file.Files/createTempDirectory
        "mimamori-cell" (make-array java.nio.file.attribute.FileAttribute 0))
       "/log.kotoba.edn"))

(deftest fire-runs-one-cycle
  (let [log (tmplog)
        s1 (cell/fire log)]
    (is (= 1 (:cycle s1)))
    (is (= 1 (:chain-length s1)))
    (is (>= (:offers-emitted s1) 6))
    (is (= 2 (get-in s1 [:shakai :minted-units])))
    (let [s2 (cell/fire log)]                 ;; next fire = next cycle (resume from log)
      (is (= 2 (:cycle s2)))
      (is (not= (:cid s2) (:cid s1))))))

(deftest fire-summary-no-did
  (let [s (cell/fire (tmplog))]
    (is (not (str/includes? (str s) "did:")))        ;; G5 aggregate-only
    (is (not (str/includes? (str s) "fictional")))))

(deftest registry-entry-consistent
  (let [actor-dir (-> (io/resource "mimamori/cell.cljc") io/file .getParentFile)
        repo-root (-> actor-dir .getParentFile .getParentFile)
        edn (slurp (io/file repo-root "50-infra" "cluster" "murakumo" "cell-runner" "cells.edn"))]
    (is (= 1 (count (re-seq #"MimamoriHeartbeatCell" edn))))
    (is (str/includes? edn ":module \"mimamori.cell\" :entry \"fire\"")) ;; contract matches this ns
    (is (some? (ns-resolve 'mimamori.cell 'fire)))
    (is (str/includes? edn ":expr \"23 * * * *\""))                      ;; off-minute, collision-free
    (is (= 1 (count (re-seq #":healthz_port 13080" edn))))))            ;; unique port
