(ns yobel.tests.test-orchestrator
  "yobel orchestrator E2E — drives the shmita_5786 fixture through the full BPMN
  (declare → ratify → enroll fan-out → release → audit) with in-memory Fake
  ports. Clojure port of scripts/dry_run.py including its golden-file checks
  (fixtures/shmita_5786/expected.json): rite ratifies, exactly ONE release
  settles, the §2(b) one-way violation is blocked."
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.test :refer [deftest is]]
            [clojure.walk :as walk]
            [langgraph.checkpoint :as cp]
            [yobel.orchestrator :as orch]
            [yobel.ports :as ports]))

;; ─── fixture loading (snake_case JSON → kebab-keyword maps) ───────

(defn- kebabize [x]
  (walk/postwalk
   (fn [v]
     (if (map? v)
       (into {} (map (fn [[k val]]
                       [(keyword (str/replace (name k) "_" "-")) val]))
             v)
       v))
   x))

(defn- load-json [path]
  (let [parse (requiring-resolve 'cheshire.core/parse-string)]
    (kebabize (parse (slurp (io/resource (str "yobel/fixtures/shmita_5786/" path))) true))))

;; ─── Fake ports (port of dry_run.py's Fake* stubs) ────────────────

(defrecord StubAnchorBridge [writes]
  ports/AnchorBridgePort
  (write-and-anchor [_ collection rkey payload anchor-to-base-l2]
    (swap! writes conj {:collection collection :rkey rkey :payload payload
                        :anchor-to-base-l2 anchor-to-base-l2})
    (ports/->AnchorResult (str "at://dryrun/" collection "/" rkey)
                          (if anchor-to-base-l2 "0xdry-anchor" "")))
  (batched-anchor [_ _contract cids]
    (ports/->BatchedAnchorResult (str "0xdry-batch-" (count cids)))))

(defn- wire-ports
  "Wire the Fake ports from fixture content (pre-populate SBT levels, community
  membership, signed consents, the creditor-side debt ledger) — 1:1 with
  dry_run.py wire_ports()."
  [rite-input creditor-inputs]
  (let [rite-id (:rite-id rite-input)
        rite (ports/map->Rite {:rite-id rite-id
                               :rite-type (:rite-type rite-input)
                               :status "active"
                               :effective-date (:effective-date rite-input)
                               :expiry-date (:expiry-date rite-input)
                               :scope (:scope rite-input)
                               :scope-jurisdictions ["ALL"]
                               :issuer-did (:issuer-did rite-input)
                               :doctrinal-basis (:doctrinal-basis rite-input)})
        ;; SBT levels / entity types / signers / debt ledger from the fixture
        base {:levels {(:issuer-did rite-input) 9}
              :types {(:issuer-did rite-input) "natural_person"}
              :signers #{}
              :by-debtor {}
              :by-debt-id {}}
        {:keys [levels types signers by-debtor by-debt-id]}
        (reduce
         (fn [acc cred]
           (let [acc (-> acc
                         (assoc-in [:levels (:creditor-did cred)] 2)
                         (assoc-in [:types (:creditor-did cred)] "natural_person")
                         (update :signers conj [(:creditor-did cred) (:signed-consent cred)]))]
             (reduce
              (fn [acc d]
                (let [debtor (:debtor-did d)
                      row (ports/map->DebtRow {:debt-id (:debt-id d)
                                               :debtor-did debtor
                                               :principal-micro-usdc (:principal-micro-usdc d)
                                               :accrued-micro-usdc (get d :accrued-micro-usdc 0)
                                               :origination-date (:origination-date d)
                                               :instrument (:instrument d)})
                      acc (cond
                            ;; community debtors get SBT Lv2 unless already set
                            (str/starts-with? debtor "did:web:etzhayyim.com")
                            (-> acc
                                (update-in [:levels debtor] #(or % 2))
                                (update-in [:types debtor] #(or % "natural_person")))
                            ;; secular foreigner: natural person, NO SBT (R3 rejects)
                            (str/starts-with? debtor "did:web:secular.example")
                            (update-in acc [:types debtor] #(or % "natural_person"))
                            :else acc)]
                  (-> acc
                      (update-in [:by-debtor [(:rite-id cred) debtor]] (fnil conj []) row)
                      (assoc-in [:by-debt-id [(:rite-id cred) (:creditor-did cred) (:debt-id d)]] row))))
              acc
              (:debts cred))))
         base
         creditor-inputs)
        ;; debtor-no-sbt must NOT have an SBT entry
        levels (dissoc levels "did:web:no-sbt.example:debtor-no-sbt")
        env-counter (atom 0)
        audit-counter (atom 0)
        audit-tail (atom nil)
        state {:writes (atom [])
               :audit-appends (atom [])
               :proposals (atom [])
               :emits (atom [])
               :grants (atom [])
               :notifications (atom [])}]
    {:state state
     :ports
     (orch/ports
      {:rite-registry (reify ports/RiteRegistryPort
                        (get-rite [_ rid] (when (= rid rite-id) rite)))
       :creditor-enrollment (reify ports/CreditorEnrollmentPort
                              (find-debts-for-debtor [_ rid debtor-did]
                                (get by-debtor [rid debtor-did] []))
                              (get-debt-for-release [_ rid creditor-did debt-id _decryptor]
                                (get by-debt-id [rid creditor-did debt-id])))
       :council-sbt (reify ports/CouncilSbtPort
                      (balance-of-level [_ did] (get levels did 0))
                      (entity-type-of [_ did] (get types did "unknown")))
       :charter-compliance (reify ports/CharterCompliancePort
                             (is-aligned [_ did] (contains? levels did))
                             (jurisdiction-of [_ _did] "ALL"))
       :erc725 (reify ports/Erc725Port
                 (verify-eip712-signed-consent [_ signer-did _payload signature]
                   (contains? signers [signer-did signature])))
       :envelope-crypto (reify ports/EnvelopeCryptoPort
                          (envelope [_ _plaintext _recipients purpose]
                            (ports/->EnvelopeCipher
                             (str "ipfs://dryrun-cipher-" purpose "-" (swap! env-counter inc)))))
       :tithe-router nil
       :base-l2-paymaster (reify ports/BaseL2PaymasterPort
                            (release-usdc [_ _from _to _amount _tithe-neutral _rid]
                              (ports/->BaseL2Tx "0xdryrun-tx-hash")))
       :lawfirm-invoke (reify ports/LawfirmInvokePort
                         (invoke [_ method _state]
                           (ports/->LawfirmInvokeResult true (str "at://dryrun/lawfirm/" method))))
       :anchor-bridge (->StubAnchorBridge (:writes state))
       :audit-witness-emit (reify ports/AuditWitnessEmitPort
                             (emit [_ event-type kwargs]
                               (swap! (:emits state) conj (assoc kwargs :event-type event-type))))
       :witness-keystore (reify ports/WitnessKeystorePort
                           (current-key [_] (ports/->WitnessKey "dryrun-witness-key"))
                           (sign [_ _key-id payload] (str "dryrun-sig:" payload)))
       :audit-log (reify ports/AuditLogPort
                    (tail-signed-triple [_ _rid] @audit-tail)
                    (verify-chain-link [_ _prev _next] true)
                    (poll-replica-consensus [_ _rid _expected]
                      (ports/->ReplicaConsensus true))
                    (append [_ rid source-kind prev-cid srb sra tx-digest key-id sig payload]
                      (swap! (:audit-appends state) conj
                             {:rite-id rid :source-kind source-kind :prev-cid prev-cid
                              :state-root-before srb :state-root-after sra
                              :tx-digest tx-digest :witness-key-id key-id
                              :signature-hex sig :event-payload payload})
                      (let [cid (str "ipfs://dryrun-audit-" (swap! audit-counter inc))]
                        (reset! audit-tail (ports/->SignedTriple cid))
                        (ports/->AuditAppendResult cid (str "at://dryrun/audit/" @audit-counter))))
                    (batch-status [_] (ports/->AuditBatchStatus 0 0 []))
                    (mark-anchored [_ _cids _tx] nil))
       :public-fund (reify ports/PublicFundPort
                      (request-audit-grant [_ reason rid _chain-break]
                        (swap! (:grants state) conj {:reason reason :rite-id rid})))
       :council-notifier (reify ports/CouncilNotifierPort
                           (notify [_ targets event-type rid _severity _payload]
                             (swap! (:notifications state) conj
                                    {:targets targets :event-type event-type})
                             (ports/->NotificationResult (str "at://dryrun/incident/" rid))))
       :council-ratification (reify ports/CouncilRatificationPort
                               (submit-proposal [_ _topic rid _rite-type _lv6 _lv9
                                                 _quorum _gates _doctrinal _scope]
                                 (swap! (:proposals state) conj {:rite-id rid})
                                 (str "at://dryrun/proposal/" rid))
                               (await-decision [_ _uri _timeout]
                                 (ports/->ProposalDecision
                                  true
                                  (into ["did:web:council/lv9-chair"]
                                        (map #(str "did:web:council/lv6-" %) ["a" "b" "c"])))))
       :land-registry (reify ports/LandRegistryPort
                        (find-overlapping-tenures [_ _scope] []))})}))

;; ─── the golden E2E ───────────────────────────────────────────────

(deftest shmita-5786-dry-run-golden
  (let [rite (load-json "rite.json")
        creditors (load-json "creditors.json")
        debtors (load-json "debtors.json")
        releases (load-json "releases.json")
        expected (load-json "expected.json")
        {:keys [ports state]} (wire-ports rite creditors)
        orchestrator (orch/make-orchestrator ports (cp/mem-checkpointer))
        snap (orch/run-rite-lifecycle orchestrator
                                      {:rite-input rite
                                       :creditor-enrollments creditors
                                       :debtor-enrollments debtors
                                       :release-inputs releases})]
    ;; golden check 1: rite ratifies and goes active
    (is (= (:rite-status expected) (:rite-status snap)))
    (is (= "complete" (:phase snap)))
    ;; golden check 2: exactly ONE release settles (the §2(b) violation and the
    ;; two DMN-ineligible debtors are blocked)
    (is (= (get-in expected [:summary :successful-releases])
           (count (filter :settlement-ok (:releases snap)))))
    ;; structure: all fixture rows produced a result
    (is (= 3 (count (:creditor-enrollments snap))))
    (is (= 5 (count (:debtor-enrollments snap))))
    (is (= 4 (count (:releases snap))))
    ;; creditor-A accepted. creditor-B is rejected by the historical-record gate
    ;; (its only debt originated AFTER the rite's effective date — the Python
    ;; cell.py gate rejects it identically; expected.json's creditor table is
    ;; design-level documentation, and dry_run.py's golden check never asserts
    ;; creditors_accepted). creditor-C is rejected by the §2(b) instrument gate.
    (let [[a b c] (:creditor-enrollments snap)]
      (is (seq (get a :enrollment-vertex-uri "")))
      (is (false? (:historical-record-compliant b)))
      (is (= "" (get b :enrollment-vertex-uri "")))
      (is (false? (:instrument-safety-compliant c)))
      (is (= "" (get c :enrollment-vertex-uri ""))))
    ;; debtors: exactly 1 anchored eligible (R1)
    (is (= (get-in expected [:summary :debtors-eligible])
           (count (filter #(and (= "com.etzhayyim.apps.etzhayyim.yobel.debtorEnrollment"
                                   (:collection %))
                                (true? (get-in % [:payload :eligible])))
                          @(:writes state)))))
    ;; the §2(b) one-way violation never settles
    (let [violation (some #(when (= "" (get % :release-vertex-uri ""))
                             %)
                          (:releases snap))]
      (is (some? violation))
      (is (false? (boolean (:settlement-ok violation)))))
    ;; Council saw exactly one proposal; the audit witness chain appended
    (is (= 1 (count @(:proposals state))))
    (is (pos? (count @(:audit-appends state))))
    ;; operator snapshot reconstructs the ratified rite from bridge writes
    (let [s (orch/snapshot orchestrator (:rite-id rite))]
      (is (= "active" (:rite-status s)))
      (is (= "ratified" (:phase s))))))
