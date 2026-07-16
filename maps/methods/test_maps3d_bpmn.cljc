(ns maps.methods.test-maps3d-bpmn
  "Tests for the maps3d processTile BPMN engine executed on kotoba/Datomic
  (Zeebe-free). Drives the pure interpreter through every gateway branch with
  stub handlers, then asserts instance state lands in the kotoba Datom store and
  is queryable back (EAVT/AVET)."
  (:require [clojure.test :refer [deftest is]]
            [maps.methods.kotoba-local :as kl]
            [maps.methods.ingest :as ingest]
            [maps.methods.maps3d-bpmn :as b]))

(defn- handlers
  "Stub task handlers (task-type → fn[vars]→output). Each test passes the
  outputs that steer the gateways."
  [overrides]
  (merge {"maps3d.fetchMapillary"       (fn [_] {})
          "maps3d.curateImages"         (fn [_] {:abort false})
          "maps3d.colmapTile"           (fn [_] {:recon-ok true})
          "maps3d.replanReconstruction" (fn [_] {:action "retry"})
          "maps3d.simplifyAndExport"    (fn [_] {})
          "maps3d.visionAnnotate"       (fn [_] {})
          "maps3d.linkActor"            (fn [_] {})
          "generic.db.insert"           (fn [_] {})}
         overrides))

;; ── start + gateways ─────────────────────────────────────────────────────────

(deftest test-start-instance-token-at-start
  (let [i (b/start-instance "t-abc" "run1")]
    (is (= :start (:token i)))
    (is (= :running (:status i)))
    (is (= "maps3d-tile/t-abc/run1" (:instance-id i)))))

(deftest test-resolve-gateway-enough-images
  (let [node (get b/process-tile-def :gw-enough)]
    (is (= :end-fail (b/resolve-gateway node {:abort true})))
    (is (= :colmap   (b/resolve-gateway node {:abort false})))))

(deftest test-resolve-gateway-replan-action
  (let [node (get b/process-tile-def :gw-replan)]
    (is (= :colmap   (b/resolve-gateway node {:action "retry"})))
    (is (= :fetch    (b/resolve-gateway node {:action "requestMore"})))
    (is (= :end-osm  (b/resolve-gateway node {:action "downgradeOsm"})))
    (is (= :end-fail (b/resolve-gateway node {:action "abort"})))
    (is (= :end-fail (b/resolve-gateway node {})))))   ; no match → dead-end guard

(deftest test-resolve-gateway-reconstruct-ok
  (let [node (get b/process-tile-def :gw-recon)]
    (is (= :simplify (b/resolve-gateway node {:recon-ok true})))
    (is (= :replan   (b/resolve-gateway node {:recon-ok false})))))

;; ── terminal? + replay edge cases ────────────────────────────────────────────

(deftest test-terminal?-predicate
  (is (b/terminal? (assoc (b/start-instance "t" "r") :token :end-ok :status :done)))
  (is (b/terminal? (assoc (b/start-instance "t" "r") :status :failed)))
  (is (not (b/terminal? (b/start-instance "t" "r")))))   ; :running at :start

(deftest test-replay-no-events-returns-nil
  (is (nil? (b/replay (kl/new-store) "absent/instance"))))

;; ── snapshot persistence (instance->batch + persist-instance!) ───────────────

(deftest test-instance->batch-snapshot-shape
  (let [i (assoc (b/start-instance "t-snap" "r") :token :colmap :status :running)
        ent (get-in (b/instance->batch i) ["entities" 0])
        preds (set (map #(get % "pred") (get ent "claims")))]
    (is (= "bpmn-instance" (get ent "type")))
    (is (contains? preds "bpmn.instance/token"))
    (is (contains? preds "bpmn.instance/process"))))

;; ── drive-live! operator-gate refusals (no network) ──────────────────────────

(deftest test-drive-live-refuses-without-gate
  (binding [ingest/*getenv* (fn [_] nil)]
    (is (thrown-with-msg? clojure.lang.ExceptionInfo #"G7"
          (b/drive-live! (b/start-instance "t" "r") {})))))

(deftest test-drive-live-refuses-without-credentials
  (binding [ingest/*getenv* (fn [k] (when (= k "MAPS_OPERATOR_GATE") "1"))]
    (is (thrown-with-msg? clojure.lang.ExceptionInfo #"KOTOBA_AUTH"
          (b/drive-live! (b/start-instance "t" "r") {})))))

(deftest test-push-instance-refuses-without-gate
  (binding [ingest/*getenv* (fn [_] nil)]
    (is (thrown-with-msg? clojure.lang.ExceptionInfo #"G7"
          (b/push-instance! (b/start-instance "t" "r"))))))

;; ── drive-live! / push-instance! success push paths (gate satisfied, no net) ──

(def ^:private gate-ok
  (fn [k] (get {"MAPS_OPERATOR_GATE" "1" "KOTOBA_AUTH" "tok" "KOTOBA_ENDPOINT" "http://localhost:0"} k)))

(def ^:private happy-handlers
  {"maps3d.fetchMapillary" (fn [_] {})
   "maps3d.curateImages" (fn [_] {:abort false :selectedIds ["a"]})
   "maps3d.colmapTile" (fn [_] {:recon-ok true})
   "maps3d.simplifyAndExport" (fn [_] {})
   "maps3d.visionAnnotate" (fn [_] {})
   "maps3d.linkActor" (fn [_] {})
   "generic.db.insert" (fn [_] {})})

(deftest test-drive-live-pushes-each-transition
  (let [pushes (atom [])]
    (binding [ingest/*getenv* gate-ok]
      (with-redefs [ingest/push-batch (fn [batch _auth _endpoint] (swap! pushes conj batch) [200 "ok"])]
        (let [final (b/drive-live! (b/start-instance "t-live" "r") happy-handlers)]
          (is (= :done (:status final)))
          ;; one push per transition = history length minus the initial :start token
          (is (= (dec (count (:history final))) (count @pushes)))
          (is (pos? (count @pushes)))
          ;; every pushed batch is a bpmn-event datom batch
          (is (every? #(= "bpmn-event" (get-in % ["entities" 0 "type"])) @pushes)))))))

(deftest test-push-instance-success-pushes-snapshot
  (let [pushes (atom [])]
    (binding [ingest/*getenv* gate-ok]
      (with-redefs [ingest/push-batch (fn [batch _auth _endpoint] (swap! pushes conj batch) [200 "ok"])]
        (let [r (b/push-instance! (assoc (b/start-instance "t-snap" "r") :token :colmap))]
          (is (= [200 "ok"] r))
          (is (= 1 (count @pushes)))
          (is (= "bpmn-instance" (get-in (first @pushes) ["entities" 0 "type"]))))))))

;; ── full runs through each terminal ──────────────────────────────────────────

(deftest test-happy-path-reaches-done
  (let [i (b/run (b/start-instance "t1" "r") (handlers {}))]
    (is (= :done (:status i)))
    (is (= :end-ok (:token i)))
    ;; visited the whole success chain in order (gateways included in the trace)
    (is (= [:start :fetch :curate :gw-enough :colmap :gw-recon
            :simplify :vision :link :mark-done :end-ok]
           (vec (:history i))))))

(deftest test-curator-abort-reaches-failed
  (let [i (b/run (b/start-instance "t2" "r")
                 (handlers {"maps3d.curateImages" (fn [_] {:abort true})}))]
    (is (= :failed (:status i)))
    (is (= :end-fail (:token i)))
    (is (not (some #{:colmap} (:history i))))))     ; never ran COLMAP

(deftest test-replan-retry-then-success-converges
  ;; colmap fails once → replan:retry → colmap succeeds → done.
  (let [colmap-calls (atom 0)
        h (handlers {"maps3d.colmapTile"
                     (fn [_] (swap! colmap-calls inc)
                       {:recon-ok (>= @colmap-calls 2)})
                     "maps3d.replanReconstruction" (fn [_] {:action "retry"})})
        i (b/run (b/start-instance "t3" "r") h)]
    (is (= :done (:status i)))
    (is (= 2 @colmap-calls))                          ; looped back through colmap
    (is (some #{:replan} (:history i)))))

(deftest test-replan-downgrade-reaches-osm-only
  (let [i (b/run (b/start-instance "t4" "r")
                 (handlers {"maps3d.colmapTile"           (fn [_] {:recon-ok false})
                            "maps3d.replanReconstruction" (fn [_] {:action "downgradeOsm"})}))]
    (is (= :osm-only (:status i)))
    (is (= :end-osm (:token i)))))

(deftest test-step-limit-backstops-infinite-replan-loop
  ;; pathological: colmap never succeeds and replan always retries.
  (let [i (b/run (b/start-instance "t5" "r")
                 (handlers {"maps3d.colmapTile"           (fn [_] {:recon-ok false})
                            "maps3d.replanReconstruction" (fn [_] {:action "retry"})})
                 12)]
    (is (= :step-limit (:status i)))))

;; ── Datomic persistence (state lives in the kotoba Datom log) ────────────────

(deftest test-instance-persists-to-datom-store-and-queries-back
  (let [store (kl/new-store)
        i (b/run (b/start-instance "t-geo" "r") (handlers {}))
        n (b/persist-instance! store i)
        ent (kl/entity store (:instance-id i))
        preds (set (map #(get % "pred") (get ent "claims")))]
    (is (= 1 n))
    (is (some? ent))
    (is (contains? preds "bpmn.instance/status"))
    (is (contains? preds "bpmn.instance/token"))
    ;; AVET: the instance is findable by its terminal status (Datom-log read path)
    (let [hits (kl/avet-query store "bpmn.instance/status" ["done"])]
      (is (= 1 (count hits)))
      (is (= (:instance-id i) (get (first hits) "id"))))))

(deftest test-instance-batch-records-visited-trace
  (let [i (b/run (b/start-instance "t6" "r") (handlers {}))
        batch (b/instance->batch i)
        claims (get-in batch ["entities" 0 "claims"])
        visited (filter #(= "bpmn.instance/visited" (get % "pred")) claims)]
    (is (= (count (:history i)) (count visited)))))
