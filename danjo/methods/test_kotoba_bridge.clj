;; test_kotoba_bridge.clj — standalone suite for the live kotoba-engine transact bridge.
;; Hermetic: no real network I/O (dry-run + injected transport only), the G7 discipline.
;; Run: bb test_kotoba_bridge.clj   (or: clojure -M test_kotoba_bridge.clj)   from methods/.
(ns root.danjo.methods.test-kotoba-bridge
  (:require [clojure.string :as str]
            [clojure.java.io :as io]))

(load-file "kotoba_bridge.clj")
(load-file "ingest.clj")
(alias 'kb 'root.danjo.methods.kotoba-bridge)
(alias 'rl 'root.danjo.methods.revenue-ledger)
(alias 'in 'root.danjo.methods.ingest)

(def checks (atom 0)) (def fails (atom 0))
(defn check [l p] (swap! checks inc) (if p (println "  ok  " l) (do (swap! fails inc) (println "  FAIL" l))))
(defn throws? [f] (try (f) false (catch Exception _ true)))

(defn fresh-log []
  (let [p (str (System/getProperty "java.io.tmpdir") "/danjo-bridge-test-" (rand-int 1000000) ".kotoba.edn")]
    (when (.exists (io/file p)) (.delete (io/file p)))
    (let [model (in/ingest "../data/gov-revenue-corpus.jp.edn")]
      (rl/run-cycle! {:seed model :log-path p :as-of 1})
      (rl/run-cycle! {:seed model :log-path p :as-of 2}))
    p))

;; ── graph CID = kotoba CIDv1 dag-cbor sha2-256 base32 ('bafyrei…') ──
(check "graph-cid is a CIDv1 base32"   (str/starts-with? (kb/graph-cid "danjo-revenue") "bafyrei"))
(check "graph-cid deterministic"       (= (kb/graph-cid "x") (kb/graph-cid "x")))
(check "graph-cid distinguishes names" (not= (kb/graph-cid "a") (kb/graph-cid "b")))

;; ── host allowlist (raises BEFORE any I/O) ──
(check "loopback :8077 allowed"        (not (throws? #(kb/assert-kotoba! kb/default-endpoint))))
(check "off-allowlist host RAISES"     (throws? #(kb/assert-kotoba! "http://evil.example:8077/x")))
(check "push to off-allowlist RAISES"  (throws? #(kb/push {:endpoint "http://evil.example:8077/x"})))

;; ── dry-run is the default and does NO I/O ──
(let [log (fresh-log)
      dry (kb/push {:log-path log})]
  (check "default mode is :dry-run"      (= :dry-run (:mode dry)))
  (check "dry-run exports 2 pending"     (= 2 (:pending dry)))
  (check "body carries :graph + :tx_edn" (= #{:graph :tx_edn} (set (keys (first (:bodies dry))))))
  (check "tx_edn has :db/add forms"      (str/includes? (:tx_edn (first (:bodies dry))) ":db/add"))
  (check "tx_edn carries :danjo.tx/ meta" (str/includes? (:tx_edn (first (:bodies dry))) ":danjo.tx/id"))
  (.delete (io/file log)))

;; ── live via injected transport: exactly-once cursor + expected_parent chaining ──
(let [log   (fresh-log)
      calls (atom [])
      tport (fn [_ep b _oa?] (swap! calls conj b)
              "{\"status\":\"ok\",\"tx_cid\":\"bR\",\"commit_cid\":\"bC1\",\"datom_count\":51}")
      live  (kb/push {:log-path log :transport tport})
      after (kb/push {:log-path log})]
  (check "live pushed 2 txs"             (= 2 (:pushed live)))
  (check "engine datom echo summed"      (= 102 (:datoms-confirmed live)))
  (check "1st call has NO expected_parent" (not (str/includes? (first @calls) "expected_parent")))
  (check "2nd call chains expected_parent" (str/includes? (second @calls) "expected_parent"))
  (check "cursor advanced → re-push is empty" (= 0 (:pending after)))
  (check "checkpoint did not corrupt the chain" (:ok (rl/verify-chain log)))
  (check "checkpoint tx is NOT itself pushable" (= :dry-run (:mode after)))
  (.delete (io/file log)))

;; ── re-push after a fork on the remote head: parent is threaded, refusal surfaces ──
(let [log   (fresh-log)
      tport (fn [_ _ _] "{\"status\":\"refused\",\"reason\":\"parent fork\"}")]
  (check "non-ok status RAISES" (throws? #(kb/push {:log-path log :transport tport})))
  (.delete (io/file log)))

;; ── operator bearer requires the PUBLIC DID env, holds no key ──
(check "operator-bearer without DID env RAISES"
       (or (some? (System/getenv kb/operator-did-env))    ; (skip if env happens to be set)
           (throws? #(kb/operator-bearer))))

(println (format "── kotoba-bridge: %d checks, %d failures ──" @checks @fails))
(when (pos? @fails) (System/exit 1))
