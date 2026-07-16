(ns mitate.methods.test-charter-gates
  "mitate 見立て — constitutional-gate conformance tests (manifest + central lexicons).

  Substrate-native Clojure (clj + datomic first tier). mitate is a DIAGNOSIS-ROUTING advisory
  substrate — explicitly advisory-only, disclaimer-gated, licensed-MD-in-loop for Rx-tier, and
  the shared emergency-escalation target for the L4 Care lineage (iyashi / kokoro G13). Its 14
  gates are declared in the manifest `constitutionalGates` and encoded structurally as `required`
  fields across the 9 central AT-Proto lexicons at 00-contracts/lexicons/com/etzhayyim/mitate/.
  This suite pins both so a future R1+ cell wave cannot silently drift them:

    G1  patient consent revocable + DID-bound (diagnosticConsentReceipt)
    G2  health data in an encrypted envelope only (diagnosticResult)
    G3  AI diagnosis = advisory only, disclaimer REQUIRED (triageVerdict / treatmentPlan)
    G4  R2+ licensed-MD-in-loop (diagnosticResult.physicianAttestorDid)
    G5  emergency-keyword fail-safe ER routing (emergencyEscalation)
    G9  training/design Council attestation (silenMitateReview)
    privacy — patient is a rotating PSEUDONYM DID, never a stable id/name

  Reads central lexicons via cheshire (string keys). It weakens no gate; it asserts them.
  Touches neither the substrate-wide no-server-key (G7-substrate) nor Murakumo-only (G6-substrate)
  invariants — mitate holds no key; its manifest G12 already pins Murakumo-only inference."
  (:require [clojure.test :refer [deftest is run-tests]]
            [clojure.set :as set]
            [clojure.string :as str]
            [cheshire.core :as json]))

#?(:clj
   (do
     (def ^:private here (.getParentFile (java.io.File. ^String *file*)))      ;; methods/
     (def ^:private actor-dir (.getParentFile here))                          ;; mitate/
     (def ^:private root (.getParentFile (.getParentFile actor-dir)))          ;; repo root
     (def ^:private lexdir
       (java.io.File. root "00-contracts/lexicons/com/etzhayyim/mitate"))
     (defn- lex [name]
       (json/parse-string (slurp (java.io.File. lexdir (str name ".json")))))
     (defn- manifest []
       (:actor/manifest (clojure.edn/read-string (slurp (java.io.File. actor-dir "manifest.edn")))))))

(defn- record-node [doc]
  (let [main (get-in doc ["defs" "main"])]
    (or (get main "record") main)))
(defn- required-of [doc] (set (get (record-node doc) "required")))
(defn- prop-keys [doc] (set (keys (get (record-node doc) "properties"))))

(def STABLE-ID-FIELDS #{"patientDid" "patientName" "memberDid" "memberName" "realName" "personDid"})

;; ── 14 gates declared (manifest dict; keys are like "G1_…", all true) ──
(deftest all-14-gates-declared-true
  (let [gates (get (manifest) "constitutionalGates")
        nums  (->> (keys gates)
                   (keep #(second (re-matches #"G(\d+)_.*" %)))
                   (map #(Integer/parseInt %))
                   set)]
    (is (= (set (range 1 15)) nums) "manifest must declare G1–G14")
    (is (every? true? (vals gates)) "every declared gate must be true")))

;; ── G1 — patient consent revocable + DID-bound + signed ──
(deftest g1-consent-revocable-did-bound
  (let [c (lex "diagnosticConsentReceipt")
        req (required-of c)]
    (doseq [f ["revocableUntilUtc" "consentingAdherentDid" "patientPseudonymDid" "digitalSignatureAlg"]]
      (is (contains? req f) (str "G1: diagnosticConsentReceipt must require " f)))))

;; ── G2 — health data only in an encrypted envelope ──
(deftest g2-encrypted-result-envelope
  (is (contains? (required-of (lex "diagnosticResult")) "encryptedResultEnvelope")
      "G2: diagnosticResult must require encryptedResultEnvelope"))

;; ── G3 — advisory only: disclaimer REQUIRED (and accepted, for a treatment plan) ──
(deftest g3-advisory-disclaimer-required
  (is (contains? (required-of (lex "triageVerdict")) "disclaimerText")
      "G3: triageVerdict must require disclaimerText")
  (let [tp (required-of (lex "treatmentPlan"))]
    (is (contains? tp "disclaimerText") "G3: treatmentPlan must require disclaimerText")
    (is (contains? tp "disclaimerAccepted") "G3: treatmentPlan must require disclaimerAccepted")))

;; ── G4 — R2+ licensed-MD-in-loop attests a diagnostic result ──
(deftest g4-licensed-md-in-loop
  (is (contains? (required-of (lex "diagnosticResult")) "physicianAttestorDid")
      "G4: diagnosticResult must require physicianAttestorDid"))

;; ── G5 — emergency-keyword fail-safe ER routing ──
(deftest g5-emergency-er-routing
  (let [e (required-of (lex "emergencyEscalation"))]
    (doseq [f ["redFlagCategory" "urgency" "erRoutingInstruction"]]
      (is (contains? e f) (str "G5: emergencyEscalation must require " f)))))

;; ── G9 — training/design intent under Council attestation review ──
(deftest g9-council-attested-review
  (let [r (required-of (lex "silenMitateReview"))]
    (is (contains? r "councilAttestors") "G9: silenMitateReview must require councilAttestors")
    (is (contains? r "riskClass") "G9: silenMitateReview must require riskClass")))

;; ── privacy — patient is a rotating PSEUDONYM DID, never a stable id/name ──
(deftest privacy-pseudonym-only
  (doseq [n ["triageVerdict" "diagnosticResult" "treatmentPlan" "diagnosticConsentReceipt"]]
    (is (contains? (required-of (lex n)) "patientPseudonymDid")
        (str "privacy: " n " must require patientPseudonymDid"))
    (is (empty? (set/intersection (prop-keys (lex n)) STABLE-ID-FIELDS))
        (str "privacy: " n " must carry no stable patient identifier"))))

#?(:clj
   (defn -main [& _]
     (let [r (run-tests 'mitate.methods.test-charter-gates)]
       (System/exit (if (zero? (+ (:fail r) (:error r))) 0 1)))))
