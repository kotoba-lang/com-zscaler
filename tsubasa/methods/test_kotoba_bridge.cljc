#!/usr/bin/env bb
;; tsubasa 翼 — live-engine bridge tests (allowlist / cursor / dry-run / leash / fail-open).
;; Run:  bb --classpath 20-actors 20-actors/tsubasa/methods/test_kotoba_bridge.cljc
(ns tsubasa.methods.test-kotoba-bridge
  (:require [tsubasa.methods.kotoba-bridge :as kb]
            [tsubasa.methods.kotoba :as k]
            [clojure.string :as str]
            [clojure.test :refer [deftest is run-tests]]
            [clojure.java.io :as io]))

(defn- tmp [] (str (System/getProperty "java.io.tmpdir") "/tsubasa-bridge-test-" (gensym) ".edn"))
(defn- seed-log [p n]
  ;; n data txs chained
  (loop [i 0 prev ""]
    (when (< i n)
      (let [ds [(k/add (str "tsubasa-route:R" i) ":tsubasa.obs/concentration" ":competitive")
                (k/add (str "tsubasa-route:R" i) ":tsubasa/derived" true)]
            tx (k/make-tx ds (str "t" i) (str "as" i) prev)]
        (k/append-tx tx p)
        (recur (inc i) (get tx ":tx/cid")))))
  p)

(deftest allowlist-refuses-non-fleet-endpoint
  (is (thrown? #?(:clj clojure.lang.ExceptionInfo :cljs js/Error)
               (kb/push "x" {:endpoint "http://evil.example/xrpc/transact"}))))

(deftest graph-cid-deterministic-and-cidv1
  (is (= (kb/graph-cid "tsubasa") (kb/graph-cid "tsubasa")))
  (is (str/starts-with? (kb/graph-cid "tsubasa") "b"))
  (is (not= (kb/graph-cid "tsubasa") (kb/graph-cid "kaname"))))

(deftest dry-run-by-default-no-io
  (let [p (tmp)]
    (try
      (seed-log p 2)
      (let [r (kb/push p {})]              ; no :live, no env → dry-run
        (is (= "dry-run" (:mode r)))
        (is (= 2 (:pending r)))
        (is (str/starts-with? (:graph-cid r) "b"))
        (is (= 2 (count (k/read-log p)))))  ; nothing appended in dry-run
      (finally (io/delete-file p true)))))

(deftest live-push-via-injected-transport-appends-bridge-checkpoint
  (let [p (tmp) calls (atom [])
        fake (fn [_ body] (swap! calls conj body) {:status "ok" :tx_cid "btxremote" :commit_cid "bcommit" :datom_count 5})]
    (try
      (seed-log p 2)
      (let [r (kb/push p {:live true :transport fake})]
        (is (= "live" (:mode r)))
        (is (= 2 (:pushed r)))
        (is (= 10 (:datoms-confirmed r)))
        (is (= 2 (count @calls)))
        ;; 2nd body carries expected_parent from the 1st commit (optimistic concurrency)
        (is (= "bcommit" (:expected_parent (second @calls))))
        ;; a :bridge checkpoint tx is appended → log grew by 1
        (is (= 3 (count (k/read-log p)))))
      (finally (io/delete-file p true)))))

(deftest exactly-once-second-push-is-noop
  (let [p (tmp) calls (atom [])
        fake (fn [_ body] (swap! calls conj body) {:status "ok" :tx_cid "btx" :commit_cid "bc" :datom_count 1})]
    (try
      (seed-log p 2)
      (kb/push p {:live true :transport fake})
      (reset! calls [])
      (let [r2 (kb/push p {:live true :transport fake})]   ; cursor → nothing pending
        (is (= 0 (:pushed r2)))
        (is (empty? @calls)))
      (finally (io/delete-file p true)))))

(deftest member-leash-presents-cacao-and-drops-operator-bearer
  (let [p (tmp) calls (atom [])
        fake (fn [_ body] (swap! calls conj body) {:status "ok" :tx_cid "b" :commit_cid "" :datom_count 1})
        deleg {:cacao-b64 "bWVtYmVyLXNpZ25lZA" :graph "tsubasa" :exp 9999999999}]
    (try
      (seed-log p 1)
      (let [r (kb/push p {:live true :transport fake :delegation deleg :now-epoch 1000})]
        (is (true? (:delegated r)))
        (is (= "member-delegation" (:principal r)))
        (is (every? #(= "bWVtYmVyLXNpZ25lZA" (:cacao_b64 %)) @calls)))   ; member-signed cap presented
      (finally (io/delete-file p true)))))

(deftest expired-leash-falls-open-to-operator
  (let [p (tmp) calls (atom [])
        fake (fn [_ body] (swap! calls conj body) {:status "ok" :tx_cid "b" :commit_cid "" :datom_count 1})
        deleg {:cacao-b64 "x" :graph "tsubasa" :exp 500}]
    (try
      (seed-log p 1)
      (let [r (kb/push p {:live true :transport fake :delegation deleg :now-epoch 1000})]
        (is (false? (:delegated r)))
        (is (str/includes? (:principal r) "operator"))
        (is (every? #(not (contains? % :cacao_b64)) @calls)))   ; no cap; fail-open to operator
      (finally (io/delete-file p true)))))

#?(:clj
   (when (= *file* (System/getProperty "babashka.file"))
     (let [{:keys [fail error]} (run-tests 'tsubasa.methods.test-kotoba-bridge)]
       (System/exit (if (zero? (+ fail error)) 0 1)))))
