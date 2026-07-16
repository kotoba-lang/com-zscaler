(ns tsutae.methods.test-charter-gates
  "tsutae 伝え — constitutional-gate conformance tests. Substrate-native Clojure (ADR-2606160842); 1:1 port of pruned test_charter_gates.py."
  (:require [clojure.test :refer [deftest is run-tests]]
            [clojure.string :as str]
            [cheshire.core :as json]))

(def ^:private here (.getParentFile (java.io.File. ^String *file*)))
(def ^:private actor-dir (.getParentFile here))
(def ^:private actor-name (.getName actor-dir))
(def ^:private root (.. actor-dir getParentFile getParentFile))
(def ^:private lexdir (java.io.File. root (str "00-contracts/lexicons/com/etzhayyim/" actor-name)))
(defn- manifest [] (:actor/manifest (clojure.edn/read-string (slurp (java.io.File. actor-dir "manifest.edn")))))
(defn- lex [name] (json/parse-string (slurp (java.io.File. lexdir (str name ".json")))))

(def ^:private OPEN-SOC
  #{"third-party-open-RISC-V-StarFive-JH7110" "third-party-open-RISC-V-SiFive-Unmatched"
    "third-party-open-RISC-V-Allwinner-D1" "iwakura-SoC-silicon-Wave-1"})

(defn- required-union [doc]
  (let [acc (atom #{})]
    (letfn [(walk [x] (cond (map? x) (do (when (sequential? (get x "required")) (swap! acc into (get x "required"))) (doseq [v (vals x)] (walk v)))
                            (sequential? x) (doseq [v x] (walk v))))]
      (walk doc)) @acc))

(defn- known [doc field]
  (let [acc (atom #{})]
    (letfn [(walk [x parent]
              (cond (map? x) (do (when (and (contains? x "knownValues") (= parent field)) (swap! acc into (get x "knownValues")))
                                 (doseq [[k v] x] (walk v k)))
                    (sequential? x) (doseq [v x] (walk v parent))))]
      (walk doc nil)) @acc))

;; ── full gate set ──
(deftest test-all-14-gates-declared
  (let [gates (set (keys (get-in (manifest) ["constitutionalGates" "gates"])))]
    (is (= gates (set (map #(str "G" %) (range 1 15)))) "manifest must declare G1–G14")))

;; ── G6 — anti-surveillance: removable cellular + mandatory mic kill switch ──
(deftest test-g6-anti-surveillance
  (let [req (required-union (lex "chassisAttestation"))]
    (doseq [field ["g6CellularRemovableVerified" "g6MicrophoneKillSwitchVerified"]]
      (is (contains? req field) (str "G6: chassisAttestation must require " field)))))

;; ── G3 — repair-rightful: hand-tool-replaceable battery + repair score ──
(deftest test-g3-repair-rightful
  (let [doc (lex "chassisAttestation")
        req (required-union doc)]
    (doseq [field ["batteryReplaceableHandToolsOnly" "g3RepairScore"]]
      (is (contains? req field) (str "G3: chassisAttestation must require " field)))
    (is (contains? (known doc "batteryChemistry") "LFP-LiFePO4-preferred") "G10: LFP battery chemistry must be offered")))

;; ── G8 — anti-addiction UX ──
(deftest test-g8-anti-addiction-ux
  (let [req (required-union (lex "firmwareAttestation"))]
    (doseq [field ["dopamineLoopApiInstalled" "infiniteScrollOsApiInstalled" "g8CalmDefaultConfig" "notificationBatchingMinutesDefault"]]
      (is (contains? req field) (str "G8: firmwareAttestation must require " field)))))

;; ── G2 — open OS + bootloader-unlock-default + blob audit ──
(deftest test-g2-open-os-unlock-default
  (let [doc (lex "firmwareAttestation")
        req (required-union doc)]
    (doseq [field ["bootloader" "unlockStateAtShipDefault" "blobRatioPercent" "g7BinaryBlobAudit"]]
      (is (contains? req field) (str "G2/G7: firmwareAttestation must require " field)))
    ;; `name` knownValues span the OS field + the bootloader field; both are open by design.
    (let [names (set (map str/lower-case (known doc "name")))
          closed ["knox" "sep" "ios" "windows" "snapdragon" "proprietary" "closed"]]
      (is (not (some (fn [n] (some #(str/includes? n %) closed)) names)) "G2: no proprietary/closed firmware representable")
      (is (and (contains? names "linux-mainline") (contains? names "grapheneos-class")) "G2: open OS options (Linux-mainline / GrapheneOS) must be present"))))

;; ── G9 — open SoC mandatory (closed SoCs unrepresentable) ──
(deftest test-g9-open-soc-only
  (let [soc (known (lex "silenTsutaeReview") "approvedSoc")]
    (is (= soc OPEN-SOC) "G9: approvedSoc must be the open-RISC-V/iwakura set only (no Snapdragon/Apple)")))

;; ── secure crypto-erase on recycle ──
(deftest test-secure-wipe-on-recycle
  (let [req (required-union (lex "recyclingCertificate"))]
    (doseq [field ["cryptoEraseCompleted" "secureWipeAttestation" "wipeMethod"]]
      (is (contains? req field) (str "recyclingCertificate must require " field)))))

;; ── G4 — witness quorum across the line ──
(deftest test-g4-witness-quorum
  (doseq [name ["chassisAttestation" "firmwareAttestation" "pcbAttestation" "deviceAttestation"]]
    (is (contains? (required-union (lex name)) "witnessRobotDids") (str "G4: " name " must require witnessRobotDids"))))
