(ns nyusatsu.methods.normalize
  "normalize.cljc — 入札 (nyusatsu) procurement-bid normalizer + charter gates. ADR-2606271700.

  The heart of the worldwide mirror: maps a jurisdiction's PUBLIC procurement release
  (preferably an OCDS release) into a validated, jurisdiction-neutral
  `com.etzhayyim.apps.govFiscal.procurementBid` datom (string-keyed, \":bid/…\"), keyed
  on the global OCDS `ocid` for cross-jurisdiction dedup.

  Every record passes the same G1..G10 gates whether it came from the seed or live ingest:
    G1  mirror-not-author   — issuer-did is a gov DID, NEVER etzhayyim's own DID
    G2  non-adjudicating    — no winner-prediction / bidder-score / corruption-verdict key
    G3  primary-source-only — ≥1 source (≥2 if awarded); paid-aggregator domains refused
    G4  event-log / as-of   — status ∈ the closed OCDS status set
    G5  PII                  — no `:bid/*personal*` / `:pii/*` bidder-PII key
    G6  well-formed          — method/category ∈ enum, ISO-4217 currency, value ≥ 0
    G10 sourcing-honesty     — sourcing ∈ {:representative :authoritative}

  Pure (the gates are throwing assertions); no I/O. House style: ':…' keyword strings stay
  literal strings (root CLAUDE.md convention)."
  (:require [clojure.string :as str]))

;; ── closed enums (OCDS-aligned) ──────────────────────────────────────────────
(def methods    #{":open" ":selective" ":limited" ":direct"})
(def statuses   #{":planning" ":active" ":complete" ":cancelled" ":unsuccessful"})
(def categories #{":goods" ":works" ":services"})
(def sourcings  #{":representative" ":authoritative"})

;; G3 — commercial aggregators are a prohibited citation (Rider §2(e)); a bid must cite the
;; ISSUER's own primary publication, never a paid re-distributor.
(def paid-aggregator-domains
  #{"njss.info" "njss" "kankoujuyou" "kanpou-watcher" "g-search" "nikkei-tender"
    "worldcheck" "refinitiv" "dowjones" "bvdinfo" "spendnetwork.com"})

;; G2 — keys that would turn a mirror into an adjudicator are UNREPRESENTABLE.
(def forbidden-keys
  #{":bid/winner-prediction" ":bid/predicted-winner" ":bid/bidder-score"
    ":bid/bidder-ranking" ":bid/corruption-verdict" ":bid/collusion-verdict"
    ":bid/risk-score"})

;; G5 — bidder PII is Tier-3, off-graph; it must not appear on a bid datom.
(defn- pii-key? [k]
  (let [s (str k)]
    (or (str/starts-with? s ":pii/")
        (str/includes? s "personal")
        (str/includes? s "individual-address"))))

(defn- iso4217? [c] (boolean (and (string? c) (re-matches #"[A-Z]{3}" c))))

(defn- num? [x] (and (number? x) (not (Double/isNaN (double x)))))

(defn- host-of [url]
  (some-> url (str/replace #"^https?://" "") (str/split #"/") first str/lower-case))

(defn- paid-aggregator? [url]
  (let [h (host-of url)]
    (boolean (and h (some #(str/includes? h %) paid-aggregator-domains)))))

(defn validate-bid
  "VALIDATE a `:bid/*` datom against G1..G10. Returns the bid on success; raises ex-info on a gate."
  [b]
  ;; G2 — adjudicating keys are unrepresentable
  (when-let [bad (some forbidden-keys (keys b))]
    (throw (ex-info (str "G2: non-adjudicating — forbidden key " bad) {:bid b})))
  ;; G5 — no bidder PII
  (when-let [pk (some #(when (pii-key? %) %) (keys b))]
    (throw (ex-info (str "G5: PII Tier-3 may not appear on a bid datom: " pk) {:bid b})))
  (let [ocid     (get b ":bid/ocid")
        issuer   (get b ":bid/issuer-did")
        method   (get b ":bid/method")
        status   (get b ":bid/status")
        category (get b ":bid/category")
        currency (get b ":bid/value-currency")
        amount   (get b ":bid/value-amount")
        sources  (get b ":bid/sources")
        sourcing (get b ":bid/sourcing")
        awarded? (some? (get b ":bid/awarded-supplier"))]
    ;; ocid = the global dedup key, mandatory
    (when (str/blank? (str ocid))
      (throw (ex-info "ocid is the global dedup key and is mandatory" {:bid b})))
    ;; G1 — issuer is a gov DID, never etzhayyim's own
    (when (str/blank? (str issuer))
      (throw (ex-info "G1: a bid must be attributed to an issuer gov DID" {:bid b})))
    (when (str/starts-with? (str issuer) "did:web:etzhayyim.com")
      (throw (ex-info "G1: mirror-not-author — etzhayyim is never the issuer of a tender" {:bid b})))
    ;; G4 / G6 — closed enums
    (when-not (statuses status)
      (throw (ex-info (str "G4: status not in the OCDS status set: " status) {:bid b})))
    (when-not (methods method)
      (throw (ex-info (str "G6: procurement method not in enum: " method) {:bid b})))
    (when-not (categories category)
      (throw (ex-info (str "G6: category not in enum: " category) {:bid b})))
    ;; G6 — ISO-4217 currency + non-negative value
    (when-not (iso4217? currency)
      (throw (ex-info (str "G6: value-currency is not ISO-4217: " currency) {:bid b})))
    (when-not (and (num? amount) (>= (double amount) 0.0))
      (throw (ex-info (str "G6: value-amount must be a non-negative number: " amount) {:bid b})))
    ;; G3 — primary-source-only (≥1; ≥2 if awarded); no paid aggregator
    (when-not (and (sequential? sources) (>= (count sources) (if awarded? 2 1)))
      (throw (ex-info (str "G3: need ≥" (if awarded? 2 1) " primary-source citation(s)") {:bid b})))
    (when-let [pa (some #(when (paid-aggregator? %) %) sources)]
      (throw (ex-info (str "G3: paid aggregator is a prohibited citation: " pa) {:bid b})))
    ;; G10 — sourcing honesty
    (when-not (sourcings sourcing)
      (throw (ex-info (str "G10: sourcing must be :representative or :authoritative: " sourcing) {:bid b})))
    b))

;; ── OCDS release → :bid datom ────────────────────────────────────────────────
(defn- colonize [v]
  (when (some? v)
    (let [s (str v)] (if (str/starts-with? s ":") s (str ":" s)))))

(def ^:private ocds-method->kw
  {"open" ":open" "selective" ":selective" "limited" ":limited" "direct" ":direct"
   "openTender" ":open"})

(def ^:private ocds-category->kw
  {"goods" ":goods" "works" ":works" "services" ":services"})

(def ^:private ocds-status->kw
  {"planning" ":planning" "planned" ":planning"
   "active" ":active" "tender" ":active"
   "complete" ":complete" "awarded" ":complete"
   "cancelled" ":cancelled" "canceled" ":cancelled"
   "unsuccessful" ":unsuccessful"})

(defn release->bid
  "Map ONE OCDS release map (string-keyed, as parsed from an OCDS release package) plus a
  per-source `ctx` {:jurisdiction :issuer-did :source-url :source-lang :sourcing} into a
  validated `:bid/*` datom. The ctx carries what a release alone cannot (which jurisdiction
  published it, the source URL we fetched, the issuer DID, and sourcing honesty)."
  [release ctx]
  (let [tender   (get release "tender" {})
        buyer    (get release "buyer" {})
        period   (get tender "tenderPeriod" {})
        value    (get tender "value" {})
        awards   (get release "awards" [])
        award    (first awards)
        a-value  (get award "value" {})
        a-suppl  (first (get award "suppliers" []))
        sources  (vec (remove str/blank?
                              (distinct (conj (get release "sources" [])
                                              (:source-url ctx)
                                              (get tender "url")))))
        bid (cond->
              {":bid/ocid"           (get release "ocid")
               ":bid/jurisdiction"   (:jurisdiction ctx)
               ":bid/issuer-did"     (:issuer-did ctx)
               ":bid/issuer-name"    (get buyer "name")
               ":bid/tender-id"      (str (get tender "id"))
               ":bid/title"          (get tender "title")
               ":bid/method"         (or (ocds-method->kw (get tender "procurementMethod")) ":open")
               ":bid/status"         (or (ocds-status->kw (get tender "status")) ":active")
               ":bid/category"       (or (ocds-category->kw (get tender "mainProcurementCategory")) ":services")
               ":bid/value-amount"   (or (get value "amount") 0)
               ":bid/value-currency" (get value "currency")
               ":bid/tender-start"   (get period "startDate")
               ":bid/tender-end"     (get period "endDate")
               ":bid/source-url"     (:source-url ctx)
               ":bid/source-lang"    (or (:source-lang ctx) (get release "language") "en")
               ":bid/sources"        sources
               ":bid/sourcing"       (or (:sourcing ctx) ":representative")}
              a-suppl (assoc ":bid/awarded-supplier" (get a-suppl "name")
                             ":bid/awarded-amount"   (get a-value "amount")
                             ":bid/awarded-currency" (get a-value "currency")
                             ":bid/awarded-at"       (get award "date")))]
    (validate-bid bid)))

;; ── dedup by ocid (the cross-jurisdiction MERGE) ─────────────────────────────
(defn dedupe-bids
  "MERGE a seq of bids by `:bid/ocid` — later releases for the same contracting process win,
  but award fields once present are retained (an award release supersedes a tender release)."
  [bids]
  (->> bids
       (reduce (fn [acc b]
                 (let [k (get b ":bid/ocid")
                       prev (get acc k)]
                   (assoc acc k
                          (if prev
                            ;; later release wins per-field, but sources UNION across releases
                            ;; (tender-notice + award-notice corroborate, G3 ≥2-for-award)
                            (assoc (merge prev b)
                                   ":bid/sources"
                                   (vec (distinct (concat (get prev ":bid/sources")
                                                          (get b ":bid/sources")))))
                            b))))
               {})
       vals
       (sort-by #(get % ":bid/ocid"))
       vec))
