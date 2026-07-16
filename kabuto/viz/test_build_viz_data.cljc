(ns kabuto.viz.test-build-viz-data
  "Tests for the kabuto viz payload + datom builder (ADR-2606022000 port of build_viz_data
  build_payload + build_datoms). A precise hand fixture exercises out-degree, jurisdiction
  criticality-load with descending stable sort, address/contact sub-objects, commodity keyword-
  strip, name-defaults-to-id; edn-scalar is unit-tested across its value kinds; build-datoms is
  checked for keyword-passthrough / int / float / quoted-string encodings + the from/to edge skip;
  and a seed run pins aggregate counts against the trusted kabuto-edn classify."
  (:require [clojure.test :refer [deftest is]]
            [clojure.string :as str]
            [kabuto.methods.kabuto-edn :as kedn]
            [kabuto.viz.build-viz-data :as v]))

(def ^:private companies-fix
  [{":company/id" "c1" ":company/name" "Co One" ":company/ticker" "C1" ":company/sector" ":tech" ":company/country" "US"}
   {":company/id" "c2" ":company/name" "Co Two" ":company/country" "JP"}
   {":company/id" "c3" ":company/country" "US"}])

(def ^:private addresses-fix
  [{":company.address/id" "ad1" ":company.address/company" "c1" ":company.address/street" "1 Main"
    ":company.address/city" "Town" ":company.address/country" "US" ":company.address/lat" 24.7 ":company.address/lon" 121.0}])

(def ^:private contacts-fix
  [{":company.contact/id" "ct1" ":company.contact/company" "c1"
    ":company.contact/website" "c1.example" ":company.contact/ir-url" "c1.example/ir"}])

(def ^:private edges-fix
  [{":supply.edge/id" "e1" ":supply.edge/from" "c1" ":supply.edge/to" "c2" ":supply.edge/commodity" ":chips" ":supply.edge/criticality" 0.9}
   {":supply.edge/id" "e2" ":supply.edge/from" "c1" ":supply.edge/to" "c3" ":supply.edge/criticality" 0.1}
   {":supply.edge/id" "e3" ":supply.edge/from" "c2" ":supply.edge/to" "c3" ":supply.edge/commodity" ":steel" ":supply.edge/criticality" 0.5}])

(defn- payload-fix []
  (let [{:keys [companies]} (kedn/classify companies-fix)]
    (v/build-payload companies addresses-fix contacts-fix edges-fix)))

(defn- node [p id] (first (filter #(= id (get % "id")) (get p "companies"))))

(deftest test-payload-nodes-and-degree
  (let [p (payload-fix)]
    (is (= 3 (count (get p "companies"))))
    (is (= 3 (count (get p "edges"))))
    (is (= 2 (get (node p "c1") "out")))          ; e1 + e2
    (is (= 1 (get (node p "c2") "out")))          ; e3
    (is (= 0 (get (node p "c3") "out")))          ; sink → 0
    (is (= "c3" (get (node p "c3") "name")))      ; name defaults to id
    (is (= "Co One" (get (node p "c1") "name")))))

(deftest test-payload-address-contact
  (let [p (payload-fix)]
    (is (= {"street" "1 Main" "city" "Town" "country" "US" "lat" 24.7 "lon" 121.0}
           (get (node p "c1") "address")))
    (is (= {"website" "c1.example" "ir" "c1.example/ir"} (get (node p "c1") "contact")))
    (is (nil? (get (node p "c2") "address")))     ; no address row → nil
    (is (nil? (get (node p "c2") "contact")))))

(deftest test-payload-edges-and-jurisdictions
  (let [p (payload-fix)
        chips (first (filter #(= "c2" (get % "to")) (get p "edges")))
        unknown (first (filter #(= "c3" (get % "to")) (get p "edges")))]
    (is (= "chips" (get chips "commodity")))                  ; keyword stripped
    (is (= "unknown" (get unknown "commodity")))              ; absent → :unknown → "unknown"
    (is (= 0.9 (get chips "criticality")))
    ;; jur load: US = 0.9+0.1 (c1 from-edges) = 1.0 ; JP = 0.5 (c2) ; sorted desc, US first
    (is (= [{"country" "US" "load" 1.0} {"country" "JP" "load" 0.5}] (get p "jurisdictions")))))

(deftest test-edn-scalar
  (is (nil? (v/edn-scalar nil)))
  (is (nil? (v/edn-scalar "")))
  (is (= "true" (v/edn-scalar true)))
  (is (= "false" (v/edn-scalar false)))
  (is (= "5" (v/edn-scalar 5)))
  (is (= "8.0" (v/edn-scalar 8.0)))
  (is (= ":semiconductors" (v/edn-scalar ":semiconductors")))   ; keyword passthrough
  (is (= "\"TSMC\"" (v/edn-scalar "TSMC"))))                     ; quoted EDN string literal

(deftest test-datoms-encodings-and-entity
  (let [datoms (v/build-datoms (payload-fix))
        triples (set (map (juxt #(get % "e") #(get % "a") #(get % "v_edn")) datoms))]
    (is (contains? triples ["c1" ":company/id" "\"c1\""]))
    (is (contains? triples ["c1" ":company/sector" ":tech"]))      ; keyword passthrough
    (is (contains? triples ["c1" ":company/out" "2"]))             ; int
    (is (contains? triples ["c1" ":company.address/lat" "24.7"]))  ; float passthrough
    (is (contains? triples ["c3" ":company/out" "0"]))             ; sink degree present
    ;; c2 has no ticker → no ticker datom emitted
    (is (not (some #(and (= "c2" (get % "e")) (= ":company/ticker" (get % "a"))) datoms)))
    ;; edge entity id format + ":"+commodity + floated criticality
    (is (contains? triples ["supply.edge:c1>>>c2" ":supply.edge/commodity" ":chips"]))
    (is (contains? triples ["supply.edge:c1>>>c2" ":supply.edge/criticality" "0.9"]))))

(deftest test-datoms-skip-edge-missing-endpoint
  ;; An edge missing "to" produces no supply.edge datoms (the from/to guard).
  (let [payload {"companies" [] "edges" [{"from" "c1" "to" nil "commodity" "x" "criticality" 0.5}]}
        datoms (v/build-datoms payload)]
    (is (empty? (filter #(str/starts-with? (str (get % "e")) "supply.edge:") datoms)))))

(deftest test-seed-aggregate-counts
  (let [rows (kedn/load-edn "20-actors/kabuto/data/seed-public-companies.kotoba.edn")
        {:keys [companies addresses contacts edges]} (kedn/classify rows)
        p (v/build-payload companies addresses contacts edges)
        d (v/build-datoms p)]
    (is (= 1719 (count (get p "companies"))))
    (is (= 361 (count (get p "edges"))))
    (is (= 31 (count (get p "jurisdictions"))))
    (is (= 13363 (count d)))
    (is (= {"country" "US" "load" 33.85} (first (get p "jurisdictions"))))))
