(ns kokoro.methods.test-charter-gates
  "kokoro 心 — constitutional-gate conformance tests (manifest + central lexicons).

  Substrate-native Clojure (clj + datomic first tier). kokoro is mental-health PEER + grief
  support — explicitly NOT a clinical psychiatric entity, NOT commercial therapy software, NOT
  an AI therapist. Its 14 gates are structural in the manifest + the 5 central AT-Proto lexicons
  at 00-contracts/lexicons/com/etzhayyim/kokoro/*.json. This suite pins them so a future R1 cell
  wave cannot silently drift them:

    G3  community-witnessed-competent (NOT a state-licensed psychiatric entity)
    G4  encrypted envelope MANDATORY + NO video recording
    G5/G6/G7/G8  silenKokoroReview negative-space battery — conversion-therapy / AI-only-therapy
        / commercial-MH-software / commercial-AI-chatbot penetration all const 0
    G9  opt-in only; NO mandatory screening (free conscience)
    G10 NO surveillance-based monitoring / mood-tracking
    G13 acute crisis escalates to mitate (G5 emergency keyword) under encrypted context
    G14 counselors are vocation-flow L5 stewards; NO payroll
    privacy — participants are rotating PSEUDONYM DIDs, never a stable id/name

  Reads central lexicons via cheshire (string keys). It weakens no gate; it asserts them.
  Touches neither the substrate-wide no-server-key (G7-substrate) nor Murakumo-only (G6-substrate)
  invariants — kokoro holds no key and is human-in-loop by construction."
  (:require [clojure.test :refer [deftest is run-tests]]
            [clojure.set :as set]
            [cheshire.core :as json]))

#?(:clj
   (do
     (def ^:private here (.getParentFile (java.io.File. ^String *file*)))      ;; methods/
     (def ^:private actor-dir (.getParentFile here))                          ;; kokoro/
     (def ^:private root (.getParentFile (.getParentFile actor-dir)))          ;; repo root
     (def ^:private lexdir
       (java.io.File. root "00-contracts/lexicons/com/etzhayyim/kokoro"))
     (defn- lex [name]
       (json/parse-string (slurp (java.io.File. lexdir (str name ".json")))))
     (defn- manifest []
       (:actor/manifest (clojure.edn/read-string (slurp (java.io.File. actor-dir "manifest.edn")))))))

;; ── navigation over an AT-Proto lexicon (string keys) ──
(defn- record-node [doc]
  (let [main (get-in doc ["defs" "main"])]
    (or (get main "record") main)))
(defn- required-of [doc] (set (get (record-node doc) "required")))
(defn- prop-keys [doc] (set (keys (get (record-node doc) "properties"))))

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

(def PAYROLL-TERMS #{"payroll" "wage" "salary" "bonus" "commission"})
(def STABLE-ID-FIELDS #{"memberDid" "memberName" "patientDid" "patientName"
                        "realName" "participantDids" "personDid"})

;; ── 14 gates declared ──
(deftest all-14-gates-declared
  (let [gates (set (keys (get-in (manifest) ["constitutionalGates" "gates"])))]
    (is (= gates (set (map #(str "G" %) (range 1 15))))
        "manifest must declare G1–G14")))

;; ── G3 — community-witnessed-competent, NOT a clinical psychiatric entity ──
(deftest g3-not-clinical-psychiatric-entity
  (is (= "community-witnessed-competent" (a-const (lex "counselorAttestation") "counselorClass"))
      "G3: counselorClass const community-witnessed-competent"))

;; ── G4 — encrypted envelope MANDATORY + NO video recording ──
(deftest g4-encrypted-no-video
  (doseq [n ["peerSupportCircleAttestation" "griefSupportAttestation"]]
    (is (contains? (required-of (lex n)) "encryptedPayloadCid")
        (str "G4: " n " must require encryptedPayloadCid"))
    (is (= false (a-const (lex n) "videoRecordingPresent"))
        (str "G4: " n ".videoRecordingPresent const false"))))

;; ── G9 opt-in only + G10 no surveillance monitoring ──
(deftest g9-g10-opt-in-no-surveillance
  (let [c (lex "peerSupportCircleAttestation")]
    (is (= true (a-const c "optInOnly")) "G9: optInOnly const true")
    (is (= false (a-const c "surveillanceBasedMonitoring"))
        "G10: surveillanceBasedMonitoring const false")))

;; ── G5/G6/G7/G8/G9/G10/G3 + G14 — silenKokoroReview negative-space battery ──
(deftest negative-space-const-zero-battery
  (let [r (lex "silenKokoroReview")]
    (doseq [[field gate] [["clinicalPsychiatricEntityPenetrationPct" "G3"]
                          ["videoRecordingEventsCount" "G4"]
                          ["conversionTherapyEventsCount" "G5"]
                          ["aiOnlyTherapyEventsCount" "G6"]
                          ["commercialMentalHealthSoftwarePenetrationPct" "G7"]
                          ["commercialAiTherapyChatbotPenetrationPct" "G8"]
                          ["mandatoryScreeningEventsCount" "G9"]
                          ["surveillanceBasedMoodMonitoringEventsCount" "G10"]]]
      (is (= 0 (a-const r field)) (str gate ": silenKokoroReview." field " const 0")))
    (is (= 10000 (a-const r "counselorVocationFlowCompliantRatioPctIntegerHundredths"))
        "G14: counselor vocation-flow compliance const 100.00%")))

;; ── G13 — acute crisis escalates to mitate (G5 emergency keyword), encrypted; consent/Council gated ──
(deftest g13-crisis-escalation-to-mitate
  (let [a (lex "acuteCrisisEscalationLog")]
    (is (contains? (required-of a) "mitateG5EmergencyKeywordTriggeredCid")
        "G13: crisis log must require the mitate G5 emergency-keyword trigger CID")
    (is (contains? (required-of a) "encryptedContextCid")
        "G13: crisis context must be encrypted")
    (is (contains? (prop-keys a) "councilLv6AttestationsForInvoluntary")
        "G13: involuntary intervention is Council-Lv6-gated")
    (is (contains? (prop-keys a) "memberConsentObtainedForVoluntary")
        "G13: voluntary path is member-consent gated")))

;; ── G14 — counselors are vocation-flow L5 stewards; no payroll term representable ──
(deftest g14-vocation-flow-no-payroll
  (let [c (lex "counselorAttestation")]
    (is (= "L5" (a-const c "lLevel")) "G14: counselorAttestation.lLevel const L5")
    (is (= "vocation-flow" (a-const c "employmentRelation"))
        "G14: counselorAttestation.employmentRelation const vocation-flow")
    (doseq [n ["counselorAttestation" "silenKokoroReview"]]
      (let [enums (->> (collect (lex n) "enum") vals (mapcat identity) (map str) set)]
        (is (empty? (set/intersection enums PAYROLL-TERMS))
            (str "G14: " n " must not make a payroll/wage term representable"))))))

;; ── privacy — participants are rotating PSEUDONYM DIDs, never a stable id/name ──
(deftest privacy-pseudonym-only
  (doseq [[n req] [["acuteCrisisEscalationLog" "affectedPseudonymDid"]
                   ["peerSupportCircleAttestation" "participantPseudonymDids"]
                   ["griefSupportAttestation" "bereavedPseudonymDid"]]]
    (is (contains? (required-of (lex n)) req)
        (str "privacy: " n " must require " req))
    (is (empty? (set/intersection (prop-keys (lex n)) STABLE-ID-FIELDS))
        (str "privacy: " n " must carry no stable patient/member identifier"))))

#?(:clj
   (defn -main [& _]
     (let [r (run-tests 'kokoro.methods.test-charter-gates)]
       (System/exit (if (zero? (+ (:fail r) (:error r))) 0 1)))))
