(ns shomei.methods.verify
  "verify.cljc — 証明 (shomei) VERIFY WIRING. ADR-2606072100.
  1:1 Clojure port of `methods/verify.py`.

  Routes each `proofKind` to the canonical kotoba-auth verification surface (PROOF_ROUTING)
  and enforces the verification POLICY (challenge binding, single-use nonce, freshness, gov
  gate, subject-signature) in pure Clojure. The cryptographic signature math itself lives in
  kotoba-auth (never reimplemented here).

  A `SignatureVerifier` is injected as a map:
    {:kind :kotoba-auth}                          — production: each path raises with the
                                                     EXACT canonical call to wire at R1.
    {:kind :reference :secrets {[did kind] secret} :allow-gated bool}
                                                   — hermetic HMAC-SHA256 test double.
  HMAC-SHA256 uses the host (javax.crypto.Mac 'HmacSHA256') at the :clj edge — a :cljs/wasm
  impl can slot a portable HMAC here. `hmac.compare_digest` → `ct-equal` (constant-time).

  Closed-vocab / gate violations → ex-info; GatedError → an ex-info tagged :shomei/gated."
  (:require [clojure.string :as str]
            [shomei.methods.claims :as claims]
            [shomei.methods.factors :as f]))

;; ── The wiring table (proofKind → canonical kotoba-auth verification call) ──────────────
(def PROOF_ROUTING
  {"eip191" (str "kotoba_auth::eth::recover_eth_address("
                 "kotoba_auth::eth::personal_sign_hash(siwe_msg), sig) "
                 "== kotoba_auth::eth::parse_address(claimed_addr)")
   "eip1271" (str "kotoba_auth::cacao::DelegationChain::verify_signature_eip191_smart(rpc)  "
                  "# ERC-1271/4337 smart-wallet (ECDSA recover fails → isValidSignature)")
   "bip322" (str "kotoba_auth::btc::verify_message(siwe_msg, sig, "
                 "kotoba_auth::btc::address::BtcAddress::parse(claimed_addr))  # legacy signmessage R0")
   "oauth-sub" (str "OIDC id_token verify against provider JWKS; bind `sub` + `nonce` claim "
                    "(google/apple/github IdP); shomei stores only blake2b(salt||provider:sub)")
   "dns-txt" (str "resolve TXT _etzhayyim-shomei.<domain> == proof token over nonce; "
                  "host-controlled domain attests SNS/site ownership")
   "signed-gist" (str "fetch member-hosted gist/file at the SNS handle; must contain subjectDid + nonce "
                      "(GitHub/X profile-link gist convention)")
   "webauthn-assertion" (str "WebAuthn P-256 ECDSA assertion verify over (clientDataJSON||challenge) "
                             "(ADR-2605260000 §3 gov_auth)")
   "nfc-jpki" (str "JPKI X.509 chain validate + local one-way NFC read (ADR-2605260000 Trust L2, "
                   "Council-gated); plaintext encrypted XChaCha20 before substrate (G3/G6)")
   "base-l2-event" "Base L2 read: EtzhayyimMembership Joined(subjectDid) event present (ADR-2605172600)"
   "erc5192-sbt" (str "geth-private read: AdherentRegistry ERC-5192 ownerOf(tokenForSubject)==subject "
                      "(ADR-2605172700)")
   "at-record-sig" "AT oath record Ed25519 sig verify (com.etzhayyim.apps.etzhayyim.oath, ADR-2605172600)"})

(defn gated-error?
  "True if `ex` is the shomei Council-gated-proof signal (the GatedError analogue)."
  [ex]
  (and (instance? clojure.lang.ExceptionInfo ex) (= :shomei/gated (:type (ex-data ex)))))

;; ── HMAC-SHA256 (host) + constant-time compare (hmac.compare_digest) ────────────
#?(:clj
   (defn- hmac-sha256-hex [^String secret ^bytes msg]
     (let [mac (javax.crypto.Mac/getInstance "HmacSHA256")
           key (javax.crypto.spec.SecretKeySpec. (.getBytes secret "UTF-8") "HmacSHA256")]
       (.init mac key)
       (let [^bytes out (.doFinal mac msg)
             sb (StringBuilder.)]
         (dotimes [i (alength out)]
           (let [v (bit-and (long (aget out i)) 0xff)
                 s (.toString (java.math.BigInteger/valueOf v) 16)]
             (when (< v 16) (.append sb "0"))
             (.append sb s)))
         (.toString sb)))))

(defn- ct-equal
  "Constant-time string compare — the hmac.compare_digest analogue (length-leaking only)."
  [^String a ^String b]
  (let [a (str a) b (str b)]
    (if (not= (count a) (count b))
      false
      (loop [i 0 acc 0]
        (if (< i (count a))
          (recur (inc i) (bit-or acc (bit-xor (int (.charAt a i)) (int (.charAt b i)))))
          (zero? acc))))))

;; ── verifier dispatch ───────────────────────────────────────────────────────────
(defn reference-verifier
  "Hermetic test double. 'Control' = knowing a per-(subject,factor) secret.
  proof_material['proof'] must equal HMAC(secret, nonce ‖ '|' ‖ externalSubjectHash);
  subjectSig must equal HMAC(secret, canonical_claim_bytes)."
  ([secrets] (reference-verifier secrets false))
  ([secrets allow-gated]
   {:kind :reference :secrets secrets :allow-gated allow-gated}))

(defn kotoba-auth-verifier [] {:kind :kotoba-auth})

(defn- ref-tok [secret ^bytes msg] (hmac-sha256-hex secret msg))

(defn verify-proof [verifier proof-kind claim proof-material]
  (case (:kind verifier)
    :kotoba-auth
    (throw (ex-info (str "KotobaAuthVerifier.verify_proof(" (pr-str proof-kind) "): wire to → "
                         (get PROOF_ROUTING proof-kind "<unknown>"))
                    {:type :not-implemented}))
    :reference
    (let [k [(get claim "subjectDid") (get claim "factorKind")]
          secret (get (:secrets verifier) k)]
      (if (nil? secret)
        false
        (let [msg (str (get claim "challengeNonce") "|" (get claim "externalSubjectHash"))]
          (ct-equal (ref-tok secret #?(:clj (.getBytes ^String msg "UTF-8") :cljs msg))
                    (get proof-material "proof" "")))))))

(defn verify-subject-sig [verifier claim]
  (case (:kind verifier)
    :kotoba-auth
    (throw (ex-info (str "KotobaAuthVerifier.verify_subject_sig: wire to → kotoba-auth Ed25519 "
                         "verify over claims.canonical_claim_bytes(claim) with subjectDid's signing key")
                    {:type :not-implemented}))
    :reference
    (let [k [(get claim "subjectDid") (get claim "factorKind")]
          secret (get (:secrets verifier) k)]
      (if (nil? secret)
        false
        (ct-equal (ref-tok secret (claims/canonical-claim-bytes claim))
                  (get claim "subjectSig"))))))

(defn- fail [claim reason]
  {"verified" false
   "factorKind" (get claim "factorKind")
   "factorClass" (get claim "factorClass")
   "reason" reason})

(defn verify-claim
  "Run the full verification POLICY for one claim. Returns a result map
  {verified factorKind factorClass reason}. Marks the nonce consumed in `seen-nonces` (an atom
  holding a set — the mutable single-use ledger). Raises an :shomei/gated ex-info on a gov gate.

  Policy: structural gate (claims/validate-claim) → challenge binding (subject + factor + nonce)
  → freshness (now ≤ expiresAt) → single-use → gov-gate (nfc-jpki Council-gated at R0) → crypto
  proof → subject Ed25519 signature."
  [claim challenge verifier & {:keys [proof-material now seen-nonces]}]
  (let [proof-material (or proof-material {})
        seen-nonces (or seen-nonces (atom #{}))]
    (claims/validate-claim claim)            ;; structural gates G1/G2/G3/G4/G7
    (cond
      (not= (get challenge "subjectDid") (get claim "subjectDid"))
      (fail claim "challenge subjectDid mismatch")
      (not= (get challenge "factorKind") (get claim "factorKind"))
      (fail claim "challenge factorKind mismatch")
      (not= (get challenge "nonce") (get claim "challengeNonce"))
      (fail claim "challenge nonce mismatch")
      (> (long now) (long (get challenge "expiresAt" 0)))
      (fail claim "challenge expired")
      (contains? @seen-nonces (get claim "challengeNonce"))
      (fail claim "nonce already consumed (replay)")
      :else
      (do
        ;; G11 gov gate — Council-gated proofs raise at R0 (ADR-2605260000).
        (when (and (contains? f/GATED_PROOFS (get claim "proofKind"))
                   (not (:allow-gated verifier)))
          (throw (ex-info (str "proofKind " (pr-str (get claim "proofKind")) " (gov L2) is Council-gated; "
                               "R0 shomei_gov_attest cell .solve() raises (ADR-2605260000 R1 activation required)")
                          {:type :shomei/gated})))
        (let [proof-ok (verify-proof verifier (get claim "proofKind") claim proof-material)]
          (if-not proof-ok
            (fail claim "proof verification failed")
            (let [sig-ok (verify-subject-sig verifier claim)]
              (if-not sig-ok
                (fail claim "subject signature invalid")
                (do
                  (swap! seen-nonces conj (get claim "challengeNonce"))  ;; single-use consumption
                  {"verified" true
                   "factorKind" (get claim "factorKind")
                   "factorClass" (get claim "factorClass")
                   "reason" "ok"})))))))))
