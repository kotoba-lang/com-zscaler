;; ported from 20-actors/hakoniwa/methods/murakumo.py — real port replacing the unit_refactor
;; stage-0 "TODO: port-failed" stubs. NS fixed root.hakoniwa.* → hakoniwa.* (20-actors source root).
(ns hakoniwa.methods.murakumo
  "murakumo.py — hakoniwa 箱庭 LLM narration client (Murakumo-only). 1:1 Clojure port.
  ADR-2605215000 + 2606111500.

  G5 — ALL inference routes through the Murakumo fleet (LiteLLM gateway 127.0.0.1:4000). No
  RunPod / OpenAI-direct / Vertex / Anthropic-direct / commercial GPU is ever contacted. This is
  the ONLY place hakoniwa talks to a model, and it talks only to the loopback gateway.

  GRACEFUL FALLBACK: if the gateway is unreachable, every call falls back to a deterministic
  TEMPLATE narration / the scalar kernel and sets ':via :template-fallback' / ':kernel-fallback'.

  Network I/O is behind #?(:clj ...); the fallback paths are pure + deterministic. The Python
  `__main__` demo printer is omitted.")

(def gateway "http://127.0.0.1:4000/v1/chat/completions")
(def model "gemma3:4b")
(def ^:private timeout-ms 8000)

(defn fleet-available?
  "True iff the Murakumo LiteLLM gateway answers on loopback. Never throws. In .cljs (no JVM
  http here) always false → the deterministic fallback path."
  ([] (fleet-available? "http://127.0.0.1:4000/v1/models" 3000))
  ([url timeout]
   #?(:clj
      (try
        (let [^java.net.HttpURLConnection conn
              (doto (.openConnection (java.net.URL. url))
                (.setConnectTimeout timeout)
                (.setReadTimeout timeout)
                (.setRequestMethod "GET"))]
          (= 200 (.getResponseCode conn)))
        (catch Exception _ false))
      :cljs false)))

(defn- chat
  "One Murakumo chat completion. Returns the text, or nil if the fleet is unreachable.
  ONLY ever contacts the loopback gateway (G5). #?(:clj) only."
  [system user temperature]
  #?(:clj
     (try
       (let [body (str "{\"model\":\"" model "\","
                       "\"messages\":[{\"role\":\"system\",\"content\":" (pr-str system) "},"
                       "{\"role\":\"user\",\"content\":" (pr-str user) "}],"
                       "\"temperature\":" temperature ",\"max_tokens\":220}")
             ^java.net.HttpURLConnection conn
             (doto (.openConnection (java.net.URL. gateway))
               (.setConnectTimeout timeout-ms)
               (.setReadTimeout timeout-ms)
               (.setRequestMethod "POST")
               (.setRequestProperty "Content-Type" "application/json")
               (.setDoOutput true))]
         (with-open [os (.getOutputStream conn)]
           (.write os (.getBytes ^String body "UTF-8")))
         (if (= 200 (.getResponseCode conn))
           (let [resp (slurp (.getInputStream conn))
                 ;; minimal extraction of choices[0].message.content
                 idx (.indexOf resp "\"content\"")]
             (when (>= idx 0)
               (let [after (subs resp (+ idx 9))
                     q1 (.indexOf after "\"")
                     rest (subs after (inc q1))
                     q2 (loop [i 0] (cond (>= i (count rest)) -1
                                          (and (= (nth rest i) \") (or (zero? i) (not= (nth rest (dec i)) \\))) i
                                          :else (recur (inc i))))]
                 (when (>= q2 0) (clojure.string/trim (subs rest 0 q2))))))
           nil))
       (catch Exception _ nil))
     :cljs nil))

(def system-narrate
  (str "あなたは etzhayyim の箱庭 (hakoniwa) アクターのナレーターです。架空の latent ペルソナで構成された"
       "シミュレーション結果を、防災・備えの計画材料として中立に要約します。厳守事項: "
       "(1) 単一の予測を断定しない — 結果は必ず『分布』として述べる(非終末論)。"
       "(2) 売買・投票・購入・支持などの行動を一切推奨しない(誘導禁止)。"
       "(3) 実在の個人には言及しない。日本語で1段落。"))

(defn- f2 [v] (format "%.2f" (double v)))
(defn- f3 [v] (format "%.3f" (double v)))

(defn- template-narration [scenario dist]
  (let [q (get dist "quantiles")]
    (str "箱庭シミュレーション「" scenario "」の結果は、単一の予測ではなく可能性の分布です: "
         "町全体の採用スタンスは中央値 (p50) " (f2 (get q ":p50")) "、"
         "下位10% (p10) " (f2 (get q ":p10")) " 〜 上位90% (p90) " (f2 (get q ":p90")) " の幅。"
         "これは架空ペルソナによるシナリオ探索であり、備えの計画材料です。"
         "特定の行動を推奨するものではありません。")))

(defn narrate
  "Return {\"text\" .. \"via\" ..}. Tries Murakumo; falls back to a deterministic template.
  The text is NOT yet guarded — social/draft-distribution-post applies G2/G3 before emission."
  ([scenario dist] (narrate scenario dist true))
  ([scenario dist prefer-fleet]
   (if (and prefer-fleet (fleet-available?))
     (let [q (get dist "quantiles")
           user (str "シナリオ: " scenario "\n"
                     "分布(町全体の採用スタンス): p10=" (f3 (get q ":p10")) " p25=" (f3 (get q ":p25")) " "
                     "p50=" (f3 (get q ":p50")) " p75=" (f3 (get q ":p75")) " p90=" (f3 (get q ":p90")) " "
                     "mean=" (f3 (get dist "mean")) " stdev=" (f3 (get dist "stdev")) "\n"
                     "上記を1段落で中立に要約してください(分布として、行動推奨なし)。")
           text (chat system-narrate user 0.2)]
       (if text
         {"text" text "via" ":murakumo"}
         {"text" (template-narration scenario dist) "via" ":template-fallback"}))
     {"text" (template-narration scenario dist) "via" ":template-fallback"})))

(defn persona-step
  "LLM-persona swarm variant (gated). Asks the fleet for a synthetic persona's next stance; falls
  back to the deterministic Friedkin-Johnsen scalar update. Returns {\"stance\" v \"via\" ..}.
  Drop-in for the simulate swarm step-fn (mirrors persona_step)."
  ([stance neighbour-mean susceptibility anchor]
   (persona-step stance neighbour-mean susceptibility anchor false))
  ([stance neighbour-mean susceptibility anchor prefer-fleet]
   (let [clamp (fn [^double v] (min 1.0 (max 0.0 v)))]
     (or
      (when (and prefer-fleet (fleet-available?))
        (let [user (str "架空ペルソナ(susceptibility=" (f2 susceptibility) ", anchor=" (f2 anchor) ")の"
                        "現在スタンス=" (f2 stance) "、近傍平均=" (f2 neighbour-mean) "。"
                        "次ステップのスタンスを 0〜1 の数値のみで答えてください。")
              text (chat "0〜1の数値のみを返す。説明不要。" user 0.0)]
          (when text
            (try
              (let [v (Double/parseDouble (first (clojure.string/split (clojure.string/trim text) #"\s+")))]
                {"stance" (clamp v) "via" ":murakumo"})
              (catch Exception _ nil)))))
      (let [nx (+ (* (double susceptibility) (double neighbour-mean))
                  (* (- 1.0 (double susceptibility)) (double anchor)))]
        {"stance" (clamp nx) "via" ":kernel-fallback"})))))
