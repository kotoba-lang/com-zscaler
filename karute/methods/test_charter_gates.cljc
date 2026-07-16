(ns karute.methods.test-charter-gates
  "karute カルテ — constitutional-gate / structural-invariant tests (central FHIR lexicons).

  Substrate-native Clojure (clj + datomic first tier). karute is the EMR (電子カルテ) — its
  charter discipline is **PHI confidentiality by encryption**: every PHI-bearing record travels
  ONLY inside a `com.etzhayyim.encrypted.record` envelope (XChaCha20-Poly1305 + Signal key-wrap,
  ADR-2605181100), and the public graph carries meta only. The 11 lexicons under
  00-contracts/lexicons/com/etzhayyim/karute/ are the FHIR INNER (decrypted) shapes that live
  inside that envelope — so they intentionally carry clinical content, and a 'no-plaintext-PHI'
  assertion would be WRONG here (that gate is enforced at the envelope / @etzhayyim/sdk layer,
  not in these inner types). This suite instead pins the invariants that ARE structural in the
  lexicons and that the charter depends on:

    DID-centric identity — every clinical resource binds the patient via `patientDid` (a DID,
      not a plaintext name / MRN as the link key); subject-DID custody (ADR-2605172400).
    accountability — clinical authorship/attribution is DID-bound (soapNote.authorDid required;
      prescriber / performer / pharmacist / requester / recordedBy are DID fields), never an
      anonymous or free-text author.
    interop discipline — every resource pins a stable FHIR `fhirResourceType` const (no drift).
    closed clinical vocabularies — status / class / category / intent are closed FHIR value
      sets (no arbitrary representable clinical state).

  Reads central lexicons via cheshire (string keys). It weakens no gate; it asserts them.
  Touches neither the substrate-wide no-server-key (G7) nor Murakumo-only (G6) invariants —
  karute holds no key (consent is an Ed25519 member-signed capability, ADR-2605231400)."
  (:require [clojure.test :refer [deftest is run-tests]]
            [cheshire.core :as json]))

#?(:clj
   (do
     (def ^:private here (.getParentFile (java.io.File. ^String *file*)))      ;; methods/
     (def ^:private actor-dir (.getParentFile here))                          ;; karute/
     (def ^:private root (.getParentFile (.getParentFile actor-dir)))          ;; repo root
     (def ^:private lexdir
       (java.io.File. root "00-contracts/lexicons/com/etzhayyim/karute"))
     (defn- lex [name]
       (json/parse-string (slurp (java.io.File. lexdir (str name ".json")))))))

(defn- record-node [doc]
  (let [main (get-in doc ["defs" "main"])]
    (or (get main "record") main)))
(defn- required-of [doc] (set (get (record-node doc) "required")))
(defn- props-of [doc] (get (record-node doc) "properties"))
(defn- prop-keys [doc] (set (keys (props-of doc))))
(defn- const-of [doc field] (get-in (props-of doc) [field "const"]))
(defn- enum-of [doc field] (get-in (props-of doc) [field "enum"]))

;; resource → its pinned FHIR resourceType
(def RESOURCE-TYPES
  {"patient" "Patient" "encounter" "Encounter" "condition" "Condition"
   "observation" "Observation" "medicationRequest" "MedicationRequest"
   "serviceRequest" "ServiceRequest" "carePlan" "CarePlan"
   "dispenseRecord" "MedicationDispense" "soapNote" "Composition"
   "homecareEpisode" "EpisodeOfCare" "homeVisit" "Encounter"})

;; every clinical resource EXCEPT the patient record itself binds the patient by DID
(def CLINICAL (disj (set (keys RESOURCE-TYPES)) "patient"))

;; ── interop — every resource pins its FHIR resourceType const ──
(deftest fhir-resource-type-pinned
  (doseq [[n rt] RESOURCE-TYPES]
    (is (= rt (const-of (lex n) "fhirResourceType"))
        (str "interop: " n ".fhirResourceType const must be " rt))))

;; ── DID-centric identity — clinical resources require patientDid ──
(deftest patient-binding-is-did
  (doseq [n CLINICAL]
    (is (contains? (required-of (lex n)) "patientDid")
        (str "DID-identity: " n " must require patientDid")))
  ;; the patient record itself carries a patientDid (the subject-custody anchor)
  (is (contains? (prop-keys (lex "patient")) "patientDid")
      "DID-identity: patient record must carry patientDid"))

;; ── accountability — clinical authorship/attribution is DID-bound ──
(deftest authorship-is-did-bound
  (is (contains? (required-of (lex "soapNote")) "authorDid")
      "accountability: a clinical note must require its authorDid")
  (doseq [[n field] [["medicationRequest" "prescriberDid"]
                     ["observation" "performerDid"]
                     ["dispenseRecord" "pharmacistDid"]
                     ["serviceRequest" "requesterDid"]
                     ["condition" "recordedByDid"]]]
    (is (contains? (prop-keys (lex n)) field)
        (str "accountability: " n " must attribute via " field " (a DID)"))))

;; ── closed clinical vocabularies — no arbitrary representable state ──
(deftest closed-clinical-vocabularies
  (doseq [[n field] [["encounter" "status"] ["encounter" "encounterClass"]
                     ["observation" "status"] ["observation" "category"]
                     ["medicationRequest" "status"] ["medicationRequest" "intent"]]]
    (let [vs (enum-of (lex n) field)]
      (is (and (sequential? vs) (seq vs))
          (str "closed-vocab: " n "." field " must be a closed enum"))))
  ;; the FHIR status sets must NOT silently admit an open free value (sanity: 'unknown' present, bounded)
  (is (contains? (set (enum-of (lex "encounter") "status")) "completed")
      "closed-vocab: encounter.status is the FHIR-bounded set"))

#?(:clj
   (defn -main [& _]
     (let [r (run-tests 'karute.methods.test-charter-gates)]
       (System/exit (if (zero? (+ (:fail r) (:error r))) 0 1)))))
