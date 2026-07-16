(ns keizu.methods.autorun
  "autorun.cljc — keizu (系図) AUTONOMOUS government-power-relations heartbeat on the kotoba Datom log.
  ADR-2606066000. 1:1 Clojure port of `methods/autorun.py`.

  This is what 'kotoba で自律的に稼働する' means for keizu, in the constitution-permitted form
  (mirrors shionome / ipaddress / yabai / sukashi / watatsuna / watari / kabuto / kanjō / danjo
  autorun). Each heartbeat the actor runs its whole power-relations pipeline ITSELF:

    observe (load the OFFLINE relation-graph seed) → weave (validate every node/rel/committee/
      money/statement against the gates; raises on a violation) → concentration (aggregate,
      edge-primary) → PERSIST a content-addressed transaction to the append-only kotoba Datom log
      (graph datoms + derived :keizu.conc/* signals), linking the previous tx's CID.

  Constitutional posture holds by construction: an accountability MAP, never a target-list; FACTUAL +
  non-adjudicating; no-doxxing (PII node attrs unrepresentable, validated by weave); edge-primary
  (no per-person score). The loop is deterministic / resume-safe: canonical-order sorts datoms by
  canonical JSON before hashing so the CID is reproducible across processes. Append-only.

  House style: Python ':…' keyword strings stay strings; canonical JSON via the kotoba sibling's
  reader; the kotoba/weave/edn siblings supply the log + metrics; file I/O at the #?(:clj) edge.
  Omits the Python __main__ CLI (the test suite + a -main analogue cover it)."
  (:require [clojure.string :as str]
            #?(:clj [keizu.methods.edn :as kedn])
            [keizu.methods.weave :as w]
            [keizu.methods.kotoba :as k]))

#?(:clj
   (def ^:private here (.getParentFile (java.io.File. ^String *file*))))
#?(:clj
   (def ^:private data (java.io.File. (.getParentFile here) "data")))
#?(:clj
   (def SEED (java.io.File. data "seed-relation-graph.kotoba.edn")))
#?(:clj
   (def LOG (java.io.File. data "keizu.datoms.kotoba.edn")))

(def BASE-AS-OF 20260609)

;; ── canonical JSON for the datom sort key (json.dumps sort_keys=True, default separators) ──
(defn- sort-key
  "json.dumps(d, ensure_ascii=False, sort_keys=True) of one datom — the canonical sort key."
  [d]
  (#'k/canon d))

(defn canonical-order
  "Sort datoms by canonical JSON so the tx is DETERMINISTIC regardless of any set-iteration
  order inside concentration. EAVT assertions are an unordered set, so a canonical sort makes the
  content-addressed CID reproducible / resume-safe."
  [datoms]
  (vec (sort-by sort-key datoms)))

#?(:clj
   (defn run-cycle
     "One autonomous heartbeat: observe → weave (validate) → concentration → persist a
     content-addressed Datom transaction (graph + derived :keizu.conc/* signals). cycle drives
     tx-id + as-of."
     ([cycle] (run-cycle cycle SEED LOG))
     ([cycle seed-path log-path]
      (let [g (w/weave (kedn/load-edn seed-path))          ;; observe + VALIDATE (raises on any gate)
            c (w/concentration g)                          ;; aggregate, edge-primary (G4)
            datoms (canonical-order (concat (k/graph-datoms g) (k/derived-datoms c)))
            tx (k/make-tx datoms :tx-id cycle :as-of (+ BASE-AS-OF cycle)
                          :prev-cid (k/head-cid log-path))
            cid (k/append-tx tx log-path)]                 ;; PERSIST to append-only LOCAL kotoba log
        {"cycle" cycle
         "nodes" (get c "node_count")
         "rels" (get c "rel_count")
         "committees" (get c "committee_count")
         "money" (get c "money_count")
         "money_hhi" (get-in c ["money_concentration" "hhi"])
         "revolving" (count (get c "revolving_door"))
         "award_fund" (count (get c "award_and_fund"))
         "datoms" (count datoms)
         "cid" cid}))))

#?(:clj
   (defn run-autonomous
     ([] (run-autonomous 3 SEED LOG))
     ([cycles] (run-autonomous cycles SEED LOG))
     ([cycles seed-path log-path]
      (let [beats (mapv #(run-cycle % seed-path log-path) (range 1 (inc cycles)))]
        {"cycles" cycles
         "beats" beats
         "log_length" (count (k/read-log log-path))
         "head_cid" (k/head-cid log-path)
         "chain" (k/verify-chain log-path)}))))
