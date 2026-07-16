(ns yobel.ports
  "Port Protocols for yobel cells.
  Per ADR-2605201800. These protocols define the minimal interface surface that
  yobel cells call into. Implementations live in etzhayyim-sdk / etzhayyim-libp2p.
  
  Naming convention: <Concept>Port.")

;; ─── Data Records (replacing Python dataclasses) ──────────────────

(defrecord Rite [rite-id rite-type status effective-date expiry-date scope scope-jurisdictions issuer-did doctrinal-basis])
(defrecord DebtRow [debt-id debtor-did principal-micro-usdc accrued-micro-usdc origination-date instrument])
(defrecord AnchorResult [vertex-uri anchor-tx-hash])
(defrecord ProposalDecision [ratified signatures])
(defrecord BaseL2Tx [hash])
(defrecord LawfirmInvokeResult [ok invoke-uri])
(defrecord SignedTriple [cid])
(defrecord AuditAppendResult [cid vertex-uri])
(defrecord AuditBatchStatus [event-count seconds-since-last-anchor pending-cids])
(defrecord ReplicaConsensus [replicas-agree])
(defrecord BatchedAnchorResult [tx-hash])
(defrecord WitnessKey [id])
(defrecord NotificationResult [incident-uri])
(defrecord EnvelopeCipher [cid])

;; ─── Port Protocols ────────────────────────────────────────────────

(defprotocol RiteRegistryPort
  (get-rite [this rite-id]))

(defprotocol CreditorEnrollmentPort
  (find-debts-for-debtor [this rite-id debtor-did])
  (get-debt-for-release [this rite-id creditor-did debt-id decryptor]))

(defprotocol CouncilSbtPort
  (balance-of-level [this did])
  (entity-type-of [this did]))

(defprotocol CharterCompliancePort
  (is-aligned [this did])
  (jurisdiction-of [this did]))

(defprotocol Erc725Port
  (verify-eip712-signed-consent [this signer-did payload signature]))

(defprotocol EnvelopeCryptoPort
  (envelope [this plaintext recipients purpose]))

(defprotocol TitheRouterPort
  (route [this from-did to-did amount-micro-usdc tithe-rate]))

(defprotocol BaseL2PaymasterPort
  (release-usdc [this from-did to-did amount-micro-usdc tithe-neutral rite-id]))

(defprotocol LawfirmInvokePort
  (invoke [this method state]))

(defprotocol AnchorBridgePort
  (write-and-anchor [this collection rkey payload anchor-to-base-l2])
  (batched-anchor [this contract cids]))

(defprotocol AuditWitnessEmitPort
  (emit [this event-type kwargs]))

(defprotocol WitnessKeystorePort
  (current-key [this])
  (sign [this key-id payload]))

(defprotocol AuditLogPort
  (tail-signed-triple [this rite-id])
  (verify-chain-link [this prev-signed-cid next-state-root-before])
  (poll-replica-consensus [this rite-id expected-prev-cid])
  (append [this rite-id source-kind prev-cid state-root-before state-root-after tx-digest witness-key-id signature-hex event-payload])
  (batch-status [this])
  (mark-anchored [this cids tx-hash]))

(defprotocol PublicFundPort
  (request-audit-grant [this reason rite-id chain-break-reason]))

(defprotocol CouncilNotifierPort
  (notify [this targets event-type rite-id severity payload]))

(defprotocol CouncilRatificationPort
  (submit-proposal [this topic rite-id rite-type required-lv6-plus-count required-lv9-chair-count required-quorum-pct additional-gates doctrinal-basis scope])
  (await-decision [this proposal-uri timeout-days]))

(defprotocol LandRegistryPort
  (find-overlapping-tenures [this scope]))
