(ns mimamori.tests.test-kotoba-autorun
  "mimamori R1 tests — kotoba commit-DAG + match cell + autorun heartbeat.
  Port of tests/test_kotoba_autorun.py, plus a Python↔Clojure CID-parity test."
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.test :refer [deftest is]]
            [kotoba.datom :as kd]
            [mimamori.methods.autorun :as autorun]
            [mimamori.methods.bond :as bond]
            [mimamori.methods.match :as match]))

(defn- seed []
  (bond/load-seed-file (io/resource "mimamori/data/seed-mimamori-bonds.json")))

(defn- tmplog []
  (str (java.nio.file.Files/createTempDirectory
        "mimamori-clj" (make-array java.nio.file.attribute.FileAttribute 0))
       "/log.kotoba.edn"))

(deftest match-closes-gap
  (let [[eng s] (match/match-cycle (bond/replay (seed)) (:roster (seed)))]
    (is (= 8 (:unkept-before s)))            ;; the baseline gap
    (is (>= (:offers-emitted s) 6))          ;; most unkept reached this pass
    ;; every emitted offer is a real :offered bond addressed to its parties
    (let [offered (count (filter #(= ":offered" (val %)) (:state eng)))]
      (is (>= offered (:offers-emitted s))))))

(deftest match-capacity-cap
  (let [s (seed)
        [eng _] (match/match-cycle (bond/replay s) (:roster s))]
    (doseq [did (:roster s)]
      (is (<= (match/keeper-load eng did) match/max-kept))))) ;; covenant, not a queue

(deftest match-aggregate-only
  (let [[_ s] (match/match-cycle (bond/replay (seed)) (:roster (seed)))]
    (is (every? integer? (vals s)))))        ;; counts only, never names (G5)

(deftest chain-appends-and-verifies
  (let [log (tmplog)
        cid1 (kd/append-tx! (kd/make-tx [[":db/add" "e1" ":mishmeret.bond/cycle" 1]]
                                        {:tx-id 1 :as-of 1}) log)
        cid2 (kd/append-tx! (kd/make-tx [[":db/add" "e2" ":mishmeret.bond/cycle" 2]]
                                        {:tx-id 2 :as-of 2 :prev-cid cid1}) log)
        v (kd/verify-chain log)]
    (is (= cid2 (kd/head-cid log)))
    (is (:ok v))
    (is (= 2 (:length v)))))

(deftest tamper-detect
  (let [log (tmplog)
        cid1 (kd/append-tx! (kd/make-tx [[":db/add" "e1" ":mishmeret.bond/cycle" 1]]
                                        {:tx-id 1 :as-of 1}) log)]
    (kd/append-tx! (kd/make-tx [[":db/add" "e2" ":mishmeret.bond/cycle" 2]]
                               {:tx-id 2 :as-of 2 :prev-cid cid1}) log)
    (spit log (str/replace-first (slurp log) "\"e1\"" "\"eX\""))
    (let [v (kd/verify-chain log)]
      (is (not (:ok v)))
      (is (= 0 (:broken-at v))))))           ;; earliest tamper breaks the DAG

(deftest autorun-deterministic-and-resume-safe
  (let [s (seed)
        log-a (tmplog)
        log-b (tmplog)
        s1 (autorun/run-cycle s log-a)
        s2 (autorun/run-cycle s log-b)]
    (is (= (:cid s1) (:cid s2)))             ;; same seed+cycle → same CID
    (let [s3 (autorun/run-cycle s log-a)]    ;; resume: cycle derives from log
      (is (= 2 (:cycle s3)))
      (is (not= (:cid s3) (:cid s1))))       ;; prev-linked → new CID
    (is (:ok (kd/verify-chain log-a)))
    (is (= 2 (count (kd/read-log log-a))))))

(deftest autorun-summary-no-did
  (let [s (autorun/run-cycle (seed) (tmplog))
        flat (str s)]
    (is (not (str/includes? flat "did:")))   ;; G5 aggregate-only summary
    (is (not (str/includes? flat "fictional")))
    ;; post-match coverage: every emitted offer moved an unkept member to pending
    (is (= (get-in s [:coverage :unkept-count])
           (- (:unkept-before s) (:offers-emitted s))))
    (is (>= (:offers-emitted s) 6))))

(deftest log-roundtrip-preserves-datoms
  (let [log (tmplog)]
    (autorun/run-cycle (seed) log)
    (let [txs (kd/read-log log)
          attrs (set (map #(nth % 2) (:tx/datoms (first txs))))]
      (is (some #(str/starts-with? % ":mishmeret.bond/") attrs))
      (is (some #(str/starts-with? % ":mimamori.coverage/") attrs))
      (is (every? #(= ":db/add" (first %)) (:tx/datoms (first txs)))))))

(deftest python-cid-parity
  ;; Golden-file parity (ADR-2606131300): `golden-py-autorun.log` is the REAL Python
  ;; autorun.py output (content-addressed Datom DAG over seed-mimamori-bonds.json), frozen
  ;; byte-for-byte BEFORE the Python prune. Freezing it keeps the cross-language CID guarantee
  ;; after autorun.py is gone — the cljc cycle must reproduce the SAME head CID, and cljc must
  ;; still verify the Python-written chain.
  (let [actor-dir (-> (io/resource "mimamori/cell.cljc") io/file .getParentFile)
        py-log (str actor-dir "/tests/golden_py_autorun.kotoba.edn")
        s (autorun/run-cycle (seed) (tmplog))]
    (is (:ok (kd/verify-chain py-log)))    ;; Clojure verifies the Python-written log
    (is (= (kd/head-cid py-log) (:cid s))))) ;; byte-identical CID across languages
