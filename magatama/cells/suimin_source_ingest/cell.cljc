(ns magatama.cells.suimin-source-ingest.cell
  "SuiminSourceIngestCell — read-only ingest of WHITELISTED sources into evidenceRecord.
  Per ADR-2606072800 §Decision 2 (source-whitelist invariant G1) + §Decision 5.

  Fetches treatment-evidence only from Council-ratified whitelisted sources (PubMed/MeSH,
  Cochrane, AASM / national sleep-society guidelines, ICSD-3 / ICD-11) with verifiable
  provenance. R0 scaffold — .solve() raises until the Council activation gate is satisfied
  (1:1 port of suimin_source_ingest/cell.py import-time RuntimeError).")

(defn solve
  [_input-state]
  (throw (ex-info
          (str "suimin_source_ingest cell scaffold-only — Council has not (a) attested the "
               "suimin master charter ADR-2606072800 (silen-suimin master-charter-baseline), "
               "or (b) ratified the source whitelist registry (G1 — only PubMed/Cochrane/"
               "ICSD-3/ICD-11/AASM/national-sleep-society sources are admissible, every claim "
               "needs verifiable provenance). Do not deploy.")
          {:scaffold true :cell :suimin-source-ingest})))
