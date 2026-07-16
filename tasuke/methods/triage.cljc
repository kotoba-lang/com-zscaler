(ns tasuke.methods.triage
  "triage.cljc — 助 (tasuke) intake triage. ADR-2606060900.
  1:1 Clojure port of `methods/triage.py`.

  THE HEART of the membrane. Given a consenting victim's intake it produces:
    - scam-kind ∈ ontology :scam-kinds       — a KIND for routing, NEVER a verdict (G4)
    - severity  ∈ {info, elevated, urgent, critical}
    - actions   — the time-ordered initial-action checklist
    - windows   — the FREE public windows (G5; never a paid counsel)
    - deadlines — statutory/practical clocks the victim must not miss

  Two charter invariants are enforced here:
    G1 — 助's support is FREE; support-cost-jpy is hard-wired to 0. A non-zero cost raises.
    G7 — a case needs explicit consent; an intake without it raises.

  House style: Python ':…' keyword strings stay strings; pure fns; closed-vocab/gate → ex-info.
  The _KEYWORDS / _WINDOWS ordering matches Python exactly (classify scans in order)."
  (:require [clojure.string :as str]))

;; ── closed vocab (mirror of the ontology :db/allowed) ───────────────────────────
(def SCAM-KINDS
  ["phishing" "unauthorized-transfer" "account-takeover" "support-scam" "romance-scam"
   "investment-scam" "ransomware" "impersonation" "fake-billing" "sns-fraud" "leak-extortion"])

(def SEVERITIES ["info" "elevated" "urgent" "critical"])
(def SUPPORT-ROLES ["guide" "draft-assist" "self-submit"])

;; G1 INVARIANT — 助 is free. This is the only cost the actor can express.
(def SUPPORT-COST-JPY 0)

;; keyword → scam-kind (deterministic R0 classifier; the Murakumo LLM refines at R1, never replaces).
(def ^:private KEYWORDS
  [["unauthorized-transfer" ["不正送金" "勝手に振込" "残高が減" "身に覚えのない出金" "atm" "振込"
                             "wire" "unauthorized transfer"]]
   ["account-takeover" ["乗っ取り" "ログインできない" "パスワード変更された" "二段階" "takeover"
                        "hijack" "locked out"]]
   ["phishing" ["フィッシング" "偽サイト" "偽メール" "sms" "ショートメール" "リンク" "phish"]]
   ["support-scam" ["サポート詐欺" "警告画面" "ウイルスに感染" "電話してください" "tech support"
                    "microsoft" "遠隔操作"]]
   ["romance-scam" ["ロマンス" "国際恋愛" "結婚" "投資を勧め" "romance"]]
   ["investment-scam" ["投資詐欺" "必ず儲かる" "暗号資産" "fx" "未公開株" "investment" "crypto"]]
   ["ransomware" ["ランサム" "暗号化された" "復号" "身代金" "ransom" "encrypted my"]]
   ["impersonation" ["なりすまし" "偽アカウント" "偽プロフィール" "impersonat" "fake account"]]
   ["fake-billing" ["架空請求" "未払い" "請求メール" "電子マネー" "ギフトカード" "fake bill"]]
   ["leak-extortion" ["流出" "晒す" "拡散する" "脅迫" "sextortion" "leak" "extort"]]
   ["sns-fraud" ["sns" "dm" "副業" "もうけ話" "x で" "instagram" "line で"]]])

;; scam-kind → ordered free public windows (G5). Codes resolve in the registry (seed).
;; A vector of [kind windows] pairs preserves the Python dict insertion order.
(def WINDOWS
  [["unauthorized-transfer" ["bank-direct" "no-and-bank-fund-recovery" "police-cyber-9110"]]
   ["account-takeover" ["platform-abuse-desk" "police-cyber-9110" "jpcert"]]
   ["phishing" ["antiphishing-council" "safeline" "police-cyber-9110"]]
   ["support-scam" ["consumer-188" "police-cyber-9110" "jpcert"]]
   ["romance-scam" ["police-cyber-9110" "consumer-188" "nccc"]]
   ["investment-scam" ["police-cyber-9110" "consumer-188" "nccc"]]
   ["ransomware" ["jpcert" "police-cyber-9110"]]
   ["impersonation" ["platform-abuse-desk" "police-cyber-9110" "safeline"]]
   ["fake-billing" ["consumer-188" "police-cyber-9110"]]
   ["leak-extortion" ["police-cyber-9110" "safeline" "jpcert"]]
   ["sns-fraud" ["platform-abuse-desk" "consumer-188" "police-cyber-9110"]]])

(defn- windows-for
  "WINDOWS.get(kind, ('police-cyber-9110',))"
  [kind]
  (or (some (fn [[k ws]] (when (= k kind) ws)) WINDOWS)
      ["police-cyber-9110"]))

(defn- lstrip-colon [s]
  (str/replace (str s) #"^:+" ""))

(defn- txt
  "_txt — joined narrative + scam-kind + title, lower-cased."
  [intake]
  (-> (str/join " " (map #(str (get intake % "")) [":case/narrative" ":case/scam-kind" ":case/title"]))
      (str/lower-case)))

(defn support-cost-jpy
  "G1 全て無料 INVARIANT — there is no other answer. 助's support always costs 0."
  ([] SUPPORT-COST-JPY)
  ([_intake] SUPPORT-COST-JPY))

(defn- to-int
  "int(x or 0) — Python truthiness: nil/false/0/\"\" → 0."
  [v]
  (cond
    (nil? v) 0
    (false? v) 0
    (number? v) (long v)
    (string? v) (if (str/blank? v) 0 (try (Long/parseLong (str/trim v)) (catch Exception _ 0)))
    :else 0))

(defn classify
  "Return the scam KIND (G4 — for routing, not a verdict). Honors an explicit :case/scam-kind."
  [intake]
  (let [explicit (-> (str (get intake ":case/scam-kind" "")) (lstrip-colon) (str/lower-case))]
    (if (some #{explicit} SCAM-KINDS)
      explicit
      (let [blob (txt intake)]
        (or (some (fn [[kind kws]]
                    (when (some #(str/includes? blob %) kws) kind))
                  KEYWORDS)
            "sns-fraud")))))           ;; safe generic default → still routed to a free window

(defn- truthy?
  "Python bool(x) for the values that appear here (nil/false → false, else true)."
  [v]
  (cond (nil? v) false (false? v) false (= v 0) false (= v "") false :else true))

(defn assess-severity
  [intake kind]
  (let [loss (to-int (get intake ":case/loss-jpy" 0))
        ongoing (truthy? (get intake ":case/ongoing" false))]
    (cond
      (and (= kind "unauthorized-transfer") (> loss 0)) "critical"
      (or (#{"ransomware" "leak-extortion"} kind) ongoing) (if (= loss 0) "urgent" "critical")
      (or (>= loss 100000) (#{"account-takeover" "investment-scam"} kind)) "urgent"
      (or (> loss 0) (#{"phishing" "support-scam" "romance-scam" "fake-billing"} kind)) "elevated"
      :else "info")))

(defn initial-actions
  "The first-response checklist, evidence-first then containment then report."
  [kind]
  (let [base ["まず証拠を保全(スクショ・URL・メール全文ヘッダ・取引履歴を保存。改変しない)"]
        by-kind {"unauthorized-transfer"
                 ["ただちに口座のある金融機関に電話し、不正送金の申告と口座凍結・組戻しを依頼(振り込め詐欺救済法)"
                  "ネットバンキングのパスワードを変更し、追加の不正送金を止める"
                  "被害届の下書きを作成して最寄りの警察署/サイバー犯罪相談窓口(#9110)へ"]
                 "account-takeover"
                 ["他端末から該当サービスのパスワードをリセットし、攻撃者のセッションを失効"
                  "二段階認証を再設定し、登録メール・電話番号が書き換えられていないか確認"
                  "アカウント復旧手順に沿って復旧 → プラットフォーム abuse 窓口へ凍結/復旧依頼"]
                 "phishing"
                 ["入力してしまった ID/パスワード/カード番号を直ちに変更・停止"
                  "フィッシング対策協議会・セーフライン へ URL を通報"]
                 "support-scam"
                 ["遠隔操作ソフトを入れた場合はネット切断のうえアンインストール、パスワード全変更"
                  "電子マネー/ギフトカードで支払った場合は番号と購入レシートを保全"]
                 "ransomware"
                 ["感染端末をネットワークから隔離(電源は切らずLAN/Wi-Fi遮断)"
                  "身代金は支払わず JPCERT/CC へ相談、復号ツールの有無を確認"]
                 "leak-extortion"
                 ["相手の要求に応じず、やり取りを保全。送金・画像送付をしない"
                  "拡散先がある場合はセーフライン/各プラットフォームへ削除通報"]}
        tail ["被害状況報告書(時系列)・証拠目録・被害額算定書を作成して届出に添える"
              "無料の公的窓口(警察 #9110 / 消費者ホットライン 188 / NCCC)に相談"]]
    (vec (concat base (get by-kind kind []) tail))))

(defn deadlines
  "Practical/statutory clocks the victim must not miss (informational, G4 — not legal advice)."
  [kind]
  (cond-> []
    (#{"unauthorized-transfer" "phishing" "account-takeover"} kind)
    (conj "クレジットカード不正利用は約款上おおむね60日以内の申告で補償対象になりやすい — 至急申告")
    (#{"fake-billing" "support-scam" "romance-scam" "investment-scam"} kind)
    (conj "通信販売の契約はクーリングオフ対象外のことが多い — ただし不実告知等は取消し得る(消費生活センター 188 で確認)")
    (= kind "unauthorized-transfer")
    (conj "預金口座の凍結は早いほど組戻し成功率が上がる — 認知後ただちに銀行へ")))

(defn triage
  "Validate (G1/G7) then classify + score + route a victim intake. Raises on a hard gate.

  Returns a triage map. It NEVER returns a legal verdict (G4) — only a KIND + severity + the
  free public windows + the self-help action checklist."
  [intake]
  (when-not (truthy? (get intake ":case/consent" false))
    (throw (ex-info "G7: a support case is opened only with the victim's explicit consent" {})))
  (let [cost (to-int (get intake ":case/support-cost-jpy" 0))]
    (when (not= cost SUPPORT-COST-JPY)
      (throw (ex-info (str "G1 全て無料: support cost must be 0 (cash≡0); got " cost) {}))))
  (when (truthy? (get intake ":case/server-held-key" false))
    (throw (ex-info "G7/no-server-key: server-held-key must be false (ADR-2605231525)" {})))
  (let [kind (classify intake)
        sev (assess-severity intake kind)]
    {":triage/case" (get intake ":case/id" "?")
     ":triage/scam-kind" (str ":" kind)
     ":triage/severity" (str ":" sev)
     ":triage/support-cost-jpy" SUPPORT-COST-JPY        ;; always 0 (G1)
     ":triage/windows" (mapv #(str ":" %) (windows-for kind))
     ":triage/actions" (initial-actions kind)
     ":triage/deadlines" (deadlines kind)
     ":triage/paid-referral" false}))                   ;; G5 — never a paid counsel
