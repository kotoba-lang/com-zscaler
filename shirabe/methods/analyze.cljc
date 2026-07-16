(ns shirabe.methods.analyze
  "shirabe 調べ — research-plan analyzer (the PLAN leg of the ReAct loop). ADR-2606131600.

  Turns a natural-language question into a deterministic research PLAN: question type,
  whether it needs CURRENT (freshness-sensitive) information, the entities it is about,
  and the decomposed search sub-queries the retrieve leg will run. Pure + deterministic —
  NO network I/O here (the live web leg is retrieve, the inference leg synthesize; both
  G7-gated). This is what lets shirabe answer a『青山の島田は今日やっている?』-style
  question the way a person would: see it is freshness-sensitive, pull 島田 / 青山, and
  emit focused queries (営業時間 / 定休日 / 今日 営業).

  CONSTITUTIONAL:
    G3 — no personalization. plan takes ONLY the question string. No user profile,
      history, or behavioural signal — a research membrane, never a tracker.
    G7 — the loop does no network I/O. plan is pure; the live legs are injected.

  kotoba-clj (ADR-2606131300): pure fns, EDN-native, runs under babashka + the kotoba
  Clojure engine. Idiomatic clj keys (:question/:lang/:qtype/:freshness/:subqueries)."
  (:require [clojure.string :as str]))

;; ── freshness signal: the answer depends on CURRENT state (hours / price / weather /
;;    news / availability). These questions MUST hit the live web leg; a stale Datom-log
;;    or Common-Crawl snapshot would answer them wrong.
(def ^:private fresh-ja
  ["今日" "今" "現在" "営業" "やってる" "やっている" "開いて" "開いてる" "空いて"
   "在庫" "価格" "値段" "いくら" "天気" "ニュース" "最新" "今週" "今月"
   "リアルタイム" "本日" "明日" "週末" "予約" "混雑"])
(def ^:private fresh-en
  ["today" "now" "current" "currently" "open" "hours" "price" "in stock" "weather"
   "news" "latest" "this week" "tonight" "right now" "real-time" "realtime" "available"])

;; ── question-type signals (orthogonal to freshness)
(def ^:private cmp-sig ["より" "どっち" "比較" "違い" " vs " "compare" "difference" "better" "versus"])
(def ^:private how-sig ["方法" "やり方" "手順" "どうやって" "どうすれば" "how to" "how do i" "how can i" "steps to"])
(def ^:private def-sig ["とは" "意味" "って何" "ってなに" "what is" "what are" "define" "definition of" "meaning of"])

;; ── focused sub-query templates for a freshness entity question (営業/価格/天気 …)
(def ^:private fresh-aspects ["営業時間 定休日" "今日 営業" "アクセス 場所"])

(defn- cjk? [s] (boolean (re-find #"[぀-鿿豈-﫿]" (str s))))

(defn- has? [text needles]
  (let [low (str/lower-case (str text))]
    (boolean (some #(str/includes? low %) needles))))

(defn- add-uniq
  "Order-preserving de-dup: trim a candidate, keep it if ≥2 chars and unseen."
  [[seen acc] e]
  (let [e (-> (str e)
              (str/replace #"^[\s　、。,.!?！？]+" "")
              (str/replace #"[\s　、。,.!?！？]+$" "")
              str/trim)]
    (if (and (>= (count e) 2) (not (contains? seen e)))
      [(conj seen e) (conj acc e)]
      [seen acc])))

(defn- entities
  "Naive, deterministic entity extraction: quoted spans, then CJK runs split on the
  common particles (so we keep 青山 and 島田, not 青山の島田は), then Capitalised ASCII."
  [question]
  (let [quoted (mapcat (fn [m] [(or (nth m 1) (nth m 2))])
                       (re-seq #"\"([^\"]+)\"|「([^」]+)」" question))
        cjk (mapcat (fn [run] (str/split run #"の|は|が|を|に|で|と|も|や|から|まで|って"))
                    (re-seq #"[぀-ヿ㐀-鿿豈-﫿]{2,}" question))
        caps (re-seq #"\b[A-Z][A-Za-z0-9]{1,}\b" question)]
    (second (reduce add-uniq [#{} []] (concat quoted cjk caps)))))

(defn- qtype [q]
  (cond
    (has? q cmp-sig) :comparison
    (has? q how-sig) :howto
    (has? q def-sig) :definition
    :else :factual))

(defn- subqueries
  "Decompose into focused search queries (deterministic, capped at 4 — G5)."
  [question qt freshness ents]
  (let [norm (fn [s] (-> (str s) (str/replace #"\s+" " ")
                         (str/replace #"[\s　?？]+$" "") str/trim))
        head (first ents)
        cands (concat
               [question]
               (cond
                 (and freshness head) (map #(str head " " %) fresh-aspects)
                 (and (= qt :comparison) (>= (count ents) 2)) (map #(str % " 評価 仕様") (take 2 ents))
                 head [head]
                 :else []))]
    (vec (take 4 (second (reduce (fn [[seen acc] q]
                                   (let [q (norm q)]
                                     (if (and (seq q) (not (contains? seen q)))
                                       [(conj seen q) (conj acc q)]
                                       [seen acc])))
                                 [#{} []] cands))))))

(defn plan
  "Question → deterministic research plan. Pure; no network I/O (G7)."
  [question]
  (let [question (str/trim (or question ""))
        lang (if (cjk? question) :ja :en)
        freshness (or (has? question fresh-ja) (has? question fresh-en))
        qt (qtype question)
        ents (entities question)]
    {:question question
     :lang lang
     :qtype qt
     :freshness freshness
     :entities ents
     :subqueries (subqueries question qt freshness ents)}))
