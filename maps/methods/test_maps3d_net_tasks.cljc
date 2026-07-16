(ns maps.methods.test-maps3d-net-tasks
  "Tests for the Clojure reimplementation of the maps3d network/LLM tasks. All
  I/O is exercised by rebinding the injectable seams (*http-get* / *http-post* /
  *llm-json* / *llm-vision* / *sleep!*) — no real Mapillary / COLMAP / Murakumo."
  (:require [clojure.test :refer [deftest is]]
            [maps.methods.maps3d-net-tasks :as n]
            [maps.methods.maps3d-tasks :as t]
            [maps.methods.maps3d-bpmn :as b]
            [maps.methods.ingest]))

;; ── fetchMapillary ───────────────────────────────────────────────────────────

(deftest test-fetch-mapillary-guards
  (is (= "tileH3 required" (:error (n/fetch-mapillary {:tile-h3 "" :token "t"}))))
  (is (= "MAPILLARY_TOKEN not set" (:error (n/fetch-mapillary {:tile-h3 "h" :token ""})))))

(deftest test-fetch-mapillary-filters-and-maps
  (binding [n/*http-get*
            (fn [_url _h]
              {"data" [{"id" "a" "thumb_1024_url" "http://x/a"
                        "computed_geometry" {"coordinates" [139.7 35.6]}
                        "quality_score" 0.9 "compass_angle" 12.0}
                       {"id" "b" "quality_score" 0.1}]})]  ; below min-quality → dropped
    (let [out (n/fetch-mapillary {:tile-h3 "h" :token "t" :bbox [139 35 140 36] :min-quality 0.5})]
      (is (:ok out))
      (is (= 1 (:totalAvailable out)))
      (let [c (first (:candidates out))]
        (is (= "a" (:id c))) (is (= 139.7 (:lng c))) (is (= 35.6 (:lat c)))))))

(deftest test-fetch-mapillary-http-error-is-caught
  (binding [n/*http-get* (fn [_ _] (throw (ex-info "HTTP 429" {})))]
    (let [out (n/fetch-mapillary {:tile-h3 "h" :token "t" :bbox [0 0 0 0]})]
      (is (false? (:ok out))))))

;; ── colmapTile ───────────────────────────────────────────────────────────────

(deftest test-colmap-missing-input
  (is (= "MISSING_INPUT" (:error-code (n/colmap-tile {:tile-h3 "" :selected-ids []})))))

(deftest test-colmap-submit-failure
  (binding [n/*http-post* (fn [_ _ _] (throw (ex-info "conn refused" {})))]
    (is (= "SUBMIT_FAILED" (:error-code (n/colmap-tile {:tile-h3 "t" :selected-ids ["a"]}))))))

(deftest test-colmap-no-job-id
  (binding [n/*http-post* (fn [_ _ _] {"unexpected" 1})]
    (is (= "NO_JOB_ID" (:error-code (n/colmap-tile {:tile-h3 "t" :selected-ids ["a"]}))))))

(deftest test-colmap-poll-done
  (binding [n/*http-post* (fn [_ _ _] {"jobId" "j1"})
            n/*http-get* (fn [_ _] {"status" "done" "rawMeshUri" "ipfs://m" "imageCount" 12})
            n/*sleep!* (fn [_] nil)]
    (let [out (n/colmap-tile {:tile-h3 "t" :selected-ids ["a" "b"]})]
      (is (:recon-ok out))
      (is (= "ipfs://m" (:raw-mesh-uri out)))
      (is (= 12 (:image-count out))))))

(deftest test-colmap-poll-failed
  (binding [n/*http-post* (fn [_ _ _] {"job_id" "j1"})
            n/*http-get* (fn [_ _] {"status" "failed" "errorCode" "DEGENERATE" "imageCount" 3})
            n/*sleep!* (fn [_] nil)]
    (let [out (n/colmap-tile {:tile-h3 "t" :selected-ids ["a"]})]
      (is (false? (:recon-ok out)))
      (is (= "DEGENERATE" (:error-code out))))))

(deftest test-colmap-poll-timeout
  (binding [n/*http-post* (fn [_ _ _] {"jobId" "j1"})
            n/*http-get* (fn [_ _] {"status" "running"})
            n/*sleep!* (fn [_] nil)]
    (let [out (n/colmap-tile {:tile-h3 "t" :selected-ids ["a"] :max-polls 3})]
      (is (= "POLL_TIMEOUT" (:error-code out))))))

(deftest test-colmap-builds-image-payload-from-urls
  ;; the submit body must pair each selected id with its thumbUrl from image-urls
  (let [posted (atom nil)]
    (binding [n/*http-post* (fn [_ _ body] (reset! posted body) {"jobId" "j1"})
              n/*http-get* (fn [_ _] {"status" "done" "rawMeshUri" "ipfs://m"})
              n/*sleep!* (fn [_] nil)]
      (n/colmap-tile {:tile-h3 "t" :selected-ids ["a" "b"]
                      :image-urls [{:id "a" :thumbUrl "ua"} {:id "b" :thumbUrl "ub"}]})
      (let [sent (maps.methods.ingest/parse-json @posted)]
        (is (= "t" (get sent "tileH3")))
        (is (= [{"id" "a" "url" "ua"} {"id" "b" "url" "ub"}] (get sent "images")))))))

(deftest test-colmap-transient-get-error-keeps-polling
  (let [calls (atom 0)]
    (binding [n/*http-post* (fn [_ _ _] {"jobId" "j1"})
              n/*http-get* (fn [_ _] (swap! calls inc)
                             (if (< @calls 2) (throw (ex-info "transient" {}))
                                 {"status" "done" "rawMeshUri" "ipfs://m"}))
              n/*sleep!* (fn [_] nil)]
      (is (:recon-ok (n/colmap-tile {:tile-h3 "t" :selected-ids ["a"] :max-polls 5}))))))

;; ── simplifyAndExport ────────────────────────────────────────────────────────

(deftest test-simplify-requires-raw-mesh
  (is (= "rawMeshUri required" (:error (n/simplify-and-export {:tile-h3 "t" :raw-mesh-uri ""})))))

(deftest test-simplify-poll-done
  (binding [n/*http-post* (fn [_ _ _] {"jobId" "s1"})
            n/*http-get* (fn [_ _] {"status" "done" "tileMeshUri" "ipfs://glb" "triangleCount" 4800})
            n/*sleep!* (fn [_] nil)]
    (let [out (n/simplify-and-export {:tile-h3 "t" :raw-mesh-uri "ipfs://m"})]
      (is (:ok out))
      (is (= "ipfs://glb" (:tile-mesh-uri out)))
      (is (= 4800 (:triangle-count out))))))

(deftest test-simplify-poll-failed
  (binding [n/*http-post* (fn [_ _ _] {"jobId" "s1"})
            n/*http-get* (fn [_ _] {"status" "failed" "errorMessage" "non-manifold"})
            n/*sleep!* (fn [_] nil)]
    (let [out (n/simplify-and-export {:tile-h3 "t" :raw-mesh-uri "ipfs://m"})]
      (is (false? (:ok out)))
      (is (= "non-manifold" (:error out))))))

(deftest test-simplify-poll-timeout
  (binding [n/*http-post* (fn [_ _ _] {"jobId" "s1"})
            n/*http-get* (fn [_ _] {"status" "running"})
            n/*sleep!* (fn [_] nil)]
    (let [out (n/simplify-and-export {:tile-h3 "t" :raw-mesh-uri "ipfs://m" :max-polls 3})]
      (is (false? (:ok out)))
      (is (= "simplify timed out" (:error out))))))

;; ── visionAnnotate ───────────────────────────────────────────────────────────

(deftest test-vision-parses-detections-and-filters-confidence
  (binding [n/*llm-vision*
            (fn [_sys _url]
              "noise {\"detections\":[{\"label\":\"Starbucks\",\"confidence\":0.9,\"category\":\"business\"},{\"label\":\"Blur\",\"confidence\":0.2}]} trailing")]
    (let [out (n/vision-annotate {:image-refs [{:id "i1" :thumbUrl "http://x/1"}] :min-confidence 0.55})]
      (is (= 1 (count (:detections out))))                ; low-confidence dropped
      (is (= "Starbucks" (:label (first (:detections out))))))))

(deftest test-vision-dedupes-labels-across-images
  (binding [n/*llm-vision*
            (fn [_ _] "{\"detections\":[{\"label\":\"Tower\",\"confidence\":0.9}]}")]
    (let [out (n/vision-annotate {:image-refs [{:id "i1" :thumbUrl "u1"}
                                               {:id "i2" :thumbUrl "u2"}]})]
      (is (= 1 (count (:detections out)))))))            ; same label deduped

(deftest test-vision-skips-blank-url-and-empty
  (binding [n/*llm-vision* (fn [_ _] "{\"detections\":[]}")]
    (is (= [] (:detections (n/vision-annotate {:image-refs [{:id "i" :thumbUrl ""}]}))))
    (is (= [] (:detections (n/vision-annotate {:image-refs []}))))))

(deftest test-vision-caps-at-sample-limit
  ;; only :sample images are sent to the LLM, even when more are provided
  (let [calls (atom 0)]
    (binding [n/*llm-vision* (fn [_ url] (swap! calls inc)
                               (str "{\"detections\":[{\"label\":\"L" url "\",\"confidence\":0.9}]}"))]
      (let [refs (for [i (range 5)] {:id (str i) :thumbUrl (str "u" i)})
            out (n/vision-annotate {:image-refs refs :sample 2})]
        (is (= 2 @calls))                          ; capped at :sample
        (is (= 2 (count (:detections out))))))))   ; distinct labels per url

;; ── linkActor ────────────────────────────────────────────────────────────────

(deftest test-link-empty-detections
  (is (= [] (:links (n/link-actor {:tile-h3 "t" :detections []})))))

(deftest test-link-filters-by-confidence-and-did
  (binding [n/*llm-json*
            (fn [_ _] {"data" {"links" [{"label" "Starbucks" "actorDid" "did:web:a" "confidence" 0.95 "source" "wikidata"}
                                        {"label" "Weak" "actorDid" "did:web:b" "confidence" 0.4}
                                        {"label" "NoDid" "actorDid" "" "confidence" 0.99}]}})]
    (let [out (n/link-actor {:tile-h3 "t" :detections [{:label "Starbucks"}] :min-confidence 0.7})]
      (is (= 1 (count (:links out))))
      (is (= "did:web:a" (:actorDid (first (:links out)))))
      (is (= "wikidata" (:source (first (:links out))))))))

(deftest test-link-llm-failure-degrades-gracefully
  (binding [n/*llm-json* (fn [_ _] (throw (ex-info "no llm" {})))]
    (let [out (n/link-actor {:tile-h3 "t" :detections [{:label "X"}]})]
      (is (:ok out))
      (is (= [] (:links out)))
      (is (some? (:warning out))))))

;; ── live-adapters wiring into the BPMN engine shape ──────────────────────────

(deftest test-live-adapters-shape-and-merge
  (let [ad (n/live-adapters {:token "tok" :bbox [0 0 0 0]})]
    (is (= #{"maps3d.fetchMapillary" "maps3d.colmapTile" "maps3d.simplifyAndExport"
             "maps3d.visionAnnotate" "maps3d.linkActor"}
           (set (keys ad))))
    ;; the fetch adapter merges cfg(token) with live vars and returns :candidates
    (binding [n/*http-get* (fn [_ _] {"data" [{"id" "a" "quality_score" 0.9
                                               "computed_geometry" {"coordinates" [1 2]}}]})]
      (let [out ((get ad "maps3d.fetchMapillary") {:tile-h3 "h"})]
        (is (:ok out))
        (is (= 1 (count (:candidates out))))))))

(deftest test-curated-refs-filters-to-selection
  (let [vars {:candidates [{:id "1" :thumbUrl "u1"} {:id "2" :thumbUrl "u2"} {:id "3" :thumbUrl "u3"}]
              :selectedIds ["1" "3"]}]
    (is (= #{"1" "3"} (set (map :id (n/curated-refs vars)))))))

;; ── end-to-end: the WHOLE pipeline runs in Clojure (no Python) ───────────────

(deftest test-whole-pipeline-runs-in-clj
  (binding [n/*http-get*  (fn [u _] (if (re-find #"/jobs/" u)
                                      {"status" "done" "rawMeshUri" "ipfs://m" "tileMeshUri" "ipfs://glb" "triangleCount" 4800}
                                      {"data" (vec (for [i (range 12)]
                                                     {"id" (str i) "thumb_1024_url" (str "u" i)
                                                      "quality_score" 0.9
                                                      "computed_geometry" {"coordinates" [139.7 35.6]}}))}))
            n/*http-post* (fn [_ _ _] {"jobId" "j1"})
            n/*llm-vision* (fn [_ _] "{\"detections\":[{\"label\":\"Cafe\",\"confidence\":0.9}]}")
            n/*llm-json*   (fn [_ _] {"data" {"links" [{"label" "Cafe" "actorDid" "did:web:x" "confidence" 0.9}]}})
            n/*sleep!*     (fn [_] nil)]
    (let [handlers (t/default-handlers (n/live-adapters {:token "tok" :bbox [139 35 140 36]}))
          final (b/run (b/start-instance "h3-allclj" "r") handlers)]
      (is (= :done (:status final)))                          ; reached the success terminal
      (is (= 12 (count (get-in final [:vars :selectedIds]))))  ; curate ran
      (is (= 1 (count (get-in final [:vars :detections]))))    ; vision ran over curated refs
      (is (= 1 (count (get-in final [:vars :links])))))))      ; link ran
