(ns todoke.cells.handoff-proof.test-state-machine
  "State-machine tests for the todoke 届け handoff_proof cell (R0).
  1:1 port of the handoff_proof portion of cells/test_state_machines.py (ADR-2606042300).
  G8 on-device privacy / G12 no-server-key / G13 consent enforced as hard refusals; .solve() raises at R0."
  (:require [clojure.test :refer [deftest is]]
            [todoke.cells.handoff-proof.state-machine :as sm]))

(defn- run-handoff
  [& {:keys [consent-ref proof-kind recipient-sig server-signed]
      :or {consent-ref "enc:consent-0001" proof-kind "recipient-signature"
           recipient-sig "sig-recipient" server-signed false}}]
  (let [s (sm/transition-to-arrived {"recipient_did" "did:web:member.example/r1"})
        s (sm/transition-to-consent-verified (merge s {"consent_ref" consent-ref}))
        s (sm/transition-to-proof-captured (merge s {"proof_kind" proof-kind}))
        s (sm/transition-to-proof-sealed (merge s {"recipient_sig" recipient-sig "server_signed" server-signed}))]
    s))

(deftest test-handoff-happy-path-seals-on-device
  (let [cs (get (run-handoff) "cell_state")
        rec (get-in cs ["payload" "handoff_proof"])]
    (is (= sm/phase-proof-sealed (get cs "phase")))
    (is (= true (get rec "onDeviceOnly")))     ; G8
    (is (= false (get rec "serverSigned")))    ; G12
    (is (= true (get rec "sealed")))))

(deftest test-handoff-g13-requires-consent
  (is (thrown-with-msg? clojure.lang.ExceptionInfo #"G13 violation" (run-handoff :consent-ref ""))))

(deftest test-handoff-g8-rejects-cloud-image-proof
  (is (thrown-with-msg? clojure.lang.ExceptionInfo #"G8 violation" (run-handoff :proof-kind "cloud-image"))))

(deftest test-handoff-g8-rejects-face-match
  (is (thrown-with-msg? clojure.lang.ExceptionInfo #"G8 violation" (run-handoff :proof-kind "face-match"))))

(deftest test-handoff-g12-rejects-server-signing
  (is (thrown-with-msg? clojure.lang.ExceptionInfo #"G12 violation" (run-handoff :server-signed true))))

(deftest test-handoff-unsigned-is-not-sealed
  (is (= false (get-in (run-handoff :recipient-sig "") ["cell_state" "payload" "handoff_proof" "sealed"]))))

(deftest test-handoff-solve-raises-at-r0
  (is (thrown-with-msg? clojure.lang.ExceptionInfo #"R0 scaffold" (sm/solve {}))))
