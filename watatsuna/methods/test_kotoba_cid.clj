#!/usr/bin/env bb
;; Cross-process CID-determinism guard for the watatsuna kotoba commit-DAG.
(ns watatsuna.methods.test-kotoba-cid
  "test_kotoba_cid.clj — watatsuna content-addressing reproducibility (ADR-2605312345 / 2606012600).

  Deepens the determinism leg the autorun test left implicit: the in-process verify-chain
  proves a single run self-consistent, but ONLY a pinned literal tx-cid proves the sha256 over
  the canonical (pr-str) form is REPRODUCIBLE ACROSS PROCESSES — recomputed in whatever bb/JVM
  runs the test, on any CI machine. watatsuna's `canonical` carries no internal sort (the caller
  / autorun's graph-datoms supplies a stable vector order), so the guard is: a FIXED datom
  vector hashes to a FIXED CID, in any process. Seed-independent (a sibling may edit the seed).

  Run:  bb --classpath 20-actors 20-actors/watatsuna/methods/test_kotoba_cid.clj"
  (:require [watatsuna.methods.kotoba :as k]
            [clojure.java.io :as io]
            [clojure.test :refer [deftest is run-tests]]))

;; A fixed, seed-independent cable-graph datom vector — ground + a derived :resilience/* signal.
;; Attrs/values are the house string-keyed EAVT convention (Python ':...' keyword strings stay
;; strings), matching what kotoba.cljc's canonical-json-utf8 actually serializes -- NOT bare
;; Clojure keywords, which canonical-json-utf8 has no case for and raises "unsupported value" on.
(def ^:private fixed-datoms
  [[":db/add" "cable.sea-me-we-6" ":cable/name" "SeaMeWe-6"]
   [":db/add" "cable.sea-me-we-6" ":cable/status" ":active"]
   [":db/add" "resil.malacca" ":resilience/chokepoint" ":malacca"]
   [":db/add" "resil.malacca" ":resilience/cable-load" 940.16]])

;; ── pinned literals (recomputed directly against this actor's kotoba.cljc; the cross-process
;;    anchor). empty-cid is independent of fixed-datoms (unaffected) but was ALSO a stale,
;;    never-actually-verified copy-pasted template literal in the original file — recomputed.
(def ^:private empty-cid "b2fc787b426127d7002522f570fd7ecc7576f34c65385163053d35e20c9b3ff76")
(def ^:private fixed-cid "ba06d401ca0be1d085972d37f3bf46f9cdc2b860304debff38f965c099617b6c3")
(def ^:private with-prev-cid "b166cba59104c1875942cf2f028ca730552f56b0ef1b47b1ce7c7d1a572f4622a")

(deftest empty-tx-cid-is-pinned
  (is (= empty-cid (k/tx-cid [])))
  (is (= empty-cid (k/tx-cid [] ""))))

(deftest empty-cid-matches-the-shared-commit-dag-canonical-form
  ;; NB: sibling actors' test_kotoba_cid.clj files pin the same literal string, but that was a
  ;; copy-pasted template value never actually verified against any of their kotoba.cljc either
  ;; (this file's own empty-cid above was wrong until this fix) — so this is NOT a confirmed
  ;; cross-actor invariant, just this actor's own recomputed, self-consistent pin.
  (is (= empty-cid (k/tx-cid []))))

(deftest fixed-datoms-cid-is-pinned
  (is (= fixed-cid (k/tx-cid fixed-datoms))))

(deftest tx-cid-is-a-pure-fn-of-datoms-and-prev
  ;; same input → same output, recomputed (no hidden state / no per-call entropy).
  (is (= (k/tx-cid fixed-datoms) (k/tx-cid fixed-datoms)))
  (is (= (k/tx-cid fixed-datoms "bX") (k/tx-cid fixed-datoms "bX"))))

(deftest prev-pointer-changes-cid-and-is-pinned
  (is (= with-prev-cid (k/tx-cid fixed-datoms "bDEADBEEF")))
  (is (not= fixed-cid with-prev-cid)))

(deftest make-tx-threads-the-pinned-cid
  (let [tx (k/make-tx fixed-datoms :tx-id 1 :as-of "2026-06-16" :prev-cid "")]
    (is (= fixed-cid (get tx ":tx/cid")))
    (is (= 4 (get tx ":tx/count")))
    (is (= "" (get tx ":tx/prev")))))

(deftest append-read-verify-roundtrip-on-temp-log
  (let [tmp (java.io.File/createTempFile "watatsuna-cid-" ".kotoba.edn")
        path (.getAbsolutePath tmp)]
    (try
      (.delete tmp)
      (let [tx1 (k/make-tx fixed-datoms :tx-id 1 :as-of "2026-06-16" :prev-cid "")
            _ (k/append-tx tx1 path)
            head1 (k/head-cid path)
            tx2 (k/make-tx [[":db/add" "cable.tam-1" ":cable/name" "TAM-1"]]
                           :tx-id 2 :as-of "2026-06-16" :prev-cid head1)
            _ (k/append-tx tx2 path)]
        (is (= fixed-cid head1))
        (is (= 2 (count (k/read-log path))))
        (let [v (k/verify-chain path)]
          (is (true? (get v "ok")))
          (is (= 2 (get v "length")))
          (is (= -1 (get v "broken_at"))))
        (is (= (get tx2 ":tx/cid") (k/head-cid path)))
        ;; tamper-evident: corrupting a datom breaks the recomputed CID
        (let [bad (str (pr-str (assoc tx1 ":tx/datoms" [[":db/add" "x" ":y" "z"]])) "\n"
                       (pr-str tx2) "\n")]
          (spit path (str ";; hdr\n" bad))
          (is (false? (get (k/verify-chain path) "ok")))))
      (finally (.delete (io/file path))))))

(when (= *file* (System/getProperty "babashka.file"))
  (let [{:keys [fail error]} (run-tests 'watatsuna.methods.test-kotoba-cid)]
    (System/exit (if (zero? (+ fail error)) 0 1))))
