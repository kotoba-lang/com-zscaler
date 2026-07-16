(ns kuni-umi.cells.commissioning.cell
  "CommissioningCell — Phase 4 of kuni-umi 4-phase deployment workflow.
  1:1 port of cells/commissioning/cell.py (ADR-2605201400 §3 + ADR-2605202200).

  Trigger:    MST listener on terminal recordConstructionProgress with phase=handoff-ready
  Effect:     Register assets with open-* utility lexicons → register loops with
              open-ot defineLoop → run acceptance test → emit `commissionDeployment`
              → site transitions to `operational`.
  Murakumo:   simeon (leader)

  R0 scaffold WITH structure: the 6-node register→test→commission→transfer graph is wired,
  but every live leg raises (the Python NotImplementedError) until deps.sdk + Council ratify.
  .solve() starts the graph at its entry node register-with-utility, which raises — there is
  NO live commissioning / actuation at R0 (no-server-key, witness invariant).

  After commissionDeployment, kuni-umi is observer-only for that site (except
  AuditWitnessCell continues continuous coverage, DecommissionCell activates at lifespan
  expiry).")

;; ─── Nodes ──────────────────────────────────────────────────────────

(defn register-with-utility
  "For each commissioned asset, call the relevant open-* utility lexicon
  (open-denki/open-gas/open-water/open-network/…). Gated until deps.sdk + S2 Council ratify."
  [_state _deps]
  (throw (ex-info
          "Requires deps.sdk + open-* utility XRPC calls (open-denki/open-gas/open-water/open-network/...)."
          {:cell :commissioning :node :register-with-utility :gated true})))

(defn register-with-open-ot
  "For each control loop, call open-ot defineDevice/defineCell/defineLoop (ADR-2605151200).
  R0 offline: the loop-DID set is RECORDED (the dry-run, no-XRPC reference is
  robotics/commissioning.py); the LIVE XRPC write requires deps.sdk and is gated."
  [state deps]
  (let [loop-dids (or (get state "openOtLoopDids")
                      ["did:web:etzhayyim.com:openot:loop:droop-p-f"
                       "did:web:etzhayyim.com:openot:loop:anti-islanding-rocof"])
        state (assoc state "openOtLoopDids" loop-dids)]
    (if (nil? (get deps :sdk))
      state                              ; R0 offline: loop DIDs recorded, no live XRPC (G15/G8)
      (throw (ex-info
              (str "Live open-ot XRPC (com.etzhayyim.apps.openOt.defineDevice/defineCell/defineLoop) "
                   "requires deps.sdk — gated until S2 Council ratify.")
              {:cell :commissioning :node :register-with-open-ot :gated true})))))

(defn enrol-stream
  "Start telemetry stream (smart-meter / pressure-log / quality-sample / utilization)."
  [_state _deps]
  (throw (ex-info
          (str "Requires per-utility-class telemetry binding (open-denki recordMeterReading, "
               "open-water recordReading, open-network recordUtilization, etc.).")
          {:cell :commissioning :node :enrol-stream :gated true})))

(defn commission-test
  "Run short acceptance test (S2 microgrid: black-start + droop-P-f response), reusing the
  open-ot reference BFB cells via robotics/commissioning.run_microgrid_acceptance; R1 records
  acceptanceTier device-wasm vs python-twin from the committed device-loop golden trace.
  TODO(port): the robotics/commissioning.py twin + golden device_loop_trace.json acceptance
  tiering are not yet cljc-ported — kept as a gated stub (never reached at R0: register-with-utility
  raises first)."
  [_state _deps]
  (throw (ex-info
          (str "Requires robotics/commissioning run_microgrid_acceptance twin (not yet cljc-ported) "
               "+ device-loop golden trace; live device-in-the-loop gated behind deps.sdk + safety PLC.")
          {:cell :commissioning :node :commission-test :gated true :todo :port})))

(defn commission
  "Emit `commissionDeployment` MST record + site state → operational."
  [_state _deps]
  (throw (ex-info
          "Requires deps.sdk for MST write of com.etzhayyim.apps.etzhayyim.kuniUmi.commissionDeployment."
          {:cell :commissioning :node :commission :gated true})))

(defn transfer-stewardship
  "Formal transfer of day-to-day operation to steward + open-ot orchestrator."
  [_state _deps]
  (throw (ex-info
          "Requires Steward Lv5+ attestation chain + open-ot stewardOperatorDid binding."
          {:cell :commissioning :node :transfer-stewardship :gated true})))

;; ─── Entry / runner ─────────────────────────────────────────────────

(defn solve
  "Run the commissioning Pregel graph from its entry node (START → register-with-utility).
  At R0 register-with-utility raises (ex-info, the NotImplementedError analogue): no live
  commissioning/actuation without deps.sdk + Council Lv6+ + a certified-safety review."
  ([input-state] (solve input-state {}))
  ([input-state deps]
   (register-with-utility input-state deps)))

(defn thread-id-from-event
  "1:1 port of thread_id_from_event: CommissioningCell:<planDid|rkey>."
  [event-record _nsid]
  (let [value (get event-record "value" {})
        plan-did (or (get value "planDid") (get event-record "rkey" "unknown"))]
    (str "CommissioningCell:" plan-did)))

(defn healthz-extra
  "1:1 port of healthz_extra."
  [_deps]
  {"phase" "4-commissioning"
   "trigger_nsid" "com.etzhayyim.apps.etzhayyim.kuniUmi.recordConstructionProgress"
   "trigger_filter" "phase=handoff-ready"
   "hand_off_targets" ["open-ot WASM PLC" "open-* utility lexicons"]})
