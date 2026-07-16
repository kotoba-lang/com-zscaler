(ns yamabiko.methods.agent
  "yamabiko 山彦 — high-speed rail trainset manufacturing actor (babashka port).")

(def ^:const TITHE_BPS 1000)

;; ---------------------------------------------------------------------------
;; carbody_fabrication — L1 aluminum/composite FSW seams
;; ---------------------------------------------------------------------------
(defn validate-carbody-fabrication
  [fsw-count test-pass]
  (if (< fsw-count 100)
    {"ok" false, "reason" "FSW count too low for Shinkansen-class trainset"}
    (if-not test-pass
      {"ok" false, "reason" "Structural test failed"}
      {"ok" true, "reason" "carbody fabrication valid"})))

(defn record-carbody-attestation
  [carbody-id fsw-count structural-pass material]
  (let [val (validate-carbody-fabrication fsw-count structural-pass)]
    (if-not (get val "ok")
      {"error" (get val "reason"), "blocked" true}
      {":carbodyAttestation/id" carbody-id
       ":carbodyAttestation/fswWelds" (str fsw-count)
       ":carbodyAttestation/structuralTests" (if structural-pass "passed" "failed")
       ":carbodyAttestation/material" material})))

;; ---------------------------------------------------------------------------
;; bogie_assembly — L2 wheel set + suspension + traction motor
;; ---------------------------------------------------------------------------
(defn validate-bogie-assembly
  [bogie-id traction-type witness-ok]
  (if-not (#{"bemu" "h2" "electric"} traction-type)
    {"ok" false, "reason" (str "unknown traction type '" traction-type "'")}
    (if-not witness-ok
      {"ok" false, "reason" "bogie marriage quorum < 2 robots (G4)"}
      {"ok" true, "reason" "bogie assembly valid"})))

(defn record-bogie-attestation
  [bogie-id position traction-type witness-ok]
  (let [val (validate-bogie-assembly bogie-id traction-type witness-ok)]
    (if-not (get val "ok")
      {"error" (get val "reason"), "blocked" true}
      {":bogieAttestation/id" bogie-id
       ":bogieAttestation/position" position
       ":bogieAttestation/tractionMotor" traction-type
       ":bogieAttestation/witnessQuorum" (if witness-ok "passed" "failed")})))

;; ---------------------------------------------------------------------------
;; interior_hvac — L3 seating + HVAC + PIS
;; ---------------------------------------------------------------------------
(defn validate-interior-hvac
  [car-number seating hvac-pass]
  (cond
    (or (< car-number 1) (> car-number 16))
    {"ok" false, "reason" (str "invalid car number " car-number)}

    (or (< seating 60) (> seating 100))
    {"ok" false, "reason" (str "seating " seating " out of range")}

    (not hvac-pass)
    {"ok" false, "reason" "HVAC functional test failed"}

    :else
    {"ok" true, "reason" "interior HVAC valid"}))

(defn record-interior-attestation
  [interior-id car-number seating hvac-pass pis-version]
  (let [val (validate-interior-hvac car-number seating hvac-pass)]
    (if-not (get val "ok")
      {"error" (get val "reason"), "blocked" true}
      {":interiorAttestation/id" interior-id
       ":interiorAttestation/carNumber" (str car-number)
       ":interiorAttestation/seatingCount" (str seating)
       ":interiorAttestation/hvacSpec" (if hvac-pass "passed" "failed")
       ":interiorAttestation/pisVersions" pis-version})))

;; ---------------------------------------------------------------------------
;; traction_electrical — L4 25 kV AC / 1500 V DC + ATP/ATO firmware (open-source G1)
;; ---------------------------------------------------------------------------
(defn validate-traction-electrical
  [hv-type firmware-sha license-verified]
  (cond
    (not (#{"25kV AC" "1500V DC" "25kV AC / 1500V DC hybrid"} hv-type))
    {"ok" false, "reason" (str "unknown HV system type '" hv-type "'")}

    (or (nil? firmware-sha) (< (count firmware-sha) 20))
    {"ok" false, "reason" "ATP/ATO firmware SHA invalid"}

    (not license-verified)
    {"ok" false, "reason" "ATP/ATO firmware license not Apache 2.0 (G1)"}

    :else
    {"ok" true, "reason" "traction electrical valid"}))

(defn record-traction-electrical-attestation
  [traction-id hv-type firmware-sha license-verified]
  (let [val (validate-traction-electrical hv-type firmware-sha license-verified)]
    (if-not (get val "ok")
      {"error" (get val "reason"), "blocked" true}
      {":tractionElectricalAttestation/id" traction-id
       ":tractionElectricalAttestation/hvHarness" hv-type
       ":tractionElectricalAttestation/atpAtoFirmwareSha" firmware-sha
       ":tractionElectricalAttestation/firmwareOpenSource" (if license-verified "Apache 2.0" "unverified")})))

;; ---------------------------------------------------------------------------
;; final_assembly — L5a carbody + bogie + interior + traction integration
;; ---------------------------------------------------------------------------
(defn validate-final-assembly
  [integration-pass system-checks-pass]
  (if-not integration-pass
    {"ok" false, "reason" "carbody-bogie integration failed"}
    (if-not system-checks-pass
      {"ok" false, "reason" "system cross-checks failed"}
      {"ok" true, "reason" "final assembly valid"})))

(defn record-final-assembly-attestation
  [assembly-id integration-pass system-checks-pass]
  (let [val (validate-final-assembly integration-pass system-checks-pass)]
    (if-not (get val "ok")
      {"error" (get val "reason"), "blocked" true}
      {":finalAssemblyAttestation/id" assembly-id
       ":finalAssemblyAttestation/carbodyBogieIntegration" (if integration-pass "passed" "failed")
       ":finalAssemblyAttestation/systemCrossChecks" (if system-checks-pass "passed" "failed")})))

;; ---------------------------------------------------------------------------
;; dynamic_test — L5b speed ramp-up (≤320 km/h per G12), braking, power
;; ---------------------------------------------------------------------------
(defn validate-dynamic-test
  [max-speed braking-dist power-draw]
  (cond
    (> max-speed 320)
    {"ok" false, "reason" (str "max speed " max-speed " exceeds 320 km/h (G12)")}

    (< max-speed 200)
    {"ok" false, "reason" (str "max speed " max-speed " too low for Shinkansen-class")}

    (> braking-dist 1000)
    {"ok" false, "reason" (str "braking distance " braking-dist " m excessive")}

    (or (< power-draw 5000) (> power-draw 15000))
    {"ok" false, "reason" (str "power draw " power-draw " kW out of range")}

    :else
    {"ok" true, "reason" "dynamic test valid"}))

(defn record-dynamic-test-record
  [test-id max-speed braking-dist power-draw]
  (let [val (validate-dynamic-test max-speed braking-dist power-draw)]
    (if-not (get val "ok")
      {"error" (get val "reason"), "blocked" true}
      {":dynamicTestRecord/id" test-id
       ":dynamicTestRecord/speedProfile" (str "max " max-speed " km/h")
       ":dynamicTestRecord/brakingDistance" (str braking-dist " m")
       ":dynamicTestRecord/powerDraw" (str power-draw " kW")
       ":dynamicTestRecord/thermalSignature" "nominal"})))

;; ---------------------------------------------------------------------------
;; emissions_acoustic — cross-cutting ISO 3095 + IEC 62236 + 騒音規制法
;; ---------------------------------------------------------------------------
(defn validate-emissions-acoustic
  [iso3095-dba iec62236-pass kyoukusei-pass]
  (cond
    (> iso3095-dba 85)
    {"ok" false, "reason" (str "ISO 3095 " iso3095-dba " dB(A) exceeds 85 dB(A) (G8)")}

    (not iec62236-pass)
    {"ok" false, "reason" "IEC 62236 EMC test failed (G8)"}

    (not kyoukusei-pass)
    {"ok" false, "reason" "騒音規制法 (Noise Regulation Act) compliance failed (G8)"}

    :else
    {"ok" true, "reason" "emissions acoustic valid"}))

(defn record-acoustic-emissions-audit
  [audit-id iso3095-dba iec62236-pass kyoukusei-pass]
  (let [val (validate-emissions-acoustic iso3095-dba iec62236-pass kyoukusei-pass)]
    (if-not (get val "ok")
      {"error" (get val "reason"), "blocked" true}
      {":acousticEmissionsAuditRecord/id" audit-id
       ":acousticEmissionsAuditRecord/iso3095Wayside" (str iso3095-dba " dB(A)")
       ":acousticEmissionsAuditRecord/iec62236Emc" (if iec62236-pass "passed" "failed")
       ":acousticEmissionsAuditRecord/kyoukuseiFuinsho" (if kyoukusei-pass "passed" "failed")})))

;; ---------------------------------------------------------------------------
;; homologation_binder — L5c aggregate tests + mint per-trainset DID + EoL recyclability
;; ---------------------------------------------------------------------------
(defn mint-trainset-did
  [serial]
  (str "did:web:etzhayyim.com:yamabiko:trainset:" serial))

(defn validate-homologation
  [all-gates-pass eol-recyclability]
  (if-not all-gates-pass
    {"ok" false, "reason" "not all constitutional gates passed"}
    (if (< eol-recyclability 90)
      {"ok" false, "reason" (str "EoL recyclability " eol-recyclability "% < 90% (G14)")}
      {"ok" true, "reason" "homologation preconditions valid"})))

(defn record-homologation
  [hom-id serial all-gates-pass eol-recyclability]
  (let [val (validate-homologation all-gates-pass eol-recyclability)]
    (if-not (get val "ok")
      {"error" (get val "reason"), "blocked" true}
      {:trainset-did (mint-trainset-did serial)
       ":homologationRecord/id" hom-id
       ":homologationRecord/trainsetDid" (mint-trainset-did serial)
       ":homologationRecord/iso3095Cert" (if all-gates-pass "passed" "pending")
       ":homologationRecord/iec62236Cert" (if all-gates-pass "passed" "pending")
       ":homologationRecord/kyoukuseiCert" (if all-gates-pass "passed" "pending")
       ":homologationRecord/eolRecyclabilityAudit" (str eol-recyclability "%")})))

;; ---------------------------------------------------------------------------
;; silen_rail_review — governance attestation of all gates G1..G22
;; ---------------------------------------------------------------------------
(defn validate-silen-review
  [member-sigs all-gates-checked firmware-audit-pass]
  (cond
    (< member-sigs 5)
    {"ok" false, "reason" (str "Council sigs " member-sigs " < 5 (need ≥5 of 7)")}

    (not all-gates-checked)
    {"ok" false, "reason" "not all gates G1..G22 checked"}

    (not firmware-audit-pass)
    {"ok" false, "reason" "ATP/ATO firmware audit failed (G1)"}

    :else
    {"ok" true, "reason" "Council review valid"}))

(defn record-silen-rail-review
  [review-id member-sigs all-gates-checked firmware-audit-pass]
  (let [val (validate-silen-review member-sigs all-gates-checked firmware-audit-pass)]
    (if-not (get val "ok")
      {"error" (get val "reason"), "blocked" true}
      {":silenRailReview/id" review-id
       ":silenRailReview/councilAttestationSigs" (str member-sigs "/7")
       ":silenRailReview/gatesChecklist" (if all-gates-checked "all passed" "incomplete")
       ":silenRailReview/openSourceFirmwareVerified" (if firmware-audit-pass "verified" "failed")})))

;; ---------------------------------------------------------------------------
;; settlement — USDC + TitheRouter intent (NOT broadcast; G17/G21/G22)
;; ---------------------------------------------------------------------------
(defn build-settlement-intent
  ([gross-minor]
   (build-settlement-intent gross-minor nil))
  ([gross-minor buyer-sig-ref]
   (let [tithe (quot (* gross-minor TITHE_BPS) 10000)]
     {"rail" "usdc-base-l2"
      "grossMinor" gross-minor
      "titheMinor" tithe
      "manufacturerPayoutMinor" (- gross-minor tithe)
      "titheRouter" "50-infra/etzhayyim-tithe-router"
      "state" (if buyer-sig-ref "executed" "intent")
      "buyerSigRef" (or buyer-sig-ref "")})))
