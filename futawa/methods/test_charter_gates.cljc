(ns futawa.methods.test-charter-gates
  "futawa 二輪 — constitutional-gate conformance tests (manifest + local lexicons).

  Substrate-native Clojure (clj + datomic first tier). futawa builds small-displacement
  motorcycles (≤250cc / ≤15kW) with several CONSTITUTIONAL-FIRST gates: no built-in
  surveillance/telematics (G8), KPI caps (G11), mandatory ABS (G7), right-to-repair
  forward-publishing (G12), 30-year service life (G14). Its 14 gates are declared in the
  manifest and the per-step evidence/compliance flags are encoded across the 8 first-tier
  `lex/*.edn` lexicons. This suite pins them so a future R-phase cell wave cannot silently
  drift them:

    G8  NO GPS tracker / telematics at build — electricalAttestation carries g8SurveillanceClear;
        the gate text still forbids GPS-tracker + always-on telematics (doc-drift guard)
    G11 KPI caps — engineAttestation requires displacementCc + powerKw + a g11CapVerified flag;
        the cap VALUES (250cc / 15kW) are still pinned in the gate text
    G7  ABS mandatory — testRecord.absPass + electricalAttestation.absEcuCalibration recordable
    G6  sound ≤80 dB — testRecord.soundDb recordable; the 80 dB limit still pinned
    G12 right-to-repair — vehicleLotAttestation REQUIRES a partsCatalogCid (+ diagnostic + CAD)
    G13 circular feed — vehicleLotAttestation carries hodokiPreregisterOk + recycledMassCertCid
    G10 dive/build review Council-gated — silenMobilityReview requires councilApproval

  Reads manifest via cheshire + local lexicons via clojure.edn. It weakens no gate; it asserts
  them — the cap-value checks are a doc-drift guard. No-server-key + Murakumo-only (G9) untouched."
  (:require [clojure.test :refer [deftest is run-tests]]
            [clojure.edn :as edn]
            [clojure.string :as str]
            [cheshire.core :as json]))

#?(:clj
   (do
     (def ^:private here (.getParentFile (java.io.File. ^String *file*)))      ;; methods/
     (def ^:private actor-dir (.getParentFile here))                          ;; futawa/
     (def ^:private lexdir (java.io.File. actor-dir "lex"))
     (defn- unblob
       "lex/*.edn are now Datomic/Datascript tx-data (edn-datomize wrap-map, per-file namespace on
       the bare :lexicon/:id/:defs keys); non-scalar values (here: :defs) are pr-str blob strings.
       Parse back to the original nested value where possible."
       [v]
       (if (string? v)
         (try (let [parsed (edn/read-string v)] (if (coll? parsed) parsed v))
              (catch Exception _ v))
         v))
     (defn- reconstitute-entity
       "tx-data [{:db/id -1 :<ns>/lexicon 1 :<ns>/id \"...\" :<ns>/defs \"...blob...\"}] -> the
       original bare {:lexicon 1 :id \"...\" :defs {...}} map so get-in-based readers below are
       unchanged."
       [tx-data]
       (into {} (map (fn [[k v]] [(keyword (name k)) (unblob v)]))
             (dissoc (first tx-data) :db/id)))
     (defn- lex [name]
       (reconstitute-entity (edn/read-string (slurp (java.io.File. lexdir (str name ".edn"))))))
     (defn- manifest []
       (:actor/manifest (clojure.edn/read-string (slurp (java.io.File. actor-dir "manifest.edn")))))))

(defn- record-node [doc] (get-in doc [:defs :main :record]))
(defn- required-of [doc] (set (:required (record-node doc))))
(defn- prop-names [doc] (set (map name (keys (:properties (record-node doc))))))
(defn- gate-map []
  (let [cg (get (manifest) "constitutionalGates")] (or (get cg "gates") cg)))
(defn- gate-text [g] (str/lower-case (str (get (gate-map) g))))

;; ── 14 gates + non-goals declared ──
(deftest gates-and-nongoals-declared
  (let [gn (->> (keys (gate-map)) (keep #(second (re-matches #"G(\d+).*" %)))
                (map #(Integer/parseInt %)) set)]
    (is (= (set (range 1 15)) gn) "manifest must declare G1–G14")
    (is (contains? (manifest) "nonGoals") "manifest must declare nonGoals")))

;; ── G8 — NO surveillance/telematics at build ──
(deftest g8-no-surveillance-telematics
  (is (contains? (prop-names (lex "electricalAttestation")) "g8SurveillanceClear")
      "G8: electricalAttestation must record a g8SurveillanceClear flag")
  (let [t (gate-text "G8")]
    (is (str/includes? t "gps tracker") "G8: gate text must still forbid a GPS tracker")
    (is (str/includes? t "telematics") "G8: gate text must still forbid always-on telematics")))

;; ── G11 — KPI caps (≤250cc / ≤15kW), recorded + verified ──
(deftest g11-kpi-caps
  (let [e (lex "engineAttestation")]
    (is (every? (required-of e) ["displacementCc" "powerKw"])
        "G11: engine attestation must record displacement + power")
    (is (contains? (prop-names e) "g11CapVerified")
        "G11: engine attestation must carry a g11CapVerified flag"))
  (let [t (gate-text "G11")]
    (is (str/includes? t "250cc") "G11: cap text must still pin 250cc")
    (is (str/includes? t "15kw") "G11: cap text must still pin 15kW")))

;; ── G7 — ABS mandatory ──
(deftest g7-abs-mandatory
  (is (contains? (prop-names (lex "testRecord")) "absPass")
      "G7: testRecord must record absPass")
  (is (contains? (prop-names (lex "electricalAttestation")) "absEcuCalibration")
      "G7: electrical attestation must record ABS ECU calibration"))

;; ── G6 — sound ≤80 dB ──
(deftest g6-sound-limit
  (is (contains? (prop-names (lex "testRecord")) "soundDb")
      "G6: testRecord must record soundDb")
  (is (str/includes? (gate-text "G6") "80 db") "G6: gate text must still pin the 80 dB limit"))

;; ── G12 — right-to-repair forward-publishing (+ G14 30-year) ──
(deftest g12-right-to-repair
  (let [v (lex "vehicleLotAttestation")]
    (is (contains? (required-of v) "partsCatalogCid")
        "G12: every vehicle lot must ship a parts catalog (right-to-repair)")
    (is (every? (prop-names v) ["diagnosticProtocolCid" "cadCatalogCid"])
        "G12: open diagnostic protocol + CAD catalog recordable"))
  (is (str/includes? (gate-text "G14") "30") "G14: gate text must still pin the 30-year minimum"))

;; ── G13 — circular feed: hodoki pre-register + recycled-mass cert ──
(deftest g13-circular-feed
  (let [v (prop-names (lex "vehicleLotAttestation"))]
    (is (contains? v "hodokiPreregisterOk") "G13: VIN pre-registered with hodoki")
    (is (contains? v "recycledMassCertCid") "G13: recycled-mass certificate recordable")))

;; ── G10 — build/test review Council-gated ──
(deftest g10-council-gated
  (let [s (lex "silenMobilityReview")]
    (is (contains? (required-of s) "councilApproval") "G10: review must require councilApproval")
    (is (contains? (prop-names s) "councilSig") "G10: review carries a Council signature")))

#?(:clj
   (defn -main [& _]
     (let [r (run-tests 'futawa.methods.test-charter-gates)]
       (System/exit (if (zero? (+ (:fail r) (:error r))) 0 1)))))
