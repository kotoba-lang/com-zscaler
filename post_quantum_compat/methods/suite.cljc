(ns post-quantum-compat.methods.suite
  "post_quantum-compat — pqh-v1 suite + migration-state SSoT (ADR-2606111300).

  1:1 Clojure port of `methods/suite.py`. The machine-readable registry of WHERE
  the substrate stands against the quantum/HNDL threat: which crypto layer runs
  which primitive, whether Shor or only Grover applies, what suite it migrated to,
  and what remains operator-/chain-/upstream-gated. The companion paper's §7
  table, as data — so coverage can be asserted by tests instead of believed.

  Pure stdlib. Keywords are kept as \":ns/name\" STRINGS (not Clojure keywords)
  to mirror the Python source. Non-eschatological framing per Charter §1.15."
  (:require [clojure.set :as set]))

;; ── suite registry (FIPS 203/204 + RFC 9106 constants) ──────────────────────

(def SUITES
  {":suite/pqh-v1"
   {":suite/id" "pqh-v1"
    ":suite/adr" "ADR-2606111300"
    ":suite/kem"
    {":kem/classical" "X25519"
     ":kem/pq" "ML-KEM-768"
     ":kem/pq-fips" "FIPS 203"
     ":kem/combiner" "HKDF-SHA256 transcript-bound (X-Wing pattern)"
     ":kem/pq-public-bytes" 1184
     ":kem/pq-ciphertext-bytes" 1088
     ":kem/shared-secret-bytes" 32
     ":kem/pq-multicodec" 0x120C}            ; mlkem-768-pub (draft)
    ":suite/sig"
    {":sig/classical" "Ed25519"
     ":sig/pq" "ML-DSA-65"
     ":sig/pq-fips" "FIPS 204"
     ":sig/composition" "dual signature, verifier requires both (AND)"
     ":sig/pq-public-bytes" 1952
     ":sig/pq-signature-bytes" 3309
     ":sig/pq-multicodec" 0x1211}            ; mldsa-65-pub (draft)
    ":suite/kdf"
    {":kdf/id" "argon2id-v1"
     ":kdf/rfc" "RFC 9106"
     ":kdf/default-m-kib" 19456
     ":kdf/default-t" 2
     ":kdf/default-p" 1}}})

;; ── layer migration registry (the paper's §7 table as data) ─────────────────
;; :layer/status enum:
;;   :migrated          pqh-v1 landed in code (PR refs below)
;;   :adequate          Grover-bounded only — no migration needed by design
;;   :operator-pending  code landed; production key/flag flip is an operator step
;;   :chain-blocked     cannot migrate unilaterally (Base L2 / ERC-4337 constraint)
;;   :upstream-pending  waiting on a dependency's own PQ release
;;   :deferred          surface not live yet; migrate when it ships

;; NOTE: keys are kept in source order via an explicit vector of [k v] pairs so
;; that datom emission matches the Python dict's insertion order exactly.
(def LAYERS
  [{":layer/id" ":layer/record-at-rest" ":layer/primitive" "XChaCha20-Poly1305-256"
    ":layer/quantum-attack" ":grover" ":layer/status" ":adequate"
    ":layer/adr" "ADR-2605181100"}
   {":layer/id" ":layer/vault-at-rest" ":layer/primitive" "AES-256-GCM"
    ":layer/quantum-attack" ":grover" ":layer/status" ":adequate"
    ":layer/adr" "ADR-2605181100"}
   {":layer/id" ":layer/hashes" ":layer/primitive" "SHA-256/Keccak-256/BLAKE2b"
    ":layer/quantum-attack" ":grover" ":layer/status" ":adequate"
    ":layer/adr" "ADR-2606111300"}
   {":layer/id" ":layer/key-wrap" ":layer/primitive" "X25519"
    ":layer/quantum-attack" ":shor" ":layer/status" ":migrated"
    ":layer/suite" "pqh-v1" ":layer/adr" "ADR-2606111300" ":layer/pr" [1616 1621]}
   {":layer/id" ":layer/did-signal-binding" ":layer/primitive" "Ed25519"
    ":layer/quantum-attack" ":shor" ":layer/status" ":migrated"
    ":layer/suite" "pqh-v1" ":layer/adr" "ADR-2606111300" ":layer/pr" [1616]}
   {":layer/id" ":layer/did-doc-attestation" ":layer/primitive" "Ed25519"
    ":layer/quantum-attack" ":shor" ":layer/status" ":migrated"
    ":layer/suite" "pqh-v1" ":layer/adr" "ADR-2606111300" ":layer/pr" [1630]
    ":layer/note" "requirePq/expectedPqDidKey enforcement flip = operator step"}
   {":layer/id" ":layer/password-kdf" ":layer/primitive" "PBKDF2-SHA256"
    ":layer/quantum-attack" ":grover" ":layer/status" ":migrated"
    ":layer/suite" "argon2id-v1" ":layer/adr" "ADR-2606111300" ":layer/pr" [1625]
    ":layer/note" "T3 implementation-layer hardening, not a quantum fix"}
   {":layer/id" ":layer/production-pq-keys" ":layer/primitive" "ML-DSA-65 did:key"
    ":layer/quantum-attack" ":shor" ":layer/status" ":operator-pending"
    ":layer/suite" "pqh-v1" ":layer/adr" "ADR-2606111300"
    ":layer/note" "sign-diddoc.mjs --pq exists; key generation/publication is operator-held (no-server-key)"}
   {":layer/id" ":layer/governance-signature" ":layer/primitive" "secp256k1-ECDSA"
    ":layer/quantum-attack" ":shor" ":layer/status" ":chain-blocked"
    ":layer/adr" "ADR-2606111300"
    ":layer/note" "Base L2 / ERC-4337 constraint; mitigation = key rotation + spend-before-z"}
   {":layer/id" ":layer/libsignal-path" ":layer/primitive" "X25519-X3DH"
    ":layer/quantum-attack" ":shor" ":layer/status" ":upstream-pending"
    ":layer/note" "upstream PQXDH adoption via optional-dependency bump"}
   {":layer/id" ":layer/passkey-signature" ":layer/primitive" "P-256-ES256"
    ":layer/quantum-attack" ":shor" ":layer/status" ":deferred"
    ":layer/note" "surface not live (future R2 federated training); WebAuthn PQ tracked"}])

;; LAYER_ATTRS in canonical emit order (the durable scalar attrs; :layer/pr is
;; handled separately by the emitter to mirror the Python dict-iteration order).
(def LAYER-ATTRS
  [":layer/primitive" ":layer/quantum-attack" ":layer/status"
   ":layer/suite" ":layer/adr" ":layer/note"])

(def MIGRATION-DONE #{":migrated" ":adequate"})
(def GATED #{":operator-pending" ":chain-blocked" ":upstream-pending" ":deferred"})

;; ── math helpers (testable, from the survivability paper) ────────────────────

(defn grover-effective-bits
  "BBBV-proved quadratic bound: brute force of an n-bit key costs 2^(n/2)."
  [key-bits]
  (quot key-bits 2))

(defn mosca
  "Mosca inequality: act now iff x + y > z. Returns the slack either way."
  [x-shelf-life-years y-migration-years z-crqc-years]
  (let [slack (- z-crqc-years (+ x-shelf-life-years y-migration-years))]
    {":mosca/act-now" (< slack 0)
     ":mosca/slack-years" slack}))

(defn shor-applies [layer]
  (= (get layer ":layer/quantum-attack") ":shor"))

;; ── coverage readout (DERIVED — computed on read, never stored) ──────────────

(defn coverage-report []
  (let [shor (filterv shor-applies LAYERS)
        migrated (filterv #(= (get % ":layer/status") ":migrated") shor)
        gated (filterv #(contains? GATED (get % ":layer/status")) shor)
        done-or-gated (set/union MIGRATION-DONE GATED)
        unknown (filterv #(not (contains? done-or-gated (get % ":layer/status"))) LAYERS)]
    {":coverage/layers-total" (count LAYERS)
     ":coverage/shor-vulnerable" (count shor)
     ":coverage/migrated" (count migrated)
     ":coverage/gated" (count gated)
     ":coverage/unknown" (count unknown)
     ":coverage/migrated-fraction"
     (/ (Math/round (* 10000.0 (/ (double (count migrated)) (count shor)))) 10000.0)
     ":coverage/gated-ids" (vec (sort (map #(get % ":layer/id") gated)))}))
