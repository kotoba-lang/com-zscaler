(ns tatara.methods.test-kotoba
  "tatara 鑪 — kotoba commit-DAG tests (ADR-2606171800).

  Verifies the append-only content-addressed Datom log: deterministic CIDs, commit-DAG
  linkage, tamper-detection, derived-flagging, and the load-bearing G4 gate — no per-worker
  attribute is representable in the persisted datoms (a plant employment figure is an
  aggregate SIZE, never a roster)."
  (:require [clojure.test :refer [deftest is run-tests]]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [tatara.methods.analyze :as az]
            [tatara.methods.kotoba :as kt]))

(def actor-dir (-> *file* io/file .getParentFile .getParentFile))
(def seed (io/file actor-dir "data" "seed-plant-graph.kotoba.edn"))

(defn rows [] (az/load-edn seed))

(deftest test-graph-datoms-are-eavt-add-only
  (let [ds (kt/graph-datoms (rows))]
    (is (pos? (count ds)))
    (is (every? (fn [d] (and (= 4 (count d)) (= :db/add (first d)))) ds))))

(deftest test-no-per-worker-attribute-in-persisted-datoms  ;; ── load-bearing G4 gate ──
  (let [ds (kt/graph-datoms (rows))]
    (doseq [[_ _ attr _] ds]
      (is (not (#{"worker" "person"} (namespace attr)))
          (str "forbidden per-individual attribute persisted: " attr)))))

(deftest test-cid-is-deterministic
  (let [ds (kt/graph-datoms (rows))]
    (is (= (kt/tx-cid ds "") (kt/tx-cid ds "")))
    (is (str/starts-with? (kt/tx-cid ds "") "b"))))

(deftest test-commit-dag-links-and-verifies
  (let [g (az/classify (rows))
        a (az/analyze g)
        ds1 (kt/graph-datoms (rows))
        ds2 (kt/derived-datoms a)
        tx1 (kt/make-tx ds1 :tx-id "t1" :as-of "2026-06-17T00:00:00Z" :prev-cid "")
        tx2 (kt/make-tx ds2 :tx-id "t2" :as-of "2026-06-17T00:00:01Z" :prev-cid (:tx/cid tx1))
        log (io/file actor-dir "out" "test-kotoba-log.edn")]
    (.delete log)
    (kt/append-tx tx1 log)
    (kt/append-tx tx2 log)
    (let [v (kt/verify-chain log)]
      (is (:ok v))
      (is (= 2 (:length v))))
    (is (= (:tx/cid tx2) (kt/head-cid log)))
    ;; tamper: rewrite the log with a mutated datom value → chain must break
    (let [bad (assoc-in tx1 [:tx/datoms 0 3] "TAMPERED")
          hdr ";; hdr\n"]
      (spit log hdr)
      (spit log (str (pr-str bad) "\n") :append true)
      (spit log (str (pr-str tx2) "\n") :append true)
      (is (false? (:ok (kt/verify-chain log)))))
    (.delete log)))

(deftest test-derived-datoms-are-flagged
  (let [a (az/analyze (az/classify (rows)))
        ds (kt/derived-datoms a)]
    (is (some (fn [[_ _ attr v]] (and (= :concentration/derived attr) (true? v))) ds))
    (is (some (fn [[_ _ attr v]] (and (= :concentration/sector attr) (= :semiconductor v))) ds))
    ;; the logistics modal/commodity split (computed by analyze) is now persisted too
    (is (some (fn [[_ _ attr v]] (and (= :split/mode attr) (= :sea v))) ds))
    (is (some (fn [[_ _ attr v]] (and (= :split/commodity attr) (= :components v))) ds))
    (is (some (fn [[_ _ attr v]] (and (= :split/derived attr) (true? v))) ds))))

#?(:clj
   (defn -main [& _]
     (let [{:keys [fail error]} (run-tests 'tatara.methods.test-kotoba)]
       (System/exit (if (zero? (+ fail error)) 0 1)))))
