(ns tate.methods.case-actors-gen
  "tate 盾 — case-actor generator (wave 41).
  1:1 Clojure port of `methods/case_actors_gen.py`.

  1 手続き (case) = 1 keyless mirror-actor (`did:web:etzhayyim.com:actor:tate-<case>`) in
  the entity-as-actor (ADR-2606042330) / actor-profile (ADR-2606013800) form. Each case
  actor = 5 files (did.json / profile.json / case.json / checklist.md / template.md), plus
  /actor/tate/cases.json — the index.

  CONSTITUTIONAL: 非裁定/UPL disclaimer in case.json + checklist.md; consultation = each
  jurisdiction's public/free directory; no server key / send capability (static files only).

  House style: data maps stay string-keyed; ':…' keyword strings stay strings; file I/O
  only behind #?(:clj …). The Python __main__ demo printer is omitted.

  Parity: json.dumps(obj, ensure_ascii=False, indent=N) — faithful indented encoder
  (key insertion order preserved via array-map); the tests json.loads the output back."
  (:require [clojure.string :as str]
            [tate.methods.terms-scan :as ts]
            [tate.methods.respond-plan :as rp]))

(def ROOT-DEFAULT "https://etzhayyim.com")

(def DISCLAIMER
  (str "一般的な法情報であり個別の法的助言ではありません (非裁定/UPL)。"
       "期限の起算点 (送達日) は必ず自分で確認し, 重要な判断は記載の無料相談窓口・専門家へ。"
       "法令は改正されます — アンカーは現行条文で要確認。"))

;; ── json.dumps(obj, ensure_ascii=False, indent=N) ────────────────────────────
(defn- json-escape-utf8 [s]
  (str/escape s {\" "\\\"" \\ "\\\\"
                 \backspace "\\b" \tab "\\t" \newline "\\n" \formfeed "\\f" \return "\\r"}))

(defn- json-scalar [v]
  (cond
    (string? v) (str "\"" (json-escape-utf8 v) "\"")
    (boolean? v) (if v "true" "false")
    (nil? v) "null"
    (integer? v) (str v)
    (number? v) (str v)
    :else (str "\"" (json-escape-utf8 (str v)) "\"")))

(defn- json-indent
  "Python json.dumps with indent=n. depth = current nesting level."
  [v n depth]
  (let [pad (apply str (repeat (* n (inc depth)) " "))
        close-pad (apply str (repeat (* n depth) " "))]
    (cond
      (map? v)
      (if (empty? v)
        "{}"
        (str "{\n"
             (str/join ",\n"
                       (map (fn [[k val]]
                              (str pad (json-scalar (str k)) ": " (json-indent val n (inc depth))))
                            v))
             "\n" close-pad "}"))
      (sequential? v)
      (if (empty? v)
        "[]"
        (str "[\n"
             (str/join ",\n" (map (fn [x] (str pad (json-indent x n (inc depth)))) v))
             "\n" close-pad "]"))
      :else (json-scalar v))))

(defn- json-dumps-indent [v n] (json-indent v n 0))

(defn slug [proc-id]
  (str "tate-" (second (str/split proc-id #":" 2))))

(defn did-doc [p root]
  (let [s (slug (get p ":proc/id"))
        did (str "did:web:etzhayyim.com:actor:" s)]
    (array-map
     "@context" ["https://www.w3.org/ns/did/v1"
                 "https://w3id.org/security/suites/jws-2020/v1"]
     "id" did
     "alsoKnownAs" []
     "verificationMethod" []
     "service" [(array-map "id" (str did "#case-data") "type" "EtzhayyimCaseData"
                           "serviceEndpoint" (str root "/actor/" s "/case.json"))
                (array-map "id" (str did "#checklist") "type" "EtzhayyimCaseChecklist"
                           "serviceEndpoint" (str root "/actor/" s "/checklist.md"))
                (array-map "id" (str did "#template") "type" "EtzhayyimCaseTemplate"
                           "serviceEndpoint" (str root "/actor/" s "/template.md"))
                (array-map "id" (str did "#guide") "type" "EtzhayyimCaseGuide"
                           "serviceEndpoint" (str root "/tate/"
                                                  (subs (get p ":proc/jurisdiction" ":jp") 1) ".html"))]
     "_meta" (array-map
              "adr" ["2606112301" "2606112400" "2606122000"]
              "source" "tate procedure-registry" "kind" "case-mirror"
              "parent" "did:web:etzhayyim.com:actor:tate"
              "track" (get p ":proc/track" ":civil")
              "jurisdiction" (get p ":proc/jurisdiction" ":jp")
              "note" (str "verificationMethod empty — keyless case mirror; did:web trust root = "
                          "TLS (no server-minted key, ADR-2605231525)")))))

(defn profile [p juris root]
  (let [s (slug (get p ":proc/id"))
        j (get juris (get p ":proc/jurisdiction" ":jp"))]
    (array-map
     "did" (str "did:web:etzhayyim.com:actor:" s)
     "handle" (str s ".etzhayyim.com")
     "displayName" (str (get p ":proc/label") " — case actor")
     "description" (str (get j ":juris/label") " の『" (get p ":proc/label") "』を受け取った人のための "
                        "case actor。期限ルール・防御選択肢・無料相談先のデータ DL と相談導線。"
                        " " DISCLAIMER)
     "performerType" "system" "uiType" "document"
     "labels" [] "viewer" (array-map)
     "_etzhayyim"
     (array-map
      "kind" "case-mirror" "parent" "tate"
      "track" (get p ":proc/track" ":civil")
      "jurisdiction" (get p ":proc/jurisdiction" ":jp")
      "didDocument" (str root "/actor/" s "/did.json")
      "downloads" (array-map
                   "case_json" (str root "/actor/" s "/case.json")
                   "checklist_md" (str root "/actor/" s "/checklist.md")
                   "template_md" (str root "/actor/" s "/template.md")
                   "jurisdiction_guide" (str root "/tate/"
                                             (subs (get p ":proc/jurisdiction" ":jp") 1) ".html"))
      "consultation" (array-map
                      "free_referrals" (get j ":juris/referrals")
                      "fraud_help" (get j ":juris/fake-help")
                      "yoro_convo" (str "PLANNED — yoro convo chat 経由の相談は operator/Council "
                                        "ゲートの R+ レグ (現状は上記の公的・無料窓口へ)"))))))

(defn case-json [p juris]
  (let [j (get juris (get p ":proc/jurisdiction" ":jp"))]
    (array-map
     "disclaimer" DISCLAIMER
     "case" (get p ":proc/id") "label" (get p ":proc/label")
     "jurisdiction" (get p ":proc/jurisdiction" ":jp")
     "jurisdiction_label" (get j ":juris/label")
     "track" (get p ":proc/track" ":civil")
     "genuine_channels" (get p ":proc/genuine-channels" [])
     "service_note" (get j ":juris/service-note")
     "deadlines" (mapv (fn [d]
                         (array-map "label" (get d ":dl/label") "rule" (get d ":dl/rule")
                                    "anchor" (get d ":dl/anchor")
                                    "critical" (boolean (get d ":dl/critical"))
                                    "verify_service_date" true))
                       (get p ":proc/deadline-rules" []))
     "options" (mapv (fn [o]
                       (array-map "id" (get o ":opt/id") "kind" (get o ":opt/kind")
                                  "protective" (boolean (get o ":opt/protective"))
                                  "label" (get o ":opt/label")))
                     (get p ":proc/options" []))
     "referrals" (get p ":proc/refer-when" [])
     "jurisdiction_referrals" (get j ":juris/referrals")
     "fraud_help" (get j ":juris/fake-help")
     "verify_current_law" true)))

(defn checklist-md [p juris]
  (let [j (get juris (get p ":proc/jurisdiction" ":jp"))
        L (transient
           [(str "# " (get p ":proc/label") " — 自己対応チェックリスト") ""
            (str "> " DISCLAIMER) ""
            (str "本物の書類の経路: " (get j ":juris/service-note"))
            (str "SMS/メールのみの『裁判所』通知は接触せず: " (str/join " / " (get j ":juris/fake-help"))) ""
            "## 期限 (起算点=送達日を自分で確認)"])]
    (doseq [d (get p ":proc/deadline-rules" [])]
      (let [mark (if (get d ":dl/critical") "⚠ " "- ")]
        (conj! L (str mark "**" (get d ":dl/label") "**: " (get d ":dl/rule")
                      " (" (get d ":dl/anchor") " — 要改正確認)"))))
    (conj! L "")
    (conj! L "## 選択肢 (member 本人が決めて提出する — 代理はしない)")
    (doseq [o (get p ":proc/options" [])]
      (let [star (if (get o ":opt/protective") "🛡 " "- ")]
        (conj! L (str star (get o ":opt/label")))))
    (conj! L "")
    (conj! L "## 相談先 (無料/公的)")
    (doseq [r (concat (get p ":proc/refer-when" []) (get j ":juris/referrals"))]
      (conj! L (str "- " r)))
    (str (str/join "\n" (persistent! L)) "\n")))

(def OFFICIAL-FORM-HINTS
  ["Form" "様式" "Formular" "formulaire" "FL-120" "용지" "用紙" "Official" "公式"])

(defn template-md [p juris]
  (let [j (get juris (get p ":proc/jurisdiction" ":jp"))
        subs* (filterv #(= (get % ":opt/kind") ":self-submit") (get p ":proc/options" []))]
    (if (empty? subs*)
      (let [L [(str "# " (get p ":proc/label") " — 提出書面の雛形 (記入式)") ""
               (str "> " DISCLAIMER) ""
               "> この雛形は member 本人が【 】を埋めて確定・提出するための構造テンプレートです。"
               ""
               "この手続きは出頭・相談・確認が中心で、定型の提出書面はありません。"
               "checklist.md の手順と相談先に従ってください。"]]
        (str (str/join "\n" L) "\n"))
      (let [L (transient
               [(str "# " (get p ":proc/label") " — 提出書面の雛形 (記入式)") ""
                (str "> " DISCLAIMER) ""
                "> この雛形は member 本人が【 】を埋めて確定・提出するための構造テンプレートです。"
                ""])
            official (filterv (fn [o]
                                (some #(str/includes? (str/lower-case (get o ":opt/label"))
                                                      (str/lower-case %))
                                      OFFICIAL-FORM-HINTS))
                              subs*)]
        (when (seq official)
          (conj! L "## まず公式様式を確認")
          (doseq [o official]
            (conj! L (str "- " (get o ":opt/label") " — **公式様式が存在します。自由書式より様式を優先**してください。")))
          (conj! L ""))
        (conj! L "## 自由書式の構造 (様式がない/補助書面の場合)")
        (conj! L "")
        (conj! L "```")
        (conj! L (str "【提出先】 " (first (str/split (get (first subs*) ":opt/label") #" \(" 2))))
        (conj! L (str "【件名】   " (get p ":proc/label") " に対する "
                      (str/trim (first (str/split (get (first subs*) ":opt/label") #"を")))))
        (conj! L "")
        (conj! L "【自分の氏名・住所・連絡先】")
        (conj! L "【相手方/事件の特定】 事件番号・通知の日付: 【受領した書面の番号と日付】")
        (conj! L "")
        (conj! L "1. 私は【通知を受領した日 — 期限の起算点】に標記の通知を受領しました。")
        (conj! L (str "2. 私は次のとおり申し立てます: 【" (get (first subs*) ":opt/label") "】"))
        (conj! L "3. 理由: 【簡潔に。理由不要の手続き (異議のみで足りる類型) は省略可 —")
        (conj! L "   checklist.md の期限ルール参照】")
        (conj! L "4. 添付書類: 【受領通知の写し・証拠など】")
        (conj! L "")
        (conj! L "【日付】 【署名】")
        (conj! L "```")
        (conj! L "")
        (conj! L "## 提出前チェック")
        (doseq [d (get p ":proc/deadline-rules" [])]
          (let [mark (if (get d ":dl/critical") "⚠ " "- ")]
            (conj! L (str mark (get d ":dl/label") ": " (get d ":dl/rule") " (" (get d ":dl/anchor") ")"))))
        (conj! L (str "- 提出方法・控えの保管。不安があれば: "
                      (str/join " / " (take 2 (get j ":juris/referrals")))))
        (str (str/join "\n" (persistent! L)) "\n")))))

#?(:clj
   (defn generate
     "Write all case actors + cases.json under actor-dir; returns the index vector."
     ([actor-dir] (generate actor-dir ROOT-DEFAULT))
     ([actor-dir root]
      (let [procs (rp/load-procs)
            juris (rp/load-jurisdictions)
            actor-dir (clojure.java.io/file actor-dir)
            index
            (mapv (fn [p]
                    (let [s (slug (get p ":proc/id"))
                          d (clojure.java.io/file actor-dir s)]
                      (.mkdirs d)
                      (spit (clojure.java.io/file d "did.json")
                            (str (json-dumps-indent (did-doc p root) 2) "\n"))
                      (spit (clojure.java.io/file d "profile.json")
                            (str (json-dumps-indent (profile p juris root) 2) "\n"))
                      (spit (clojure.java.io/file d "case.json")
                            (str (json-dumps-indent (case-json p juris) 2) "\n"))
                      (spit (clojure.java.io/file d "checklist.md") (checklist-md p juris))
                      (spit (clojure.java.io/file d "template.md") (template-md p juris))
                      (array-map "slug" s "did" (str "did:web:etzhayyim.com:actor:" s)
                                 "label" (get p ":proc/label")
                                 "jurisdiction" (get p ":proc/jurisdiction" ":jp")
                                 "track" (get p ":proc/track" ":civil"))))
                  procs)
            tate-dir (clojure.java.io/file actor-dir "tate")]
        (.mkdirs tate-dir)
        (spit (clojure.java.io/file tate-dir "cases.json")
              (str (json-dumps-indent
                    (array-map "disclaimer" DISCLAIMER "count" (count index) "cases" index) 1)
                   "\n"))
        index))))
