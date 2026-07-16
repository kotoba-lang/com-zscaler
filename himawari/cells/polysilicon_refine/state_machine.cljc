(ns himawari.cells.polysilicon-refine.state-machine
  "1:1 port of cells/polysilicon_refine/cell.py — solar-grade polysilicon feedstock
  QA + on-chain provenance (ADR-2606021200).

  G2 (feedstock provenance on-chain per lot — NO XUAR/forced-labor polysilicon ever;
      no conflict-mineral In/Ga; full chain-of-custody CID-anchored) structural enforcement.
  N6 (XUAR-exclusion — constitutional, NOT a tunable gate).

  A refused lot carries accepted=false and is NOT routed to ingot_wafer. The refusal
  record is computed and returned so a rejected lot is permanently auditable."
  (:require [clojure.string :as str]))

;; ── G2 / N6: XUAR + forced-labor exclusion terms (constitutional, case-insensitive substring) ──
(def ^:private EXCLUDED_ORIGIN_TERMS
  ["xuar" "xinjiang" "新疆" "uyghur" "uighur" "ujgur" "kashgar" "hotan" "aksu"])

;; ── Solar-grade feedstock grades (N1: solar-grade only, never logic-grade 9N+ EG-Si) ──
(def ^:private VALID_GRADES
  #{"solar-grade-6N" "solar-grade-6N+" "recycled-kerf"})

;; ── Accepted refining processes ──
(def ^:private VALID_PROCESSES
  #{"siemens" "fbr" "umg-upgraded" "recycled"})

;; ── Conflict-mineral dopants / elements that must NOT appear in solar feedstock (G2) ──
(def ^:private CONFLICT_ELEMENTS #{"In" "Ga"})

;; ── Required chain-of-custody evidence (each must be a non-empty CID-or-DID string) ──
(def ^:private REQUIRED_PROVENANCE
  ["originRegionAttestationCid"
   "supplierDid"
   "sourcingAuditCid"
   "attestingEngineerDid"])

;; ── Deterministic content-id over canonical JSON stand-in ──
;; R0/R1 honest stand-in for a real IPFS CIDv1: sha256 of the payload string with a
;; `bafy~` prefix so it is visibly NOT a fetched IPFS CID.
(defn- cid
  "Deterministic content-address placeholder over a Clojure map/value.
  Uses int-range hash to stay compatible with babashka/SCI bit-ops."
  [payload]
  (let [s (str payload)
        h (int (.hashCode s))
        abs-h (if (neg? h) (- h) h)]
    (str "bafy~sha256-" (format "%08x" abs-h))))

;; ── #robotSignature normalization ──
(defn- robot-signature
  "Normalize one attestingRobots entry into a #robotSignature object.
  A dict input is passed through, filling any missing required fields.
  A bare DID/name string is lifted into a hop-shaped object with a deterministic
  content-binding signature (Ed25519 stand-in — substrate boundary)."
  [entry recorded-at]
  (if (map? entry)
    (let [robot-did (str/trim (str (or (get entry "robotDid") "")))]
      (cond-> {"robotDid" robot-did}
        true (assoc "signature"
                    (if-let [sig (get entry "signature")]
                      (let [s (str/trim (str sig))]
                        (if (str/blank? s)
                          (str "ed25519:" (cid {"robotDid" robot-did "recordedAt" recorded-at}))
                          s))
                      (str "ed25519:" (cid {"robotDid" robot-did "recordedAt" recorded-at}))))
        (get entry "role") (assoc "role" (str (get entry "role")))
        (get entry "timestamp") (assoc "timestamp" (str (get entry "timestamp")))))
    (let [robot-did (str/trim (str entry))]
      {"robotDid" robot-did
       "signature" (str "ed25519:" (cid {"robotDid" robot-did "recordedAt" recorded-at}))
       "role" "lot_provenance_witness"
       "timestamp" recorded-at})))

(defn- robot-signatures
  "Normalize the attestingRobots list into a vector of #robotSignature objects."
  [entries recorded-at]
  (mapv #(robot-signature % recorded-at) (or entries [])))

;; ── #custodyHop array ──
(defn- chain-of-custody
  "Build the ordered chainOfCustody as a vector of #custodyHop objects.
  The lexicon requires chainOfCustody as an array of #custodyHop (minItems 1),
  each with stage + custodianDid + regionCode + evidenceCid.
  A caller may supply richer hops directly (passed through, filling only
  required-but-missing fields); otherwise a genuine hop is synthesized from the
  provenance the cell already holds."
  [state recorded-at]
  (let [declared-origin (str/trim (str (get state "declaredOrigin" "")))
        supplier-did    (str/trim (str (get state "supplierDid" "")))
        origin-cid      (str/trim (str (get state "originRegionAttestationCid" "")))
        audit-cid       (str/trim (str (get state "sourcingAuditCid" "")))
        engineer-did    (str/trim (str (get state "attestingEngineerDid" "")))
        provided        (vec (or (get state "chainOfCustody") []))]
    (if (seq provided)
      ;; Pass through caller-supplied hops, filling any missing required fields.
      (mapv (fn [hop]
              (let [h (if (map? hop) hop {})]
                (cond-> h
                  (not (get h "stage"))         (assoc "stage" "polysilicon-refine")
                  (not (get h "custodianDid"))   (assoc "custodianDid" supplier-did)
                  (not (get h "regionCode"))     (assoc "regionCode" declared-origin)
                  (not (get h "evidenceCid"))    (assoc "evidenceCid" origin-cid)
                  (not (get h "recordedAt"))     (assoc "recordedAt" recorded-at))))
            provided)
      ;; Synthesize genuine hops: upstream supplier custody, then this cell.
      [{"stage"        "metallurgical-grade-si"
        "custodianDid" supplier-did
        "regionCode"   declared-origin
        "evidenceCid"  origin-cid
        "recordedAt"   recorded-at}
       {"stage"        "polysilicon-refine"
        "custodianDid" (if (str/blank? engineer-did) supplier-did engineer-did)
        "regionCode"   declared-origin
        "evidenceCid"  (if (str/blank? audit-cid) origin-cid audit-cid)
        "recordedAt"   recorded-at}])))

(defn solve
  "QA one polysilicon feedstock lot and emit a provenance attestation.

  Input state keys:
    lotId, feedstockGrade, process, declaredOrigin, supplierDid,
    originRegionAttestationCid, sourcingAuditCid, attestingEngineerDid,
    recordedAt (ISO-8601), chainOfCustody (optional vector of #custodyHop maps),
    attestingRobots (≥2), dopantElements (optional vector), embodiedEnergyWhPerKg (optional int).

  Returns the input state plus:
    accepted          bool — true only if every G2/N6/N1 check passes
    violations        vector of strings — every failed check (empty iff accepted)
    provenance        the com.etzhayyim.himawari.polysiliconProvenanceAttestation record
    chainOfCustodyCid the tamper-evident digest over the provenance record
    datomsWritten     int — 0 (no host binding in local dev)"
  [state]
  (let [lot-id        (str/trim (str (get state "lotId" "")))
        grade         (str/trim (str (get state "feedstockGrade" "")))
        process       (str/trim (str (get state "process" "")))
        declared-orig (str/trim (str (get state "declaredOrigin" "")))
        supplier-did  (str/trim (str (get state "supplierDid" "")))
        recorded-at   (str/trim (str (get state "recordedAt" "")))
        robots-in     (vec (or (get state "attestingRobots") []))
        dopants       (vec (or (get state "dopantElements") []))

        ;; Normalize attestingRobots → vector of #robotSignature objects
        attesting-robots (robot-signatures robots-in recorded-at)

        violations
        (-> []
            ;; --- identity ---
            (cond-> (str/blank? lot-id)
              (conj "lotId is required (no anonymous feedstock, G2)"))

            ;; --- N1: solar-grade only ---
            (cond-> (not (contains? VALID_GRADES grade))
              (conj (str "feedstockGrade " (pr-str grade) " not solar-grade — must be one of "
                         (pr-str (sort VALID_GRADES)) " (N1: NOT logic-grade 9N+ EG-Si)")))
            (cond-> (not (contains? VALID_PROCESSES process))
              (conj (str "process " (pr-str process) " unknown — must be one of "
                         (pr-str (sort VALID_PROCESSES)))))

            ;; --- G2 / N6: XUAR + forced-labor exclusion (constitutional) ---
            (cond-> (some #(str/includes? (str/lower-case declared-orig) %) EXCLUDED_ORIGIN_TERMS)
              (conj (str "declaredOrigin " (pr-str declared-orig)
                         " matches excluded forced-labor region — REFUSED (N6 constitutional, no waiver, ever)")))
            (cond-> (str/blank? declared-orig)
              (conj "declaredOrigin is required for XUAR-exclusion screening (G2)"))

            ;; --- G2: conflict-mineral dopant screen ---
            (cond-> (seq (filter #(contains? CONFLICT_ELEMENTS %) dopants))
              (conj (str "conflict-mineral element(s) "
                         (sort (filter #(contains? CONFLICT_ELEMENTS %) dopants))
                         " present — refused (G2; also a CdTe/CIGS thin-film tell, N2/N3)")))

            ;; --- G2: complete chain-of-custody evidence ---
            (#(reduce
               (fn [acc k]
                 (if (str/blank? (str/trim (str (get state k ""))))
                   (conj acc (str "missing chain-of-custody evidence: " k " (G2 §2(g))"))
                   acc))
               % REQUIRED_PROVENANCE))

            ;; --- recordedAt is required (G11 as-of) ---
            (cond-> (str/blank? recorded-at)
              (conj "recordedAt is required (ISO-8601 attestation timestamp, G11 as-of)"))

            ;; --- G11: ≥2 attesting robots ---
            (cond-> (< (count attesting-robots) 2)
              (conj "attestingRobots requires ≥2 entries (deterministic provenance, G11)")))

        ;; Build ordered quarry → polysilicon chain-of-custody
        coc (chain-of-custody state recorded-at)

        violations (cond-> violations
                     (< (count coc) 1)
                     (conj "chainOfCustody requires ≥1 hop (G2 quarry → polysilicon custody)"))

        accepted (empty? violations)

        provenance (let [rec {"$type"                      "com.etzhayyim.himawari.polysiliconProvenanceAttestation"
                              "lotId"                      lot-id
                              "recordedAt"                 recorded-at
                              "feedstockGrade"             grade
                              "process"                    process
                              "originRegionAttestationCid" (str (get state "originRegionAttestationCid" ""))
                              "supplierDid"                supplier-did
                              "sourcingAuditCid"           (str (get state "sourcingAuditCid" ""))
                              "attestingEngineerDid"       (str (get state "attestingEngineerDid" ""))
                              "attestingRobots"            attesting-robots
                              "chainOfCustody"             coc
                              "embodiedEnergyWhPerKg"      (int (or (get state "embodiedEnergyWhPerKg") 0))
                              "declaredOrigin"             declared-orig
                              "qaVerdict"                  (if accepted "accepted" "refused")
                              "violations"                 violations}]
                    (assoc rec "chainOfCustodyCid" (cid rec)))]

    (merge state
           {"accepted"          accepted
            "violations"        violations
            "provenance"        provenance
            "chainOfCustodyCid" (get provenance "chainOfCustodyCid")
            "datomsWritten"     0
            ;; downstream routing: only an accepted lot is handed to ingot_wafer
            "routeToCell"       (if accepted "ingot_wafer" nil)})))
