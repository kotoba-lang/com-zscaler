(ns yobel.cells.audit-witness.cell
  "AuditWitnessCell — Continuous Pregel cell witnessing all super-step transitions + release MST events.

  Per ADR-2605201800 §Decision + ADR-2605192415 §B continuous-witness pattern.

  Trigger: LangGraph super-step boundary, release MST finalization, 60s sensor polling
  Effect:
    - Collect (stateRootBefore, stateRootAfter, txDigest) triple
    - Sign with rotating witness key, append to auditLog MST collection
    - Detect tampering (missing prior signature / hash-chain break)
    - Batched anchor (every 100 events or 10 minutes) via AnchorBridge
    - On tampering: rite status=superseded_for_audit + Public Fund grant request +
      Council Lv9 + Five-Bootstrap-Council notification

  Murakumo node: reuben (firstborn / witness — Gen 49:3, Gen 29:32 \"God has seen my affliction\").

  Clojure port of cells/audit_witness/cell.py (langgraph-clj, portable .cljc)."
  (:require [langgraph.graph :as g]
            [yobel.ports :as p]))

;; ─── Helpers ─────────────────────────────────────────────────────────

(defn- take-prefix
  "Python-slice-safe prefix: (subs s 0 n) but never out of bounds."
  [s n]
  (let [s (or s "")]
    (subs s 0 (min n (count s)))))

(defn- ->hex
  "Bridge for WitnessKeystorePort/sign return values: Python's cell calls
  sig.hex() on bytes; here byte arrays are hex-encoded, strings pass through
  (a Clojure port impl may already return a hex string)."
  [sig]
  (cond
    (string? sig) sig
    #?(:clj (bytes? sig)
       :cljs (instance? js/Uint8Array sig))
    #?(:clj (apply str (map #(format "%02x" (bit-and (int %) 0xff)) sig))
       :cljs (apply str (map #(.padStart (.toString % 16) 2 "0") (array-seq sig))))
    :else (str sig)))

;; ─── Node functions ──────────────────────────────────────────────────

(defn collect-state-diff
  "Compute / pass-through (stateRootBefore, stateRootAfter, txDigest)."
  [state]
  ;; Inputs already populated by trigger emitter; this node is a hook for extra normalization
  (if (seq (:tx-digest state))
    state
    ;; Fallback digest: concat roots
    (let [before (get state :state-root-before "")
          after (get state :state-root-after "")]
      {:tx-digest (str (take-prefix before 32) "::" (take-prefix after 32))})))

(defn verify-chain-continuity
  "Look up previous signed triple; verify chain hash continuity."
  [state audit-log-port]
  (if (nil? audit-log-port)
    {:chain-valid true :tampering-severity "none" :prev-signed-triple-cid ""}
    (let [prev (p/tail-signed-triple audit-log-port (get state :rite-id ""))]
      (cond
        (nil? prev)
        ;; First witness for this rite — chain genesis OK
        {:chain-valid true :tampering-severity "none" :prev-signed-triple-cid ""}

        (p/verify-chain-link audit-log-port (:cid prev) (get state :state-root-before ""))
        {:chain-valid true
         :tampering-severity "none"
         :prev-signed-triple-cid (:cid prev)}

        :else
        ;; Chain break detected — require 2-node consensus before raising `confirmed`
        (let [consensus (p/poll-replica-consensus audit-log-port (get state :rite-id "") (:cid prev))
              severity (if (:replicas-agree consensus) "confirmed" "suspicion")]
          {:chain-valid false
           :chain-break-reason (str "prev_cid " (:cid prev) " not matched by next state_root_before")
           :tampering-severity severity
           :prev-signed-triple-cid (:cid prev)})))))

(defn sign-and-append
  "Sign (prev_cid || state_root_before || state_root_after || tx_digest) and append."
  [state witness-keystore audit-log-port]
  (if (or (nil? witness-keystore) (nil? audit-log-port))
    {:witness-key-id "stub-key"
     :signed-triple-hex "0xstub"
     :audit-event-cid "ipfs://stub-audit-event"
     :audit-event-uri (str "at://stub/audit/"
                           (get state :rite-id "unknown") "/"
                           (get state :tx-digest ""))}
    (let [key (p/current-key witness-keystore)
          payload (str (get state :prev-signed-triple-cid "")
                       (get state :state-root-before "")
                       (get state :state-root-after "")
                       (get state :tx-digest ""))
          sig-hex (->hex (p/sign witness-keystore (:id key) payload))
          appended (p/append audit-log-port
                             (get state :rite-id "")
                             (get state :source-kind "super_step")
                             (get state :prev-signed-triple-cid "")
                             (get state :state-root-before "")
                             (get state :state-root-after "")
                             (get state :tx-digest "")
                             (:id key)
                             sig-hex
                             (get state :event-payload {}))]
      {:witness-key-id (:id key)
       :signed-triple-hex sig-hex
       :audit-event-cid (:cid appended)
       :audit-event-uri (:vertex-uri appended)})))

(defn anchor-batch
  "Anchor every 100 events or 10 minutes, whichever first."
  [_state anchor-bridge audit-log-port]
  (if (or (nil? anchor-bridge) (nil? audit-log-port))
    {:batch-anchored false :base-l2-anchor-tx-hash ""}
    (let [batch-status (p/batch-status audit-log-port)]
      (if-not (or (>= (:event-count batch-status) 100)
                  (>= (:seconds-since-last-anchor batch-status) 600))
        {:batch-anchored false :base-l2-anchor-tx-hash ""}
        (let [result (p/batched-anchor anchor-bridge
                                       "AuditAnchorRegistry"
                                       (:pending-cids batch-status))]
          (p/mark-anchored audit-log-port (:pending-cids batch-status) (:tx-hash result))
          {:batch-anchored true :base-l2-anchor-tx-hash (:tx-hash result)})))))

(defn on-tampering-detected
  "Mark rite superseded + Public Fund grant + Council notification."
  [state public-fund-port council-notifier]
  (let [rite-id (get state :rite-id "")]
    (when (some? public-fund-port)
      (p/request-audit-grant public-fund-port
                             "yobel.tampering_detected"
                             rite-id
                             (get state :chain-break-reason "")))
    (let [incident-uri (if (some? council-notifier)
                         (:incident-uri
                          (p/notify council-notifier
                                    ["council_lv9_chair" "five_bootstrap_council"]
                                    "yobel.tampering_confirmed"
                                    rite-id
                                    "confirmed"
                                    state))
                         "")]
      {:incident-uri (if (seq incident-uri)
                       incident-uri
                       (str "at://stub/incident/" rite-id))})))

;; ─── Graph ───────────────────────────────────────────────────────────

(defn chain-router
  "tampering_severity confirmed only on 2-node consensus; single-node = suspicion."
  [state]
  (if (= "confirmed" (:tampering-severity state))
    :on-tampering-detected
    :sign-and-append))

(defn build-graph
  "opts: {:checkpointer :audit-log-port :witness-keystore :anchor-bridge
          :public-fund-port :council-notifier}
  Returns the compiled graph."
  [{:keys [checkpointer audit-log-port witness-keystore anchor-bridge
           public-fund-port council-notifier]}]
  (-> (g/state-graph)
      (g/add-node :collect-state-diff collect-state-diff)
      (g/add-node :verify-chain-continuity
                  (fn [s] (verify-chain-continuity s audit-log-port)))
      (g/add-node :sign-and-append
                  (fn [s] (sign-and-append s witness-keystore audit-log-port)))
      (g/add-node :anchor-batch
                  (fn [s] (anchor-batch s anchor-bridge audit-log-port)))
      (g/add-node :on-tampering-detected
                  (fn [s] (on-tampering-detected s public-fund-port council-notifier)))
      (g/set-entry-point :collect-state-diff)
      (g/add-edge :collect-state-diff :verify-chain-continuity)
      (g/add-conditional-edges :verify-chain-continuity chain-router)
      (g/add-edge :sign-and-append :anchor-batch)
      (g/add-edge :anchor-batch g/END)
      (g/add-edge :on-tampering-detected g/END)
      (g/compile-graph {:checkpointer checkpointer})))
