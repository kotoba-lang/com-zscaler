(ns narashi.methods.test-charter-gates
  "narashi — constitutional-gate conformance tests (manifest + lexicons).
  Substrate-native Clojure, following the chigiri/kanae/musubi idiom
  (ADR-2606160842 py->clj port wave applied at inception — narashi never had
  a Python predecessor to prune). R0 design-only slice per ADR-2607101800:
  test-only, network-free, no cell execution. Fixtures below are the actor's
  own JSON schema documents (manifest.jsonld + 00-contracts lexicons) — no
  real country / personal / financial data is read or asserted."
  (:require [clojure.test :refer [deftest is run-tests]]
            [clojure.string :as str]
            [cheshire.core :as json]))

(def ^:private here (.getParentFile (java.io.File. ^String *file*)))     ;; methods/
(def ^:private actor-dir (.getParentFile here))                         ;; narashi/
(def ^:private actor-name (.getName actor-dir))
(def ^:private root (.. actor-dir getParentFile getParentFile))         ;; 20-actors -> ROOT
(def ^:private lexdir (java.io.File. root (str "00-contracts/lexicons/com/etzhayyim/" actor-name)))

(defn- manifest [] (json/parse-string (slurp (java.io.File. actor-dir "manifest.jsonld"))))
(defn- lex [name] (json/parse-string (slurp (java.io.File. lexdir (str name ".json")))))

;; ── generic JSON-schema introspection helpers (chigiri/kanae idiom) ──

(defn- collect
  "Walk a parsed JSON doc; for every map that contains `attr`, record
  {immediate-parent-key -> (get map attr)}. Works because every schema field
  here is shaped `\"fieldName\": {attr ...}` — the parent map key IS the
  field name the attr describes."
  [doc attr]
  (let [acc (atom {})]
    (letfn [(walk [x parent]
              (cond (map? x) (do (when (and (string? parent) (contains? x attr))
                                   (swap! acc assoc parent (get x attr)))
                                 (doseq [[k v] x] (walk v k)))
                    (sequential? x) (doseq [v x] (walk v parent))))]
      (walk doc nil))
    @acc))

(defn- a-const [doc field] (get (collect doc "const") field))
(defn- a-default [doc field] (get (collect doc "default") field))
(defn- known [doc field] (some-> (get (collect doc "knownValues") field) set))
(defn- a-max-length [doc field] (get (collect doc "maxLength") field))

(defn- required-union [doc]
  (let [acc (atom #{})]
    (letfn [(walk [x] (cond (map? x) (do (when (sequential? (get x "required")) (swap! acc into (get x "required")))
                                         (doseq [v (vals x)] (walk v)))
                            (sequential? x) (doseq [v x] (walk v))))]
      (walk doc))
    @acc))

(defn- property-keys [doc]
  (let [acc (atom #{})]
    (letfn [(walk [x] (cond (map? x) (do (when (map? (get x "properties")) (swap! acc into (keys (get x "properties"))))
                                         (doseq [v (vals x)] (walk v)))
                            (sequential? x) (doseq [v x] (walk v))))]
      (walk doc))
    @acc))

(def ^:private all-lexicon-names
  ["metricObservation" "crossReferenceNote" "metricNarrative" "methodNote"])

;; ── structural drift guard — manifest matches its own declared summary
;;    ("3 cells / 4 Lexicons / 9 gates / 8 non-goals") ──

(deftest test-manifest-structural-counts-match-declared-summary
  (let [m (manifest)]
    (is (= 3 (count (get m "cells"))) "manifest must path-reserve exactly 3 cells")
    (is (= #{"metric_ingest" "cross_reference" "narrative"}
           (set (map #(get % "name") (get m "cells"))))
        "cell name set drifted from ADR-2607101800 §3")
    (is (= 4 (count (get m "lexiconNamespaces"))) "manifest must declare exactly 4 lexicon namespaces")
    (is (= 9 (count (get-in m ["constitutionalGates" "gates"])))
        "manifest must declare exactly 9 gates")
    (is (= (set (keys (get-in m ["constitutionalGates" "gates"])))
           (set (map #(str "G" %) (range 1 10))))
        "gate id set must be exactly G1..G9")
    (is (= 8 (count (get-in m ["nonGoals" "goals"]))) "manifest must declare exactly 8 non-goals")))

(deftest test-all-4-lexicons-resolve
  ;; drift-zero: every declared lexiconNamespace must resolve to an actual file
  ;; whose "id" matches (mirrors sonae/musubi's manifest-lexicon-drift audits).
  (doseq [nsid (get (manifest) "lexiconNamespaces")]
    (let [short (last (str/split nsid #"\."))]
      (is (some #{short} all-lexicon-names) (str "unresolvable lexiconNamespace: " nsid))
      (is (= nsid (get (lex short) "id")) (str short ".json id must equal its declared namespace")))))

;; ── G4 — non-adjudicating: no rank/verdict/blame anywhere, descriptive-only
;;    indicator vocabulary, and metricNarrative's structural notice ──

(deftest test-g4-non-adjudicating-notice
  (is (contains? (required-union (lex "metricNarrative")) "nonAdjudicatingNotice")
      "G4: metricNarrative must require nonAdjudicatingNotice")
  (is (= true (a-const (lex "metricNarrative") "nonAdjudicatingNotice"))
      "G4: nonAdjudicatingNotice must be a structural const true"))

(deftest test-g4-indicator-vocabulary-is-descriptive-only
  (is (= #{"gini" "poverty-headcount-ratio-international" "poverty-headcount-ratio-national"
           "income-share-bottom40" "income-share-top10" "sdg10-shared-prosperity-premium"}
         (known (lex "metricObservation") "indicator"))
      "G4: metricObservation.indicator vocabulary drifted from ADR-2607101800 §6"))

(deftest test-g4-no-merit-or-verdict-field-anywhere
  (doseq [lname all-lexicon-names]
    (let [ks (set (map (comp str/lower-case name) (property-keys (lex lname))))]
      (doseq [bad ["verdict" "score" "ranking" "rank" "rating" "blame" "merit"]]
        (is (not (contains? ks bad))
            (str "G4: " lname " must not carry a '" bad "' field (narashi is non-adjudicating)"))))))

;; ── G5 — source-provenance mandatory (>=2 CIDs unless singleSourced explicit) ──

(deftest test-g5-source-provenance-mandatory
  (let [req (required-union (lex "metricObservation"))]
    (is (contains? req "sourceRecordCids") "G5: metricObservation must require sourceRecordCids")
    (is (contains? req "methodNoteCid") "G5: metricObservation must require methodNoteCid"))
  (is (= false (a-default (lex "metricObservation") "singleSourced"))
      "G5: singleSourced must default false (silent single-sourcing is prohibited)"))

;; ── G6 — open method, cross-referenced against the manifest's own cell roster
;;    (drift guard: methodNote.appliesToCell must name exactly narashi's 3 cells) ──

(deftest test-g6-open-method-required-fields
  (let [req (required-union (lex "methodNote"))]
    (doseq [field ["version" "appliesToCell" "description" "attestingDid"]]
      (is (contains? req field) (str "G6: methodNote must require " field)))))

(deftest test-g6-method-note-cell-vocabulary-matches-manifest-cells
  (let [cell-tokens (set (map #(last (str/split (get % "module") #"\.")) (get (manifest) "cells")))]
    (is (= cell-tokens (known (lex "methodNote") "appliesToCell"))
        "G6: methodNote.appliesToCell must name exactly the manifest's own cell modules")))

;; ── G7 — Murakumo-only inference ──

(deftest test-g7-murakumo-only-narration
  (is (contains? (required-union (lex "metricNarrative")) "murakumoInferenceAttestation")
      "G7: metricNarrative must require murakumoInferenceAttestation")
  (is (str/includes? (get-in (manifest) ["constitutionalDiscipline" "murakumoOnlyInference"]) "Murakumo")
      "G7: manifest constitutionalDiscipline.murakumoOnlyInference must name Murakumo"))

;; ── G8 — non-causal cross-reference (narashi-specific defining gate) ──

(deftest test-g8-non-causal-cross-reference
  (let [doc (lex "crossReferenceNote")]
    (is (= false (a-const doc "causalClaim"))
        "G8: crossReferenceNote.causalClaim must be a structural const false")
    (let [req (required-union doc)]
      (doseq [field ["causalClaim" "metricObservationCids" "kanaeFundFlowEdgeCids" "jurisdiction"]]
        (is (contains? req field) (str "G8: crossReferenceNote must require " field))))))

;; ── G9 — aggregate-only (no individual/household-level fields anywhere) ──

(deftest test-g9-aggregate-only-no-individual-level-fields
  (doseq [lname all-lexicon-names]
    (let [ks (set (map (comp str/lower-case name) (property-keys (lex lname))))]
      (doseq [bad ["personid" "householdid" "individualid" "citizenid" "ssn" "taxid" "beneficiaryid"]]
        (is (not (contains? ks bad))
            (str "G9: " lname " must not carry a '" bad "' field (narashi is aggregate-only)"))))))

;; ── G2 — kotoba-native persistence (no competing store surfaces in schema) ──

(deftest test-g2-no-competing-store-fields
  (doseq [lname all-lexicon-names]
    (let [ks (set (map (comp str/lower-case name) (property-keys (lex lname))))]
      (doseq [bad ["postgrestable" "risingwaveview" "lanceindex" "duckdbtable" "sqlitetable"]]
        (is (not (contains? ks bad))
            (str "G2: " lname " must not carry a '" bad "' field (kotoba EAVT is the sole store)"))))))

;; ── cross-lexicon consistency — jurisdiction is bounded (ISO-3 / aggregate code)
;;    identically everywhere it appears, mirroring kanae's bounded-vocabulary test ──

(deftest test-jurisdiction-field-bounded-consistently
  (doseq [lname ["metricObservation" "crossReferenceNote" "metricNarrative"]]
    (is (= 8 (a-max-length (lex lname) "jurisdiction"))
        (str lname ".jurisdiction maxLength must stay 8 (ISO-3 / aggregate code ceiling)"))))
