(ns keizu.methods.ingest
  "ingest.cljc — 系図 (keizu) offline public-source normalizer. ADR-2606066000.
  1:1 Clojure port of `methods/ingest.py`.

  Normalizes batches of public-source records (官報 / 政治資金収支報告書 / 調達ポータル /
  Federal Register / USAspending / TED / OECD rosters) into keizu :node/:rel/:money/:committee
  datoms. OFFLINE by default and REFUSES live without the G8 gate (operator attestation +
  KEIZU_ALLOW_LIVE=1) — the yadori/watari pattern.

  Every normalized record is run through the same weave.validate-* gates, so an under-sourced or
  verdict-bearing input is refused here, not silently ingested.

  House style: Python ':…' keyword strings stay strings; the weave siblings supply validation;
  string-keyed raw maps; pure fns; host env (KEIZU_ALLOW_LIVE) at the #?(:clj) edge. Omits the
  Python __main__ demo.

  Stdlib only."
  (:require [clojure.string :as str]
            [keizu.methods.registry :as registry]
            [keizu.methods.weave :as w]))

;; raw node fields that map to canonical :node/* attrs; anything else is carried through as
;; :node/<field> so the validate-node PII / power-score scan (G1/G4/G9) bites on the ingest path.
(def ^:private KNOWN-NODE-FIELDS
  #{"id" "scope" "label" "jurisdiction" "organ" "sources" "sourcing" "sourceId"})

(defn- lstrip-colon
  "Python str.lstrip(':') — strip every leading ':' character."
  [s]
  (str/replace (str s) #"^:+" ""))

(defn- non-blank-sources
  "Keep only sources whose str().strip() is non-empty (mirror of the Python comprehension)."
  [sources]
  (vec (filter #(seq (str/trim (str %))) (or sources []))))

(defn- sourcing
  "G11 — if the record names a registry sourceId, the REGISTRY'S verification status WINS
  (a caller cannot forge :authoritative for an unverified source). Else honor the caller's
  declared sourcing, defaulting to :representative."
  [raw]
  (if (get raw "sourceId")
    (registry/sourcing-for (get raw "sourceId"))
    (str ":" (lstrip-colon (str (get raw "sourcing" "representative"))))))

(defn normalize-node
  "Normalize a public-seat record → validated :node/* datom (raises on G1/G4/G9). Extra raw
  fields are carried through so a smuggled PII / power-score field is caught, not silently dropped."
  [raw]
  (let [base {":node/id" (get raw "id")
              ":node/scope" (str ":" (lstrip-colon (str (get raw "scope" ""))))
              ":node/sourcing" (sourcing raw)}
        base (reduce (fn [n k]
                       (if (get raw k) (assoc n (str ":node/" k) (get raw k)) n))
                     base ["label" "jurisdiction" "organ"])
        base (if (get raw "sources")
               (assoc base ":node/sources" (non-blank-sources (get raw "sources")))
               base)
        node (reduce (fn [n [k v]]
                       (if (contains? KNOWN-NODE-FIELDS k)
                         n
                         (assoc n (str ":node/" k) v)))   ;; surfaces PII/power-score keys
                     base raw)]
    (w/validate-node node)
    node))

(defn normalize-committee
  "Normalize a public committee roster record → :committee/* datom (seats as node ids)."
  [raw]
  (let [committee {":committee/id" (get raw "id")
                   ":committee/label" (get raw "label" (get raw "id"))
                   ":committee/jurisdiction" (get raw "jurisdiction" "")
                   ":committee/organ" (get raw "organ" "")
                   ":committee/members" (mapv str (get raw "members" []))
                   ":committee/term-from" (long (get raw "term_from" 0))
                   ":committee/sourcing" (sourcing raw)
                   ":committee/sources" (non-blank-sources (get raw "sources" []))}]
    (w/validate-committee committee)   ;; G1 members + G3 sources/deny + G11 sourcing
    committee))

(defn normalize-rel
  "Normalize a tie record → validated :rel/* datom (raises on a gate)."
  [raw]
  (let [rel {":rel/id" (get raw "id")
             ":rel/source" (get raw "source")
             ":rel/target" (get raw "target")
             ":rel/kind" (str ":" (lstrip-colon (str (get raw "kind"))))
             ":rel/weight" (double (get raw "weight" 1.0))
             ":rel/as-of" (long (get raw "as_of" 0))
             ":rel/non-adjudicating-notice" true
             ":rel/sourcing" (sourcing raw)
             ":rel/sources" (non-blank-sources (get raw "sources" []))}]
    (w/validate-rel rel)
    rel))

(defn normalize-money
  "Normalize a money-flow record → validated :money/* datom (raises on a gate)."
  [raw]
  (let [m {":money/id" (get raw "id")
           ":money/payer" (get raw "payer")
           ":money/payee" (get raw "payee")
           ":money/kind" (str ":" (lstrip-colon (str (get raw "kind"))))
           ":money/amount" (double (get raw "amount" 0.0))
           ":money/currency" (get raw "currency" "")
           ":money/as-of" (long (get raw "as_of" 0))
           ":money/sourcing" (sourcing raw)
           ":money/sources" (non-blank-sources (get raw "sources" []))}]
    (w/validate-money m)
    m))

(defn normalize-batch
  "Normalize a mixed offline batch into keizu datoms. Each record validated."
  [batch]
  {"nodes" (mapv normalize-node (get batch "nodes" []))
   "committees" (mapv normalize-committee (get batch "committees" []))
   "rels" (mapv normalize-rel (get batch "rels" []))
   "money" (mapv normalize-money (get batch "money" []))})

;; The G8 operator gate. Mirrors Python's `os.environ.get("KEIZU_ALLOW_LIVE")`; a dynamic var so
;; tests can exercise the with-gate path without mutating the JVM environment (the default reads env).
(def ^:dynamic *allow-live*
  #?(:clj (System/getenv "KEIZU_ALLOW_LIVE") :default nil))

(defn ingest-live
  "G8 — live ingest from government portals is outward-gated. Refuses unless the operator
  gate is set AND an attestation DID is supplied (which still routes to Council Lv6+)."
  [& _args]
  (when-not (= "1" *allow-live*)
    (throw (ex-info (str "keizu R0: live public-source ingest is Council Lv6+ + operator gated (G8). "
                         "Set KEIZU_ALLOW_LIVE=1 + supply an operator attestation DID to proceed (still Council-gated).")
                    {})))
  (throw (ex-info "keizu R0: live ingest path not wired — design-only (G8)." {})))
