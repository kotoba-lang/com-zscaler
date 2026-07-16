(ns himawari.cells.outbound-logistics.state-machine
  "1:1 port of cells/outbound_logistics/cell.py — 輸送 handoff to autonomous transport
  (ADR-2606021200).

  5-node super-step pipeline: init → bind-carrier → customs-clear → plan-route → emit-manifest.
  COMPOSES kami-autodrive GNC (ADR-2606010600) — does NOT re-implement guidance/navigation/control.
  Wires the EXISTING open-customs-clearance BPMN for cross-border legs.

  Gates enforced:
  G13  no weaponization · encrypted telemetry · own-module → hikari sites only
       (no external commercial logistics carriage, N10)."
  (:require [clojure.string :as str]))

;; G13 / N1: himawari modules are produced for INTERNAL hikari install only.
(def ^:private ALLOWED_CONSIGNEE_PREFIX "did:web:etzhayyim.com:hikari")

;; Customs engine lexicon namespace (verified to exist on disk).
(def ^:private CUSTOMS_ENGINE "com.etzhayyim.etzhayyim.apps.customsClearance")
(def ^:private CUSTOMS_BPMN "00-contracts/bpmn/com/etzhayyim/open-customs-clearance")

;; kami-autodrive vehicle classes (mirror of the lexicon knownValues +
;; the Rust enum VehicleClass { Car, Ship, Drone, Aircraft }).
;; himawari composes kami-autodrive; it does not define new vehicle classes.
(def ^:private VEHICLE_CLASSES #{"car" "ship" "drone" "aircraft"})

;; ── #robotSignature normalization ──

(defn- robot-signatures
  "Normalize carried robot-witness provenance into #robotSignature objects.
  The outboundManifest lexicon requires attestingRobots as an array of #robotSignature
  (required: robotDid, signature; optional: role, timestamp), minItems 1."
  [raw]
  (let [sigs (mapv (fn [r]
                     (if (map? r)
                       (let [did (str (or (get r "robotDid") (get r "did") ""))
                             sig (str (get r "signature" ""))]
                         (cond-> {"robotDid" did "signature" sig}
                           (get r "role")      (assoc "role" (str (get r "role")))
                           (get r "timestamp") (assoc "timestamp" (str (get r "timestamp")))))
                       ;; bare DID string → minimal #robotSignature (signature sealed off-cell)
                       {"robotDid" (str r) "signature" "" "role" "gnc-handoff"}))
                   (or raw []))]
    ;; minItems 1: record the dispatching GNC handoff witness placeholder so the array is never empty.
    (if (seq sigs)
      sigs
      [{"robotDid" "did:web:etzhayyim.com:himawari:gnc-dispatch"
        "signature" ""
        "role" "gnc-handoff"}])))

;; ── Super-step nodes ──

(defn- transition-init
  "INIT: load the loadingRecord handed off by panel_loading."
  [state]
  (let [loading (or (get state "loadingRecord") {})]
    (assoc state
           "outbound_state"
           {"phase"            "init"
            "manifestId"       (str (get state "manifestId" "unknown"))
            "recordedAt"       (str (get state "recordedAt" ""))
            "loadingId"        (str (or (get loading "loadingId") (get state "loadingId") ""))
            "loadingRecordCid" (or (get loading "recordCid") (get state "loadingRecordCid"))
            "moduleSerials"    (vec (or (get loading "moduleSerials") (get state "moduleSerials") []))
            "consigneeDid"     (str (get state "consigneeDid" ""))
            "attestingRobots"  (vec (or (get state "attestingRobots") []))
            "completionPct"    0}
           "next_node" "bind_carrier")))

(defn- transition-bind-carrier
  "INIT → CARRIER_BOUND: compose kami-autodrive GNC + enforce G13."
  [state]
  (let [os       (get state "outbound_state")
        requested (str/lower-case (str/trim (str (get state "carrierClass" ""))))
        mode      (str/lower-case (str (get state "transportMode" "road")))
        requested (if (str/blank? requested)
                    (if (#{"marine" "sea" "ocean"} mode) "ship" "car")
                    requested)]
    ;; Validate vehicle class against the kami-autodrive VehicleClass enum.
    (when-not (contains? VEHICLE_CLASSES requested)
      (throw (ex-info (str "himawari outbound_logistics: carrier class " (pr-str requested)
                           " is not a kami-autodrive VehicleClass "
                           (pr-str (sort VEHICLE_CLASSES))
                           " (ADR-2606010600). himawari composes kami-autodrive; "
                           "it does not define new vehicle classes.")
                      {:type ::invalid-carrier-class :class requested})))
    ;; G13: own-module → hikari sites only. Reject any non-hikari consignee.
    (let [consignee (str (get os "consigneeDid" ""))]
      (when-not (str/starts-with? consignee ALLOWED_CONSIGNEE_PREFIX)
        (throw (ex-info (str "himawari outbound_logistics G13 violation: consignee "
                             (pr-str consignee) " is not a hikari install site ("
                             ALLOWED_CONSIGNEE_PREFIX "*). himawari modules ship to internal "
                             "hikari install only (SBT↔SBT carve-out, ADR-2605192115 §3); no "
                             "external commercial logistics carriage (N10).")
                        {:type ::g13-violation :consignee consignee})))
      (assoc state
             "outbound_state" (assoc os
                                     "phase"                "carrier_bound"
                                     "carrierClass"         requested
                                     "transportMode"        mode
                                     "telemetryEncrypted"   true
                                     "weaponizationPayload" false
                                     "completionPct"        25)
             "next_node" "customs_clear"))))

(defn- transition-customs-clear
  "CARRIER_BOUND → CUSTOMS_CLEARED: drive the EXISTING customs BPMN.
  For cross-border legs, build the lodgeDeclaration input + releaseShipment handle.
  Domestic legs skip customs but record the decision explicitly."
  [state]
  (let [os          (get state "outbound_state")
        cross-border (boolean (get state "crossBorder" false))
        customs
        (if cross-border
          (let [hs-code         (str (get state "hsCode" "854143"))
                declared-value  (long (Math/round (double (or (get state "declaredValueUsd") 0.0))))
                lodged-at       (str (or (get state "lodgedAt") (get os "recordedAt") (get os "manifestId") ""))
                manifest-id     (get os "manifestId")]
            {"lodgeDeclaration"
             {"declarationId"          (str manifest-id ":decl")
              "manifestVid"            manifest-id
              "hsCode"                 hs-code
              "declaredValueUsd"       declared-value
              "importerLei"            (get state "importerLei")
              "sanctionsScreeningVid"  (get state "sanctionsScreeningVid")
              "lodgedAt"               lodged-at}
             "releaseShipmentRef" (str manifest-id ":release")
             "bpmn"               CUSTOMS_BPMN
             "engine"             CUSTOMS_ENGINE})
          {"required" false "reason" "domestic leg — no customs declaration"})]
    (assoc state
           "outbound_state" (assoc os "phase" "customs_cleared" "customs" customs "completionPct" 55)
           "next_node" "plan_route")))

(defn- transition-plan-route
  "CUSTOMS_CLEARED → ROUTE_PLANNED: emit the kami-autodrive route request."
  [state]
  (let [os     (get state "outbound_state")
        origin (str (get state "originSite" "did:web:etzhayyim.com:himawari"))
        route  {"gnc"              "kami-autodrive"
                "vehicleClass"     (get os "carrierClass")
                "origin"           origin
                "destination"      (get os "consigneeDid")
                "waypoints"        (vec (get state "waypoints" []))
                "telemetryChannel" "com.etzhayyim.encrypted.telemetry"}]
    (assoc state
           "outbound_state" (assoc os
                                   "phase"        "route_planned"
                                   "routeRequest" route
                                   "originSite"   origin
                                   "completionPct" 80)
           "next_node" "emit_manifest")))

(defn- transition-emit-manifest
  "ROUTE_PLANNED → COMPLETE: build the outboundManifest record."
  [state]
  (let [os      (get state "outbound_state")
        record  {"$type"              "com.etzhayyim.himawari.outboundManifest"
                 "manifestId"         (get os "manifestId")
                 "recordedAt"         (str (get os "recordedAt" ""))
                 "loadingId"          (str (get os "loadingId" ""))
                 "loadingRecordCid"   (get os "loadingRecordCid")
                 "moduleSerials"      (vec (get os "moduleSerials" []))
                 "consigneeDid"       (get os "consigneeDid")
                 "originSite"         (str (or (get os "originSite") (get state "originSite") "did:web:etzhayyim.com:himawari"))
                 "carrierClass"       (get os "carrierClass")
                 "transportMode"      (get os "transportMode")
                 "routeRequest"       (get os "routeRequest")
                 "customs"            (get os "customs")
                 "telemetryEncrypted" (get os "telemetryEncrypted")
                 "telemetryChannel"   (get-in os ["routeRequest" "telemetryChannel"]
                                              "com.etzhayyim.encrypted.telemetry")
                 "weaponizationPayload" (get os "weaponizationPayload")
                 "attestingRobots"    (robot-signatures (get os "attestingRobots"))
                 "destinationKind"    "hikari-install-site"
                 "adr"                "ADR-2606021200"}]
    (assoc state
           "outbound_state"  (assoc os "phase" "complete" "completionPct" 100 "outboundManifest" record)
           "outboundManifest" record
           "next_node"        "end")))

(defn solve
  "Execute the 5-node outbound-logistics super-step pipeline.

  Input state keys:
    manifestId, loadingRecord (from panel_loading), consigneeDid, recordedAt,
    carrierClass (optional, default car/ship by transportMode), transportMode,
    crossBorder (bool), hsCode (optional), declaredValueUsd (optional),
    lodgedAt (optional), waypoints (optional), attestingRobots (optional).

  Returns state augmented with outboundManifest.
  Throws on G13 violation or invalid carrier class."
  [state]
  (-> state
      transition-init
      transition-bind-carrier
      transition-customs-clear
      transition-plan-route
      transition-emit-manifest))
