(ns shomei.methods.aggregate
  "aggregate.cljc — 証明 (shomei) personhoodCredential aggregation. ADR-2606072100.
  1:1 Clojure port of `methods/aggregate.py`.

  Rolls a DID's VERIFIED, ACTIVE identityClaims into an Identity Assurance Level +
  proof-of-personhood + a W3C-Verifiable-Credential-shaped record. G3: no PII (kinds + counts
  + DID hash only). G8: identity-assurance, NEVER a social-credit / worth / reputation score.

  did-hash = blake2b-256(did) → base64url (hand-ported blake2b, NOT a host blake2b).
  Credential maps carry ::order metadata so any JSON serialization matches the Python
  json.dumps key order byte-for-byte."
  (:require [clojure.string :as str]
            [shomei.methods.blake2b :as b2]
            [shomei.methods.claims :as claims]
            [shomei.methods.factors :as f]))

(def W3C_VC_CONTEXT
  ["https://www.w3.org/2018/credentials/v1"
   "https://etzhayyim.com/ns/shomei/v1"])

(def COUNCIL_ATTESTOR_DID "did:web:etzhayyim.com:council:attestor")  ;; IAL-4 issuer (gated)

(defn did-hash [did]
  (let [digest (b2/digest-bytes #?(:clj (.getBytes ^String did "UTF-8")
                                   :cljs (vec (map #(.charCodeAt % 0) did))) 32)]
    (#'claims/b64url-no-pad digest)))

(defn aggregate
  "Build a personhoodCredential from the SET of distinct verified+active factorKinds."
  [subject-did active-verified-factors & {:keys [issued-at expiration-date]}]
  (let [factors (vec (sort (filter #(contains? f/FACTOR_CLASS %) active-verified-factors)))
        classes (set (map #(get f/FACTOR_CLASS %) factors))
        count* (clojure.core/count factors)
        level (f/assurance-level classes count*)
        pop (f/proof-of-personhood level (clojure.core/count classes))
        issuer (if (<= level 3) subject-did COUNCIL_ATTESTOR_DID)
        cred (cond-> ^{::order ["subjectDidHash" "assuranceLevel" "verifiedFactors"
                                "distinctClasses" "factorCount" "proofOfPersonhood"
                                "issuer" "issuanceDate" "expirationDate"]}
                     {"subjectDidHash" (did-hash subject-did)
                      "assuranceLevel" level
                      "verifiedFactors" factors
                      "distinctClasses" (clojure.core/count classes)
                      "factorCount" count*
                      "proofOfPersonhood" pop
                      "issuer" issuer
                      "issuanceDate" (long issued-at)}
               (some? expiration-date) (assoc "expirationDate" (long expiration-date)))]
    cred))

(defn to-w3c-vc
  "Render the credential as a W3C VC data-model JSON-LD object for presentation (G3: no PII)."
  [subject-did cred]
  (let [subject ^{::order ["id" "assuranceLevel" "verifiedFactors" "distinctClasses"
                           "factorCount" "proofOfPersonhood"]}
                {"id" subject-did
                 "assuranceLevel" (get cred "assuranceLevel")
                 "verifiedFactors" (get cred "verifiedFactors")
                 "distinctClasses" (get cred "distinctClasses")
                 "factorCount" (get cred "factorCount")
                 "proofOfPersonhood" (get cred "proofOfPersonhood")}
        vc (cond-> ^{::order ["@context" "type" "issuer" "issuanceDate" "credentialSubject"
                              "expirationDate"]}
                   {"@context" W3C_VC_CONTEXT
                    "type" ["VerifiableCredential" "EtzhayyimPersonhoodCredential"]
                    "issuer" (get cred "issuer")
                    "issuanceDate" (get cred "issuanceDate")
                    "credentialSubject" subject}
             (contains? cred "expirationDate") (assoc "expirationDate" (get cred "expirationDate")))]
    vc))

(defn assurance-label [level]
  (get {0 "did-only"
        1 "self-attested"
        2 "multi-factor"
        3 "covenant-bound"
        4 "government-verified"} level))

(defn is-covenant-bound [active-verified-factors]
  (boolean (seq (clojure.set/intersection (set active-verified-factors) f/COVENANT_FACTORS))))
