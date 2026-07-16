(ns talent.methods.agent
  "talent — kotoba-native cohort-first talent registry. 1:1 port of py/agent.py. Self-sovereign,
  hard-deletable profiles + k-anonymous cohort stats. Structural PII-Tier-3 invariants: self-
  sovereign write (G1 — caller DID == subject DID), Signal-E2E PII (G3 — plaintext identifying
  field refused), k-anonymity cohort stats (G2 — below-k suppressed), GDPR Art 17 hard delete (G4 —
  no soft-delete flag). The optional `from kotoba import datalog, llm` host binding is unused and is
  the omitted leg."
  (:import [java.security MessageDigest]))

;; Identifying fields that MUST be Signal-E2E ciphertext (G3).
(def IDENTIFYING-FIELDS ["fullName" "email" "phone" "address" "dateOfBirth" "governmentId"])
(def ENC-PREFIX "signal:v1:")
(def ALLOWED-ENRICHMENT #{"orcid" "github-public" "public-credential-registry"})
(def PROHIBITED-SOURCES #{"linkedin" "indeed" "glassdoor" "purchased-list" "scraped-db"})
(def K-ANONYMITY 5)   ; a cohort below this many members is suppressed (G2)
(def ^:private ENRICHABLE-FIELDS ["skills" "links" "credentials" "publications"])

(defn is-encrypted
  "True iff a field value is Signal-E2E ciphertext (G3)."
  [value]
  (boolean (and (string? value) (.startsWith ^String value ENC-PREFIX))))

(defn subject-hash
  "Deterministic hash of the subject DID — the self path profile:{hash}."
  [subject-did]
  (let [md (MessageDigest/getInstance "SHA-256")
        bs (.digest md (.getBytes ^String subject-did "UTF-8"))]
    (subs (apply str (map #(format "%02x" (bit-and % 0xff)) bs)) 0 16)))

;; ── register_self (G1 self-sovereign, G3 signal-e2e) ──────────────────────────
(defn register-self
  "Register a SELF-SOVEREIGN profile. Refuses unless caller == subject (G1), any plaintext
  identifying field (G3), or a prohibited enrichment source (G1)."
  [caller-did subject-did profile]
  (let [bad-pii (some (fn [f] (let [v (get profile f)]
                                (when (and v (not (is-encrypted v))) f)))
                      IDENTIFYING-FIELDS)
        src (get profile "enrichmentSource")]
    (cond
      (not= caller-did subject-did)
      {"state" "refused" "reason" "third-party registration forbidden — caller must be the subject (G1)"}
      bad-pii
      {"state" "refused" "reason" (str "identifying field " (pr-str bad-pii) " must be signal:v1: ciphertext (G3)")}
      (and src (not (ALLOWED-ENRICHMENT src)))
      {"state" "refused" "reason" (str "enrichment source " (pr-str src) " not in public-consent allowlist (G1)")}
      :else
      {"state" "registered"
       "profile" (merge profile {"subjectDidHash" (subject-hash subject-did) "registeredBy" caller-did})})))

(defn ingest-external
  "Any attempt to ingest from a commercial/scraped candidate source is refused (G1)."
  [source]
  (cond
    (PROHIBITED-SOURCES source)
    {"state" "refused" "reason" (str "prohibited candidate source " (pr-str source) " (G1) — license notwithstanding")}
    (not (ALLOWED-ENRICHMENT source))
    {"state" "refused" "reason" (str "source " (pr-str source) " not in public-consent allowlist (G1)")}
    :else
    {"state" "allowed" "source" source "note" "enrichment only attaches to a self-registered profile"}))

;; ── cohort_stats (G2 k-anonymity) ────────────────────────────────────────────
(defn cohort-stats
  "Aggregate a cohort (ISCO × country). Below k members → SUPPRESSED (G2). Otherwise size + top
  non-identifying skills. No individual-profile field in the output."
  ([isco country profiles] (cohort-stats isco country profiles K-ANONYMITY))
  ([isco country profiles k]
   (let [members (filter #(and (= (get % "isco") isco) (= (get % "country") country)) profiles)
         n (count members)]
     (if (< n k)
       {"suppressed" true "reason" (str "cohort below k=" k " (G2 k-anonymity)") "count" nil}
       (let [skill-counts (reduce (fn [m p] (reduce (fn [m s] (update m s (fnil inc 0))) m (get p "skills" [])))
                                  {} members)
             top (->> skill-counts (sort-by (fn [[s c]] [(- c) s])) (take 10) (mapv first))]
         {"suppressed" false "count" n "topSkills" top
          "cohortDid" (str "did:web:talent.etzhayyim.com:cohort:" isco ":" country)})))))

;; ── forget_self (G4 GDPR Art 17 hard delete) ─────────────────────────────────
(defn forget-self
  "GDPR Art 17 cascade HARD delete (G4). Refuses unless caller == subject. Removes the profile
  Datom entirely (no soft-delete flag). Returns the new store + count removed."
  [caller-did subject-did store]
  (if (not= caller-did subject-did)
    {"state" "refused" "reason" "only the subject may forget their own profile (G1/G4)"}
    (let [h (subject-hash subject-did)
          kept (filterv #(not= (get % "subjectDidHash") h) store)]
      {"state" "forgotten" "store" kept "hardDeleted" (- (count store) (count kept))})))

;; ── attach_enrichment (G1 self-sovereign, G3 signal-e2e) ─────────────────────
(defn attach-enrichment
  "Attach public-consent enrichment to the subject's OWN profile. Refuses unless caller == subject
  (G1) and the source is allowed (G1); only non-identifying fields enrich, any identifying field
  must still be Signal-E2E ciphertext (G3). Provenance recorded per source."
  [caller-did subject-did profile source fields]
  (let [bad-pii (some (fn [f] (when (and (contains? fields f) (not (is-encrypted (get fields f)))) f))
                      IDENTIFYING-FIELDS)]
    (cond
      (not= caller-did subject-did)
      {"state" "refused" "reason" "enrichment attaches to the subject's OWN profile only (G1)"}
      (not (ALLOWED-ENRICHMENT source))
      {"state" "refused" "reason" (str "enrichment source " (pr-str source) " not in public-consent allowlist (G1)")}
      bad-pii
      {"state" "refused" "reason" (str "enrichment cannot add plaintext identifying field " (pr-str bad-pii) " (G3)")}
      :else
      (let [merged (reduce (fn [m f]
                             (if (contains? fields f)
                               (let [existing (vec (get m f []))]
                                 (assoc m f (into existing (remove (set existing) (get fields f)))))
                               m))
                           profile ENRICHABLE-FIELDS)
            prov (conj (vec (get merged "enrichmentProvenance" [])) source)]
        {"state" "enriched" "profile" (assoc merged "enrichmentProvenance" prov)}))))

;; ── listOccupations (G2) ─────────────────────────────────────────────────────
(defn list-occupations
  "Public listing of ISCO×country cohorts. A cohort below k is NOT listed at all (its existence is
  not disclosed, G2). Returns sorted {isco, country, count} for cohorts meeting k."
  ([profiles] (list-occupations profiles K-ANONYMITY))
  ([profiles k]
   (let [counts (reduce (fn [m p] (update m [(get p "isco") (get p "country")] (fnil inc 0))) {} profiles)]
     (->> counts
          (filter (fn [[_ n]] (>= n k)))
          (map (fn [[[isco country] n]] {"isco" isco "country" country "count" n}))
          (sort-by (fn [c] [(get c "isco") (get c "country")]))
          vec))))
