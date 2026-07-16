;; revenue_ledger.clj — 弾正 (danjo) revenue-side ledger + honest tax-use tracer. ADR-2605301600.
;;
;; Answers, in Clojure on the kotoba EAVT Datom log, the question:
;;   「源泉所得税 及び 復興特別所得税 が、どこに、どのように使われているか 1円単位で追えるか?」
;;
;; The honest answer is structural, not rhetorical, and it differs by tax-kind:
;;
;;   源泉所得税  → 一般会計 (:gov.account/earmark? false). ノン・アフェクタシオン原則 (non-earmarking):
;;               revenue is fungible, so a SPECIFIC yen has NO accounting link to a SPECIFIC 歳出.
;;               `trace` returns :traceable? false with an AGGREGATE cross-reference only, and
;;               `outlay-datoms` RAISES if anyone tries to assert per-yen provenance through a
;;               non-earmarked boundary — the honesty analogue of danjo's G4 (a verdict is
;;               unrepresentable; here a false provenance claim is unrepresentable).
;;
;;   復興特別所得税 → 一般会計 → (繰入 :gov.transfer) → 東日本大震災復興特別会計 (:earmark? true).
;;               A special account is a CLOSED boundary; within it 繰入額 → 歳出 reconciles to the
;;               yen, so `trace` returns :traceable? true with a per-yen path + residual.
;;
;; Pure functions + JVM stdlib only (java.security.MessageDigest, clojure.edn). Runs under
;; `clojure` or `bb`. The persisted log is the same content-addressed commit-DAG shape as
;; danjo/methods/kotoba.py (`:tx/cid` = "b" <sha256 hex>), so the two writers interoperate.
(ns root.danjo.methods.revenue-ledger
  (:require [clojure.edn :as edn]
            [clojure.string :as str]
            [clojure.java.io :as io])
  (:import [java.security MessageDigest]))

;; ── danjo discipline constants ───────────────────────────────────────────────
(def forbidden-verdict-tokens
  "G4 — no danjo datom may name a legal verdict (mirrors kotoba.py)."
  ["verdict" "guilt" "wrongdoing" "culprit" "illegal" "crime" "violation"
   "unlawful" "fraud" "sanction" "犯罪" "違法" "有罪" "不正"])

(def seed-default
  (str (System/getProperty "user.dir") "/../data/gov-revenue-seed.jp.edn"))

(def log-default
  "Append-only local kotoba Datom log for the revenue ledger."
  "../data/persisted/danjo.revenue.datoms.kotoba.edn")

;; ── load ──────────────────────────────────────────────────────────────────────
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
   bare, un-namespaced map so downstream key lookups (:accounts, :revenue-lines, …) keep
   working unchanged. Tolerates both the tx-data shape and a legacy bare map."
  [content]
  (if (and (vector? content) (seq content) (map? (first content)) (contains? (first content) :db/id))
    (into {} (map (fn [[k v]] [(keyword (name k)) (unblob v)]))
          (dissoc (first content) :db/id))
    content))

(defn load-seed
  "Read the representative JP revenue seed (EDN). Path defaults to the actor data dir."
  ([] (load-seed nil))
  ([path]
   (let [f (io/file (or path "20-actors/danjo/data/gov-revenue-seed.jp.edn"))
         f (if (.exists f) f (io/file "data/gov-revenue-seed.jp.edn"))]
     (reconstitute-entity (edn/read-string (slurp f))))))

;; ── pure index over the seed (the in-memory "db") ───────────────────────────────
(defn index
  "Project the seed into lookup maps keyed by the natural ids. Pure."
  [seed]
  {:accounts  (into {} (map (juxt :id identity) (:accounts seed)))
   :revenue   (vec (:revenue-lines seed))
   :transfers (vec (:transfers seed))
   :outlays   (vec (:outlays seed))})

(defn earmarked?
  "Is `account-id` an earmarked (special-account) boundary in this db?"
  [db account-id]
  (boolean (get-in db [:accounts account-id :earmark?])))

;; ── EAVT datom emission ([:db/add E A V], append-only) ──────────────────────────
(defn- add [e a v] [:db/add e a v])

(defn- check-sources!
  "G5 — every record cites ≥2 upstream public-record CIDs."
  [rec what]
  (when (< (count (:source-record-cids rec)) 2)
    (throw (ex-info (str "G5: " what " needs ≥2 source-record-cids")
                    {:record (:record-id rec)}))))

(defn revenue-datoms
  "Flatten revenue lines → EAVT. E = revenue-line:jp:<fy>:<tax-kind>."
  [seed]
  (mapcat
   (fn [r]
     (check-sources! r "revenue line")
     (let [e (str "revenue-line:jp:" (:fiscal-year r) ":" (name (:tax-kind r)))]
       [(add e :gov.revenue/tax-kind (:tax-kind r))
        (add e :gov.revenue/account (:account r))
        (add e :gov.revenue/fiscal-year (:fiscal-year r))
        (add e :gov.revenue/amount-jpy (:amount-jpy r))
        (add e :gov.revenue/source-record-cids (vec (:source-record-cids r)))
        (add e :gov.revenue/sourcing :representative)]))
   (:revenue-lines seed)))

(defn account-datoms
  "Flatten account boundaries → EAVT. The :earmark? bit is what makes per-yen
   provenance representable (special) or not (general)."
  [seed]
  (mapcat
   (fn [a]
     (let [e (str "account:" (subs (str (:id a)) 1))]
       [(add e :gov.account/kind (:kind a))
        (add e :gov.account/earmark? (:earmark? a))
        (add e :gov.account/note (:note a))]))
   (:accounts seed)))

(defn transfer-datoms
  "Flatten 繰入 transfers → EAVT. A transfer is only legal OUT of an earmarked tax."
  [seed]
  (let [db (index seed)]
    (mapcat
     (fn [t]
       (check-sources! t "transfer")
       (when-not (earmarked? db (:to t))
         (throw (ex-info "transfer target must be an earmarked account" {:transfer (:record-id t)})))
       (let [e (str "transfer:" (subs (str (:from t)) 1) "->"
                    (subs (str (:to t)) 1) ":" (:fiscal-year t))]
         [(add e :gov.transfer/from (:from t))
          (add e :gov.transfer/to (:to t))
          (add e :gov.transfer/tax-kind (:tax-kind t))
          (add e :gov.transfer/fiscal-year (:fiscal-year t))
          (add e :gov.transfer/amount-jpy (:amount-jpy t))
          (add e :gov.transfer/source-record-cids (vec (:source-record-cids t)))]))
     (:transfers seed))))

(defn outlay-datoms
  "Flatten outlays → EAVT. STRUCTURAL HONESTY GATE: an outlay may carry
   :gov.outlay/funded-by-tax ONLY when its account is earmarked. Trying to bind a
   non-earmarked (一般会計) revenue to a specific outlay RAISES — per-yen provenance
   through a fungible boundary is unrepresentable, exactly as a verdict is for danjo."
  [seed]
  (let [db (index seed)]
    (mapcat
     (fn [o]
       (check-sources! o "outlay")
       (when (and (contains? o :funded-by-tax)
                  (not (earmarked? db (:account o))))
         (throw (ex-info (str "honesty-gate: cannot link tax " (:funded-by-tax o)
                              " to outlay " (:record-id o)
                              " — account " (:account o) " is non-earmarked (fungible)")
                         {:outlay (:record-id o) :account (:account o)})))
       (let [e (str "outlay:" (:program-code o) ":" (:fiscal-year o))]
         (cond-> [(add e :gov.outlay/account (:account o))
                  (add e :gov.outlay/program-code (:program-code o))
                  (add e :gov.outlay/program-name (:program-name o))
                  (add e :gov.outlay/cofog (:cofog o))
                  (add e :gov.outlay/recipient-class (:recipient-class o))
                  (add e :gov.outlay/fiscal-year (:fiscal-year o))
                  (add e :gov.outlay/amount-jpy (:amount-jpy o))
                  (add e :gov.outlay/source-record-cids (vec (:source-record-cids o)))
                  (add e :gov.outlay/sourcing :representative)]
           (:funded-by-tax o) (conj (add e :gov.outlay/funded-by-tax (:funded-by-tax o))))))
     (:outlays seed))))

(defn appropriation-datoms
  "Flatten 予算 appropriations → EAVT. E = appropriation:<program-code>:<fy>. These are the
   budget side the appropriation↔outlay reconciliation (discrepancy.clj) groups against."
  [seed]
  (mapcat
   (fn [a]
     (check-sources! a "appropriation")
     (let [e (str "appropriation:" (:program-code a) ":" (:fiscal-year a))]
       [(add e :gov.appropriation/account (:account a))
        (add e :gov.appropriation/program-code (:program-code a))
        (add e :gov.appropriation/program-name (:program-name a))
        (add e :gov.appropriation/fiscal-year (:fiscal-year a))
        (add e :gov.appropriation/amount-jpy (:amount-jpy a))
        (add e :gov.appropriation/source-record-cids (vec (:source-record-cids a)))
        (add e :gov.appropriation/sourcing :representative)]))
   (:appropriations seed)))

(defn all-datoms
  "Every account/revenue/transfer/appropriation/outlay assertion, G4/G5/honesty-gated."
  [seed]
  (let [out (concat (account-datoms seed) (revenue-datoms seed)
                    (transfer-datoms seed) (appropriation-datoms seed) (outlay-datoms seed))]
    ;; G4 structural self-check: no verdict token in any attribute we persist.
    (doseq [[_ a _] out]
      (let [an (str/lower-case (str a))]
        (when (some #(str/includes? an %) forbidden-verdict-tokens)
          (throw (ex-info (str "G4: verdict attr " a " is unrepresentable") {:attr a})))))
    (vec out)))

;; ── the tracer (the actual question) ────────────────────────────────────────────
(defn trace
  "Trace where a tax-kind's money goes, for a fiscal year. Returns an honest result:

   earmarked tax (e.g. :reconstruction-surtax) →
     {:traceable? true :per-yen? true :path [...] :collected :transferred :spent :residual ...}
   non-earmarked tax (e.g. :withholding-income) →
     {:traceable? false :reason :non-earmarked-general-account :per-yen? false
      :aggregate {...} :note ...}"
  [seed tax-kind fiscal-year]
  (let [db   (index seed)
        rev  (->> (:revenue db)
                  (filter #(and (= (:tax-kind %) tax-kind) (= (:fiscal-year %) fiscal-year)))
                  first)]
    (when-not rev
      (throw (ex-info "no revenue line" {:tax-kind tax-kind :fiscal-year fiscal-year})))
    ;; The earmark decision is STRUCTURAL, not where the tax is first booked: a tax is
    ;; per-yen traceable iff an explicit 繰入 (transfer) moves it INTO an earmarked
    ;; (special) account. 復興特別所得税 is collected via 一般会計 yet earmarked, because a
    ;; transfer into 復興特別会計 exists; 源泉所得税 has no such transfer → fungible.
    (let [xfers (->> (:transfers db)
                     (filter #(and (= (:tax-kind %) tax-kind)
                                   (= (:fiscal-year %) fiscal-year)
                                   (earmarked? db (:to %)))))]
    (if (empty? xfers)
      ;; ── 源泉所得税 path: fungible, NOT per-yen traceable ──
      {:tax-kind   tax-kind
       :fiscal-year fiscal-year
       :traceable? false
       :per-yen?   false
       :reason     :non-earmarked-general-account
       :collected  (:amount-jpy rev)
       :account    (:account rev)
       :aggregate  {:note "一般会計 is fungible; this tax mixes with all other general revenue."
                    :general-revenue-line (:amount-jpy rev)}
       :note "源泉所得税の特定の1円が特定の歳出に充てられた、という会計的事実は存在しない (ノン・アフェクタシオン原則)。danjo は予算→支出の相互参照と乖離の事実指摘までに留まる。"
       :non-adjudicating true}
      ;; ── 復興特別所得税 path: earmarked, per-yen traceable within the special account ──
      (let [targets (set (map :to xfers))
            outs    (->> (:outlays db)
                         (filter #(and (contains? targets (:account %))
                                       (= (:fiscal-year %) fiscal-year))))
            collected   (:amount-jpy rev)
            transferred (reduce + 0 (map :amount-jpy xfers))
            spent       (reduce + 0 (map :amount-jpy outs))]
        {:tax-kind    tax-kind
         :fiscal-year fiscal-year
         :traceable?  true
         :per-yen?    true
         :collected   collected
         :transferred transferred
         :spent       spent
         :residual    (- transferred spent)           ; 0 = fully reconciled to the yen
         :path        (vec (concat
                            [{:step :collect  :account (:account rev) :amount-jpy collected}]
                            (map (fn [t] {:step :transfer :from (:from t) :to (:to t)
                                          :amount-jpy (:amount-jpy t)}) xfers)
                            (map (fn [o] {:step :outlay :account (:account o)
                                          :program (:program-name o) :cofog (:cofog o)
                                          :recipient-class (:recipient-class o)
                                          :amount-jpy (:amount-jpy o)}) outs)))
         :note "復興特別所得税は復興特別会計へ繰入される特定財源。閉じた会計境界内で繰入額と歳出が1円単位で照合できる。"
         :non-adjudicating true})))))

;; ── content-addressed commit-DAG log (kotoba.py-compatible) ──────────────────────
(defn- sha256-hex [^String s]
  (let [md (MessageDigest/getInstance "SHA-256")
        bs (.digest md (.getBytes s "UTF-8"))]
    (apply str (map #(format "%02x" (bit-and % 0xff)) bs))))

(defn- canonical [datoms prev-cid]
  (str "{\"datoms\":[" (str/join "," (map pr-str datoms)) "],\"prev\":\"" prev-cid "\"}"))

(defn tx-cid
  "Content address = 'b' + sha256 over (prev-cid, datoms) → a commit-DAG."
  [datoms prev-cid]
  (str "b" (sha256-hex (canonical datoms (or prev-cid "")))))

(defn make-tx [datoms {:keys [tx-id as-of prev-cid]}]
  (let [prev-cid (or prev-cid "")]
    {:tx/id tx-id :tx/as-of as-of :tx/prev prev-cid
     :tx/cid (tx-cid datoms prev-cid) :tx/count (count datoms) :tx/datoms (vec datoms)}))

(defn data-tx? [tx]
  "A revenue/account/transfer/outlay tx (NOT a :bridge/* push-cursor checkpoint)."
  (not-any? (fn [[_ _ a _]] (and (keyword? a) (= "bridge" (namespace a))))
            (:tx/datoms tx)))

(defn read-log [path]
  (let [f (io/file path)]
    (if-not (.exists f)
      []
      (->> (str/split-lines (slurp f))
           (map str/trim)
           (remove #(or (str/blank? %) (str/starts-with? % ";")))
           (mapv edn/read-string)))))

(defn head-cid [path]
  (let [txs (read-log path)] (if (seq txs) (:tx/cid (peek txs)) "")))

(defn append-tx!
  "Append ONE transaction to the append-only log (never rewrites). Returns the tx CID."
  [tx path]
  (let [f (io/file path)]
    (io/make-parents f)
    (when-not (.exists f)
      (spit f (str ";; danjo revenue-ledger kotoba Datom log — append-only EAVT (content-addressed DAG).\n"
                   ";; The censor's EYE, never the SWORD. Per-yen provenance through a fungible\n"
                   ";; (non-earmarked) account is unrepresentable. DO NOT hand-edit. ADR-2605301600.\n")))
    (spit f (str (pr-str tx) "\n") :append true)
    (:tx/cid tx)))

(defn verify-chain
  "Recompute every CID from its datoms + prev; verify the DAG is intact."
  [path]
  (let [txs (read-log path)]
    (loop [prev "" i 0 ts txs]
      (if (empty? ts)
        {:ok true :length (count txs) :broken-at -1}
        (let [tx (first ts)
              expect (tx-cid (:tx/datoms tx) prev)]
          (if (or (not= (:tx/cid tx) expect) (not= (:tx/prev tx) prev))
            {:ok false :length (count txs) :broken-at i}
            (recur (:tx/cid tx) (inc i) (rest ts))))))))

;; ── one heartbeat cycle (offline, deterministic, resume-safe) ────────────────────
(defn run-cycle!
  "Emit guarded datoms → append ONE tx chained on the log head → return the head CID +
   trace summaries. `:seed` (a pre-ingested model, e.g. from ingest.clj) takes precedence
   over `:seed-path`. tx-id auto-increments per DATA tx so the kotoba bridge cursor is
   monotonic. Offline; no external I/O."
  [{:keys [seed seed-path log-path tx-id as-of extra-datoms]
    :or {log-path log-default as-of 0 extra-datoms []}}]
  (let [seed   (or seed (load-seed seed-path))
        datoms (vec (concat (all-datoms seed) extra-datoms))
        existing (read-log log-path)
        tx-id  (or tx-id (inc (count (filter data-tx? existing))))
        prev   (head-cid log-path)
        tx     (make-tx datoms {:tx-id tx-id :as-of as-of :prev-cid prev})
        cid    (append-tx! tx log-path)]
    {:head-cid cid
     :tx-id tx-id
     :datom-count (count datoms)
     :traces {:withholding-income    (trace seed :withholding-income 2024)
              :reconstruction-surtax (trace seed :reconstruction-surtax 2024)}}))

(defn -main [& args]
  (let [seed-path (first args)
        r (trace (load-seed seed-path) :reconstruction-surtax 2024)
        w (trace (load-seed seed-path) :withholding-income 2024)]
    (println "復興特別所得税 (earmarked → 復興特会):")
    (println "  traceable?" (:traceable? r) " per-yen?" (:per-yen? r)
             " collected" (:collected r) " spent" (:spent r) " residual" (:residual r))
    (doseq [s (:path r)] (println "   " s))
    (println)
    (println "源泉所得税 (一般会計, fungible):")
    (println "  traceable?" (:traceable? w) " reason" (:reason w))
    (println "  " (:note w))))
