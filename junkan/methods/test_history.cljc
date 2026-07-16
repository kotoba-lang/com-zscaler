#!/usr/bin/env bb
;; junkan 循環 — as-of / regime-trajectory (history) tests.
;; Run:  bb --classpath 20-actors 20-actors/junkan/methods/test_history.cljc
(ns junkan.methods.test-history
  (:require [junkan.methods.history :as h]
            [junkan.methods.kotoba :as k]
            [junkan.methods.junkan-edn :as je]
            [junkan.methods.analyze :as az]
            [clojure.java.io :as io]
            [clojure.test :refer [deftest is run-tests]]))

(defn- tmp [] (str (System/getProperty "java.io.tmpdir")
                   "/junkan-hist-" (hash (str (gensym))) ".edn"))

;; build a 2-tx ledger where one stock's regime flips :vicious → :virtuous
(defn- mk-tx [datoms prev] (k/make-tx datoms "t" "a" prev))

(deftest detects-regime-shift
  (let [p (tmp)]
    (try
      (let [d1 [[":db/add" "junkan-stock:coercion-asymmetry" ":junkan.gov.stock/regime" ":vicious"]
                [":db/add" "junkan-stock:information-asymmetry" ":junkan.gov.stock/regime" ":transitioning"]]
            d2 [[":db/add" "junkan-stock:coercion-asymmetry" ":junkan.gov.stock/regime" ":virtuous"]
                [":db/add" "junkan-stock:information-asymmetry" ":junkan.gov.stock/regime" ":transitioning"]]
            c1 (k/append-tx (mk-tx d1 (k/head-cid p)) p)
            _  (k/append-tx (mk-tx d2 (k/head-cid p)) p)
            txs (k/read-log p)
            shifts (h/regime-shifts txs)]
        (is (= 1 (count shifts)) "one step has a shift")
        (is (= [["junkan-stock:coercion-asymmetry" ":vicious" ":virtuous"]]
               (:stock-shifts (first shifts)))
            "the flipped stock is detected; the unchanged stock is not")
        (let [s (h/summary txs)]
          (is (= 2 (:txs s)))
          (is (= 1 (:stock-shift-count s)))))
      (finally (io/delete-file p true)))))

(deftest no-shift-on-identical-txs
  (let [p (tmp)]
    (try
      (let [d [[":db/add" "junkan-stock:economic-capture" ":junkan.gov.stock/regime" ":neutral"]]]
        (k/append-tx (mk-tx d (k/head-cid p)) p)
        ;; identical regime datoms in a 2nd tx (different entity to force append)
        (k/append-tx (mk-tx (conj d [":db/add" "junkan-stock:paradigm-subordination"
                                     ":junkan.gov.stock/regime" ":neutral"]) (k/head-cid p)) p)
        (let [shifts (h/regime-shifts (k/read-log p))]
          (is (empty? shifts) "no regime CHANGED → no shift recorded")))
      (finally (io/delete-file p true)))))

(deftest extracts-regimes-from-real-analysis-tx
  ;; a real analysis tx's datoms carry all five stock regimes
  (let [insts (je/instruments "20-actors/junkan/kotoba/seed.governance-asymmetry.edn")
        ds (az/datoms insts (az/analyze insts))
        tx (k/make-tx ds "t" "a" "")
        regimes (h/stock-regimes-of-tx tx)]
    (is (= 5 (count regimes)) "all five stock regimes extracted from a live tx")))

#?(:clj
   (defn -main [& _]
     (let [r (run-tests 'junkan.methods.test-history)]
       (System/exit (if (zero? (+ (:fail r) (:error r))) 0 1)))))

#?(:clj
   (when (= *file* (System/getProperty "babashka.file"))
     (-main)))
