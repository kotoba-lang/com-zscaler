#!/usr/bin/env bb
;; test_kotoba.clj — babashka tests for keizu.methods.kotoba. ADR-2606066000.
;;
;; Brand-new test coverage (there is no test_kotoba.py).
;; Pins the tx_cid against python3 (verified byte-identical to kotoba.py on the seed graph).
;; ALWAYS writes to a TEMP log path — never mutates committed data files.
(ns keizu.methods.test-kotoba
  (:require [clojure.test :refer [deftest is testing run-tests]]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [keizu.methods.edn :as e]
            [keizu.methods.weave :as w]
            [keizu.methods.kotoba :as kotoba]))

;; ── shared seed fixtures ────────────────────────────────────────────────────

;; *file* is only reliably bound during this file's own top-level compilation —
;; capture it here, not inside a defn- body (which resolves it lazily, at call
;; time, when required as a library rather than run as the entry script).
(def ^:private this-file *file*)

(defn- seed-path []
  (let [here (-> this-file io/file .getAbsoluteFile .getParentFile .getParentFile)]
    (io/file here "data" "seed-relation-graph.kotoba.edn")))

(defn- seed-graph []
  (w/weave (e/load-edn (seed-path))))

(defn- temp-log []
  (let [f (java.io.File/createTempFile "keizu-test-kotoba-" ".edn")]
    (.deleteOnExit f)
    f))

;; ── CID PARITY: the central invariant ───────────────────────────────────────

(deftest test-tx-cid-parity-with-python
  (testing "tx_cid over seed graph_datoms + derived_datoms matches kotoba.py (byte-identical)"
    (let [g        (seed-graph)
          c        (w/concentration g)
          gdatoms  (kotoba/graph-datoms g)
          ddatoms  (kotoba/derived-datoms c)
          all-dats (into gdatoms ddatoms)
          cid      (kotoba/tx-cid all-dats "")]
      ;; pinned against `python3 -c "..."` on the seed — must be byte-identical to kotoba.py
      (is (= "ba9e2d1f206b2b4d0744b5abe69d1ea644ad83037ab326d6b99a4f5f9dd8f9fdc"
             cid)
          "CID must be byte-identical to kotoba.py on the seed graph"))))

;; ── datom counts ─────────────────────────────────────────────────────────────

(deftest test-graph-datoms-count
  (testing "graph-datoms produces 344 EAVT assertions from the seed (mirrors kotoba.py)"
    (let [g (seed-graph)]
      (is (= 344 (count (kotoba/graph-datoms g)))))))

(deftest test-derived-datoms-count
  (testing "derived-datoms produces 82 EAVT assertions from the seed (mirrors kotoba.py)"
    (let [g (seed-graph)
          c (w/concentration g)]
      (is (= 82 (count (kotoba/derived-datoms c)))))))

;; ── datom structure invariants ────────────────────────────────────────────────

(deftest test-datoms-append-only
  (testing "Every datom has op = :db/add (no :db/retract — 非終末論)"
    (let [g    (seed-graph)
          c    (w/concentration g)
          dats (into (kotoba/graph-datoms g) (kotoba/derived-datoms c))]
      (is (every? #(= ":db/add" (first %)) dats)
          "All ops must be :db/add"))))

(deftest test-graph-datoms-first-node
  (testing "First datoms are for jp-mof with :node/scope :public-org (insertion order)"
    (let [g    (seed-graph)
          dats (kotoba/graph-datoms g)]
      (is (= [":db/add" "jp-mof" ":node/scope" ":public-org"]
             (first dats))))))

(deftest test-graph-datoms-second-node
  (testing "Second datom is jp-mof :node/label"
    (let [g    (seed-graph)
          dats (kotoba/graph-datoms g)]
      (is (= [":db/add" "jp-mof" ":node/label" "財務省 (MOF)"]
             (second dats))))))

(deftest test-derived-datoms-counts-block
  (testing "derived-datoms emits the counts block first with correct values"
    (let [g    (seed-graph)
          c    (w/concentration g)
          dats (kotoba/derived-datoms c)]
      (is (= [":db/add" "keizu.conc-counts" ":keizu.conc/node-count" 18]
             (nth dats 0)))
      (is (= [":db/add" "keizu.conc-counts" ":keizu.conc/committee-count" 3]
             (nth dats 1)))
      (is (= [":db/add" "keizu.conc-counts" ":keizu.conc/rel-count" 15]
             (nth dats 2)))
      (is (= [":db/add" "keizu.conc-counts" ":keizu.conc/money-count" 6]
             (nth dats 3)))
      (is (= [":db/add" "keizu.conc-counts" ":keizu.conc/statement-count" 3]
             (nth dats 4)))
      (is (= [":db/add" "keizu.conc-counts" ":keizu.conc/derived" true]
             (nth dats 5))))))

(deftest test-derived-datoms-money-block
  (testing "derived-datoms emits the money block with correct HHI and total"
    (let [g    (seed-graph)
          c    (w/concentration g)
          dats (kotoba/derived-datoms c)]
      (is (= [":db/add" "keizu.conc-money" ":keizu.conc/money-hhi" 0.9606]
             (nth dats 6)))
      (is (= [":db/add" "keizu.conc-money" ":keizu.conc/money-total" 2347000000.0]
             (nth dats 7)))
      (is (= [":db/add" "keizu.conc-money" ":keizu.conc/payer-hhi" 0.9606]
             (nth dats 8)))
      (is (= [":db/add" "keizu.conc-money" ":keizu.conc/derived" true]
             (nth dats 9))))))

(deftest test-derived-datoms-payee-share-rounded
  (testing "payee share is rounded to 4dp via Python-compatible rounding (w/pyround)"
    (let [g    (seed-graph)
          c    (w/concentration g)
          dats (kotoba/derived-datoms c)]
      ;; jp-vendor-x share ≈ 0.97997443544951 → round(_, 4) = 0.98
      (is (= [":db/add" "keizu.conc-payee-jp-vendor-x" ":keizu.conc/payee" "jp-vendor-x"]
             (nth dats 10)))
      (is (= [":db/add" "keizu.conc-payee-jp-vendor-x" ":keizu.conc/share" 0.98]
             (nth dats 11))
          "share must be Python round(x, 4) = 0.98"))))

(deftest test-derived-datoms-last-block
  (testing "last derived datom is :keizu.conc/derived true for the last by-jurisdiction entry"
    (let [g    (seed-graph)
          c    (w/concentration g)
          dats (kotoba/derived-datoms c)]
      (is (= [":db/add" "keizu.conc-juris-oecd" ":keizu.conc/derived" true]
             (last dats))))))

;; ── make-tx structure ─────────────────────────────────────────────────────────

(deftest test-make-tx-shape
  (testing "make-tx builds a transaction map with correct keys and values"
    (let [g    (seed-graph)
          c    (w/concentration g)
          dats (into (kotoba/graph-datoms g) (kotoba/derived-datoms c))
          tx   (kotoba/make-tx dats :tx-id 1 :as-of 20260609 :prev-cid "")]
      (is (= 1 (get tx ":tx/id")))
      (is (= 20260609 (get tx ":tx/as-of")))
      (is (= "" (get tx ":tx/prev")))
      (is (= 426 (get tx ":tx/count")))
      (is (= "ba9e2d1f206b2b4d0744b5abe69d1ea644ad83037ab326d6b99a4f5f9dd8f9fdc"
             (get tx ":tx/cid")))
      (is (= dats (get tx ":tx/datoms"))))))

;; ── append-tx / read-log round-trip ───────────────────────────────────────────

(deftest test-append-read-roundtrip
  (testing "append-tx → read-log round-trip recovers the same tx"
    (let [g    (seed-graph)
          c    (w/concentration g)
          dats (into (kotoba/graph-datoms g) (kotoba/derived-datoms c))
          tx   (kotoba/make-tx dats :tx-id 1 :as-of 20260609 :prev-cid "")
          log  (temp-log)]
      (kotoba/append-tx tx log)
      (let [read-txs (kotoba/read-log log)]
        (is (= 1 (count read-txs)))
        (let [rt (first read-txs)]
          (is (= (get tx ":tx/id")    (get rt ":tx/id")))
          (is (= (get tx ":tx/as-of") (get rt ":tx/as-of")))
          (is (= (get tx ":tx/prev")  (get rt ":tx/prev")))
          (is (= (get tx ":tx/cid")   (get rt ":tx/cid")))
          (is (= (get tx ":tx/count") (get rt ":tx/count")))
          (is (= (get tx ":tx/datoms") (get rt ":tx/datoms"))))))))

;; ── head-cid after two transactions ───────────────────────────────────────────

(deftest test-head-cid-after-two-txs
  (testing "head-cid returns the CID of the last (second) appended transaction"
    (let [g    (seed-graph)
          c    (w/concentration g)
          dats (into (kotoba/graph-datoms g) (kotoba/derived-datoms c))
          log  (temp-log)
          tx1  (kotoba/make-tx dats :tx-id 1 :as-of 20260609 :prev-cid "")
          cid1 (kotoba/append-tx tx1 log)
          tx2  (kotoba/make-tx dats :tx-id 2 :as-of 20260610 :prev-cid cid1)
          cid2 (kotoba/append-tx tx2 log)]
      (is (= cid2 (kotoba/head-cid log)))
      (is (not= cid1 cid2)
          "tx2 CID differs from tx1 because prev-cid differs"))))

;; ── verify-chain: intact and tamper-detection ─────────────────────────────────

(deftest test-verify-chain-ok
  (testing "verify-chain returns :ok true after two correct appends"
    (let [g    (seed-graph)
          c    (w/concentration g)
          dats (into (kotoba/graph-datoms g) (kotoba/derived-datoms c))
          log  (temp-log)
          tx1  (kotoba/make-tx dats :tx-id 1 :as-of 20260609 :prev-cid "")
          cid1 (kotoba/append-tx tx1 log)
          tx2  (kotoba/make-tx dats :tx-id 2 :as-of 20260610 :prev-cid cid1)
          _    (kotoba/append-tx tx2 log)
          res  (kotoba/verify-chain log)]
      (is (true?  (get res "ok")))
      (is (= 2    (get res "length")))
      (is (= -1   (get res "broken_at"))))))

(deftest test-verify-chain-detects-tampering
  (testing "verify-chain returns :ok false when a log line is tampered"
    (let [g    (seed-graph)
          c    (w/concentration g)
          dats (into (kotoba/graph-datoms g) (kotoba/derived-datoms c))
          log  (temp-log)
          tx   (kotoba/make-tx dats :tx-id 1 :as-of 20260609 :prev-cid "")
          _    (kotoba/append-tx tx log)
          ;; Tamper: replace the cid in the log file
          content  (slurp log :encoding "UTF-8")
          cid      (get tx ":tx/cid")
          tampered (str/replace content cid "bdeadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeef")
          _        (spit log tampered :encoding "UTF-8")
          res      (kotoba/verify-chain log)]
      (is (false? (get res "ok")))
      (is (= 0    (get res "broken_at"))))))

;; ── read-log on non-existent log ──────────────────────────────────────────────

(deftest test-read-log-empty
  (testing "read-log returns [] for a non-existent log path"
    (let [log (io/file "/tmp/keizu-test-nonexistent-12345.edn")]
      (is (= [] (kotoba/read-log log))))))

;; ── CID changes when prev-cid changes ────────────────────────────────────────

(deftest test-cid-chaining
  (testing "CID is different when prev-cid differs (commit-DAG chaining)"
    (let [dats [["  :db/add" "e" ":a" "v"]]
          cid0 (kotoba/tx-cid dats "")
          cid1 (kotoba/tx-cid dats "bdeadbeef")]
      (is (not= cid0 cid1)))))

;; ── runner ───────────────────────────────────────────────────────────────────

(when (= *file* (System/getProperty "babashka.file"))
  (let [{:keys [fail error]} (clojure.test/run-tests 'keizu.methods.test-kotoba)]
    (System/exit (if (zero? (+ fail error)) 0 1))))
