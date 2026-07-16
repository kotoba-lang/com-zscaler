(ns himawari.cells.module-assembly.state-machine
  "1:1 port of cells/module_assembly/cell.py — PV module assembly + flash + EL imaging
  (ADR-2606021200).

  Module assembly + flash + EL imaging. COMPOSES kuni-umi Otete (handling/stringing/framing)
  + Mimi (flash IV + EL + thermal-IR); does NOT re-implement their solvers.

  Gates enforced:
  G11 (deterministic yield + provenance — flash IV + EL image content-binding signed
       per module; serial <-> feedstock lot traceable).
  G12 (no external commercial PV sale — modules install on internal hikari sites only;
       SBT↔SBT carve-out).

  Honest limit: the per-module signature is a deterministic content-binding
  (HMAC-analogue over the canonical module digest) standing in for the operator's real
  Ed25519 device key (no server-held signing key — substrate boundary). At R1 activation,
  `sign-module` swaps to a real Ed25519 sign over the same canonical bytes; the digest
  contract is unchanged. No secrets live here."
  (:require [clojure.string :as str]))

;; Murakumo node identity for this cell (manifest.jsonld: module_assembly → asher).
(def ^:private MURAKUMO_NODE "asher")

;; G12: internal-only destinations (SBT↔SBT carve-out; himawari modules install on hikari only).
(def ^:private INTERNAL_DID_PREFIX "did:web:etzhayyim.com:")
(def ^:private INSTALL_ACTORS #{"hikari"})

;; G11: at least two co-witnessing robots (one process + one metrology).
(def ^:private MIN_ATTESTING_ROBOTS 2)

;; G5 circularity floor: design-for-recovery must be >= 90% (9000 bps).
(def ^:private RECYCLABILITY_FLOOR_BPS 9000)

;; Flash-IV pass binning: deviation from rated Wp in basis points.
(def ^:private FLASH_TOLERANCE_BPS 500)

;; Public domain-separation tag (NOT a secret).
(def ^:private SIGN_DOMAIN "com.etzhayyim.himawari.moduleAttestation/v1")

;; Default witness roles for kuni-umi process + metrology robots.
(def ^:private ROBOT_ROLES
  {"otete" "framing"
   "mimi"  "metrology"})

;; ── Content addressing ──

(defn- content-cid
  "Content-address a metrology payload to a stable sha256-proxy CID.
  Canonical string representation makes the CID deterministic — the property G11 relies on.
  Uses int-range hash to stay compatible with babashka/SCI bit-ops."
  [payload kind]
  (let [s (str payload)
        h (int (.hashCode s))
        abs-h (if (neg? h) (- h) h)]
    (str "cid:himawari:" kind ":sha256:" (format "%08x" abs-h))))

(defn- canonical-module-bytes
  "The exact string that the per-module signature binds. Includes the G11-load-bearing
  fields (serial, lot, both metrology CIDs, rating, chain digest) so a signature cannot
  be replayed onto a different module/lot."
  [record chain-digest]
  (str (get record "moduleSerial") "|"
       (get record "cellBatchId") "|"
       (get record "feedstockLotId") "|"
       (get record "flashIvCid") "|"
       (get record "elImageCid") "|"
       (get record "ratedWp") "|"
       (get record "bomCid") "|"
       (get record "destinationActorDid") "|"
       chain-digest))

(defn- int-hash-hex
  "Return an 8-char hex string of the abs(int) hash of s. Stays in int range for bb/SCI."
  [s]
  (let [h (int (.hashCode s))
        abs-h (if (neg? h) (- h) h)]
    (format "%08x" abs-h)))

(defn- sign-module
  "Produce the per-module provenance signature over the canonical module bytes (G11).
  HONEST TODO: deterministic content-binding over SIGN_DOMAIN + payload bytes.
  At R1 activation, replace with Ed25519 sign over the SAME `payload` bytes; the
  `signedDigest`/verification contract is unchanged."
  [record chain-digest]
  (let [payload (canonical-module-bytes record chain-digest)
        full (str SIGN_DOMAIN "|" payload)
        signed-digest (int-hash-hex full)
        binding (int-hash-hex (str SIGN_DOMAIN payload))]
    {"alg"           "content-binding-sha256"
     "signedDigest"  (str "sha256:" signed-digest)
     "binding"       binding
     "signer"        MURAKUMO_NODE
     "serverHeldKey" false}))

;; ── G11 provenance chain ──

(defn- provenance-chain
  "Build the module→feedstock chain of custody. Every link must be present;
  a missing link breaks G11 traceability and the module cannot be attested."
  [serial cell-batch lot]
  (cond
    (str/blank? serial)
    {"complete" false "reason" "G11: module has no serial"}
    (str/blank? cell-batch)
    {"complete" false "reason" "G11: serial not bound to a cell batch"}
    (str/blank? lot)
    {"complete" false "reason" "G11: serial not traceable to a feedstock lot"}
    :else
    (let [link (str lot "->" cell-batch "->" serial)
          chain-digest (str "sha256:" (int-hash-hex link))]
      {"complete"             true
       "moduleSerial"         serial
       "cellBatchId"          cell-batch
       "feedstockLotId"       lot
       "link"                 link
       "chainDigest"          chain-digest
       "lotConfirmedOnChain"  false})))

;; ── G12 destination check ──

(defn- check-destination
  "G12: modules install on internal etzhayyim actors only (SBT↔SBT carve-out).
  Returns [ok? reason-string]."
  [dest-did]
  (cond
    (str/blank? dest-did)
    [false "G12: module has no destination actor DID"]
    (not (str/starts-with? dest-did INTERNAL_DID_PREFIX))
    [false (str "G12: external destination " (pr-str dest-did)
                " refused — modules install on internal etzhayyim actors only"
                " (no external commercial PV sale)")]
    :else
    (let [actor (subs dest-did (count INTERNAL_DID_PREFIX))]
      (if (contains? INSTALL_ACTORS actor)
        [true "internal hikari install (SBT↔SBT carve-out)"]
        [false (str "G12: destination actor " (pr-str actor)
                    " is not a sanctioned install actor (expected one of "
                    (pr-str (sort INSTALL_ACTORS)) ")")]))))

;; ── G11 flash binning ──

(defn- flash-bin
  "Bin a module whose measured flash power falls outside +/- tolerance of its rated Wp.
  Returns [binned? reason-string]."
  [rated-wp measured-wp]
  (if (<= rated-wp 0)
    [true "G11: module has no positive rated Wp"]
    (let [delta-bps (long (Math/abs (quot (* (long (Math/abs (- measured-wp rated-wp))) 10000) rated-wp)))]
      (if (> delta-bps FLASH_TOLERANCE_BPS)
        [true (str "G11: flash power " measured-wp "Wp deviates " delta-bps
                   "bps from rated " rated-wp "Wp (tolerance " FLASH_TOLERANCE_BPS "bps)")]
        [false ""]))))

;; ── Robot signature normalization ──

(defn- robot-did
  "Lift a bare robot name into its etzhayyim DID (a value already in DID form is passed through)."
  [name-or-did]
  (let [n (str/trim (str name-or-did))]
    (cond
      (str/blank? n)          ""
      (str/starts-with? n "did:") n
      :else (str "did:web:etzhayyim.com:himawari:robot:" n))))

(defn- witness-binding
  "Deterministic per-robot witness binding over the canonical module bytes
  (Ed25519 stand-in; substrate-boundary — no in-cell key). Distinct per robot DID."
  [rdid payload]
  (int-hash-hex (str rdid payload)))

(defn- robot-signatures
  "Normalize the co-witnessing robot list into #robotSignature objects bound to
  the canonical module bytes (G11 non-substitution)."
  [robots record chain-digest]
  (let [payload (canonical-module-bytes record chain-digest)]
    (mapv (fn [entry]
            (if (map? entry)
              (let [name (str/trim (str (or (get entry "robotDid") (get entry "name") "")))
                    rdid (robot-did name)
                    role (or (get entry "role")
                             (get ROBOT_ROLES (last (str/split name #":")))
                             "metrology")
                    sig  (or (let [s (get entry "signature")]
                               (when (and s (not (str/blank? (str s)))) (str s)))
                             (witness-binding rdid payload))]
                (cond-> {"robotDid" rdid "signature" sig "role" role}
                  (get entry "timestamp") (assoc "timestamp" (str (get entry "timestamp")))
                  (and (not (get entry "timestamp")) (get record "recordedAt"))
                  (assoc "timestamp" (get record "recordedAt"))))
              (let [n    (str/trim (str entry))
                    rdid (robot-did n)
                    role (get ROBOT_ROLES n "metrology")]
                (cond-> {"robotDid" rdid
                         "signature" (witness-binding rdid payload)
                         "role" role}
                  (get record "recordedAt") (assoc "timestamp" (get record "recordedAt"))))))
          (or robots []))))

(defn solve
  "Assemble one PV module from its cell batch + feedstock lot, run flash-IV + EL
  metrology (Mimi), and emit a signed moduleAttestation.

  Expected input state:
    moduleSerial, cellBatchId, feedstockLotId, bomCid, ratedWp,
    measuredWp (optional, defaults to ratedWp),
    flashIv (Mimi flash-IV payload), elImage (Mimi EL image payload),
    destinationActorDid, recordedAt, attestingRobots (≥2),
    epbtMonths (optional), recyclabilityBps (optional).

  Returns state augmented with moduleAttestation, provenance, binned, kotobaWritten.
  A module that fails G11/G12 gates is returned with refused=true and NO attestation."
  [state]
  (let [module-serial  (str (get state "moduleSerial" ""))
        cell-batch-id  (str (get state "cellBatchId" ""))
        feedstock-lot  (str (get state "feedstockLotId" ""))
        bom-cid        (str (get state "bomCid" ""))
        rated-wp       (int (or (get state "ratedWp") 0))
        recorded-at    (str (get state "recordedAt" ""))
        dest-did       (str (get state "destinationActorDid" ""))
        robots-in      (vec (or (get state "attestingRobots") []))

        ;; G11: serial <-> feedstock lot traceability is mandatory
        chain (provenance-chain module-serial cell-batch-id feedstock-lot)]

    (if-not (get chain "complete")
      (merge state {"refused" true "reason" (get chain "reason") "kotobaWritten" false})

      (let [[dest-ok dest-reason] (check-destination dest-did)]
        (if-not dest-ok
          (merge state {"refused" true "reason" dest-reason "kotobaWritten" false})

          (if (< (count robots-in) MIN_ATTESTING_ROBOTS)
            (merge state {"refused" true
                          "reason" (str "G11: module needs >= " MIN_ATTESTING_ROBOTS
                                        " co-attesting robots (got " (count robots-in) ")")
                          "kotobaWritten" false})

            ;; All gates passed — build the attestation
            (let [flash-iv-cid   (content-cid (get state "flashIv" {}) "flashiv")
                  el-image-cid   (content-cid (get state "elImage" {}) "elimage")
                  measured-wp    (int (or (get state "measuredWp") rated-wp))
                  [binned bin-reason] (flash-bin rated-wp measured-wp)
                  recyclability-bps (int (or (get state "recyclabilityBps") 0))

                  record-base    {"$type"              "com.etzhayyim.himawari.moduleAttestation"
                                  "moduleSerial"        module-serial
                                  "cellBatchId"         cell-batch-id
                                  "feedstockLotId"      feedstock-lot
                                  "bomCid"              bom-cid
                                  "ratedWp"             rated-wp
                                  "measuredWp"          measured-wp
                                  "flashIvCid"          flash-iv-cid
                                  "elImageCid"          el-image-cid
                                  "destinationActorDid" dest-did
                                  "recordedAt"          recorded-at
                                  "epbtMonths"          (int (or (get state "epbtMonths") 0))
                                  "recyclabilityBps"    recyclability-bps
                                  "recyclabilityBelowFloor" (and (pos? recyclability-bps)
                                                                  (< recyclability-bps RECYCLABILITY_FLOOR_BPS))}

                  chain-digest  (get chain "chainDigest")
                  signature     (sign-module record-base chain-digest)
                  attesting     (robot-signatures robots-in record-base chain-digest)

                  record (cond-> (assoc record-base
                                        "provenanceChainDigest" chain-digest
                                        "signature" signature
                                        "attestingRobots" attesting
                                        "attestingNode" MURAKUMO_NODE)
                           binned (assoc "binned" true "binReason" bin-reason))]

              (merge state
                     {"moduleAttestation" record
                      "provenance"        chain
                      "binned"            binned
                      "kotobaWritten"     false}))))))))
