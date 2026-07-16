;; ingest.clj — 弾正 (danjo) revenue-corpus ingest (passive-only, G3). ADR-2605301600.
;;
;; Projects the pre-published `com.etzhayyim.gov.dataset.*Record` corpus
;; (data/gov-revenue-corpus.jp.edn) → the revenue model that revenue_ledger.clj consumes
;; ({:accounts :revenue-lines :transfers :outlays}). The sibling of budget_ledger.py, but for
;; the REVENUE side and in Clojure.
;;
;; Discipline (inherited from danjo + budget_ledger.py):
;;   G3 — PASSIVE: the corpus IS the input; this never fetches a live portal.
;;   G4 — FACTUAL structure only; no crime/violation field exists or is computed.
;;   G5 — every projected model entry carries ≥2 source CIDs (its own record CID + the
;;        dataset manifest CID), so downstream trace/outlay datoms satisfy danjo G5.
;;   account-EARMARK is accounting LAW (特別会計法 / 復興財源確保法), encoded here as a constant —
;;        NOT a fetched record. That is what keeps the per-yen-traceability decision honest.
;;
;; Pure + JVM stdlib only; runs under bb and clojure.
(ns root.danjo.methods.ingest
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as str])
  (:import [java.security MessageDigest]))

;; The account-boundary framework is LAW, not data (so it is a constant, not ingested).
(def account-law
  [{:id :general                :kind :general :ja "一般会計"
    :earmark? false
    :note "non-earmarked: fungible revenue, per-yen expenditure linkage unrepresentable (ノン・アフェクタシオン原則)"}
   {:id :special/reconstruction :kind :special :ja "東日本大震災復興特別会計"
    :earmark? true
    :note "closed earmarked boundary: 繰入→歳出 traceable to the yen within the account (復興財源確保法)"}])

(defn- sha256-hex [^String s]
  (let [md (MessageDigest/getInstance "SHA-256")]
    (apply str (map #(format "%02x" (bit-and % 0xff)) (.digest md (.getBytes s "UTF-8"))))))

(defn- canonical
  "Deterministic canonical string for a map: entries sorted by string key, stable (same map →
   same CID). Works for keyword-keyed (EDN) and string-keyed (JSON) records alike."
  [m]
  (str "{" (str/join "," (for [[k v] (sort-by (comp str key) m)]
                           (str (pr-str k) " " (pr-str v)))) "}"))

(defn record-cid
  "gov.dataset record CID: locator + sha256[:24] content digest (G5 provenance). Mirrors the
   string shape of budget_ledger.py record_cid, generalized over :record-kind."
  [rec]
  (let [kind   (name (or (:record-kind rec) :unknown))
        sensor (or (:source-sensor rec) "unknown")
        fy     (or (:fiscal-year rec) 0)
        rid    (or (:record-id rec) "unknown")]
    (str "gov.dataset." kind "Record:" sensor ":" fy ":" rid "#"
         (subs (sha256-hex (canonical rec)) 0 24))))

(defn dataset-cid
  "Content CID over the whole corpus manifest (the dataset the records came from) — the 2nd
   source CID every projected entry carries, so G5 (≥2) holds structurally."
  [corpus]
  (str "gov.dataset.manifest:" (or (:source-sensor corpus) "unknown") "#"
       (subs (sha256-hex (canonical (dissoc corpus :records))) 0 24)))

(defn- non-neg-int! [rec]
  (let [a (:amount-local rec)]
    (when-not (and (integer? a) (>= a 0))
      (throw (ex-info (str "amount-local must be a non-negative integer (1円 minor units), got " (pr-str a))
                      {:record (:record-id rec)})))
    a))

(defn ingest-corpus
  "Project a gov.dataset corpus map → the revenue model. Passive (G3). Each model entry's
   :source-record-cids = [its own record CID, the dataset manifest CID] (G5 ≥2)."
  [corpus]
  (let [recs    (:records corpus)
        ds-cid  (dataset-cid corpus)
        cids    (fn [rec] [(record-cid rec) ds-cid])
        by-kind (group-by :record-kind recs)]
    {:source-sensor       (:source-sensor corpus)
     :jurisdiction        (:jurisdiction corpus)
     :currency-iso4217    (:currency-iso4217 corpus)
     :verification-status (:verification-status corpus)
     :primary-sources     (:primary-sources corpus)
     :dataset-cid         ds-cid
     :accounts            account-law
     :revenue-lines
     (vec (for [r (:revenue by-kind)]
            {:record-id (:record-id r) :tax-kind (:tax-kind r) :account (:account r)
             :ja (:program-name r) :fiscal-year (:fiscal-year r)
             :amount-jpy (non-neg-int! r) :source-record-cids (cids r) :tier "A"}))
     :transfers
     (vec (for [r (:transfer by-kind)]
            {:record-id (:record-id r) :tax-kind (:tax-kind r)
             :from (:from r) :to (:to r) :fiscal-year (:fiscal-year r)
             :amount-jpy (non-neg-int! r) :source-record-cids (cids r) :tier "A"}))
     :outlays
     (vec (for [r (:outlay by-kind)]
            {:record-id (:record-id r) :account (:account r)
             :program-code (:program-code r) :program-name (:program-name r)
             :cofog (:cofog r) :recipient-class (:recipient-class r)
             :fiscal-year (:fiscal-year r) :amount-jpy (non-neg-int! r)
             :source-record-cids (cids r) :tier "A"}))}))

(defn- unblob
  "A datomized attribute value may be a pr-str'd blob (nested map/vector-of-map that doesn't
   fit a scalar Datomic valueType) — parse it back to data. Non-blob values pass through."
  [v]
  (if (string? v)
    (try (let [parsed (edn/read-string v)] (if (coll? parsed) parsed v))
         (catch Exception _ v))
    v))

(defn- reconstitute-entity
  "Reconstitutes a datomized tx-data entity ([{:db/id … :ns/k v …}]) back into the original
   bare, un-namespaced map so downstream key lookups (:records, :source-sensor, …) keep
   working unchanged. Tolerates both the tx-data shape and a legacy bare map."
  [content]
  (if (and (vector? content) (seq content) (map? (first content)) (contains? (first content) :db/id))
    (into {} (map (fn [[k v]] [(keyword (name k)) (unblob v)]))
          (dissoc (first content) :db/id))
    content))

(defn load-corpus
  "Read the pre-published corpus EDN (passive, G3). Defaults to the actor data dir."
  ([] (load-corpus nil))
  ([path]
   (let [f (io/file (or path "20-actors/danjo/data/gov-revenue-corpus.jp.edn"))
         f (if (.exists f) f (io/file "../data/gov-revenue-corpus.jp.edn"))]
     (reconstitute-entity (edn/read-string (slurp f))))))

(defn ingest
  "Convenience: load + project the JP revenue corpus into the model."
  ([] (ingest-corpus (load-corpus)))
  ([path] (ingest-corpus (load-corpus path))))

;; ── budgetRecord JSON ingest (the EXISTING danjo corpus, gov-fiscal-seed.jp.json) ──
;; danjo's appropriation/outlay corpus is JSON (budget_ledger.py shape). A dep-free JSON
;; reader lets ingest.clj consume it directly — same passive-only (G3) discipline, no portal.

(defn parse-json
  "Minimal RFC-8259 JSON → clj data (string keys). Integers parse as Long (1円 precision;
   amountLocal fits). Dep-free; runs under bb and clojure."
  [^String s]
  (let [n (count s) pos (atom 0)
        peek- (fn [] (when (< @pos n) (.charAt s @pos)))
        nxt   (fn [] (let [c (.charAt s @pos)] (swap! pos inc) c))
        ws    (fn [] (while (and (< @pos n) (Character/isWhitespace (.charAt s @pos))) (swap! pos inc)))]
    (letfn [(rd-str []
              (nxt)                                   ; opening quote
              (let [sb (StringBuilder.)]
                (loop []
                  (let [c (nxt)]
                    (cond
                      (= c \") (.toString sb)
                      (= c \\) (let [e (nxt)]
                                 (.append sb (case e \n \newline \t \tab \r \return \b \backspace \f \formfeed
                                               \u (let [h (subs s @pos (+ @pos 4))] (swap! pos + 4) (char (Integer/parseInt h 16)))
                                               e))
                                 (recur))
                      :else (do (.append sb c) (recur)))))))
            (rd-num []
              (let [start @pos]
                (while (and (< @pos n) (let [c (.charAt s @pos)] (or (Character/isDigit c) (#{\- \+ \. \e \E} c))))
                  (swap! pos inc))
                (let [t (subs s start @pos)]
                  (if (re-find #"[.eE]" t) (Double/parseDouble t) (Long/parseLong t)))))
            (rd-arr []
              (nxt) (ws)
              (if (= (peek-) \]) (do (nxt) [])
                  (loop [acc []]
                    (let [v (rd-val)] (ws)
                      (let [c (nxt)]
                        (cond (= c \,) (do (ws) (recur (conj acc v)))
                              (= c \]) (conj acc v)
                              :else (throw (ex-info "json: bad array" {:pos @pos}))))))))
            (rd-obj []
              (nxt) (ws)
              (if (= (peek-) \}) (do (nxt) {})
                  (loop [acc {}]
                    (ws)
                    (let [k (rd-str)] (ws) (nxt)        ; colon
                      (let [v (rd-val)] (ws)
                        (let [c (nxt)]
                          (cond (= c \,) (recur (assoc acc k v))
                                (= c \}) (assoc acc k v)
                                :else (throw (ex-info "json: bad object" {:pos @pos})))))))))
            (rd-val []
              (ws)
              (let [c (peek-)]
                (cond
                  (= c \{) (rd-obj)
                  (= c \[) (rd-arr)
                  (= c \") (rd-str)
                  (or (= c \-) (Character/isDigit c)) (rd-num)
                  (= c \t) (do (dotimes [_ 4] (nxt)) true)
                  (= c \f) (do (dotimes [_ 5] (nxt)) false)
                  (= c \n) (do (dotimes [_ 4] (nxt)) nil)
                  :else (throw (ex-info "json: unexpected char" {:pos @pos :c c})))))]
      (let [v (rd-val)] (ws) v))))

(defn budget-record-cid
  "gov.dataset.budgetRecord CID for a JSON (string-keyed) record — matches budget_ledger.py's
   record_cid string shape."
  [rec]
  (str "gov.dataset.budgetRecord:" (get rec "sourceSensor" "unknown") ":"
       (get rec "fiscalYear" 0) ":" (get rec "recordId" "unknown") "#"
       (subs (sha256-hex (canonical rec)) 0 24)))

(defn ingest-budget-corpus
  "Project a parsed budgetRecord JSON corpus (string keys, budget_ledger.py shape) → the
   appropriation/outlay side of the model. Passive (G3); ≥2 source CIDs each (G5). These are
   一般会計 (:general) lines — they feed the appropriation↔outlay reconciliation, NOT the
   復興特会 per-yen trace (which filters on earmarked accounts)."
  [corpus]
  (let [recs   (get corpus "records")
        ds-cid (str "gov.dataset.manifest:" (get corpus "sourceSensor" "unknown") "#"
                    (subs (sha256-hex (canonical (dissoc corpus "records"))) 0 24))
        cids   (fn [r] [(budget-record-cid r) ds-cid])
        amt!   (fn [r] (let [a (get r "amountLocal")]
                         (when-not (and (integer? a) (>= a 0))
                           (throw (ex-info (str "amountLocal must be non-negative integer, got " (pr-str a))
                                           {:record (get r "recordId")})))
                         a))
        norm   (fn [r] {:record-id (get r "recordId")
                        :account (keyword (get r "account" "general"))
                        :program-code (get r "programCode") :program-name (get r "programName")
                        :recipient-class (get r "recipientName" "")
                        :cofog (get r "cofog" "")
                        :fiscal-year (long (get r "fiscalYear")) :amount-jpy (amt! r)
                        :source-record-cids (cids r) :tier (get r "tier" "A")})]
    {:dataset-cid ds-cid
     :appropriations (vec (for [r recs :when (= "appropriation" (get r "recordKind"))] (norm r)))
     :outlays        (vec (for [r recs :when (#{"outlay" "obligation" "subaward"} (get r "recordKind"))] (norm r)))}))

(defn ingest-budget
  "Read + project the JSON budget corpus (defaults to danjo's gov-fiscal-seed.jp.json)."
  ([] (ingest-budget nil))
  ([path]
   (let [f (io/file (or path "20-actors/danjo/data/gov-fiscal-seed.jp.json"))
         f (if (.exists f) f (io/file "../data/gov-fiscal-seed.jp.json"))]
     (ingest-budget-corpus (parse-json (slurp f))))))

(defn with-budget
  "Merge a budget projection (its :appropriations + :outlays) into a revenue model."
  [model budget]
  (-> model
      (update :appropriations (fnil into []) (:appropriations budget))
      (update :outlays (fnil into []) (:outlays budget))))

(defn -main [& args]
  (let [model (ingest (first args))]
    (println "ingested" (:source-sensor model) "→ dataset-cid" (:dataset-cid model))
    (println " " (count (:revenue-lines model)) "revenue-lines,"
             (count (:transfers model)) "transfers,"
             (count (:outlays model)) "outlays")
    (doseq [rl (:revenue-lines model)]
      (println "  rev" (:tax-kind rl) (:amount-jpy rl) "← " (first (:source-record-cids rl))))))
