#!/usr/bin/env bb
;; uzu 渦 — log read-back tests: round-trip + as-of time-travel.
;; Run: bb --classpath 20-actors 20-actors/uzu/methods/test_query.cljc
(ns uzu.methods.test-query
  (:require [uzu.methods.uzu-edn :as ue]
            [uzu.methods.autorun :as auto]
            [uzu.methods.query :as q]
            [uzu.methods.kotoba :as k]
            [clojure.test :refer [deftest is run-tests]]
            [clojure.java.io :as io]))

(def seed (ue/classify (ue/load-edn "20-actors/uzu/kotoba/seed.edn")))
(defn tmp [] (str (System/getProperty "java.io.tmpdir") "/uzu-q-" (gensym) ".kotoba.edn"))

;; ── pure index/pull ──────────────────────────────────────────────────────────
(deftest index-folds-eavt
  (let [idx (q/index [[":db/add" "e1" ":a" 1] [":db/add" "e1" ":b" 2] [":db/add" "e2" ":a" 9]])]
    (is (= {":a" 1 ":b" 2} (q/pull idx "e1")))
    (is (= {":a" 9} (q/pull idx "e2")))))

(deftest last-write-wins
  (is (= {":x" 2} (q/pull (q/index [[":db/add" "e" ":x" 1] [":db/add" "e" ":x" 2]]) "e"))))

;; ── round-trip through a real persisted log ──────────────────────────────────
(deftest persisted-log-reads-back
  (let [p (tmp)]
    (auto/beat {:seed seed :tx-id "b1" :as-of "a1" :log-path p})
    (let [cd (q/colony-digest p)]
      (is (= 1 (get cd ":uzu.digest/n-alive")) "the persisted colony digest reads back")
      (is (= 3 (get cd ":uzu.digest/n"))))
    (let [gdp (q/flow p "gdp")]
      (is (= "USD/yr" (get gdp ":uzu.flow/unit")) "a measured flow reads back with its native unit")
      (is (= true (get gdp ":uzu.flow/reference-only"))))
    (is (pos? (count (q/entities-of-kind (q/index-of-log p) "uzu:organism/"))))
    (.delete (io/file p))))

;; ── as-of time-travel ────────────────────────────────────────────────────────
(deftest as-of-reconstructs-history
  ;; build three chained txs, each setting ctr :n to 1, 2, 3
  (let [txs (loop [acc [] prev "" i 1]
              (if (> i 3) acc
                  (let [tx (k/make-tx [[":db/add" "ctr" ":n" i]] (str "t" i) "a" prev)]
                    (recur (conj acc tx) (get tx ":tx/cid") (inc i)))))]
    (is (= 1 (get (q/pull (q/index (q/datoms-asof txs 1)) "ctr") ":n")) "as-of tx 1")
    (is (= 2 (get (q/pull (q/index (q/datoms-asof txs 2)) "ctr") ":n")) "as-of tx 2")
    (is (= 3 (get (q/pull (q/index (q/datoms-asof txs nil)) "ctr") ":n")) "as-of latest")))

(deftest empty-log-pulls-nothing
  (is (= {} (q/index []))))

(let [{:keys [fail error]} (run-tests 'uzu.methods.test-query)]
  (when (pos? (+ fail error)) (System/exit 1)))
