(ns matsurigoto.methods.sign-capability
  "matsurigoto 政 — R1.C: the sign / authority layer (verify-only, NO server key).
  1:1 Clojure port of `methods/sign_capability.py` (ADR-2606062300 + ADR-2605231525).

  R0 artifacts are unsigned by construction (G1). This layer attaches a signature WITHOUT
  matsurigoto ever holding a private key: the signature is produced EXTERNALLY by the
  governing organ —

    principal A (\":sovereign-governance\") : the Council 5-of-7 Safe / 1 SBT=1 vote, an
                                            etzhayyim constitutional organ
                                            (did:web:etzhayyim.com:council:*).
    principal B (\":supplied-to-state\")    : the adopting state's OWN key (its ICAO-PKD
                                            CSCA/DS for passports, its tax-authority cert,
                                            etc.) — NOT an etzhayyim did.

  matsurigoto only (a) emits the canonical payload to be signed and (b) ATTACHES a signature
  the caller brings back, after checking the signer is a legitimate authority for the
  principal. It NEVER mints a signature. This mirrors okaimono's member-principal checkout
  (server-sig refused).

    SIGNER-HELD-PRIVATE-KEY = false — there is no key here; `sign-server-side` always RAISES.

  HONEST R1: this verifies the STRUCTURE (legitimate signer for the principal + payload
  integrity via sha256). Real ed25519 / Safe-threshold cryptographic verification is R2.

  Crypto: the capability digest is SHA-256 over canonical JSON —
  `json.dumps(obj, sort_keys=True, ensure_ascii=False, separators=(',',':'))` then
  `hashlib.sha256(blob.encode('utf-8')).hexdigest()`. Reproduced byte-for-byte by
  `canonical-payload`; the hashing primitive is isolated at the #?(:clj) edge via
  `java.security.MessageDigest \"SHA-256\"` (a :cljs/wasm impl slots in there).

  House style: artifacts are Clojure maps with the Python string keys; Python ':…' strings stay
  strings; closed-vocab/gate violations → ex-info; pure fns; hashing at the host edge only."
  (:require [clojure.string :as str]))

(def SIGNER-HELD-PRIVATE-KEY
  "G1 / ADR-2605231525 — matsurigoto holds no private key."
  false)

(def ^:private etzhayyim-council-prefix "did:web:etzhayyim.com:council")

;; ── canonical JSON (json.dumps sort_keys=True, ensure_ascii=False, separators=(",",":")) ──
;; The values present in a matsurigoto artifact payload are strings, booleans, ints, floats,
;; nil, and nested maps/vectors. Reproduce Python's compact serializer byte-for-byte.

(defn- json-escape
  "json.dumps string body for the chars json.dumps escapes by default (ensure_ascii=False keeps
  non-ASCII literal). Mirrors CPython's c_encode_basestring (the default escape set)."
  [s]
  (let [sb (StringBuilder.)]
    (doseq [^char c (str s)]
      (let [code (int c)]
        (cond
          (= c \") (.append sb "\\\"")
          (= c \\) (.append sb "\\\\")
          (= c \newline) (.append sb "\\n")
          (= c \return) (.append sb "\\r")
          (= c \tab) (.append sb "\\t")
          (= code 8) (.append sb "\\b")
          (= code 12) (.append sb "\\f")
          (< code 0x20) (.append sb (format "\\u%04x" code))
          :else (.append sb c))))
    (.toString sb)))

(defn- float-repr
  "Python repr(float) for the finite decimal amounts this method handles (currency values).
  json.dumps emits float(x) via repr; for whole/decimal magnitudes in scope Java's
  Double/toString agrees (200000.0 → \"200000.0\", 0.0 → \"0.0\", 12345.67 → \"12345.67\").
  (Scientific-notation magnitudes — |x| ≥ 1e16 or tiny — are a deferred :cljs/edge case.)"
  [^double d]
  #?(:clj (Double/toString d) :cljs (str d)))

(declare json-map)

(defn- json-value
  "Serialize one value to compact JSON exactly as json.dumps (separators=(\",\",\":\")) does."
  [v]
  (cond
    (nil? v) "null"
    (true? v) "true"
    (false? v) "false"
    (string? v) (str "\"" (json-escape v) "\"")
    #?(:clj (instance? Double v) :cljs (and (number? v) (not (integer? v))))
    (float-repr (double v))
    (integer? v) (str v)
    (map? v) (json-map v)
    (sequential? v) (str "[" (str/join "," (map json-value v)) "]")
    ;; a ":…" keyword-string or anything else stringifies as its str form (kept literal)
    :else (str "\"" (json-escape (str v)) "\"")))

(defn- json-map
  "Serialize a map with sort_keys=True, separators=(\",\",\":\")."
  [m]
  (let [pairs (sort-by (fn [[k _]] (str k)) m)]
    (str "{"
         (str/join "," (map (fn [[k v]] (str "\"" (json-escape (str k)) "\":" (json-value v))) pairs))
         "}")))

(defn canonical-json
  "json.dumps(obj, sort_keys=True, ensure_ascii=False, separators=(',',':')) — byte-identical."
  [obj]
  (json-value obj))

;; ── SHA-256 over the canonical bytes — the hashing primitive, isolated at the host edge ──
#?(:clj
   (defn- sha256-hex
     "hashlib.sha256(s.encode('utf-8')).hexdigest() — host JVM MessageDigest at the :clj edge."
     [^String s]
     (let [md (java.security.MessageDigest/getInstance "SHA-256")
           bs (.digest md (.getBytes s "UTF-8"))
           sb (StringBuilder.)]
       (doseq [b bs]
         (.append sb (format "%02x" (bit-and (long b) 0xff))))
       (.toString sb))))

(defn canonical-payload
  "Deterministic bytes-to-be-signed for an artifact or datom batch
  (sha256 over canonical JSON). 1:1 with canonical_payload."
  [obj]
  (sha256-hex (canonical-json obj)))

(defn- payload-digest
  "Hash the SUBSTANTIVE content only — excluding `proof` and the `status` lifecycle marker
  (which flips unsigned→signed) so the digest is stable across signing. 1:1 with _payload."
  [artifact]
  (canonical-payload (dissoc artifact "proof" "status")))

(defn- legitimate-signer?
  "principal A must be signed by an etzhayyim Council organ; principal B by a NON-etzhayyim
  (the adopting state's own) did — etzhayyim never holds the state's key. 1:1 _legitimate_signer."
  [signer-did authority-mode]
  (let [is-council (str/starts-with? (str signer-did) etzhayyim-council-prefix)]
    (cond
      (= authority-mode ":sovereign-governance") is-council
      (= authority-mode ":supplied-to-state") (not (str/starts-with? (str signer-did) "did:web:etzhayyim.com"))
      :else false)))

(defn sign-server-side
  "STRUCTURAL no-server-key: there is no path for matsurigoto to sign. Always raises
  (the okaimono `authorize_payment` server-sig-refused pattern, ADR-2605231525)."
  [& _args]
  (throw (ex-info
          (str "no-server-key (ADR-2605231525): matsurigoto holds no signing key and signs nothing. "
               "The Council Safe (principal A) or the adopting state (principal B) signs externally; "
               "use attach-external-proof to attach their signature.")
          {:gate :no-server-key})))

(defn- constant-time-equal?
  "hmac.compare_digest analogue — length-independent, constant-time string comparison.
  (Used by verify-proof's digest match; never short-circuits on the first differing byte.)"
  [a b]
  (let [a (str a) b (str b)
        la (count a) lb (count b)
        n (max la lb)]
    (loop [i 0, acc (bit-xor la lb)]
      (if (< i n)
        (recur (inc i)
               (bit-or acc (bit-xor (if (< i la) (int (.charAt a i)) 0)
                                    (if (< i lb) (int (.charAt b i)) 0))))
        (zero? acc)))))

(defn attach-external-proof
  "Attach an EXTERNALLY-produced signature to an unsigned artifact. Pure; returns a NEW map.
  1:1 with attach_external_proof.

  Raises (ex-info) if the artifact is already signed (G1: a module artifact must arrive
  unsigned), the signer is illegitimate for the principal, or the signature is empty."
  [artifact {:keys [signer-did authority-mode signature signed-at]}]
  (when (some? (get artifact "proof"))
    (throw (ex-info "artifact already signed — a module artifact must arrive unsigned (G1)"
                    {:gate :G1-unsigned-on-arrival})))
  (when (or (nil? signature) (= signature ""))
    (throw (ex-info "no external signature supplied — matsurigoto mints none (no-server-key)"
                    {:gate :no-server-key})))
  (when-not (legitimate-signer? signer-did authority-mode)
    (throw (ex-info (str "illegitimate signer " (pr-str signer-did) " for " authority-mode)
                    {:gate :illegitimate-signer :signer-did signer-did :authority-mode authority-mode})))
  (-> artifact
      (assoc "proof" {"signer_did" signer-did
                      "authority_mode" authority-mode
                      "signature" signature
                      "signed_at" signed-at
                      "payload_sha256" (payload-digest artifact)})
      (assoc "status" (str/replace (get artifact "status") "unsigned" "signed"))
      (assoc "server_held_authority" false)))  ; unchanged — still no operator key

(defn verify-proof
  "Structural verification: a proof is present, by a legitimate signer, over the matching
  payload. (Cryptographic ed25519/Safe-threshold check is R2.) 1:1 with verify_proof."
  [signed-artifact]
  (let [proof (get signed-artifact "proof")]
    (cond
      (or (not proof) (let [s (get proof "signature")] (or (nil? s) (= s "")))) false
      (not (legitimate-signer? (get proof "signer_did") (get proof "authority_mode"))) false
      :else (constant-time-equal? (get proof "payload_sha256") (payload-digest signed-artifact)))))
