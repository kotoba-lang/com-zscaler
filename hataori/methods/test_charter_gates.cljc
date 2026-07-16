(ns hataori.methods.test-charter-gates
  "hataori 機織 — constitutional-gate conformance tests (local lexicons).

  Substrate-native Clojure (clj + datomic first tier). hataori is garment/apparel robotics built
  to END the sweatshop (ISIC C13-14) — not to make a faster one (N3). Its charter discipline is
  const/enum-encoded across the 5 first-tier `lex/*.edn` lexicons (read via clojure.edn). This
  suite pins them so a future R-phase cell wave cannot silently drift them:

    G9  fair-labor-provenance (headline) — every finished lot binds a displacedCohortId and pins
        noWorkerBelowBhi const true (no displaced worker re-employed below Basic-High-Income)
    G1  open patterns — cuttingPlanAttestation.patented const false (no patented/licensed pattern)
    QC  needle-detect (検針) safety — quality inspection records needleClear + a bounded result

  It weakens no gate; it asserts them. The dividend-coupling (G2), cash≡0 (G5), Murakumo-only (G4),
  outward-gating (G7) and no-wearer-surveillance (N2) gates live in cells/manifest and are
  untouched here."
  (:require [clojure.test :refer [deftest is run-tests]]
            [clojure.edn :as edn]))

#?(:clj
   (do
     (def ^:private here (.getParentFile (java.io.File. ^String *file*)))      ;; methods/
     (def ^:private actor-dir (.getParentFile here))                          ;; hataori/
     (def ^:private lexdir (java.io.File. actor-dir "lex"))

     (defn- unblob
       "edn-datomize.bb pr-str's non-scalar values into a blob string; read it back."
       [v]
       (if (string? v)
         (try (let [parsed (edn/read-string v)] (if (coll? parsed) parsed v))
              (catch Exception _ v))
         v))

     (defn- reconstitute-entity
       "Datomic/Datascript tx-data [{:db/id -1 :lex.<name>/k v ...}] -> the original
        un-namespaced lexicon map, so downstream (:defs / :id / :lexicon) lookups are
        unchanged (Phase 4 edn-datomize actor fan-out; root/20-actors/hataori)."
       [tx-data]
       (into {} (map (fn [[k v]] [(keyword (name k)) (unblob v)]))
             (dissoc (first tx-data) :db/id)))

     (defn- lex [name]
       (let [content (edn/read-string (slurp (java.io.File. lexdir (str name ".edn"))))]
         (if (and (vector? content) (map? (first content)) (contains? (first content) :db/id))
           (reconstitute-entity content)
           content)))))

(defn- record-node [doc] (get-in doc [:defs :main :record]))
(defn- required-of [doc] (set (:required (record-node doc))))
(defn- const-of [doc field] (get-in (record-node doc) [:properties field :const]))
(defn- enum-of [doc field]
  (let [p (get-in (record-node doc) [:properties field])]
    (set (or (:enum p) (get-in p [:items :enum])))))

;; ── G9 — fair-labor-provenance: no displaced worker re-employed below BHI ──
(deftest g9-fair-labor-provenance
  (let [p (lex "fairLaborProvenance")
        req (required-of p)]
    (is (contains? req "displacedCohortId")
        "G9: every finished lot must bind a displacedCohortId (dividend-attested)")
    (is (contains? req "noWorkerBelowBhi")
        "G9: provenance must require the no-worker-below-BHI attestation")
    (is (= true (const-of p :noWorkerBelowBhi))
        "G9: noWorkerBelowBhi const true (Basic-High-Income floor)")))

;; ── G1 — open patterns (no patented/licensed pattern) ──
(deftest g1-open-patterns
  (let [c (lex "cuttingPlanAttestation")]
    (is (contains? (required-of c) "patternSource") "G1: a cutting plan must cite its pattern source")
    (is (= false (const-of c :patented)) "G1: cuttingPlanAttestation.patented const false")))

;; ── QC — needle-detect (検針) safety + bounded result ──
(deftest qc-needle-detect
  (let [q (lex "qualityInspectionRecord")]
    (is (contains? (required-of q) "needleClear") "QC: inspection must record a needle-clear (検針) result")
    (is (= #{"PASS" "REWORK" "FAIL"} (enum-of q :result)) "QC: result is a bounded set")))

#?(:clj
   (defn -main [& _]
     (let [r (run-tests 'hataori.methods.test-charter-gates)]
       (System/exit (if (zero? (+ (:fail r) (:error r))) 0 1)))))
