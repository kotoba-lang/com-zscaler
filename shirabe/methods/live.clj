#!/usr/bin/env bb
(ns shirabe.methods.live
  "shirabe 調べ — LIVE driver (the G7-gated operator/member leg). ADR-2606131600.

  Wires the two live legs the pure methods leave injected:
    - a read-only public-web `fetcher` (G1): the DuckDuckGo HTML endpoint over
      babashka.http-client, OR pre-gathered evidence via --evidence FILE (so the gemma4
      leg can be proven live even where outbound search is unavailable);
    - a Murakumo-fleet `infer` (G2): pinned by synthesize/validate-host! to the LiteLLM
      gateway / EVO-X2 / local Ollama gemma 4 E4B QAT.

  This is an EXPLICIT operator/member step — never a cron; the pure loop never calls it (G7).

    bb --classpath 20-actors 20-actors/shirabe/methods/live.clj \"青山の島田は今日やっている?\" --asof 2026-06-13
    bb --classpath 20-actors 20-actors/shirabe/methods/live.clj \"<q>\" --evidence <file.edn> [--infer http://127.0.0.1:11434]"
  (:require [shirabe.methods.session :as session]
            [shirabe.methods.synthesize :as synth]
            [shirabe.methods.kotoba :as kotoba]
            [babashka.http-client :as http]
            [cheshire.core :as json]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as str]))

;; ── G1: read-only public-web search (DuckDuckGo HTML). Best-effort, fail-soft. ──
(def ^:private ddg-re
  #"(?s)result__a\"[^>]*href=\"([^\"]+)\"[^>]*>(.*?)</a>.*?result__snippet\"[^>]*>(.*?)</a>")

(defn- strip [s]
  (-> (str s) (str/replace #"<[^>]+>" "") (str/replace #"&amp;" "&")
      (str/replace #"&#x27;" "'") (str/replace #"&quot;" "\"") str/trim))

(defn- unwrap-url
  "DuckDuckGo HTML wraps results as //duckduckgo.com/l/?uddg=<encoded-real-url>&rut=… —
  unwrap to the real source URL so provenance in the Datom log points at the source, not DDG."
  [href]
  (if-let [m (re-find #"[?&]uddg=([^&]+)" href)]
    (java.net.URLDecoder/decode (second m) "UTF-8")
    (if (str/starts-with? href "//") (str "https:" href) href)))

(defn ddg-fetcher [query]
  (try
    (let [resp (http/get "https://html.duckduckgo.com/html/"
                         {:query-params {"q" query}
                          :headers {"User-Agent" "Mozilla/5.0 etzhayyim-shirabe"}
                          :timeout 12000})]
      (->> (re-seq ddg-re (:body resp))
           (take 8)
           (mapv (fn [[_ href title snip]]
                   {:title (strip title) :url (unwrap-url href) :snippet (strip snip)}))))
    (catch Exception _ [])))

(defn file-fetcher
  "Use pre-gathered evidence (member browser / operator tool). Proves the gemma4 leg live."
  [path]
  (let [data (edn/read-string (slurp path))]
    (cond
      (and (map? data) (:search data)) (fn [q] (get-in data [:search q] (get-in data [:search "*"] [])))
      (sequential? data) (constantly data)
      :else (constantly (:evidence data [])))))

;; ── G2: Murakumo-fleet inference adapter (validate-host! raises if not fleet). ──
(defn make-infer [base-url model]
  (let [hp (synth/validate-host! base-url)
        ollama? (str/ends-with? hp ":11434")
        f (fn [prompt]
            (if ollama?
              (-> (http/post (str base-url "/api/generate")
                             {:headers {"Content-Type" "application/json"}
                              :body (json/generate-string
                                     {:model model :prompt prompt :stream false
                                      :options {:temperature 0.2}})
                              :timeout 180000})
                  :body (json/parse-string true) :response)
              (-> (http/post (str base-url "/v1/chat/completions")
                             {:headers {"Content-Type" "application/json"}
                              :body (json/generate-string
                                     {:model model :temperature 0.2
                                      :messages [{:role "user" :content prompt}]})
                              :timeout 180000})
                  :body (json/parse-string true) :choices first :message :content)))]
    (with-meta f {:model-id (str model "@" hp)})))

(defn- arg [args flag] (let [i (.indexOf args flag)] (when (>= i 0) (nth args (inc i)))))

(def log-path
  (str (-> (io/file *file*) .getParentFile .getParentFile) "/data/persisted/shirabe.datoms.kotoba.edn"))

(defn -main [& args]
  (when (or (empty? args) (str/starts-with? (first args) "--"))
    (println "usage: live.clj \"<question>\" [--asof YYYY-MM-DD] [--evidence FILE] [--infer URL]")
    (System/exit 2))
  (let [args (vec args)
        question (first args)
        asof (or (arg args "--asof") "")
        infer-url (or (arg args "--infer") "http://127.0.0.1:11434")
        model (or (arg args "--model") "hf.co/unsloth/gemma-4-E4B-it-qat-GGUF:UD-Q4_K_XL")
        fetcher (if-let [f (arg args "--evidence")] (file-fetcher f) ddg-fetcher)
        infer (make-infer infer-url model)]
    (binding [*out* *err*] (println (str "shirabe 調べ — LIVE · infer=" (:model-id (meta infer)))))
    (let [s (session/research question fetcher infer asof)
          r (:result s)]
      (println (apply str (repeat 72 "=")))
      (println (str "Q: " question))
      (println (str "model: " (:model r) "  ·  sources: " (count (:evidence s))
                    "  ·  rounds: " (:rounds s) "  ·  insufficient: " (:insufficient r)))
      (println (apply str (repeat 72 "-")))
      (println (:answer r))
      (println (apply str (repeat 72 "-")))
      (println "SOURCES:")
      (doseq [src (:sources r)] (println (str "  [" (:rank src) "] " (:title src) "  " (:url src))))
      (println (apply str (repeat 72 "=")))
      ;; persist to the local kotoba Datom log (content-addressed commit-DAG)
      (let [cid (kotoba/persist! s log-path {:tx-id 1 :as-of (or (seq asof) "live") :member ""})]
        (binding [*out* *err*]
          (println (str "kotoba Datomic tx → " log-path "  (cid " cid ", chain-ok "
                        (kotoba/verify-chain log-path) ")")))))))

(apply -main *command-line-args*)
