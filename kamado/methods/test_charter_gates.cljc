(ns kamado.methods.test-charter-gates
  "kamado 竈 — constitutional-gate conformance tests (local lexicons).

  Substrate-native Clojure (clj + datomic first tier). kamado is closed-loop carbon refining +
  fossil-refinery decommission/observation — fossil VIRGIN crude is structurally unrepresentable,
  and net carbon must be ≤0. Its discipline is const/enum-encoded across the 6 first-tier
  `lex/*.edn` lexicons (read via clojure.edn). This suite pins them so a future R-phase cell wave
  cannot silently drift them:

    fossil-prohibition (headline) — feedstockClass ∈ {biogenic, captured-co2, recycled-carbon,
      existing-inventory-decommission} ONLY; no virgin/crude/fossil feedstock representable;
      feedstockProvenance.closedLoop + screened const true
    net≤0 — carbonBalance records the net ΔtCO2e/t and a D3 (net≤0) pass
    no-server-key — decommissionPlan.serverHeldKey const false; outward-gated
    observation-only on legacy refineries — refineryAsset.isObservation const true
    renewable energy + bounded product fate — synthesisRun.energy + productFate are bounded sets
    decommission-not-operate — interventions are {decommission, remediate, convert, monitor}

  It weakens no gate; it asserts them. The no-server-key invariant is pinned directly here."
  (:require [clojure.test :refer [deftest is run-tests]]
            [clojure.set :as set]
            [clojure.string :as str]
            [clojure.edn :as edn]))

#?(:clj
   (do
     (def ^:private here (.getParentFile (java.io.File. ^String *file*)))      ;; methods/
     (def ^:private actor-dir (.getParentFile here))                          ;; kamado/
     (def ^:private lexdir (java.io.File. actor-dir "lex"))
     (defn- unblob
       "lex/*.edn files are now Datomic/Datascript tx-data (Phase 4 edn-datomize):
        non-scalar values (here, :defs) are pr-str'd into a blob string. Parse it
        back into a real map for callers that expect the original wire shape."
       [v]
       (if (string? v)
         (try (let [parsed (edn/read-string v)] (if (coll? parsed) parsed v))
              (catch Exception _ v))
         v))
     (defn- reconstitute-entity
       "Reconstitute the original bare {:lexicon :id :defs} map from a
        [{:db/id -1 <ns>/<key> <value> ...}] tx-data vector, so downstream
        get-in/:lexicon/:id/:defs lookups keep working unchanged."
       [tx-data]
       (into {} (map (fn [[k v]] [(keyword (name k)) (unblob v)]))
             (dissoc (first tx-data) :db/id)))
     (defn- lex [name]
       (reconstitute-entity
        (edn/read-string (slurp (java.io.File. lexdir (str name ".edn"))))))))

(defn- record-node [doc] (get-in doc [:defs :main :record]))
(defn- required-of [doc] (set (:required (record-node doc))))
(defn- const-of [doc field] (get-in (record-node doc) [:properties field :const]))
(defn- enum-of [doc field]
  (let [p (get-in (record-node doc) [:properties field])]
    (set (or (:enum p) (get-in p [:items :enum])))))

(def CLOSED-LOOP-FEEDSTOCK
  #{"biogenic" "captured-co2" "recycled-carbon" "existing-inventory-decommission"})
(def FOSSIL-TOKENS #{"virgin" "crude" "fossil" "petroleum" "wellhead"})

;; ── headline — fossil VIRGIN feedstock is unrepresentable ──
(deftest fossil-prohibition
  (doseq [n ["synthesisRun" "feedstockProvenance"]]
    (let [e (enum-of (lex n) :feedstockClass)]
      (is (= CLOSED-LOOP-FEEDSTOCK e)
          (str "fossil-prohibition: " n ".feedstockClass must be the closed-loop set, got " e))
      (is (empty? (filter (fn [v] (some #(str/includes? (str/lower-case v) %) FOSSIL-TOKENS)) e))
          (str "fossil-prohibition: " n " must not make a virgin/crude/fossil feedstock representable"))))
  (let [p (lex "feedstockProvenance")]
    (is (= true (const-of p :closedLoop)) "feedstock is closed-loop (const true)")
    (is (= true (const-of p :screened)) "feedstock is screened (const true)")))

;; ── net≤0 carbon balance (D3) ──
(deftest net-leq-zero-carbon
  (let [r (required-of (lex "carbonBalance"))]
    (is (contains? r "netDeltaTco2ePerT") "carbonBalance must record net ΔtCO2e/t")
    (is (contains? r "passesD3") "carbonBalance must record the D3 (net≤0) pass")))

;; ── no-server-key + outward-gated decommission ──
(deftest no-server-key-outward-gated
  (let [d (lex "decommissionPlan")]
    (is (contains? (required-of d) "serverHeldKey") "decommissionPlan must declare serverHeldKey")
    (is (= false (const-of d :serverHeldKey)) "decommissionPlan.serverHeldKey const false")
    (is (= true (const-of d :outwardGated)) "decommissionPlan is outward-gated")))

;; ── legacy refineries are OBSERVED, not operated ──
(deftest observation-only-legacy
  (is (= true (const-of (lex "refineryAsset") :isObservation))
      "refineryAsset.isObservation const true (legacy refineries are mirrored, not operated)"))

;; ── renewable-leaning energy + bounded product fate ──
(deftest energy-and-product-fate
  (let [s (lex "synthesisRun")]
    (is (= #{"hikari-renewable" "grid-mixed"} (enum-of s :energy))
        "synthesisRun.energy is a bounded set (renewable-leaning)")
    (is (= #{"combusted-fuel" "durable-material"} (enum-of s :productFate))
        "synthesisRun.productFate is a bounded set")))

;; ── decommission interventions are transition-only (no operate/expand) ──
(deftest decommission-not-operate
  (let [i (enum-of (lex "decommissionPlan") :intervention)]
    (is (= #{"decommission" "remediate" "convert" "monitor"} i)
        (str "intervention must be transition-only {decommission, remediate, convert, monitor}, got " i))
    (is (empty? (set/intersection i #{"operate" "expand" "commission" "restart"}))
        "no operate/expand/restart intervention representable")))

#?(:clj
   (defn -main [& _]
     (let [r (run-tests 'kamado.methods.test-charter-gates)]
       (System/exit (if (zero? (+ (:fail r) (:error r))) 0 1)))))
