#!/usr/bin/env bb
;; iriai 入会 — live-engine bridge tests (offline: allowlist, dry-run, exactly-once cursor, provenance).
;; Run:  bb --classpath 20-actors 20-actors/iriai/methods/test_kotoba_bridge.cljc
(ns iriai.methods.test-kotoba-bridge
  (:require [iriai.methods.kotoba :as k]
            [iriai.methods.kotoba-bridge :as br]
            [clojure.string :as str]
            [clojure.test :refer [deftest is run-tests]]
            [clojure.java.io :as io]))

(def ^:private tmp (str (System/getProperty "java.io.tmpdir") "/iriai-test-bridge.kotoba.edn"))
(defn- clean! [] (let [f (io/file tmp)] (when (.exists f) (.delete f))))

(defn- seed-log! []
  (clean!)
  (let [d1 [(k/add "iriai-cell:kibou:electric" ":iriai.infra/verdict" ":provision")
            (k/add "iriai-cell:kibou:electric" ":iriai.fund/cash-to-consumer" 0)]
        d2 [(k/add "iriai-asset:xfmr-decom-1" ":iriai.maint/verdict" ":decommission")]
        c1 (k/append-tx (k/make-tx d1 "t1" "a1" (k/head-cid tmp)) tmp)]
    (k/append-tx (k/make-tx d2 "t2" "a2" (k/head-cid tmp)) tmp)
    c1))

;; ── allowlist: any host outside the fleet throws BEFORE I/O ────────────────────
(deftest allowlist-enforced
  (is (nil? (br/assert-kotoba "http://127.0.0.1:8077/x")))
  (is (nil? (br/assert-kotoba "http://192.168.1.70:8077/x")))
  (is (thrown? clojure.lang.ExceptionInfo (br/assert-kotoba "http://evil.example.com/x")))
  (is (thrown? clojure.lang.ExceptionInfo (br/assert-kotoba "https://127.0.0.1:8077/x")) "https not in allowlist (http only)")
  (is (thrown? clojure.lang.ExceptionInfo (br/push tmp {:endpoint "http://1.2.3.4:9/x"}))))

;; ── dry-run is the default: exact bodies, no I/O, no cursor advance ────────────
(deftest dry-run-default
  (seed-log!)
  (let [r (br/push tmp)]
    (is (= "dry-run" (:mode r)))
    (is (= 2 (:pending r)) "both data txs pending")
    (is (str/starts-with? (:graph-cid r) "b") "graph CID is a CIDv1 base32 string")
    (is (= "" (:pushed-cid r)) "cursor not advanced in dry-run")
    ;; provenance is embedded in each tx_edn body
    (let [edn0 (get-in r [:bodies 0 :tx_edn])]
      (is (str/includes? edn0 ":iriai.tx/id"))
      (is (str/includes? edn0 ":iriai.tx/local-cid"))
      (is (str/includes? edn0 ":iriai.infra/verdict")))
    (clean!)))

;; ── live (injected transport): pushes all pending, appends ONE :bridge cursor ──
(deftest live-injected-transport-exactly-once
  (seed-log!)
  (let [calls (atom [])
        fake (fn [_url body]
               (swap! calls conj body)
               {:status "ok" :tx_cid (str "remote-" (count @calls)) :commit_cid (str "commit-" (count @calls)) :datom_count 3})
        r1 (br/push tmp {:live true :transport fake})]
    (is (= "live" (:mode r1)))
    (is (= 2 (:pushed r1)) "both data txs pushed")
    (is (= 6 (:datoms-confirmed r1)) "2 txs × 3 datoms")
    (is (= 2 (count @calls)))
    ;; expected_parent threaded from the 1st push's commit into the 2nd
    (is (= "commit-1" (:expected_parent (second @calls))))
    ;; a 2nd push is a NO-OP (exactly-once cursor)
    (reset! calls [])
    (let [r2 (br/push tmp {:live true :transport fake})]
      (is (= 0 (:pushed r2)) "nothing pending after cursor advance")
      (is (empty? @calls) "no transport calls on the re-push"))
    (clean!)))

;; ── a refused transact throws (status not ok/committed/success) ────────────────
(deftest refused-transact-throws
  (seed-log!)
  (let [bad (fn [_ _] {:status "refused"})]
    (is (thrown? clojure.lang.ExceptionInfo (br/push tmp {:live true :transport bad})))
    (clean!)))

;; ── graph CID is deterministic + content-addressed ─────────────────────────────
(deftest graph-cid-deterministic
  (is (= (br/graph-cid "iriai") (br/graph-cid "iriai")))
  (is (not= (br/graph-cid "iriai") (br/graph-cid "kaname")))
  (is (str/starts-with? (br/graph-cid "iriai") "b")))

#?(:clj
   (when (= *file* (System/getProperty "babashka.file"))
     (let [{:keys [fail error]} (run-tests 'iriai.methods.test-kotoba-bridge)]
       (System/exit (if (zero? (+ fail error)) 0 1)))))
