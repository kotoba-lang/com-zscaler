(ns kabuto.methods.test-autorun
  "test_autorun.py — kabuto autonomous supply-chain heartbeat + kotoba Datom-log invariants.
  ADR-2606022000. 1:1 Clojure port of methods/test_autorun.py (clojure.test/deftest+is mirroring
  the ok() asserts).

  Guards the autonomy + persistence + resilience-not-target-list contract for the fleet:
    - the loop persists one content-addressed tx per heartbeat to an append-only log;
    - the log is a verifiable commit-DAG (every CID recomputes; tamper is detected);
    - determinism / resume-safe: the persisted datoms are in CANONICAL sorted order, so the CID is
      reproducible across processes even though analyze builds some derived lists by iterating sets;
    - it is append-only (re-running grows the log, never rewrites);
    - derived :supply/* signals are flagged :supply/derived (recomputed-on-read);
    - G2 resilience, not target-list: the log carries concentration/accountability framing and NO
      raid/takeover/target attr;
    - it does NO external I/O (offline ingest, local persist — G7/G11 stay gated).

  (The Python `__main__` runner is preserved behind #?(:clj …) as -main.)"
  (:require [clojure.test :refer [deftest is run-tests]]
            [clojure.string :as str]
            #?(:clj [clojure.java.io :as io])
            [kabuto.methods.autorun :as autorun]
            [kabuto.methods.kotoba :as kotoba]))

#?(:clj
   (defn- tmp-log []
     (let [f (java.io.File/createTempFile "tmp" ".datoms.kotoba.edn")]
       (.delete f)
       f)))

;; def test_heartbeat_persists
(deftest test-heartbeat-persists
  (let [log (tmp-log)]
    (try
      (let [res (autorun/run-autonomous 3 nil log)]
        (is (= 3 (get res "log_length")) "one tx per heartbeat")
        (is (every? #(> (get % "datoms") 0) (get res "beats")) "every heartbeat persisted datoms")
        (is (get (get res "chain") "ok") "commit-DAG verifies (chain OK)")
        (is (str/starts-with? (get res "head_cid") "b") "head CID is content-addressed"))
      (finally (.delete log)))))

;; def test_canonical_order_is_deterministic
(deftest test-canonical-order-is-deterministic
  (let [log (tmp-log)]
    (try
      (autorun/run-cycle 1 nil log)
      (let [tx (nth (kotoba/read-log log) 0)
            datoms (get tx ":tx/datoms")
            keyed (mapv kotoba/datom-key datoms)]
        (is (= keyed (vec (sort keyed)))
            "persisted datoms are in canonical sorted order (cross-process deterministic)")
        ;; canonical-order is idempotent
        (let [once (autorun/canonical-order datoms)
              twice (autorun/canonical-order once)]
          (is (= once twice) "canonical-order is idempotent")))
      (finally (.delete log)))))

;; def test_append_only_and_tamper
(deftest test-append-only-and-tamper
  (let [log (tmp-log)]
    (try
      (autorun/run-cycle 1 nil log)
      (let [first-log (kotoba/read-log log)]
        (autorun/run-cycle 2 nil log)
        (let [second-log (kotoba/read-log log)]
          (is (= (count second-log) (inc (count first-log)))
              "second heartbeat appends, does not rewrite")
          (is (= (get (nth second-log 1) ":tx/prev") (get (nth first-log 0) ":tx/cid"))
              "tx 2 links tx 1's CID (commit-DAG)")
          (let [lines (str/split-lines (slurp log))
                tampered (mapv (fn [ln]
                                 (if (str/includes? ln ":tx/id 1 ")
                                   (str/replace-first ln
                                                      ":supply/derived true"
                                                      ":supply/derived false")
                                   ln))
                               lines)]
            (spit log (str (str/join "\n" tampered) "\n"))
            (let [v (kotoba/verify-chain log)]
              (is (and (not (get v "ok")) (= 0 (get v "broken_at")))
                  "tampering an earlier tx breaks the chain")))))
      (finally (.delete log)))))

;; def test_g2_resilience_not_target_list
(deftest test-g2-resilience-not-target-list
  (let [log (tmp-log)]
    (try
      (autorun/run-cycle 1 nil log)
      (let [tx (nth (kotoba/read-log log) 0)
            attrs (set (map #(str (nth % 2)) (get tx ":tx/datoms")))]
        (is (some #(str/starts-with? % ":supply/") attrs) "derived :supply/* signals persisted")
        (doseq [forbidden [":supply/target" ":supply/raid" ":supply/takeover-target"
                           ":target" ":supply/who-to-hit" ":supply/attack"]]
          (is (not (contains? attrs forbidden))
              (str "no target-list attr `" forbidden "` in the log (G2)"))))
      (finally (.delete log)))))

;; def test_derived_flagged_and_append_only_op
(deftest test-derived-flagged-and-append-only-op
  (let [log (tmp-log)]
    (try
      (autorun/run-cycle 1 nil log)
      (let [tx (nth (kotoba/read-log log) 0)
            datoms (get tx ":tx/datoms")
            derived (filter #(= (nth % 2) ":supply/derived") datoms)]
        (is (> (count derived) 0) "derived :supply/* signals are persisted")
        (is (every? #(true? (nth % 3)) derived) "every :supply/derived flag is true")
        (let [ops (set (map #(nth % 0) datoms))]
          (is (= #{":db/add"} ops) "every datom is append-only :db/add (no :db/retract)")))
      (finally (.delete log)))))

;; def test_no_external_io
(deftest test-no-external-io
  (let [methods-dir autorun/here
        src (str (slurp (io/file methods-dir "autorun.cljc"))
                 (slurp (io/file methods-dir "kotoba.cljc")))]
    (doseq [banned ["urllib" "http.client" "socket" "requests" "subprocess"]]
      (is (not (str/includes? src banned))
          (str "autorun/kotoba does no external I/O (no `" banned "`)")))))

#?(:clj
   (do
     (defn -main [& _] (run-tests 'kabuto.methods.test-autorun))
     (when (= *file* (System/getProperty "babashka.file")) (-main))))
