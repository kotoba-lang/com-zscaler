(ns hinagata.methods.esign
  "hinagata 雛形 — electronic-contract bridge: template → esign envelope → signature verify.
  1:1 Clojure port of `methods/esign.py` (ADR-2606111954).

  This is the electronic-contract flow (gap #4): it turns a published template into an
  executable, signable contract document and wires it to the EXISTING religious-corp
  e-signature substrate (`com.etzhayyim.esign.*` lexicons, ADR-2605231230 + 2605181100) — the
  document body is content-addressed (kotoba IPFS CIDv1 + SHA-256), the envelope rosters DID
  signers, and each signer authenticates with their OWN WebAuthn passkey (ES256/EdDSA) bound
  to their DID.

  // no-server-key: read-only — hinagata NEVER holds a signing key. It only (1) renders the
  // document deterministically, (2) constructs the UNSIGNED envelope record, and (3) VERIFIES
  // the structural binding of a signature a member produced client-side. Signing happens in the
  // member's authenticator; the server never signs (ADR-2605231525, 9th invariant).

  CONSTITUTIONAL: G1 — a contract a member chooses to execute is the member's act, never
  hinagata advice. hinagata supplies a fair public template + a faithful signing envelope; it
  does not counsel a party to sign, does not represent anyone, and certifies no enforceability.
  The emitted envelope is ALWAYS :status \"pending\" (UNSIGNED).

  House style: pure fns; Python ':…' keyword strings stay strings; closed-vocab/gate → ex-info;
  requires the ported analyze (loader) + cid (content-address). Portable .cljc."
  (:require [clojure.string :as str]
            [hinagata.methods.analyze :as analyze]
            [hinagata.methods.cid :as cid]))

(def has-clause ":has-clause")
(def cite-kinds #{":cites-statute" ":mandated-by"})

(defn- get-or
  "t.get(key, default) — but Python's `.get(k, d)` returns the stored value even if it is nil;
  here keys are always present-or-absent so (get m k default) matches."
  [m k default]
  (get m k default))

(defn- lstrip-colon [s]
  (if (and (string? s) (str/starts-with? s ":")) (subs s 1) s))

(defn render-document
  "Deterministically render a template into a contract body (the bytes that get signed).

  The body lists, in stable order, the template's clauses and the public statute each clause
  rests on — so the signed document itself carries its statutory provenance. `fields` fills
  party/term placeholders; missing fields render as explicit `[___]` blanks (never invented).
  PUBLIC reference only (G1). Closed-vocab failure (not a template) → ex-info."
  ([template-id nodes edges] (render-document template-id nodes edges nil))
  ([template-id nodes edges fields]
   (let [fields (or fields {})
         t (get nodes template-id)]
     (when (or (nil? t) (not= ":template" (get t ":lt/kind")))
       (throw (ex-info (str "not a template: " template-id) {:template-id template-id})))
     (let [clause-ids (for [e edges
                            :when (and (= has-clause (get e ":en/kind"))
                                       (= template-id (get e ":en/from")))]
                        (get e ":en/to"))
           cites (reduce (fn [m e]
                           (if (contains? cite-kinds (get e ":en/kind"))
                             (update m (get e ":en/from") (fnil conj []) (get e ":en/to"))
                             m))
                         {} edges)
           L (transient [])]
       (conj! L (str "# " (get-or t ":template/title" template-id)))
       (conj! L "")
       (conj! L (str "Language: " (get-or t ":template/lang" "—") "  ·  "
                     "License: " (get-or t ":template/license" "Apache-2.0") " + etzhayyim Charter Rider  ·  "
                     "Version: " (get-or t ":template/version" "—") "  ·  "
                     "Disclosed stance: " (lstrip-colon (str (get-or t ":template/stance" "—")))))
       (conj! L "")
       (conj! L (str "> This is a FAIR, openly-licensed template from the hinagata 雛形 commons. It is "
                     "NOT legal advice and NOT a substitute for counsel. The parties execute it as "
                     "their own act. Each clause cites the public law it rests on, for traceability."))
       (conj! L "")
       (conj! L "## Parties & Terms")
       (doseq [key ["party_a" "party_b" "effective_date" "term" "governing_law" "amount"]]
         (if (contains? fields key)
           (conj! L (str "- " key ": " (get fields key)))
           (conj! L (str "- " key ": [___]"))))
       (conj! L "")
       (conj! L "## Clauses")
       (doseq [[i cid-id] (map-indexed vector clause-ids)]
         (let [c (get nodes cid-id {})
               role (lstrip-colon (str (get-or c ":clause/role" "—")))
               opt (lstrip-colon (str (get-or c ":clause/optionality" "—")))]
           (conj! L (str "### " (inc i) ". " (get-or c ":lt/label" cid-id) "  (" role ", " opt ")"))
           (let [st (get cites cid-id [])]
             (when (seq st)
               (let [refs (str/join "; "
                                    (map (fn [s]
                                           (str/trim
                                            (str (get-in nodes [s ":statute/citation"] s) " "
                                                 "(" (get-in nodes [s ":statute/instrument"] "") ")")))
                                         st))]
                 (conj! L (str "_Rests on:_ " refs))))
             (conj! L ""))))
       (conj! L "## Execution")
       (conj! L (str "Executed electronically via the etzhayyim esign substrate "
                     "(com.etzhayyim.esign.envelope): each party signs with a WebAuthn passkey bound to "
                     "their DID. Electronic execution rests on eIDAS Art. 25 (EU), ESIGN/UETA (US) and "
                     "電子署名法 (JP), as cited by the signature clause."))
       (conj! L "")
       (str (str/join "\n" (persistent! L)) "\n")))))

#?(:clj
   (defn build-envelope
     "Construct the UNSIGNED com.etzhayyim.esign.envelope record for a rendered document.
     The body is content-addressed (CIDv1 raw) + SHA-256 hashed — the two independent integrity
     anchors the lexicon requires. hinagata produces this record; it is written to the
     requester's repo and signed client-side (no server key). Returns an ordered map matching
     build_envelope's dict key order."
     ([document requester-did signer-dids]
      (build-envelope document requester-did signer-dids "" "parallel" "1970-01-01T00:00:00Z"))
     ([document requester-did signer-dids subject signing-order created-at]
      (let [body (cid/utf8-bytes document)]
        (when (empty? signer-dids)
          (throw (ex-info "at least one signer required" {})))
        (when-not (contains? #{"sequential" "parallel"} signing-order)
          (throw (ex-info (str "signing_order must be sequential|parallel, got " signing-order)
                          {:signing-order signing-order})))
        (array-map
         "$type" "com.etzhayyim.esign.envelope"
         "requesterDid" requester-did
         "subject" (subs subject 0 (min 256 (count subject)))
         "documentCid" (cid/cidv1-raw body)
         "documentSha256" (cid/sha256-hex body)
         "documentMimeType" "text/markdown"
         "signers" (vec signer-dids)
         "signingOrder" signing-order
         "status" "pending"
         "createdAt" created-at)))))

(defn verify-signature
  "Structurally verify a com.etzhayyim.esign.signature against its envelope.
  Checks the bindings hinagata CAN check without a key: signer on roster, attested document
  hash matches the envelope (anti-tamper), accepted WebAuthn algorithm, present assertion.
  The CRYPTOGRAPHIC assertion verification is kotoba-auth's job. Returns [ok? reasons]."
  [envelope signature]
  (let [reasons (cond-> []
                  (not (contains? (set (get envelope "signers" [])) (get signature "signerDid")))
                  (conj "signerDid not in envelope.signers roster")
                  (not= (get signature "documentSha256") (get envelope "documentSha256"))
                  (conj "documentSha256 mismatch (document tampered between request and sign)")
                  (not (contains? #{"ES256" "EdDSA"} (get signature "webauthnAlgorithm")))
                  (conj (str "unsupported webauthn algorithm: " (get signature "webauthnAlgorithm")))
                  (not (get signature "assertionEnvelope"))
                  (conj "missing assertionEnvelope (encrypted WebAuthn assertion)"))]
    [(empty? reasons) reasons]))

(defn check-completion
  "Return a com.etzhayyim.esign.completedEvent iff every roster signer has a VALID signature.
  Returns nil otherwise. (For `sequential` order, roster-order is enforced by construction.)"
  ([envelope signatures] (check-completion envelope signatures "1970-01-01T00:00:00Z"))
  ([envelope signatures completed-at]
   (let [signers (get envelope "signers" [])
         valid-by-did (reduce (fn [m sig]
                                (let [[ok _] (verify-signature envelope sig)]
                                  (if ok (assoc m (get sig "signerDid") sig) m)))
                              {} signatures)]
     (if-not (every? #(contains? valid-by-did %) signers)
       nil
       (let [ordered (mapv #(get valid-by-did %) signers)]
         (array-map
          "$type" "com.etzhayyim.esign.completedEvent"
          "envelopeCid" (get envelope "documentCid")
          "documentCid" (get envelope "documentCid")
          "documentSha256" (get envelope "documentSha256")
          "signatureCount" (count ordered)
          "completedAt" completed-at))))))

#?(:clj
   (defn -main
     "CLI entry: render a template + build an UNSIGNED esign envelope (member signs client-side).
     File I/O + JSON only at this edge."
     [& argv]
     (let [argv (vec argv)
           here (-> *file* clojure.java.io/file .getParentFile .getParentFile)
           seed (if (and (seq argv) (not (str/starts-with? (first argv) "--")))
                  (clojure.java.io/file (first argv))
                  (clojure.java.io/file here "data" "seed-legal-template-graph.kotoba.edn"))
           idx (fn [flag] (.indexOf argv flag))
           template-id (if (some #{"--template"} argv) (nth argv (inc (idx "--template"))) "tmpl.nda-mutual")
           requester (if (some #{"--requester"} argv) (nth argv (inc (idx "--requester")))
                         "did:web:etzhayyim.com:actor:hinagata")
           signers (vec (for [[i a] (map-indexed vector argv) :when (= a "--signer")] (nth argv (inc i))))
           signers (if (seq signers) signers ["did:plc:alice" "did:plc:bob"])
           outdir (if (some #{"--out"} argv) (clojure.java.io/file (nth argv (inc (idx "--out"))))
                      (clojure.java.io/file here "out"))
           {:keys [nodes edges]} (analyze/load-file* seed)
           doc (render-document template-id nodes edges)
           env (build-envelope doc requester signers
                               (get-in nodes [template-id ":template/title"] "") "parallel"
                               "1970-01-01T00:00:00Z")]
       (.mkdirs outdir)
       (spit (clojure.java.io/file outdir (str "contract-" template-id ".md")) doc)
       (println (str "hinagata esign: " template-id " → " (count (cid/utf8-bytes doc)) " B"))
       (println (str "  documentCid:    " (get env "documentCid")))
       (println (str "  documentSha256: " (get env "documentSha256")))
       (println (str "  signers:        " (str/join ", " signers) " (" (get env "signingOrder") ")"))
       0)))
