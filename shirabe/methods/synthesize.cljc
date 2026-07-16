(ns shirabe.methods.synthesize
  "shirabe 調べ — answer synthesis (the gemma4 leg of the ReAct loop). ADR-2606131600.

  Builds a citation-grounded prompt from the retrieved evidence and asks a Murakumo-fleet
  gemma4 to answer FROM THE SOURCES ONLY. Returns the cited answer + which sources it leaned
  on + the model id.

  CONSTITUTIONAL:
    G2 — Murakumo-only inference (ADR-2605215000). The inference endpoint is checked against
      `allowed-infer-hosts`: the LiteLLM gateway (127.0.0.1:4000), the EVO-X2 LAN box
      (192.168.1.70:4000), and a per-node Ollama (127.0.0.1:11434) running gemma 4 E4B QAT
      (Murakumo-conformant per meisai ADR-2606122400). Any other host — every commercial LLM
      API — raises (validate-host!). This is structural, not a comment.
    G4 — citation-grounded, non-fabricating. The system prompt forbids any claim not supported
      by a numbered source and tells the model to answer `INSUFFICIENT` when the sources do not
      suffice (surfaced honestly as :insufficient).
    G7 — the loop does no network I/O by default. `infer` is REQUIRED and injected; the live
      adapter (live.clj make-infer) is an explicit operator/member leg.

  An `infer` is any fn `(fn [prompt] -> string)`; attach {:model-id _} as metadata."
  (:require [clojure.string :as str]))

;; ── G2: the ONLY inference hosts a religious-corp caller may use (ADR-2605215000).
(def allowed-infer-hosts
  #{"127.0.0.1:4000" "localhost:4000"        ;; Murakumo LiteLLM gateway
    "192.168.1.70:4000"                       ;; EVO-X2 LAN
    "127.0.0.1:11434" "localhost:11434"})     ;; per-node Ollama (gemma 4 E4B QAT)

(def ^:private sys-ja
  (str "あなたは etzhayyim 調べ — 出典に厳密な調査アシスタントです。"
       "以下の【出典】だけを根拠に、日本語で簡潔に答えてください。\n"
       "厳守事項:\n"
       "1. 各事実の直後に [1] [2] のように出典番号を付ける。\n"
       "2. 出典に無い情報は決して創作しない。推測しない。\n"
       "3. ただし【本日】の日付・曜日と、出典に書かれた営業時間・定休日のルールから"
       "論理的に結論を導くのは『創作』ではなく許可される（例: 定休日が第2土曜で本日が"
       "第2土曜なら『本日は定休日』と結論してよい）。\n"
       "4. 出典にも本日情報にも基づけず答えられない場合のみ、最初の行に正確に `INSUFFICIENT` とだけ書く。\n"
       "5. 広告・宣伝・誘導をしない。事実だけを述べる。\n"))
(def ^:private sys-en
  (str "You are etzhayyim 調べ — a source-grounded research assistant. Answer ONLY from the "
       "SOURCES below, concisely.\nRules:\n"
       "1. Cite each fact inline as [1] [2].\n"
       "2. Never invent or guess anything not in the sources.\n"
       "3. If the sources are insufficient, your FIRST line must be exactly `INSUFFICIENT`.\n"
       "4. No advertising, promotion, or nudging — facts only.\n"))

;; minimal prohibited-content scan on the produced answer (Charter Rider §2 backstop).
(def ^:private prohibited ["兵器設計" "weapon design" "child sexual" "児童性的"])

(defn host-port
  "Normalise a base URL to host:port (default port by scheme)."
  [base-url]
  (let [u (java.net.URI. (if (str/includes? base-url "://") base-url (str "http://" base-url)))
        port (let [p (.getPort u)] (if (neg? p) (if (= "https" (.getScheme u)) 443 80) p))]
    (str (.getHost u) ":" port)))

(defn validate-host!
  "Return host:port iff it is a Murakumo-fleet endpoint (G2); raise otherwise. No commercial
  LLM API is representable here. Building/validating does no network I/O."
  [base-url]
  (let [hp (host-port base-url)]
    (when-not (contains? allowed-infer-hosts hp)
      (throw (ex-info (str "shirabe: inference host " (pr-str hp) " is not Murakumo-fleet "
                           "(ADR-2605215000). Allowed: " (pr-str (sort allowed-infer-hosts))
                           ". No commercial LLM API is permitted.")
                      {:gate :G2 :host hp})))
    hp))

(def ^:private ja-dow {"MONDAY" "月" "TUESDAY" "火" "WEDNESDAY" "水" "THURSDAY" "木"
                       "FRIDAY" "金" "SATURDAY" "土" "SUNDAY" "日"})

(defn date-context
  "Resolve an ISO date into a 本日 fact line (weekday + which-nth-weekday-of-month) so the
  model can reason 'today is the 2nd Saturday → closed' WITHOUT fabricating: the current
  date is a fact, not a source claim. nil for a non-date / blank as-of."
  [asof lang]
  (when (and asof (re-matches #"\d{4}-\d{2}-\d{2}" (str asof)))
    (let [d (java.time.LocalDate/parse asof)
          wd (get ja-dow (str (.getDayOfWeek d)) "?")
          nth-wd (inc (quot (dec (.getDayOfMonth d)) 7))]
      (if (= lang :ja)
        (format "本日は %s（%d月 第%d %s曜日）。" asof (.getMonthValue d) nth-wd wd)
        (format "Today is %s (the %s of %s, week-%d)."
                asof (str (.getDayOfWeek d)) (str (.getMonth d)) nth-wd)))))

(defn build-prompt
  ([question evidence lang] (build-prompt question evidence lang nil))
  ([question evidence lang asof]
   (let [sys (if (= lang :ja) sys-ja sys-en)
         today (date-context asof lang)
         today-line (when today (str (if (= lang :ja) "\n【本日】 " "\nTODAY: ") today))
         src-hdr (if (= lang :ja) "\n【出典 / SOURCES】" "\nSOURCES:")
         srcs (map (fn [e] (format "[%d] %s — %s (%s, retrieved %s)"
                                   (:rank e) (:title e) (:snippet e) (:url e) (:retrieved-at e)))
                   evidence)
         q-line (str (if (= lang :ja) "\n【質問】 " "\nQUESTION: ") question)
         a-line (if (= lang :ja) "\n【回答】" "\nANSWER:")]
     (str/join "\n" (concat [sys] (when today-line [today-line]) [src-hdr] srcs [q-line a-line])))))

(defn- cited [answer n]
  (->> (re-seq #"\[(\d+)\]" answer)
       (map #(Integer/parseInt (second %)))
       (filter #(<= 1 % n))
       distinct sort vec))

(defn synthesize
  "Ground a gemma4 answer in `evidence`. `infer` is REQUIRED (G7). Optional `asof` injects the
  resolved 本日 date so freshness questions can be reasoned (a fact, not a fabrication — G4)."
  ([question evidence infer lang] (synthesize question evidence infer lang nil))
  ([question evidence infer lang asof]
   (when (nil? infer)
     (throw (ex-info "shirabe.synthesize: a Murakumo-fleet `infer` must be injected (G2 + G7)."
                     {:gate :G2})))
   (if (empty? evidence)
     {:answer "" :model "" :citations [] :sources [] :insufficient true :charter-ok true}
     (let [raw (str/trim (or (infer (build-prompt question evidence lang asof)) ""))
          low (str/lower-case raw)]
      {:answer raw
       :model (or (:model-id (meta infer)) "murakumo:gemma4")
       :citations (cited raw (count evidence))
       :sources (mapv (fn [e] {:rank (:rank e) :url (:url e) :title (:title e)}) evidence)
       :insufficient (str/starts-with? (str/upper-case raw) "INSUFFICIENT")
       :charter-ok (not (boolean (some #(str/includes? low %) prohibited)))}))))
