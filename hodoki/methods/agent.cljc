(ns hodoki.methods.agent
  "hodoki 解き — ELV disassembly + materials recovery cell. 1:1 port of py/agent.py. Handlers over
  the ELV processing chain with gates: G5 charter-rider scan (no military/weapon), G6 f-gas ≥95%
  capture, G8 ECU-data-wipe mandatory + witness ≥2, G12 right-to-repair part DID, G13 material
  recovery ≥95% / ASR <5%, G14 PGM recovery ≥95%, S2/S3 USDC + 10% tithe (stops at :intent). Pure
  compute; the Murakumo llm host binding is unused here (the omitted leg)."
  (:require [clojure.string :as str])
  (:import [java.security MessageDigest]))

(def TITHE-BPS 1000)
(def ^:private PROHIBITED-PATTERNS
  ["military" "weapon" "armored" "ballistic" "surveillance" "drone" "robot-swarm" "autonomous-targeting" "covert"])

(defn- py-list-repr
  "Python repr(list-of-str): ['a', 'b'] — single-quoted, comma-space-joined. Mirrors
  the Python original's f-string message so the reason text is byte-parity-stable."
  [items]
  (str "[" (str/join ", " (map #(str "'" % "'") items)) "]"))

(defn scan-charter-compliance
  "G5 Charter §2(a-h) scan: reject military/weapon-carrying vehicles."
  [_vin vehicle-desc]
  (let [low (str/lower-case vehicle-desc)
        hits (filterv #(str/includes? low %) PROHIBITED-PATTERNS)]
    (if (seq hits)
      {"ok" false "reason" (str "Charter violation: " (py-list-repr hits) " (G5)") "status" "failed"}
      {"ok" true "reason" "Charter scan passed" "status" "passed"})))

(defn check-fgas-compliance
  "G6 F-gas (HFC/HFO) capture ≥95% by mass; no atmospheric venting."
  ([fgas-mass-g] (check-fgas-compliance fgas-mass-g 95.0))
  ([fgas-mass-g target-pct]
   (cond
     (< fgas-mass-g 0) {"ok" false "reason" "F-gas mass cannot be negative"}
     (< target-pct 95.0) {"ok" false "reason" (str "F-gas capture " target-pct "% < 95% (G6)")}
     :else {"ok" true "reason" (str "F-gas capture " target-pct "% ≥ 95% (G6)")})))

(defn ecu-data-wipe-mandatory
  "G8 CONSTITUTIONAL FIRST: MANDATORY ECU + infotainment + telematics wipe."
  [ecu-wiped witness-count]
  (cond
    (not ecu-wiped) {"ok" false "reason" "ECU data wipe NOT completed (G8)" "blocked" true}
    (< witness-count 2) {"ok" false "reason" (str "Witness count " witness-count " < 2 robots (G8)") "blocked" true}
    :else {"ok" true "reason" "ECU wipe + witness quorum ≥2 (G8)"}))

(defn issue-part-did
  "G12 Issue DID for harvested part (IPFS-pinned, VIN-provenanced)."
  [vin part-name]
  (let [md (MessageDigest/getInstance "SHA-256")
        bs (.digest md (.getBytes (str vin ":" part-name) "UTF-8"))
        hex (apply str (map #(format "%02x" (bit-and % 0xff)) bs))]
    (str "did:web:hodoki.etzhayyim.com:part:" (subs hex 0 16))))

(defn calc-recovery-rate
  "G13 Material recovery ≥95%; ASR <5% to landfill."
  [ferrous-kg non-ferrous-kg copper-kg asr-kg input-mass-kg]
  (let [total-recovered (+ ferrous-kg non-ferrous-kg copper-kg)
        recovery-pct (if (> input-mass-kg 0) (* (/ total-recovered input-mass-kg) 100) 0)
        asr-pct (if (> input-mass-kg 0) (* (/ asr-kg input-mass-kg) 100) 0)]
    (cond
      (< recovery-pct 95.0) {"ok" false "reason" (str "Recovery " recovery-pct "% < 95% (G13)") "blocked" true}
      (>= asr-pct 5.0) {"ok" false "reason" (str "ASR landfill " asr-pct "% >= 5% (G13)") "blocked" true}
      :else {"ok" true "reason" (str "Recovery " recovery-pct "% ≥ 95%, ASR " asr-pct "% < 5% (G13)")
             "recovery_pct" (long recovery-pct) "asr_landfill_pct" (long asr-pct)})))

(defn audit-pgm-yield
  "G14 PGM (Pt/Pd/Rh) recovery ≥95% from catalytic converters."
  [pgm-yield-pct]
  (if (< pgm-yield-pct 95.0)
    {"ok" false "reason" (str "PGM yield " pgm-yield-pct "% < 95% (G14)") "blocked" true}
    {"ok" true "reason" (str "PGM yield " pgm-yield-pct "% ≥ 95% (G14)")}))

(defn build-settlement-intent
  "USDC settlement split. 10% tithe → Public Fund. Stops at :intent — broadcast needs operator
  signature (S3)."
  ([gross-minor] (build-settlement-intent gross-minor nil))
  ([gross-minor buyer-sig-ref]
   (let [tithe (quot (* gross-minor TITHE-BPS) 10000)]
     {"rail" "usdc-base-l2" "grossMinor" gross-minor "titheMinor" tithe
      "operatorPayoutMinor" (- gross-minor tithe) "titheRouter" "50-infra/etzhayyim-tithe-router"
      "state" (if buyer-sig-ref "executed" "intent") "operatorSigRef" (or buyer-sig-ref "")})))

;; ── handlers ──────────────────────────────────────────────────────────────────
(defn handle-intake-audit
  "L1a: Intake audit — VIN + title + consent + Charter scan."
  [state]
  (let [result (scan-charter-compliance (get state "vin" "") (get state "vehicle_desc" ""))]
    (merge state {"charter_status" (get result "status" "pending")})))

(defn handle-depollution
  "L1b: Depollution — fluid drain + F-gas capture + airbag disarm."
  [state]
  (let [ecu (ecu-data-wipe-mandatory (get state "ecu_wiped" false) (get state "witness_count" 0))
        fgas (check-fgas-compliance (get state "fgas_mass_g" 0) (get state "fgas_target_pct" 0))
        overall-ok (and (get ecu "ok") (get fgas "ok"))]
    (merge state {"status" (if overall-ok "passed" "blocked")})))

(defn handle-battery-handling
  "L2: Battery handling — SoH measurement + routing."
  [state]
  (let [soh (get state "soh_pct" 0)
        routing (cond (>= soh 70) "hikari-second-life" (>= soh 40) "cell-recycle" :else "kanayama-reclaim")]
    (merge state {"routing_decision" routing})))

(defn handle-parts-harvest
  "L3a: Parts harvest — grade + DID + IPFS catalog (G12)."
  [state]
  (merge state {"part_did" (issue-part-did (get state "vin" "") (get state "part_name" ""))}))

(defn handle-catalyst-recovery
  "L3b: Catalyst recovery — PGM yield audit ≥95% (G14)."
  [state]
  (let [result (audit-pgm-yield (get state "pgm_yield_pct" 0))]
    (merge state {"status" (if (get result "ok") "passed" "blocked")})))

(defn handle-body-shred
  "L4: Body shred — ferrous/non-ferrous/Cu/ASR sort (G13)."
  [state]
  (let [result (calc-recovery-rate (get state "ferrous_kg" 0) (get state "non_ferrous_kg" 0)
                                   (get state "copper_kg" 0) (get state "asr_kg" 0) (get state "input_mass_kg" 0))
        update (if (get result "ok")
                 {"recovery_pct" (get result "recovery_pct" 0) "asr_landfill_pct" (get result "asr_landfill_pct" 0)}
                 {})]
    (merge state update)))
