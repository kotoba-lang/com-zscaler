(ns kamado.methods.ingest
  "kamado 竈 — legacy oil-refining export → kotoba EAVT migrator (the pure core).
  Clojure port of methods/ingest.py (1:1). ADR-2606051500.

  Migrates a legacy refinery node-export into kamado-EAVT dicts (refineries / units / outages),
  G1/G4-guarded: a refinery is an ORG asset, never a person (a person field, or a non-org
  operator, is refused on sight); migrated assets are :observed-fossil / :representative
  (observation of a fossil asset, NOT a :synthesis record — the feedstock guard is never
  bypassed). to-kg-batch shapes the kotoba kg.ingest contract.

  The network push (push_batch / urllib) + the live --live gate live only in the Python main
  (G8-gated), omitted from this port; the migration core is pure stdlib, network-free."
  (:require [clojure.string :as str]))

;; G4: fields that would tie a refinery to a natural person — refused on sight.
(def PERSON-FIELDS ["owner_person" "ceo" "person" "individual" "operator_person" "crew"])
(def STATUS-MAP {"active" ":active" "idled" ":idled" "idle" ":idled"
                 "decommissioning" ":decommissioning" "converted" ":converted"
                 "down" ":down" "planned" ":planned" "resolved" ":resolved"})
(def UNIT-KINDS #{"cdu" "fcc" "hydrocracker" "coker" "reformer" "hydrotreater" "alkylation"})

(defn- status* [s & [default]]
  (get STATUS-MAP (str/lower-case (str/trim (str (or s "")))) (or default ":active")))

(defn- rid* [code]
  (let [code (str code) i (.indexOf code "-")]
    (if (neg? i)
      (str "rf." (str/lower-case code))
      (let [cc (subs code 0 i) rest (subs code (inc i))]
        (if (str/blank? rest)
          (str "rf." (str/lower-case code))
          (str "rf." (str/lower-case cc) "." (str/replace (str/lower-case rest) "-" "_")))))))

(defn- uid* [code unit-type]
  (str "u." (-> (rid* code) (subs 3) (str/replace "." "_")) "." (str/lower-case (str unit-type))))

(defn- lstrip-colon [k] (str/replace (str k) #"^:+" ""))

(defn- guard-no-person [node ctx]
  (doseq [f PERSON-FIELDS]
    (when (get node f)
      (throw (ex-info (str "G4 violation (" ctx "): export carries a person field " (pr-str f)
                           "; a refinery is an org asset, never a person (operator must be an "
                           "org.corp.* id). Refusing.") {:gate "G4"})))))

(defn migrate
  "Legacy node export → {:refineries :units :outages} kamado-EAVT maps (keyed by id)."
  [export]
  (let [refineries
        (reduce (fn [m r]
                  (guard-no-person r (str "Refinery " (get r "refinery_code")))
                  (let [rid (rid* (get r "refinery_code"))
                        op (get r "operator_org")]
                    (when (and op (not (str/starts-with? (str op) "org.")))
                      (throw (ex-info (str "G4 violation: operator " (pr-str op)
                                           " is not an org.corp.* id (" rid ")") {:gate "G4"})))
                    (assoc m rid {":refinery/id" rid
                                  ":refinery/name" (or (get r "name") (get r "refinery_code"))
                                  ":refinery/country" (get r "country_code")
                                  ":refinery/operator" op
                                  ":refinery/throughput-bpd" (get r "throughput_bpd" 0)
                                  ":refinery/status" (status* (get r "status"))
                                  ":refinery/feedstock-class" ":observed-fossil"
                                  ":refinery/transition-readiness" ":unknown"
                                  ":refinery/sourcing" ":representative"})))
                {} (get export "Refinery" []))
        units
        (reduce (fn [m u]
                  (let [kind (str/lower-case (str (or (get u "unit_type") "")))
                        uid (uid* (get u "refinery_code") kind)]
                    (assoc m uid {":unit/id" uid
                                  ":unit/refinery" (rid* (get u "refinery_code"))
                                  ":unit/kind" (if (contains? UNIT-KINDS kind) (str ":" kind) ":unknown")
                                  ":unit/status" (status* (get u "status"))
                                  ":unit/sourcing" ":representative"})))
                {} (get export "RefineryUnit" []))
        outages
        (reduce (fn [m o]
                  (let [kind (str/lower-case (str (or (get o "unit_type") "")))
                        as-of (get o "as_of" "")
                        oid (str "o." (-> (rid* (get o "refinery_code")) (subs 3) (str/replace "." "_"))
                                 "." kind "." as-of)]
                    (assoc m oid {":outage/id" oid
                                  ":outage/unit" (uid* (get o "refinery_code") kind)
                                  ":outage/status" (status* (get o "status") ":planned")
                                  ":outage/as-of" as-of
                                  ":outage/sourcing" ":representative"})))
                {} (get export "RefineryOutage" []))]
    {:refineries refineries :units units :outages outages}))

(defn dedup-vs-seed
  "Seed identity wins (watari convention): keep seed rows, add only new migrated ids.
  Returns [merged added]."
  [migrated seed-dict]
  (reduce (fn [[out added] [k v]]
            (if (contains? out k) [out added] [(assoc out k v) (inc added)]))
          [seed-dict 0] migrated))

(defn- entity* [eid etype label row relations]
  (let [claims (for [[k v] row
                     :when (or (not (contains? #{nil "" 0} v)) (str/ends-with? (str k) "throughput-bpd"))]
                 {"pred" (lstrip-colon k) "value" (str v)})]
    {"id" eid "type" etype "label_en" label "claims" (vec claims) "relations" relations}))

(defn to-kg-batch
  "Shape the migrated maps into the kotoba kg.ingest_batch contract {entities […]}."
  [{:keys [refineries units outages]}]
  (let [ref-es (map (fn [[rid r]] (entity* (str "refinery." rid) "refinery-asset"
                                           (get r ":refinery/name" rid) r [])) refineries)
        unit-es (map (fn [[uid u]] (entity* (str "unit." uid) "refinery-unit" uid u
                                            [{"pred" "unit/refinery" "target" (str "refinery." (get u ":unit/refinery"))}]))
                     units)
        outage-es (map (fn [[oid o]] (entity* (str "outage." oid) "refinery-outage" oid o
                                              [{"pred" "outage/unit" "target" (str "unit." (get o ":outage/unit"))}]))
                       outages)]
    {"entities" (vec (concat ref-es unit-es outage-es))}))
