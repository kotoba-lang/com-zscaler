(ns hinagata.tests.test-esign
  "hinagata 雛形 — electronic-contract (esign bridge) tests (ADR-2606111954). 1:1 Clojure port
  of tests/test_esign.py. Pure fns (+ #?(:clj) content-address / file-read edges).

  Verifies the contract-signing flow that wires the EXISTING com.etzhayyim.esign.* lexicons:
    - a template renders to a deterministic document carrying its statutory provenance
    - the document is content-addressed (CIDv1 raw, ipfs-parity) + SHA-256 hashed
    - build-envelope produces a schema-shaped com.etzhayyim.esign.envelope — UNSIGNED (G8)
    - verify-signature enforces roster membership + document-hash anti-tamper binding
    - check-completion fires only when EVERY roster signer has a valid signature
    - no-server-key: hinagata never signs — it builds UNSIGNED records + verifies structure
    - G1: the rendered body is a COMMONS template, NOT advice; citations are DISCLOSED facts"
  (:require [clojure.test :refer [deftest is testing run-tests]]
            [clojure.string :as str]
            [clojure.java.io :as io]
            [hinagata.methods.analyze :as analyze]
            [hinagata.methods.cid :as cid]
            [hinagata.methods.esign :as esign]))

(def actor-dir (-> *file* io/file .getParentFile .getParentFile))
(def seed (io/file actor-dir "data" "seed-legal-template-graph.kotoba.edn"))
(defn load-seed [] (analyze/load-file* seed))

(deftest test-render-is-deterministic-and-traceable
  (let [{:keys [nodes edges]} (load-seed)
        a (esign/render-document "tmpl.dpa-gdpr" nodes edges)
        b (esign/render-document "tmpl.dpa-gdpr" nodes edges)]
    (is (= a b) "document render is not deterministic")
    ;; the rendered body must carry the statute citations the clauses rest on (traceability)
    (is (str/includes? a "GDPR Art. 28") "rendered DPA missing its mandating statute citation")
    (is (str/includes? a "NOT legal advice") "G1 disclaimer missing from rendered document")
    ;; missing fields render as explicit blanks, never invented
    (is (str/includes? a "[___]") "missing fields should render as explicit blanks")))

(deftest test-render-rejects-non-template
  (let [{:keys [nodes edges]} (load-seed)]
    (is (thrown? clojure.lang.ExceptionInfo
                 (esign/render-document "cl.confidentiality" nodes edges))
        "render-document accepted a non-template node")))

(deftest test-envelope-shape-and-content-address
  (let [{:keys [nodes edges]} (load-seed)
        doc (esign/render-document "tmpl.nda-mutual" nodes edges)
        env (esign/build-envelope doc "did:web:etzhayyim.com:actor:x"
                                  ["did:plc:alice" "did:plc:bob"] "NDA" "parallel"
                                  "2026-06-11T00:00:00Z")]
    (is (= "com.etzhayyim.esign.envelope" (get env "$type")))
    (doseq [k ["requesterDid" "documentCid" "documentSha256" "signers" "signingOrder"
               "status" "createdAt"]]
      (is (contains? env k) (str "envelope missing required field " k)))
    ;; G8 — the envelope is emitted UNSIGNED (no-server-key)
    (is (= "pending" (get env "status")) "envelope must be emitted UNSIGNED (status pending)")
    ;; content-address matches an independent recompute (ipfs-parity CID + hash)
    (let [raw (cid/utf8-bytes doc)]
      (is (= (get env "documentCid") (cid/cidv1-raw raw)))
      (is (= (get env "documentSha256") (cid/sha256-hex raw)))
      (is (str/starts-with? (get env "documentCid") "bafkrei") "not a CIDv1 raw/sha2-256")
      (is (and (= 66 (count (get env "documentSha256")))
               (str/starts-with? (get env "documentSha256") "0x"))))))

(defn- sig
  ([env did] (sig env did "ES256" false))
  ([env did alg tamper]
   {"$type" "com.etzhayyim.esign.signature"
    "signerDid" did
    "documentSha256" (if tamper (str "0x" (apply str (repeat 32 "00"))) (get env "documentSha256"))
    "webauthnAlgorithm" alg
    "assertionEnvelope" "ciphertext-stub"
    "signedAt" "2026-06-11T01:00:00Z"}))

(deftest test-verify-signature-binding
  (let [{:keys [nodes edges]} (load-seed)
        doc (esign/render-document "tmpl.nda-mutual" nodes edges)
        env (esign/build-envelope doc "did:web:x" ["did:plc:alice" "did:plc:bob"])]
    (let [[ok reasons] (esign/verify-signature env (sig env "did:plc:alice"))]
      (is ok (str "valid signature rejected: " reasons)))
    (let [[ok reasons] (esign/verify-signature env (sig env "did:plc:mallory"))]
      (is (and (not ok) (some #(str/includes? % "roster") reasons)) "off-roster signer accepted"))
    (let [[ok reasons] (esign/verify-signature env (sig env "did:plc:alice" "ES256" true))]
      (is (and (not ok) (some #(or (str/includes? % "tamper") (str/includes? % "mismatch")) reasons))
          "tampered document hash accepted"))
    (let [[ok reasons] (esign/verify-signature env (sig env "did:plc:alice" "RS256" false))]
      (is (and (not ok) (some #(str/includes? % "algorithm") reasons)) "unsupported algorithm accepted"))))

(deftest test-completion-requires-all-signers
  (let [{:keys [nodes edges]} (load-seed)
        doc (esign/render-document "tmpl.loan-qard" nodes edges)
        env (esign/build-envelope doc "did:web:x" ["did:plc:alice" "did:plc:bob"]
                                  "" "sequential" "1970-01-01T00:00:00Z")]
    ;; only one of two signed → no completion
    (is (nil? (esign/check-completion env [(sig env "did:plc:alice")])))
    ;; both signed → completedEvent fires
    (let [ev (esign/check-completion env [(sig env "did:plc:alice") (sig env "did:plc:bob")]
                                     "2026-06-11T02:00:00Z")]
      (is (and (some? ev) (= "com.etzhayyim.esign.completedEvent" (get ev "$type"))))
      (is (= 2 (get ev "signatureCount")))
      (is (= (get ev "documentSha256") (get env "documentSha256"))))
    ;; a tampered signature does not count toward completion
    (is (nil? (esign/check-completion
               env [(sig env "did:plc:alice") (sig env "did:plc:bob" "ES256" true)])))))

(deftest test-no-server-key-marker-present
  (testing "The esign bridge must declare it holds no signing key (ADR-2605231525)."
    (let [src (slurp (io/file actor-dir "methods" "esign.cljc"))]
      (is (str/includes? src "no-server-key") "esign.cljc missing no-server-key declaration"))))

(deftest test-g1-commons-not-the-practice-of-law
  (testing "G1: the rendered document is a COMMONS template, never advice; citations are DISCLOSED facts."
    (let [{:keys [nodes edges]} (load-seed)
          doc (esign/render-document "tmpl.dpa-gdpr" nodes edges)]
      ;; the disclaimer that it is NOT advice / NOT a substitute for counsel is mandatory
      (is (str/includes? doc "NOT legal advice"))
      (is (str/includes? doc "NOT a substitute for counsel"))
      (is (str/includes? doc "execute it as their own act"))
      ;; a citation appears under '_Rests on:_' as a DISCLOSED fact, never a validity verdict
      (is (str/includes? doc "_Rests on:_"))
      (is (not (str/includes? doc "is valid")))
      (is (not (str/includes? doc "is enforceable")))
      (is (not (str/includes? doc "we advise"))))))
