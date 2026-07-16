(ns himawari.cells.panel-loading.state-machine
  "1:1 port of cells/panel_loading/cell.py — 積込 panel loading (ADR-2606021200).

  Palletize finished PV modules + load onto a carrier. Composes sarutahiko F10
  LoaderRobot (ADR-2606013100); does NOT re-implement loader physics.

  Gates enforced:
  G7  labor-liberation transparency — every human task removed by automation logged
      to the Liberation Metric.
  G12 no external commercial PV sale — modules load for internal hikari install only."
  (:require [clojure.string :as str]))

;; sarutahiko F10 LoaderRobot cycle phases (mirror of the authoritative Rust enum).
(def ^:private LOAD_PHASES
  ["ToPick" "Carry" "Lower" "Done"])

(def ^:private LOAD_PHASE_DONE "Done")

;; F10 lineage DID.
(def ^:private F10_LOADER_DID "did:web:etzhayyim.com:sarutahiko#F10-loader")

(defn- pallet-count
  "Ceil-divide modules into pallets (the F10 straddle loader moves one pallet per cycle)."
  [module-count capacity]
  (if (<= module-count 0)
    0
    (quot (+ module-count capacity -1) capacity)))

(defn- liberation-cid
  "G7: content-address the displaced-manual-task manifest for the Liberation Metric."
  [loading-id removed-tasks]
  (let [payload (str loading-id "|" (str/join "+" (sort removed-tasks)))
        digest (bit-and (Math/abs (hash payload)) 0xFFFFFFFFFFFF)]
    (str "bafyhimawari" (format "%012x" digest))))

(defn- cid
  "Deterministic content-address placeholder (R0 stand-in for CIDv1)."
  [payload]
  (let [digest (bit-and (Math/abs (hash payload)) 0xFFFFFFFFFFFF)]
    (str "bafyhimawari" (format "%012x" digest))))

(defn- attesting-robots
  "Build the #robotSignature witness array over the completed loading cycle.
  The F10 LoaderRobot is always present as the >=1 mandatory witness."
  [supplied loader-robot-did loading-id loader-phase recorded-at]
  (let [loader-sig {"robotDid" loader-robot-did
                    "role" "straddle-loader"
                    "signature" (cid (str "sig:" loader-robot-did ":" loading-id ":" loader-phase))
                    "timestamp" recorded-at}
        others (filter (fn [item]
                         (let [did (if (map? item) (str (get item "robotDid" "")) (str item))]
                           (and (not (str/blank? did)) (not= did loader-robot-did))))
                       (or supplied []))]
    (into [loader-sig]
          (map (fn [item]
                 (if (map? item)
                   (cond-> {"robotDid" (str (get item "robotDid" ""))
                            "role" (str (get item "role" "witness"))
                            "signature" (str (get item "signature" (cid (str "sig:" (get item "robotDid") ":" loading-id))))
                            "timestamp" (str (get item "timestamp" recorded-at))}
                     (get item "role") (assoc "role" (str (get item "role"))))
                   {"robotDid" (str item)
                    "role" "witness"
                    "signature" (cid (str "sig:" (str item) ":" loading-id))
                    "timestamp" recorded-at}))
               others))))

(defn solve
  "Compose the F10 LoaderRobot cycle result + emit a com.etzhayyim.himawari.loadingRecord.

  Input state keys:
    loadingId, moduleSerials, carrierDid, carrierInternal, loaderPhase,
    loaderRobotDid (opt, default F10), palletCapacity (opt, default 36),
    humanTasksRemoved, recordedAt, attestingRobots

  Returns state augmented with loadingRecord."
  [state]
  (let [loading-id (str/trim (str (get state "loadingId" "")))
        _ (when (str/blank? loading-id)
            (throw (ex-info "panel_loading: loadingId is required"
                            {:type ::invalid-input})))

        module-serials (vec (filter #(str/trim (str %))
                                    (get state "moduleSerials" [])))
        _ (when (empty? module-serials)
            (throw (ex-info "panel_loading: moduleSerials must be non-empty"
                            {:type ::invalid-input})))

        carrier-did (str/trim (str (get state "carrierDid" "")))
        _ (when (str/blank? carrier-did)
            (throw (ex-info "panel_loading: carrierDid is required"
                            {:type ::invalid-input})))

        ;; G12 — modules load for internal hikari install only
        carrier-internal (boolean (get state "carrierInternal" false))
        _ (when-not carrier-internal
            (throw (ex-info "panel_loading G12 violation: external carrier refused"
                            {:type ::g12-violation})))

        loader-phase (str (get state "loaderPhase" LOAD_PHASE_DONE))
        _ (when-not (some #(= % loader-phase) LOAD_PHASES)
            (throw (ex-info (str "panel_loading: loaderPhase " loader-phase " not a LoaderRobot phase")
                            {:type ::invalid-input})))

        cycle-complete (= loader-phase LOAD_PHASE_DONE)
        loader-robot-did (str (or (get state "loaderRobotDid") F10_LOADER_DID))

        ;; Palletize: split modules into pallets
        pallet-capacity (max 1 (int (get state "palletCapacity" 36)))
        pallet-count (pallet-count (count module-serials) pallet-capacity)

        ;; G7 — labor-liberation transparency
        human-tasks-removed (vec (filter #(str/trim (str %))
                                          (get state "humanTasksRemoved" [])))
        human-tasks-removed-cid (liberation-cid loading-id human-tasks-removed)

        ;; Cycle state log CID (deterministic placeholder)
        cycle-state-log-cid (cid (str "cycle:" loading-id ":" loader-phase))

        recorded-at (str (get state "recordedAt" ""))

        ;; Attesting robots
        attesting-robots-list (attesting-robots (get state "attestingRobots")
                                                loader-robot-did
                                                loading-id
                                                loader-phase
                                                recorded-at)

        record {"$type" "com.etzhayyim.himawari.loadingRecord"
                "loadingId" loading-id
                "recordedAt" recorded-at
                "moduleSerials" module-serials
                "palletCount" pallet-count
                "carrierDid" carrier-did
                "carrierInternal" carrier-internal
                "loaderRobotDid" loader-robot-did
                "loaderPhase" loader-phase
                "humanTasksRemovedCid" human-tasks-removed-cid
                "cycleStateLogCid" cycle-state-log-cid
                "attestingRobots" attesting-robots-list}]
    (merge state
           {"loadingRecord" record
            "cycleComplete" cycle-complete
            "palletCount" pallet-count
            "loaderRobotDid" loader-robot-did
            "humanTasksRemovedCid" human-tasks-removed-cid
            "refused" false})))
