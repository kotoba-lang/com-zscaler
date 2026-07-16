(ns kamado.cells.asset-observation.test-state-machine
  "Tests for kamado asset_observation state machine (ADR-2606051500).
  R0 scaffold: verifies the cell raises correctly before Council activation."
  (:require [clojure.test :refer [deftest is]]
            [kamado.cells.asset-observation.state-machine :as sm]))

(deftest solve-raises-r0-scaffold
  (is (thrown-with-msg?
       clojure.lang.ExceptionInfo
       #"kamado R0 scaffold"
       (sm/solve {}))))

(deftest solve-raises-with-correct-actor-metadata
  (try
    (sm/solve {"projectId" "TEST"})
    (is false "expected exception")
    (catch clojure.lang.ExceptionInfo e
      (is (= :kamado (:actor (ex-data e))))
      (is (= :asset-observation (:cell (ex-data e))))
      (is (= :r0-scaffold (:status (ex-data e)))))))
