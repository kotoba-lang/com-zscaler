(ns silicon.methods.fab-cell
  "silicon 珪 — fab-run orchestration cell (datalog/kotoba, R0 dry-run).

  The runnable 'cell' that ties the three method layers into one lot run:

      wafer-handler (physical reachability + throughput)
        → fab-flow (process-physics simulation → yield)
          → lot-ledger (content-addressed EAVT Datom commit)

  This is the cljc + kotoba-Datom realization of the silicon manifest's process
  cells (deposition/etch/implant/cmp), which previously existed only as langgraph
  Pregel scaffolds whose `.solve()` raised. Per ADR-2605242500 + 2605242545.

  G11: pure simulation. `commit?` only builds an in-memory commit map — it does
  NOT write the live kotoba log (no-server-key; that is an operator/Council step)."
  (:require [silicon.methods.fab-flow :as flow]
            [silicon.methods.lot-ledger :as ledger]
            [silicon.methods.wafer-handler :as wh]))

(defn run-fab-lot
  "Run ONE wafer-lot through the fab end-to-end and return a summary.

  opts:
    :route          fab route (default flow/default-route)
    :recipe         per-step recipe (default flow/reference-recipe)
    :attest         silen-force-attest string (required for litho/implant routes)
    :process-times  per-station process seconds (for throughput; optional)
    :arm            SCARA arm {:l1 :l2} (optional)
    :stations       station [{:x :y} …] for reachability (optional)
    :prev-cid       prior lot-ledger CID to chain onto (default genesis)

  Returns:
    {:lot-id … :all-pass … :yield … :good-die … :packaged-units …
     :defect-density … :tx-cid … :datom-count …
     :throughput {…}        (when :process-times given)
     :reachable bool}        (when :arm + :stations given)

  Throws on an un-attested dual-use route (G1) or an unreachable station layout."
  [lot {:keys [route recipe attest process-times arm stations prev-cid]
        :or {route flow/default-route recipe flow/reference-recipe prev-cid ""}}]
  ;; G — physical precondition: every station must be reachable before we sim.
  (when (and arm stations (not (wh/station-reachable? arm stations)))
    (throw (ex-info "station layout unreachable by the wafer-handler arm"
                    {:error :unreachable :stations stations})))
  (let [record (flow/run-lot lot route recipe :silen-force-attest attest)
        tx (ledger/commit-lot record prev-cid)
        base {:lot-id (:lot-id record)
              :all-pass (:all-pass record)
              :yield (:yield record)
              :good-die (:good-die record)
              :packaged-units (:packaged-units record)
              :defect-density (:defect-density record)
              :tx-cid (:tx/cid tx)
              :tx-prev (:tx/prev tx)
              :datom-count (:tx/count tx)}]
    (cond-> base
      process-times (assoc :throughput (wh/throughput-wph process-times))
      (and arm stations) (assoc :reachable true))))

(defn run-reference
  "Convenience: run the reference iwakura ternary-PE tile lot with a realistic
  cluster-tool layout, attested. Useful as a smoke / demo entry."
  []
  (run-fab-lot
    flow/reference-lot
    {:attest "ok: iwakura ternary-PE tile, civilian inference ASIC (Charter Rider §2(a))"
     :process-times [45.0 60.0 50.0 40.0 35.0 20.0 30.0 55.0]
     :arm {:l1 0.4 :l2 0.35}
     :stations [{:x 0.5 :y 0.0} {:x 0.45 :y 0.2} {:x 0.3 :y 0.35}
                {:x 0.0 :y 0.5} {:x -0.3 :y 0.35} {:x -0.45 :y 0.2}
                {:x -0.5 :y 0.0} {:x 0.4 :y -0.2}]}))
