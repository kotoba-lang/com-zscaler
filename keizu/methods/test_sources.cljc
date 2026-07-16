(ns keizu.methods.test-sources
  "test_sources.py — 系図 (keizu) public-source registry well-formedness + deny-list. ADR-2606066000.
  1:1 Clojure port (stdlib _t harness → clojure.test).

  Validates registry/sources.seed.json: required fields, valid kinds + mapsTo targets, the
  unverified-seed safety state (G8), and the no-commercial-gov-intel deny-list (Charter Rider §2(e),
  N5) — the structural gate keizu inherits from danjo G8."
  (:require [clojure.test :refer [deftest is run-tests]]
            [clojure.string :as str]
            #?(:clj [clojure.java.io :as io])
            [keizu.methods.registry :as registry]
            [keizu.methods.weave :as w]))

(def ^:private DENY w/SOURCE-DENY)   ;; single source of truth (also enforced at runtime)

;; …/20-actors/keizu/methods/test_sources.cljc → up 2 = keizu → registry/sources.seed.json
(def ^:private reg-file
  #?(:clj (io/file (-> *file* io/file .getParentFile .getParentFile) "registry" "sources.seed.json")))

(def ^:private SOURCE-KINDS
  #{"procurement" "budget" "political-finance" "committee-roster" "statements"})
(def ^:private MAPSTO-PREFIXES #{"node" "committee" "rel" "money" "statement"})
(def ^:private REQUIRED
  ["sourceId" "title" "jurisdiction" "sourceKind" "authority" "datasetUrl"
   "legalBasis" "mapsTo" "verificationStatus"])

(defn- reg [] (registry/load-registry reg-file))

(deftest test-registry-parses-and-nonempty
  (let [r (reg)]
    (is (seq (get r "sources")) "registry has no sources")
    (is (> (get r "freshnessWindowDays" 0) 0))))

(deftest test-every-source-has-required-fields
  (doseq [s (get (reg) "sources")]
    (doseq [f REQUIRED]
      (is (get s f) (str (pr-str (get s "sourceId")) " missing " f)))))

(deftest test-source-kinds-valid
  (doseq [s (get (reg) "sources")]
    (is (contains? SOURCE-KINDS (get s "sourceKind")) (pr-str [(get s "sourceId") (get s "sourceKind")]))))

(deftest test-mapsto-targets-valid
  (doseq [s (get (reg) "sources")]
    (let [m (get s "mapsTo")
          targets (if (sequential? m) m [m])]
      (doseq [t targets]
        (is (contains? MAPSTO-PREFIXES (first (str/split t #":")))
            (pr-str [(get s "sourceId") t]))))))

(deftest test-all-unverified-seed
  ;; G8 — nothing is verified yet, so no live ingest may run (safety default)
  (doseq [s (get (reg) "sources")]
    (is (= "unverified-seed" (get s "verificationStatus")) (get s "sourceId"))))

(deftest test-urls-present-and-httpish
  (doseq [s (get (reg) "sources")]
    (is (str/starts-with? (get s "datasetUrl") "http") (get s "sourceId"))))

(deftest test-no-commercial-gov-intel-terminal
  ;; Charter Rider §2(e) / N5 — the deny-list must not appear in any source's url/title/authority
  (let [blob (str/lower-case (slurp reg-file))
        hits (filter #(str/includes? blob %) DENY)]
    (is (empty? hits) (str "prohibited commercial gov-intel terminal in registry: " (vec hits)))))

(deftest test-global-coverage
  ;; the registry is global (the chosen scope) — multiple jurisdictions present
  (let [juris (set (map #(get % "jurisdiction") (get (reg) "sources")))]
    (is (clojure.set/subset? #{"jp" "us" "eu"} juris) (pr-str juris))))

#?(:clj (defn -main [& _] (run-tests 'keizu.methods.test-sources)))
