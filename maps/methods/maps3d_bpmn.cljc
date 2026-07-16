(ns maps.methods.maps3d-bpmn
  "maps3d processTile — BPMN-as-actor executed on kotoba + Datomic (NOT Zeebe).

  WHY: the legacy maps3d pipeline ran on a Zeebe BPMN engine with Python worker
  tasks (`processTile.bpmn` + the kotoba-kotodama `maps3d.*` Zeebe handlers).
  Per the migration directive the Zeebe worker is DEPRECATED; the workflow is now
  driven over the kotoba Datom log / Datomic substrate. This module is the
  Clojure execution engine for that — a small, pure BPMN-2.0 interpreter whose
  process-instance state lives as datoms in a kotoba store (the offline
  reference store `maps.methods.kotoba-local`, or the live kotoba node).

  Faithful to `00-contracts/bpmn/com/etzhayyim/maps3d/processTile.bpmn`:
    start → fetch → curate → ⟨enough images?⟩
      → colmap → ⟨reconstruct ok?⟩
          → simplify → vision → link → markDone → done
          → replan → ⟨replan action?⟩ → {retry→colmap | requestMore→fetch
                                          | downgradeOsm→osm-only | abort→failed}
      ↘ (curator abort) → failed

  House style mirrors the sibling ports: pure data + interpreter (portable,
  unit-tested); the live-node push behind #?(:clj ...). Task execution is
  injected as a handler map (task-type → fn(vars) → output-map) so the real
  maps3d clj/py task implementations — or test stubs — plug in unchanged."
  (:require [clojure.string :as str]
            [maps.methods.kotoba-local :as kl]
            [maps.methods.maps3d-tasks :as tasks]
            #?(:clj [maps.methods.ingest :as ingest])))

;; ── process definition (data, faithful to processTile.bpmn) ──────────────────

(def process-tile-def
  "node-id → node. :task carries the Zeebe-equivalent :task-type the handler map
  is keyed on; :gateway carries ordered [condition-kw target] branches."
  {:start     {:type :start :next :fetch}
   :fetch     {:type :task :task-type "maps3d.fetchMapillary"     :next :curate}
   :curate    {:type :task :task-type "maps3d.curateImages"       :next :gw-enough}
   :gw-enough {:type :gateway :name "enough images?"
               :branches [[:abort? :end-fail]
                          [:else :colmap]]}
   :colmap    {:type :task :task-type "maps3d.colmapTile"         :next :gw-recon}
   :gw-recon  {:type :gateway :name "reconstruct ok?"
               :branches [[:recon-ok? :simplify]
                          [:else :replan]]}
   :replan    {:type :task :task-type "maps3d.replanReconstruction" :next :gw-replan}
   :gw-replan {:type :gateway :name "replan action?"
               :branches [[:action-retry?        :colmap]
                          [:action-request-more? :fetch]
                          [:action-downgrade?    :end-osm]
                          [:else                 :end-fail]]}
   :simplify  {:type :task :task-type "maps3d.simplifyAndExport"  :next :vision}
   :vision    {:type :task :task-type "maps3d.visionAnnotate"     :next :link}
   :link      {:type :task :task-type "maps3d.linkActor"          :next :mark-done}
   :mark-done {:type :task :task-type "generic.db.insert"         :next :end-ok}
   :end-ok    {:type :end :status :done}
   :end-osm   {:type :end :status :osm-only}
   :end-fail  {:type :end :status :failed}})

(def conditions
  "Exclusive-gateway predicates over the accumulated instance vars."
  {:abort?               (fn [v] (boolean (get v :abort)))
   :recon-ok?            (fn [v] (boolean (get v :recon-ok)))
   :action-retry?        (fn [v] (= (get v :action) "retry"))
   :action-request-more? (fn [v] (= (get v :action) "requestMore"))
   :action-downgrade?    (fn [v] (= (get v :action) "downgradeOsm"))
   :else                 (constantly true)})

;; ── pure interpreter ─────────────────────────────────────────────────────────

(defn start-instance
  "Create a process instance with a token at :start. run-id is supplied by the
  caller (no Date/random in the pure core)."
  [tile-h3 run-id]
  {:instance-id (str "maps3d-tile/" tile-h3 "/" run-id)
   :tile-h3 tile-h3
   :token :start
   ;; seed :tile-h3 into vars so task handlers (which receive only vars) can read
   ;; it — fetchMapillary etc. need the tile id.
   :vars {:tile-h3 tile-h3}
   :history [:start]
   :status :running})

(defn resolve-gateway
  "Pick the first branch whose condition holds for vars. Returns the target
  node-id (defaults to :end-fail if no branch matches — a dead-end guard)."
  [node vars]
  (or (some (fn [[cond-kw target]]
              (when-let [pred (get conditions cond-kw)]
                (when (pred vars) target)))
            (:branches node))
      :end-fail))

(defn step
  "Advance the instance one node. handlers = {task-type (fn [vars] => output-map)}.
  - :start          → move to :next
  - :gateway        → resolve branch on vars
  - :task           → run handler, merge its output into vars, move to :next
  - :end            → mark terminal status, no further movement
  Unknown/handler-less tasks advance with no var change (fail-open, like the
  Zeebe path's optional service tasks)."
  [inst handlers]
  (let [node-id (:token inst)
        node (get process-tile-def node-id)]
    (case (:type node)
      :end (assoc inst :status (or (:status node) :done))
      :start (let [nxt (:next node)]
               (-> inst (assoc :token nxt) (update :history conj nxt)))
      :gateway (let [nxt (resolve-gateway node (:vars inst))]
                 (-> inst (assoc :token nxt) (update :history conj nxt)))
      :task (let [h (get handlers (:task-type node))
                  out (if h (or (h (:vars inst)) {}) {})
                  nxt (:next node)]
              (-> inst
                  (update :vars merge out)
                  (assoc :token nxt)
                  (update :history conj nxt)))
      ;; unknown node type → terminate defensively
      (assoc inst :status :failed))))

(defn terminal?
  [inst]
  (or (not= :running (:status inst))
      (= :end (:type (get process-tile-def (:token inst))))))

(defn run
  "Drive the instance to a terminal node (or until max-steps — a loop backstop
  for the retry/requestMore cycles). Returns the final instance."
  ([inst handlers] (run inst handlers 100))
  ([inst handlers max-steps]
   (loop [i inst, n 0]
     (let [node (get process-tile-def (:token i))]
       (cond
         (= :end (:type node)) (step i handlers)        ; settle terminal status
         (>= n max-steps) (assoc i :status :step-limit)
         :else (recur (step i handlers) (inc n)))))))

;; ── Datomic persistence (kotoba Datom log) ───────────────────────────────────

(defn instance->batch
  "Process-instance state → a kg.ingest_batch body: one :bpmn.instance/* entity
  whose claims carry the token, status, tile, and the visited-node trace. This
  is the datoms the Datom-log read path queries (replacing Zeebe's process DB)."
  [inst]
  {"entities"
   [{"id" (:instance-id inst)
     "type" "bpmn-instance"
     "label_en" (str "maps3d processTile " (:tile-h3 inst))
     "claims" (into [{"pred" "bpmn.instance/process" "value" "maps3d.processTile"}
                     {"pred" "bpmn.instance/tile-h3" "value" (:tile-h3 inst)}
                     {"pred" "bpmn.instance/token" "value" (name (:token inst))}
                     {"pred" "bpmn.instance/status" "value" (name (:status inst))}]
                    (for [n (:history inst)]
                      {"pred" "bpmn.instance/visited" "value" (name n)}))
     "relations" []}]})

(defn persist-instance!
  "Land the instance state in a kotoba Datom store (offline reference store by
  default). Returns the count of entities ingested. State is then queryable via
  kotoba-local/entity + AVET (e.g. all instances at a given token)."
  [store inst]
  (kl/ingest-batch store (instance->batch inst)))

;; ── ② durable, append-only execution on the Datom log ────────────────────────
;; The kotoba Datom log is append-only canonical state (ADR-2605312345): drive!
;; appends one :bpmn.event/* datom per transition rather than mutating a snapshot,
;; so the run is tamper-evident and resumable — replay folds the events back into
;; the current instance. This is the same code path a live kotoba node uses (the
;; store is just a different BlockStore behind kotoba-local's API).

(defn transition->batch
  "One step transition → an append-only :bpmn.event/* datom batch."
  [instance-id seq to-node status]
  {"entities"
   [{"id" (str instance-id "/evt/" seq)
     "type" "bpmn-event"
     "label_en" (str instance-id " #" seq " → " (name to-node))
     "claims" [{"pred" "bpmn.event/instance" "value" instance-id}
               {"pred" "bpmn.event/seq" "value" (str seq)}
               {"pred" "bpmn.event/node" "value" (name to-node)}
               {"pred" "bpmn.event/status" "value" (name status)}]
     "relations" []}]})

(defn drive*
  "Execute the instance to a terminal node, calling (sink! batch) with a
  :bpmn.event datom batch after EVERY transition (durable + resumable). The sink
  abstracts the store: offline = kotoba-local ingest, live = ingest/push-batch to
  a running kotoba node. Returns the final instance. max-steps backstops the
  retry/requestMore loop."
  [sink! inst handlers max-steps]
  (letfn [(end-node? [tok] (= :end (:type (get process-tile-def tok))))
          (append! [inst seq] (sink! (transition->batch (:instance-id inst) seq (:token inst) (:status inst))))]
    (loop [i inst, seq 0]
      (cond
        ;; started on (or already at) a terminal node — settle + record once
        (end-node? (:token i))
        (let [done (step i handlers)] (append! done seq) done)

        ;; (inc seq): the prior transition already recorded this node at `seq`;
        ;; append a distinct terminal marker rather than colliding on its id.
        (>= seq max-steps)
        (let [stopped (assoc i :status :step-limit)] (append! stopped (inc seq)) stopped)

        :else
        (let [moved (step i handlers)
              seq' (inc seq)]
          (if (end-node? (:token moved))
            ;; landed on a terminal node — settle its status in the same append
            (let [done (step moved handlers)] (append! done seq') done)
            (do (append! moved seq') (recur moved seq'))))))))

(defn drive!
  "Durable execution against an offline kotoba-local Datom store (the test/dev
  substrate). See drive* for the sink-based core."
  ([store inst handlers] (drive! store inst handlers 100))
  ([store inst handlers max-steps]
   (drive* #(kl/ingest-batch store %) inst handlers max-steps)))

#?(:clj
   (defn drive-live!
     "Durable execution against a LIVE kotoba node: every transition's
     :bpmn.event datoms are pushed via ingest/push-batch, behind the maps G7
     operator gate (no-server-key). Returns the final instance. Throws on a
     gate/credential refusal before any push."
     ([inst handlers] (drive-live! inst handlers 100))
     ([inst handlers max-steps]
      (when (not= (ingest/*getenv* "MAPS_OPERATOR_GATE") "1")
        (throw (ex-info "maps G7: live Datom-log drive is operator-gated (MAPS_OPERATOR_GATE=1)." {:exit 1})))
      (let [auth (ingest/*getenv* "KOTOBA_AUTH")
            endpoint (ingest/*getenv* "KOTOBA_ENDPOINT")]
        (when-not (and auth endpoint (seq auth) (seq endpoint))
          (throw (ex-info "maps G4/G7: live drive needs KOTOBA_AUTH + KOTOBA_ENDPOINT (no-server-key)." {:exit 1})))
        (drive* (fn [batch] (ingest/push-batch batch auth endpoint)) inst handlers max-steps)))))

(defn replay
  "Fold the append-only :bpmn.event datoms for an instance back into its current
  state {:instance-id :token :status :history}. Resume / audit path — the Datom
  log is the source of truth, not an in-memory object."
  [store instance-id]
  (let [evts (kl/avet-query store "bpmn.event/instance" [instance-id])
        claim (fn [e p] (some #(when (= p (get % "pred")) (get % "value")) (get e "claims")))
        ordered (sort-by #(let [s (claim % "bpmn.event/seq")]
                            #?(:clj (try (Long/parseLong s) (catch Exception _ 0))
                               :default (or (parse-long s) 0)))
                         evts)
        nodes (mapv #(keyword (claim % "bpmn.event/node")) ordered)
        last-evt (last ordered)]
    (when last-evt
      {:instance-id instance-id
       :token (keyword (claim last-evt "bpmn.event/node"))
       :status (keyword (claim last-evt "bpmn.event/status"))
       :history nodes})))

;; ── ③ Datomic-native entry: claim a pending tile and start an instance ───────

(defn claim-and-start
  "Datomic replacement for the BPMN start→'select pending tile'→'tile?' gateway:
  probe the Datom store for the next pending tile and start an instance for it.
  Returns the instance, or nil when no tile is pending (the gateway's no-tile
  exit). run-id is caller-supplied (pure core)."
  [store run-id]
  (let [sel (tasks/select-pending-tile store)]
    (when (:has-tile sel)
      (start-instance (:tile-h3 sel) run-id))))

#?(:clj
   (defn push-instance!
     "Push the instance datoms to the LIVE kotoba node via ingest/push-batch,
     behind the maps G7 operator gate (no-server-key). Returns [status body] or
     throws on a gate/credential refusal."
     [inst]
     (let [batch (instance->batch inst)]
       (when (not= (ingest/*getenv* "MAPS_OPERATOR_GATE") "1")
         (throw (ex-info (str "maps G7: live Datom-log push is Council+operator gated. "
                              "Set MAPS_OPERATOR_GATE=1 with attestation to enable.")
                         {:exit 1})))
       (let [auth (ingest/*getenv* "KOTOBA_AUTH")
             endpoint (ingest/*getenv* "KOTOBA_ENDPOINT")]
         (when-not (and auth endpoint (seq auth) (seq endpoint))
           (throw (ex-info "maps G4/G7: push needs KOTOBA_AUTH + KOTOBA_ENDPOINT (no-server-key)."
                           {:exit 1})))
         (ingest/push-batch batch auth endpoint)))))
