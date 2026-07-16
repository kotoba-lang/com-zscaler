;; matsurigoto 政 — tax-collect / 源泉所得税の問い合わせ先・連絡先 engine。ADR-2606062300。
;; 法人が源泉所得税の届出・納付・相談を行う際の問い合わせ先 (国税庁・e-Taxヘルプデスク・
;; 国税局電話相談センター・所轄税務署の担当部門) を、所在都道府県から引き当てる。
;;
;; G5 sourcing-honest: 確証ある中央窓口の電話番号のみ :authoritative。国税局・税務署の個別番号は
;;   :pending-verification / :pending-live-ingest とし、出典 URL を示す。fabrication を避ける。
;; G1 何も送信しない (案内のみ)。個人名・個別納税者情報は扱わない (公的窓口の部門のみ)。
(ns matsurigoto.tax-collect.contacts
  (:require [clojure.edn :as edn]))

(def ^:private DEFAULT-REGISTRY-PATH
  "20-actors/matsurigoto/data/contacts/jpn-nta.edn")

;; jpn-nta.edn is now Datomic/Datascript tx-data (ADR-2607100030 fan-out): a single
;; [{:db/id -1 :contacts.jpn-nta/<key> <value-or-blob>}] entity. `unblob`/
;; `reconstitute-entity` un-namespace + pr-str-parse it back into the original bare
;; map so every existing keyword lookup below (`:national-tax-agency`, `:bureaus`, …)
;; keeps working unchanged.
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

(defn national-tax-agency [registry] (:national-tax-agency registry))
(defn etax-helpdesk       [registry] (:etax-helpdesk registry))
(defn phone-consultation  [registry] (:phone-consultation registry))
(defn bureaus             [registry] (:bureaus registry))

(defn bureau-for-prefecture
  "都道府県名 (例 \"東京\" \"大阪\") から所轄の国税局/国税事務所を返す。未対応は nil。"
  [registry prefecture]
  (first (filter (fn [b] (some #(= % prefecture) (:prefectures b)))
                 (bureaus registry))))

(defn corporate-division
  "税務署で法人の源泉所得税を担当する部門名 (源泉徴収義務者=法人の問い合わせ窓口)。"
  [registry]
  (get-in registry [:tax-office-division :gensen-corporate]))

(defn contact-plan
  "所在都道府県について、源泉所得税の問い合わせ先一式を返す。
   - 所轄国税局 (管轄は確証あり / 個別電話は要確認)
   - 国税局 電話相談センター (代表番号→自動音声「2」→源泉所得税)
   - 所轄税務署の担当部門 (法人課税部門。具体署は所在地で確定 = live ingest)
   - e-Tax ヘルプデスク (電子納税の操作案内)
   honest: 個別の局/署の電話番号は :phone-provenance で確からしさを明示する。"
  [registry prefecture]
  (let [bureau (bureau-for-prefecture registry prefecture)]
    {:prefecture prefecture
     :national-tax-agency (national-tax-agency registry)
     :bureau (when bureau
               {:ja (:ja bureau)
                :phone (:phone bureau)
                :postal (:postal bureau)
                :address (:address bureau)
                :prefectures (:prefectures bureau)
                :phone-provenance (:bureaus-phone-provenance registry)
                :source-url (:bureaus-phone-source-url registry)})
     :phone-consultation (phone-consultation registry)
     :tax-office {:division (corporate-division registry)
                  :resolution (get-in registry [:tax-offices :resolution])
                  :provenance (get-in registry [:tax-offices :provenance])
                  :source-url (get-in registry [:tax-offices :source-url])}
     :etax-helpdesk (etax-helpdesk registry)
     :unresolved (when-not bureau
                   (str "都道府県 " (pr-str prefecture) " は registry 未収載 (要確認)"))}))

(defn tax-offices
  "registry に取り込み済みの個別税務署レコード (operator ingest 前は空)。"
  [registry] (get-in registry [:tax-offices :offices] []))

(defn ingest-tax-offices
  "operator が国税庁ページから整形した個別署レコード列を registry に merge する (G7-gated step)。
   各 office は :tax-offices/:ingest-format に従う。fabrication は禁止 — 出典付きのみ受理する。"
  [registry offices]
  (doseq [o offices]
    (when-not (and (:id o) (:ja o) (:bureau o) (:source-url o))
      (throw (ex-info "税務署レコードは :id :ja :bureau :source-url 必須 (G5 出典必須)" {:office o}))))
  (update-in registry [:tax-offices :offices] (fnil into []) offices))

(defn tax-office-for-ward
  "市区町村名から所轄税務署を引く (ingest 済みのときのみ)。未 ingest は nil。"
  [registry ward]
  (first (filter (fn [o] (some #(= % ward) (:wards o))) (tax-offices registry))))

(defn coverage
  "連絡先 registry のカバレッジ (honest)。中央2窓口 + 12局の代表電話が :authoritative。
   個別の税務署 (約520署) は live ingest 待ち。"
  [registry]
  (let [central-auth (->> [(national-tax-agency registry) (etax-helpdesk registry)]
                          (filter #(and (:phone %) (= :authoritative (:provenance %))))
                          count)
        bureau-auth  (if (= :authoritative (:bureaus-phone-provenance registry))
                       (count (filter :phone (bureaus registry)))
                       0)]
    {:bureaus (count (bureaus registry))
     :prefectures-covered (reduce + (map #(count (:prefectures %)) (bureaus registry)))
     :authoritative-phones (+ central-auth bureau-auth)
     :bureau-phone-status (:bureaus-phone-provenance registry)
     :tax-office-status (get-in registry [:tax-offices :provenance])}))
