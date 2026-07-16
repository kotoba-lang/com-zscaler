#!/usr/bin/env bb
;; tsubasa 翼 — Murakumo-narrated fare digest (R3, G6 local-only, fail-open). ADR-2606072802.
(ns tsubasa.methods.digest
  "digest.cljc — tsubasa 翼 human-readable digest of the competition / fare map.

  Turns the analyze readout into a short, honest paragraph for a member — the one
  layer a pure fold can't produce. Inference is Murakumo-ONLY (G6): the Murakumo
  LiteLLM gateway on the LOOPBACK (127.0.0.1:4000, OpenAI-compatible) — never an
  external LLM (the host is hardcoded loopback, so an external endpoint is
  unrepresentable). Read-only inference, nothing signed (no-server-key).

  FAIL-OPEN (ibuki G6 pattern): if Murakumo is unreachable / errors / returns blank,
  `digest` degrades to a deterministic TEMPLATE built from the data and marks
  :source \"template\" so the degraded path stays legible. The digest is honest by
  construction — it restates DISCLOSED facts (cheapest/greenest/fastest, which routes
  have thin competition) and NEVER invents urgency / a 'book now' nudge (G3) or a paid
  recommendation (G1/G2)."
  (:require [clojure.string :as str]
            [tsubasa.methods.analyze :as analyze]
            #?(:clj [clojure.edn :as edn])
            #?(:clj [cheshire.core :as json])
            #?(:clj [babashka.http-client :as http])))

(def ^:private gateway "http://127.0.0.1:4000/v1/chat/completions")  ; loopback Murakumo ONLY (G6)
(def ^:private model "gemma4:e4b-it-qat")

(defn- top-opening
  "The routes flagged :opening (thin competition), most concentrated first."
  [analysis]
  (->> (get analysis "routes")
       (filter #(= :opening (get % "opening")))
       (sort-by #(- (get % "carrier_hhi")))))

(defn build-messages
  "Murakumo chat messages: ask for ONE honest Japanese paragraph. The prompt forbids
  urgency / 'book now' / paid-recommendation language (G3/G1)."
  [analysis coverage]
  (let [routes (get analysis "routes")
        opening (top-opening analysis)
        greenest (when (seq routes)
                   (apply min-key #(get % "greenest_co2_kg") routes))]
    [{:role "system"
      :content (str "あなたは tsubasa 翼、正直な航空券メタ検索コモンズ。出力は日本語で簡潔な一段落のみ。"
                    "煽り(『今すぐ予約』『値上がり』)・有料おすすめ・特定航空会社への誘導は禁止。"
                    "事実(最安・最少CO₂・競争の薄い路線)だけを淡々と述べる。")}
     {:role "user"
      :content (str "観測: 路線数=" (count routes)
                    "、競争の薄い(:opening)路線=" (count opening)
                    (when greenest (str "、最も低CO₂の路線=" (get greenest "route")
                                        " (" (get greenest "greenest_co2_kg") "kg, " (get greenest "greenest_carrier") ")"))
                    "、空港カバレッジ=" (get coverage "airports_have") "/" (get coverage "airports_target")
                    "。この観測を、煽らず、CO₂を隠さず、一段落で要約して。")}]))

#?(:clj
   (defn murakumo-infer
     "POST to the loopback Murakumo gateway. Returns trimmed text, or nil on ANY error
     (fail-open). Short timeout — a digest must never block the heartbeat."
     [messages]
     (try
       (let [body (json/generate-string
                   {:model model :messages messages :stream false
                    :temperature 0.6 :max_tokens 220})
             r (http/post gateway {:headers {"Content-Type" "application/json"}
                                   :body body :timeout 8000})
             txt (-> (json/parse-string (:body r))
                     (get-in ["choices" 0 "message" "content"]) (or "")
                     str/trim)]
         (when-not (str/blank? txt) txt))
       (catch Exception _ nil))))

(defn template-digest
  "Deterministic fallback paragraph from the data (no inference). Honest: restates
  facts, never an urgency nudge."
  [analysis coverage]
  (let [routes (get analysis "routes")
        opening (top-opening analysis)
        greenest (when (seq routes) (apply min-key #(get % "greenest_co2_kg") routes))]
    (str "現在 " (count routes) " 路線を観測。"
         (if (seq opening)
           (str "競争の薄い(:opening)路線が " (count opening) " 本 — "
                (str/join "・" (map #(get % "route") (take 3 opening)))
                " など(代替提示の対象、目的は競争の開放であって誘導ではない)。")
           "全路線で複数社が競合(:served)。")
         (when greenest
           (str " 最も低CO₂の路線は " (get greenest "route")
                " (" (get greenest "greenest_co2_kg") "kg, " (get greenest "greenest_carrier") ")。"))
         " 空港カバレッジ " (get coverage "airports_have") "/" (get coverage "airports_target")
         "。運賃は手荷物込みの実額で比較し、CO₂ は全 option に表示。手数料は取らない。")))

(defn digest
  "Produce the human digest. opts: {:infer-fn <messages->text|nil>} (defaults to the
  loopback Murakumo call; injectable for tests). Returns {:text <s> :source \"murakumo\"|\"template\"}."
  ([analysis coverage] (digest analysis coverage {}))
  ([analysis coverage {:keys [infer-fn]}]
   (let [f (or infer-fn #?(:clj murakumo-infer :cljs (constantly nil)))
         live (try (f (build-messages analysis coverage)) (catch #?(:clj Exception :cljs :default) _ nil))]
     (if (and live (not (str/blank? live)))
       {:text live :source "murakumo"}
       {:text (template-digest analysis coverage) :source "template"}))))

#?(:clj
   (defn -main [& args]
     (let [seed (or (first args) "20-actors/tsubasa/data/seed-fares.kotoba.edn")
           rows (edn/read-string (slurp seed))
           analysis (analyze/analyze rows) coverage (analyze/coverage rows)
           {:keys [text source]} (digest analysis coverage)]
       (println (str ";; tsubasa digest — source=" source))
       (println text))))

#?(:clj
   (when (= *file* (System/getProperty "babashka.file"))
     (apply -main *command-line-args*)))
