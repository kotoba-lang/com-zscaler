(ns maps.methods.maps3d-persist
  "maps3d photogrammetry pipeline → kotoba Datom-log persistence (Clojure).

  Clojure counterpart of the Python primitives' landing step (the
  `_persist_vision_results` / `_persist_actor_links` helpers in
  `kotoba-kotodama/.../primitives/maps3d.py`). processTile.bpmn's 'mark tile
  done' step documents that the worker tasks 'have already INSERT-ed into
  vertex_vision_result / edge_*'; these helpers make that contract true on the
  maps actor's kotoba-native write path.

  Per ADR-2606064500 the maps actor is kotoba-native: the canonical write is a
  `kg.ingest_batch` body (entities with type/claims/relations) pushed to the
  kotoba Datom log — the same surface `ingest.cljc` uses, NOT a RisingWave
  insert. Pure builders (vision-result-entity / actor-link-entity /
  vision-results->batch / actor-links->batch / stable-rkey) are portable and
  unit-tested; the gated network push (persist-vision! / persist-links!) is
  host-only behind #?(:clj ...) and reuses ingest/push-batch + the maps G7
  operator gate."
  (:require [clojure.string :as str]
            [maps.methods.ingest :as ingest]))

(def repo "did:web:maps.etzhayyim.com")
(def collection-vision "com.etzhayyim.apps.maps3d.visionResult")
(def collection-actor-link "com.etzhayyim.apps.maps3d.actorLink")

(defn- num? [v] (and (number? v) (not (boolean? v))))

(defn- as-double [v] (if (num? v) (double v) 0.0))

(defn stable-rkey
  "Deterministic 16-hex rkey so re-running a tile upserts instead of duplicating
  (parity with the Python sha256(...)[:16])."
  [& parts]
  (let [s (str/join "" (map str parts))]
    #?(:clj
       (let [md (java.security.MessageDigest/getInstance "SHA-256")
             bs (.digest md (.getBytes s "UTF-8"))]
         (subs (apply str (map #(format "%02x" (bit-and % 0xff)) bs)) 0 16))
       :default
       ;; portable fallback (cljs / sci): stable but not sha256 — JVM/bb is the
       ;; real runtime, this only keeps non-JVM compilation honest.
       (let [h (Math/abs (hash s))]
         (subs (str h "0000000000000000") 0 16)))))

(defn- vid [collection rkey]
  (str "at://" repo "/" collection "/" rkey))

(defn- claim [pred value] {"pred" pred "value" (str value)})

(defn vision-result-entity
  "One Murakumo-Vision detection → a kg.ingest_batch entity, or nil for a blank
  label. Idempotent id keyed on (tile-h3, label, image-ref)."
  [tile-h3 det]
  (let [label (str/trim (str (get det "label" (get det :label))))
        image-ref (str (or (get det "imageRef" (get det :imageRef)) ""))]
    (when (seq label)
      (let [conf (as-double (get det "confidence" (get det :confidence)))
            category (str (or (get det "category" (get det :category)) "building"))
            rkey (stable-rkey tile-h3 label image-ref)
            claims (cond-> [(claim "maps3d.vision/tile-h3" tile-h3)
                            (claim "maps3d.vision/label" label)
                            (claim "maps3d.vision/confidence" conf)
                            (claim "maps3d.vision/category" category)
                            (claim "maps3d.vision/source" "murakumo-vision")]
                     (seq image-ref) (conj (claim "maps3d.vision/image-ref" image-ref)))]
        {"id" (vid collection-vision rkey)
         "type" "maps3d-vision-result"
         "label_en" label
         "claims" claims
         "relations" []}))))

(defn actor-link-entity
  "One resolved actor link → a kg.ingest_batch entity (with a links-to relation
  to the actor DID), or nil when no actorDid. Idempotent id keyed on
  (tile-h3, label, actor-did)."
  [tile-h3 lk]
  (let [actor-did (str (or (get lk "actorDid" (get lk :actorDid)) ""))]
    (when (seq actor-did)
      (let [label (str (or (get lk "label" (get lk :label))
                           (get lk "detectionId" (get lk :detectionId)) ""))
            conf (as-double (get lk "confidence" (get lk :confidence)))
            source (str (or (get lk "source" (get lk :source)) "llm-disambiguate"))
            rkey (stable-rkey tile-h3 label actor-did)]
        {"id" (vid collection-actor-link rkey)
         "type" "maps3d-actor-link"
         "label_en" label
         "claims" [(claim "maps3d.link/tile-h3" tile-h3)
                   (claim "maps3d.link/label" label)
                   (claim "maps3d.link/actor-did" actor-did)
                   (claim "maps3d.link/confidence" conf)
                   (claim "maps3d.link/source" source)]
         "relations" [{"pred" "maps3d/links-to" "target" actor-did}]}))))

(defn vision-results->batch
  "Vision detections → a kg.ingest_batch body (blank labels dropped)."
  [tile-h3 detections]
  {"entities" (vec (keep #(vision-result-entity tile-h3 %) detections))})

(defn actor-links->batch
  "Resolved actor links → a kg.ingest_batch body (DID-less links dropped)."
  [tile-h3 links]
  {"entities" (vec (keep #(actor-link-entity tile-h3 %) links))})

#?(:clj
   (defn- push-gated!
     "Push a kg.ingest_batch body via ingest/push-batch behind the maps G7
     operator gate (mirrors ingest/main's --push refusal paths). Returns
     [status body] on success, or {:skipped :empty} when the batch has no
     entities. Throws ex-info on a gate/credential refusal."
     [batch]
     (if (empty? (get batch "entities"))
       {:skipped :empty}
       (do
         (when (not= (ingest/*getenv* "MAPS_OPERATOR_GATE") "1")
           (throw (ex-info (str "maps G7: live kg.ingest_batch push is Council+operator gated. "
                                "Set MAPS_OPERATOR_GATE=1 with attestation to enable.")
                           {:exit 1})))
         (let [auth (ingest/*getenv* "KOTOBA_AUTH")
               endpoint (ingest/*getenv* "KOTOBA_ENDPOINT")]
           (when-not (and auth endpoint (seq auth) (seq endpoint))
             (throw (ex-info (str "maps G4/G7: push needs KOTOBA_AUTH (member/operator DID bearer) + "
                                  "KOTOBA_ENDPOINT. The maps Worker holds no server key (no-server-key).")
                             {:exit 1})))
           (ingest/push-batch batch auth endpoint))))))

#?(:clj
   (defn persist-vision!
     "Land vision detections for a tile in the kotoba Datom log (operator-gated)."
     [tile-h3 detections]
     (push-gated! (vision-results->batch tile-h3 detections))))

#?(:clj
   (defn persist-links!
     "Land resolved actor links for a tile in the kotoba Datom log (operator-gated)."
     [tile-h3 links]
     (push-gated! (actor-links->batch tile-h3 links))))
