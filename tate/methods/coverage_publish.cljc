(ns tate.methods.coverage-publish
  "tate 盾 — PUBLIC anonymized coverage digest (mesh-distributable, content-addressed).

  The PUBLIC face of tate. Builds an AGGREGATE-ONLY coverage digest from coverage_report
  (jurisdictions covered, track × jurisdiction matrix, clause-shape counts per jurisdiction,
  one-tier provenance ratios, named worklist gaps) and content-addresses it (CIDv1/raw/sha2-256,
  `tate.methods.cid`) so it is verifiable + distributable on the kotoba mesh.

  G1 (member-principal-own-documents-only) is preserved BY CONSTRUCTION: coverage_report loads
  ONLY the coded registries (clause-patterns / procedure-registry / jurisdictions / us-states),
  never `seed-member-docs`. The digest selects an explicit AGGREGATE allowlist and
  `member-leak?` is a structural test-enforced guard — a member-document marker (`:doc/…`,
  `:notice/…`, member text) can never appear in the published bytes.

  Distinct from the per-member scan/respond legs (which run over the member's OWN private docs,
  consent-bound + encrypted, never published). This namespace publishes registry METADATA only."
  (:require [clojure.string :as str]
            [tate.methods.coverage-report :as cov]
            [tate.methods.cid :as cid]))

;; ── AGGREGATE allowlist — the ONLY keys that may leave the substrate ─────────────
(def ^:private digest-keys
  ["covered_count" "jurisdictions" "coverage_ratio" "un_member_states"
   "us_states_covered" "us_states_total"
   "procedure_tracks" "track_matrix"
   "patterns_by_jurisdiction" "procedures_by_jurisdiction"
   "clause_total" "clause_source_url_count" "proc_total" "proc_source_url_count"
   "worklist_remaining" "named_gaps"])

(defn aggregate-digest
  "The anonymized AGGREGATE coverage digest body (string keys, allowlist only). No member data."
  ([] (aggregate-digest (cov/coverage)))
  ([coverage]
   (let [picked (reduce (fn [m k] (assoc m k (get coverage k))) (array-map) digest-keys)]
     (assoc picked
            "critical_deadline_count" (count (get coverage "critical_deadlines" []))
            "actor" "tate"
            "kind" "coverage-digest"))))

;; ── deterministic canonical form (CID stability) ─────────────────────────────────
(defn- canon
  "Recursively sort maps by key string; keep vector order. Deterministic across runs."
  [x]
  (cond
    (map? x)        (into (sorted-map) (map (fn [[k v]] [k (canon v)]) x))
    (sequential? x) (mapv canon x)
    :else           x))

(defn canonical-edn
  "Deterministic EDN string of the digest body — the bytes the CID is taken over."
  [body]
  (pr-str (canon body)))

(defn content-id
  "CIDv1/raw/sha2-256 of the canonical digest bytes (ipfs-parity, no daemon)."
  [body]
  (cid/cidv1-raw (canonical-edn body)))

;; ── G1 structural guard ──────────────────────────────────────────────────────────
(def ^:private member-markers
  [":doc/" ":notice/" "seed-member" "member-doc" ":doc.text" ":notice.text"])

(defn member-leak?
  "True if any member-document marker appears anywhere in the serialized digest (G1 guard)."
  [body]
  (let [s (canonical-edn body)]
    (boolean (some #(str/includes? s %) member-markers))))

;; ── minimal JSON encoder (array-map order preserved; UTF-8 kept) ─────────────────
(defn- jesc [s]
  (-> (str s) (str/replace "\\" "\\\\") (str/replace "\"" "\\\"")
      (str/replace "\n" "\\n") (str/replace "\t" "\\t") (str/replace "\r" "\\r")))

(defn- name-or-str [k] (if (keyword? k) (subs (str k) 1) (str k)))

(defn- json [x depth]
  (let [pad (apply str (repeat (inc depth) "  "))
        pad0 (apply str (repeat depth "  "))]
    (cond
      (map? x)        (if (empty? x) "{}"
                          (str "{\n"
                               (str/join ",\n"
                                         (map (fn [[k v]] (str pad "\"" (jesc (name-or-str k)) "\": " (json v (inc depth)))) x))
                               "\n" pad0 "}"))
      (sequential? x) (if (empty? x) "[]"
                          (str "[\n"
                               (str/join ",\n" (map (fn [v] (str pad (json v (inc depth)))) x))
                               "\n" pad0 "]"))
      (string? x)     (str "\"" (jesc x) "\"")
      (keyword? x)    (str "\"" (jesc (subs (str x) 1)) "\"")
      (number? x)     (str x)
      (boolean? x)    (str x)
      (nil? x)        "null"
      :else           (str "\"" (jesc (str x)) "\""))))

(defn coverage-json
  "The mesh-distributable artifact: the content-addressed wrapper, as a JSON string.
  { actor, kind, cid, g1, digest, _meta }."
  ([] (coverage-json (aggregate-digest)))
  ([body]
   (let [cidv (content-id body)
         wrapper (array-map
                  "actor" "tate"
                  "kind" "coverage-digest"
                  "cid" cidv
                  "g1" (if (member-leak? body) "LEAK" "member-data-free (aggregate-only)")
                  "_meta" (array-map
                           "source" "tate coverage_report (registries only; never seed-member-docs)"
                           "content_address" "CIDv1/raw/sha2-256 over the canonical EDN of `digest`"
                           "mesh" "distributable; verify by recomputing the CID over the digest body"
                           "adr" ["2606112301" "2606112400" "2606013800"])
                  "digest" body)]
     (json wrapper 0))))
