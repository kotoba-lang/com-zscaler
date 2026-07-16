(ns danjo.methods.test-autorun
  "test_autorun.py — danjo autonomous heartbeat + kotoba Datom-log invariants. ADR-2605301600.
  1:1 Clojure port of methods/test_autorun.py (clojure.test/deftest+is mirroring the ok() asserts).

  Guards the autonomy + persistence + non-adjudication contract for the fleet:
    - one content-addressed tx per heartbeat to an append-only log (commit-DAG verifies, tamper detected);
    - deterministic / resume-safe (same cycles → same CIDs) and append-only;
    - G4 non-adjudicating (every obs carries :danjo.obs/non-adjudicating true; no verdict attr);
    - G5/G6 provenance (≥2 source-record CIDs + a method-note CID per observation);
    - no external I/O (offline corpus, local persist)."
  (:require [clojure.test :refer [deftest is run-tests]]
            [clojure.string :as str]
            #?(:clj [clojure.java.io :as io])
            [danjo.methods.autorun :as autorun]
            [danjo.methods.kotoba :as kotoba]))

#?(:clj
   (defn- tmp-log []
     (let [f (java.io.File/createTempFile "tmp" ".datoms.kotoba.edn")]
       (.delete f)
       f)))

(deftest test-heartbeat-persists
  (let [log (tmp-log)]
    (try
      (let [res (autorun/run-autonomous 3 autorun/corpus-default autorun/methods-default log)]
        (is (= 3 (get res "log_length")) "one tx per heartbeat")
        (is (every? #(> (get % "datoms") 0) (get res "beats")) "every heartbeat persisted datoms")
        (is (every? #(>= (get % "observations") 1) (get res "beats")) "discrepancy observations computed")
        (is (get (get res "chain") "ok") "commit-DAG verifies (chain OK)")
        (is (str/starts-with? (get res "head_cid") "b") "head CID is content-addressed"))
      (finally (.delete log)))))

(deftest test-deterministic-resume-safe
  (let [a (tmp-log) b (tmp-log)]
    (try
      (let [ra (autorun/run-autonomous 3 autorun/corpus-default autorun/methods-default a)
            rb (autorun/run-autonomous 3 autorun/corpus-default autorun/methods-default b)]
        (is (= (mapv #(get % "cid") (get ra "beats"))
               (mapv #(get % "cid") (get rb "beats")))
            "same cycles → same CIDs (deterministic / resume-safe)"))
      (finally (.delete a) (.delete b)))))

(deftest test-append-only-and-tamper
  (let [log (tmp-log)]
    (try
      (autorun/run-cycle 1 autorun/corpus-default autorun/methods-default log)
      (let [first-log (kotoba/read-log log)]
        (autorun/run-cycle 2 autorun/corpus-default autorun/methods-default log)
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
                                                      ":danjo.obs/non-adjudicating true"
                                                      ":danjo.obs/non-adjudicating false")
                                   ln))
                               lines)]
            (spit log (str (str/join "\n" tampered) "\n"))
            (let [v (kotoba/verify-chain log)]
              (is (and (not (get v "ok")) (= 0 (get v "broken_at")))
                  "tampering an earlier tx breaks the chain")))))
      (finally (.delete log)))))

(deftest test-g4-non-adjudicating-and-no-verdict
  ;; the defining danjo invariant: the censor's EYE, never the SWORD.
  (let [log (tmp-log)]
    (try
      (autorun/run-cycle 1 autorun/corpus-default autorun/methods-default log)
      (let [tx (nth (kotoba/read-log log) 0)
            datoms (get tx ":tx/datoms")
            obs-ents (set (for [d datoms :when (= (nth d 2) ":danjo.obs/category")] (nth d 1)))]
        (is (> (count obs-ents) 0) "observation entities persisted")
        (doseq [e obs-ents]
          (let [na (vec (for [d datoms
                              :when (and (= (nth d 1) e) (= (nth d 2) ":danjo.obs/non-adjudicating"))]
                          (nth d 3)))]
            (is (= [true] na) (str "observation " e " carries :danjo.obs/non-adjudicating true (G4)"))))
        (let [attrs (set (map #(str/lower-case (str (nth % 2))) datoms))]
          (doseq [tok ["verdict" "guilt" "wrongdoing" "crime" "violation" "illegal" "fraud"]]
            (is (not (some #(str/includes? % tok) attrs))
                (str "no verdict token `" tok "` in any attr (G4)")))))
      (finally (.delete log)))))

(deftest test-g5-g6-provenance
  (let [log (tmp-log)]
    (try
      (autorun/run-cycle 1 autorun/corpus-default autorun/methods-default log)
      (let [tx (nth (kotoba/read-log log) 0)
            datoms (get tx ":tx/datoms")
            obs-ents (set (for [d datoms :when (= (nth d 2) ":danjo.obs/category")] (nth d 1)))]
        (doseq [e obs-ents]
          (let [cids (vec (for [d datoms
                                :when (and (= (nth d 1) e) (= (nth d 2) ":danjo.obs/source-record-cids"))]
                            (nth d 3)))
                mnote (vec (for [d datoms
                                 :when (and (= (nth d 1) e) (= (nth d 2) ":danjo.obs/method-note-cid"))]
                             (nth d 3)))]
            (is (and (seq cids) (>= (count (first cids)) 2))
                (str "observation " e " cites ≥2 source-record CIDs (G5)"))
            (is (and (seq mnote) (seq (first mnote)))
                (str "observation " e " carries a method-note CID (G6)"))))
        (let [ops (set (map #(nth % 0) datoms))]
          (is (= #{":db/add"} ops) "every datom is append-only :db/add (no :db/retract)")))
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
      (let [res (autorun/run-autonomous 3 autorun/corpus-default autorun/methods-default log)
            cids (mapv #(get % "cid") (get res "beats"))]
        (is (= ["b64d0d46f2bda8271d848d0cb5973115e2e458e117dca94d1506f90197a71ce09"
                "b94361cdefdf6e0f4a817c49378ba1ca345f49e61795517fbf98b23ae5585ae2b"
                "b63be513cea39dd0c1a74f7ac9a17fc4d8443756e335fd57b70f2391090d8cc4f"]
               cids)
            "tx CIDs reproduce python3 autorun.py byte-for-byte"))
      (finally (.delete log)))))

#?(:clj
   (do
     (defn -main [& _] (run-tests 'danjo.methods.test-autorun))
     (when (= *file* (System/getProperty "babashka.file")) (-main))))
