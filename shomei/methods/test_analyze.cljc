(ns shomei.methods.test-analyze
  "test_analyze.cljc — 証明 (shomei) full closure tests. ADR-2606072100.
  1:1 Clojure port of test_analyze.py + test_factors.py + test_claims.py + test_verify.py
  + test_revoke.py + test_aggregate.py (every assertion satisfiable with this closure).
  clojure.test. The end-to-end `run` is pure over the parsed seed (I/O at the #?(:clj) -main
  edge), so the membrane is exercised via `run` directly — same results."
  (:require [clojure.test :refer [deftest is run-tests testing]]
            [clojure.string :as str]
            [shomei.methods.factors :as f]
            [shomei.methods.claims :as c]
            [shomei.methods.revoke :as rev]
            [shomei.methods.aggregate :as agg]
            [shomei.methods.verify :as v]
            [shomei.methods.analyze :as a]
            #?(:clj [shomei.methods.edn :as e])))

(def seed-path "20-actors/shomei/data/seed-claims.json")

;; ── helper: raises-with ─────────────────────────────────────────────────────────
(defmacro raises-containing [substr & body]
  `(let [r# (try (do ~@body ::no-throw)
                 (catch #?(:clj Throwable :cljs :default) ex#
                   (#?(:clj #(.getMessage ^Throwable %) :cljs ex-message) ex#)))]
     (is (and (string? r#) (str/includes? r# ~substr))
         (str "expected throw containing " (pr-str ~substr) ", got " (pr-str r#)))))

;; ════════════════════════ test_factors.py ════════════════════════
(deftest test-every-kind-has-class-and-proofs
  (doseq [k f/FACTOR_KINDS]
    (is (contains? f/FACTOR_CLASS k))
    (is (seq (get f/ALLOWED_PROOFS k)) (str k " has no allowed proofs"))))

(deftest test-class-partition
  (is (= "key" (get f/FACTOR_CLASS "wallet-evm") (get f/FACTOR_CLASS "wallet-btc")))
  (is (= "government" (get f/FACTOR_CLASS "gov-mynumber")))
  (is (= "covenant" (get f/FACTOR_CLASS "etz-at-oath")))
  (is (= "device" (get f/FACTOR_CLASS "webauthn")))
  (is (= "social" (get f/FACTOR_CLASS "sns-x"))))

(deftest test-gov-factors-set
  (is (= #{"gov-mynumber" "gov-passport" "gov-license"} f/GOV_FACTORS)))

(deftest test-public-handle-only-wallet-sns
  (doseq [k f/PUBLIC_HANDLE_FACTORS]
    (is (or (str/starts-with? k "wallet-") (str/starts-with? k "sns-"))))
  (is (not (contains? f/PUBLIC_HANDLE_FACTORS "gov-mynumber")))
  (is (not (contains? f/PUBLIC_HANDLE_FACTORS "etz-at-oath"))))

(deftest test-factor-class-unknown-raises
  (raises-containing "unknown factorKind" (f/factor-class "nope")))

(deftest test-ial-levels
  (is (= 0 (f/assurance-level #{} 0)))
  (is (= 1 (f/assurance-level #{"key"} 1)))
  (is (= 2 (f/assurance-level #{"key" "social"} 2)))
  (is (= 3 (f/assurance-level #{"key" "covenant"} 2)))
  (is (= 4 (f/assurance-level #{"device" "government"} 2))))

(deftest test-two-key-wallets-is-one-class-not-ial2
  (is (= 1 (f/assurance-level #{"key"} 2))))

(deftest test-covenant-alone-one-factor-is-ial1
  (is (= 1 (f/assurance-level #{"covenant"} 1))))

(deftest test-proof-of-personhood
  (is (true? (f/proof-of-personhood 2 2)))
  (is (false? (f/proof-of-personhood 1 1)))
  (is (false? (f/proof-of-personhood 2 1))))

;; ════════════════════════ test_claims.py ════════════════════════
(defn- valid-evm []
  (c/build-claim
    {:subject-did "did:web:etzhayyim.com:actor:x"
     :factor-kind "wallet-evm" :proof-kind "eip191" :challenge-nonce "nonce-1"
     :external-subject-hash (c/external-subject-hash "salt" "0xabc")
     :issued-at 1781000000 :subject-sig "sig" :verified true :external-handle "0xabc…"}))

(deftest test-valid-evm-claim-builds
  (let [c* (valid-evm)]
    (is (= "key" (get c* "factorClass")))
    (c/validate-claim c*)))

(deftest test-external-subject-hash-stable-and-case-insensitive
  (let [a (c/external-subject-hash "s" "0xABC")
        b (c/external-subject-hash "s" "0xabc ")]
    (is (= a b))
    (is (not (str/includes? a "=")))))

(deftest test-g4-wrong-proof-for-factor-raises
  (raises-containing "G4"
    (c/build-claim {:subject-did "did:web:x" :factor-kind "wallet-evm" :proof-kind "bip322"
                    :challenge-nonce "n" :external-subject-hash "h" :issued-at 1 :subject-sig "s"})))

(deftest test-g3-gov-requires-encrypted-cid
  (raises-containing "encryptedPayloadCid"
    (c/build-claim {:subject-did "did:web:x" :factor-kind "gov-mynumber" :proof-kind "nfc-jpki"
                    :challenge-nonce "n" :external-subject-hash "h" :issued-at 1 :subject-sig "s"})))

(deftest test-g3-gov-must-not-carry-plaintext-handle
  (raises-containing "plaintext externalHandle"
    (c/build-claim {:subject-did "did:web:x" :factor-kind "gov-passport" :proof-kind "nfc-jpki"
                    :challenge-nonce "n" :external-subject-hash "h" :issued-at 1 :subject-sig "s"
                    :external-handle "PASSPORT-NO-123" :encrypted-payload-cid "bafyenc"})))

(deftest test-g3-gov-valid-with-cid-no-handle
  (let [c* (c/build-claim {:subject-did "did:web:x" :factor-kind "gov-mynumber" :proof-kind "nfc-jpki"
                           :challenge-nonce "n" :external-subject-hash "h" :issued-at 1 :subject-sig "s"
                           :encrypted-payload-cid "bafyenc" :verified true})]
    (is (= "government" (get c* "factorClass")))))

(deftest test-g3-covenant-factor-cannot-carry-handle
  (raises-containing "may not carry a plaintext externalHandle"
    (c/build-claim {:subject-did "did:web:x" :factor-kind "etz-at-oath" :proof-kind "at-record-sig"
                    :challenge-nonce "n" :external-subject-hash "h" :issued-at 1 :subject-sig "s"
                    :external-handle "leak"})))

(deftest test-g7-no-server-sig-field
  (raises-containing "G7 no-server-key"
    (c/validate-claim (assoc (valid-evm) "serverSig" "platform-key-signed"))))

(deftest test-g7-subject-sig-mandatory
  (raises-containing "subjectSig"
    (c/build-claim {:subject-did "did:web:x" :factor-kind "wallet-evm" :proof-kind "eip191"
                    :challenge-nonce "n" :external-subject-hash "h" :issued-at 1 :subject-sig ""})))

(deftest test-g1-did-required
  (raises-containing "subjectDid must be a DID"
    (c/build-claim {:subject-did "aaron" :factor-kind "wallet-evm" :proof-kind "eip191"
                    :challenge-nonce "n" :external-subject-hash "h" :issued-at 1 :subject-sig "s"})))

(deftest test-canonical-bytes-exclude-sig-and-handle
  (let [b (c/canonical-claim-bytes (valid-evm))
        s #?(:clj (String. ^bytes b "UTF-8") :cljs b)]
    (is (not (str/includes? s "subjectSig")))
    (is (not (str/includes? s "externalHandle")))
    (is (str/includes? s "externalSubjectHash"))))

;; ════════════════════════ test_revoke.py ════════════════════════
(def REV-SUB "did:web:etzhayyim.com:actor:rev")

(defn- mk-claim
  ([kind cid] (mk-claim kind cid 100 true))
  ([kind cid issued verified]
   {"subjectDid" REV-SUB "factorKind" kind "verified" verified "issuedAt" issued "cid" cid}))

(defn- mk-rev
  [kind claim-ref & {:keys [at reason subject] :or {at 200 reason "key-rotated" subject REV-SUB}}]
  {"subjectDid" subject "claimRef" claim-ref "factorKind" kind
   "reason" reason "revokedAt" at "subjectSig" "sig"})

(deftest test-valid-revocation
  (rev/validate-revocation (mk-rev "wallet-evm" "claim:1")))

(deftest test-g5-only-owner-revokes
  (raises-containing "only the owner revokes"
    (rev/validate-revocation (mk-rev "wallet-evm" "claim:1" :subject "did:web:other")
                             (mk-claim "wallet-evm" "claim:1"))))

(deftest test-unknown-reason-raises
  (raises-containing "unknown revocation reason"
    (rev/validate-revocation (mk-rev "wallet-evm" "c" :reason "nuke"))))

(deftest test-active-excludes-revoked-by-ref
  (let [claims [(mk-claim "wallet-evm" "claim:1") (mk-claim "sns-x" "claim:2")]
        revs [(mk-rev "wallet-evm" "claim:1")]]
    (is (= #{"sns-x"} (rev/active-verified-factors claims revs)))))

(deftest test-active-excludes-unverified
  (is (= #{} (rev/active-verified-factors [(mk-claim "wallet-evm" "claim:1" 100 false)] []))))

(deftest test-revocation-does-not-delete-history
  (let [claims [(mk-claim "wallet-evm" "claim:1")]
        revs [(mk-rev "wallet-evm" "claim:1")]]
    (rev/active-verified-factors claims revs)
    (is (= "wallet-evm" (get (first claims) "factorKind")))
    (is (true? (get (first claims) "verified")))))

(deftest test-revoke-by-kind-when-no-ref-link
  (let [claims [(mk-claim "sns-github" nil 100 true)]
        revs [(mk-rev "sns-github" "missing-ref" :at 150)]]
    (is (= #{} (rev/active-verified-factors claims revs)))))

;; ════════════════════════ test_aggregate.py ════════════════════════
(def AGG-SUB "did:web:etzhayyim.com:actor:agg")

(deftest test-did-only-ial0
  (let [c* (agg/aggregate AGG-SUB #{} :issued-at 1)]
    (is (= 0 (get c* "assuranceLevel")))
    (is (false? (get c* "proofOfPersonhood")))))

(deftest test-single-factor-ial1
  (let [c* (agg/aggregate AGG-SUB #{"wallet-evm"} :issued-at 1)]
    (is (= 1 (get c* "assuranceLevel")))
    (is (= 1 (get c* "distinctClasses")))))

(deftest test-multi-class-ial2-is-pop
  (let [c* (agg/aggregate AGG-SUB #{"wallet-evm" "sns-github"} :issued-at 1)]
    (is (= 2 (get c* "assuranceLevel")))
    (is (true? (get c* "proofOfPersonhood")))))

(deftest test-two-wallets-one-class-not-pop
  (let [c* (agg/aggregate AGG-SUB #{"wallet-evm" "wallet-btc"} :issued-at 1)]
    (is (= 1 (get c* "distinctClasses")))
    (is (= 1 (get c* "assuranceLevel")))
    (is (false? (get c* "proofOfPersonhood")))))

(deftest test-covenant-bound-ial3-self-issued
  (let [c* (agg/aggregate AGG-SUB #{"webauthn" "etz-adherent-sbt"} :issued-at 1)]
    (is (= 3 (get c* "assuranceLevel")))
    (is (= AGG-SUB (get c* "issuer")))))

(deftest test-gov-ial4-council-issued
  (let [c* (agg/aggregate AGG-SUB #{"webauthn" "gov-mynumber"} :issued-at 1)]
    (is (= 4 (get c* "assuranceLevel")))
    (is (str/ends-with? (get c* "issuer") "council:attestor"))))

(deftest test-no-pii-in-credential
  (let [c* (agg/aggregate AGG-SUB #{"wallet-evm" "sns-x"} :issued-at 1)
        blob (pr-str c*)]
    (is (not (str/includes? blob "0x")))
    (is (not (str/includes? blob "@")))
    (is (= (agg/did-hash AGG-SUB) (get c* "subjectDidHash")))
    (is (= AGG-SUB (get c* "issuer")))))

(deftest test-no-social-credit-fields
  (let [c* (agg/aggregate AGG-SUB #{"wallet-evm" "sns-x"} :issued-at 1)]
    (doseq [forbidden ["score" "rank" "reputation" "trustScore" "worth" "behavior"]]
      (is (not (contains? c* forbidden)) (str "G8: " forbidden " must not exist")))))

(deftest test-w3c-vc-shape
  (let [c* (agg/aggregate AGG-SUB #{"wallet-evm" "sns-x"} :issued-at 1)
        vc (agg/to-w3c-vc AGG-SUB c*)]
    (is (= ["VerifiableCredential" "EtzhayyimPersonhoodCredential"] (get vc "type")))
    (is (= AGG-SUB (get-in vc ["credentialSubject" "id"])))
    (is (contains? (get vc "credentialSubject") "proofOfPersonhood"))))

(deftest test-aggregate-helpers
  (is (= "government-verified" (agg/assurance-label 4)))
  (is (true? (agg/is-covenant-bound #{"etz-at-oath"})))
  (is (false? (agg/is-covenant-bound #{"wallet-evm"}))))

;; ════════════════════════ test_verify.py ════════════════════════
(def VSUB "did:web:etzhayyim.com:actor:t")
(def VSALT "salt-t")
(def TS 1781000000)

(defn- vref [secret ^bytes msg] (#'v/hmac-sha256-hex secret msg))
(defn- sb [^String s] #?(:clj (.getBytes s "UTF-8") :cljs s))

(defn- vmint [factor-kind proof-kind identifier secret & {:keys [handle cid nonce]
                                                          :or {nonce "nonce-t"}}]
  (let [esh (c/external-subject-hash VSALT identifier)
        skel {"subjectDid" VSUB "factorKind" factor-kind "factorClass" (get f/FACTOR_CLASS factor-kind)
              "proofKind" proof-kind "challengeNonce" nonce "externalSubjectHash" esh
              "verified" true "issuedAt" TS}
        claim (c/build-claim {:subject-did VSUB :factor-kind factor-kind :proof-kind proof-kind
                              :challenge-nonce nonce :external-subject-hash esh :issued-at TS
                              :subject-sig (vref secret (c/canonical-claim-bytes skel))
                              :verified true :external-handle handle :encrypted-payload-cid cid})
        challenge {"subjectDid" VSUB "factorKind" factor-kind "nonce" nonce
                   "issuedAt" TS "expiresAt" (+ TS 600)}
        pm {"proof" (vref secret (sb (str nonce "|" esh)))}]
    [claim challenge pm]))

(deftest test-routing-table-covers-every-proof-kind
  (doseq [p f/PROOF_KINDS]
    (is (contains? v/PROOF_ROUTING p) (str "proofKind " p " not in PROOF_ROUTING"))))

(deftest test-valid-claim-verifies
  (let [[claim ch pm] (vmint "wallet-evm" "eip191" "0xabc" "sec1" :handle "0xabc")
        res (v/verify-claim claim ch (v/reference-verifier {[VSUB "wallet-evm"] "sec1"})
                            :proof-material pm :now (+ TS 10))]
    (is (get res "verified"))
    (is (= "ok" (get res "reason")))))

(deftest test-bad-proof-fails
  (let [[claim ch pm] (vmint "wallet-evm" "eip191" "0xabc" "sec1" :handle "0xabc")
        pm (assoc pm "proof" "deadbeef")
        res (v/verify-claim claim ch (v/reference-verifier {[VSUB "wallet-evm"] "sec1"})
                            :proof-material pm :now (+ TS 10))]
    (is (not (get res "verified")))
    (is (= "proof verification failed" (get res "reason")))))

(deftest test-wrong-subject-secret-fails-signature
  (let [[claim ch pm] (vmint "wallet-evm" "eip191" "0xabc" "sec1" :handle "0xabc")
        res (v/verify-claim claim ch (v/reference-verifier {[VSUB "wallet-evm"] "WRONG"})
                            :proof-material pm :now (+ TS 10))]
    (is (not (get res "verified")))))

(deftest test-challenge-subject-mismatch
  (let [[claim ch pm] (vmint "wallet-evm" "eip191" "0xabc" "sec1" :handle "0xabc")
        ch (assoc ch "subjectDid" "did:web:someone-else")
        res (v/verify-claim claim ch (v/reference-verifier {[VSUB "wallet-evm"] "sec1"})
                            :proof-material pm :now (+ TS 10))]
    (is (= "challenge subjectDid mismatch" (get res "reason")))))

(deftest test-challenge-expired
  (let [[claim ch pm] (vmint "wallet-evm" "eip191" "0xabc" "sec1" :handle "0xabc")
        res (v/verify-claim claim ch (v/reference-verifier {[VSUB "wallet-evm"] "sec1"})
                            :proof-material pm :now (+ TS 999999))]
    (is (= "challenge expired" (get res "reason")))))

(deftest test-nonce-single-use
  (let [[claim ch pm] (vmint "wallet-evm" "eip191" "0xabc" "sec1" :handle "0xabc")
        verf (v/reference-verifier {[VSUB "wallet-evm"] "sec1"})
        seen (atom #{})
        first* (v/verify-claim claim ch verf :proof-material pm :now (+ TS 10) :seen-nonces seen)
        second* (v/verify-claim claim ch verf :proof-material pm :now (+ TS 10) :seen-nonces seen)]
    (is (get first* "verified"))
    (is (= "nonce already consumed (replay)" (get second* "reason")))))

(deftest test-gov-proof-gated-raises
  (let [[claim ch pm] (vmint "gov-mynumber" "nfc-jpki" "MY" "sec1" :cid "bafyenc")]
    (raises-containing "Council-gated"
      (v/verify-claim claim ch (v/reference-verifier {[VSUB "gov-mynumber"] "sec1"})
                      :proof-material pm :now (+ TS 10)))))

(deftest test-gov-proof-passes-when-gate-open
  (let [[claim ch pm] (vmint "gov-mynumber" "nfc-jpki" "MY" "sec1" :cid "bafyenc")
        res (v/verify-claim claim ch (v/reference-verifier {[VSUB "gov-mynumber"] "sec1"} true)
                            :proof-material pm :now (+ TS 10))]
    (is (get res "verified"))))

(deftest test-kotoba-auth-verifier-documents-canonical-call
  (let [[claim _ pm] (vmint "wallet-btc" "bip322" "bc1q" "s" :handle "bc1q")]
    (raises-containing "kotoba_auth::btc::verify_message"
      (v/verify-proof (v/kotoba-auth-verifier) "bip322" claim pm))))

;; ════════════════════════ test_analyze.py ════════════════════════
#?(:clj
   (defn- results [] (get (a/run (e/load-json seed-path)) "results")))

#?(:clj
   (defn- by-name []
     (into {} (map (fn [r] [(last (str/split (get r "subjectDid") #":")) r]) (results)))))

#?(:clj
   (deftest test-runs-all-members
     (let [rs (results)]
       (is (= 4 (count rs)))
       (is (= #{"demo-aaron" "demo-miriam" "demo-noah" "demo-esther"} (set (keys (by-name))))))))

#?(:clj
   (deftest test-aaron-is-multi-class-pop
     (let [aaron (get (get (by-name) "demo-aaron") "credential")]
       (is (= 3 (get aaron "assuranceLevel")))
       (is (true? (get aaron "proofOfPersonhood")))
       (is (>= (get aaron "distinctClasses") 3)))))

#?(:clj
   (deftest test-miriam-two-key-wallets-not-pop
     (let [m (get (get (by-name) "demo-miriam") "credential")]
       (is (= 2 (get m "factorCount")))
       (is (= 1 (get m "distinctClasses")))
       (is (false? (get m "proofOfPersonhood"))))))

#?(:clj
   (deftest test-noah-single-factor-ial1
     (let [n (get (get (by-name) "demo-noah") "credential")]
       (is (= 1 (get n "assuranceLevel")))
       (is (false? (get n "proofOfPersonhood"))))))

#?(:clj
   (deftest test-esther-gov-factor-gated-not-counted
     (let [e (get (by-name) "demo-esther")]
       (is (some #(= "gov-mynumber" (get % "factorKind")) (get e "gated")))
       (is (not (some #{"gov-mynumber"} (get-in e ["credential" "verifiedFactors"]))))
       (is (= 3 (get-in e ["credential" "assuranceLevel"]))))))

#?(:clj
   (deftest test-no-pii-or-handles-in-output-credentials
     (doseq [r (results)]
       (let [blob (pr-str (get r "credential"))]
         (is (not (str/includes? blob "0x")))
         (is (not (str/includes? blob "@")))
         (is (not (str/includes? blob "bc1q")))
         (is (seq (get-in r ["credential" "subjectDidHash"])))))))

#?(:clj
   (deftest test-vc-is-w3c-shaped
     (doseq [r (results)]
       (let [vc (get r "vc")]
         (is (= "VerifiableCredential" (first (get vc "type"))))
         (is (some #{"EtzhayyimPersonhoodCredential"} (get vc "type")))))))

#?(:clj (defn -main [& _] (run-tests 'shomei.methods.test-analyze)))
