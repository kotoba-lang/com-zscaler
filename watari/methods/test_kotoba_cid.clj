#!/usr/bin/env bb
;; Cross-process CID-determinism guard for the watari kotoba commit-DAG.
(ns watari.methods.test-kotoba-cid
  "test_kotoba_cid.clj — watari content-addressing reproducibility (ADR-2605312345 / 2606041827).

  Deepens the determinism leg the autorun test left implicit: only a pinned literal tx-cid
  proves the sha256-over-canonical-(pr-str) form is REPRODUCIBLE ACROSS PROCESSES — recomputed
  in whatever bb/JVM runs the test, any CI machine. Seed-independent FIXED craft/movement datom
  vector (a sibling may edit the seed). G4 no-person-tracking: the fixed vector carries only
  craft + chokepoint movement, no person fields.

  Run:  bb --classpath 20-actors 20-actors/watari/methods/test_kotoba_cid.clj"
  (:require [watari.methods.kotoba :as k]
            [clojure.java.io :as io]
            [clojure.test :refer [deftest is run-tests]]))

;; Attrs/values are the house string-keyed EAVT convention (Python ':...' keyword strings stay
;; strings), matching what kotoba.cljc's canonical-json-utf8 actually serializes -- NOT bare
;; Clojure keywords, which canonical-json-utf8 has no case for and raises "unsupported value" on.
(def ^:private fixed-datoms
  [[":db/add" "craft.imo9876543" ":craft/kind" ":vessel"]
   [":db/add" "craft.imo9876543" ":craft/name" "MV Example"]
   [":db/add" "movement.malacca" ":movement/chokepoint" ":malacca"]
   [":db/add" "movement.malacca" ":movement/transit-load" 3.0]])

;; ── pinned literals (recomputed directly against this actor's kotoba.cljc; the original
;;    2026-06-16 capture used bare-keyword fixed-datoms, which canonical-json-utf8 can't
;;    serialize, so none of these were ever actually exercised against real code until now).
(def ^:private empty-cid "b2fc787b426127d7002522f570fd7ecc7576f34c65385163053d35e20c9b3ff76")
(def ^:private fixed-cid "be87580f0777b1f460578e61fd6eb4015a43331325c22ecc1ae76ef4d63e054c9")
(def ^:private with-prev-cid "bf6b212b38468f0ffa5a2b9d6761eb1d88125837dab8a64e903789e809159dee2")

(deftest empty-tx-cid-is-pinned
  (is (= empty-cid (k/tx-cid [])))
  (is (= empty-cid (k/tx-cid [] ""))))

(deftest empty-cid-matches-the-shared-commit-dag-canonical-form
  ;; cross-actor invariant: an empty datoms vector + empty prev has no actor-specific data, so
  ;; it hashes identically everywhere the same kotoba.cljc canonical form + sha256 is used
  ;; (confirmed against watatsuna's independently-recomputed empty-cid — same literal). The old
  ;; "b752d9f3…" value here was a stale, never-actually-verified copy-pasted template literal.
  (is (= empty-cid (k/tx-cid []))))

(deftest fixed-datoms-cid-is-pinned
  (is (= fixed-cid (k/tx-cid fixed-datoms))))

(deftest tx-cid-is-a-pure-fn-of-datoms-and-prev
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
  (let [tmp (java.io.File/createTempFile "watari-cid-" ".kotoba.edn")
        path (.getAbsolutePath tmp)]
    (try
      (.delete tmp)
      (let [tx1 (k/make-tx fixed-datoms :tx-id 1 :as-of "2026-06-16" :prev-cid "")
            _ (k/append-tx tx1 path)
            head1 (k/head-cid path)
            tx2 (k/make-tx [[":db/add" "craft.icao-abc123" ":craft/kind" ":aircraft"]]
                           :tx-id 2 :as-of "2026-06-16" :prev-cid head1)
            _ (k/append-tx tx2 path)]
        (is (= fixed-cid head1))
        (is (= 2 (count (k/read-log path))))
        (let [v (k/verify-chain path)]
          (is (true? (get v "ok")))
          (is (= 2 (get v "length")))
          (is (= -1 (get v "broken_at"))))
        (is (= (get tx2 ":tx/cid") (k/head-cid path)))
        (let [bad (str (pr-str (assoc tx1 ":tx/datoms" [[":db/add" "x" ":y" "z"]])) "\n"
                       (pr-str tx2) "\n")]
          (spit path (str ";; hdr\n" bad))
          (is (false? (get (k/verify-chain path) "ok")))))
      (finally (.delete (io/file path))))))

(when (= *file* (System/getProperty "babashka.file"))
  (let [{:keys [fail error]} (run-tests 'watari.methods.test-kotoba-cid)]
    (System/exit (if (zero? (+ fail error)) 0 1))))
