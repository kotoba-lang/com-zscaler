(ns maps.tests.test-verify
  "maps — verify-reads tests (ADR-2606064500). stdlib; network-free.
  1:1 Clojure port of `methods/test_verify.py`."
  (:require [clojure.test :refer [deftest is run-tests]]
            [maps.methods.search    :as search-ns]
            [maps.methods.verify    :as verify]
            [maps.tests.kotoba-local :as kl]))

;; ── shared seed store ─────────────────────────────────────────────────────────

(def ^:private CELL "8a276524ddfffff")
(def ^:private STOP "f.station.tokyo")

(defn- make-full-store []
  (let [store (kl/make-store)
        toks  (search-ns/name-tokens "Tokyo Station")]
    (kl/ingest-batch!
     store
     {"entities"
      [{"id"     "st.tokyo"
        "claims" (into [{"pred" "feature/label"   "value" ":station"}
                        {"pred" "feature/name"    "value" "Tokyo Station"}
                        {"pred" "feature/lat"     "value" "35.6812"}
                        {"pred" "feature/lon"     "value" "139.7671"}
                        {"pred" "feature/sourcing" "value" ":representative"}
                        {"pred" (str "feature.cell/r" 10) "value" CELL}]
                       (map (fn [t] {"pred" "feature/name-token" "value" t})
                            (sort toks)))}
       {"id"     (str STOP ".stop.1")
        "claims" [{"pred" "transit.stop-time/stop"           "value" STOP}
                  {"pred" "transit.stop-time/departure-time" "value" "08:00:00"}
                  {"pred" "transit.stop-time/trip"           "value" "trip.001"}]}]})
    store))

(defn- always-cells [_lat _lon _res _ring] [CELL])

(deftest test-verify-reads-all-ok
  (let [store  (make-full-store)
        qfn    (kl/make-query-fn store)
        result (verify/verify-reads qfn always-cells
                                    :lat 35.6812 :lon 139.7671
                                    :res 10 :ring 2
                                    :query "tok" :stop-id STOP)]
    (is (map? result))
    (is (contains? result :all-ok))
    (is (contains? result :chunk))
    (is (contains? result :search))
    (is (contains? result :reverse))
    (is (contains? result :transit))))

(deftest test-verify-reads-chunk-ok
  (let [store  (make-full-store)
        qfn    (kl/make-query-fn store)
        result (verify/verify-reads qfn always-cells
                                    :lat 35.6812 :lon 139.7671
                                    :res 10 :ring 2
                                    :query "tok" :stop-id STOP)]
    (is (true? (get-in result [:chunk :ok])) "chunk read must succeed")))

(deftest test-verify-reads-search-ok
  (let [store  (make-full-store)
        qfn    (kl/make-query-fn store)
        result (verify/verify-reads qfn always-cells
                                    :lat 35.6812 :lon 139.7671
                                    :query "tok" :stop-id STOP)]
    (is (true? (get-in result [:search :ok])) "search read must succeed")))

(deftest test-verify-reads-transit-ok
  (let [store  (make-full-store)
        qfn    (kl/make-query-fn store)
        result (verify/verify-reads qfn always-cells
                                    :lat 35.6812 :lon 139.7671
                                    :query "tok" :stop-id STOP)]
    (is (true? (get-in result [:transit :ok])) "transit read must succeed")))

(deftest test-verify-reads-nil-ring-cells-chunk-note
  ;; When ring-cells-fn unavailable, chunk gets a :note and ok=false
  (let [store  (make-full-store)
        qfn    (kl/make-query-fn store)
        result (verify/verify-reads qfn nil
                                    :lat 35.6812 :lon 139.7671
                                    :query "tok" :stop-id STOP)]
    ;; chunk.ok should be false (no cells to probe)
    (is (false? (get-in result [:chunk :ok])))
    (is (some? (get-in result [:chunk :note])))))

(deftest test-verify-reads-empty-store
  ;; Empty store → all ok=false, report still emitted (fail-soft)
  (let [store  (kl/make-store)
        qfn    (kl/make-query-fn store)
        result (verify/verify-reads qfn always-cells
                                    :lat 35.6812 :lon 139.7671
                                    :query "tok" :stop-id STOP)]
    (is (map? result))
    (is (contains? result :all-ok))
    ;; No features → not all-ok
    (is (false? (:all-ok result)))))

#?(:clj
   (when (= *ns* (find-ns 'maps.tests.test-verify))
     (run-tests)))
