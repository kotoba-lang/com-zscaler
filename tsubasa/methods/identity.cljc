#!/usr/bin/env bb
;; tsubasa 翼 — actor self-certifying did:key identity (R3+). ADR-2606072802 §R3.
(ns tsubasa.methods.identity
  "identity.cljc — tsubasa 翼 self-certifying `did:key` (Ed25519), present-only.

  This is the code realization of *\"the actor makes its OWN key and does not expose it\"*
  (the design raised in review). The actor GENERATES its own Ed25519 keypair; the public
  half becomes a `did:key` (its self-certifying identity, published in did.json
  `alsoKnownAs` — the kanae/kanae pattern); the private SEED is **sealed** (macOS Keychain
  / 1Password, CONCEALED, never committed, never logged) and used **present-only** — the
  actor signs/attests with it but never emits it. No server holds it; no human types it.

  Why this is charter-clean (no-server-key, ADR-2605231525): the invariant bars a
  *custodial, exfiltratable* signing key on an etzhayyim process. A self-generated key
  whose seed is sealed + present-only + whose autonomous writes are attributed to a
  consenting human via the member CACAO leash (see kotoba_bridge) keeps custody off the
  platform and accountability on a person. (The stronger \"the actor itself can never read
  the seed\" guarantee is a TEE/enclave or threshold-MPC construction — repo-future.)

  Pure stdlib (java.security Ed25519 + base58btc); deterministic verify; no network."
  (:require [clojure.string :as str]
            [multiformats.core :as mf]
            #?(:clj [babashka.process :as p])))

;; ── base58btc (Bitcoin alphabet) — for did:key multibase 'z' ──────────────────
(defn b58btc
  "Encode bytes as base58btc (no checksum), preserving leading-zero bytes as '1's.
   Delegates to the shared com-junkawasaki/multiformats-clj (portable clj+cljs)."
  [bs]
  (mf/base58btc bs))

;; ── did:key (Ed25519 multicodec 0xed 0x01) ───────────────────────────────────
(defn did-key
  "Ed25519 raw 32-byte public key → `did:key:z…` (multicodec 0xed01 + base58btc, multibase
  'z'). An Ed25519 did:key always begins `did:key:z6Mk`."
  [^bytes pub32]
  (let [prefixed (byte-array (concat [(unchecked-byte 0xed) (unchecked-byte 0x01)] (seq pub32)))]
    (str "did:key:z" (b58btc prefixed))))

#?(:clj
   (do
     (defn- raw-ed25519-public
       "Last 32 bytes of the X.509 SubjectPublicKeyInfo = the raw Ed25519 public key."
       [pk]
       (let [enc (.getEncoded pk) n (alength enc)]
         (java.util.Arrays/copyOfRange enc (- n 32) n)))

     (defn gen-keypair
       "GENERATE a fresh Ed25519 keypair IN the actor. Returns
       {:did <did:key> :public <bytes32> :private <PrivateKey>}.
       NOTE: in production the :private is sealed immediately (seal-seed!) and never returned
       to a caller that logs it — here it is returned so the actor can sign present-only."
       []
       (let [kpg (java.security.KeyPairGenerator/getInstance "Ed25519")
             kp (.generateKeyPair kpg)
             pub (raw-ed25519-public (.getPublic kp))]
         {:did (did-key pub) :public pub :private (.getPrivate kp)}))

     (defn sign
       "Sign msg-bytes with the actor's sealed private key (present-only — the key is used,
       never exfiltrated). Returns the signature bytes."
       [priv ^bytes msg]
       (let [s (java.security.Signature/getInstance "Ed25519")]
         (.initSign s priv) (.update s msg) (.sign s)))

     (defn verify
       "Verify a signature against the raw 32-byte public key. Pure, deterministic."
       [pub32 ^bytes msg ^bytes sig]
       (try
         (let [spec (java.security.spec.X509EncodedKeySpec.
                     (byte-array (concat [0x30 0x2a 0x30 0x05 0x06 0x03 0x2b 0x65 0x70 0x03 0x21 0x00]
                                         (seq pub32))))
               pk (.generatePublic (java.security.KeyFactory/getInstance "Ed25519") spec)
               s (java.security.Signature/getInstance "Ed25519")]
           (.initVerify s pk) (.update s msg) (.verify s sig))
         (catch Exception _ false)))

     (defn seal-seed!
       "OPERATOR helper: seal the actor's private seed into the macOS Keychain so it is
       present-only (the runtime reads it to sign; it is never committed/logged). This is the
       one operator step; the actor itself calls `gen-keypair` once at provisioning. Shells to
       the system `security` binary via babashka.process (allowed — invoking an installed tool).
       (1Password mirror: `op item create … etzhayyim-tsubasa-did`.)"
       [^bytes seed32]
       (let [b64 (.encodeToString (java.util.Base64/getEncoder) seed32)]
         (p/shell "security" "add-generic-password" "-U"
                  "-s" "etzhayyim.tsubasa" "-a" "did-seed" "-w" b64)
         :sealed))))

;; ── present-only attestation helper (no key needed to VERIFY) ─────────────────
(defn attest-did-doc
  "Build the message an actor signs to self-certify its did.json CID (kanae diddoc-attest
  pattern): the bytes `did:key|<cid>`. The server never signs; only the actor's sealed key
  does, and anyone verifies with the public did:key."
  [^String did ^String diddoc-cid]
  (str did "|" diddoc-cid))
