(ns tatara.methods.test-autorun
  "tatara 鑪 — autonomous-heartbeat tests (ADR-2606171800).

  The autonomous loop persists a content-addressed Datom transaction per cycle to the LOCAL
  append-only kotoba log. Verifies: commit-DAG verify, determinism (same cycles → same CIDs),
  append-only growth, derived-flagging, the G4 gate (no per-worker datom ever persisted), and
  the G7 posture (no external feed — runs entirely off the offline seed)."
  (:require [clojure.test :refer [deftest is run-tests]]
            [clojure.java.io :as io]
            [tatara.methods.autorun :as ar]
            [tatara.methods.kotoba :as kt]))

(def actor-dir (-> *file* io/file .getParentFile .getParentFile))
(def seed (io/file actor-dir "data" "seed-plant-graph.kotoba.edn"))
(defn- tmp [] (io/file actor-dir "out" "test-autorun-log.edn"))

(deftest test-heartbeat-persists-and-chain-verifies
  (let [log (tmp)]
    (.delete log)
    (let [res (ar/run-autonomous 3 seed log)]
      (is (= 3 (:cycles res)))
      (is (= 3 (count (:beats res))))
      (is (= 3 (:log-length res)))
      (is (:ok (:chain res)))
      (is (= (:head-cid res) (:cid (last (:beats res)))))
      ;; every beat saw all 33 plants and reported malacca as top chokepoint
      (is (every? #(= 33 (:plants %)) (:beats res)))
      (is (= "malacca" (:top-chokepoint (first (:beats res))))))
    (.delete log)))

(deftest test-determinism-same-cycles-same-cids
  (let [a (io/file actor-dir "out" "test-autorun-a.edn")
        b (io/file actor-dir "out" "test-autorun-b.edn")]
    (.delete a) (.delete b)
    (let [ra (ar/run-autonomous 2 seed a)
          rb (ar/run-autonomous 2 seed b)]
      (is (= (mapv :cid (:beats ra)) (mapv :cid (:beats rb))))
      (is (= (:head-cid ra) (:head-cid rb))))
    (.delete a) (.delete b)))

(deftest test-append-only-resume
  (let [log (tmp)]
    (.delete log)
    (ar/run-cycle 1 seed log)
    (let [len1 (count (kt/read-log log))
          head1 (kt/head-cid log)]
      (ar/run-cycle 2 seed log)               ;; resume — prev linked to head1
      (is (= 2 (count (kt/read-log log))))
      (is (> (count (kt/read-log log)) len1))
      (is (:ok (kt/verify-chain log)))
      (is (not= head1 (kt/head-cid log))))
    (.delete log)))

(deftest test-no-per-worker-datom-ever-persisted  ;; ── load-bearing G4 gate ──
  (let [log (tmp)]
    (.delete log)
    (ar/run-autonomous 2 seed log)
    (doseq [tx (kt/read-log log)]
      (doseq [[_ _ attr _] (:tx/datoms tx)]
        (is (not (#{"worker" "person"} (namespace attr)))
            (str "forbidden per-individual attribute persisted by heartbeat: " attr))))
    (.delete log)))

(deftest test-heartbeat-persists-cross-actor-composition
  (let [log (tmp)]
    (.delete log)
    (ar/run-cycle 1 seed log)
    (let [datoms (mapcat :tx/datoms (kt/read-log log))]
      ;; the autonomous loop now captures the full 静↔動↔cable resilience picture, not just
      ;; tatara's internal concentration
      (is (some (fn [[_ _ attr v]] (and (= :composition/chokepoint attr) (= :malacca v))) datoms))
      (is (some (fn [[_ _ attr v]] (and (= :composition/derived attr) (true? v))) datoms))
      (is (some (fn [[_ _ attr v]] (and (= :composition/exposure attr) (number? v))) datoms)))
    (.delete log)))

(deftest test-derived-concentration-signals-flagged
  (let [log (tmp)]
    (.delete log)
    (ar/run-cycle 1 seed log)
    (let [datoms (mapcat :tx/datoms (kt/read-log log))]
      (is (some (fn [[_ _ attr v]] (and (= :concentration/derived attr) (true? v))) datoms))
      (is (some (fn [[_ _ attr v]] (and (= :concentration/chokepoint attr) (= :malacca v))) datoms)))
    (.delete log)))

#?(:clj
   (defn -main [& _]
     (let [{:keys [fail error]} (run-tests 'tatara.methods.test-autorun)]
       (System/exit (if (zero? (+ fail error)) 0 1)))))
