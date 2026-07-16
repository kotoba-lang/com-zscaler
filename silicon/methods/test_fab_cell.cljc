(ns silicon.methods.test-fab-cell
  "Tests for silicon.methods.fab-cell (end-to-end orchestration)."
  (:require [clojure.test :refer [deftest is]]
            [silicon.methods.fab-cell :as cell]
            [silicon.methods.fab-flow :as flow]))

(deftest test-run-reference-end-to-end
  (let [out (cell/run-reference)]
    (is (= "LOT-IWAKURA-PE-0001" (:lot-id out)))
    (is (:all-pass out))
    (is (< 0.0 (:yield out) 1.000001))
    (is (pos? (:good-die out)))
    (is (<= (:packaged-units out) (:good-die out)))
    (is (clojure.string/starts-with? (:tx-cid out) "b"))
    (is (pos? (:datom-count out)))
    (is (:reachable out))
    (is (pos? (get-in out [:throughput :wph])))))

(deftest test-dual-use-route-needs-attest
  (is (thrown? clojure.lang.ExceptionInfo
               (cell/run-fab-lot flow/reference-lot {}))))   ; no attest, route has litho

(deftest test-unreachable-station-rejected
  (is (thrown-with-msg? clojure.lang.ExceptionInfo #"unreachable"
        (cell/run-fab-lot flow/reference-lot
                          {:attest "ok"
                           :arm {:l1 0.4 :l2 0.35}
                           :stations [{:x 9.0 :y 0.0}]}))))

(deftest test-ledger-chaining
  ;; chaining a second lot onto the first links prev→cid
  (let [a (cell/run-fab-lot flow/reference-lot {:attest "ok"})
        b (cell/run-fab-lot (assoc flow/reference-lot :lot-id "LOT-2")
                            {:attest "ok" :prev-cid (:tx-cid a)})]
    (is (= (:tx-cid a) (:tx-prev b)))
    (is (not= (:tx-cid a) (:tx-cid b)))))

(deftest test-no-handler-opts-still-runs
  ;; without :process-times / :arm the run still produces a committed lot
  (let [out (cell/run-fab-lot flow/reference-lot {:attest "ok"})]
    (is (contains? out :tx-cid))
    (is (not (contains? out :throughput)))
    (is (not (contains? out :reachable)))))
