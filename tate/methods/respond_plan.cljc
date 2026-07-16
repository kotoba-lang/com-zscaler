(ns tate.methods.respond-plan
  "tate 盾 — legal-procedure response planner (個人としての対応支援; dry-run only).
  1:1 Clojure port of `methods/respond_plan.py` (ADR-2606112301 + worldwide ADR-2606112400).

  Classifies a notice the member RECEIVED against the jurisdiction-keyed procedure registry
  and builds a response plan: DISCLOSED deadline rules, response options, a self-submit
  checklist, and referral triggers.

  CONSTITUTIONAL (read before any change):
    G3 — UPL. Representation is structurally unrepresentable — make-option THROWS on
      :representation — in EVERY jurisdiction. The member decides and submits THEMSELVES.
    G4 — deadline honesty. tate NEVER computes a calendar date. Every deadline is the
      DISCLOSED rule text + statutory anchor + verify-service-date true.
    G6 — fake-notice (架空請求) guard, generalized via :proc/genuine-channels. Court
      vocabulary on any other channel is :suspected-fake: REFUSE any contact-sender step,
      preserve evidence, route to the jurisdiction's fake-help directory.
    G7 — referral-forward. High-stakes shapes carry the jurisdiction's referral directory.
    G10 — jurisdiction honesty. Procedures never cross jurisdictions; a notice from a
      jurisdiction outside jurisdictions.edn degrades to :unknown-jurisdiction.

  House style: ':…' strings stay strings; pure fns; I/O at #?(:clj) edges; gate → ex-info.
  Portable .cljc."
  (:require [clojure.string :as str]
            [tate.methods.edn :as edn]
            [tate.methods.terms-scan :as terms]))

;; fake-guard trip-wires (G6). The vocabulary is DERIVED — every procedure's trigger
;; keywords are automatically trip-wires — plus a small curated set of generic scam words.
(def curated-tripwires
  ["法院" "법원" "강제집행" "lawsuit" "差押" "garnishment"
   "Pfändung" "Insolvenz" "Betreibung" "assignation" "juzgado"])

(defn court-vocabulary
  "Curated generics + the union of ALL procedure trigger keywords (derived, G6)."
  [procs]
  (into (vec curated-tripwires)
        (for [p procs k (get p ":proc/trigger-keywords" [])] k)))

(def generic-referrals
  ["local bar association / legal aid" "認定司法書士 (JPのみ・簡裁140万円以下)"])

(def proc-referral-always #{"proc:sojou" "proc:us-summons"})  ;; 本訴/civil suit — G7

;; ── default-path loaders (#?(:clj) edge) ──────────────────────────────────
(defn- unblob
  "data/procedure-registry.edn is stored Datomic/Datascript-queryable (ADR-2607100030
  fan-out, Phase 4): each proc entity carries :db/id and its two vector-of-map fields
  (:proc/deadline-rules, :proc/options) are pr-str'd into a blob string (Datomic has no
  nested-map valueType). Unblob at load time via tate's OWN edn/read-edn (not
  clojure.edn) so the reconstituted value keeps the SAME string-keyword-as-string
  shape the rest of this namespace already expects -- every call site below
  (classify/build-plan/coverage_report/site_gen/case_actors_gen, all of which load
  procs exclusively through this fn) is unchanged."
  [v]
  (if (string? v) (edn/read-edn v) v))

#?(:clj
   ;; Unblobbing 181 procs with tate's regex-tokenizing reader costs ~1s/call; load-procs
   ;; is called from ~70 deftests across 4 test namespaces (+ every method that needs
   ;; procs), so an unmemoized reload-per-call would blow up run_tests.sh from seconds
   ;; to 1-2 minutes+. Cache by path (process-local; each bb invocation is a fresh
   ;; process, so this cannot serve stale data across runs).
   (def ^:private procs-cache (atom {})))

#?(:clj
   (defn load-procs
     ([] (load-procs (clojure.java.io/file (terms/here) "data" "procedure-registry.edn")))
     ([path]
      (let [k (str path)]
        (or (get @procs-cache k)
            (let [procs (->> (edn/load-edn path)
                              (filter #(contains? % ":proc/id"))
                              (map (fn [p] (cond-> p
                                             (contains? p ":proc/deadline-rules") (update ":proc/deadline-rules" unblob)
                                             (contains? p ":proc/options") (update ":proc/options" unblob))))
                              vec)]
              (swap! procs-cache assoc k procs)
              procs))))))

#?(:clj
   (defn load-jurisdictions
     ([] (load-jurisdictions (clojure.java.io/file (terms/here) "data" "jurisdictions.edn")))
     ([path]
      (->> (edn/load-edn path)
           (filter #(contains? % ":juris/id"))
           (reduce (fn [m j] (assoc m (get j ":juris/id") j)) {})))))

#?(:clj
   (defn load-us-states
     ([] (load-us-states (clojure.java.io/file (terms/here) "data" "us-states.edn")))
     ([path]
      (->> (edn/load-edn path)
           (filter #(contains? % ":state/id"))
           (reduce (fn [m s] (assoc m (get s ":state/id") s)) {})))))

(defn make-option
  "The only option constructor. Representation is unrepresentable (G3) — globally."
  [opt]
  (when (= (get opt ":opt/kind") ":representation")
    (throw (ex-info (str "G3/UPL: representation is unrepresentable in tate "
                         "(弁護士法72条 / state UPL / LSA 2007 / RDG)")
                    {:opt opt})))
  {"id" (get opt ":opt/id") "kind" (get opt ":opt/kind") "label" (get opt ":opt/label")
   "mode" "dry-run" "submitted_by" "member"})

(defn- to-double [v]
  (cond (nil? v) nil
        (number? v) (double v)
        :else (try (Double/parseDouble (str v)) (catch Exception _ nil))))

(defn- claim [notice]
  ;; float(notice.get(":notice/claim-jpy") or notice.get(":notice/claim-amount") or 0)
  (let [pick (fn [k] (let [x (get notice k)] (when (and x (not (and (number? x) (zero? x)))) x)))]
    (double (or (pick ":notice/claim-jpy") (pick ":notice/claim-amount") 0))))

(defn classify
  "(proc, status) — :genuine | :suspected-fake | :unknown | :unknown-jurisdiction.
  Returns [proc status]."
  ([notice procs] (classify notice procs (load-jurisdictions)))
  ([notice procs jurisdictions]
   (let [juris (get notice ":notice/jurisdiction" ":jp")
         text (str/lower-case (get notice ":notice/text" ""))
         channel (get notice ":notice/channel")]
     (if-not (contains? jurisdictions juris)
       [nil ":unknown-jurisdiction"]                       ;; G10
       (let [matched (some (fn [p]
                             (when (and (= (get p ":proc/jurisdiction" ":jp") juris)  ;; G10
                                        (some #(str/includes? text (str/lower-case %))
                                              (get p ":proc/trigger-keywords")))
                               p))
                           procs)]
         (if (nil? matched)
           (if (and (some #(str/includes? text (str/lower-case %)) (court-vocabulary procs))
                    (contains? #{":sms" ":email" ":mail"} channel))
             [nil ":suspected-fake"]
             [nil ":unknown"])
           (let [genuine (get matched ":proc/genuine-channels" [])
                 genuine-set (set genuine)]
             (cond
               ;; G6 hardening: a digital channel is NEVER genuine unless explicitly declared
               (and (contains? #{":sms" ":email"} channel) (not (contains? genuine-set channel)))
               [matched ":suspected-fake"]
               :else
               (let [formal-required (boolean (some #(not= % ":mail") genuine))]
                 (if (and formal-required (not (contains? genuine-set channel)))
                   [matched ":suspected-fake"]              ;; G6
                   [matched ":genuine"]))))))))))

(defn- comma
  "Python f'{n:,}' — group integer digits with commas."
  [n]
  (let [s (str (long n))
        neg (str/starts-with? s "-")
        digits (if neg (subs s 1) s)
        grouped (->> (reverse (vec digits))
                     (partition-all 3)
                     (map #(apply str (reverse %)))
                     reverse
                     (str/join ","))]
    (str (when neg "-") grouped)))

(defn build-plan
  ([notice procs] (build-plan notice procs (load-jurisdictions)))
  ([notice procs jurisdictions]
   (let [juris-id (get notice ":notice/jurisdiction" ":jp")
         juris (get jurisdictions juris-id {})
         [proc status] (classify notice procs jurisdictions)
         base {"notice" (get notice ":notice/id")
               "notice_label" (get notice ":notice/label" (get notice ":notice/id"))
               "jurisdiction" juris-id
               "channel" (get notice ":notice/channel")
               "proc" (when proc (get proc ":proc/id"))
               "status" status
               "deadlines" [] "options" [] "steps" [] "referrals" []
               "mode" "dry-run"}]
     (cond
       (= status ":suspected-fake")
       (assoc base
              "steps"
              [{"verb" "do-not-contact-sender"
                "detail" (str "記載の電話番号・URL・口座に一切接触しない "
                              "(never call/click/pay the sender)") "mode" "dry-run"}
               {"verb" "preserve-evidence"
                "detail" "現物/スクリーンショットを保全 (日時・差出経路)" "mode" "dry-run"}
               {"verb" "verify-with-court"
                "detail" (str "実在確認は記載先ではなく公的窓口の公開番号で行う "
                              "(genuine service: " (get juris ":juris/service-note" "—") ")")
                "mode" "dry-run"}]
              "referrals"
              (vec (get juris ":juris/fake-help"
                        ["tasuke 助 (サイバー犯罪被害支援)" "local police"])))

       (= status ":unknown-jurisdiction")
       (assoc base
              "steps"
              [{"verb" "declare-uncovered"
                "detail" (str "管轄 " juris-id " は tate 未カバー "
                              "(coverage_report.py 参照) — 現地法を推測しない") "mode" "dry-run"}
               {"verb" "preserve-evidence"
                "detail" "文書全文・封筒・送達方法を記録" "mode" "dry-run"}]
              "referrals" (vec generic-referrals))

       (= status ":unknown")
       (assoc base
              "steps" [{"verb" "collect-more"
                        "detail" "文書全文・封筒・送達方法を記録して再分類" "mode" "dry-run"}]
              "referrals" (vec (get juris ":juris/referrals" generic-referrals)))

       :else  ;; :genuine — DISCLOSED rules verbatim (G4)
       (let [deadlines0 (vec (for [dl (get proc ":proc/deadline-rules" [])]
                               {"label" (get dl ":dl/label") "rule" (get dl ":dl/rule")
                                "anchor" (get dl ":dl/anchor")
                                "critical" (boolean (get dl ":dl/critical"))
                                "verify_service_date" (boolean (get dl ":dl/verify-service-date"))}))
             ;; catastrophic-if-missed deadlines surface first; stable sort keeps registry order
             deadlines1 (vec (sort-by #(if (get % "critical") 0 1) deadlines0))
             deadlines2
             (if (= juris-id ":us")
               (let [state (get (load-us-states) (get notice ":notice/us-state"))]
                 (conj deadlines1
                       (if state
                         {"label" (str "州規則 (" (get state ":state/label") ")")
                          "rule" (str (get state ":state/answer-rule")
                                      " · small claims 上限 $" (comma (get state ":state/small-claims-usd")))
                          "anchor" (get state ":state/answer-anchor")
                          "critical" false "verify_service_date" true}
                         {"label" "州規則 (州不明)"
                          "rule" (str "州が特定できないため州規則は提示しない — サモンズ記載の期限と"
                                      "当該州の rules of civil procedure を必ず確認 (州差が本体)")
                          "anchor" "—" "critical" false "verify_service_date" true})))
               deadlines1)
             options (vec (map make-option (get proc ":proc/options" [])))
             steps [{"verb" "verify-service-date" "detail" "送達/受領日を自分で確認 (期限の起算点)" "mode" "dry-run"}
                    {"verb" "draft-response" "detail" "選んだ選択肢の書面雛形を作成 (member が記入・確定)" "mode" "dry-run"}
                    {"verb" "self-submit" "detail" "member 本人が提出 (郵送/窓口/オンライン)" "mode" "dry-run"}
                    {"verb" "record-to-ledger" "detail" "対応と期限を kotoba Datom log に記録 (G8)" "mode" "dry-run"}]
             referrals0 (vec (get proc ":proc/refer-when" []))
             refer-over (or (to-double (get juris ":juris/refer-over-amount" 0)) 0.0)
             cl (claim notice)
             claim-cur (or (get notice ":notice/claim-currency")
                           (when (contains? notice ":notice/claim-jpy") "JPY"))
             juris-cur (get juris ":juris/refer-over-currency")
             currency-mismatch (boolean (and (> cl 0) claim-cur juris-cur (not= claim-cur juris-cur)))
             over-line (boolean (and (not (zero? refer-over)) (not currency-mismatch) (> cl refer-over)))
             referrals
             (if (or over-line currency-mismatch (contains? proc-referral-always (get proc ":proc/id")))
               (-> referrals0
                   (cond-> currency-mismatch
                     (conj (str "請求が外貨建て (" claim-cur ") — 金額比較不能のため保守的に専門家照会")))
                   (into (get juris ":juris/referrals" generic-referrals)))   ;; G7
               referrals0)]
         (assoc base
                "deadlines" deadlines2
                "options" options
                "steps" steps
                "referrals" referrals))))))

(defn plans
  [notices procs]
  (let [jurisdictions (load-jurisdictions)]
    (vec (for [n notices] (build-plan n procs jurisdictions)))))

(defn report
  [ps]
  (let [L (transient ["# tate 盾 — response plans (dry-run; member self-submit — G3 UPL, all jurisdictions)" ""])]
    (doseq [p ps]
      (conj! L (str "## " (get p "notice_label") " [" (get p "jurisdiction") "] — " (get p "status")
                    (if (get p "proc") (str " (" (get p "proc") ")") "")))
      (doseq [d (get p "deadlines")]
        (let [mark (if (get d "critical") "⚠ " "")]
          (conj! L (str "- " mark "期限ルール [" (get d "label") "]: " (get d "rule")
                        " (" (get d "anchor") ") — 送達日は自分で確認"))))
      (doseq [o (get p "options")]
        (conj! L (str "- 選択肢: " (get o "label"))))
      (doseq [[i s] (map-indexed (fn [i s] [(inc i) s]) (get p "steps"))]
        (conj! L (str i ". [" (get s "verb") "] " (get s "detail"))))
      (when (seq (get p "referrals"))
        (conj! L (str "- 照会先: " (str/join ", " (get p "referrals")))))
      (conj! L ""))
    (str (str/join "\n" (persistent! L)) "\n")))
