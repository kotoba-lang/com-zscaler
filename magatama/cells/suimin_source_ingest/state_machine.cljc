(ns magatama.cells.suimin-source-ingest.state-machine
  "SuiminSourceIngestCell — read-only ingest of WHITELISTED sources into evidenceRecord.
  Per ADR-2606072800 §Decision 2 (source-whitelist invariant G1).
  Scaffold-only (Council activation gate). Port of suimin_source_ingest/cell.py.")

(def council-charter-attestation-tx-hash nil)
(def silen-suimin-baseline-review-cid nil)
(def source-whitelist-registry-cid nil)

(defn- council-activated? []
  (and council-charter-attestation-tx-hash
       silen-suimin-baseline-review-cid
       source-whitelist-registry-cid))

(defn- assert-council! []
  (when-not (council-activated?)
    (throw (ex-info
            (str "suimin_source_ingest cell scaffold-only — Council has not (a) attested the "
                 "suimin master charter ADR-2606072800 (silen-suimin master-charter-baseline), "
                 "or (b) ratified the source whitelist registry (G1 — only PubMed/Cochrane/"
                 "ICSD-3/ICD-11/AASM/national-sleep-society sources are admissible, every claim "
                 "needs verifiable provenance). Do not deploy.")
            {:cell :suimin-source-ingest
             :gate :council-activation}))))

(defn super-step [_ingest-xrpc _whitelist]
  (assert-council!)
  (throw (ex-info "R1 phase wave implements super-step"
                  {:cell :suimin-source-ingest})))

(defn run-chain [state]
  (assert-council!)
  (throw (ex-info "R1 phase wave implements run-chain"
                  {:cell :suimin-source-ingest :state state})))
