(ns iryo.methods.handoff
  "handoff.cljc — the karute -> iryo hand-off boundary (ADR-2605231401 Pattern 2
  'etzhayyim <-> vendor bridge (insurance billing)' / ADR-2606074000 G1/G2/G3/G5).

  karute's `com.etzhayyim.apps.karute.requestIryoBilling` procedure forwards a
  billing request to iryo via `agent.invoke` naming the method
  `ingestKaruteEncounterForBilling` (see karute/actor-manifest.jsonld
  'requestIryoBilling' pipeline, forwardToIryo step). Until this namespace, iryo
  had no receiving implementation for that hand-off at all — this is the boundary
  karute/MATURITY.md item 11 ('iryo(レセプト)への hand-off boundary テスト') tracks.

  Scope of THIS boundary (deliberately narrow, matches the ADR's phased rollout):
    - structural PHI-free gate on the wire request (G2) — the request may carry
      only DIDs / AT-URIs, never plaintext identity or free text;
    - consent-capability structural gate (G1/G7) — purpose / grantee / granter /
      revocation / expiry / scope, checked against the resolved capability record
      the caller supplies (ADR-2605231401's step 1 'Resolves the capability
      record' — PDS resolution itself is cross-repo / karute-side, out of scope
      here);
    - G3 no-server-key / G5 non-adjudicating: on success this only ACCEPTS the
      intake into a `:pending` draft queue (`iryoStatus \"pending\"`) — it never
      submits online and never adjudicates. On a gate failure it returns
      `iryoStatus \"needs-info\"`, NEVER `\"rejected\"` or `\"accepted\"` — those
      two values are reserved for the 審査支払機関's own adjudication (G5); an
      iryo-side intake-gate failure is not a claim decision.

  Ed25519 signature verification (karute/MATURITY.md #8, ADR-2605231401's step 2
  'Verifies signature against the patient's DID document') — `signature-gate` —
  is now IMPLEMENTED, but deliberately OPT-IN via an additional already-resolved
  input, `\"granterPublicKey\"` (base64 raw 32-byte Ed25519 public key), mirroring
  the same already-resolved-input contract this namespace already uses for
  `\"capability\"` itself: obtaining that key by resolving granterDid's DID
  document is still cross-repo network I/O (in this bridge patientDid is a
  rotating pseudonym did:web — `iryo.methods.karte/rotating-pseudonym-did`,
  `did:web:patient.iryo.etzhayyim.com:<hash>` — so verifying it means an HTTPS
  did:web document fetch, the same class of problem as PDS resolution below) and
  stays out of scope here. When `granterPublicKey` is NOT supplied, this gate
  no-ops and behavior is byte-for-byte unchanged from before.

  consentCapabilityUri structural self-consistency (NEW, this iteration) —
  `parse-at-uri` + `capability-gate`'s new checks close the STRUCTURAL half of
  'PDS/AT-URI resolution' without doing the network fetch: given the
  already-resolved `capability` record, this boundary now also verifies that
  `consentCapabilityUri` itself (a) parses as a well-formed
  `at://<did>/<collection>/<rkey>` AT-URI, (b) its collection segment is
  exactly `com.etzhayyim.consent.capability` (the canonical NSID — the lexicon
  says the capability record 'is stored at com.etzhayyim.consent.capability in
  the granter's PDS', ADR-2605231401), and (c) its did segment equals
  `capability[\"granterDid\"]`. This is a pure string-parse, no network I/O —
  it catches a caller supplying a capability record that is structurally
  inconsistent with the very URI the wire request references (a
  substitution/confusion bug or attack), which nothing previously checked.
  It does NOT fetch consentCapabilityUri's bytes from a real PDS — an actor
  could still forge a self-consistent (uri, capability) pair; only a real
  network resolve + Ed25519 signature check closes that fully, and the
  signature half is exactly what `signature-gate` above already does GIVEN a
  key. The two together narrow the remaining out-of-scope surface to key
  ACQUISITION (both the granter public key AND the capability bytes
  themselves) via network resolution — still cross-repo, still not attempted
  here.

  Explicitly NOT in scope (tracked separately, do not conflate):
    - resolving a capability's granter public key FROM granterDid (did:web
      document fetch, cross-repo network I/O) — `signature-gate` verifies GIVEN
      a key, it does not obtain one;
    - actually FETCHING consentCapabilityUri's bytes from a real PDS (needs
      `@etzhayyim/sdk` / `kotoba-lang/atproto-client`, cross-repo, real HTTPS
      I/O) — `capability-gate`'s new checks verify GIVEN an already-resolved
      capability that it is at least self-consistent with the URI, they do not
      obtain the record;
    - actual レセプト計算 from the referenced encounter (that is
      `iryo.methods.agent/handle-rezept`, unchanged — this boundary only governs
      whether the intake is even accepted into iryo's queue)."
  (:require [clojure.string :as str]
            [clojure.set :as set]
            [iryo.methods.karte :as karte])
  (:import [java.time Instant]
           [java.security KeyFactory MessageDigest Signature]
           [java.security.spec X509EncodedKeySpec]
           [java.util Base64]))

(def iryo-did
  "iryo's own DID (manifest.edn :actor/did) — the only valid consentCapabilityUri
  granteeDid for this hand-off."
  "did:web:iryo.etzhayyim.com")

(def billing-purpose
  "The only consent.capability purpose this hand-off honors (ADR-2605231401)."
  "insurance-billing")

(def request-fields
  "The exact wire shape karute's requestIryoBilling forwards to
  ingestKaruteEncounterForBilling (karute/actor-manifest.jsonld forwardToIryo
  step args) — the ONLY keys allowed on the intake request. Anything else is a
  smuggled field and fails the PHI gate closed, not open."
  #{"patientDid" "encounterDid" "facilityDid"
    "serviceRequestUris" "medicationRequestUris" "consentCapabilityUri"})

(def ^:private uri-array-fields ["serviceRequestUris" "medicationRequestUris"])
(def ^:private did-fields ["patientDid" "facilityDid"])

(defn- ascii-only?
  "Real DIDs and AT-URIs are always ASCII. Any non-ASCII byte in a field this
  boundary is supposed to be codes/identifiers-only is treated as a smuggled-PHI
  signal (e.g. a Japanese patient name) — a cheap, structural defense-in-depth
  check alongside the field allow-list."
  [s]
  (every? #(< (int %) 128) (str s)))

(defn- check-ascii! [field-label s]
  (when-not (ascii-only? s)
    (karte/phi-leak! (str field-label " contains non-ASCII characters — looks like smuggled PHI, not an identifier: " s))))

(defn assert-request-phi-free!
  "Structural PHI gate for the karute -> iryo intake request (G2). Request MUST
  be `(select-keys state request-fields)` shaped — only identifiers, never
  plaintext PHI (name / dob / address / SOAP free text). Throws
  (karte/phi-leak!) on any violation; returns the request unchanged on success."
  [request]
  (doseq [k (keys request)]
    (when-not (contains? request-fields k)
      (karte/phi-leak! (str "unexpected field in iryo hand-off intake (not on the codes/DID/URI allow-list): " k))))
  (doseq [k did-fields]
    (when-let [v (get request k)]
      (check-ascii! k v)
      (when-not (str/starts-with? (str v) "did:")
        (karte/phi-leak! (str k " is not a DID: " v)))))
  (when-let [v (get request "encounterDid")]
    (check-ascii! "encounterDid" v)
    (when (str/blank? v)
      (karte/phi-leak! "encounterDid is blank"))
    (when-not (or (str/starts-with? v "did:") (str/starts-with? v "at://"))
      (karte/phi-leak! (str "encounterDid is neither a DID nor an AT-URI: " v))))
  (when-let [v (get request "consentCapabilityUri")]
    (check-ascii! "consentCapabilityUri" v)
    (when-not (str/starts-with? v "at://")
      (karte/phi-leak! (str "consentCapabilityUri is not an AT-URI: " v))))
  (doseq [field uri-array-fields]
    (doseq [uri (get request field [])]
      (check-ascii! field uri)
      (when-not (str/starts-with? (str uri) "at://")
        (karte/phi-leak! (str field " entry is not an AT-URI (PHI must never travel as inline data): " uri)))))
  request)

(defn required-scope
  "The consent.capability `scope` NSIDs this request needs, derived from which
  resource references are actually present (least-privilege check — a
  capability scoped ONLY to encounter data cannot authorize forwarding
  serviceRequest/medicationRequest records too)."
  [request]
  (cond-> #{"com.etzhayyim.karute.encounter"}
    (seq (get request "serviceRequestUris")) (conj "com.etzhayyim.karute.serviceRequest")
    (seq (get request "medicationRequestUris")) (conj "com.etzhayyim.karute.medicationRequest")))

(defn- blank-or-nil? [v] (or (nil? v) (and (string? v) (str/blank? v))))

(defn parse-instant
  "Pure, never-throwing ISO-8601 instant parse — same never-throws contract as
  `parse-at-uri` above (returns nil on anything that is not a valid instant
  string instead of letting `java.time.format.DateTimeParseException`
  propagate). This closes a KNOWN GAP found + pinned during a 2026-07-08
  health-check pass: `capability-gate` used to call `java.time.Instant/parse`
  directly on `now`/`capability[\"expiresAt\"]` with no guard, and
  `handle-ingest`'s `try/catch` only catches `ExceptionInfo`
  (`karte/phi-leak!`'s type), so a malformed instant string threw UNCAUGHT out
  of `handle-ingest` — the one place in this namespace that broke the
  otherwise-uniform graceful-degradation discipline every other malformed
  input follows (G5 non-adjudicating: fail closed to `needs-info`, never a
  crash). `capability-gate` now gates on this BEFORE calling `instant-before?`
  (see methods/test_handoff.cljc for the before/after pinned tests)."
  [s]
  (try
    (Instant/parse s)
    (catch #?(:clj Exception :cljs js/Error) _ nil)))

(defn- instant-before? [a b]
  (.isBefore (parse-instant a) (parse-instant b)))

(def consent-capability-collection
  "The canonical NSID a consentCapabilityUri MUST resolve under, per the
  com.etzhayyim.consent.capability lexicon description: the record 'is stored
  at com.etzhayyim.consent.capability in the granter's PDS' (ADR-2605231401)."
  "com.etzhayyim.consent.capability")

(defn parse-at-uri
  "Pure structural parse of an AT-URI (`at://<did>/<collection>/<rkey>`) into
  `{:did ... :collection ... :rkey ...}` — no network I/O, this does NOT
  resolve/fetch anything, it only decomposes the string. Returns nil (never
  throws) on anything that isn't `at://` followed by exactly 3 non-blank
  `/`-delimited segments. Splitting on `/` (not `:`) is safe even though the
  did segment itself contains colons (e.g. `did:web:foo.example:bar`), since
  AT-URI syntax reserves `/` as the segment separator after the authority."
  [uri]
  (when (and (string? uri) (str/starts-with? uri "at://"))
    (let [parts (str/split (subs uri (count "at://")) #"/" -1)]
      (when (= 3 (count parts))
        (let [[did collection rkey] parts]
          (when (and (seq did) (seq collection) (seq rkey))
            {:did did :collection collection :rkey rkey}))))))

(defn capability-gate
  "Structural consent-capability check (G1/G7 — the licensed clinic's patient
  consented, iryo does not originate a claim on its own key). Deliberately does
  NOT verify the Ed25519 signature (that is the separate `signature-gate`
  below, karute/MATURITY.md #8) — this is the purpose/grantee/granter/
  revocation/expiry/scope gate, PLUS (this iteration) a structural
  self-consistency check between `consentCapabilityUri` and the
  ALREADY-RESOLVED `capability` record it is supposed to name (see
  `parse-at-uri` + the ns docstring's 'consentCapabilityUri structural
  self-consistency' section) — it does NOT fetch consentCapabilityUri's bytes
  from a real PDS (that remains cross-repo/out of scope). `capability` is the
  ALREADY-RESOLVED com.etzhayyim.consent.capability record (string-keyed,
  camelCase). Returns {:ok? true} or {:ok? false :reason \"...\"}."
  [capability request now]
  (let [uri (get request "consentCapabilityUri")
        parsed-uri (parse-at-uri uri)]
    (cond
      (nil? capability)
      {:ok? false :reason "no consent capability resolved for consentCapabilityUri"}

      (not= billing-purpose (get capability "purpose"))
      {:ok? false :reason (str "consent capability purpose is not '" billing-purpose "': " (get capability "purpose"))}

      (not= iryo-did (get capability "granteeDid"))
      {:ok? false :reason (str "consent capability granteeDid is not iryo: " (get capability "granteeDid"))}

      (not= (get request "patientDid") (get capability "granterDid"))
      {:ok? false :reason "consent capability granterDid does not match the billed patientDid"}

      (not (blank-or-nil? (get capability "revokedAt")))
      {:ok? false :reason (str "consent capability was revoked at " (get capability "revokedAt"))}

      (blank-or-nil? (get capability "expiresAt"))
      {:ok? false :reason "consent capability has no expiresAt"}

      (nil? (parse-instant (get capability "expiresAt")))
      {:ok? false :reason (str "consent capability expiresAt is not a valid ISO-8601 instant: " (get capability "expiresAt"))}

      (nil? (parse-instant now))
      {:ok? false :reason (str "current time is not a valid ISO-8601 instant: " now)}

      (not (instant-before? now (get capability "expiresAt")))
      {:ok? false :reason (str "consent capability expired at " (get capability "expiresAt"))}

      (not (set/subset? (required-scope request) (set (get capability "scope" []))))
      {:ok? false :reason (str "consent capability scope " (get capability "scope") " does not cover required scope " (required-scope request))}

      (let [allowlist (set (get capability "resourceUris" []))]
        (and (seq allowlist)
             (not (set/subset? (set (concat (get request "serviceRequestUris" [])
                                             (get request "medicationRequestUris" [])))
                                allowlist))))
      {:ok? false :reason "requested resource URIs are outside the consent capability's resourceUris allowlist"}

      (blank-or-nil? uri)
      {:ok? false :reason "request is missing consentCapabilityUri (required per com.etzhayyim.apps.karute.requestIryoBilling)"}

      (nil? parsed-uri)
      {:ok? false :reason (str "consentCapabilityUri does not parse as a well-formed AT-URI (at://<did>/<collection>/<rkey>): " uri)}

      (not= consent-capability-collection (:collection parsed-uri))
      {:ok? false :reason (str "consentCapabilityUri collection is not " consent-capability-collection ": " (:collection parsed-uri))}

      (not= (:did parsed-uri) (get capability "granterDid"))
      {:ok? false :reason "consentCapabilityUri's repo DID does not match the resolved capability's granterDid (mismatched/substituted capability record)"}

      :else
      {:ok? true})))

;; ── Ed25519 signature verification (karute/MATURITY.md #8) ──────────────────
;; JDK-only (java.security), no third-party crypto dep — the same approach
;; already proven green elsewhere in this monorepo
;; (20-actors/kaiyaku/tools/issue_capability.cljc's gen-keypair/sign-b64/verify,
;; which itself exercises a real sign->verify roundtrip under babashka).

(defn- canonical-json-value
  "Deterministic (sorted-keys) canonical string serialization of a Clojure
  value — the signing/verification input for `signature-gate` below. NOTE:
  this is THIS namespace's OWN canonicalization, not yet verified for byte
  parity against the eventual real granter-side signer
  (`@etzhayyim/sdk.signConsentCapability`, ADR-2605231401 Phase 2 — still a
  stub per the ADR, so no reference bytes exist anywhere to check against
  yet). It round-trips correctly against THIS namespace's own
  `verify-ed25519-signature` (see methods/test_handoff.cljc for the full
  keypair->sign->verify roundtrip), the same open byte-parity caveat
  `issue_capability.cljc`'s docstring already carries for its own CACAO
  envelope (its 'G6 operator step')."
  [v]
  (cond
    (map? v)
    (str "{" (str/join "," (for [k (sort (keys v))]
                              (str (canonical-json-value (name k)) ":" (canonical-json-value (get v k)))))
         "}")

    (sequential? v)
    (str "[" (str/join "," (map canonical-json-value v)) "]")

    (string? v)
    (str "\"" (str/escape v {\" "\\\"" \\ "\\\\" \newline "\\n" \tab "\\t" \return "\\r"}) "\"")

    (nil? v) "null"
    (boolean? v) (str v)
    (number? v) (str v)
    :else (str "\"" (str v) "\"")))

(defn canonicalize-capability-payload
  "The canonical byte-source `signature-gate` verifies a capability's Ed25519
  signature over: every field of `capability` EXCEPT `\"signature\"` itself
  (per the lexicon: 'Ed25519 signature by granterDid over the canonicalized
  payload — everything except signature itself'), sorted-keys, JSON-ish
  canonical string. See `canonical-json-value`'s docstring for the open
  byte-parity caveat vs the eventual real granter-side signer."
  [capability]
  (canonical-json-value (dissoc capability "signature")))

(def ^:private ed25519-spki-der-prefix
  "The fixed 12-byte DER prefix that wraps a raw 32-byte Ed25519 public key
  into an X.509 SubjectPublicKeyInfo structure per RFC 8410 §4 — needed
  because JDK's `KeyFactory` only loads keys from that structure, not raw
  key bytes directly. Verified against a real JDK-generated Ed25519 keypair
  round-trip (keypair -> raw pubkey bytes -> this wrapper -> KeyFactory ->
  verify) before landing; see methods/test_handoff.cljc."
  (byte-array [0x30 0x2a 0x30 0x05 0x06 0x03 0x2b 0x65 0x70 0x03 0x21 0x00]))

(defn- raw-ed25519-pubkey->public-key
  "Wrap a raw 32-byte Ed25519 public key (byte array) in the SPKI DER
  envelope and load it as a java.security.PublicKey. Returns nil if `raw32`
  is not exactly 32 bytes."
  ^java.security.PublicKey [^bytes raw32]
  (when (= 32 (alength raw32))
    (let [der (byte-array (concat (seq ed25519-spki-der-prefix) (seq raw32)))]
      (.generatePublic (KeyFactory/getInstance "Ed25519") (X509EncodedKeySpec. der)))))

(defn verify-ed25519-signature
  "Verify a raw Ed25519 signature (JDK java.security, no third-party dep).
  `pub32-bytes` / `message-bytes` / `sig-bytes` are all byte arrays. Returns
  false (never throws) on any malformed input — a bad key/signature is a
  verification failure, not a program error."
  [^bytes pub32-bytes ^bytes message-bytes ^bytes sig-bytes]
  (boolean
    (try
      (when-let [pk (raw-ed25519-pubkey->public-key pub32-bytes)]
        (let [v (Signature/getInstance "Ed25519")]
          (.initVerify v pk)
          (.update v message-bytes)
          (.verify v sig-bytes)))
      (catch Exception _ false))))

(defn signature-gate
  "Ed25519 cryptographic verification gate for the consent.capability record
  (karute/MATURITY.md #8 — previously wholly unimplemented anywhere in the
  monorepo). Verification ONLY runs when the caller supplies
  `granter-public-key-b64` — the ALREADY-RESOLVED raw 32-byte Ed25519 public
  key for `capability[\"signature\"][\"keyId\"]`, base64-encoded — mirroring
  the same already-resolved-input contract this boundary already uses for
  `capability` itself (and, in agent.cljc, `encounter`). Actually RESOLVING
  that key from granterDid is still out of scope (see the ns docstring):
  patientDid in this bridge is a rotating pseudonym did:web
  (`did:web:patient.iryo.etzhayyim.com:<hash>`), so obtaining its
  verification material means an HTTPS did:web document fetch — network I/O,
  cross-repo, the same class of problem as PDS/AT-URI resolution.

  Without `granter-public-key-b64`, this gate no-ops (`{:ok? true}`) —
  existing callers that have not resolved a key see byte-for-byte unchanged
  behavior (backward compatible). WHEN a key is supplied, a missing/malformed
  signature or a signature that fails to verify (wrong key OR a tampered
  payload signed-then-modified) is REJECTED (fail-closed)."
  [capability granter-public-key-b64]
  (if (nil? granter-public-key-b64)
    {:ok? true}
    (let [sig (get capability "signature")]
      (cond
        (nil? sig)
        {:ok? false :reason "consent capability has no signature to verify (granterPublicKey was supplied but capability[\"signature\"] is missing)"}

        (not= "ed25519" (get sig "alg"))
        {:ok? false :reason (str "consent capability signature alg is not ed25519: " (get sig "alg"))}

        (blank-or-nil? (get sig "value"))
        {:ok? false :reason "consent capability signature value is missing"}

        :else
        (try
          (let [payload-bytes (.getBytes ^String (canonicalize-capability-payload capability) "UTF-8")
                sig-bytes (.decode (Base64/getDecoder) ^String (get sig "value"))
                pub-bytes (.decode (Base64/getDecoder) ^String granter-public-key-b64)]
            (if (verify-ed25519-signature pub-bytes payload-bytes sig-bytes)
              {:ok? true}
              {:ok? false :reason "consent capability signature does not verify against the supplied granter public key (wrong key or tampered payload)"}))
          (catch Exception e
            {:ok? false :reason (str "consent capability signature could not be verified: " (ex-message e))}))))))

(defn- sha256-hex [^String s]
  (let [b (.digest (MessageDigest/getInstance "SHA-256") (.getBytes s "UTF-8"))]
    (apply str (map #(format "%02x" (bit-and (int %) 0xff)) b))))

(defn- claim-ref [request]
  (str "iryo-req-" (subs (sha256-hex (str (get request "patientDid") "|"
                                           (get request "encounterDid") "|"
                                           (get request "consentCapabilityUri")))
                          0 16)))

(def intent "member-principal-claim-substrate; non-adjudicating")

(def ^:private cell-only-keys
  "Keys the cell wiring adds on top of the actual karute wire payload — never
  part of the intake request itself, so they're excluded before the PHI/
  allow-list gate runs (otherwise `assert-request-phi-free!` would flag its own
  plumbing as a smuggled field). `granterPublicKey` is the OPTIONAL
  already-resolved Ed25519 verification material for `signature-gate` (see its
  docstring) — same already-resolved-input category as `capability`."
  #{"capability" "now" "granterPublicKey"})

(defn handle-ingest
  "The `ingestKaruteEncounterForBilling` cell handler karute's requestIryoBilling
  forwards to (state is string-keyed, matching the other iryo.methods.agent
  handlers). `state` carries the wire request fields PLUS `\"capability\"` (the
  already-resolved consent.capability record), optionally `\"now\"`
  (ISO-8601 instant string; defaults to the real current time), and optionally
  `\"granterPublicKey\"` (base64 raw 32-byte Ed25519 public key — when
  supplied, `signature-gate` cryptographically verifies the capability's
  signature; see its docstring for what obtaining this key still requires).

  Returns a map matching com.etzhayyim.apps.karute.requestIryoBilling's output
  shape (`ack` / `iryoClaimRef` / `iryoStatus` / `error`) since the karute
  bridge's forwardToIryo step reads `iryoClaimRef`/`iryoStatus` straight through
  into its IryoBillingRequest record."
  [state]
  (let [request (apply dissoc state cell-only-keys)
        now (get state "now" (str (Instant/now)))]
    (try
      (assert-request-phi-free! request)
      (let [capability (get state "capability")
            structural-gate (capability-gate capability request now)
            gate (if (:ok? structural-gate)
                   (signature-gate capability (get state "granterPublicKey"))
                   structural-gate)]
        (if (:ok? gate)
          {"ack" true "iryoStatus" "pending" "iryoClaimRef" (claim-ref request) "intent" intent}
          {"ack" false "iryoStatus" "needs-info" "error" (:reason gate) "intent" intent}))
      (catch #?(:clj clojure.lang.ExceptionInfo :cljs ExceptionInfo) e
        {"ack" false "iryoStatus" "needs-info" "error" (ex-message e) "intent" intent}))))
