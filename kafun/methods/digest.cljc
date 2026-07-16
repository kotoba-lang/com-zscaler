#!/usr/bin/env bb
;; kafun 花粉 — Murakumo-narrated remediation digest (fail-open template, G6/G8).
(ns kafun.methods.digest
  "digest.cljc — kafun 花粉 reasons about its remediation MAP in human-readable words
  (ADR-2606211712 R1; the ibuki/colony-digest pattern, ADR-2606101800).

  The digest is a mirror REPORT of where the 花粉 burden is being rectified into
  prioritized restoration — the L1-1 無花粉苗木 + L3-1 主伐再造林 bottlenecks, the
  refusals (撲滅 ≠ deforestation), and what feeds the downstream actors. It is
  NARRATED, optionally, by the Murakumo fleet, and emitted as a DRY-RUN post.

  Purity + fail-open by construction: `narrate` takes an injected `infer` fn (the
  Murakumo client); absent OR throwing → a deterministic TEMPLATE (the organism keeps
  reporting even offline, G6). `murakumo-infer` is the loopback-only client (Ollama
  127.0.0.1:11434, gemma-4-E4B, temperature 0 — the kaname pattern); a non-loopback
  host is refused (G6 Murakumo-only). The digest is `:digest/status :dry-run` ONLY —
  `:published` is unrepresentable here (G8; live posting is member-principal elsewhere).

  Assessment-only: the digest reports a RESTORATION map, never a cut-list (G1/G2)."
  (:require [kafun.methods.remediate :as rem]
            [kafun.methods.kafun-edn :as ke]
            [clojure.string :as str]
            #?(:clj [babashka.http-client :as http])
            #?(:clj [clojure.edn :as edn])))

;; ── digest data (pure fold over the assessment) ──────────────────────────────

(defn- round3 [x] (/ (Math/round (* (double x) 1000.0)) 1000.0))

(defn digest-data
  "Fold a remediation assessment into the structured digest (pure). Returns
  {:route-tally {...} :throughput :top-priority [...] :bottlenecks {...} :refused-reasons [...]}."
  [assessment]
  (let [rows (get assessment "stands")
        burden (fn [r] (double (get r "pollen_burden")))
        by-verdict (group-by #(get % "verdict") rows)
        verdict-burden (fn [v] (round3 (reduce + 0.0 (map burden (get by-verdict v)))))
        top (->> (get by-verdict :reforest-priority)
                 (sort-by #(- (double (get % "remediation_priority"))))
                 (take 3)
                 (mapv #(hash-map :name (get % "name") :burden (round3 (burden %)))))]
    {:route-tally (into {} (map (fn [[v rs]] [v (count rs)]) by-verdict))
     :throughput (round3 (reduce + 0.0 (map burden rows)))
     :top-priority top
     :bottlenecks {:l3-reforest (count (get by-verdict :reforest-priority))
                   :l1-await-sapling (count (get by-verdict :await-sapling-supply))
                   :await-consent (count (get by-verdict :await-consent))
                   :protected (count (get by-verdict :protected-selective))}
     :refused (count (get by-verdict :refuse))
     :refused-burden (verdict-burden :refuse)}))

;; ── deterministic template narration (the fail-open default, G6) ─────────────

(defn template-narration
  "A deterministic, human-readable digest (no LLM). The fail-open default — kafun keeps
  reporting even when the Murakumo fleet is offline. Pure."
  [{:keys [route-tally throughput top-priority bottlenecks refused] :as _d}]
  (str
   "【kafun 花粉 — 花粉撲滅 remediation digest】\n"
   "総花粉負荷 (throughput) " throughput " を整流し、" (reduce + 0 (vals route-tally))
   " stand を優先順位化しました。撲滅は復元 (主伐再造林) であり、伐採ではありません。\n"
   "・主伐再造林 優先 (L3-1): " (:l3-reforest bottlenecks) " stand"
   (when (seq top-priority)
     (str " — 筆頭: " (str/join " / " (map :name top-priority))))
   "\n・無花粉苗木 待ち (L1-1 隘路): " (:l1-await-sapling bottlenecks) " stand\n"
   "・地権者同意 待ち: " (:await-consent bottlenecks) " stand\n"
   "・保安林・急傾斜 (漸進的のみ、皆伐せず): " (:protected bottlenecks) " stand\n"
   "・拒否 (再造林なき主伐 / 炭素ポジティブ): " refused " stand\n"
   "下流アクター (sanae 植林 / inochi 復元 / musubi 同意 / kamado 炭素) への入力となります。"
   " kafun は伐採も植林もせず、動かすのは情報-エネルギー (優先順位化された地図) のみです。"))

;; ── Murakumo prompt + loopback-only client (G6) ──────────────────────────────

(defn murakumo-prompt
  "A charter-clean narration prompt built from the digest data (pure). The LLM narrates a
  RESTORATION report; it never invents stands and never proposes a cut."
  [d]
  (str "あなたは宗教法人 etzhayyim の actor『kafun 花粉』です。以下の花粉撲滅 remediation "
       "集計を、復元 (主伐再造林) の観点で、3-4文の人間可読な日本語レポートに要約してください。"
       "撲滅は伐採ではなく生態系の復元です。伐採の提案や stand の捏造は禁止です。\n\n"
       (pr-str d)))

(def ^:private murakumo-loopback-hosts #{"127.0.0.1" "localhost"})
(def ^:private ollama-model "hf.co/unsloth/gemma-4-E4B-it-qat-GGUF:UD-Q4_K_XL")

#?(:clj
   (defn murakumo-infer
     "Loopback-only Murakumo narrator (Ollama 127.0.0.1:11434, gemma-4-E4B, temperature 0 —
     the kaname pattern). G6: a non-loopback host is REFUSED (throws). The caller (narrate)
     wraps this in try/catch so an offline fleet fails open to the template — so this may
     throw freely. Returns the narration string."
     ([prompt] (murakumo-infer prompt "127.0.0.1"))
     ([prompt host]
      (when-not (contains? murakumo-loopback-hosts host)
        (throw (ex-info "G6 Murakumo-only: non-loopback host refused" {:host host})))
      (let [body (str "{\"model\":\"" ollama-model "\",\"stream\":false,\"options\":{\"temperature\":0},"
                      "\"prompt\":" (pr-str prompt) "}")
            resp (http/post (str "http://" host ":11434/api/generate")
                            {:headers {"content-type" "application/json"} :body body :timeout 60000})]
        (-> (:body resp) (edn/read-string) (get "response") str str/trim)))))

(defn narrate
  "Narrate the digest. opts: :infer (a fn String→String, e.g. murakumo-infer). With :infer,
  try it and FAIL OPEN to the template on any error (G6). Without :infer → the template. Pure
  except for the injected infer. Returns the narration string."
  ([d] (narrate d {}))
  ([d {:keys [infer]}]
   (if infer
     (try (let [out (infer (murakumo-prompt d))]
            (if (str/blank? out) (template-narration d) out))
          (catch #?(:clj Exception :cljs :default) _ (template-narration d)))
     (template-narration d))))

;; ── digest datoms (:digest/status :dry-run ONLY, G8) ─────────────────────────

(defn- add [e a v] [":db/add" e a v])

(defn digest-datoms
  "Append-only EAVT datoms for one digest post. `:digest/status` is `:dry-run` ONLY —
  `:published` is unrepresentable (G8). Flagged :kafun/derived. `digest-id` is caller-supplied
  (deterministic; no wall clock)."
  [d narration digest-id]
  (let [e (str "kafun-digest:" digest-id)]
    [(add e ":digest/throughput" (:throughput d))
     (add e ":digest/reforest-priority" (get-in d [:bottlenecks :l3-reforest]))
     (add e ":digest/await-sapling" (get-in d [:bottlenecks :l1-await-sapling]))
     (add e ":digest/refused" (:refused d))
     (add e ":digest/narration" narration)
     (add e ":digest/status" ":dry-run")
     (add e ":kafun/derived" true)]))

;; ── CLI (bb) ─────────────────────────────────────────────────────────────────

#?(:clj
   (defn -main [& args]
     (let [flags (set (filter #(str/starts-with? % "--") args))
           pos (vec (remove #(str/starts-with? % "--") args))
           seed (or (first pos) "20-actors/kafun/kotoba/seed.edn")
           ;; kafun-edn/stands, not a raw read+filter: seed.edn is Datomic/
           ;; Datascript tx-data (Phase 4 EDN datomize) — kafun-edn/classify is
           ;; where the tx-data -> bare-row reconstitution lives.
           stands (ke/stands seed)
           d (digest-data (rem/assess stands))
           narration (narrate d (when (contains? flags "--live") {:infer murakumo-infer}))]
       (println narration)
       (println (str "\n-- :digest/status :dry-run · "
                     (if (contains? flags "--live") "Murakumo (fail-open)" "template") " --")))))

#?(:clj
   (when (= *file* (System/getProperty "babashka.file"))
     (apply -main *command-line-args*)))
