#!/usr/bin/env bb
;; kenchi 検地 — structural charter-gate conformance tests over the central lexicons.
;;
;; ADR-2606272030. kenchi is an EXTERNAL-MARKET real-estate valuation + transparency
;; substrate — NOT a brokerage, NOT a closed AVM, NOT an advice renderer. Its discipline
;; is structural and is pinned here at the schema layer so a silent weakening is caught:
;;
;;   G3 PROVENANCE-OR-SILENCE   — valuation MUST carry provenance + nComps(≥3) + nAuthorities(≥2) + CI.
;;   G4 DERIVED/AGGREGATE       — a PUBLISHED record's license is exactly {open, derived-only}.
;;   G5 INALIENABLE-LAND        — assetClass is exactly {external-market}; manifest pins the exclusion + N1.
;;   G6 NO-PII                  — no owner/person/PII field is representable on a valuation.
;;   N8 NO SINGLE-SOURCE ORACLE — provenanceAttestation MUST carry nAuthorities + nComps + verdict.
;;
;; Babashka, pure (clj/bb over the kotoba substrate convention — replaces the legacy .py).
;; Run standalone: `bb methods/test_charter_gates.clj`  (or via `bb run_tests.clj`).
(ns test-charter-gates
  (:require [clojure.test :refer [deftest is run-tests]]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [cheshire.core :as json]))

(def publish-licenses #{"open" "derived-only"})            ; G4: never raw/restricted
(def pii-tokens ["owner" "person" "occupant" "resident" "pii" "ssn" "name"])  ; G6
(def asset-classes #{"external-market"})                   ; G5

(def ^:private script-dir (-> *file* io/file .getCanonicalFile .getParentFile))
(def ^:private actor-dir (.getParentFile script-dir))

(defn- find-root []
  (loop [d actor-dir]
    (cond
      (nil? d) (throw (ex-info "could not locate 00-contracts/lexicons/com/etzhayyim/kenchi" {}))
      (.isDirectory (io/file d "00-contracts" "lexicons" "com" "etzhayyim" "kenchi")) d
      :else (recur (.getParentFile d)))))

(def ^:private lex-dir (io/file (find-root) "00-contracts" "lexicons" "com" "etzhayyim" "kenchi"))

(defn- load-json [f] (json/parse-string (slurp f) true))
(defn- lex [name] (load-json (io/file lex-dir name)))
(defn- record-props [doc] (get-in doc [:defs :main :record :properties]))
(defn- required [doc] (set (get-in doc [:defs :main :record :required])))
(defn- known [props k] (set (get-in props [k :knownValues])))

(defn- all-prop-names [o]
  (cond
    (map? o) (concat (when (map? (:properties o)) (map name (keys (:properties o))))
                     (mapcat all-prop-names (vals o)))
    (sequential? o) (mapcat all-prop-names o)
    :else nil))

;; ─────────────────────────────────── gates ───────────────────────────────────

(deftest g3-provenance-or-silence
  (let [v (lex "valuation.json") req (required v) props (record-props v)]
    (doseq [f ["provenance" "nComps" "nAuthorities" "ciLoUsd" "ciHiUsd" "valueUsd"]]
      (is (contains? req f) (str "G3: valuation must require " f)))
    (is (= 3 (get-in props [:nComps :minimum])) "G3: nComps minimum must be 3")
    (is (= 2 (get-in props [:nAuthorities :minimum])) "G3: nAuthorities minimum must be 2")))

(deftest g4-published-license-is-open-or-derived-only
  (doseq [name ["valuation.json" "regionReport.json"]]
    (is (= publish-licenses (known (record-props (lex name)) :license))
        (str "G4: " name " license must be exactly " publish-licenses)))
  (is (contains? (known (record-props (lex "sourceLicense.json")) :licenseClass) "restricted")
      "G4: sourceLicense must still represent 'restricted' sources"))

(deftest g5-asset-class-is-external-market-only
  (is (= asset-classes (known (record-props (lex "valuation.json")) :assetClass))
      (str "G5: assetClass must be exactly " asset-classes)))

(deftest g5-manifest-pins-inalienable-exclusion
  (let [m (load-json (io/file actor-dir "manifest.jsonld"))
        g5 (get-in m [:constitutionalGates :gates :G5])
        n1 (get-in m [:nonGoals :goals :N1])]
    (is (str/includes? (str/upper-case g5) "INALIENABLE") "G5 must be a named gate")
    (is (str/includes? g5 "2605192245") "G5 must cite the Land-Sovereignty ADR")
    (is (and (str/includes? n1 "Land Trust")
             (str/includes? (str/lower-case n1) "no market price"))
        "N1 must exclude Land-Trust valuation")))

(deftest g6-no-pii-fields-anywhere-in-valuation
  (let [names (map str/lower-case (all-prop-names (lex "valuation.json")))
        leaked (sort (filter (fn [n] (some #(str/includes? n %) pii-tokens)) names))]
    (is (empty? leaked) (str "G6: PII-like fields must not exist on valuation: " leaked))))

(deftest n8-no-single-source-oracle
  (let [pa (lex "provenanceAttestation.json") req (required pa)]
    (doseq [f ["nAuthorities" "nComps" "verdict" "comps"]]
      (is (contains? req f) (str "N8: provenanceAttestation must require " f)))
    (is (= #{"published" "withheld-mrv" "insufficient-evidence"}
           (known (record-props pa) :verdict))
        "G3: verdict must offer MRV / insufficient-evidence, not only 'published'")))

(let [{:keys [fail error]} (run-tests 'test-charter-gates)]
  (println (format "── kenchi charter gates: %s ──" (if (zero? (+ fail error)) "ALL green" "FAILURES above")))
  (System/exit (if (pos? (+ fail error)) 1 0)))
