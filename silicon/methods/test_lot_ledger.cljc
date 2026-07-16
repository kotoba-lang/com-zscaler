(ns silicon.methods.test-lot-ledger
  "Tests for silicon.methods.lot-ledger."
  (:require [clojure.test :refer [deftest is]]
            [silicon.methods.fab-flow :as f]
            [silicon.methods.lot-ledger :as l]))

(defn- a-lot []
  (f/run-lot f/reference-lot f/default-route f/reference-recipe
             :silen-force-attest "ok"))

(deftest test-datoms-cover-every-step
  (let [rec (a-lot)
        datoms (l/lot->datoms rec)
        lot-eid (str "silicon-lot:" (:lot-id rec))]
    ;; all datoms are append-only :db/add
    (is (every? #(= ":db/add" (first %)) datoms))
    ;; one lot entity present with id + route
    (is (some #(= [":db/add" lot-eid ":silicon.lot/id" (:lot-id rec)] %) datoms))
    ;; G8: a step entity per process step (no truncation)
    (let [step-names (->> datoms
                          (filter #(= ":silicon.step/name" (nth % 2 nil)))
                          (map #(nth % 3)))]
      (is (= (map :step (:steps rec)) step-names)))))

(deftest test-measured-scalars-are-lossless
  (let [rec (a-lot)
        datoms (l/lot->datoms rec)
        ;; every measured key of the litho step should appear as :silicon.step/m.*
        litho (first (:steps rec))
        m-keys (set (map name (keys (:measured litho))))
        emitted (->> datoms
                     (map #(nth % 2))
                     (filter #(clojure.string/starts-with? % ":silicon.step/m."))
                     (map #(subs % (count ":silicon.step/m.")))
                     set)]
    (is (every? emitted m-keys))))

(deftest test-cid-deterministic
  (let [rec (a-lot)
        c1 (l/commit-lot rec)
        c2 (l/commit-lot rec)]
    (is (= (:tx/cid c1) (:tx/cid c2)))
    (is (clojure.string/starts-with? (:tx/cid c1) "b"))
    (is (= "" (:tx/prev c1)))
    (is (= (count (l/lot->datoms rec)) (:tx/count c1)))))

(deftest test-chain-and-verify
  (let [rec (a-lot)
        c1 (l/commit-lot rec)
        c2 (l/commit-lot rec (:tx/cid c1))]
    (is (= (:tx/cid c1) (:tx/prev c2)))
    (is (:ok (l/verify-chain [c1 c2])))
    (is (= 2 (:length (l/verify-chain [c1 c2]))))))

(deftest test-tamper-detected
  (let [rec (a-lot)
        c1 (l/commit-lot rec)
        ;; corrupt a datom value → CID no longer matches
        bad (assoc c1 :tx/datoms (conj (:tx/datoms c1) [":db/add" "x" ":y" 1]))
        v (l/verify-chain [bad])]
    (is (not (:ok v)))
    (is (= 0 (:broken-at v)))))

(deftest test-different-lots-differ
  (let [a (l/commit-lot (a-lot))
        b (l/commit-lot (f/run-lot (assoc f/reference-lot :lot-id "LOT-OTHER")
                                   f/default-route f/reference-recipe
                                   :silen-force-attest "ok"))]
    (is (not= (:tx/cid a) (:tx/cid b)))))
