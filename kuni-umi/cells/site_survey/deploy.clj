(ns kuni-umi.cells.site-survey.deploy
  "Deploy entrypoint — wires a REAL Murakumo-fleet LLM (langchain.model
  OpenAI-compatible against the local Ollama, gemma-4-E4B) into
  `jurisdiction-eligibility`'s `:llm-primary` DI slot and runs ONE
  site-eligibility check end to end (advise -> jurisdiction-governor ->
  accepted/rejected). Same shape as tashikame.deploy/kouhou.deploy/
  yosoku.deploy/fleet.deploy.

  This does NOT advance kuni-umi's S-phase (still S0, no hardware, no
  motion, no witness signing touched) -- it only exercises the cognitive/
  policy judgment layer of ONE pure node. All other cell nodes remain
  NotImplementedError-gated exactly as before.

  `cell.cljc`/`advisor.cljc` are loaded via `load-file`, not `:require` —
  this repo's `kuni-umi.*` namespace declarations don't match their actual
  file paths outside the full etzhayyim/root monorepo bb classpath (a
  pre-existing structural quirk, not introduced here); only external
  library namespaces (langchain.model, clojure.data.json) resolve normally.

  Usage: clojure -M -e '(load-file \"cells/site_survey/deploy.clj\") (kuni-umi.cells.site-survey.deploy/-main)'
  Env:   KUNI_UMI_OLLAMA_URL (default http://127.0.0.1:11434)
         KUNI_UMI_OLLAMA_MODEL (default gemma-4-E4B qat)"
  (:require [clojure.data.json :as json]
            [clojure.string :as str]
            [langchain.model :as model])
  (:import [java.net URI]
           [java.net.http HttpClient HttpRequest HttpRequest$BodyPublishers
            HttpResponse$BodyHandlers]))

(let [dir (.getParent (java.io.File. *file*))]
  (load-file (str dir "/cell.cljc"))
  (load-file (str dir "/advisor.cljc")))

(def ^:private default-ollama-url
  (or (System/getenv "KUNI_UMI_OLLAMA_URL") "http://127.0.0.1:11434"))

(def ^:private default-ollama-model
  (or (System/getenv "KUNI_UMI_OLLAMA_MODEL")
      "hf.co/unsloth/gemma-4-E4B-it-qat-GGUF:UD-Q4_K_XL"))

(defn jvm-http-fn
  "langchain.model :http-fn backed by the JDK HTTP client (no dependency)."
  [{:keys [url method headers body]}]
  (let [b (HttpRequest/newBuilder (URI/create url))]
    (doseq [[k v] headers] (.header b k v))
    (let [req  (-> b (.method (str/upper-case (name (or method :post)))
                             (if body
                               (HttpRequest$BodyPublishers/ofString body)
                               (HttpRequest$BodyPublishers/noBody)))
                   (.build))
          resp (.send (HttpClient/newHttpClient) req (HttpResponse$BodyHandlers/ofString))]
      {:status (.statusCode resp) :body (.body resp)})))

(defn ollama-chat-model
  "Build a langchain.model/openai-model against a Murakumo-fleet Ollama.
  Refuses non-Murakumo hosts (Rider v3.3 §2(i))."
  ([]
   (ollama-chat-model default-ollama-url default-ollama-model))
  ([ollama-url ollama-model]
   ((resolve 'kuni-umi.cells.site-survey.advisor/assert-murakumo!) ollama-url)
   (model/openai-model
    {:url        (str ollama-url "/v1/chat/completions")
     :model      ollama-model
     :api-key    nil
     :http-fn    jvm-http-fn
     :json-write json/write-str
     :json-read  #(json/read-str % :key-fn keyword)})))

(def ^:private demo-site-states
  "Two candidate sites: one constitutionally eligible, one not (military
  intendedUse) -- proves the governor's hard floor holds against a real LLM's
  judgment either way, not just the mock."
  {"eligible" {:siteDid "did:web:example.gov:site:001"
               :utilityClass "water" :domain "terrestrial"
               :intendedUse "community"
               :jurisdictionDid "did:web:example.gov"
               :localLawAttestationCid "bafyattestation001"}
   "military" {:siteDid "did:web:example.gov:site:002"
               :utilityClass "power" :domain "terrestrial"
               :intendedUse "military"
               :jurisdictionDid "did:web:example.gov"
               :localLawAttestationCid "bafyattestation002"}})

(defn -main [& args]
  (let [which (or (first args) "eligible")
        state (get demo-site-states which)
        chat  (ollama-chat-model)
        llm-advise (resolve 'kuni-umi.cells.site-survey.advisor/llm-advise)
        deps  ((resolve 'kuni-umi.cells.site-survey.cell/make-cell-deps)
               :llm-primary (llm-advise chat {:max-tokens 1024}))
        out   ((resolve 'kuni-umi.cells.site-survey.cell/jurisdiction-eligibility) state deps)]
    (println "=== kuni-umi site_survey deploy (real LLM @ Murakumo) ===")
    (println "site       :" which "->" (pr-str state))
    (println "accepted   :" (:accepted out))
    (println "rejection  :" (:rejectionReason out))))
