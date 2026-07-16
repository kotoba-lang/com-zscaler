(ns shionome.methods.test-autorun
  "Cross-language oracle tests for shionome.methods.autorun — the Clojure port of
  methods/autorun.py (the AUTONOMOUS heartbeat loop).

  Ported 1:1 from the REAL Python test_autorun.py: the actor runs its full
  observe→validate→weave→analyze→dry-run-post→persist cycle by ITSELF over the kotoba
  Datom log, append-only + content-addressed, with NO live external I/O. Runs over the
  committed seed via the already-ported weave + social + kotoba + edn."
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.string :as str]
            [shionome.methods.autorun :as autorun]
            [shionome.methods.kotoba :as kotoba]))

(defn- tmp-log []
  (let [f (java.io.File/createTempFile "shionome-autorun" ".edn")]
    (.delete f)
    (.getAbsolutePath f)))

(def seed-path "20-actors/shionome/data/seed-capital-flow-graph.kotoba.edn")

(deftest single-cycle-persists-tx
  (let [log (tmp-log)]
    (try
      (let [beat (autorun/run-cycle 1 seed-path log)]
        (is (= "risk-on" (get beat "regime")))
        (is (> (get beat "datoms") 0))
        (is (= 3 (get beat "posts")))
        (is (= 1 (count (kotoba/read-log log)))))
      (finally (.delete (java.io.File. log))))))

(deftest multi-cycle-grows-append-only
  (let [log (tmp-log)]
    (try
      (let [res (autorun/run-autonomous 3 seed-path log)]
        (is (= 3 (get res "cycles")))
        (is (= 3 (get res "log_length")))
        (is (= true (get-in res ["chain" "ok"]))))
      (finally (.delete (java.io.File. log))))))

(deftest cycles-link-into-dag
  (let [log (tmp-log)]
    (try
      (autorun/run-autonomous 2 seed-path log)
      (let [txs (kotoba/read-log log)]
        ;; tx 2's prev is tx 1's cid (a real commit-DAG, not independent snapshots)
        (is (= (get (nth txs 0) ":tx/cid") (get (nth txs 1) ":tx/prev"))))
      (finally (.delete (java.io.File. log))))))

(deftest run-is-deterministic-resume-safe
  (let [la (tmp-log) lb (tmp-log)]
    (try
      (let [a (autorun/run-autonomous 2 seed-path la)
            b (autorun/run-autonomous 2 seed-path lb)]
        (is (= (get a "head_cid") (get b "head_cid"))))   ; same input → same content address
      (finally (.delete (java.io.File. la)) (.delete (java.io.File. lb))))))

(deftest persisted-posts-are-dry-run-only
  (let [log (tmp-log)]
    (try
      (autorun/run-cycle 1 seed-path log)
      (let [txs (kotoba/read-log log)
            statuses (->> (get (first txs) ":tx/datoms")
                          (filter #(= ":post/status" (nth % 2)))
                          (mapv #(nth % 3)))]
        (is (seq statuses))
        (is (every? #(= ":dry-run" %) statuses)))         ; G8 — never :published
      (finally (.delete (java.io.File. log))))))

(deftest no-published-status-anywhere
  (let [log (tmp-log)]
    (try
      (autorun/run-autonomous 2 seed-path log)
      (is (not (str/includes? (slurp log) ":published")))  ; outward publication stays G8-gated
      (finally (.delete (java.io.File. log))))))
