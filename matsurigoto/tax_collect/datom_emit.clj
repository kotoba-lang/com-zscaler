;; matsurigoto 政 — tax-collect / 源泉徴収納付の kotoba Datom-log emitter (canonical EAVT state)。
;; ADR-2606062300 · ADR-2605312345 (Datom = first-class canonical state)。
;;
;; 源泉徴収納付イベント (所得税徴収高計算書) を append-only な EAVT datom 列 [e a v tx op] に
;; 変換する。属性名前空間は :gensen.*。GROUND op=:add のみ (G5 非終末論; 取消は別 datom)。
;;
;; G1: :gensen.remit/server-held-authority は常に false。:proof は付与しない (署名なし)。
;; G8: 実際の canonical Datom log への ingest は Council+operator gated → ingest! は raise。
(ns matsurigoto.tax-collect.datom-emit
  (:require [clojure.string :as str]))

(def ^:const SERVER-HELD-AUTHORITY false)

(defn- fmt
  "datom 値を EDN 文字列に。keyword はそのまま、文字列は quote、bool/long はそのまま。"
  [v]
  (cond
    (keyword? v) (str v)
    (string? v)  (str \" (str/replace v "\"" "\\\"") \")
    (true? v)    "true"
    (false? v)   "false"
    (nil? v)     "nil"
    :else        (str v)))

(defn remittance-datoms
  "所得税徴収高計算書 (build-remittance-slip の出力) を EAVT datom のシーケンスに変換。
   各 datom = [entity attr value tx :add]。納付書 entity + 内訳行 entity を生成する。"
  ([slip] (remittance-datoms slip 1))
  ([{:keys [slip-type slip-name pay-period legal-due-date due-date total-tax
            operated-by status lines]} tx]
   (let [{:keys [year month special]} pay-period
         e (str "gensen.remit." year "-" (format "%02d" month)
                (when special ".tokurei") "." (name slip-type))
         head [[e :gensen.remit/slip-type slip-type tx :add]
               [e :gensen.remit/slip-name slip-name tx :add]
               [e :gensen.remit/period-year year tx :add]
               [e :gensen.remit/period-month month tx :add]
               [e :gensen.remit/special (boolean special) tx :add]
               [e :gensen.remit/legal-due-date legal-due-date tx :add]
               [e :gensen.remit/due-date due-date tx :add]
               [e :gensen.remit/total-tax total-tax tx :add]
               [e :gensen.remit/operated-by operated-by tx :add]
               [e :gensen.remit/status status tx :add]
               [e :gensen.remit/server-held-authority SERVER-HELD-AUTHORITY tx :add]]
         line-datoms
         (mapcat (fn [i {:keys [区分 人員 支給額 税額]}]
                   (let [le (str e ".line." i)]
                     (cond-> [[le :gensen.line/of e tx :add]
                              [le :gensen.line/区分 区分 tx :add]
                              [le :gensen.line/税額 税額 tx :add]]
                       人員  (conj [le :gensen.line/人員 人員 tx :add])
                       支給額 (conj [le :gensen.line/支給額 支給額 tx :add]))))
                 (range) lines)]
     (vec (concat head line-datoms)))))

(defn serialize
  "datom 列を kotoba Datom-log EDN 文字列に (canonical EAVT, ADR-2605312345)。"
  [datoms]
  (str ";; matsurigoto tax-collect — GENERATED 源泉徴収納付 kotoba Datom log. DO NOT hand-edit.\n"
       ";; Canonical EAVT state (ADR-2605312345). [e a v tx op]. op :add = append-only (G5).\n"
       "[\n"
       (str/join "\n"
                 (map (fn [[e a v tx op]]
                        (str "[" (fmt e) " " (fmt a) " " (fmt v) " " tx " " (fmt op) "]"))
                      datoms))
       "\n]\n"))

(defn ingest!
  "G8: canonical kotoba Datom log への live ingest は Council+operator gated → 必ず raise。"
  [& _]
  (throw (ex-info
          (str "tax-collect/datom-emit: live ingest into the canonical kotoba Datom log is "
               "Council+operator gated (G8). Datoms are produced offline + unsigned (G1).")
          {:server-held-authority SERVER-HELD-AUTHORITY})))
