(ns matsurigoto.methods.test-sign-capability
  "Tests for the R1.C sign/authority layer (matsurigoto 政, ADR-2606062300 + 2605231525).
  1:1 port of `methods/test_sign_capability.py` — every assertion preserved.

  The Python test pulls its unsigned artifact from `tax_assess.assess_from_return(1_000_000, 0,
  \"FLAT20.income\")['receipt']`. sign_capability is self-contained, so `_unsigned` is inlined
  to the exact map that call produces (taxable 1,000,000 × flat 20% → 200000.0 assessed):
  {\"assessed_amount\" 200000.0 \"currency\" \"XXX\" \"proof\" nil
   \"server_held_authority\" false \"status\" \"assessed-unsigned\"}."
  (:require [clojure.test :refer [deftest is]]
            [matsurigoto.methods.sign-capability :as S]))

(def COUNCIL "did:web:etzhayyim.com:council:safe")
(def STATE "did:web:gov.example:tax-authority")
(def AT "2026-06-06T00:00:00Z")

(defn- unsigned []
  {"assessed_amount" 200000.0
   "currency" "XXX"
   "proof" nil
   "server_held_authority" false
   "status" "assessed-unsigned"})

(deftest test-module-holds-no-key
  (is (= S/SIGNER-HELD-PRIVATE-KEY false)))

(deftest test-server-side-signing-always-raises
  ;; The structural no-server-key guarantee.
  (is (thrown? clojure.lang.ExceptionInfo (S/sign-server-side (unsigned)))))

(deftest test-principal-a-council-signs
  (let [signed (S/attach-external-proof (unsigned)
                 {:signer-did COUNCIL :authority-mode ":sovereign-governance"
                  :signature "0xSAFE" :signed-at AT})]
    (is (= (get-in signed ["proof" "signer_did"]) COUNCIL))
    (is (not (clojure.string/includes? (get signed "status") "unsigned")))
    (is (= (S/verify-proof signed) true))))

(deftest test-principal-b-state-signs-with-own-key
  (let [signed (S/attach-external-proof (unsigned)
                 {:signer-did STATE :authority-mode ":supplied-to-state"
                  :signature "0xSTATE" :signed-at AT})]
    (is (= (S/verify-proof signed) true))))

(deftest test-principal-a-rejects-non-council-signer
  ;; Sovereign acts must be signed by a Council organ, not an arbitrary did.
  (is (thrown? clojure.lang.ExceptionInfo
        (S/attach-external-proof (unsigned)
          {:signer-did STATE :authority-mode ":sovereign-governance"
           :signature "0xX" :signed-at AT}))))

(deftest test-principal-b-rejects-etzhayyim-holding-state-key
  ;; etzhayyim never holds the adopting state's key — an etzhayyim did can't sign a state act.
  (is (thrown? clojure.lang.ExceptionInfo
        (S/attach-external-proof (unsigned)
          {:signer-did "did:web:etzhayyim.com:worker" :authority-mode ":supplied-to-state"
           :signature "0xX" :signed-at AT}))))

(deftest test-empty-signature-refused
  (is (thrown? clojure.lang.ExceptionInfo
        (S/attach-external-proof (unsigned)
          {:signer-did COUNCIL :authority-mode ":sovereign-governance"
           :signature "" :signed-at AT}))))

(deftest test-double-sign-refused
  (let [signed (S/attach-external-proof (unsigned)
                 {:signer-did COUNCIL :authority-mode ":sovereign-governance"
                  :signature "0xSAFE" :signed-at AT})]
    (is (thrown? clojure.lang.ExceptionInfo
          (S/attach-external-proof signed
            {:signer-did COUNCIL :authority-mode ":sovereign-governance"
             :signature "0xAGAIN" :signed-at AT})))))

(deftest test-tampered-payload-fails-verify
  (let [signed (S/attach-external-proof (unsigned)
                 {:signer-did COUNCIL :authority-mode ":sovereign-governance"
                  :signature "0xSAFE" :signed-at AT})
        tampered (assoc signed "assessed_amount" 999999.0)]  ; tamper a SUBSTANTIVE field
    (is (= (S/verify-proof tampered) false))))

(deftest test-unsigned-artifact-does-not-verify
  (is (= (S/verify-proof (unsigned)) false)))

;; ── byte-parity vector vs python3: the capability digest over the unsigned payload ──
(deftest test-digest-parity-vector
  (is (= (S/canonical-payload (dissoc (unsigned) "proof" "status"))
         "fc432c7a68d2bb3da298ffb083e84689d02e97f504209c8efbf8753bf9eddcab"))
  (is (= (S/canonical-json (dissoc (unsigned) "proof" "status"))
         "{\"assessed_amount\":200000.0,\"currency\":\"XXX\",\"server_held_authority\":false}")))
