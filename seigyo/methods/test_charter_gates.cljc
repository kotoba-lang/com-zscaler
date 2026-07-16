(ns seigyo.methods.test-charter-gates
  "seigyo — structural charter/safety-gate conformance tests. Substrate-native Clojure (ADR-2606160842); 1:1 port of pruned test_charter_gates.py."
  (:require [clojure.test :refer [deftest is run-tests]]
            [clojure.string :as str]
            [cheshire.core :as json]))

(def ^:private here (.getParentFile (java.io.File. ^String *file*)))
(def ^:private actor-dir (.getParentFile here))
(def ^:private actor-name (.getName actor-dir))
(def ^:private root (.. actor-dir getParentFile getParentFile))
(def ^:private lexdir (java.io.File. root (str "00-contracts/lexicons/com/etzhayyim/" actor-name)))

(def ^:private OPEN-SCADA #{"fuxa" "rapid-scada" "openscada"})
(def ^:private COMMERCIAL-VENDORS
  ["siemens" "honeywell" "yokogawa" "emerson" "rockwell" "abb"
   "schneider" "mitsubishi" "aveva" "ignition" "wonderware" "deltav"])

(defn- load-lex [name] (json/parse-string (slurp (java.io.File. lexdir name))))
(defn- lex-files [] (filter #(.endsWith (.getName ^java.io.File %) ".json") (seq (.listFiles lexdir))))

(defn- required-union [doc]
  (let [acc (atom #{})]
    (letfn [(walk [x] (cond (map? x) (do (when (sequential? (get x "required")) (swap! acc into (get x "required"))) (doseq [v (vals x)] (walk v)))
                            (sequential? x) (doseq [v x] (walk v))))]
      (walk doc)) @acc))

(defn- known [doc field]
  (let [acc (atom #{})]
    (letfn [(walk [x parent]
              (cond (map? x) (do (when (and (= parent field) (contains? x "knownValues")) (swap! acc into (get x "knownValues")))
                                 (doseq [[k v] x] (walk v k)))
                    (sequential? x) (doseq [v x] (walk v parent))))]
      (walk doc nil)) @acc))

(defn- all-enum-values [doc]
  (let [acc (atom #{})]
    (letfn [(walk [x] (cond (map? x) (do (when (sequential? (get x "knownValues")) (swap! acc into (map #(str/lower-case (str %)) (get x "knownValues")))) (doseq [v (vals x)] (walk v)))
                            (sequential? x) (doseq [v x] (walk v))))]
      (walk doc)) @acc))

(defn- all-property-keys [doc]
  (let [acc (atom #{})]
    (letfn [(walk [x] (cond (map? x) (do (when (map? (get x "properties")) (swap! acc into (keys (get x "properties")))) (doseq [v (vals x)] (walk v)))
                            (sequential? x) (doseq [v x] (walk v))))]
      (walk doc)) @acc))

;; ── no commercial DCS/SCADA — only the open stack is representable ──
(deftest test-scada-stack-is-open-only
  (is (= (known (load-lex "scadaProjectAttestation.json") "scadaStack") OPEN-SCADA)))

(deftest test-no-commercial-vendor-in-any-enum-value
  (doseq [f (lex-files)]
    (let [vals (all-enum-values (json/parse-string (slurp f)))]
      (doseq [vendor COMMERCIAL-VENDORS]
        (is (not (some #(str/includes? % vendor) vals))
            (str (.getName ^java.io.File f) ": commercial vendor '" vendor "' must not be a representable enum value"))))))

;; ── safety interlock: physically verified, hardwired safety functions ──
(deftest test-interlock-physically-verified-required
  (let [doc (load-lex "interlockVerificationRecord.json")
        req (required-union doc)]
    (is (contains? req "physicallyVerified"))
    (let [types (known doc "interlockType")]
      (doseq [t ["estop" "over-pressure" "over-temperature" "gas-detection" "light-curtain" "loto"]]
        (is (contains? types t) (str "safety: interlockType must cover " t))))))

;; ── IO trust isolation ──
(deftest test-io-trust-class-isolates-safety
  (is (= (known (load-lex "ioPointRegistry.json") "trustClass")
         #{"attested" "untrusted-external" "safety-mirror-readonly"})))

;; ── attestation: every PLC program Council-attested + content-addressed ──
(deftest test-plc-program-attested-and-addressed
  (let [req (required-union (load-lex "plcProgramAttestation.json"))]
    (doseq [field ["councilAttestationRef" "programCid" "stSourceCid"]]
      (is (contains? req field) (str "attestation: plcProgramAttestation must require " field)))))

(deftest test-runtime-attestation-matches-attested-program
  (let [req (required-union (load-lex "runtimeAttestation.json"))]
    (doseq [field ["loadedProgramHash" "attestedProgramCid" "match"]]
      (is (contains? req field) (str "attestation: runtimeAttestation must require " field)))))

;; ── advisory-only actuation: bounded envelope ──
(deftest test-setpoint-envelope-is-bounded-with-rate-limit
  (let [doc (load-lex "setpointEnvelope.json")
        req (required-union doc)
        pkeys (all-property-keys doc)]
    (doseq [field ["minMilli" "maxMilli" "maxRateOfChangeMilli"]]
      (is (or (contains? req field) (contains? pkeys field))
          (str "safety: setpointEnvelope must carry " field)))))

;; ── G6 surveillance: telemetry declares person-attributability ──
(deftest test-telemetry-declares-person-attributability
  (is (contains? (required-union (load-lex "telemetryAggregateRecord.json")) "personAttributable")))
