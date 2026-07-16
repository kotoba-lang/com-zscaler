#!/usr/bin/env bb
;; iriai 入会 — actor self-certifying did:key identity (self-key autonomy). ADR-2606280900.
(ns iriai.methods.identity
  "identity.cljc — iriai 入会 self-certifying `did:key` (Ed25519), present-only.

  The code realization of *\"the actor makes its OWN key and registers/signs autonomously\"*.
  iriai GENERATES its own Ed25519 keypair; the public half becomes a `did:key` (its
  self-certifying identity, published in did.json `alsoKnownAs` — kaname/tsubasa pattern);
  the private SEED is **sealed** (macOS Keychain / 1Password, CONCEALED, never committed,
  never logged) and used **present-only** — the actor signs/attests with it but never emits it.
  No server holds it; no human types it.

  Why this is charter-clean (no-server-key, ADR-2605231525): the invariant bars a
  *custodial, exfiltratable* signing key on an etzhayyim process. A self-generated key whose
  seed is sealed + present-only — and whose autonomous writes are attributed to a consenting
  human via the member CACAO leash (kotoba_bridge) — keeps custody off the platform and
  accountability on a person. With this key iriai can **self-mint to its OWN kotoba graph**
  (depth-1 self-mint is structurally authorized: the key-derived IPNS name IS the actor's
  graph — no owner hand-off, no shared token) and self-certify its did.json CID. The ONLY
  leg still Council-gated is OUTWARD broadcast to a third-party public network (AT-proto),
  which is a §1.12 outward-action gate, NOT a key-custody gate.

  Pure stdlib (java.security Ed25519 + inlined base58btc); deterministic verify; no network.
  Mirrors tsubasa.methods.identity / kaname's self-key (base58btc inlined here so identity is
  dependency-free + portable to the kototama actor-runtime subset)."
  (:require [clojure.string :as str]
            #?(:clj [babashka.process :as p])))

;; ── base58btc (Bitcoin alphabet) — for did:key multibase 'z' ──────────────────
(def ^:private b58-alphabet "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz")

(defn b58btc
  "Encode bytes as base58btc (no checksum), preserving leading-zero bytes as '1's.
  Self-contained (no multiformats dep) so identity is portable to the kototama subset."
  [bs]
  (let [bs (mapv #(bit-and (long %) 0xff) (seq bs))
        n (count bs)
        zeros (count (take-while zero? bs))
        ;; base-256 → base-58 via repeated division on a big-endian digit vector
        digits (loop [src (vec (drop zeros bs)) out []]
                 (if (empty? src)
                   out
                   (let [[q rem'] (reduce (fn [[acc carry] b]
                                            (let [cur (+ (* carry 256) b)]
                                              [(conj acc (quot cur 58)) (mod cur 58)]))
                                          [[] 0] src)
                         q (vec (drop-while zero? q))]
                     (recur q (conj out rem')))))
        body (apply str (map #(nth b58-alphabet %) (reverse digits)))]
    (str (apply str (repeat zeros \1)) body)))

;; ── did:key (Ed25519 multicodec 0xed 0x01) ───────────────────────────────────
(defn did-key
  "Ed25519 raw 32-byte public key → `did:key:z…` (multicodec 0xed01 + base58btc, multibase
  'z'). An Ed25519 did:key always begins `did:key:z6Mk`."
  [^bytes pub32]
  (let [prefixed (byte-array (concat [(unchecked-byte 0xed) (unchecked-byte 0x01)] (seq pub32)))]
    (str "did:key:z" (b58btc prefixed))))

(def keychain-service "etzhayyim.iriai")
(def keychain-account "did-seed")
(def did-1password-item "etzhayyim-iriai-did")

#?(:clj
   (do
     (defn- raw-ed25519-public
       "Last 32 bytes of the X.509 SubjectPublicKeyInfo = the raw Ed25519 public key."
       [pk]
       (let [enc (.getEncoded pk) n (alength enc)]
         (java.util.Arrays/copyOfRange enc (- n 32) n)))

     (defn raw-ed25519-private-seed
       "Last 32 bytes of the PKCS#8 Ed25519 PrivateKeyInfo = the private seed to seal."
       [priv]
       (let [enc (.getEncoded priv) n (alength enc)]
         (java.util.Arrays/copyOfRange enc (- n 32) n)))

     (defn gen-keypair
       "GENERATE a fresh Ed25519 keypair IN the actor. Returns
       {:did <did:key> :public <bytes32> :private <PrivateKey>}.
       In production :private is sealed immediately (seal-seed!) and never returned to a caller
       that logs it — here it is returned so the actor can sign present-only."
       []
       (let [kpg (java.security.KeyPairGenerator/getInstance "Ed25519")
             kp (.generateKeyPair kpg)
             pub (raw-ed25519-public (.getPublic kp))
             priv (.getPrivate kp)]
         {:did (did-key pub)
          :public pub
          :private priv
          :seed (raw-ed25519-private-seed priv)}))

     (defn sign
       "Sign msg-bytes with the actor's sealed private key (present-only — used, never exfiltrated)."
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
       present-only (the runtime reads it to sign; never committed/logged). One operator step;
       the actor calls `gen-keypair` once at provisioning. Shells to the system `security`
       binary via babashka.process (allowed — invoking an installed tool).
       (1Password mirror: `op item create … etzhayyim-iriai-did`.)"
       [^bytes seed32]
       (let [b64 (.encodeToString (java.util.Base64/getEncoder) seed32)]
         (p/shell "security" "add-generic-password" "-U"
                  "-s" keychain-service "-a" keychain-account "-w" b64)
         :sealed))))

;; ── present-only attestation helper (no key needed to VERIFY) ─────────────────
(defn attest-did-doc
  "Build the message an actor signs to self-certify its did.json CID (kanae diddoc-attest
  pattern): the bytes `did:key|<cid>`. The server never signs; only the actor's sealed key
  does, and anyone verifies with the public did:key."
  [^String did ^String diddoc-cid]
  (str did "|" diddoc-cid))

;; ── CLI (bb) — provision: generate + (optionally) seal ────────────────────────
#?(:clj
   (defn -main [& args]
     (let [{:keys [did public private seed]} (gen-keypair)
           msg (.getBytes "iriai self-key provisioning probe" "UTF-8")
           sig (sign private msg)
           ok (verify public msg sig)]
       (println (str "iriai self-key generated: " did))
       (println (str "  sign+verify round-trip: " ok))
       (println (str "  did:key prefix z6Mk: " (str/starts-with? did "did:key:z6Mk")))
       (if (some #{"--seal"} args)
         (do
           (seal-seed! seed)
           (println (str "  sealed seed into Keychain " keychain-service)))
         (println "  (dry-run: pass --seal to seal the seed into the Keychain)")))))

#?(:clj
   (when (= *file* (System/getProperty "babashka.file"))
     (apply -main *command-line-args*)))
