;; matsurigoto 政 — `tax-collect` module facade (源泉徴収納付 / withholding remittance)。
;; ADR-2606062300 · backs COFOG service `tax.withholding.remit` (源泉徴収納付)。
;;
;; tax-assess (Python, 申告税額の算定) の徴収側 sibling。法人 (源泉徴収義務者) の源泉所得税+
;; 復興特別所得税の (1) 源泉徴収税額計算 → (2) 納付書組立・納期限・加算税延滞税 → (3) 政府手続き →
;; (4) 問い合わせ先 → (5) kotoba Datom 化、を一つに束ねる。
;;
;; 全モジュール共通の invariant (matsurigoto CLAUDE.md):
;;   SERVER-HELD-AUTHORITY = false (G1, 何も署名しない) · solve() は raise (live 納付は
;;   Council+operator gated, G8) · spec-derived (G2) · operated-by を呼び出し側が渡す (G3)。
(ns matsurigoto.tax-collect.tax-collect
  (:require [matsurigoto.tax-collect.withholding :as w]
            [matsurigoto.tax-collect.payment :as p]
            [matsurigoto.tax-collect.procedures :as proc]
            [matsurigoto.tax-collect.contacts :as c]
            [matsurigoto.tax-collect.datom-emit :as d]))

(def ^:const SERVER-HELD-AUTHORITY false)

(def MODULE
  {:id "tax-collect"
   :label-ja "徴収・収納 (源泉徴収納付)"
   :backs ["tax.withholding.remit"]
   :maturity :reference-impl
   :statutes ["所得税法 181-230条" "復興財源確保法 28条" "国税通則法 36・60・67条"]
   :invariants {:server-held-authority SERVER-HELD-AUTHORITY :spec-derived true}})

(defn process-period
  "ある支払年月の源泉徴収結果 (withholding の出力マップ列) から、納付書 + Datom 列を組み立てる
   (すべてオフライン・unsigned)。lines は {:区分 :人員 :支給額 :税額} の列。
     opts {:slip-type :pay-year :pay-month :special? :operated-by :lines :tx}"
  [{:keys [slip-type pay-year pay-month special? operated-by lines tx]
    :or {special? false tx 1}}]
  (let [slip   (p/build-remittance-slip
                {:slip-type slip-type :pay-year pay-year :pay-month pay-month
                 :special? special? :operated-by operated-by :lines lines})
        datoms (d/remittance-datoms slip tx)]
    {:slip slip
     :datoms datoms
     :datom-edn (d/serialize datoms)
     :contacts-hint "都道府県を contacts/contact-plan に渡すと所轄局・税務署部門・e-Tax窓口を取得"}))

(defn solve
  "Cell entry — R0 は参照計算・納付書準備・Datom生成 (オフライン) のみ。
   実際の納付 (e-Tax 送信・口座引落・法定調書提出・Datom ingest) は Council+operator gated (G8)。"
  [& _]
  (throw (ex-info
          (str "tax-collect R0: reference withholding-remittance only. Live remittance "
               "(e-Tax / direct debit / 法定調書提出 / Datom ingest) is Council+operator gated "
               "(principal A: Council Lv7+; principal B: adopting state). The module signs nothing.")
          {:module "tax-collect" :server-held-authority SERVER-HELD-AUTHORITY})))
