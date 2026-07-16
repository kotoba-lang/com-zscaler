(ns iryo.methods.test-handoff
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.set :as set]
            [iryo.methods.handoff :as handoff])
  (:import [java.security KeyPairGenerator Signature]
           [java.util Base64]))

(def NOW "2026-07-08T00:00:00Z")

(defn- valid-request []
  {"patientDid" "did:web:patient.iryo.etzhayyim.com:e2e1"
   "encounterDid" "at://did:web:karute.etzhayyim.com/com.etzhayyim.karute.encounter/enc1"
   "facilityDid" "did:web:clinic-example.etzhayyim.com"
   "serviceRequestUris" ["at://did:web:karute.etzhayyim.com/com.etzhayyim.karute.serviceRequest/sr1"]
   "medicationRequestUris" ["at://did:web:karute.etzhayyim.com/com.etzhayyim.karute.medicationRequest/mr1"]
   "consentCapabilityUri" "at://did:web:patient.iryo.etzhayyim.com:e2e1/com.etzhayyim.consent.capability/cap1"})

(defn- valid-capability []
  {"granterDid" "did:web:patient.iryo.etzhayyim.com:e2e1"
   "granteeDid" handoff/iryo-did
   "purpose" "insurance-billing"
   "scope" ["com.etzhayyim.karute.encounter" "com.etzhayyim.karute.serviceRequest" "com.etzhayyim.karute.medicationRequest"]
   "resourceUris" []
   "issuedAt" "2026-06-01T00:00:00Z"
   "expiresAt" "2026-08-01T00:00:00Z"})

;; ── Happy path ───────────────────────────────────────────────────────────────

(deftest test-valid-handoff-is-accepted-into-draft-queue
  (let [out (handoff/handle-ingest (assoc (valid-request) "capability" (valid-capability) "now" NOW))]
    (is (= true (get out "ack")))
    (is (= "pending" (get out "iryoStatus")))
    (is (.startsWith (str (get out "iryoClaimRef")) "iryo-req-"))
    (is (nil? (get out "error")))))

(deftest test-claim-ref-is-deterministic-for-same-request
  (let [state (assoc (valid-request) "capability" (valid-capability) "now" NOW)
        a (handoff/handle-ingest state)
        b (handoff/handle-ingest state)]
    (is (= (get a "iryoClaimRef") (get b "iryoClaimRef")))))

;; ── PHI-free intake gate (G2) ────────────────────────────────────────────────

(deftest test-smuggled-plaintext-field-is-rejected
  (let [bad (assoc (valid-request) "patientName" "山田太郎")
        out (handoff/handle-ingest (assoc bad "capability" (valid-capability) "now" NOW))]
    (is (= false (get out "ack")))
    (is (= "needs-info" (get out "iryoStatus")))
    (is (.contains (str (get out "error")) "unexpected field"))))

(deftest test-patient-did-must-be-a-did
  (let [bad (assoc (valid-request) "patientDid" "not-a-did")
        out (handoff/handle-ingest (assoc bad "capability" (valid-capability) "now" NOW))]
    (is (= false (get out "ack")))
    (is (= "needs-info" (get out "iryoStatus")))
    (is (.contains (str (get out "error")) "is not a DID"))))

(deftest test-service-request-uri-must-be-at-uri
  (let [bad (assoc (valid-request) "serviceRequestUris" ["not-an-at-uri"])
        out (handoff/handle-ingest (assoc bad "capability" (valid-capability) "now" NOW))]
    (is (= false (get out "ack")))
    (is (= "needs-info" (get out "iryoStatus")))
    (is (.contains (str (get out "error")) "not an AT-URI"))))

(deftest test-non-ascii-did-shaped-value-is-rejected-as-smuggled-phi
  ;; passes the "did:" prefix check syntactically but carries a kanji name —
  ;; the ASCII-only defense-in-depth must still catch it (G2 fail-closed).
  (let [bad (assoc (valid-request) "facilityDid" "did:web:患者クリニック.example")
        out (handoff/handle-ingest (assoc bad "capability" (valid-capability) "now" NOW))]
    (is (= false (get out "ack")))
    (is (= "needs-info" (get out "iryoStatus")))
    (is (.contains (str (get out "error")) "non-ASCII"))))

;; ── PHI-free intake gate: additional boundary/edge cases (health-check pass,
;; 2026-07-08) — ascii-only? / check-ascii! is called on EVERY field this
;; boundary structurally checks, not just facilityDid; these tests spread the
;; same non-ASCII coverage to the other fields to pin that the defense-in-depth
;; check is genuinely uniform, plus lock in blank-string handling that was
;; previously only exercised incidentally (never directly asserted on).

(deftest test-blank-encounter-did-is-rejected-with-its-own-message
  ;; encounterDid has a DEDICATED blank check (distinct from the generic
  ;; "not a DID/AT-URI" prefix check below it) — an empty string is ASCII-only
  ;; and would otherwise slip past check-ascii!, so this pins that the blank
  ;; check still fires with its own specific message.
  (let [bad (assoc (valid-request) "encounterDid" "")
        out (handoff/handle-ingest (assoc bad "capability" (valid-capability) "now" NOW))]
    (is (= false (get out "ack")))
    (is (= "needs-info" (get out "iryoStatus")))
    (is (.contains (str (get out "error")) "encounterDid is blank"))))

(deftest test-blank-consent-capability-uri-value-is-a-format-failure-not-a-missing-key-failure
  ;; A PRESENT-BUT-EMPTY consentCapabilityUri is a structurally different case
  ;; from an ABSENT key (test-consent-capability-uri-missing-is-rejected below,
  ;; which dissoc's the key entirely so it is nil and assert-request-phi-free!
  ;; never inspects it via when-let). An empty string IS truthy for when-let,
  ;; so it reaches assert-request-phi-free!'s "at://" prefix check FIRST and
  ;; fails there with a format error — capability-gate's dedicated
  ;; "missing consentCapabilityUri" business message is never reached. This
  ;; pins that distinction (both fail closed, but via different gates/messages).
  (let [bad (assoc (valid-request) "consentCapabilityUri" "")
        out (handoff/handle-ingest (assoc bad "capability" (valid-capability) "now" NOW))]
    (is (= false (get out "ack")))
    (is (= "needs-info" (get out "iryoStatus")))
    (is (.contains (str (get out "error")) "is not an AT-URI"))
    (is (not (.contains (str (get out "error")) "missing consentCapabilityUri")))))

(deftest test-non-ascii-in-encounter-did-is-rejected
  (let [bad (assoc (valid-request) "encounterDid" "at://did:web:karute.etzhayyim.com/com.etzhayyim.karute.encounter/患者1")
        out (handoff/handle-ingest (assoc bad "capability" (valid-capability) "now" NOW))]
    (is (= false (get out "ack")))
    (is (.contains (str (get out "error")) "non-ASCII"))))

(deftest test-non-ascii-in-consent-capability-uri-is-rejected
  (let [bad (assoc (valid-request) "consentCapabilityUri" "at://did:web:patient.iryo.etzhayyim.com:e2e1/com.etzhayyim.consent.capability/患者1")
        out (handoff/handle-ingest (assoc bad "capability" (valid-capability) "now" NOW))]
    (is (= false (get out "ack")))
    (is (.contains (str (get out "error")) "non-ASCII"))))

(deftest test-non-ascii-in-resource-uri-arrays-is-rejected
  (testing "serviceRequestUris"
    (let [bad (assoc (valid-request) "serviceRequestUris" ["at://did:web:karute.etzhayyim.com/com.etzhayyim.karute.serviceRequest/患者1"])
          out (handoff/handle-ingest (assoc bad "capability" (valid-capability) "now" NOW))]
      (is (= false (get out "ack")))
      (is (.contains (str (get out "error")) "non-ASCII"))))
  (testing "medicationRequestUris"
    (let [bad (assoc (valid-request) "medicationRequestUris" ["at://did:web:karute.etzhayyim.com/com.etzhayyim.karute.medicationRequest/患者1"])
          out (handoff/handle-ingest (assoc bad "capability" (valid-capability) "now" NOW))]
      (is (= false (get out "ack")))
      (is (.contains (str (get out "error")) "non-ASCII")))))

(deftest test-extremely-long-but-well-formed-identifiers-are-not-falsely-rejected
  ;; Defense-in-depth (ascii-only? / allow-list) must not degrade or false-positive
  ;; at scale — a long-but-legitimate DID-shaped identifier (e.g. a long content
  ;; hash suffix) should still be accepted, and a long identifier with ONE
  ;; smuggled non-ASCII character buried near the end should still be caught.
  (let [long-suffix (apply str (repeat 5000 \a))
        req (-> (valid-request)
                (assoc "patientDid" (str "did:web:patient.iryo.etzhayyim.com:" long-suffix))
                (assoc "consentCapabilityUri" (str "at://did:web:patient.iryo.etzhayyim.com:" long-suffix "/com.etzhayyim.consent.capability/cap1")))
        cap (assoc (valid-capability) "granterDid" (str "did:web:patient.iryo.etzhayyim.com:" long-suffix))
        out (handoff/handle-ingest (assoc req "capability" cap "now" NOW))]
    (is (= true (get out "ack")))
    (is (= "pending" (get out "iryoStatus"))))
  (let [long-suffix (str (apply str (repeat 5000 \a)) "患")
        bad (assoc (valid-request) "patientDid" (str "did:web:patient.iryo.etzhayyim.com:" long-suffix))
        out (handoff/handle-ingest (assoc bad "capability" (valid-capability) "now" NOW))]
    (is (= false (get out "ack")))
    (is (.contains (str (get out "error")) "non-ASCII"))))

;; ── Consent-capability structural gate (G1/G7) ──────────────────────────────

(deftest test-missing-capability-is-rejected
  (let [out (handoff/handle-ingest (assoc (valid-request) "now" NOW))]
    (is (= false (get out "ack")))
    (is (= "needs-info" (get out "iryoStatus")))
    (is (.contains (str (get out "error")) "no consent capability"))))

(deftest test-wrong-purpose-is-rejected
  (let [cap (assoc (valid-capability) "purpose" "second-opinion")
        out (handoff/handle-ingest (assoc (valid-request) "capability" cap "now" NOW))]
    (is (= false (get out "ack")))
    (is (.contains (str (get out "error")) "purpose"))))

(deftest test-wrong-grantee-is-rejected
  (let [cap (assoc (valid-capability) "granteeDid" "did:web:some-other-vendor.example")
        out (handoff/handle-ingest (assoc (valid-request) "capability" cap "now" NOW))]
    (is (= false (get out "ack")))
    (is (.contains (str (get out "error")) "granteeDid"))))

(deftest test-granter-patient-mismatch-is-rejected
  (let [cap (assoc (valid-capability) "granterDid" "did:web:patient.iryo.etzhayyim.com:someone-else")
        out (handoff/handle-ingest (assoc (valid-request) "capability" cap "now" NOW))]
    (is (= false (get out "ack")))
    (is (.contains (str (get out "error")) "granterDid"))))

(deftest test-revoked-capability-is-rejected
  (let [cap (assoc (valid-capability) "revokedAt" "2026-07-01T00:00:00Z")
        out (handoff/handle-ingest (assoc (valid-request) "capability" cap "now" NOW))]
    (is (= false (get out "ack")))
    (is (.contains (str (get out "error")) "revoked"))))

(deftest test-expired-capability-is-rejected
  (let [cap (assoc (valid-capability) "expiresAt" "2026-07-01T00:00:00Z")
        out (handoff/handle-ingest (assoc (valid-request) "capability" cap "now" NOW))]
    (is (= false (get out "ack")))
    (is (.contains (str (get out "error")) "expired"))))

(deftest test-insufficient-scope-is-rejected
  (let [cap (assoc (valid-capability) "scope" ["com.etzhayyim.karute.encounter"])
        out (handoff/handle-ingest (assoc (valid-request) "capability" cap "now" NOW))]
    (is (= false (get out "ack")))
    (is (.contains (str (get out "error")) "scope"))))

(deftest test-resource-uri-outside-allowlist-is-rejected
  (let [cap (assoc (valid-capability) "resourceUris" ["at://did:web:karute.etzhayyim.com/com.etzhayyim.karute.serviceRequest/some-other-sr"])
        out (handoff/handle-ingest (assoc (valid-request) "capability" cap "now" NOW))]
    (is (= false (get out "ack")))
    (is (.contains (str (get out "error")) "resourceUris allowlist"))))

(deftest test-resource-uri-allowlist-permits-listed-uris
  (let [req (valid-request)
        cap (assoc (valid-capability) "resourceUris"
                   (vec (concat (get req "serviceRequestUris") (get req "medicationRequestUris"))))
        out (handoff/handle-ingest (assoc req "capability" cap "now" NOW))]
    (is (= true (get out "ack")))
    (is (= "pending" (get out "iryoStatus")))))

;; ── consentCapabilityUri structural self-consistency (NEW, this iteration) ──
;; Pure string-parse checks — no network I/O, no PDS fetch. These close the
;; STRUCTURAL half of "PDS/AT-URI resolution" (karute/MATURITY.md #11(b)):
;; given an already-resolved capability, verify it is even self-consistent
;; with the URI the wire request names it by.

(deftest test-parse-at-uri-parses-well-formed-uris
  (is (= {:did "did:web:patient.iryo.etzhayyim.com:e2e1"
          :collection "com.etzhayyim.consent.capability"
          :rkey "cap1"}
         (handoff/parse-at-uri "at://did:web:patient.iryo.etzhayyim.com:e2e1/com.etzhayyim.consent.capability/cap1")))
  (testing "did segment colons don't confuse the '/'-delimited split"
    (is (= "did:web:patient.iryo.etzhayyim.com:e2e1"
           (:did (handoff/parse-at-uri "at://did:web:patient.iryo.etzhayyim.com:e2e1/com.etzhayyim.consent.capability/cap1"))))))

(deftest test-parse-at-uri-rejects-malformed-uris
  (is (nil? (handoff/parse-at-uri "not-an-at-uri")))
  (is (nil? (handoff/parse-at-uri "at://did:web:x/only-collection")) "missing rkey segment")
  (is (nil? (handoff/parse-at-uri "at://did:web:x/collection/rkey/extra")) "too many segments")
  (is (nil? (handoff/parse-at-uri "at:///collection/rkey")) "blank did segment")
  (is (nil? (handoff/parse-at-uri "at://did:web:x//rkey")) "blank collection segment")
  (is (nil? (handoff/parse-at-uri "at://did:web:x/collection/")) "blank rkey segment (trailing slash)")
  (is (nil? (handoff/parse-at-uri "")) "empty string")
  (is (nil? (handoff/parse-at-uri nil))))

(deftest test-consent-capability-uri-missing-is-rejected
  (let [bad (dissoc (valid-request) "consentCapabilityUri")
        out (handoff/handle-ingest (assoc bad "capability" (valid-capability) "now" NOW))]
    (is (= false (get out "ack")))
    (is (= "needs-info" (get out "iryoStatus")))
    (is (.contains (str (get out "error")) "missing consentCapabilityUri"))))

(deftest test-consent-capability-uri-malformed-at-uri-is-rejected
  ;; starts with "at://" (passes the PHI-gate's prefix check) but doesn't
  ;; structurally decompose into exactly 3 segments.
  (let [bad (assoc (valid-request) "consentCapabilityUri" "at://did:web:patient.iryo.etzhayyim.com:e2e1/onlyonepart")
        out (handoff/handle-ingest (assoc bad "capability" (valid-capability) "now" NOW))]
    (is (= false (get out "ack")))
    (is (= "needs-info" (get out "iryoStatus")))
    (is (.contains (str (get out "error")) "does not parse as a well-formed AT-URI"))))

(deftest test-consent-capability-uri-wrong-collection-is-rejected
  (let [bad (assoc (valid-request) "consentCapabilityUri" "at://did:web:patient.iryo.etzhayyim.com:e2e1/com.etzhayyim.karute.encounter/cap1")
        out (handoff/handle-ingest (assoc bad "capability" (valid-capability) "now" NOW))]
    (is (= false (get out "ack")))
    (is (.contains (str (get out "error")) "collection is not com.etzhayyim.consent.capability"))))

(deftest test-consent-capability-uri-did-mismatch-is-rejected
  ;; The core value of this check: patientDid still matches capability's
  ;; granterDid (so the EARLIER granterDid/patientDid check passes cleanly),
  ;; but consentCapabilityUri itself names a DIFFERENT repo DID than the
  ;; capability it is supposed to resolve to — a substituted/mismatched
  ;; capability record that the pre-existing checks alone would not catch.
  (let [bad (assoc (valid-request) "consentCapabilityUri"
                    "at://did:web:someone-else.etzhayyim.com/com.etzhayyim.consent.capability/cap1")
        out (handoff/handle-ingest (assoc bad "capability" (valid-capability) "now" NOW))]
    (is (= false (get out "ack")))
    (is (= "needs-info" (get out "iryoStatus")))
    (is (.contains (str (get out "error")) "does not match the resolved capability's granterDid"))))

;; ── Gate priority / ordering when multiple gates fail at once (health-check
;; pass, 2026-07-08) ──────────────────────────────────────────────────────────
;; `handle-ingest` runs assert-request-phi-free! (G2) BEFORE capability-gate
;; (G1/G7), and capability-gate's own internal `cond` short-circuits on its
;; first failing clause. Neither ordering was previously pinned by a test —
;; doing so documents, for operators/debuggers reading `"error"`, WHICH
;; failure reason they should expect to see first when a request is broken in
;; more than one way at once (this is a description of existing `cond`/`try`
;; structure, not a new behavior).

(deftest test-phi-gate-failure-is-reported-before-any-capability-gate-failure
  ;; Construct a request that fails BOTH G2 (smuggled plaintext field) AND
  ;; G1/G7 (capability purpose wrong + revoked + expired, all at once) — the
  ;; PHI-free structural gate runs first in `handle-ingest`, so its message
  ;; must win regardless of how broken the capability also is.
  (let [bad-request (assoc (valid-request) "patientName" "山田太郎")
        broken-cap (assoc (valid-capability)
                           "purpose" "second-opinion"
                           "revokedAt" "2026-07-01T00:00:00Z"
                           "expiresAt" "2020-01-01T00:00:00Z")
        out (handoff/handle-ingest (assoc bad-request "capability" broken-cap "now" NOW))]
    (is (= false (get out "ack")))
    (is (= "needs-info" (get out "iryoStatus")))
    (is (.contains (str (get out "error")) "unexpected field"))
    (is (not (.contains (str (get out "error")) "purpose")))
    (is (not (.contains (str (get out "error")) "revoked")))))

(deftest test-capability-gate-reports-purpose-failure-before-revocation-or-expiry
  ;; Within capability-gate's own cond, purpose is checked before
  ;; revokedAt/expiresAt — when a capability is wrong in all three ways at
  ;; once, the purpose reason is what `handle-ingest` returns.
  (let [broken-cap (assoc (valid-capability)
                          "purpose" "second-opinion"
                          "revokedAt" "2026-07-01T00:00:00Z"
                          "expiresAt" "2020-01-01T00:00:00Z")
        out (handoff/handle-ingest (assoc (valid-request) "capability" broken-cap "now" NOW))]
    (is (= false (get out "ack")))
    (is (.contains (str (get out "error")) "purpose"))
    (is (not (.contains (str (get out "error")) "revoked")))
    (is (not (.contains (str (get out "error")) "expired")))))

;; ── G5 non-adjudicating: iryo's own intake gate never adjudicates ──────────

(deftest test-iryo-status-is-never-an-adjudication-verdict
  (testing "success and every gate-failure path only ever return pending/needs-info,
            never accepted/rejected — those verdicts belong to the 審査支払機関"
    (let [scenarios [(assoc (valid-request) "capability" (valid-capability) "now" NOW)
                     (assoc (valid-request) "now" NOW)
                     (assoc (valid-request) "capability" (assoc (valid-capability) "purpose" "second-opinion") "now" NOW)
                     (assoc (valid-request) "capability" (assoc (valid-capability) "revokedAt" "2026-07-01T00:00:00Z") "now" NOW)
                     (assoc (assoc (valid-request) "patientName" "山田太郎") "capability" (valid-capability) "now" NOW)]
          statuses (set (map #(get (handoff/handle-ingest %) "iryoStatus") scenarios))]
      (is (set/subset? statuses #{"pending" "needs-info"})))))

;; ── Ed25519 signature verification gate (karute/MATURITY.md #8) ────────────
;; Full real crypto roundtrip (no mocking) — a real JDK Ed25519 keypair signs
;; the exact bytes `handoff/canonicalize-capability-payload` produces, and
;; `handoff/handle-ingest` verifies it via `handoff/signature-gate`.

(defn- gen-keypair []
  (let [kp (.generateKeyPair (KeyPairGenerator/getInstance "Ed25519"))
        pub (.getPublic kp)
        priv (.getPrivate kp)
        pub32 (byte-array (take-last 32 (seq (.getEncoded pub))))]
    {:private priv :pub32 pub32}))

(defn- sign-b64 [private-key ^bytes message-bytes]
  (let [s (doto (Signature/getInstance "Ed25519")
            (.initSign private-key)
            (.update message-bytes))]
    (.encodeToString (Base64/getEncoder) (.sign s))))

(defn- b64 [^bytes bytes]
  (.encodeToString (Base64/getEncoder) bytes))

(defn- sign-capability
  "Real Ed25519 sign of `cap` (a capability map WITHOUT \"signature\") with
  `private-key`, using the exact same canonicalization `signature-gate`
  verifies against. Returns cap with a real \"signature\" map attached."
  [cap private-key key-id]
  (let [payload-bytes (.getBytes (handoff/canonicalize-capability-payload cap) "UTF-8")]
    (assoc cap "signature" {"alg" "ed25519" "value" (sign-b64 private-key payload-bytes) "keyId" key-id})))

(deftest test-valid-ed25519-signature-is-accepted
  (let [{:keys [private pub32]} (gen-keypair)
        signed-cap (sign-capability (valid-capability) private "did:web:patient.iryo.etzhayyim.com:e2e1#key-1")
        out (handoff/handle-ingest (assoc (valid-request) "capability" signed-cap
                                          "granterPublicKey" (b64 pub32) "now" NOW))]
    (is (= true (get out "ack")))
    (is (= "pending" (get out "iryoStatus")))
    (is (nil? (get out "error")))))

(deftest test-tampered-capability-payload-fails-signature-verification
  ;; sign the real capability, then tamper a field AFTER signing that the
  ;; structural gate (capability-gate) does NOT itself check (issuedAt) — so
  ;; this isolates a pure signature-verification failure from a structural
  ;; gate failure. The signature no longer covers the (tampered) payload.
  (let [{:keys [private pub32]} (gen-keypair)
        signed-cap (sign-capability (valid-capability) private "did:web:patient.iryo.etzhayyim.com:e2e1#key-1")
        tampered-cap (assoc signed-cap "issuedAt" "2020-01-01T00:00:00Z")
        out (handoff/handle-ingest (assoc (valid-request) "capability" tampered-cap
                                          "granterPublicKey" (b64 pub32) "now" NOW))]
    (is (= false (get out "ack")))
    (is (= "needs-info" (get out "iryoStatus")))
    (is (.contains (str (get out "error")) "signature does not verify"))))

(deftest test-wrong-public-key-fails-signature-verification
  (let [{:keys [private]} (gen-keypair)
        {other-pub32 :pub32} (gen-keypair)
        signed-cap (sign-capability (valid-capability) private "did:web:patient.iryo.etzhayyim.com:e2e1#key-1")
        out (handoff/handle-ingest (assoc (valid-request) "capability" signed-cap
                                          "granterPublicKey" (b64 other-pub32) "now" NOW))]
    (is (= false (get out "ack")))
    (is (.contains (str (get out "error")) "signature does not verify"))))

(deftest test-missing-signature-with-public-key-supplied-is-rejected
  (let [{:keys [pub32]} (gen-keypair)
        out (handoff/handle-ingest (assoc (valid-request) "capability" (valid-capability)
                                          "granterPublicKey" (b64 pub32) "now" NOW))]
    (is (= false (get out "ack")))
    (is (.contains (str (get out "error")) "no signature to verify"))))

(deftest test-wrong-alg-is-rejected-when-public-key-supplied
  (let [{:keys [pub32]} (gen-keypair)
        cap (assoc (valid-capability) "signature" {"alg" "secp256k1" "value" "AAAA" "keyId" "k1"})
        out (handoff/handle-ingest (assoc (valid-request) "capability" cap
                                          "granterPublicKey" (b64 pub32) "now" NOW))]
    (is (= false (get out "ack")))
    (is (.contains (str (get out "error")) "not ed25519"))))

(deftest test-signature-verification-is-skipped-when-no-public-key-supplied
  ;; Backward compatibility: existing/未signed capabilities still pass the
  ;; structural gate when the caller has not (yet) resolved a granter public
  ;; key — the common case, since resolving it is still cross-repo out of
  ;; scope (see handoff.cljc ns docstring). signature-gate must not newly
  ;; break every pre-existing caller of handle-ingest.
  (let [out (handoff/handle-ingest (assoc (valid-request) "capability" (valid-capability) "now" NOW))]
    (is (= true (get out "ack")))
    (is (= "pending" (get out "iryoStatus")))))

(deftest test-canonicalize-capability-payload-excludes-signature-and-is-deterministic
  (let [cap (assoc (valid-capability) "signature" {"alg" "ed25519" "value" "irrelevant" "keyId" "k1"})
        a (handoff/canonicalize-capability-payload cap)
        b (handoff/canonicalize-capability-payload (dissoc cap "signature"))]
    (is (= a b))
    (is (not (.contains a "irrelevant")))))

;; ── signature-gate malformed-input robustness (health-check pass, 2026-07-08)
;; ────────────────────────────────────────────────────────────────────────────
;; `signature-gate`'s `else` branch wraps the Base64 decode + verify in its own
;; try/catch and turns ANY exception (not just a verification mismatch) into a
;; normal fail-closed {:ok? false :reason ...} result. Previously only "wrong
;; key" / "tampered payload" (both well-formed base64 that fails cryptographic
;; verification) were exercised — this adds the DECODE-failure path itself
;; (malformed, non-base64 bytes), which is a materially different branch of
;; the same try/catch and was not hit by any existing test.

(deftest test-signature-value-non-base64-fails-closed-not-a-crash
  (let [{:keys [pub32]} (gen-keypair)
        cap (assoc (valid-capability) "signature" {"alg" "ed25519" "value" "not-valid-base64!!!" "keyId" "k1"})
        out (handoff/handle-ingest (assoc (valid-request) "capability" cap
                                          "granterPublicKey" (b64 pub32) "now" NOW))]
    (is (= false (get out "ack")))
    (is (= "needs-info" (get out "iryoStatus")))
    (is (.contains (str (get out "error")) "could not be verified"))))

(deftest test-granter-public-key-non-base64-fails-closed-not-a-crash
  (let [{:keys [private]} (gen-keypair)
        signed-cap (sign-capability (valid-capability) private "did:web:patient.iryo.etzhayyim.com:e2e1#key-1")
        out (handoff/handle-ingest (assoc (valid-request) "capability" signed-cap
                                          "granterPublicKey" "not-valid-base64!!!" "now" NOW))]
    (is (= false (get out "ack")))
    (is (= "needs-info" (get out "iryoStatus")))
    (is (.contains (str (get out "error")) "could not be verified"))))

;; ── FIXED (this iteration) — malformed date strings now degrade gracefully ──
;; Previously (health-check pass, 2026-07-08) `capability-gate`'s
;; `instant-before?` called `(Instant/parse ...)` directly on both `now` and
;; `capability["expiresAt"]` with no guard, and `handle-ingest`'s own
;; try/catch only caught `#?(:clj clojure.lang.ExceptionInfo :cljs
;; ExceptionInfo)` (the type `karte/phi-leak!` throws) — a malformed ISO-8601
;; instant string threw `java.time.format.DateTimeParseException` instead,
;; which is NOT an ExceptionInfo, so it propagated UNCAUGHT out of
;; `handle-ingest` rather than degrading to `{"ack" false "iryoStatus"
;; "needs-info" ...}` like every other malformed-input case in this
;; namespace. This was a real asymmetry: contrast with `signature-gate`
;; above, whose own try/catch already turned a malformed base64 string into a
;; graceful fail-closed result.
;;
;; Fix: `handoff/parse-instant` is a new never-throwing parse (same contract
;; as `parse-at-uri`), and `capability-gate` now gates on
;; `(nil? (parse-instant ...))` for BOTH `expiresAt` and `now` BEFORE calling
;; `instant-before?`, so a malformed date now fails closed via the same
;; `{:ok? false :reason ...}` path as every other structural gate failure —
;; G5 non-adjudicating discipline is restored for dates too. The two tests
;; below replace the prior `thrown?`-pinning KNOWN-GAP tests with the
;; corrected graceful-degradation expectation; a third pins that well-formed
;; dates (both the happy path and the pre-existing expired-capability
;; rejection) are unaffected by the fix.

(deftest test-malformed-expires-at-degrades-to-needs-info-not-a-crash
  (let [cap (assoc (valid-capability) "expiresAt" "not-a-valid-instant")
        out (handoff/handle-ingest (assoc (valid-request) "capability" cap "now" NOW))]
    (is (= false (get out "ack")))
    (is (= "needs-info" (get out "iryoStatus")))
    (is (.contains (str (get out "error")) "not a valid ISO-8601 instant"))))

(deftest test-malformed-now-degrades-to-needs-info-not-a-crash
  (let [out (handoff/handle-ingest (assoc (valid-request) "capability" (valid-capability) "now" "also-not-a-valid-instant"))]
    (is (= false (get out "ack")))
    (is (= "needs-info" (get out "iryoStatus")))
    (is (.contains (str (get out "error")) "not a valid ISO-8601 instant"))))

(deftest test-well-formed-dates-are-unaffected-by-the-malformed-date-fix
  ;; Regression guard for the fix above: well-formed ISO-8601 dates must still
  ;; compare correctly — both the happy (not-yet-expired) path and the
  ;; pre-existing expired-capability rejection (test-expired-capability-is-rejected)
  ;; must keep returning their OWN reason, not the new
  ;; "not a valid ISO-8601 instant" message.
  (testing "not yet expired -> accepted"
    (let [out (handoff/handle-ingest (assoc (valid-request) "capability" (valid-capability) "now" NOW))]
      (is (= true (get out "ack")))
      (is (= "pending" (get out "iryoStatus")))))
  (testing "already expired -> needs-info with the expired reason, not a malformed-instant reason"
    (let [cap (assoc (valid-capability) "expiresAt" "2020-01-01T00:00:00Z")
          out (handoff/handle-ingest (assoc (valid-request) "capability" cap "now" NOW))]
      (is (= false (get out "ack")))
      (is (= "needs-info" (get out "iryoStatus")))
      (is (.contains (str (get out "error")) "expired at"))
      (is (not (.contains (str (get out "error")) "not a valid ISO-8601 instant"))))))
