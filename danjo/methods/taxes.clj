;; taxes.clj — 弾正 (danjo) national-tax registry: load + honest earmark classification + EAVT.
;; ADR-2605301600. Generalizes the revenue ledger from 所得税 to 国全体の税金.
;;
;; The crux is the HONEST 3-way traceability classification (the danjo coverage-honesty discipline):
;;   :general           一般会計・普通税 → fungible → per-yen 追跡 不可.
;;   :statutory-purpose  目的税的 (法律が充当先を定める, 例 消費税→社会保障) → 一般会計内なので
;;                       依然 fungible → 法的「充当方向」は事実だが per-yen 追跡 不可 (中間カテゴリ).
;;   :special-account   特別会計へ繰入される特定財源 → 閉じた境界で per-yen 追跡 可
;;                       (revenue_ledger.clj `trace` が flow モデル上で residual=0 まで照合).
;;
;; Pure + JVM stdlib; runs under bb and clojure.
(ns root.danjo.methods.taxes
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as str]))

(load-file "revenue_ledger.clj")
(alias 'rl 'root.danjo.methods.revenue-ledger)

(def traceability-class
  "Structural per-yen traceability by earmark kind — the honest answer, before any flow trace."
  {:general           {:traceable? false :per-yen? false :reason :non-earmarked-general-account
                       :note "一般会計・普通税。ノン・アフェクタシオン原則で資金は代替可能、特定の1円の使途は会計的に存在しない。"}
   :statutory-purpose {:traceable? false :per-yen? false :reason :statutory-purpose-in-general-account
                       :note "目的税的: 法律が充当先を定めるが一般会計内のため資金は代替可能。法的充当方向は事実、特定の1円の追跡は不可。"}
   :special-account   {:traceable? true  :per-yen? true  :reason :earmarked-special-account
                       :note "特別会計へ繰入される特定財源。閉じた会計境界内で繰入額→歳出を1円単位で照合できる。"}})

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
   bare, un-namespaced map so downstream key lookups (:taxes, …) keep working unchanged.
   Tolerates both the tx-data shape and a legacy bare map."
  [content]
  (if (and (vector? content) (seq content) (map? (first content)) (contains? (first content) :db/id))
    (into {} (map (fn [[k v]] [(keyword (name k)) (unblob v)]))
          (dissoc (first content) :db/id))
    content))

(defn- read-edn [path fallback]
  (let [f (io/file (or path fallback))
        f (if (.exists f) f (io/file (str "../data/" (.getName (io/file fallback)))))]
    (reconstitute-entity (edn/read-string (slurp f)))))

(defn load-taxes
  "National-tax registry (jp-national-taxes.edn)."
  ([] (load-taxes nil))
  ([path] (read-edn path "20-actors/danjo/data/jp-national-taxes.edn")))

(defn load-local-taxes
  "Local-tax registry (jp-local-taxes.edn)."
  ([] (load-local-taxes nil))
  ([path] (read-edn path "20-actors/danjo/data/jp-local-taxes.edn")))

(defn combine
  "Merge registries into one {:taxes …} — national + local for the 国+地方 whole-tax picture.
   Each tax keeps its :level (defaulting :national for the national registry)."
  [& registries]
  {:fiscal-year (:fiscal-year (first registries))
   :verification-status "representative"
   :taxes (vec (mapcat (fn [r] (map #(update % :level (fn [l] (or l :national))) (:taxes r)))
                       registries))})

(defn classify
  "Structural traceability classification for one tax (registry-level, honest)."
  [tax]
  (merge (get traceability-class (:earmark-kind tax))
         (select-keys tax [:id :ja :en :level :account :earmark-kind :special-account
                           :statutory-purpose :statutory-basis :collected-by :administered-by
                           :fy2024-amount-jpy])
         {:level (or (:level tax) :national)}
         (when (:note tax) {:registry-note (:note tax)})))

(defn- add [e a v] [:db/add e a v])

(defn tax-datoms
  "Flatten the national-tax registry → append-only EAVT `:gov.tax/*`. The earmark kind + the
   honest traceability flags are first-class on every tax entity."
  [registry]
  (let [out (mapcat
             (fn [tx]
               (let [c (classify tx)
                     e (str "tax:jp:" (name (:id tx)))]
                 (cond-> [(add e :gov.tax/ja (:ja tx))
                          (add e :gov.tax/account (:account tx))
                          (add e :gov.tax/earmark-kind (:earmark-kind tx))
                          (add e :gov.tax/collected-by (:collected-by tx))
                          (add e :gov.tax/amount-jpy (:fy2024-amount-jpy tx))
                          (add e :gov.tax/fiscal-year (:fiscal-year registry))
                          (add e :gov.tax/statutory-basis (:statutory-basis tx))
                          (add e :gov.tax/traceable? (:traceable? c))
                          (add e :gov.tax/per-yen? (:per-yen? c))
                          (add e :gov.tax/reason (:reason c))
                          (add e :gov.tax/sourcing :representative)]
                   (:special-account tx)   (conj (add e :gov.tax/special-account (:special-account tx)))
                   (:administered-by tx)   (conj (add e :gov.tax/administered-by (:administered-by tx)))
                   (:statutory-purpose tx) (conj (add e :gov.tax/statutory-purpose (:statutory-purpose tx))))))
             (:taxes registry))]
    ;; G4: no verdict token may appear in an attribute (mirrors revenue_ledger all-datoms).
    (doseq [[_ a _] out]
      (when (some #(str/includes? (str/lower-case (str a)) %) rl/forbidden-verdict-tokens)
        (throw (ex-info (str "G4: verdict attr " a) {:attr a}))))
    (vec out)))

(defn summary
  "Honest fiscal summary over the registry: totals + per-yen-traceable share by amount."
  [registry]
  (let [txs   (map classify (:taxes registry))
        total (reduce + 0 (map :fy2024-amount-jpy txs))
        by    (group-by :earmark-kind txs)
        amt   (fn [ks] (reduce + 0 (map :fy2024-amount-jpy ks)))]
    {:tax-count (count txs)
     :total-jpy total
     :by-earmark-kind
     (into {} (for [[k v] by] [k {:count (count v) :amount-jpy (amt v)}]))
     :per-yen-traceable-amount   (amt (:special-account by))
     :per-yen-traceable-share    (if (zero? total) 0.0
                                     (double (/ (amt (:special-account by)) total)))
     :statutorily-directed-amount (amt (:statutory-purpose by))
     :fungible-amount             (amt (:general by))
     :by-level
     (into {} (for [[lvl v] (group-by :level txs)]
                [lvl {:count (count v) :amount-jpy (amt v)}]))}))

(defn -main [& args]
  (let [reg (load-taxes (first args))
        s (summary reg)]
    (println "国税レジストリ:" (:tax-count s) "taxes, total ≈" (:total-jpy s) "JPY (representative)")
    (doseq [[k v] (sort-by (comp - :amount-jpy val) (:by-earmark-kind s))]
      (println " " k ":" (:count v) "taxes," (:amount-jpy v) "JPY"))
    (println (format "per-yen-traceable share (特別会計分): %.1f%%" (* 100 (:per-yen-traceable-share s))))))
