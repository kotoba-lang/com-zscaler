(ns abaki.methods.ingest-intel
  "ingest_intel.py — 暴 (abaki) intel→abaki OSINT ingestion pipeline.
  1:1 Clojure port of `methods/ingest_intel.py` (ADR-2606073100).

  Mock integration with the `intel` actor's OSINT pipeline: it simulates a query against the
  intel entity-graph for Chokepoint/Monopoly indicators (M&A, patents, licensing changes),
  then merges those OSINT findings into abaki's primary seed data (data/seed.json) — updating
  the traits + intel_findings of an existing entity in place, or appending a new one.

  Gates (constitutional, route-AROUND-not-punishment; map-not-target; OSINT-public-only):
  the simulated intel records carry DISCLOSED traits + public findings only — no private feed,
  no attack, just structural intelligence merged into the public seed.

  House style: Python dicts stay STRING-keyed maps; ':…' keyword strings stay strings; pure
  fns; file/host I/O only behind #?(:clj ...). The Python `__main__` demo printer is omitted —
  the method API (fetch-from-intel / ingest-to-abaki / merge-intel) is the cell contract;
  -main mirrors the demo at the #?(:clj) edge."
  (:require [clojure.string :as str]
            #?(:clj [abaki.methods.analyze :as a])
            #?(:clj [clojure.java.io :as io])))

(defn fetch-from-intel
  "Mock integration with the `intel` actor's OSINT pipeline. In a real implementation this
  would call intel.etzhayyim.com/xrpc/etzhayyim.intel.v1.QueryEntityGraph or subscribe to its
  datom log to find monopolistic indicators (M&A, patents, licensing changes). Returns a vector
  of string-keyed intel records — byte-for-byte the shapes Python's list-of-dicts produced."
  []
  #?(:clj (println "[intel OSINT] Querying intel graph for Chokepoint/Monopoly indicators..."))
  [{"id" "entity:compute:megacorp_a"
    "name" "MegaCorp AI Compute"
    "domain" "compute"
    "intel_findings" ["Acquired 3 major open-source AI startups this quarter (M&A consolidation)."
                      "Changed licensing of core model infrastructure from open to proprietary closed-source."
                      "Increased API pricing by 400% after achieving 70% market share."]
    "traits" {"closed_source_models" true
              "proprietary_hardware_lockin" true
              "pricing_power_abuse" true}
    "beneficial_owners" ["individual:tech_baron_x"]}
   {"id" "entity:biology:agri_monopoly_b"
    "name" "GlobalSeeds Inc."
    "domain" "biology"
    "intel_findings" ["Sued 50+ independent farmers for accidental cross-pollination of patented traits."
                      "Lobbied successfully to ban seed-saving practices in 3 new jurisdictions."]
    "traits" {"f1_hybrid_lockin" true
              "gene_patents" true
              "lawsuits_against_farmers" true}
    "beneficial_owners" ["individual:agri_baron_y" "vc:fund_z"]}])

(defn merge-intel
  "Pure core of ingest_to_abaki's merge: given the existing seed map ({\"entities\" [..]}) and
  the intel-data records, return the updated seed map. Existing entities are keyed by id; a
  matching record UPDATES the existing entity's traits (dict.update semantics = right-biased
  merge) + replaces intel_findings; a non-matching record is appended as the full record.

  Insertion order is preserved (existing entities in their seed order, then any new records in
  intel-data order) — mirrors Python's dict insertion-order-preserving list(values())."
  [existing-data intel-data]
  (let [entities (get existing-data "entities" [])
        ;; ordered list of existing ids (Python dict insertion order)
        order (mapv #(get % "id") entities)
        by-id (reduce (fn [m e] (assoc m (get e "id") e)) {} entities)
        [by-id order]
        (reduce
         (fn [[by-id order] record]
           (let [entity-id (get record "id")]
             (if (contains? by-id entity-id)
               [(update by-id entity-id
                        (fn [existing]
                          (-> existing
                              (update "traits" merge (get record "traits"))
                              (assoc "intel_findings" (get record "intel_findings" [])))))
                order]
               [(assoc by-id entity-id record)
                (conj order entity-id)])))
         [by-id order]
         intel-data)]
    {"entities" (mapv #(get by-id %) order)}))

#?(:clj
   (defn ingest-to-abaki
     "Merge intel OSINT findings into abaki's primary seed data (data/seed.json). File I/O only
     at this #?(:clj) edge; the merge itself is the pure `merge-intel`."
     [intel-data]
     (let [base-dir  (-> *file* io/file .getParentFile .getParentFile)
           data-file (io/file base-dir "data" "seed.json")]
       (println (str "[abaki Ingest] Merging " (count intel-data)
                     " OSINT intelligence records into abaki dataset..."))
       (let [existing-data (if (.exists data-file)
                             (a/read-json (slurp data-file))
                             {"entities" []})
             updated-data  (merge-intel existing-data intel-data)]
         (spit data-file (a/to-json updated-data))
         (println (str "✅ Successfully updated abaki dataset at " data-file))))))

#?(:clj
   (defn -main
     "CLI entry: run the intel→abaki OSINT ingestion pipeline."
     [& _argv]
     (println "=== abaki OSINT Ingestion Pipeline (intel -> abaki) ===")
     (ingest-to-abaki (fetch-from-intel))
     (println "=======================================================")))
