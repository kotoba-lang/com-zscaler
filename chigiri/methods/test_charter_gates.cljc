(ns chigiri.methods.test-charter-gates
  "chigiri — constitutional-gate conformance tests (manifest + central lexicons).
  Substrate-native Clojure (ADR-2606160842). 1:1 port of the pruned methods/test_charter_gates.py."
  (:require [clojure.test :refer [deftest is run-tests]]
            [clojure.set :as set]
            [cheshire.core :as json]))

(def ^:private here (.getParentFile (java.io.File. ^String *file*)))      ;; methods/
(def ^:private actor-dir (.getParentFile here))                          ;; chigiri/
(def ^:private actor-name (.getName actor-dir))
(def ^:private root (.. actor-dir getParentFile getParentFile))          ;; 20-actors → ROOT
(def ^:private lexdir (java.io.File. root (str "00-contracts/lexicons/com/etzhayyim/" actor-name)))
(defn- manifest [] (json/parse-string (slurp (java.io.File. actor-dir "manifest.jsonld"))))
(defn- lex [name] (json/parse-string (slurp (java.io.File. lexdir (str name ".json")))))

(defn- collect [doc attr]
  (let [acc (atom {})]
    (letfn [(walk [x parent]
              (cond (map? x) (do (when (and (string? parent) (contains? x attr))
                                   (swap! acc assoc parent (get x attr)))
                                 (doseq [[k v] x] (walk v k)))
                    (sequential? x) (doseq [v x] (walk v parent))))]
      (walk doc nil)) @acc))
(defn- a-const [doc field] (get (collect doc "const") field))
(defn- known [doc field] (some-> (get (collect doc "knownValues") field) set))
(defn- required-union [doc]
  (let [acc (atom #{})]
    (letfn [(walk [x] (cond (map? x) (do (when (sequential? (get x "required")) (swap! acc into (get x "required")))
                                         (doseq [v (vals x)] (walk v)))
                            (sequential? x) (doseq [v x] (walk v))))]
      (walk doc)) @acc))

;; ── full gate set (chigiri carries G1–G17) ──
(deftest test-all-17-gates-declared
  (is (= (set (keys (get-in (manifest) ["constitutionalGates" "gates"])))
         (set (map #(str "G" %) (range 1 18))))))

;; ── G14/G15 — UPL: zero compensation, Public-Fund retained, advice via human counsel ──
(deftest test-g15-legal-aid-zero-compensation
  (let [doc (lex "legalAidMatter")]
    (is (= true (a-const doc "zeroCompensation")))
    (is (= true (a-const doc "retainedViaPublicFund")))
    (let [req (required-union doc)]
      (doseq [field ["counselDid" "supervisingCounsel" "lane"]]
        (is (contains? req field))))
    (is (= #{"advice" "certified-mediation"} (known doc "lane")))))

;; ── G12 — excommunication due process (cure window + reversal needs fresh ceremony) ──
(deftest test-g12-excommunication-due-process
  (let [doc (lex "excommunicationProcedure")]
    (is (= true (a-const doc "reversalRequiresFreshCeremony")))
    (let [req (required-union doc)]
      (doseq [field ["cureWindowStartsAt" "curePeriodEndsAt" "evidenceAttestationCids"]]
        (is (contains? req field))))))

;; ── Just-War-gated defensive/deterrent force only (no offensive) ──
(deftest test-force-defensive-deterrent-only-with-just-war
  (let [doc (lex "forceAuthorizationRecord")]
    (is (= #{"defensive" "deterrent"} (known doc "posture")))
    (let [req (required-union doc)]
      (doseq [field ["justCause" "legitimateAuthority" "rightIntention" "lastResort"
                     "reasonableHopeOfSuccess" "proportionalityAdBellum" "proportionalityInBello"
                     "discriminationInBello" "ihlCompliance" "oneSbtOneVoteChainCid"]]
        (is (contains? req field))))))

;; ── jurisdiction-aware UPL boundary ──
(deftest test-jurisdiction-aware-upl
  (let [req (required-union (lex "jurisdictionPolicy"))]
    (doseq [field ["freeAdviceLawfulAlone" "regulatoryFamily" "restrictingStatute"]]
      (is (contains? req field))))
  (is (= #{"compensation" "licensure" "activity"} (known (lex "jurisdictionPolicy") "regulatoryFamily"))))

;; ── IP-license remedy ladder (graduated, not litigation-first) ──
(deftest test-ip-license-remedy-ladder
  (let [req (required-union (lex "ipLicenseClaim"))]
    (doseq [field ["violationTier" "remedyLadderTarget" "evidenceCids"]]
      (is (contains? req field)))))

;; ── withdrawal: member-signed + cooling period (no coercion) ──
(deftest test-withdrawal-member-signed-cooling
  (let [req (required-union (lex "withdrawalAttestation"))]
    (doseq [field ["memberSignature" "coolingPeriodEndsAt" "subjectDid"]]
      (is (contains? req field)))))

;; ── mediation claim summary is encrypted ──
(deftest test-mediation-claim-encrypted
  (is (contains? (required-union (lex "disputeMediation")) "claimSummaryEncryptedCid")))
