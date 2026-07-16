(ns kabuto.methods.ingest
  "ingest.py — kabuto 兜 public-company ingest bridge (R1 scaffold; offline default). ADR-2606022000.
  1:1 Clojure port of `methods/ingest.py`.

  Normalizes PUBLIC company registries into the kotoba EAVT :company/* vocabulary and dedup-merges
  with the curated seed (seed wins). Full-universe live fetch (GLEIF / EDGAR / exchange) is G7
  Council + operator gated and is a NO-OP unless KABUTO_OPERATOR_GATE=1. Default offline run simply
  re-emits the seed as the merged graph so downstream cells have a stable input.

  House style: pure bridge/emit; the Python ':…' keyword strings stay strings; file I/O only behind
  #?(:clj …). SELF-CONTAINED: requires the actor's existing kabuto-edn sibling. (The Python `__main__`
  CLI is preserved as -main.)"
  (:require [clojure.string :as str]
            [kabuto.methods.kabuto-edn :as kedn]
            #?(:clj [clojure.java.io :as io])))

#?(:clj (def ^:private here (-> *file* io/file .getAbsoluteFile .getParentFile .getParentFile)))

;; Documented full-universe endpoints — NOT fetched unless the operator gate is set.
(def sources
  {"gleif" "https://leidata.gleif.org/api/v1/lei-records (GLEIF Golden Copy)"
   "edgar" "https://www.sec.gov/files/company_tickers.json (SEC EDGAR)"
   "exchange" "per-exchange listing directories (TWSE/TSE/KRX/Euronext/HKEX/LSE/…)"})

(defn bridge-public-json
  "Map a public-dataset-shaped record list → :company/* datom dicts. Unknown fields dropped; sourcing
  forced to :representative (G5). Mirrors bridge_public_json(records)."
  [records]
  (mapv
   (fn [r]
     (let [cid (or (get r "id")
                   (str "org.corp." (str/lower-case (get r "country" "xx")) "."
                        (str/lower-case (or (get r "ticker") (get r "lei") "unknown"))))
           d {":company/id" cid
              ":company/name" (get r "name" cid)
              ":company/status" (str ":" (get r "status" "listed"))
              ":company/sourcing" ":representative"}
           d (if (get r "ticker") (assoc d ":company/ticker" (get r "ticker")) d)
           d (if (get r "exchange") (assoc d ":company/exchange" (str ":" (str/lower-case (str (get r "exchange"))))) d)
           d (if (get r "country") (assoc d ":company/country" (get r "country")) d)
           d (if (get r "sector") (assoc d ":company/sector" (str ":" (str/lower-case (str (get r "sector"))))) d)
           d (if (get r "lei") (assoc d ":company/lei" (get r "lei")) d)]
       d))
   records))

(defn gated-source?
  "True when `source` names a full-universe live registry (gleif/edgar/exchange — see
  `sources`) AND `operator-gate?` is false — i.e. the G7 live fetch is refused. The
  `file` source (local bridge) and any source once the operator gate is open are
  never gated."
  [source operator-gate?]
  (and (contains? sources source) (not operator-gate?)))

(defn merge-bridged
  "Dedup-merge bridged :company/* records against `seed` (a map keyed by company id, e.g.
  kabuto-edn/classify's :companies) — seed wins. Returns the bridged records whose
  :company/id is not already a seed key."
  [seed bridged]
  (let [seed-ids (set (keys seed))]
    (filterv #(not (contains? seed-ids (get % ":company/id"))) bridged)))

(defn emit-company
  "Render one :company/* dict as an EDN map literal (string values quoted, keyword strings raw).
  Mirrors emit_company(d). Leading space matches the Python join contract."
  [d]
  (let [parts (map (fn [[k v]]
                     (if (and (string? v) (not (str/starts-with? v ":")))
                       (str k " " (kedn/edn-str v))
                       (str k " " v)))
                   d)]
    (str " {" (str/join " " parts) "}")))

#?(:clj
   (defn -main
     "CLI entry: [--source gleif|edgar|exchange|file] [--in PATH] [--out PATH]. File I/O at the edge.
     The live full-universe fetch is G7-gated (no-op unless KABUTO_OPERATOR_GATE=1); the `file` source
     bridges a local public-dataset JSON; default re-emits the seed."
     [& argv]
     (let [argv (vec argv)
           operator-gate (= "1" (System/getenv "KABUTO_OPERATOR_GATE"))
           seed (io/file here "data" "seed-public-companies.kotoba.edn")
           outp (let [i (.indexOf argv "--out")]
                  (if (>= i 0) (io/file (nth argv (inc i))) (io/file here "data" "companies.merged.kotoba.edn")))
           source (let [i (.indexOf argv "--source")] (when (>= i 0) (nth argv (inc i))))
           infile (let [i (.indexOf argv "--in")] (when (>= i 0) (nth argv (inc i))))
           bridged
           (cond
             (gated-source? source operator-gate)
             (do (println (str "kabuto.ingest: source '" source "' = " (get sources source)))
                 (println (str "  → G7 GATED: live full-universe fetch requires KABUTO_OPERATOR_GATE=1 "
                               "(Council). Skipping live fetch; emitting seed only (offline default)."))
                 [])
             (and (= source "file") infile)
             (let [recs0 (kedn/read-edn (slurp infile)) ; tolerant: EDN reader handles JSON-ish maps/vecs
                   recs (if (and (map? recs0) (contains? recs0 "companies")) (get recs0 "companies") recs0)
                   b (bridge-public-json recs)]
               (println (str "kabuto.ingest: bridged " (count b) " records from " infile " (:representative)"))
               b)
             source
             (do (println (str "kabuto.ingest: unknown source '" source "'. Known: "
                               (str/join ", " (keys sources)) ", file"))
                 [])
             :else [])
           ;; dedup-merge: seed wins on :company/id collision
           seed-rows (kedn/load-edn seed)
           {:keys [companies]} (kedn/classify seed-rows)
           extra (merge-bridged companies bridged)
           seed-text (str/trimr (slurp seed))]
       (when-let [parent (.getParentFile outp)] (.mkdirs parent))
       (if (seq extra)
         (let [body (str/trimr (subs seed-text 0 (str/last-index-of seed-text "]")))]
           (spit outp (str body "\n"
                           (str/join "\n" (map #(str " " (str/trim (emit-company %))) extra))
                           "\n]\n")))
         (spit outp (str seed-text "\n")))
       (println (str "kabuto.ingest: merged graph → " outp " ("
                     (count (kedn/co-keys companies)) " seed + " (count extra) " bridged companies)")))))
