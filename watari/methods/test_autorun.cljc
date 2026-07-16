(ns watari.methods.test-autorun
  "test_autorun.py — watari autonomous situational-awareness heartbeat + kotoba Datom-log invariants.
  ADR-2606041827. 1:1 Clojure port of methods/test_autorun.py (the ok() asserts → clojure.test/is).

  Guards the autonomy + persistence + no-person-tracking contract for the fleet:
    - one content-addressed tx per heartbeat to an append-only log (commit-DAG verifies, tamper detected);
    - deterministic / resume-safe (same cycles → same CIDs) and append-only;
    - derived :movement/* signals flagged :movement/derived (recomputed-on-read);
    - G4 no person-tracking: craft / lane / chokepoint aggregates and NO person/owner/passenger/crew attr;
    - no external I/O (offline ingest, local persist — G7 stays gated)."
  (:require [clojure.test :refer [deftest is run-tests]]
            [clojure.string :as str]
            #?(:clj [clojure.java.io :as io])
            [watari.methods.autorun :as autorun]
            [watari.methods.kotoba :as kotoba]))

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
                                                      ":movement/derived true"
                                                      ":movement/derived false")
                                   ln))
                               lines)]
            (spit log (str (str/join "\n" tampered) "\n"))
            (let [v (kotoba/verify-chain log)]
              (is (and (not (get v "ok")) (= 0 (get v "broken_at")))
                  "tampering an earlier tx breaks the chain")))))
      (finally (.delete log)))))

(deftest test-g4-no-person-tracking
  ;; the defining watari invariant: a craft is a craft, never a person — no person attr persisted.
  (let [log (tmp-log)]
    (try
      (autorun/run-cycle 1 nil log)
      (let [tx (nth (kotoba/read-log log) 0)
            attrs (set (map #(str (nth % 2)) (get tx ":tx/datoms")))]
        (doseq [forbidden [":craft/owner-person" ":craft/passenger" ":craft/crew"
                           ":craft/person" ":person" ":craft.fix/person" ":movement/person"
                           ":craft/owner-name" ":pattern-of-life"]]
          (is (not (contains? attrs forbidden))
              (str "no person-tracking attr `" forbidden "` in the log (G4)")))
        ;; situational-awareness framing IS present (aggregate, not per-person)
        (is (some #(str/starts-with? % ":movement/") attrs)
            "aggregate :movement/* signals persisted"))
      (finally (.delete log)))))

(deftest test-derived-flagged-and-append-only-op
  (let [log (tmp-log)]
    (try
      (autorun/run-cycle 1 nil log)
      (let [tx (nth (kotoba/read-log log) 0)
            datoms (get tx ":tx/datoms")
            derived (filter #(= (nth % 2) ":movement/derived") datoms)]
        (is (> (count derived) 0) "derived :movement/* signals are persisted")
        (is (every? #(true? (nth % 3)) derived) "every :movement/derived flag is true")
        (let [ops (set (map #(nth % 0) datoms))]
          (is (= #{":db/add"} ops)
              "every datom is append-only :db/add (the fix stream IS the trajectory)")))
      (finally (.delete log)))))

(deftest test-no-external-io
  (let [methods-dir autorun/here   ; the actor's methods/ dir (absolute, resolved by autorun)
        src (str (slurp (io/file methods-dir "autorun.cljc"))
                 (slurp (io/file methods-dir "kotoba.cljc")))]
    (doseq [banned ["urllib" "http.client" "socket" "requests" "subprocess"]]
      (is (not (str/includes? src banned))
          (str "autorun/kotoba does no external I/O (no `" banned "`)")))))

;; ── parity assertion (not in the Python suite; pin byte-/CID-identity vs python3) ──
(deftest test-cid-matches-python
  (let [log (tmp-log)]
    (try
      (let [res (autorun/run-autonomous 3 nil log)
            cids (mapv #(get % "cid") (get res "beats"))]
        (is (= ["b6b3a675eb3d9907344ecee0acf36a593c6f6f93acfe7afdd6b0f50a43e7177f3"
                "b867c9f2a93c1558cea144a655dd70e23b529b80c55eb9b9ac267b1b12ec4f7b0"
                "b2eb105970732931e3636b112058ab67e9beae24111c121b1f6d5af32c8ebb104"]
               cids)
            "tx CIDs reproduce python3 autorun.py byte-for-byte"))
      (finally (.delete log)))))

#?(:clj
   (do
     (defn -main [& _] (run-tests 'watari.methods.test-autorun))
     (when (= *file* (System/getProperty "babashka.file")) (-main))))
