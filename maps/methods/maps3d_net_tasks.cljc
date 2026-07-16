(ns maps.methods.maps3d-net-tasks
  "maps3d network/LLM task bodies, reimplemented in Clojure (the I/O tasks that
  were previously Python-only). Together with maps.methods.maps3d-tasks (the pure
  curate/replan logic) this completes the clj port of the Zeebe worker tasks, so
  the kotoba/Datomic BPMN engine can run the WHOLE pipeline without Python.

  Port of the Python primitives: task_maps3d_fetch_mapillary / _colmap_tile /
  _simplify_and_export / _vision_annotate / _link_actor.

  House style (ingest.cljc): the task logic — URL/param building, response
  shaping, quality filtering, JSON-from-LLM extraction, confidence filtering — is
  pure and unit-tested by REBINDING the I/O seams (*http-get* / *http-post* /
  *llm-json* / *llm-vision* / *sleep!*). The #?(:clj ...) defaults perform the
  real HTTP via java.net and reuse ingest's JSON codec; the LLM seams default to
  throwing so an operator MUST inject a Murakumo client (no silent no-op)."
  (:require [clojure.string :as str]
            [maps.methods.ingest :as ingest]))

;; ── injectable I/O seams ─────────────────────────────────────────────────────

#?(:clj
   (defn- http-default
     "Raw-socket HTTP/1.1 request (mirrors ingest/push-batch's dependency-free
     approach — bb's SCI sandbox blocks HttpURLConnection reflective calls, but
     allows java.net.Socket / SSLSocket). http → Socket, https → SSLSocket.
     Reads the full Connection:close response, splits headers/body, parses the
     body as JSON (the parsed body is returned even on 4xx, {} on parse failure)."
     [method url headers body]
     (let [u (java.net.URI. url)
           https? (= "https" (.getScheme u))
           host (.getHost u)
           port (let [p (.getPort u)] (if (pos? p) p (if https? 443 80)))
           raw-path (.getRawPath u)
           query (.getRawQuery u)
           path (str (if (str/blank? raw-path) "/" raw-path) (when query (str "?" query)))
           bb (when body (.getBytes ^String body "UTF-8"))
           sock (if https?
                  (.createSocket (javax.net.ssl.SSLSocketFactory/getDefault))
                  (java.net.Socket.))]
       (try
         (.connect sock (java.net.InetSocketAddress. host (int port)) 30000)
         (.setSoTimeout sock 60000)
         (let [out (.getOutputStream sock)
               in (.getInputStream sock)
               req (StringBuilder.)]
           (.append req (str method " " path " HTTP/1.1\r\nHost: " host "\r\nConnection: close\r\n"))
           (doseq [[k v] headers] (.append req (str (name k) ": " v "\r\n")))
           (when bb (.append req (str "content-type: application/json\r\nContent-Length: " (count bb) "\r\n")))
           (.append req "\r\n")
           (.write out (.getBytes (.toString req) "UTF-8"))
           (when bb (.write out bb))
           (.flush out)
           (let [baos (java.io.ByteArrayOutputStream.)
                 buf (byte-array 4096)]
             (loop [] (let [r (.read in buf)] (when (>= r 0) (.write baos buf 0 r) (recur))))
             (let [resp (String. (.toByteArray baos) "UTF-8")
                   idx (.indexOf resp "\r\n\r\n")
                   body-str (if (>= idx 0) (subs resp (+ idx 4)) "")]
               (or (try (ingest/parse-json body-str) (catch Exception _ nil)) {}))))
         (finally (.close sock))))))

(def ^:dynamic *http-get*
  "(fn [url headers] => parsed-json-map)."
  #?(:clj (fn [url headers] (http-default "GET" url headers nil))
     :default (fn [_ _] (throw (ex-info "*http-get* not configured" {})))))

(def ^:dynamic *http-post*
  "(fn [url headers json-string] => parsed-json-map)."
  #?(:clj (fn [url headers body] (http-default "POST" url headers body))
     :default (fn [_ _ _] (throw (ex-info "*http-post* not configured" {})))))

(def ^:dynamic *llm-json*
  "(fn [system user] => parsed-map). Default throws — inject a Murakumo client."
  (fn [_ _] (throw (ex-info "*llm-json* not configured (inject a Murakumo client)" {}))))

(def ^:dynamic *llm-vision*
  "(fn [system image-url] => content-string). Default throws — inject Murakumo."
  (fn [_ _] (throw (ex-info "*llm-vision* not configured (inject a Murakumo client)" {}))))

(def ^:dynamic *sleep!* (fn [_ms] nil))

;; ── helpers ──────────────────────────────────────────────────────────────────

(defn- as-double [v] (try (double (or v 0)) (catch #?(:clj Exception :default :default) _ 0.0)))
(defn- g [m & ks] (some #(get m %) ks))           ; first present of string/keyword keys

;; ── fetchMapillary ───────────────────────────────────────────────────────────

(def mapillary-base "https://graph.mapillary.com")

(defn fetch-mapillary
  "Fetch Mapillary v4 candidates for a tile bbox. Pure given *http-get*. opts:
  {:tile-h3 :bbox [w s e n] :token :max-images :min-quality :base-url}."
  [{:keys [tile-h3 bbox token max-images min-quality base-url]
    :or {max-images 200 min-quality 0.5 base-url mapillary-base}}]
  (cond
    (str/blank? (str tile-h3)) {:ok false :candidates [] :totalAvailable 0 :error "tileH3 required"}
    (str/blank? (str token)) {:ok false :candidates [] :totalAvailable 0 :error "MAPILLARY_TOKEN not set"}
    :else
    (let [[w s e n] (or bbox [0 0 0 0])
          url (str base-url "/images?access_token=" token
                   "&fields=id,thumb_1024_url,computed_geometry,quality_score,captured_at,compass_angle"
                   "&bbox=" w "," s "," e "," n "&limit=" (min max-images 2000))
          resp (try (*http-get* url {}) (catch #?(:clj Exception :default :default) ex
                                          {::error (#?(:clj #(.getMessage %) :default str) ex)}))]
      (if (::error resp)
        {:ok false :candidates [] :totalAvailable 0 :error (::error resp)}
        (let [images (get resp "data" [])
              cands (->> images
                         (filter #(>= (as-double (get % "quality_score")) min-quality))
                         (mapv (fn [img]
                                 (let [coords (get-in img ["computed_geometry" "coordinates"] [0 0])]
                                   {:id (get img "id" "")
                                    :thumbUrl (get img "thumb_1024_url" "")
                                    :lng (nth coords 0 0)
                                    :lat (nth coords 1 0)
                                    :qualityScore (as-double (get img "quality_score"))
                                    :capturedAt (get img "captured_at" "")
                                    :compassAngle (as-double (get img "compass_angle"))}))))]
          {:ok true :candidates cands :totalAvailable (count cands)})))))

;; ── colmapTile (submit + poll) ───────────────────────────────────────────────

(def colmap-worker-url "http://colmap-worker.mitama-udf.svc.cluster.local:8030")

(defn colmap-tile
  "Submit a COLMAP job and poll to completion. Pure given *http-post*/*http-get*/
  *sleep!*. Returns {:ok :recon-ok :raw-mesh-uri :image-count :error-code}."
  [{:keys [tile-h3 selected-ids image-urls worker-url dense matcher max-polls poll-ms]
    :or {worker-url colmap-worker-url dense true matcher "exhaustive" max-polls 240 poll-ms 15000}}]
  (if (or (str/blank? (str tile-h3)) (empty? selected-ids))
    {:ok false :recon-ok false :image-count 0 :error-code "MISSING_INPUT"
     :error-message "tileH3 and selectedIds required"}
    (let [id->url (into {} (for [it (or image-urls [])] [(str (g it "id" :id)) (str (g it "thumbUrl" :thumbUrl))]))
          payload (ingest/json-encode {"tileH3" tile-h3
                                       "images" (vec (for [sid selected-ids :when sid]
                                                       {"id" sid "url" (get id->url (str sid) "")}))
                                       "denseEnabled" dense "matcher" matcher})
          submit (try (*http-post* (str worker-url "/jobs") {} payload)
                      (catch #?(:clj Exception :default :default) ex {::error (str ex)}))]
      (cond
        (::error submit) {:ok false :recon-ok false :image-count 0 :error-code "SUBMIT_FAILED" :error-message (::error submit)}
        :else
        (let [job-id (or (get submit "jobId") (get submit "job_id"))]
          (if (str/blank? (str job-id))
            {:ok false :recon-ok false :image-count 0 :error-code "NO_JOB_ID" :error-message (str submit)}
            (loop [n 0]
              (if (>= n max-polls)
                {:ok false :recon-ok false :image-count 0 :error-code "POLL_TIMEOUT"
                 :error-message "COLMAP job did not complete within time budget"}
                (do
                  (when (pos? n) (*sleep!* poll-ms))
                  (let [st (try (*http-get* (str worker-url "/jobs/" job-id) {})
                                (catch #?(:clj Exception :default :default) _ {::transient true}))
                        status (str (get st "status" "unknown"))]
                    (cond
                      (::transient st) (recur (inc n))
                      (= status "done")
                      {:ok true :recon-ok true
                       :raw-mesh-uri (str (get st "rawMeshUri" "")) :image-count (int (as-double (get st "imageCount")))
                       :error-code "" :error-message ""}
                      (= status "failed")
                      {:ok false :recon-ok false :image-count (int (as-double (get st "imageCount")))
                       :error-code (str (get st "errorCode" "COLMAP_FAILED")) :error-message (str (get st "errorMessage" ""))}
                      :else (recur (inc n)))))))))))))

;; ── simplifyAndExport (submit + poll) ────────────────────────────────────────

(defn simplify-and-export
  [{:keys [tile-h3 raw-mesh-uri target-triangles worker-url max-polls poll-ms]
    :or {target-triangles 5000 worker-url colmap-worker-url max-polls 40 poll-ms 15000}}]
  (if (str/blank? (str raw-mesh-uri))
    {:ok false :tile-mesh-uri "" :triangle-count 0 :error "rawMeshUri required"}
    (let [payload (ingest/json-encode {"tileH3" tile-h3 "rawMeshUri" raw-mesh-uri "targetTriangles" target-triangles})
          submit (try (*http-post* (str worker-url "/simplify") {} payload)
                      (catch #?(:clj Exception :default :default) ex {::error (str ex)}))]
      (if (::error submit)
        {:ok false :tile-mesh-uri "" :triangle-count 0 :error (::error submit)}
        (let [job-id (or (get submit "jobId") (get submit "job_id"))]
          (if (str/blank? (str job-id))
            {:ok false :tile-mesh-uri "" :triangle-count 0 :error (str "no job_id: " submit)}
            (loop [n 0]
              (if (>= n max-polls)
                {:ok false :tile-mesh-uri "" :triangle-count 0 :error "simplify timed out"}
                (do
                  (when (pos? n) (*sleep!* poll-ms))
                  (let [st (try (*http-get* (str worker-url "/jobs/" job-id) {})
                                (catch #?(:clj Exception :default :default) _ {::transient true}))
                        status (str (get st "status" ""))]
                    (cond
                      (::transient st) (recur (inc n))
                      (= status "done")
                      {:ok true :tile-mesh-uri (str (get st "tileMeshUri" "")) :triangle-count (int (as-double (get st "triangleCount")))}
                      (= status "failed")
                      {:ok false :tile-mesh-uri "" :triangle-count 0 :error (str (get st "errorMessage" "simplify failed"))}
                      :else (recur (inc n)))))))))))))

;; ── visionAnnotate (LLM over curated images) ─────────────────────────────────

(def vision-system
  (str "You are a vision analysis assistant for 3D city modelling. Given a "
       "street-level image URL, list visible buildings, businesses, signage, and "
       "landmarks. Output ONLY valid JSON. "
       "Schema: {\"detections\":[{\"label\":\"<name>\",\"confidence\":0.0-1.0,"
       "\"category\":\"building\"|\"business\"|\"landmark\"|\"sign\"}]}"))

(defn- extract-json-block [content]
  (let [s (str content)
        b (str/index-of s "{")
        e (str/last-index-of s "}")]
    (when (and b e (> e b))
      (try (ingest/parse-json (subs s b (inc e))) (catch #?(:clj Exception :default :default) _ nil)))))

(defn vision-annotate
  "Run the injected vision LLM over up to :sample image refs, parse detections,
  dedupe by label, keep confidence ≥ :min-confidence. Pure given *llm-vision*."
  [{:keys [image-refs min-confidence sample] :or {min-confidence 0.55 sample 10}}]
  (loop [refs (take sample (or image-refs []))
         seen #{}
         out []]
    (if (empty? refs)
      {:ok true :detections out}
      (let [ref (first refs)
            url (str (g ref "thumbUrl" :thumbUrl))
            img-id (str (g ref "id" :id))]
        (if (str/blank? url)
          (recur (rest refs) seen out)
          (let [content (try (*llm-vision* vision-system url) (catch #?(:clj Exception :default :default) _ nil))
                parsed (extract-json-block content)
                [seen out]
                (reduce (fn [[seen out] d]
                          (let [conf (as-double (get d "confidence"))
                                label (str/trim (str (get d "label" "")))]
                            (if (and (>= conf min-confidence) (seq label) (not (contains? seen label)))
                              [(conj seen label)
                               (conj out {:label label :confidence conf
                                          :category (str (get d "category" "building")) :imageRef img-id})]
                              [seen out])))
                        [seen out]
                        (get parsed "detections" []))]
            (recur (rest refs) seen out)))))))

;; ── linkActor (LLM disambiguation) ───────────────────────────────────────────

(def link-system
  (str "You are an entity disambiguation assistant. Map each detected entity "
       "label to the best matching actor DID. Output ONLY valid JSON. "
       "Schema: {\"links\":[{\"label\":\"..\",\"actorDid\":\"did:web:..\",\"confidence\":0.0-1.0}]}"))

(defn link-actor
  "Resolve detection labels to actor DIDs via the injected *llm-json*. Keeps
  links with a DID and confidence ≥ :min-confidence. registry-rows is optional
  context. Pure given *llm-json*."
  [{:keys [tile-h3 detections min-confidence registry-rows] :or {min-confidence 0.7}}]
  (if (empty? detections)
    {:ok true :links []}
    (let [labels (vec (keep #(let [l (str (g % "label" :label))] (when (seq l) l)) detections))
          user (str "Tile: " tile-h3 "\nEntities: " (ingest/json-encode labels)
                    "\nRegistry: " (ingest/json-encode (vec (take 50 (or registry-rows []))))
                    "\nReturn high-confidence links only (>= " min-confidence ").")
          result (try (*llm-json* link-system user) (catch #?(:clj Exception :default :default) ex {::error (str ex)}))]
      (if (::error result)
        {:ok true :links [] :warning (::error result)}
        (let [raw (or (get-in result ["data" "links"]) (get result "links") [])
              links (->> raw
                         (filter #(and (>= (as-double (get % "confidence")) min-confidence)
                                       (seq (str (get % "actorDid")))))
                         (mapv (fn [lk] {:label (str (get lk "label" ""))
                                         :actorDid (str (get lk "actorDid" ""))
                                         :confidence (as-double (get lk "confidence"))
                                         :source (str (get lk "source" "llm-disambiguate"))})))]
          {:ok true :links links})))))

;; ── adapter map for the BPMN engine ──────────────────────────────────────────

(defn curated-refs
  "The curated image subset for vision: the fetched :candidates whose :id is in
  curate's :selectedIds (so vision annotates only the chosen images, with their
  thumbUrls). Falls back to all candidates when no selection is present."
  [vars]
  (let [cands (or (:candidates vars) [])
        sel (set (map str (or (:selectedIds vars) (:selected-ids vars))))]
    (if (seq sel)
      (vec (filter #(contains? sel (str (g % "id" :id))) cands))
      (vec cands))))

(defn live-adapters
  "Wire the clj net/LLM tasks into the BPMN handler shape (task-type → fn[vars]).
  This is the GLUE layer: it translates the cumulatively-merged var keys (which
  use the py-compatible :candidates/:selectedIds/:detections names emitted by
  fetch + curate) into each pure task's argument shape, and threads cfg
  (tokens/urls/registry). The returned maps carry the keys the gateways read
  (:candidates, :recon-ok, :detections, :links)."
  [cfg]
  {"maps3d.fetchMapillary"
   (fn [vars] (fetch-mapillary (merge cfg vars)))            ; reads :tile-h3 :token :bbox

   "maps3d.colmapTile"
   (fn [vars] (colmap-tile (merge cfg {:tile-h3 (:tile-h3 vars)
                                       :selected-ids (or (:selected-ids vars) (:selectedIds vars))
                                       :image-urls (or (:image-urls vars) (:candidates vars))})))

   "maps3d.simplifyAndExport"
   (fn [vars] (simplify-and-export (merge cfg {:tile-h3 (:tile-h3 vars)
                                               :raw-mesh-uri (or (:raw-mesh-uri vars) (:rawMeshUri vars))})))

   "maps3d.visionAnnotate"
   (fn [vars] (vision-annotate (merge cfg {:image-refs (or (:image-refs vars) (curated-refs vars))})))

   "maps3d.linkActor"
   (fn [vars] (link-actor (merge cfg {:tile-h3 (:tile-h3 vars) :detections (:detections vars)})))})
