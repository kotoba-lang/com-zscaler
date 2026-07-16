(ns shomei.methods.claims
  "claims.cljc — 証明 (shomei) identityClaim structural model + gate enforcement. ADR-2606072100.
  1:1 Clojure port of `methods/claims.py`.

  Builds + validates a SELF-SOVEREIGN external-identity binding. The structural gates here are
  the Python mirror of the lexicon `:const`/`:enum`. Validation RAISES (ex-info) — never
  silently drops.

  Crypto: `external-subject-hash` = blake2b-256(salt ‖ \\x1f ‖ canonical-id) → base64url
  (no '='), via the hand-ported `shomei.methods.blake2b` (NOT a host blake2b). The canonical
  signing bytes are `json.dumps(payload, sort_keys=True, separators=(',',':'))` over the 8
  SIGNED_FIELDS — reproduced byte-for-byte by `canonical-claim-bytes`.

  House style: claim is a Clojure map with the Python string keys; closed-vocab keys are
  strings; pure fns; I/O-free."
  (:require [clojure.string :as str]
            [shomei.methods.blake2b :as b2]
            [shomei.methods.factors :as f]))

;; The exact fields the subject signs over (order-significant only via JSON sort_keys).
(def SIGNED_FIELDS
  ["subjectDid" "factorKind" "factorClass" "proofKind" "challengeNonce"
   "externalSubjectHash" "verified" "issuedAt"])

;; G7 no-server-key: a claim may NEVER carry a server signature.
(def FORBIDDEN_SERVER_FIELDS #{"serverSig" "platformSig" "operatorSig" "adminSig"})

;; ── base64url (RFC 4648 §5, no padding) ────────────────────────────────────────
(def ^:private B64URL
  "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_")

(defn- b64url-no-pad
  "urlsafe_b64encode(bytes).rstrip('=') — matches Python exactly."
  [^bytes data]
  (let [n (alength data)
        sb (StringBuilder.)]
    (loop [i 0]
      (when (< i n)
        (let [b0 (bit-and (long (aget data i)) 0xff)
              b1 (if (< (+ i 1) n) (bit-and (long (aget data (+ i 1))) 0xff) 0)
              b2 (if (< (+ i 2) n) (bit-and (long (aget data (+ i 2))) 0xff) 0)
              triple (bit-or (bit-shift-left b0 16) (bit-shift-left b1 8) b2)
              rem (- n i)]
          (.append sb (.charAt B64URL (bit-and (unsigned-bit-shift-right triple 18) 0x3f)))
          (.append sb (.charAt B64URL (bit-and (unsigned-bit-shift-right triple 12) 0x3f)))
          (when (>= rem 2)
            (.append sb (.charAt B64URL (bit-and (unsigned-bit-shift-right triple 6) 0x3f))))
          (when (>= rem 3)
            (.append sb (.charAt B64URL (bit-and triple 0x3f))))
          (recur (+ i 3)))))
    (.toString sb)))

(defn external-subject-hash
  "Blake2b-256(salt ‖ 0x1f ‖ canonical-identifier) → base64url (G3 privacy-preserving linkage).
  The identifier is .strip().lower()-normalised (case-insensitive, whitespace-trimmed)."
  [salt identifier]
  (let [norm (str/lower-case (str/trim (str identifier)))
        msg (str salt (char 0x1f) norm)
        digest (b2/digest-bytes #?(:clj (.getBytes ^String msg "UTF-8")
                                   :cljs (vec (map #(.charCodeAt % 0) msg))) 32)]
    (b64url-no-pad digest)))

;; ── compact sorted JSON for the canonical signing bytes ─────────────────────────
(defn- json-escape [s]
  (-> (str s)
      (str/replace "\\" "\\\\")
      (str/replace "\"" "\\\"")
      (str/replace "\n" "\\n")
      (str/replace "\t" "\\t")
      (str/replace "\r" "\\r")))

(defn- json-scalar [v]
  (cond
    (nil? v) "null"
    (true? v) "true"
    (false? v) "false"
    (string? v) (str "\"" (json-escape v) "\"")
    (integer? v) (str v)
    :else (str "\"" (json-escape (str v)) "\"")))

(defn canonical-claim-bytes
  "Deterministic message the subject's Ed25519 key signs (G7: only the subject).
  json.dumps({k:claim[k] for k in SIGNED_FIELDS}, sort_keys=True, separators=(',',':'))."
  [claim]
  (let [pairs (->> SIGNED_FIELDS
                   (map (fn [k] [k (get claim k)]))
                   (sort-by first))           ;; sort_keys=True
        body (str/join "," (map (fn [[k v]] (str "\"" (json-escape k) "\":" (json-scalar v))) pairs))
        s (str "{" body "}")]
    #?(:clj (.getBytes ^String s "UTF-8") :cljs s)))

(declare validate-claim)

(defn build-claim
  [{:keys [subject-did factor-kind proof-kind challenge-nonce external-subject-hash
           issued-at subject-sig verified external-handle encrypted-payload-cid verifier-did]
    :or {verified false}}]
  (let [claim (cond-> {"subjectDid" subject-did
                       "factorKind" factor-kind
                       "factorClass" (f/factor-class factor-kind)
                       "proofKind" proof-kind
                       "challengeNonce" challenge-nonce
                       "externalSubjectHash" external-subject-hash
                       "verified" (boolean verified)
                       "issuedAt" (long issued-at)
                       "subjectSig" subject-sig}
                (some? external-handle) (assoc "externalHandle" external-handle)
                (some? encrypted-payload-cid) (assoc "encryptedPayloadCid" encrypted-payload-cid)
                (some? verifier-did) (assoc "verifierDid" verifier-did))]
    (validate-claim claim)
    claim))

(defn- non-blank? [v] (and (some? v) (not= v "")))

(defn validate-claim
  "Structural gate enforcement. Raises ex-info on any violation. Returns claim on success."
  [claim]
  ;; G7 — no server-held signature field may exist.
  (let [bad (clojure.set/intersection FORBIDDEN_SERVER_FIELDS (set (keys claim)))]
    (when (seq bad)
      (throw (ex-info (str "G7 no-server-key: forbidden server signature field(s) " (vec (sort bad))) {}))))
  (let [required ["subjectDid" "factorKind" "factorClass" "proofKind" "challengeNonce"
                  "externalSubjectHash" "verified" "issuedAt" "subjectSig"]
        missing (vec (remove #(contains? claim %) required))]
    (when (seq missing)
      (throw (ex-info (str "identityClaim missing required field(s): " missing) {}))))
  (let [did (get claim "subjectDid")]
    (when-not (and (string? did) (str/starts-with? did "did:"))
      (throw (ex-info (str "G1/G2: subjectDid must be a DID, got " (pr-str did)) {}))))
  (let [kind (get claim "factorKind")]
    (when-not (contains? f/FACTOR_KINDS kind)
      (throw (ex-info (str "unknown factorKind: " (pr-str kind)) {})))
    (when-not (some #{(get claim "factorClass")} f/CLASSES)
      (throw (ex-info (str "unknown factorClass: " (pr-str (get claim "factorClass"))) {})))
    (when-not (= (get claim "factorClass") (get f/FACTOR_CLASS kind))
      (throw (ex-info (str "factorClass " (pr-str (get claim "factorClass"))
                           " != canonical " (pr-str (get f/FACTOR_CLASS kind)) " for " (pr-str kind)) {})))
    (let [proof (get claim "proofKind")]
      (when-not (contains? (get f/ALLOWED_PROOFS kind) proof)
        (throw (ex-info (str "G4: proofKind " (pr-str proof) " not allowed for " (pr-str kind)
                             " (allowed " (vec (sort (get f/ALLOWED_PROOFS kind))) ")") {}))))
    (when-not (and (string? (get claim "challengeNonce")) (seq (get claim "challengeNonce")))
      (throw (ex-info "challengeNonce must be a non-empty string (anti-replay)" {})))
    (when-not (and (string? (get claim "externalSubjectHash")) (seq (get claim "externalSubjectHash")))
      (throw (ex-info "externalSubjectHash must be a non-empty string" {})))
    (when-not (boolean? (get claim "verified"))
      (throw (ex-info "verified must be a boolean" {})))
    (when-not (integer? (get claim "issuedAt"))
      (throw (ex-info "issuedAt must be an integer unix timestamp" {})))
    (when-not (and (string? (get claim "subjectSig")) (seq (get claim "subjectSig")))
      (throw (ex-info "G7: subjectSig (subject-signed) is mandatory" {})))
    ;; G3 PII-never-plaintext — government factors.
    (let [has-handle (non-blank? (get claim "externalHandle"))
          has-cid (non-blank? (get claim "encryptedPayloadCid"))]
      (if (contains? f/GOV_FACTORS kind)
        (do
          (when has-handle
            (throw (ex-info (str "G3: gov factor " (pr-str kind) " MUST NOT carry a plaintext externalHandle (PII)") {})))
          (when-not has-cid
            (throw (ex-info (str "G3: gov factor " (pr-str kind) " MUST carry an encryptedPayloadCid (XChaCha20, ADR-2605181100)") {}))))
        (when (and has-handle (not (contains? f/PUBLIC_HANDLE_FACTORS kind)))
          (throw (ex-info (str "G3: factor " (pr-str kind) " may not carry a plaintext externalHandle "
                               "(only public wallet/sns factors may)") {}))))))
  claim)
