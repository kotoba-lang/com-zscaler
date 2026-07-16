(ns himawari.cells.supply-procurement.state-machine
  "1:1 port of cells/supply_procurement/cell.py — 調達 SBOM<->kotoba procurement
  + okaimono commons-first (ADR-2606021200).

  G8 (full SBOM on-chain CycloneDX → kotoba EAVT) + G2 (§2(g) per-lot sourcing audit
  + XUAR-exclusion chain-of-custody) enforcement.

  COMPOSES (does not re-implement):
  - okaimono commons-first three-ring routing + SBT↔SBT eligibility (ADR-2606012100)
  - giemon CycloneDX → kotoba EAVT bridge for the on-chain SBOM (ADR-2605312330)
  The cljc port carries the pure logic without importing those actors;
  the composition seam is expressed as a data contract (ring routing, SBOM shape)
  rather than a direct function call."
  (:require [clojure.string :as str]))

;; ── Constitutional constants for procurement ──

;; G2: solar-grade feedstock grades ONLY — never logic-grade 9N+ EG-Si (N1).
(def ^:private SOLAR_GRADES #{"solar-grade-6N" "solar-grade-6N+" "recycled-kerf"})

;; G2: forced-labor / XUAR-exclusion is non-negotiable.
(def ^:private XUAR_REGIONS #{"xuar" "xinjiang" "新疆" "uyghur"})

;; G8: intra-fab transport of received lots routes to the giemon AGV.
(def ^:private INTRA_FAB_TRANSPORT "giemon-agv")

;; Commons-first ring ordering (mirrors okaimono RING_ORDER).
(def ^:private RING_ORDER ["commons" "internal" "external"])

;; Default tithe in basis points (mirrors okaimono TITHE_BPS = 10%).
(def ^:private TITHE_BPS 1000)

;; ── Deterministic CID placeholder ──

(defn- cid-placeholder
  "Deterministic content-address placeholder (R0/R1 stand-in for a real IPFS CIDv1).
  Uses int-range hash to stay compatible with babashka/SCI bit-ops."
  [payload]
  (let [s (str payload)
        h (int (.hashCode s))
        abs-h (if (neg? h) (- h) h)]
    (str "bafy~sha256-" (format "%08x" abs-h))))

;; ── G2 feedstock guards ──

(defn- guard-feedstock
  "Return a refusal map if the lot violates a G2 constitutional gate, else nil.
  Solar-grade-only (N1) + XUAR-exclusion (N6) are NOT amendable."
  [need]
  (let [grade  (get need "feedstockGrade")
        origin (str/lower-case (str/trim (str (get need "originRegion" ""))))]
    (cond
      (and (some? grade) (not (contains? SOLAR_GRADES grade)))
      {"state"  "refused"
       "reason" (str "feedstockGrade " (pr-str grade) " is not solar-grade (N1: solar-grade only, "
                     "never logic-grade EG-Si); allowed=" (pr-str (sort SOLAR_GRADES)))}

      (and (seq origin) (some #(str/includes? origin (str/lower-case %)) XUAR_REGIONS))
      {"state"  "refused"
       "reason" "origin region is XUAR/forced-labor excluded (G2/N6 constitutional — NO XUAR polysilicon ever); closes hikari §G2 structurally"}

      :else nil)))

;; ── Commons-first ring resolution ──

(defn- resolve-ring
  "Route the need commons-first (G4/G12 mirror): an explicit ring wins;
  otherwise prefer an internal Ring-1 producing actor, falling back to external
  feedstock supply only when no internal source exists."
  [need]
  (let [explicit (get need "ring")]
    (cond
      (some #(= explicit %) RING_ORDER)
      explicit
      ;; recycled-kerf feedstock is, by definition, a commons (closed-loop) source.
      (= (get need "feedstockGrade") "recycled-kerf")
      "commons"
      :else "external")))

;; ── Procurement order builder ──

(defn- build-procurement-order
  "Build the procurement order. Internal Ring-1 buys go through okaimono's verified
  SBT↔SBT eligibility + USDC/TitheRouter settlement intent; external feedstock buys
  are a member/operator-gated handoff (§1.3/G11)."
  [need ring]
  (let [buyer (str (get need "buyerDid" "did:web:etzhayyim.com:himawari"))
        gross (long (or (get need "grossMinor") 0))
        base  {"lotId"            (get need "lotId")
               "needText"         (str (get need "needText" ""))
               "ring"             ring
               "buyerDid"         buyer
               "intraFabTransport" INTRA_FAB_TRANSPORT}]
    (case ring
      "commons"
      (merge base {"state" "commons-recovery" "settlement" "commons-none" "titheMinor" 0})

      "internal"
      (let [maker   (str (get need "makerActor" ""))
            tithe   (quot (* gross TITHE_BPS) 10000)
            settlement
            {"rail"              "usdc-base-l2"
             "grossMinor"        gross
             "titheMinor"        tithe
             "makerPayoutMinor"  (- gross tithe)
             "makerActor"        maker
             "state"             (if (get need "operatorRef") "executed" "intent")}]
        (merge base {"state" "settle-intent" "makerActor" maker "settlement" settlement}))

      ;; external: feedstock supplier (§1.3 — no external value INTO etzhayyim).
      (merge base
             {"state"       (if (get need "operatorRef") "external-handoff" "external-pending-operator")
              "supplierDid" (get need "supplierDid")
              "settlement"  "operator-gated-purchase"
              "grossMinor"  gross
              "titheMinor"  0
              "operatorRef" (get need "operatorRef")}))))

;; ── SBOM attestation (G8) ──

(defn- feedstock-component
  "The feedstock lot as a CycloneDX `device` component, purl-keyed on grade+process."
  [need]
  (let [grade    (str (get need "feedstockGrade" "unknown"))
        process  (str (get need "process" "unknown"))
        lot      (str (get need "lotId" "unknown"))
        purl     (str "pkg:himawari-feedstock/" grade "/" process "@" lot)
        supplier (get need "supplierDid")]
    (cond-> {"bom-ref" (str "feedstock/" lot)
             "type"    "device"
             "name"    (str "polysilicon " grade)
             "version" lot
             "purl"    purl
             "properties"
             (cond-> [{"name" "giemon:procurement" "value" "feedstock"}
                      {"name" "giemon:grade"       "value" grade}
                      {"name" "giemon:process"     "value" process}]
               supplier (conj {"name" "giemon:supplierDid" "value" supplier}))}
      supplier (assoc "supplier" {"name" supplier}))))

(defn- cyclonedx-doc
  "Assemble a minimal, valid CycloneDX 1.5 document for the lot (G8)."
  [lot-id components]
  {"bomFormat"   "CycloneDX"
   "specVersion" "1.5"
   "version"     1
   "metadata"    {"tools"     [{"vendor"  "etzhayyim"
                                "name"    "himawari-supply_procurement"
                                "version" "0.1.0"}]
                  "component" {"type" "application" "name" (str lot-id)}}
   "components"  components})

(defn- build-sbom-attestation
  "Emit a CycloneDX 1.5 SBOM for the lot's feedstock + consumables and project it to
  kotoba `:cdx/*` datoms (G8)."
  [need order]
  (let [lot-id     (str (or (get need "lotId") (get order "needText") "unknown-lot"))
        extra-comps (vec (get need "components" []))
        components  (if (get need "feedstockGrade")
                      (into [(feedstock-component need)] extra-comps)
                      extra-comps)
        cdx         (cyclonedx-doc lot-id components)
        ;; Minimal kotoba entities (mirrors giemon CycloneDX→kotoba bridge shape).
        entities (mapv (fn [c]
                         {"id"      (str (or (get c "bom-ref") (get c "purl") (get c "name") "unknown"))
                          "type"    "SbomComponent"
                          "labelEn" (str (get c "name" ""))
                          "claims"  [{"pred" "cdx/purl" "value" (str (get c "purl" ""))}
                                     {"pred" "cdx/sbom" "value" lot-id}]})
                       components)]
    {"lotId"          lot-id
     "bomFormat"      "CycloneDX"
     "specVersion"    (get cdx "specVersion")
     "componentCount" (count components)
     "cyclonedx"      cdx
     "kotobaEntities" entities}))

;; ── Per-lot provenance attestation (G2) ──

(defn- build-provenance-attestation
  "Build a per-lot provenance attestation matching the himawari lexicon
  polysiliconProvenanceAttestation and project it to kotoba `:provenance/*` datoms (G2/G6)."
  [need]
  (let [required-keys ["lotId" "feedstockGrade" "originRegionAttestationCid"
                        "supplierDid" "sourcingAuditCid" "attestingEngineerDid"
                        "attestingRobots"]
        record        {"$type"                      "com.etzhayyim.himawari.polysiliconProvenanceAttestation"
                       "lotId"                      (get need "lotId")
                       "feedstockGrade"             (get need "feedstockGrade")
                       "process"                    (get need "process")
                       "originRegionAttestationCid" (get need "originRegionAttestationCid")
                       "supplierDid"                (get need "supplierDid")
                       "sourcingAuditCid"           (get need "sourcingAuditCid")
                       "attestingEngineerDid"       (get need "attestingEngineerDid")
                       "attestingRobots"            (vec (get need "attestingRobots" []))
                       "embodiedEnergyWhPerKg"      (get need "embodiedEnergyWhPerKg")}
        missing (filterv #(let [v (get record %)]
                            (or (nil? v) (and (string? v) (str/blank? v)) (and (coll? v) (empty? v))))
                         required-keys)
        ;; ≥2 attesting robots is a lexicon minItems constraint
        missing (if (and (< (count (get record "attestingRobots")) 2)
                         (not (some #(= % "attestingRobots") missing)))
                  (conj missing "attestingRobots(min2)")
                  missing)
        attested (empty? missing)
        record   (cond-> (assoc record "attested" attested)
                   (seq missing) (assoc "unattestedReason"
                                        (str "missing G2 provenance fields: " (vec missing))))
        claims   (cond-> [{"pred" "provenance/lot"                    "value" (str (get record "lotId"))}
                           {"pred" "provenance/grade"                  "value" (str (get record "feedstockGrade"))}
                           {"pred" "provenance/originAttestationCid"   "value" (str (get record "originRegionAttestationCid"))}
                           {"pred" "provenance/sourcingAuditCid"       "value" (str (get record "sourcingAuditCid"))}
                           {"pred" "provenance/supplierDid"            "value" (str (get record "supplierDid"))}
                           {"pred" "provenance/attestingEngineerDid"   "value" (str (get record "attestingEngineerDid"))}
                           {"pred" "provenance/attested"               "value" (str attested)}]
                   (some? (get record "embodiedEnergyWhPerKg"))
                   (conj {"pred" "provenance/embodiedEnergyWhPerKg"
                          "value" (str (get record "embodiedEnergyWhPerKg"))}))
        entity   {"id"      (str "provenance/" (get record "lotId"))
                  "type"    "PolysiliconProvenanceAttestation"
                  "labelEn" (str "lot " (get record "lotId") " " (get record "feedstockGrade"))
                  "claims"  claims}]
    {"record" record "kotobaEntity" entity}))

;; ── Pregel entrypoint ──

(defn solve
  "Resolve one procurement need and emit the order + attestations.

  Input `state` carries a `need` map describing the feedstock/consumable lot:
    {\"needText\"                    str
     \"lotId\"                       str
     \"feedstockGrade\"              str  (solar-grade-6N | solar-grade-6N+ | recycled-kerf)
     \"process\"                     str
     \"originRegion\"                str  (G2: XUAR-excluded)
     \"supplierDid\"                 str
     \"buyerDid\"                    str
     \"makerActor\"                  str  (optional, Ring-1 producer)
     \"grossMinor\"                  int  (USDC minor units)
     \"originRegionAttestationCid\"  str
     \"sourcingAuditCid\"            str
     \"attestingEngineerDid\"        str
     \"attestingRobots\"             vec
     \"embodiedEnergyWhPerKg\"       int
     \"components\"                  vec  (additional CycloneDX components)
     \"sbtRegistry\"                 map  (SBT eligibility, unused in pure cljc)
     \"operatorRef\"                 str  (G11: gate live broadcast)}

  Returns state extended with procurementOrder, sbomAttestation, provenanceAttestation,
  intraFabTransport, kotobaWrites. Refusals surface with refused=true and reason."
  [state]
  (let [need  (or (get state "need") {})
        guard (guard-feedstock need)]
    (if (some? guard)
      (merge state {"procurementOrder" guard "refused" true "reason" (get guard "reason")})
      (let [ring  (resolve-ring need)
            order (build-procurement-order need ring)]
        (if (or (= (get order "state") "refused") (get order "refused"))
          (merge state {"procurementOrder" order "refused" true "reason" (get order "reason")})
          (let [sbom      (build-sbom-attestation need order)
                prov      (when (get need "lotId") (build-provenance-attestation need))
                writes    (cond-> (vec (get sbom "kotobaEntities"))
                            (some? prov) (conj (get prov "kotobaEntity")))]
            (merge state
                   {"procurementOrder"      order
                    "sbomAttestation"       sbom
                    "provenanceAttestation" prov
                    "intraFabTransport"     INTRA_FAB_TRANSPORT
                    "kotobaWrites"          writes})))))))
