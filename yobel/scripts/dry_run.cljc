#!/usr/bin/env bb
(ns yobel.scripts.dry-run
  "yobel dry-run CLI — cljc port of scripts/dry_run.py. Drives a fixture rite
  end-to-end through the BPMN orchestrator (yobel.orchestrator) with in-memory
  Fake ports. Touches no external system (no Base L2, no MST anchor, no Council
  deliberation). Prints a JSON report of what each cell would have done.

  Usage (from repo root):
    bb 20-actors/yobel/scripts/dry_run.cljc --fixture 20-actors/yobel/fixtures/shmita_5786 [--check-golden]

  Exit 0 = ran end-to-end · 1 = load/crash · 2 = golden-file divergence.

  NOTE: the same wiring + golden checks also run as a unit test
  (yobel.tests.test-orchestrator), which is what run_tests.sh exercises."
  (:require [babashka.cli :as cli]
            [cheshire.core :as json]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [langgraph.checkpoint :as cp]
            [yobel.orchestrator :as orch]
            [yobel.ports :as ports]))

(defn- kebabize [x]
  (clojure.walk/postwalk
   (fn [v]
     (if (map? v)
       (into {} (map (fn [[k val]] [(keyword (str/replace (name k) "_" "-")) val])) v)
       v))
   x))

(defn- load-json [^String path]
  (kebabize (json/parse-string (slurp path) true)))

(defn- load-fixture [^String dir]
  {:rite (load-json (str dir "/rite.json"))
   :creditors (load-json (str dir "/creditors.json"))
   :debtors (load-json (str dir "/debtors.json"))
   :releases (load-json (str dir "/releases.json"))
   :expected (load-json (str dir "/expected.json"))})

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
  "Port of dry_run.py wire_ports(): pre-populate SBT levels, community membership,
  signed consents, and the creditor-side debt ledger from fixture content."
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
        base {:levels {(:issuer-did rite-input) 9}
              :types {(:issuer-did rite-input) "natural_person"}
              :signers #{} :by-debtor {} :by-debt-id {}}
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
                            (str/starts-with? debtor "did:web:etzhayyim.com")
                            (-> acc (update-in [:levels debtor] #(or % 2))
                                (update-in [:types debtor] #(or % "natural_person")))
                            (str/starts-with? debtor "did:web:secular.example")
                            (update-in acc [:types debtor] #(or % "natural_person"))
                            :else acc)]
                  (-> acc
                      (update-in [:by-debtor [(:rite-id cred) debtor]] (fnil conj []) row)
                      (assoc-in [:by-debt-id [(:rite-id cred) (:creditor-did cred) (:debt-id d)]] row))))
              acc (:debts cred))))
         base creditor-inputs)
        levels (dissoc levels "did:web:no-sbt.example:debtor-no-sbt")
        env-counter (atom 0) audit-counter (atom 0) audit-tail (atom nil)
        state {:writes (atom []) :audit-appends (atom []) :proposals (atom [])
               :emits (atom []) :grants (atom []) :notifications (atom [])}]
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
                    (poll-replica-consensus [_ _rid _expected] (ports/->ReplicaConsensus true))
                    (append [_ rid source-kind prev-cid srb sra tx-digest key-id sig payload]
                      (swap! (:audit-appends state) conj
                             {:rite-id rid :source-kind source-kind :prev-cid prev-cid
                              :state-root-before srb :state-root-after sra :tx-digest tx-digest
                              :witness-key-id key-id :signature-hex sig :event-payload payload})
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
                             (swap! (:notifications state) conj {:targets targets :event-type event-type})
                             (ports/->NotificationResult (str "at://dryrun/incident/" rid))))
       :council-ratification (reify ports/CouncilRatificationPort
                               (submit-proposal [_ _topic rid _rt _lv6 _lv9 _q _g _d _s]
                                 (swap! (:proposals state) conj {:rite-id rid})
                                 (str "at://dryrun/proposal/" rid))
                               (await-decision [_ _uri _timeout]
                                 (ports/->ProposalDecision
                                  true (into ["did:web:council/lv9-chair"]
                                             (map #(str "did:web:council/lv6-" %) ["a" "b" "c"])))))
       :land-registry (reify ports/LandRegistryPort
                        (find-overlapping-tenures [_ _scope] []))})}))

(defn- build-report [fixture-name snap state]
  {:fixture fixture-name
   :rite_id (:rite-id snap)
   :phase (:phase snap)
   :rite_status (:rite-status snap)
   :creditor_enrollments (mapv (fn [e] {:vertex_uri (get e :enrollment-vertex-uri "")
                                        :debt_count (get e :debt-count 0)
                                        :accepted (boolean (seq (get e :enrollment-vertex-uri "")))})
                               (:creditor-enrollments snap))
   :debtor_enrollments (mapv (fn [e] {:vertex_uri (get e :enrollment-vertex-uri "")
                                      :pairing_status (get e :pairing-status "unpaired")})
                             (:debtor-enrollments snap))
   :releases (mapv (fn [r] {:release_vertex_uri (get r :release-vertex-uri "")
                            :settlement_ok (get r :settlement-ok)
                            :base_l2_tx_hash (get r :base-l2-tx-hash "")
                            :settlement_error (get r :settlement-error "")})
                   (:releases snap))
   :anchor_writes (count @(:writes state))
   :audit_appends (count @(:audit-appends state))
   :council_proposals (count @(:proposals state))})

(defn run [{:keys [fixture check-golden]}]
  (when-not (and fixture (.isDirectory (io/file fixture)))
    (binding [*out* *err*] (println (str "fixture dir not found: " fixture)))
    (System/exit 1))
  (let [fx (load-fixture fixture)
        {:keys [ports state]} (wire-ports (:rite fx) (:creditors fx))
        orchestrator (orch/make-orchestrator ports (cp/mem-checkpointer))
        snap (orch/run-rite-lifecycle orchestrator
                                      {:rite-input (:rite fx)
                                       :creditor-enrollments (:creditors fx)
                                       :debtor-enrollments (:debtors fx)
                                       :release-inputs (:releases fx)})
        report (build-report (.getName (io/file fixture)) snap state)]
    (println (json/generate-string report {:pretty true}))
    (when check-golden
      (let [expected (:expected fx)
            ok? (atom true)]
        (when (not= (:rite_status report) (:rite-status expected))
          (binding [*out* *err*]
            (println (str "GOLDEN MISMATCH: rite_status " (:rite_status report)
                          " != " (:rite-status expected))))
          (reset! ok? false))
        (let [actual (count (filter :settlement_ok (:releases report)))
              want (get-in expected [:summary :successful-releases])]
          (when (not= actual want)
            (binding [*out* *err*]
              (println (str "GOLDEN MISMATCH: successful_releases " actual " != " want)))
            (reset! ok? false)))
        (when-not @ok? (System/exit 2))))
    (System/exit 0)))

(def ^:private cli-spec
  {:fixture {:require true} :check-golden {:coerce :boolean :default false}})

(defn -main [& args]
  (run (cli/parse-opts args {:spec cli-spec})))

(when (= *file* (System/getProperty "babashka.file"))
  (apply -main *command-line-args*))
