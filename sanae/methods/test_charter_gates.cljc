(ns sanae.methods.test-charter-gates
  "sanae 早苗 — constitutional-gate conformance tests (local lexicons).

  Substrate-native Clojure (clj + datomic first tier). sanae is field-agriculture robotics
  (sow/weed/harvest) — mechanical/biological only, herbicide-free, seed-sovereign, regenerative.
  Completes the labor-liberation robotics trio (sanae/hataori/kiyome, ADR-2606032100). Its
  G9 regenerative-only discipline is const/enum-encoded across the 5 first-tier `lex/*.edn`
  lexicons (read via clojure.edn). This suite pins them so a future R-phase cell wave cannot
  silently drift them:

    G9  regenerative-only (headline) — weedingPassRecord.herbicideFree const true; weeding method
        ∈ {mechanical, laser} (no chemical/spray); bed-prep ∈ {low-till, no-till} (no plow/till)
    G9  seed-sovereign — seedingAttestation.patented const false (no patented/licensed seed),
        seedBankSource required
    regenerative soil — soilRegenerationReport records soilCarbonDeltaPermille; field phases bounded

  It weakens no gate; it asserts them. The dividend-coupling (G2), cash≡0 (G5), Murakumo-only (G4),
  outward-gating (G7) and witness-quorum (G3) gates live in cells/manifest and are untouched here."
  (:require [clojure.test :refer [deftest is run-tests]]
            [clojure.set :as set]
            [clojure.edn :as edn]))

#?(:clj
   (do
     (def ^:private here (.getParentFile (java.io.File. ^String *file*)))      ;; methods/
     (def ^:private actor-dir (.getParentFile here))                          ;; sanae/
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
        unchanged (2607-edn-datomize actor fan-out; root/20-actors/sanae)."
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

;; ── G9 — regenerative-only: herbicide-free, mechanical weeding, no-till ──
(deftest g9-regenerative-no-chemical
  (let [w (lex "weedingPassRecord")]
    (is (contains? (required-of w) "herbicideFree") "G9: weeding pass must record herbicideFree")
    (is (= true (const-of w :herbicideFree)) "G9: herbicideFree const true")
    (let [m (enum-of w :method)]
      (is (= #{"mechanical" "laser"} m) (str "G9: weeding method must be {mechanical, laser}, got " m))
      (is (empty? (set/intersection m #{"chemical" "spray" "herbicide" "glyphosate"}))
          "G9: no chemical-herbicide weeding method representable")))
  (is (= #{"low-till" "no-till"} (enum-of (lex "fieldStateRecord") :bedPrepMethod))
      "G9: bed-prep is {low-till, no-till} (no plow/till)"))

;; ── G9 — seed-sovereign: no patented/licensed seed ──
(deftest g9-seed-sovereign
  (let [s (lex "seedingAttestation")]
    (is (= false (const-of s :patented)) "G9: seedingAttestation.patented const false")
    (is (contains? (required-of s) "seedBankSource") "G9: seeding must cite a seedBankSource")))

;; ── regenerative soil + bounded field phases ──
(deftest regenerative-soil-and-phases
  (is (contains? (required-of (lex "soilRegenerationReport")) "soilCarbonDeltaPermille")
      "regenerative: soil report records the soil-carbon delta (‰)")
  (is (= #{"prepared" "seeded" "weeded" "harvested" "fallow"} (enum-of (lex "fieldStateRecord") :phase))
      "field phase is a bounded set"))

#?(:clj
   (defn -main [& _]
     (let [r (run-tests 'sanae.methods.test-charter-gates)]
       (System/exit (if (zero? (+ (:fail r) (:error r))) 0 1)))))
