(ns maps.methods.maps3d-tasks
  "maps3d task handlers for the kotoba/Datomic BPMN engine (maps.methods.maps3d-bpmn).

  These replace the Zeebe worker tasks with native Clojure handlers. The PURE
  decision tasks — image curation down-select + reconstruction replanning — are
  ported here verbatim from the Python primitives (LLM-free fallback paths), so
  the BPMN gateways are driven by real logic over the Datom log, not stubs.
  The NETWORK/LLM tasks (fetchMapillary / colmapTile / simplifyAndExport /
  visionAnnotate / linkActor) stay injectable as `adapters` — the engine is
  substrate-pure, the I/O lives at the edge.

  `select-pending-tile` is the Datalog/AVET replacement for the BPMN's
  `generic.db.select` 'select pending tile' step, querying the kotoba Datom
  store directly (the Zeebe→Datomic migration of the entry step)."
  (:require [maps.methods.kotoba-local :as kl]))

;; ── ③ generic.db.select → Datalog/AVET pending-tile probe ────────────────────

(defn- claim-val [ent pred]
  (some #(when (= pred (get % "pred")) (get % "value")) (get ent "claims")))

(defn select-pending-tile
  "Pick the next tile to process from the Datom store: subjects carrying
  :maps3d.tile/status = \"pending\", ordered by ascending :maps3d.tile/priority
  then id (deterministic). Returns {:tile-h3 .. :has-tile true} or {:has-tile
  false} — the shape the BPMN 'tile?' gateway reads. Pure AVET probe; no I/O."
  [store]
  (let [hits (kl/avet-query store "maps3d.tile/status" ["pending"])
        ranked (sort-by (fn [e] [(or (claim-val e "maps3d.tile/priority") "9")
                                  (get e "id")])
                        hits)
        top (first ranked)]
    (if top
      {:has-tile true
       :tile-h3 (or (claim-val top "maps3d.tile/h3") (get top "id"))
       :tile-id (get top "id")}
      {:has-tile false})))

;; ── ① curateImages — LLM-free down-select (port of task_maps3d_curate_images) ─

(defn curate-images
  "Select images for COLMAP. Sort candidates by quality desc, take a top-60 pool;
  abort when the pool is below min-count, else take the top target-count. Mirrors
  the Python fallback path (the LLM ranking is an optional later adapter).
  Reads :candidates from vars; returns {:abort bool :selectedIds [...]}."
  [vars]
  (let [candidates (or (:candidates vars) [])
        target (or (:target-count vars) 30)
        min-count (or (:min-count vars) 8)]
    (if (empty? candidates)
      {:abort true :selectedIds []}
      (let [pool (->> candidates
                      (sort-by #(- (double (or (:qualityScore %) (get % "qualityScore") 0))))
                      (take 60)
                      vec)
            ids (mapv #(or (:id %) (get % "id")) pool)]
        (if (< (count pool) min-count)
          {:abort true :selectedIds ids}
          {:abort false :selectedIds (vec (take target ids))})))))

;; ── ① replanReconstruction — rule branches (port of task_maps3d_replan_*) ────

(defn replan
  "Decide recovery after a failed COLMAP reconstruction. Rule-only (the LLM is an
  optional adapter): attempt≥3 → downgradeOsm, <5 images → requestMore, else
  retry. Reads :error-code/:image-count/:attempt from vars; returns
  {:action .. :rationale ..}."
  [vars]
  (let [attempt (or (:attempt vars) 1)
        image-count (or (:image-count vars) 0)]
    (cond
      (>= attempt 3)
      {:action "downgradeOsm"
       :rationale (str "max attempts (" attempt ") reached, downgrading to OSM extrude")}
      (< image-count 5)
      {:action "requestMore"
       :rationale (str "only " image-count " images registered — need wider search area")}
      :else
      {:action "retry" :rationale "transient — retrying same images"})))

;; ── default handler map (pure tasks + injected network adapters) ─────────────

(defn default-handlers
  "Compose the pure clj tasks with caller-supplied `adapters` for the network/LLM
  steps. adapters = {task-type (fn [vars] => output)} for
  maps3d.fetchMapillary / colmapTile / simplifyAndExport / visionAnnotate /
  linkActor. Missing adapters simply no-op (fail-open, like optional Zeebe
  service tasks). The pure tasks always win for curate/replan."
  ([] (default-handlers {}))
  ([adapters]
   (merge {"maps3d.fetchMapillary" (fn [_] {})
           "maps3d.colmapTile" (fn [_] {:recon-ok false})
           "maps3d.simplifyAndExport" (fn [_] {})
           "maps3d.visionAnnotate" (fn [_] {})
           "maps3d.linkActor" (fn [_] {})}
          adapters
          {"maps3d.curateImages" (fn [vars] (curate-images vars))
           "maps3d.replanReconstruction" (fn [vars] (replan vars))
           "generic.db.insert" (fn [_] {})})))
