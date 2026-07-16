(ns iyashi.methods.test-charter-gates
  "iyashi 癒 — constitutional-gate conformance tests (manifest + central lexicons).

  Substrate-native Clojure (clj + datomic first tier). iyashi is an R0 scaffold (no cells yet),
  so its charter discipline lives entirely in the manifest + the 7 central AT-Proto lexicons at
  00-contracts/lexicons/com/etzhayyim/iyashi/*.json. This suite locks the structurally-encoded
  gates so a future R1 cell wave cannot silently drift them:

    G2  encrypted-envelope MANDATORY + NO plaintext clinical content field
        (clinical PHI is the most sensitive observation class — structural, not policy)
    G4  per-encounter consent (consentRecordCid required, default-deny)
    privacy — patient identity is a rotating PSEUDONYM DID, never a stable id/name
    G5  provider Council vetting (medical-license-cite + Council attestations required)
    G11 NO commercial EHR (silenIyashiReview.commercialEhrPenetrationBps const 0)
    G14 NO payroll — providers are vocation-flow L5 stewards (lLevel/employmentRelation const)

  Reads central lexicons via cheshire (string keys). It weakens no gate; it asserts them.
  None of these touch the substrate-wide no-server-key (G7) or Murakumo-only (G6) invariants —
  iyashi holds no key and records AI-assist as an audited, human-in-loop run (G8/G12)."
  (:require [clojure.test :refer [deftest is run-tests]]
            [cheshire.core :as json]))

#?(:clj
   (do
     (def ^:private here (.getParentFile (java.io.File. ^String *file*)))      ;; methods/
     (def ^:private actor-dir (.getParentFile here))                          ;; iyashi/
     (def ^:private root (.getParentFile (.getParentFile actor-dir)))          ;; repo root
     (def ^:private lexdir
       (java.io.File. root "00-contracts/lexicons/com/etzhayyim/iyashi"))
     (defn- lex [name]
       (json/parse-string (slurp (java.io.File. lexdir (str name ".json")))))
     (defn- manifest []
       (:actor/manifest (clojure.edn/read-string (slurp (java.io.File. actor-dir "manifest.edn")))))))

;; ── navigation over an AT-Proto lexicon (string keys) ──
(defn- record-node [doc]
  (let [main (get-in doc ["defs" "main"])]
    (or (get main "record") main)))
(defn- required-of [doc] (set (get (record-node doc) "required")))
(defn- props-of [doc] (get (record-node doc) "properties"))
(defn- prop-keys [doc] (set (keys (props-of doc))))

;; collect {property-name → attr-value} anywhere in the lexicon tree (for const/enum checks)
(defn- collect [doc attr]
  (let [acc (atom {})]
    (letfn [(walk [x parent]
              (cond
                (map? x) (do (when (and (string? parent) (contains? x attr))
                               (swap! acc assoc parent (get x attr)))
                             (doseq [[k v] x] (walk v k)))
                (sequential? x) (doseq [v x] (walk v parent))
                :else nil))]
      (walk doc nil))
    @acc))
(defn- a-const [doc field] (get (collect doc "const") field))

;; clinical free-text fields that MUST NOT be representable in plaintext (G2)
(def PLAINTEXT-CONTENT
  #{"note" "notes" "clinicalNote" "text" "content" "freeText" "diagnosis"
    "symptoms" "assessment" "plan" "chiefComplaint" "history" "body" "payload"})

(def PAYROLL-TERMS #{"payroll" "wage" "salary" "bonus" "commission"})

;; ── 14 gates declared ──
(deftest all-14-gates-declared
  (let [gates (set (keys (get-in (manifest) ["constitutionalGates" "gates"])))]
    (is (= gates (set (map #(str "G" %) (range 1 15))))
        "manifest must declare G1–G14")))

;; ── G2 — encrypted envelope MANDATORY; no plaintext clinical content ──
(deftest g2-encrypted-envelope-required
  (doseq [n ["clinicalEncounterAttestation" "chronicCareContinuityRecord"]]
    (is (contains? (required-of (lex n)) "encryptedPayloadCid")
        (str "G2: " n " must require encryptedPayloadCid"))))

(deftest g2-no-plaintext-content-field
  (doseq [n ["clinicalEncounterAttestation" "chronicCareContinuityRecord"]]
    (let [leaked (clojure.set/intersection (prop-keys (lex n)) PLAINTEXT-CONTENT)]
      (is (empty? leaked)
          (str "G2: " n " must carry no plaintext clinical content field, found " leaked)))))

;; ── G4 — per-encounter consent (default-deny): consentRecordCid required ──
(deftest g4-consent-required
  (doseq [n ["clinicalEncounterAttestation" "chronicCareContinuityRecord"]]
    (is (contains? (required-of (lex n)) "consentRecordCid")
        (str "G4: " n " must require consentRecordCid"))))

;; ── privacy — patient is a rotating PSEUDONYM DID, never a stable id/name ──
(deftest privacy-patient-pseudonym-only
  (let [e (lex "clinicalEncounterAttestation")]
    (is (contains? (required-of e) "patientPseudonymDid")
        "privacy: encounter must require patientPseudonymDid")
    (is (empty? (clojure.set/intersection (prop-keys e) #{"patientDid" "patientName" "patientId"}))
        "privacy: no stable patient identifier representable")))

;; ── G5 — provider Council vetting: license-cite + Council attestations required ──
(deftest g5-provider-vetting-required
  (let [p (lex "providerAttestation")]
    (is (contains? (required-of p) "medicalLicenseCiteCid") "G5: provider license-cite required")
    (is (contains? (required-of p) "councilAttestations") "G5: Council attestations required")))

;; ── G11 — NO commercial EHR ──
(deftest g11-no-commercial-ehr
  (is (= 0 (a-const (lex "silenIyashiReview") "commercialEhrPenetrationBps"))
      "G11: silenIyashiReview.commercialEhrPenetrationBps const 0"))

;; ── G14 — NO payroll: providers are vocation-flow L5 stewards ──
(deftest g14-vocation-flow-no-payroll
  (let [p (lex "providerAttestation")]
    (is (= "L5" (a-const p "lLevel")) "G14: providerAttestation.lLevel const L5")
    (is (= "vocation-flow" (a-const p "employmentRelation"))
        "G14: providerAttestation.employmentRelation const vocation-flow")
    ;; no payroll/wage/salary term representable as an enum value across the lexicons
    (doseq [n ["providerAttestation" "clinicalEncounterAttestation" "silenIyashiReview"]]
      (let [enums (->> (collect (lex n) "enum") vals (mapcat identity) (map str) set)]
        (is (empty? (clojure.set/intersection enums PAYROLL-TERMS))
            (str "G14: " n " must not make a payroll/wage term representable"))))))

#?(:clj
   (defn -main [& _]
     (let [r (run-tests 'iyashi.methods.test-charter-gates)]
       (System/exit (if (zero? (+ (:fail r) (:error r))) 0 1)))))
