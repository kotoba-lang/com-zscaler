(ns meisai.methods.sources
  "sources.cljc — meisai 明細 worldwide card-company / payment-service COVERAGE registry.
  clj-native (ADR-2606122400 R1; repo rule: new operational code = clj/bb over the kotoba Datom
  log). Reads the PUBLIC coverage map `sources/world-card-issuers.edn` (global card networks /
  issuers / PSPs / BNPL / wallets) and makes meisai able to ingest ANY issuer's statement — not
  just sumitclub.

  Four jobs:
    1. registry-datoms — every source → :meisai.source/* append-only EAVT datoms (the registry
       'data itself in datomic/edn'); these are PUBLIC metadata (no statement, no row, no
       credential, no PAN), so unlike the statement log they MAY be committed/published.
    2. coverage       — an HONEST coverage report + an ingest worklist (which registered sources
       have no fetch-leg adapter yet). The world has tens of thousands of issuers; the report
       never pretends otherwise.
    3. resolve        — a :source keyword → its registry entry (+ adapter :shape).
    4. normalize      — a RAW issuer statement (any field names, any currency) → the canonical
       meisai intake shape, so ingest/statement-datoms can land it unchanged. JPY sources keep the
       legacy :amount_jpy attribute (consistent with sumitclub); other currencies land as the
       generic :amount + :currency (minor units, integer — no floats in the Datom log).

  G2/G3 hold here for free: this namespace touches PUBLIC company metadata only; it parses no
  statement and persists nothing under data/. normalize feeds ingest, where the G2 credential/PAN
  guard still runs on every intake."
  (:require [clojure.string :as str]
            [meisai.methods.kotoba :as kotoba]
            #?(:clj [clojure.edn :as edn])
            #?(:clj [clojure.java.io :as io])))

(defn- kw->str
  "Real keyword (registry uses clojure.edn → real keywords) → meisai datom convention ':name'
  string. Idempotent on strings already shaped ':…'."
  [k]
  (cond
    (keyword? k) (str k)
    (and (string? k) (str/starts-with? k ":")) k
    :else (str ":" k)))

(defn- norm-id
  "Accept :rakuten-card / \"rakuten-card\" / \":rakuten-card\" → the bare name 'rakuten-card'."
  [id]
  (-> (str (if (keyword? id) (name id) id)) (str/replace #"^:+" "")))

;; Currencies with a non-2 minor-unit exponent (for :major→minor scaling). Default = 2.
(def ^:private currency-exponent
  {":jpy" 0 ":krw" 0 ":vnd" 0 ":clp" 0 ":isk" 0
   ":bhd" 3 ":kwd" 3 ":omr" 3 ":tnd" 3})

(defn- exp-of [cur] (get currency-exponent cur 2))

(defn- to-minor
  "Scale a raw amount to integer minor units. :minor = already minor units; :major = decimal major
  units (e.g. USD 39.80 → 3980 cents) using the currency exponent. Always returns a long."
  [v scale cur]
  (cond
    (= scale :major) (long (Math/round (* (double v) (Math/pow 10 (exp-of cur)))))
    :else (long v)))

;; ── registry → datoms ────────────────────────────────────────────────────────
(defn source-datoms
  "One registry entry → append-only :meisai.source/* EAVT datoms. Entity id
  'meisai-source:<id>'. Multi-valued :network → one datom per accepted network. Deterministic."
  [entry]
  (let [id (norm-id (:id entry))
        e (str "meisai-source:" id)
        base [(kotoba/add e ":meisai.source/id" (str ":" id))
              (kotoba/add e ":meisai.source/name" (str (:name entry)))
              (kotoba/add e ":meisai.source/kind" (kw->str (:kind entry)))
              (kotoba/add e ":meisai.source/country" (kw->str (:country entry)))
              (kotoba/add e ":meisai.source/region" (kw->str (:region entry)))
              (kotoba/add e ":meisai.source/currency" (kw->str (:currency entry)))
              (kotoba/add e ":meisai.source/status" (kw->str (:status entry)))]
        base (cond-> base
               (:portal-url entry) (conj (kotoba/add e ":meisai.source/portal" (str (:portal-url entry))))
               (:shape entry)      (conj (kotoba/add e ":meisai.source/has-adapter" true)))]
    (into base
          (map (fn [nw] (kotoba/add e ":meisai.source/network" (kw->str nw)))
               (or (:network entry) (:networks entry) [])))))

(defn registry-datoms
  "Whole registry → datoms, sources sorted by id (deterministic, no map/set iteration order)."
  [reg]
  (mapcat source-datoms (sort-by #(norm-id (:id %)) (:sources reg))))

;; ── coverage report + ingest worklist ────────────────────────────────────────
(defn- freq-by [f xs]
  (->> xs (map f) (reduce (fn [m k] (update m k (fnil inc 0))) {})
       (into (sorted-map-by (fn [a b] (compare (str a) (str b)))))))

(defn coverage
  "Honest coverage report over the registry.
   :total, :supported (fetch-leg adapter exists), :registry-only,
   :by-kind / :by-region (counts), :networks (distinct network count),
   :worklist (sorted ids of registry-only sources = the fetch-leg adapter backlog)."
  [reg]
  (let [ss (:sources reg)
        supported (filter #(= :supported (:status %)) ss)
        backlog   (filter #(not= :supported (:status %)) ss)]
    {:total (count ss)
     :supported (count supported)
     :registry-only (count backlog)
     :by-kind (freq-by :kind ss)
     :by-region (freq-by :region ss)
     :networks (count (filter #(= :network (:kind %)) ss))
     :worklist (vec (sort (map #(norm-id (:id %)) backlog)))}))

(defn coverage-line
  "One human line for an autorun/CLI heartbeat."
  [reg]
  (let [c (coverage reg)]
    (str (:total c) " sources (" (:supported c) " fetch-supported, "
         (:registry-only c) " registry-only / worklist); "
         (:networks c) " networks; kinds " (pr-str (:by-kind c)))))

;; ── resolve + normalize ──────────────────────────────────────────────────────
(defn resolve-source
  "Registry + a :source id (keyword / string / ':id') → its entry, or nil."
  [reg id]
  (let [want (norm-id id)]
    (first (filter #(= want (norm-id (:id %))) (:sources reg)))))

(defn normalize
  "RAW issuer statement (parse-edn shape: STRING keys; per-issuer field names) + its registry entry
  → the canonical meisai intake shape ingest/statement-datoms accepts. JPY → legacy :amount_jpy;
  other currencies → generic :amount + :currency (integer minor units). The fetch leg only needs to
  declare each issuer's field names once, in the registry :shape."
  [entry raw]
  (let [shape (:shape entry)
        rows-key  (or (:rows shape) ":statement/rows")
        month-key (or (:month shape) ":statement/month")
        date-key  (or (:date shape) ":date")
        merch-key (or (:merchant shape) ":merchant")
        amt-key   (or (:amount shape) ":amount")
        scale     (or (:amount-scale shape) :minor)
        cur       (kw->str (:currency entry))
        jpy?      (= cur ":jpy")
        raw-rows  (or (get raw rows-key) [])
        rows (mapv (fn [r]
                     (let [amt (to-minor (get r amt-key 0) scale cur)
                           base {":date" (str (get r date-key "?"))
                                 ":merchant" (str (get r merch-key "?"))}]
                       (if jpy?
                         (assoc base ":amount_jpy" amt)
                         (assoc base ":amount" amt ":currency" cur))))
                   raw-rows)
        doc (cond-> {":source" (str ":" (norm-id (:id entry)))
                     ":statement/month" (str (get raw month-key "?"))
                     ":statement/rows" rows}
              (not jpy?) (assoc ":statement/currency" cur)
              (:portal-url entry) (assoc ":source/url" (str (:portal-url entry))))]
    doc))

;; ── IO (clj only) ────────────────────────────────────────────────────────────
#?(:clj
   (do
     (def ^:private sources-dir
       ;; resolved at LOAD time (where *file* is the real path); reused at run time so -main
       ;; never dereferences *file* (which is nil when invoked via apply under bb).
       (-> (io/file *file*) .getParentFile .getParentFile (io/file "sources")))

     (def registry-default (str (io/file sources-dir "world-card-issuers.edn")))
     (def kotoba-log-default (str (io/file sources-dir "world-card-issuers.kotoba.edn")))

     (defn- unblob
       "A tx-data attribute value that is a pr-str'd non-scalar (nested map / vector-of-maps)
       comes back as a string; parse it back to the live collection. Scalars pass through
       unchanged. (edn-datomize.cljs wrap-map! blob convention.)"
       [v]
       (if (string? v)
         (try (let [parsed (edn/read-string v)] (if (coll? parsed) parsed v))
              (catch Exception _ v))
         v))

     (defn- reconstitute-registry
       "world-card-issuers.edn is wrapped as Datomic/Datascript tx-data
       (`[{:db/id -1 :world-card-issuers/meta \"...\" :world-card-issuers/sources \"...\"}]`,
       manifest/edn-datomize.cljs wrap-map!) so it stays queryable as a Datom log. Every
       in-repo reader of this registry (registry-datoms/coverage/resolve-source/normalize)
       expects the ORIGINAL bare map `{:meta {...} :sources [{...} ...]}`, so rebuild that
       shape here — strip the :world-card-issuers/ namespace back to bare keys and unblob
       the pr-str'd :meta / :sources values back into live EDN. No downstream caller changes."
       [tx-data]
       (into {} (map (fn [[k v]] [(keyword (name k)) (unblob v)]))
             (dissoc (first tx-data) :db/id)))

     (defn- already-tx-data? [content]
       (and (vector? content) (seq content) (map? (first content)) (contains? (first content) :db/id)))

     (defn load-registry
       ([] (load-registry registry-default))
       ([path]
        (let [content (edn/read-string (slurp (io/file path)))]
          (if (already-tx-data? content)
            (reconstitute-registry content)
            content))))

     (defn emit-log!
       "Persist the registry datoms as a ONE-tx public kotoba Datom log (committable — public
       metadata, not a statement). Deterministic tx-id/as-of, so re-emit is byte-identical."
       [reg out-path]
       (let [datoms (vec (registry-datoms reg))
             tx (kotoba/make-tx datoms 1 20260621 "")
             f (io/file out-path)]
         (when-let [p (.getParentFile f)] (.mkdirs p))
         (spit f (str ";; world-card-issuers.kotoba.edn — meisai 明細 coverage registry as a kotoba\n"
                      ";; Datom log (ONE append-only tx). PUBLIC company metadata only — no statement,\n"
                      ";; row, credential, or PAN. GENERATED from sources/world-card-issuers.edn by\n"
                      ";; methods/sources.cljc emit-log!; DO NOT hand-edit. ADR-2606122400.\n"
                      (kotoba/tx->edn tx) "\n"))
         {:datoms (count datoms) :cid (get tx ":tx/cid") :out out-path}))

     (defn -main [& argv]
       (let [argv (vec argv)
             opt (fn [f d] (let [i (.indexOf argv f)]
                             (if (and (>= i 0) (< (inc i) (count argv))) (nth argv (inc i)) d)))
             flag? (fn [f] (>= (.indexOf argv f) 0))
             reg (load-registry (opt "--registry" registry-default))]
         (cond
           (flag? "--emit")
           (let [out (opt "--emit" kotoba-log-default)
                 r (emit-log! reg out)]
             (println (str "emitted " (:datoms r) " datoms → " (:out r) " (head " (subs (:cid r) 0 16) ")")))
           :else
           (let [c (coverage reg)]
             (println (coverage-line reg))
             (println (str "worklist (" (count (:worklist c)) " sources need a fetch adapter): "
                           (str/join " " (:worklist c))))))))))
