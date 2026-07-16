(ns tasuke.methods.report-gen
  "report_gen.cljc — 助 (tasuke) document generation. ADR-2606060900.
  1:1 Clojure port of `methods/report_gen.py`.

  Generates the ready-to-use, member-authored filings (被害届 / 被害状況報告書 / 証拠目録 /
  被害額算定書 / 銀行組戻し依頼 / プラットフォーム依頼 / アカウント復旧手順). The LOAD-BEARING
  invariant (G3): every generated document is authored BY THE MEMBER, addressed TO the authority —
  never authored AS the police. `doc` hard-wires `:doc/authored-by :member`,
  `:doc/needs-member-signature true`, `:doc/support-cost-jpy 0`, `:doc/published false`.

  House style: Python ':…' keyword strings stay strings; pure fns; f-string thousands `{n:,}`
  reproduced exactly."
  (:require [clojure.string :as str]
            [tasuke.methods.evidence :as ev]
            [tasuke.methods.triage :as triage]))

(def POLICE-DOC-KINDS ["damage-report" "incident-statement" "evidence-index" "damage-calculation"])
(def REQUEST-DOC-KINDS ["platform-request" "bank-freeze-request"])

;; ── helpers ─────────────────────────────────────────────────────────────────────
(defn- comma
  "Python f\"{n:,}\" — group integer digits with commas (handles a leading minus sign)."
  [n]
  (let [s (str (long n))
        neg (str/starts-with? s "-")
        digits (if neg (subs s 1) s)
        grouped (->> (reverse digits)
                     (partition-all 3)
                     (map #(apply str (reverse %)))
                     (reverse)
                     (str/join ","))]
    (str (when neg "-") grouped)))

(defn- to-int
  [v]
  (cond
    (nil? v) 0
    (false? v) 0
    (number? v) (long v)
    (string? v) (if (str/blank? v) 0 (try (Long/parseLong (str/trim v)) (catch Exception _ 0)))
    :else 0))

(def ^:private JA
  {"phishing" "フィッシング" "unauthorized-transfer" "不正送金"
   "account-takeover" "アカウント乗っ取り" "support-scam" "サポート詐欺"
   "romance-scam" "ロマンス詐欺" "investment-scam" "投資詐欺" "ransomware" "ランサムウェア"
   "impersonation" "なりすまし" "fake-billing" "架空請求" "sns-fraud" "SNS型詐欺"
   "leak-extortion" "情報流出・脅迫"})

(defn- ja-kind [kind] (get JA kind kind))

;; ── doc builder (G1/G2/G3/G7/G9 baked in, not overridable) ───────────────────────
(defn- doc
  "Build a document record with the invariants baked in. `extra` is a map of extra k/v pairs;
  it may carry a :doc-id override (mirrors Python's extra.pop('doc_id'))."
  ([case kind body addressed-to] (doc case kind body addressed-to {}))
  ([case kind body addressed-to extra]
   (let [doc-id (get extra :doc-id (str case ":" kind))
         extra* (dissoc extra :doc-id)]
     (merge
      {":doc/id" doc-id
       ":doc/case" case
       ":doc/kind" (str ":" kind)
       ":doc/authored-by" ":member"          ;; G3 — never :police / :official / :server
       ":doc/addressed-to" addressed-to
       ":doc/body" body
       ":doc/needs-member-signature" true     ;; G2/G7 — member reviews + signs before use
       ":doc/support-cost-jpy" 0              ;; G1 — free
       ":doc/published" false}                ;; G9 — draft-only at R0
      extra*))))

(defn- hdr [case title]
  (let [subj (get case ":case/subject" "（被害者氏名）")]
    (str title "\n\n申告者: " subj "（本人作成・要署名）\n作成日: （提出日を記入）\n")))

(defn damage-report
  "被害届(下書き). 本人が署名・提出する申告書類。警察官作成の公文書ではない(G3)。"
  ([case] (damage-report case "（管轄）警察署長 殿"))
  ([case station]
   (let [kind (triage/classify case)
         loss (to-int (get case ":case/loss-jpy" 0))
         body (str
               (hdr case "被 害 届（下書き）")
               "\n宛先: " station "\n\n"
               "下記のとおり被害を受けましたので届け出ます。\n\n"
               "1. 被害の種類: サイバー犯罪（" (ja-kind kind) "）\n"
               "2. 被害日時: " (get case ":case/occurred-at-text" "（年月日時を記入）") "\n"
               "3. 被害の概要:\n   " (get case ":case/narrative" "（事実を時系列で記入。別紙「被害状況報告書」参照）") "\n"
               "4. 被害額: 金 " (comma loss) " 円\n"
               "5. 証拠資料: 別紙「証拠目録」のとおり\n"
               "6. 相手方に関する情報: （判明している口座番号・URL・連絡先等を記入。別紙参照）\n\n"
               "上記に相違ありません。\n\n"
               "                          申告者署名 ____________________ 印\n\n"
               "※ これは被害者本人が提出するための下書きです。警察での受理・聴取の際に内容を確認・補正してください。")]
     (doc (get case ":case/id" "?") "damage-report" body station))))

(defn incident-statement
  "被害状況報告書(時系列). 供述の整理 — 本人作成、聴取の参考資料。"
  [case]
  (let [timeline (or (not-empty (get case ":case/timeline")) ["（出来事を起きた順に記入）"])
        lines (str/join "\n" (map-indexed (fn [i t] (str "  " (inc i) ". " t)) timeline))
        body (str
              (hdr case "被害状況報告書")
              "\n■ 経緯（時系列）\n" lines
              "\n\n■ 気づいた契機\n  " (str (get case ":case/discovery" "（どのように被害に気づいたか）"))
              "\n\n■ 現在の状況\n  " (str (get case ":case/current" "（口座凍結依頼済/パスワード変更済 等）"))
              "\n\n※ 本書面は被害者本人が事実を整理したものです。")]
    (doc (get case ":case/id" "?") "incident-statement" body "（警察・金融機関 提出用）")))

(defn evidence-index-doc
  "証拠目録. evidence の chain-of-custody hash を一覧化。"
  [case items]
  (let [rows (ev/index items)
        body-lines (->> rows
                        (map-indexed (fn [i r]
                                       (str "  " (inc i) ". [" (str/replace (get r ":evidence/kind") #"^:+" "") "] "
                                            "sha256=" (subs (get r ":evidence/sha256") 0 16) "… ref=" (get r ":evidence/envelope-ref"))))
                        (str/join "\n"))
        lines (if (str/blank? body-lines) "  （証拠なし）" body-lines)
        body (str
              (hdr case "証 拠 目 録")
              "\n各証拠は暗号化保管され、下記 sha256 により改変のないことを確認できます。\n\n"
              lines
              "\n\n※ 原本（暗号化）は被害者本人が保持します。")]
    (doc (get case ":case/id" "?") "evidence-index" body "（届出添付用）"
         {"evidence_count" (count rows)})))

(defn damage-calculation
  "被害額算定書. 内訳を明細化。"
  [case]
  (let [items (or (not-empty (get case ":case/loss-breakdown"))
                  [{":label" "被害額" ":jpy" (get case ":case/loss-jpy" 0)}])
        [total lines] (reduce (fn [[tot ls] it]
                                (let [jpy (to-int (get it ":jpy" 0))]
                                  [(+ tot jpy)
                                   (conj ls (str "  ・" (get it ":label" "項目") ": 金 " (comma jpy) " 円"))]))
                              [0 []] items)
        body (str
              (hdr case "被害額算定書")
              "\n■ 内訳\n" (str/join "\n" lines)
              "\n\n■ 合計: 金 " (comma total) " 円\n\n※ 領収書・取引明細は証拠目録に対応します。")]
    (doc (get case ":case/id" "?") "damage-calculation" body "（届出添付用）"
         {"total_jpy" total})))

(defn bank-freeze-request
  "銀行 不正送金 組戻し・口座凍結依頼 (振り込め詐欺救済法). 本人が送付する依頼書(G2)。"
  ([case] (bank-freeze-request case "（金融機関名）御中"))
  ([case bank]
   (let [body (str
               (hdr case "不正送金に関する組戻し・口座凍結のご依頼")
               "\n宛先: " bank "\n\n"
               "私の口座から、身に覚えのない送金（不正送金）が行われました。つきましては、\n"
               "振り込め詐欺救済法に基づき、振込先口座の凍結および組戻し手続きをお願いいたします。\n\n"
               "・被害日時: " (get case ":case/occurred-at-text" "（記入）") "\n"
               "・送金額: 金 " (comma (to-int (get case ":case/loss-jpy" 0))) " 円\n"
               "・振込先（判明分）: " (get case ":case/counterparty" "（口座番号・名義）") "\n"
               "・警察への被害届: （提出予定/受理番号を記入）\n\n"
               "                          依頼人署名 ____________________ 印\n\n"
               "※ 本依頼書は被害者本人が金融機関へ提出するものです。")]
     (doc (get case ":case/id" "?") "bank-freeze-request" body bank
          {"legal_basis" "振り込め詐欺救済法（:representative）"}))))

(defn platform-request
  "プラットフォーム凍結/復旧/開示依頼. 本人が送付(G2)。"
  ([case] (platform-request case "（プラットフォーム）abuse 窓口" "凍結・復旧"))
  ([case & {:keys [platform purpose] :or {platform "（プラットフォーム）abuse 窓口" purpose "凍結・復旧"}}]
   (let [body (str
               (hdr case (str "アカウントに関する" purpose "のご依頼"))
               "\n宛先: " platform "\n\n"
               "私の利用するアカウントが " (ja-kind (triage/classify case)) " の被害に遭いました。\n"
               "利用規約の不正利用条項に基づき、" purpose " の対応をお願いいたします。\n\n"
               "・対象アカウント: " (get case ":case/account-id" "（ID/URL を記入）") "\n"
               "・被害日時: " (get case ":case/occurred-at-text" "（記入）") "\n"
               "・添付: 被害状況報告書・証拠目録\n\n"
               "                          利用者署名 ____________________\n\n"
               "※ 本依頼書は利用者本人が送付するものです。")]
     (doc (get case ":case/id" "?") "platform-request" body platform
          {"legal_basis" "各社利用規約 abuse 条項（:representative）"}))))

(defn recovery-plan
  "アカウント復旧手順書. 本人が実行する self-help 手順(G2 :self-submit)。"
  ([case] (recovery-plan case "（サービス名）"))
  ([case & {:keys [service] :or {service "（サービス名）"}}]
   (let [steps ["別の安全な端末・ネットワークから対象サービスのパスワードをリセットする"
                "ログイン中の全セッションを失効（「すべてのデバイスからログアウト」）させる"
                "二段階認証を再設定し、認証アプリ/バックアップコードを更新する"
                "登録メールアドレス・電話番号・復旧用情報が改ざんされていないか確認し戻す"
                "連携アプリ・API トークンを見直し、見覚えのない連携を解除する"
                "同じパスワードを使い回した他サービスもすべて変更する"
                "復旧後、プラットフォーム abuse 窓口へ被害を報告し、再発防止設定を有効化する"]
         body (str
               (hdr case (str "アカウント復旧手順書（" service "）"))
               "\n■ 手順（上から順に本人が実行）\n"
               (str/join "\n" (map-indexed (fn [i s] (str "  " (inc i) ". " s)) steps))
               "\n\n※ 復旧操作は本人が行ってください（助 は手順を提供するのみ、代理ログインはしません）。")]
     (doc (get case ":case/id" "?") "recovery-plan" body "（本人実行）"
          {"service" service "steps" steps "support_role" ":self-submit"}))))

(defn assert-member-authored
  "G3 guard usable by callers/tests: a generated doc MUST be member-authored, signed, free, unpublished."
  [doc-map]
  (when (not= (get doc-map ":doc/authored-by") ":member")
    (throw (ex-info "G3: a generated document must be authored by :member (公文書偽造を排除)" {})))
  (when (not= (get doc-map ":doc/needs-member-signature") true)
    (throw (ex-info "G2/G7: a generated document must require the member's signature" {})))
  (when (not= (get doc-map ":doc/support-cost-jpy" 0) 0)
    (throw (ex-info "G1: a generated document is free (cost 0)" {})))
  (when (not= (get doc-map ":doc/published") false)
    (throw (ex-info "G9: a generated document is draft-only at R0 (published false)" {})))
  nil)
