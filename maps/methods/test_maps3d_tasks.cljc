(ns maps.methods.test-maps3d-tasks
  "Tests for the maps3d Datomic BPMN task handlers + the durable, append-only
  drive!/replay execution + the Datalog pending-tile entry. Everything runs over
  the offline kotoba-local Datom store — no Zeebe, no network."
  (:require [clojure.test :refer [deftest is]]
            [maps.methods.kotoba-local :as kl]
            [maps.methods.maps3d-tasks :as t]
            [maps.methods.maps3d-bpmn :as b]))

(defn- seed-tile! [store id h3 priority status]
  (kl/ingest-batch store
    {"entities" [{"id" id "type" "maps3d-tile"
                  "claims" [{"pred" "maps3d.tile/status" "value" status}
                            {"pred" "maps3d.tile/h3" "value" h3}
                            {"pred" "maps3d.tile/priority" "value" priority}]}]}))

;; ── ③ select-pending-tile (Datalog/AVET) ─────────────────────────────────────

(deftest test-select-pending-tile-none
  (is (= {:has-tile false} (t/select-pending-tile (kl/new-store)))))

(deftest test-select-pending-tile-picks-lowest-priority
  (let [s (kl/new-store)]
    (seed-tile! s "tile/a" "h3-a" "5" "pending")
    (seed-tile! s "tile/b" "h3-b" "1" "pending")
    (seed-tile! s "tile/c" "h3-c" "1" "done")     ; not pending → ignored
    (let [sel (t/select-pending-tile s)]
      (is (:has-tile sel))
      (is (= "h3-b" (:tile-h3 sel))))))            ; priority 1 wins

;; ── ① curate-images (LLM-free fallback) ──────────────────────────────────────

(deftest test-curate-empty-aborts
  (is (= {:abort true :selectedIds []} (t/curate-images {:candidates []}))))

(deftest test-curate-below-min-aborts
  (let [out (t/curate-images {:candidates [{:id "1" :qualityScore 0.9}
                                           {:id "2" :qualityScore 0.8}]
                              :min-count 8})]
    (is (:abort out))
    (is (= ["1" "2"] (:selectedIds out)))))

(deftest test-curate-selects-top-n-by-quality
  (let [cands (for [i (range 20)] {:id (str i) :qualityScore (- 1.0 (* i 0.01))})
        out (t/curate-images {:candidates cands :target-count 5 :min-count 3})]
    (is (false? (:abort out)))
    (is (= 5 (count (:selectedIds out))))
    (is (= "0" (first (:selectedIds out))))))      ; highest quality first

;; ── ① replan (rule branches) ─────────────────────────────────────────────────

(deftest test-replan-rules
  (is (= "downgradeOsm" (:action (t/replan {:attempt 3}))))
  (is (= "requestMore"  (:action (t/replan {:image-count 2 :attempt 1}))))
  (is (= "retry"        (:action (t/replan {:image-count 20 :attempt 1})))))

;; ── ① default-handlers driving the real engine ───────────────────────────────

(deftest test-default-handlers-happy-path-with-adapters
  ;; fetch supplies candidates, colmap succeeds → real curate runs → done.
  (let [adapters {"maps3d.fetchMapillary"
                  (fn [_] {:candidates (for [i (range 12)]
                                         {:id (str i) :qualityScore 0.9})})
                  "maps3d.colmapTile" (fn [_] {:recon-ok true})}
        h (t/default-handlers adapters)
        i (b/run (b/start-instance "t-real" "r") h)]
    (is (= :done (:status i)))
    ;; curate actually selected images (not a stub)
    (is (seq (get-in i [:vars :selectedIds])))))

(deftest test-default-handlers-too-few-images-aborts-via-real-curate
  (let [adapters {"maps3d.fetchMapillary"
                  (fn [_] {:candidates [{:id "1" :qualityScore 0.9}]})}  ; < min-count
        i (b/run (b/start-instance "t-few" "r") (t/default-handlers adapters))]
    (is (= :failed (:status i)))))                 ; curate abort → end-fail

(deftest test-default-handlers-replan-downgrade-via-real-replan
  ;; colmap fails, attempt forced ≥3 → real replan returns downgradeOsm.
  (let [adapters {"maps3d.fetchMapillary" (fn [_] {:candidates (for [i (range 12)]
                                                                 {:id (str i) :qualityScore 0.9})})
                  "maps3d.colmapTile" (fn [_] {:recon-ok false :attempt 3})}
        i (b/run (b/start-instance "t-dg" "r") (t/default-handlers adapters))]
    (is (= :osm-only (:status i)))))

;; ── ② durable drive! + replay (append-only Datom log) ────────────────────────

(deftest test-drive-appends-events-and-replays
  (let [store (kl/new-store)
        adapters {"maps3d.fetchMapillary" (fn [_] {:candidates (for [i (range 12)]
                                                                 {:id (str i) :qualityScore 0.9})})
                  "maps3d.colmapTile" (fn [_] {:recon-ok true})}
        final (b/drive! store (b/start-instance "t-drv" "r") (t/default-handlers adapters))
        replayed (b/replay store (:instance-id final))]
    (is (= :done (:status final)))
    ;; the append-only event log reconstructs the same terminal state
    (is (= :done (:status replayed)))
    (is (= (:token final) (:token replayed)))
    ;; one event datom per transition (events are append-only, never mutated)
    (let [evts (kl/avet-query store "bpmn.event/instance" [(:instance-id final)])]
      (is (= (count (:history replayed)) (count evts)))
      (is (> (count evts) 1)))))

(deftest test-drive-step-limit-on-pathological-loop
  (let [store (kl/new-store)
        adapters {"maps3d.fetchMapillary" (fn [_] {:candidates (for [i (range 12)]
                                                                 {:id (str i) :qualityScore 0.9})})
                  "maps3d.colmapTile" (fn [_] {:recon-ok false})
                  ;; replan always retry → infinite without the backstop
                  "maps3d.replanReconstruction" (fn [_] {:action "retry"})}
        final (b/drive! store (b/start-instance "t-loop" "r")
                        (merge (t/default-handlers adapters)
                               {"maps3d.replanReconstruction" (fn [_] {:action "retry"})})
                        10)]
    (is (= :step-limit (:status final)))
    (is (= :step-limit (:status (b/replay store (:instance-id final)))))))

;; ── ③ claim-and-start (Datomic entry) ────────────────────────────────────────

(deftest test-claim-and-start-no-pending-returns-nil
  (is (nil? (b/claim-and-start (kl/new-store) "r"))))

(deftest test-claim-and-start-starts-instance-for-pending-tile
  (let [s (kl/new-store)]
    (seed-tile! s "tile/x" "h3-x" "1" "pending")
    (let [inst (b/claim-and-start s "run9")]
      (is (some? inst))
      (is (= "h3-x" (:tile-h3 inst)))
      (is (= :start (:token inst))))))
