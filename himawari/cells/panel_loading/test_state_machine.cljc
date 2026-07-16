(ns himawari.cells.panel-loading.test-state-machine
  "Tests for the himawari panel_loading cell (ADR-2606021200 port)."
  (:require [clojure.test :refer [deftest is testing]]
            [himawari.cells.panel-loading.state-machine :as sm]))

(deftest test-panel-loading-happy-path-accepted
  (testing "Panel loading completes successfully with internal carrier"
    (let [result (sm/solve {"loadingId" "load-001"
                            "moduleSerials" ["mod-001" "mod-002" "mod-003"]
                            "carrierDid" "did:web:etzhayyim.com:hikari"
                            "carrierInternal" true
                            "loaderPhase" "Done"
                            "loaderRobotDid" "did:web:etzhayyim.com:sarutahiko#F10-loader"
                            "palletCapacity" 36
                            "humanTasksRemoved" ["pick" "place"]
                            "recordedAt" "2026-06-01T00:00:00Z"
                            "attestingRobots" ["did:web:otete"]})]
      (is (false? (get result "refused")))
      (is (some? (get result "loadingRecord")))
      (is (= 3 (count (get (get result "loadingRecord") "moduleSerials"))))
      (is (= 1 (get result "palletCount"))))))

(deftest test-panel-loading-g12-external-carrier-rejected
  (testing "G12 gate rejects external carrier (non-hikari)"
    (is (thrown? clojure.lang.ExceptionInfo
                 (sm/solve {"loadingId" "load-002"
                            "moduleSerials" ["mod-001"]
                            "carrierDid" "did:web:external-seller.com"
                            "carrierInternal" false
                            "loaderPhase" "Done"})))))

(deftest test-panel-loading-missing-loadingId-raises
  (testing "Missing loadingId raises contract violation"
    (is (thrown? clojure.lang.ExceptionInfo
                 (sm/solve {"moduleSerials" ["mod-001"]
                            "carrierDid" "did:web:etzhayyim.com:hikari"
                            "carrierInternal" true})))))

(deftest test-panel-loading-empty-moduleSerials-raises
  (testing "Empty moduleSerials raises contract violation"
    (is (thrown? clojure.lang.ExceptionInfo
                 (sm/solve {"loadingId" "load-003"
                            "moduleSerials" []
                            "carrierDid" "did:web:etzhayyim.com:hikari"
                            "carrierInternal" true})))))

(deftest test-panel-loading-pallet-count-calculation
  (testing "Pallet count calculation is correct (ceil division)"
    (let [result (sm/solve {"loadingId" "load-004"
                            "moduleSerials" (mapv #(str "mod-" %) (range 37))  ;; 37 modules
                            "carrierDid" "did:web:etzhayyim.com:hikari"
                            "carrierInternal" true
                            "loaderPhase" "Done"
                            "palletCapacity" 36})]
      (is (= 2 (get result "palletCount"))))))
