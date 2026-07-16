(ns shomei.methods.factors
  "factors.cljc — 証明 (shomei) factor taxonomy + assurance ladder (SSoT). ADR-2606072100.
  1:1 Clojure port of `methods/factors.py`.

  Every external-identity factor maps to exactly one independence CLASS; the Identity
  Assurance Level (IAL) is a pure function of the SET of verified factor classes — never of
  the person (G8). Closed-vocab keys are STRINGS (the Python factorKind/factorClass strings),
  per the root CLAUDE.md convention. Pure fns; closed-vocab violations → ex-info.

  ::order metadata pins FACTOR_CLASS / ALLOWED_PROOFS insertion order so any derived listing
  matches the Python dict-iteration order byte-for-byte (>8 entries)."
  (:require [clojure.string :as str]
            [clojure.set :as set]))

;; factorKind → factorClass (independence class). G8: assurance counts DISTINCT classes,
;; so two key-class wallets (EVM + BTC) raise count but not class-diversity.
(def FACTOR_CLASS
  ^{::order ["webauthn" "wallet-evm" "wallet-btc" "sns-github" "sns-x" "sns-google" "sns-apple"
             "gov-mynumber" "gov-passport" "gov-license"
             "etz-base-membership" "etz-adherent-sbt" "etz-at-oath"]}
  {"webauthn" "device"
   "wallet-evm" "key"
   "wallet-btc" "key"
   "sns-github" "social"
   "sns-x" "social"
   "sns-google" "social"
   "sns-apple" "social"
   "gov-mynumber" "government"
   "gov-passport" "government"
   "gov-license" "government"
   "etz-base-membership" "covenant"
   "etz-adherent-sbt" "covenant"
   "etz-at-oath" "covenant"})

(def CLASSES ["device" "key" "social" "government" "covenant"])

;; factorKind → allowed proofKinds (G4 cryptographic-proof-mandatory).
(def ALLOWED_PROOFS
  ^{::order ["webauthn" "wallet-evm" "wallet-btc" "sns-github" "sns-x" "sns-google" "sns-apple"
             "gov-mynumber" "gov-passport" "gov-license"
             "etz-base-membership" "etz-adherent-sbt" "etz-at-oath"]}
  {"webauthn" #{"webauthn-assertion"}
   "wallet-evm" #{"eip191" "eip1271"}
   "wallet-btc" #{"bip322"}
   "sns-github" #{"oauth-sub" "signed-gist" "dns-txt"}
   "sns-x" #{"oauth-sub"}
   "sns-google" #{"oauth-sub"}
   "sns-apple" #{"oauth-sub"}
   "gov-mynumber" #{"nfc-jpki"}
   "gov-passport" #{"nfc-jpki"}
   "gov-license" #{"nfc-jpki"}
   "etz-base-membership" #{"base-l2-event"}
   "etz-adherent-sbt" #{"erc5192-sbt"}
   "etz-at-oath" #{"at-record-sig"}})

(def FACTOR_KINDS (set (keys FACTOR_CLASS)))
(def PROOF_KINDS (set (mapcat identity (vals ALLOWED_PROOFS))))

;; G3: government factors carry NO plaintext identifier; encryptedPayloadCid is mandatory.
(def GOV_FACTORS (set (for [[k c] FACTOR_CLASS :when (= c "government")] k)))
(def COVENANT_FACTORS (set (for [[k c] FACTOR_CLASS :when (= c "covenant")] k)))
;; Only inherently-public, pseudonymous factors may carry a plaintext externalHandle.
(def PUBLIC_HANDLE_FACTORS
  (set (for [k (keys FACTOR_CLASS)
             :when (or (str/starts-with? k "wallet-") (str/starts-with? k "sns-"))] k)))
;; G11: gov L2 proof is Council-gated (ADR-2605260000); the R0 cell .solve() raises.
(def GATED_PROOFS #{"nfc-jpki"})

(def REVOCATION_REASONS
  #{"key-rotated" "key-lost" "account-closed" "compromised" "superseded" "voluntary"})

(defn factor-class [kind]
  (if-not (contains? FACTOR_CLASS kind)
    (throw (ex-info (str "unknown factorKind: " (pr-str kind)) {:kind kind}))
    (get FACTOR_CLASS kind)))

(defn assurance-level
  "Identity Assurance Level from the SET of verified factor classes + factor count.

  0 did-only · 1 self-attested (≥1 factor) · 2 multi-factor (≥2 factors, ≥2 classes) ·
  3 covenant-bound (IAL2 + a covenant etzhayyim factor) · 4 government-verified
  (a gov factor paired with ≥1 other class; Council-attested, ADR-2605260000)."
  [classes count]
  (let [n (clojure.core/count classes)]
    (cond
      (zero? count) 0
      (and (contains? classes "government") (>= n 2)) 4
      (and (contains? classes "covenant") (>= n 2) (>= count 2)) 3
      (and (>= n 2) (>= count 2)) 2
      :else 1)))

(defn proof-of-personhood
  "Sybil-RESISTANCE (not sybil-proof): ≥2 independent classes. Never a person ranking."
  [level n-classes]
  (and (>= level 2) (>= n-classes 2)))

(defn step-up-requirement
  "What a member must still ADD to raise their Identity Assurance Level to `target-ial`, given the SET
  of factor classes they have already verified and their factor count — the constructive inverse of
  `assurance-level`. This is identity-strengthening GUIDANCE (the factor PROOF still required), never
  a worth / behavior / reputation score (G8 — it measures what's needed for identity proof, not the
  person). Returns {:current-ial :target-ial :met? :additional-factors :distinct-classes-needed
  :missing-classes :note}: :additional-factors / :distinct-classes-needed are how many more verified
  factors / distinct classes are still required, and :missing-classes names the specific class still
  needed (a covenant factor for IAL-3, a government factor for IAL-4). The IAL-4 :note flags that the
  government leg is Council-attested — surfaced, not self-reachable."
  [classes count target-ial]
  (let [cs (set classes)
        cur (assurance-level cs count)
        n (clojure.core/count cs)
        t (long target-ial)
        met? (>= cur t)
        absent (fn [k] (when-not (contains? cs k) k))
        named-missing (vec (keep identity (case t 3 [(absent "covenant")] 4 [(absent "government")] [])))
        diversity-need (if (or met? (< t 2)) 0 (max 0 (- 2 n)))]
    {:current-ial cur
     :target-ial t
     :met? met?
     :additional-factors (if met? 0 (case t 0 0, 1 (max 0 (- 1 count)), (max 0 (- 2 count))))
     :distinct-classes-needed (if met? 0 (max diversity-need (clojure.core/count named-missing)))
     :missing-classes named-missing
     :note (case t
             4 "a government factor + ≥1 other class — Council-attested, not self-reachable"
             3 "IAL-2 (≥2 factors across ≥2 classes) plus a covenant factor"
             2 "≥2 verified factors across ≥2 distinct classes"
             1 "any one verified factor"
             0 "no factors needed"
             "target IAL must be 0..4")}))
