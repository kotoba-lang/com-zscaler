(ns kuni-umi.cells.site-survey.advisor
  "Real-LLM advisor for `kuni-umi.cells.site-survey.cell/jurisdiction-
  eligibility`'s `:llm-primary` DI slot. NOT required by `cell.cljc` itself
  (that ns stays dependency-free, per its own \"no langgraph dependency at
  this layer\" convention, extended here to \"no langchain dependency at
  this layer either\") — `deps.edn`-having callers (tests, `deploy.clj`)
  require this ns explicitly when they want the real path instead of
  `cell/mock-advise`.

  Unlike tashikame/kouhou/yosoku/fleet's `Advisor` protocol, `jurisdiction-
  eligibility` only wants a bare fn of `state -> proposal` (mirroring
  `fleet.coordinator/propose`'s `:advise` shape) — so `llm-advise` returns
  that fn directly rather than a reified protocol instance.

  Sealed like every other advisor in this family: the LLM's output is a
  PROPOSAL only. `cell/jurisdiction-governor` independently re-derives the
  actual decision from the raw state and can never be talked past the
  constitutional intendedUse floor by any LLM output, however confident."
  (:require #?(:clj  [clojure.edn :as edn]
               :cljs [cljs.reader :as edn])
            [clojure.string :as str]
            [langchain.model :as model]))

;; ───────────────────────── Murakumo-fleet host allowlist ─────────────────────────
;; Same allowlist/pattern as tashikame.advisor/kouhou.advisor/yosoku.advisor/
;; fleet.advisor (Rider v3.3 §2(i)) -- includes the com-junkawasaki tailnet
;; entries verified live 2026-07-13/14.

(def allowed-infer-hosts
  #{"127.0.0.1:11434" "localhost:11434"
    "127.0.0.1:4000"  "localhost:4000"
    "192.168.1.70:4000"
    "100.98.142.59:11434"   ; dan
    "100.66.28.79:11434"    ; zebulun
    "100.102.78.81:11434"   ; levi
    "100.75.169.8:11434"    ; benjamin
    "100.89.204.30:11434"   ; issachar
    "100.82.123.35:11434"   ; joseph
    "100.101.27.85:11434"   ; naphtali
    "100.81.66.86:11434"})  ; simeon

(defn- host-port [url]
  (when (string? url) (second (re-find #"(?i)^[a-z]+://([^/]+)" url))))

(defn assert-murakumo!
  "Throw if `ollama-url` is not a Murakumo-fleet inference host."
  [ollama-url]
  (let [hp (host-port ollama-url)]
    (when-not (contains? allowed-infer-hosts hp)
      (throw (ex-info (str "inference host " hp " is not Murakumo-fleet (Rider v3.3 §2(i))")
                      {:host hp})))))

;; ───────────────────────── jurisdiction-eligibility advisor ─────────────────────────

(def system-prompt
  "You assess whether a proposed planetary-infrastructure deployment site is
eligible to proceed to survey, for kuni-umi (a civilian/community/commons-
only robotics fleet actor -- military and proprietary-closed-design uses are
constitutionally out of scope and are rejected upstream of you; do not
second-guess that boundary, just judge what you are given). Given the site's
metadata, give your own independent judgment on plausibility/coherence (e.g.
does the declared utilityClass/domain combination make sense, does the
intendedUse description read as genuinely civilian/community/commons in
substance and not just label). Respond with ONLY a single-line EDN map, no
prose, no code fences:
  {:accepted <true|false> :rationale \"one short sentence\" :confidence <0.0-1.0>}")

(defn- build-prompt [state]
  (str "Site metadata: "
       (pr-str (select-keys state [:utilityClass :domain :intendedUse
                                    :intendedBeneficiaryDids :jurisdictionDid]))
       "\n\nReturn ONLY the EDN map now."))

(defn- valid-confidence [c]
  (if (number? c) (max 0.0 (min 1.0 (double c))) 0.0))

(defn parse-proposal-edn
  "Defensively parse the LLM's `{:accepted :rationale :confidence}` EDN map.
  Any parse/shape failure yields a safe accepted=false low-confidence
  proposal (an LLM hiccup can never read as an implicit approval) --
  `jurisdiction-governor` treats this the same as an explicit rejection."
  [content]
  (let [cleaned (-> (str content)
                     (str/replace #"(?s)```[a-zA-Z]*" "")
                     (str/replace "```" ""))
        m (try (some-> (re-find #"(?s)\{.*\}" cleaned) edn/read-string)
               (catch #?(:clj Throwable :cljs :default) _ nil))]
    (if (map? m)
      {:accepted (true? (:accepted m))
       :rationale (str (or (:rationale m) ""))
       :confidence (valid-confidence (:confidence m))}
      {:accepted false :rationale "unparseable LLM output" :confidence 0.0})))

(defn llm-advise
  "Returns a `state -> proposal` fn backed by `chat-model` (a
  `langchain.model/ChatModel`) -- the shape `cell/jurisdiction-eligibility`'s
  `:llm-primary` DI slot expects. gen-opts -> `model/-generate` opts (e.g.
  `{:max-tokens 1024}` -- gemma4:e4b-it-qat is a \"thinking\" model that can
  exhaust a small budget on its :reasoning field before :content, per
  etzhayyim/com-etzhayyim-yosoku PR #5's finding)."
  ([chat-model] (llm-advise chat-model {}))
  ([chat-model gen-opts]
   (fn [state]
     (let [content (:content (model/-generate chat-model
                                [{:role :system :content system-prompt}
                                 {:role :user :content (build-prompt state)}]
                                gen-opts))]
       (parse-proposal-edn content)))))
