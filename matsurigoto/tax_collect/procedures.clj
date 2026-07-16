;; matsurigoto 政 — tax-collect / 源泉所得税まわりの政府側手続き engine。ADR-2606062300。
;; 法人 (源泉徴収義務者) が行う届出・申請・法定調書提出を、トリガー (開設/雇用/支払/年末/退職/
;; 年次法定調書) で引き当て、起算日から具体的な提出期限を算定する。
;;
;; G1 何も提出・署名しない (R0は手続き案内のみ; live 提出は solve が raise)。
;; G2 spec-derived: 所得税法 + 同施行規則 + 国税庁様式 (registry の :statute / :source-url)。
;; G5 sourcing-honest: registry は data/procedures/jpn-gensen.edn (各手続きに出典)。
(ns matsurigoto.tax-collect.procedures
  (:require [clojure.edn :as edn]
            [matsurigoto.tax-collect.jp-calendar :as cal])
  (:import [java.time LocalDate]))

(def ^:const SERVER-HELD-AUTHORITY false)

(def ^:private DEFAULT-REGISTRY-PATH
  "20-actors/matsurigoto/data/procedures/jpn-gensen.edn")

;; jpn-gensen.edn is now Datomic/Datascript tx-data (ADR-2607100030 fan-out): a single
;; [{:db/id -1 :procedures.jpn-gensen/<key> <value-or-blob>}] entity. `unblob`/
;; `reconstitute-entity` un-namespace + pr-str-parse it back into the original bare
;; map so `(:procedures registry)` etc. below keep working unchanged.
(defn- unblob [v]
  (if (string? v)
    (try (let [parsed (edn/read-string v)] (if (coll? parsed) parsed v))
         (catch Exception _ v))
    v))

(defn- reconstitute-entity [tx-data]
  (into {} (map (fn [[k v]] [(keyword (name k)) (unblob v)]))
        (dissoc (first tx-data) :db/id)))

(defn load-registry
  ([] (load-registry DEFAULT-REGISTRY-PATH))
  ([path] (reconstitute-entity (edn/read-string (slurp path)))))

(defn procedures
  "registry の全手続き (シーケンス)。"
  [registry] (:procedures registry))

(defn procedures-for
  "トリガー (例 :open-payroll-office :employment-start :annual-statutory-report) に
   該当する手続きの一覧。"
  [registry trigger]
  (filterv #(= trigger (:trigger %)) (procedures registry)))

(defn procedure-by-id
  [registry id]
  (first (filter #(= id (:id %)) (procedures registry))))

(defn resolve-deadline
  "手続きの提出期限を、起算日 base-date (yyyy-mm-dd 文字列, 任意) から算定する。
   日付が確定するものは {:deadline-date <繰下げ後の開庁日> :legal-deadline <法律上>} を、
   相対的なもの (申告書の前日まで等) は {:description …} を返す。
   提出期限が閉庁日に当たるときは翌開庁日に繰り下げる (通則法10条2項)。"
  ([proc] (resolve-deadline proc nil))
  ([proc base-date]
   (let [{:keys [kind n month day from]} (:deadline proc)
         base (when base-date (cal/parse base-date))
         finalize (fn [^LocalDate d]
                    {:legal-deadline (str d)
                     :deadline-date (str (cal/next-open-day d))})]
     (case kind
       :months-after        (if base
                              (finalize (.plusMonths base n))
                              {:description (str from "から" n "か月以内")})
       :fixed-next-year     (let [y (if base (inc (.getYear base))
                                        (inc (.getYear (LocalDate/now))))]
                              (finalize (LocalDate/of y (int month) (int day))))
       :anytime-approval    {:description (or (:effect proc) "随時")}
       :before-first-salary {:description "その年最初の給与支払日の前日まで"}
       :before-last-salary  {:description "本年最後の給与支払日の前日まで"}
       :year-end            {:description "本年最後の給与支払時 (年末調整)"}
       :before-payment      {:description "支払を受ける時まで"}
       {:description "—"}))))

(defn plan
  "あるトリガーについて、該当手続き + 算定済み期限 + 提出先 + 出典をまとめた手続きプラン。"
  [registry trigger base-date]
  (mapv (fn [proc]
          (-> (select-keys proc [:id :ja :filed-to :submitted-by :method :statute
                                 :source-url :condition :effect])
              (assoc :deadline (resolve-deadline proc base-date))))
        (procedures-for registry trigger)))

(defn solve
  "Cell entry — R0 は手続き案内のみ。実際の届出・申請・法定調書の提出 (e-Tax/eLTAX送信) は
   Council+operator gated (G8)。"
  [& _]
  (throw (ex-info
          (str "tax-collect/procedures R0: guidance only. Live filing (e-Tax / eLTAX "
               "submission of 届出書 / 申請書 / 法定調書) is Council+operator gated.")
          {:server-held-authority SERVER-HELD-AUTHORITY})))
