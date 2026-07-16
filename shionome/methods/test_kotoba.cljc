(ns shionome.methods.test-kotoba
  "Cross-language oracle tests for shionome.methods.kotoba — the Clojure port of
  methods/kotoba.py (the content-addressed EAVT Datom-log writer).

  Ported 1:1 from the REAL Python test_kotoba.py. The assertions are structural —
  append-only :db/add, deterministic + prev-dependent CID, append/read round-trip,
  chain verification, tamper detection, dry-run post status, and the load-bearing
  regression: a post body with \\n\\n survives write→read (the shared EDN reader's
  JSON-faithful unescape closes the commit-DAG). Run over the committed
  seed-capital-flow-graph.kotoba.edn via the already-ported weave + edn reader."
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.string :as str]
            [shionome.methods.kotoba :as kotoba]
            [shionome.methods.weave :as weave]
            [shionome.methods.edn :as edn]))

(def seed-path "20-actors/shionome/data/seed-capital-flow-graph.kotoba.edn")
(defn- g [] (weave/weave (edn/load-edn seed-path)))

(defn- tmp-log []
  (let [f (java.io.File/createTempFile "shionome-kotoba" ".edn")]
    (.delete f)                                       ; first append sees a missing file
    (.getAbsolutePath f)))

(deftest graph-datoms-are-append-only
  (let [datoms (kotoba/graph-datoms (g))]
    (is (seq datoms))
    (is (every? #(= ":db/add" (first %)) datoms))))   ; no :db/retract (非終末論)

(deftest tx-cid-deterministic
  (let [d (kotoba/graph-datoms (g))]
    (is (= (kotoba/tx-cid d "") (kotoba/tx-cid d "")))))

(deftest tx-cid-depends-on-prev
  (let [d (kotoba/graph-datoms (g))]
    (is (not= (kotoba/tx-cid d "") (kotoba/tx-cid d "bdeadbeef")))))

(deftest append-and-read-roundtrip
  (let [log (tmp-log)]
    (try
      (let [tx (kotoba/make-tx (kotoba/graph-datoms (g)) {:tx-id 1 :as-of 20260607 :prev-cid ""})
            cid (kotoba/append-tx tx log)
            back (kotoba/read-log log)]
        (is (= 1 (count back)))
        (is (= cid (get (first back) ":tx/cid"))))
      (finally (.delete (java.io.File. log))))))

(deftest chain-verifies-ok
  (let [log (tmp-log)]
    (try
      (loop [i 1, prev ""]
        (when (< i 4)
          (let [tx (kotoba/make-tx (kotoba/graph-datoms (g)) {:tx-id i :as-of (+ 20260607 i) :prev-cid prev})]
            (recur (inc i) (kotoba/append-tx tx log)))))
      (let [v (kotoba/verify-chain log)]
        (is (= true (get v "ok")))
        (is (= 3 (get v "length"))))
      (finally (.delete (java.io.File. log))))))

(deftest head-cid-is-last
  (let [log (tmp-log)]
    (try
      (let [last-cid (loop [i 1, last ""]
                       (if (< i 3)
                         (let [tx (kotoba/make-tx (kotoba/graph-datoms (g))
                                                  {:tx-id i :as-of (+ 20260607 i)
                                                   :prev-cid (kotoba/head-cid log)})]
                           (recur (inc i) (kotoba/append-tx tx log)))
                         last))]
        (is (= last-cid (kotoba/head-cid log))))
      (finally (.delete (java.io.File. log))))))

(deftest tamper-breaks-chain
  (let [log (tmp-log)]
    (try
      (loop [i 1]
        (when (< i 3)
          (let [tx (kotoba/make-tx (kotoba/graph-datoms (g))
                                   {:tx-id i :as-of (+ 20260607 i) :prev-cid (kotoba/head-cid log)})]
            (kotoba/append-tx tx log)
            (recur (inc i)))))
      (let [lines (str/split-lines (slurp log))
            tampered (assoc (vec lines) 1 (str/replace (nth lines 1) "US equities" "TAMPERED"))]
        (spit log (str (str/join "\n" tampered) "\n")))
      (is (= false (get (kotoba/verify-chain log) "ok")))
      (finally (.delete (java.io.File. log))))))

(deftest post-datoms-status-dry-run
  (let [posts [{":post/subject" "netflow" ":post/status" ":dry-run" ":post/body" "x"}]
        datoms (kotoba/post-datoms posts)
        statuses (->> datoms (filter #(= ":post/status" (nth % 2))) (mapv #(nth % 3)))]
    (is (= [":dry-run"] statuses))))

(deftest post-body-with-newlines-roundtrips
  (testing "the bug that broke the DAG: a post body has \\n\\n; it must survive write→read"
    (let [log (tmp-log)]
      (try
        (let [posts [{":post/subject" "regime" ":post/status" ":dry-run"
                      ":post/body" "line one\n\nline two with 日本語"}]
              tx (kotoba/make-tx (kotoba/post-datoms posts) {:tx-id 1 :as-of 20260607 :prev-cid ""})]
          (kotoba/append-tx tx log)
          (is (= true (get (kotoba/verify-chain log) "ok"))))
        (finally (.delete (java.io.File. log)))))))
