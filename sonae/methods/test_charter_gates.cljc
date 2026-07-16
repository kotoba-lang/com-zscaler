(ns sonae.methods.test-charter-gates
  "sonae — constitutional-gate conformance tests (manifest + lexicons + warning-source
  registry). Substrate-native Clojure, following the chigiri/narashi idiom (ADR-2606160842
  py->clj port wave). This is the native-runtime port of the two R0-era Python invariant
  suites `70-tools/scripts/audit/test_sonae_lexicon_invariants.py` +
  `test_sonae_warning_sources_seed.py` (Python is not on this repo's runtime-priority
  ladder — kotoba wasm > clojurewasm > cljs > nbb > downgraded JVM/bb — per repo-root
  CLAUDE.md; the Python files are left in place for one cycle per MATURITY.md, this file
  is the closing of that runtime debt, not a replacement commit). Gate content below is
  derived directly from sonae's own manifest.jsonld + CLAUDE.md + ADR-2606091200, not
  transliterated blind from the Python — every assertion here is re-verified against the
  on-disk JSON it reads. R0 scaffold slice: test-only, network-free, no cell execution.
  Fixtures are the actor's own JSON schema documents (manifest.jsonld + 00-contracts
  lexicons + registry seed) — no real hazard/personal data is read or asserted."
  (:require [clojure.test :refer [deftest is run-tests]]
            [clojure.set :as set]
            [clojure.string :as str]
            [cheshire.core :as json]))

(def ^:private here (.getParentFile (java.io.File. ^String *file*)))     ;; methods/
(def ^:private actor-dir (.getParentFile here))                         ;; sonae/
(def ^:private actor-name (.getName actor-dir))
(def ^:private root (.. actor-dir getParentFile getParentFile))         ;; 20-actors -> ROOT
(def ^:private lexdir (java.io.File. root (str "00-contracts/lexicons/com/etzhayyim/" actor-name)))

(defn- manifest [] (json/parse-string (slurp (java.io.File. actor-dir "manifest.jsonld"))))
(defn- lex [name] (json/parse-string (slurp (java.io.File. lexdir (str name ".json")))))
(defn- registry [] (json/parse-string (slurp (java.io.File. actor-dir "registry/warning-sources.seed.json"))))

;; ── generic JSON-schema introspection helpers (chigiri/narashi idiom) ──

(defn- collect
  "Walk a parsed JSON doc; for every map that contains `attr`, record
  {immediate-parent-key -> (get map attr)}. Works because every schema field
  here is shaped \"fieldName\": {attr ...} — the parent map key IS the
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
(defn- known [doc field] (some-> (get (collect doc "knownValues") field) set))

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
  ["hazardSignalRecord" "siteRiskProfile" "earlyWarningRelay" "preparednessPlan"
   "drillAttestation" "sonaeReadinessReview"])

;; Open-publication government / inter-gov hazard feeds (G4). sourceFeed
;; knownValues MUST be a subset of this allowlist — no commercial vendor.
(def ^:private open-feed-allowlist
  #{"usgs" "phivolcs" "jma" "ptwc" "gdacs" "copernicus-ems" "wmo-gts" "national-met-service-other"})

;; Commercial disaster-prediction / alerting vendors PROHIBITED per G4
;; (Charter Rider §2(e)+§2(c) via ADR-2606091200). May appear in PROSE
;; (prohibition notes) but MUST NEVER appear as an allowed knownValues value.
(def ^:private prohibited-vendors
  ["one concern" "oneconcern" "floodflash" "jupiter intelligence" "tomorrow.io"
   "everbridge" "onsolve" "alertmedia" "rms"])

;; Field-name fragments that would imply individual-level tracking (G6).
(def ^:private individual-level-forbidden
  ["personname" "fullname" "individualid" "householdid" "homeaddress" "biometric"
   "phonenumber" "deviceid" "facialid" "gpscoordinate" "personlocation"])

;; ── 1. manifest identity ─────────────────────────────────────────────────

(deftest test-manifest-parses-and-is-sonae
  (let [m (manifest)]
    (is (= "ActorManifest" (get m "@type")))
    (is (= "did:web:sonae.etzhayyim.com" (get m "id")))
    (is (= "sonae" (get m "name")))))

;; ── 2. structural counts — drift guard against CLAUDE.md's own declared
;;    "6 cells / 6 Lexicons / 12 gates / 12 non-goals" summary ──────────────

(deftest test-manifest-structural-counts-match-declared-summary
  (let [m (manifest)]
    (is (= 6 (count (get m "cells"))) "manifest must path-reserve exactly 6 cells")
    (is (= #{"hazard_watch" "risk_assessment" "early_warning_relay"
             "preparedness_plan" "drill_attestation" "handoff_trigger"}
           (set (map #(get % "name") (get m "cells"))))
        "cell name set drifted from ADR-2606091200 architecture")
    (doseq [c (get m "cells")]
      (is (= "naphtali" (get c "murakumoNode"))
          (str (get c "name") " must be on naphtali (witness-pair pattern)")))
    (is (= 6 (count (get m "lexiconNamespaces"))) "manifest must declare exactly 6 lexicon namespaces")
    (is (= 12 (count (get-in m ["constitutionalGates" "gates"])))
        "manifest must declare exactly 12 gates")
    (is (= (set (keys (get-in m ["constitutionalGates" "gates"])))
           (set (map #(str "G" %) (range 1 13))))
        "gate id set must be exactly G1..G12")
    (is (= 12 (count (get-in m ["nonGoals" "goals"]))) "manifest must declare exactly 12 non-goals")
    (is (= (set (keys (get-in m ["nonGoals" "goals"])))
           (set (map #(str "N" %) (range 1 13))))
        "non-goal id set must be exactly N1..N12")))

;; ── 3-5. lexicon namespaces resolve to files with matching ids ───────────

(deftest test-six-namespaces-all-under-sonae
  (let [ns-list (get (manifest) "lexiconNamespaces")]
    (is (= 6 (count ns-list)) (str "expected 6 lexiconNamespaces, got " (count ns-list)))
    (doseq [n ns-list]
      (is (str/starts-with? n "com.etzhayyim.sonae.") n))))

(deftest test-every-namespace-resolves-to-file-with-matching-id
  (doseq [nsid (get (manifest) "lexiconNamespaces")]
    (let [short (last (str/split nsid #"\."))]
      (is (some #{short} all-lexicon-names) (str "unresolvable lexiconNamespace: " nsid))
      (is (= nsid (get (lex short) "id")) (str short ".json id must equal its declared namespace " nsid)))))

;; ── 6. earlyWarningRelay: NO false authority (G8 — the defining gate) ────

(deftest test-early-warning-relay-is-relay-only-with-source
  (let [doc (lex "earlyWarningRelay")]
    (is (= true (a-const doc "relayOnly")) "G8: relayOnly must be const true")
    (let [req (required-union doc)]
      (is (contains? req "authoritativeSource") "G8: authoritativeSource must be required")
      (is (contains? req "relayOnly") "G8: relayOnly must be required"))))

;; ── 7. hazardSignalRecord: open-data-only (G6) + open feeds (G4) ─────────

(deftest test-hazard-signal-open-data-only-and-open-feeds
  (let [doc (lex "hazardSignalRecord")]
    (is (= true (a-const doc "openDataOnlyAttested")) "G6: openDataOnlyAttested const true")
    (let [feeds (known doc "sourceFeed")]
      (is (set/subset? feeds open-feed-allowlist)
          (str "G4: sourceFeed has non-open-feed values: " (set/difference feeds open-feed-allowlist))))))

;; ── 8. siteRiskProfile: community-scale (G3) + no individual data (G6) ───

(deftest test-site-risk-profile-community-scale-no-individual-data
  (let [doc (lex "siteRiskProfile")]
    (is (= true (a-const doc "communityScaleAttested")) "G3: communityScaleAttested const true")
    (let [blob (str/lower-case (json/generate-string doc))]
      (doseq [frag individual-level-forbidden]
        (is (not (str/includes? blob frag)) (str "G6: forbidden individual-level field fragment " frag))))))

;; ── 9. drillAttestation: opt-in (G6) ──────────────────────────────────────

(deftest test-drill-attestation-opt-in
  (is (= true (a-const (lex "drillAttestation") "participantsOptIn")) "G6: participantsOptIn const true"))

;; ── 10. G4 NEGATIVE: no commercial vendor as an allowed enum value ───────

(deftest test-no-prohibited-vendor-in-known-values
  (let [offenders (atom [])]
    (doseq [lname all-lexicon-names]
      (let [doc (lex lname)]
        (letfn [(walk [x]
                  (cond (map? x)
                        (do (when (and (contains? x "knownValues") (sequential? (get x "knownValues")))
                              (doseq [v (get x "knownValues")]
                                (when (string? v)
                                  (let [low (str/lower-case v)]
                                    (doseq [vendor prohibited-vendors]
                                      (when (str/includes? low vendor)
                                        (swap! offenders conj (str lname ": " v))))))))
                            (doseq [v (vals x)] (walk v)))
                        (sequential? x) (doseq [v x] (walk v))))]
          (walk doc))))
    (is (empty? @offenders) (str "G4: prohibited vendor as allowed value: " @offenders))))

;; ── 11. manifest declares the defining invariants + N3 ───────────────────

(deftest test-manifest-declares-invariants-and-phase-boundary
  (let [m (manifest)]
    (is (contains? m "falseAuthorityInvariant") "G8 invariant must be declared")
    (is (contains? m "phaseBoundaryInvariant") "N3 phase-boundary invariant must be declared")
    (is (contains? m "civilianOnlyInvariant") "G5+N1 civilian-only invariant must be declared")
    (is (str/includes? (str/lower-case (get-in m ["nonGoals" "goals" "N3"])) "response")
        "N3 must state sonae is NOT response")))

;; ── G10 — no unilateral declaration: structural absence of any field that
;;    would let sonae itself declare/flip an emergency state anywhere in its
;;    schema (declaration is exclusively kazaori's Council Lv6+ >=4/7 path) ──

(deftest test-g10-no-declare-emergency-field-anywhere
  (doseq [lname all-lexicon-names]
    (let [ks (set (map (comp str/lower-case name) (property-keys (lex lname))))]
      (doseq [bad ["declareemergency" "emergencydeclared" "emergencystatetrue" "declaresemergency"]]
        (is (not (contains? ks bad))
            (str "G10: " lname " must not carry a '" bad "' field (only kazaori Council may declare)"))))))

;; ── cross-gate: drillAttestation exposes the kazaori R1 drill-gate evidence
;;    field (CLAUDE.md §"Phase boundary": drill_attestation double-satisfies
;;    kazaori's R1 activation gate) ─────────────────────────────────────────

(deftest test-drill-attestation-exposes-kazaori-r1-gate-field
  (is (contains? (property-keys (lex "drillAttestation")) "satisfiesKazaoriR1DrillGate")
      "drillAttestation must expose satisfiesKazaoriR1DrillGate (kazaori R1 activation cross-gate)"))

;; ── registry: warning-sources.seed.json invariants (G8 relay allowlist + G14 honesty) ──

(deftest test-registry-parses-nonempty
  (let [srcs (get (registry) "sources")]
    (is (sequential? srcs))
    (is (pos? (count srcs)))))

(deftest test-registry-unique-source-ids
  (let [ids (map #(get % "sourceId") (get (registry) "sources"))]
    (is (= (count ids) (count (set ids))) "duplicate sourceId — fail-closed")))

(deftest test-registry-all-unverified-seed
  (doseq [s (get (registry) "sources")]
    (is (= "unverified-seed" (get s "verificationStatus")) (get s "sourceId"))))

(def ^:private http-re #"^https?://.*")
(def ^:private iso-z-re #"^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}Z$")

(deftest test-registry-urls-and-timestamps
  (doseq [s (get (registry) "sources")]
    (is (and (get s "accessUrl") (re-matches http-re (get s "accessUrl"))) (get s "sourceId"))
    (is (and (get s "provenance") (re-matches http-re (get s "provenance"))) (get s "sourceId"))
    (is (re-matches iso-z-re (or (get s "lastVerified") "")) (get s "sourceId"))))

(deftest test-registry-worldwide-jurisdiction-spread
  (let [juris (set (map #(get % "jurisdiction") (get (registry) "sources")))]
    (is (>= (count juris) 12) (str "expected >=12 jurisdictions, got " (count juris) ": " (sort juris)))))

(def ^:private allowed-source-kinds
  #{"national-seismic-tsunami-authority" "national-seismic-network" "national-met-service"
    "regional-tsunami-warning-center" "multi-hazard-alert-aggregator" "national-disaster-management-agency"})

(deftest test-registry-source-kind-taxonomy
  (doseq [s (get (registry) "sources")]
    (is (contains? allowed-source-kinds (get s "sourceKind")) (str (get s "sourceId") ": " (get s "sourceKind")))))

(deftest test-registry-g8-authoritative-and-relay-only-notes
  (doseq [s (get (registry) "sources")]
    (is (= true (get s "isAuthoritativeIssuer")) (str "G8: " (get s "sourceId") " not authoritative"))
    (let [notes (str/lower-case (or (get s "notes") ""))]
      (is (and (str/includes? notes "relay") (str/includes? notes "g8"))
          (str "G8: " (get s "sourceId") " notes must re-assert the relay-only boundary")))))

(deftest test-registry-freshness-window-present
  (is (integer? (get (registry) "freshnessWindowDays"))))

;; Catch-all enum members that need not be realized by a specific registry entry.
(def ^:private catch-all-tokens #{"national-met-service-other" "national-disaster-agency-other"})

(deftest test-registry-covers-lexicon-vocabulary
  (let [relay-enum (known (lex "earlyWarningRelay") "authoritativeSource")
        needed (set/difference relay-enum catch-all-tokens)
        present (set (map #(get % "authoritativeSourceToken") (get (registry) "sources")))
        missing (set/difference needed present)]
    (is (empty? missing) (str "G8: lexicon authoritativeSource tokens not in registry: " missing))))

(deftest test-registry-tokens-within-lexicon-vocabulary
  (let [relay-enum (known (lex "earlyWarningRelay") "authoritativeSource")]
    (doseq [s (get (registry) "sources")]
      (let [tok (get s "authoritativeSourceToken")]
        (is (contains? relay-enum tok)
            (str (get s "sourceId") ": token " tok " outside earlyWarningRelay enum " (sort relay-enum)))))))
