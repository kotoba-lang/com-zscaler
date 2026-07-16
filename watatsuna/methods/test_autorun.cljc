(ns watatsuna.methods.test-autorun
  "test_autorun.py — watatsuna autonomous cable-resilience heartbeat + kotoba Datom-log invariants.
  ADR-2606012600. 1:1 port of methods/test_autorun.py (clojure.test/deftest+is mirroring the ok() asserts).

  Guards the autonomy + persistence + resilience-not-interdiction contract for the fleet:
    - the loop persists one content-addressed tx per heartbeat to an append-only log;
    - the log is a verifiable commit-DAG (every CID recomputes; tamper is detected);
    - it is deterministic / resume-safe (same cycles → same CIDs) and append-only;
    - derived :resilience/* signals are flagged :resilience/derived (recomputed-on-read);
    - G2 resilience, not interdiction: the log carries chokepoint-load / redundancy-gap framing
      and NO 'where to cut' / target attr;
    - it does NO external I/O (offline ingest, local persist — G7 stays gated)."
  (:require [clojure.test :refer [deftest is run-tests]]
            [clojure.string :as str]
            #?(:clj [clojure.java.io :as io])
            [watatsuna.methods.autorun :as autorun]
            [watatsuna.methods.kotoba :as kotoba]))

#?(:clj
   (defn- tmp-log []
     (let [f (java.io.File/createTempFile "tmp" ".datoms.kotoba.edn")]
       (.delete f)
       f)))

(deftest test-heartbeat-persists
  (let [log (tmp-log)]
    (try
      (let [res (autorun/run-autonomous 3 nil log)]
        (is (= 3 (get res "log_length")) "one tx per heartbeat")
        (is (every? #(> (get % "datoms") 0) (get res "beats")) "every heartbeat persisted datoms")
        (is (get (get res "chain") "ok") "commit-DAG verifies (chain OK)")
        (is (str/starts-with? (get res "head_cid") "b") "head CID is content-addressed"))
      (finally (.delete log)))))

(deftest test-deterministic-resume-safe
  (let [a (tmp-log) b (tmp-log)]
    (try
      (let [ra (autorun/run-autonomous 3 nil a)
            rb (autorun/run-autonomous 3 nil b)]
        (is (= (mapv #(get % "cid") (get ra "beats"))
               (mapv #(get % "cid") (get rb "beats")))
            "same cycles → same CIDs (deterministic / resume-safe)"))
      (finally (.delete a) (.delete b)))))

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
          ;; tamper an earlier tx and confirm the chain breaks at index 0
          (let [lines (str/split-lines (slurp log))
                tampered (mapv (fn [ln]
                                 (if (str/includes? ln ":tx/id 1 ")
                                   (str/replace-first ln
                                                      ":resilience/derived true"
                                                      ":resilience/derived false")
                                   ln))
                               lines)]
            (spit log (str (str/join "\n" tampered) "\n"))
            (let [v (kotoba/verify-chain log)]
              (is (and (not (get v "ok")) (= 0 (get v "broken_at")))
                  "tampering an earlier tx breaks the chain")))))
      (finally (.delete log)))))

(deftest test-g2-resilience-not-interdiction
  ;; the defining watatsuna invariant: the log is a RESILIENCE map, never an interdiction target-list.
  (let [log (tmp-log)]
    (try
      (autorun/run-cycle 1 nil log)
      (let [tx (nth (kotoba/read-log log) 0)
            attrs (set (map #(str (nth % 2)) (get tx ":tx/datoms")))]
        ;; resilience framing IS present
        (is (some #(str/starts-with? % ":resilience/") attrs) "derived :resilience/* signals persisted")
        (is (or (contains? attrs ":resilience/redundancy-gap-station")
                (contains? attrs ":resilience/chokepoint"))
            "resilience framing (chokepoint / redundancy-gap) present")
        ;; interdiction framing is NOT representable
        (doseq [forbidden [":target" ":resilience/target" ":cut" ":resilience/cut-point"
                           ":interdiction" ":attack-point" ":vulnerability-to-attack"]]
          (is (not (contains? attrs forbidden))
              (str "no interdiction attr `" forbidden "` in the log (G2)"))))
      (finally (.delete log)))))

(deftest test-derived-flagged-and-append-only-op
  (let [log (tmp-log)]
    (try
      (autorun/run-cycle 1 nil log)
      (let [tx (nth (kotoba/read-log log) 0)
            datoms (get tx ":tx/datoms")
            derived (filter #(= (nth % 2) ":resilience/derived") datoms)]
        (is (> (count derived) 0) "derived :resilience/* signals are persisted")
        (is (every? #(true? (nth % 3)) derived) "every :resilience/derived flag is true")
        (let [ops (set (map #(nth % 0) datoms))]
          (is (= #{":db/add"} ops) "every datom is append-only :db/add (no :db/retract — 非終末論)")))
      (finally (.delete log)))))

(deftest test-no-external-io
  ;; mirror inspect.getsource(autorun)+inspect.getsource(kotoba): scan the ported source text.
  (let [methods-dir autorun/here   ; the actor's methods/ dir (absolute, resolved by autorun)
        src (str (slurp (io/file methods-dir "autorun.cljc"))
                 (slurp (io/file methods-dir "kotoba.cljc")))]
    (doseq [banned ["urllib" "http.client" "socket" "requests" "subprocess"]]
      (is (not (str/includes? src banned))
          (str "autorun/kotoba does no external I/O (no `" banned "`)")))))

#?(:clj
   (do
     (defn -main [& _] (run-tests 'watatsuna.methods.test-autorun))
     (when (= *file* (System/getProperty "babashka.file")) (-main))))
