(ns tsumugi.methods.test-autorun
  "Cross-language oracle tests for tsumugi.methods.autorun — the offline kotoba-log beat.
  Ported from the REAL Python autorun.

  The beat fuses the already-ported forage-plan + publish + coverage-scale into a
  content-addressed cycle transaction. The order-independent datom values are pinned from the
  REAL Python beat over the committed seed (seed-nodes 628, seed-edges 637, dataset-triples
  5179, frontier-tips 525, harvested 99, country-coverage 20.5128, forage 'GROW …'); the cid
  is structural (publish's content-hash over the real seed is map-order-dependent, so it need
  only be a well-formed tx:sha256 that is deterministic within the runtime and chains)."
  (:require [clojure.test :refer [deftest is testing]]
            [tsumugi.methods.autorun :as autorun]))

(defn- tmp []
  (let [f (java.io.File/createTempFile "tsumugi-cycle" ".edn")] (.delete f) (.getAbsolutePath f)))
(defn- tmp-dir []
  (let [d (java.io.File/createTempFile "tsumugi-out" "")] (.delete d) (.mkdirs d) (.getAbsolutePath d)))

(defn- close? [x y] (< (Math/abs (- (double x) (double y))) 1e-6))

(deftest beat-emits-content-addressed-cycle
  (let [log (tmp) out (tmp-dir)]
    (try
      (let [b (autorun/beat log :out-dir out)
            d (get b "datoms")]
        (is (clojure.string/starts-with? (get b "cid") "tx:sha256:"))
        (is (= 26 (count (get b "cid"))))
        (is (= "tx:genesis" (get b "prev")))
        (is (= 0 (get b "beat")))
        (is (= 2606110000 (get d ":tsumugi.cycle/as-of")))
        (is (= 628 (get d ":tsumugi.cycle/seed-nodes")))
        (is (= 637 (get d ":tsumugi.cycle/seed-edges")))
        (is (= 5179 (get d ":tsumugi.cycle/dataset-triples")))
        (is (= 525 (get d ":tsumugi.cycle/frontier-tips")))
        (is (= 99 (get d ":tsumugi.cycle/harvested")))
        (is (= "GROW → next ring anchors on 525 frontier tips" (get d ":tsumugi.cycle/forage")))
        (is (close? 20.5128 (get d ":tsumugi.cycle/country-coverage-pct")))
        (is (= "did:web:etzhayyim.com:actor:tsumugi" (get d ":tsumugi.cycle/published-by"))))
      (finally (.delete (java.io.File. log))))))

(deftest beats-chain-into-a-dag
  (let [log (tmp) out (tmp-dir)]
    (try
      (let [b1 (autorun/beat log :out-dir out)
            b2 (autorun/beat log :out-dir out)]
        (is (= (get b1 "cid") (get b2 "prev")))      ; commit-DAG linkage
        (is (= 1 (get b2 "beat")))
        (is (= 2606110001 (get-in b2 ["datoms" ":tsumugi.cycle/as-of"]))))
      (finally (.delete (java.io.File. log))))))

(deftest beat-is-deterministic-within-runtime
  (let [la (tmp) lb (tmp) out (tmp-dir)]
    (try
      ;; same seed + same beat index (both fresh logs) → identical cid
      (is (= (get (autorun/beat la :out-dir out) "cid")
             (get (autorun/beat lb :out-dir out) "cid")))
      (finally (.delete (java.io.File. la)) (.delete (java.io.File. lb))))))
