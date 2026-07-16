(ns kamado.cells.transition-bridge.test-state-machine
  "Tests for kamado transition_bridge state machine (ADR-2606051500).
  R0 scaffold: verifies routes table is correct + solve raises before activation."
  (:require [clojure.test :refer [deftest is testing]]
            [kamado.cells.transition-bridge.state-machine :as sm]))

(deftest solve-raises-r0-scaffold
  (is (thrown-with-msg?
       clojure.lang.ExceptionInfo
       #"kamado R0 scaffold"
       (sm/solve {}))))

(deftest routes-table-has-expected-keys
  (testing "routes contains all expected asset types"
    (is (contains? sm/routes "converted-site"))
    (is (contains? sm/routes "dismantled-unit"))
    (is (contains? sm/routes "remediated-land"))
    (is (contains? sm/routes "fossil-policy-question"))
    (is (contains? sm/routes "displaced-worker"))))

(deftest routes-table-values-are-correct
  (testing "converted-site routes to hikari"
    (is (= ["hikari"] (sm/route-lookup "converted-site"))))
  (testing "dismantled-unit routes to hodoki/kanayama/haraedo"
    (is (= ["hodoki" "kanayama" "haraedo"] (sm/route-lookup "dismantled-unit"))))
  (testing "remediated-land routes to hikari and mitsuho"
    (is (= ["hikari" "mitsuho"] (sm/route-lookup "remediated-land"))))
  (testing "fossil-policy-question routes to danjo and moushibumi"
    (is (= ["danjo" "moushibumi"] (sm/route-lookup "fossil-policy-question"))))
  (testing "displaced-worker routes to displacement-dividend"
    (is (= ["displacement-dividend"] (sm/route-lookup "displaced-worker")))))

(deftest route-lookup-unknown-returns-empty
  (is (= [] (sm/route-lookup "unknown-asset-type"))))
