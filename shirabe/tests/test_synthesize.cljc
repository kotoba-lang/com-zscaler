(ns shirabe.tests.test-synthesize
  "shirabe — synthesize tests (G2 Murakumo-only allowlist / prompt / citations). kotoba-clj."
  (:require [clojure.test :refer [deftest is]]
            [clojure.string :as str]
            [shirabe.methods.synthesize :as s]))

(def ev [{:rank 1 :title "T1" :url "https://x/1" :snippet "snip one" :retrieved-at "d"}
         {:rank 2 :title "T2" :url "https://x/2" :snippet "snip two" :retrieved-at "d"}])

(deftest g2-allowlist
  (is (thrown? Exception (s/validate-host! "https://api.openai.com")) "commercial host rejected (G2)")
  (is (thrown? Exception (s/validate-host! "https://api.anthropic.com")))
  (is (= "127.0.0.1:11434" (s/validate-host! "http://127.0.0.1:11434")) "local Ollama allowed")
  (is (= "127.0.0.1:4000" (s/validate-host! "http://127.0.0.1:4000")) "Murakumo LiteLLM allowed")
  (is (= "192.168.1.70:4000" (s/validate-host! "http://192.168.1.70:4000")) "EVO-X2 allowed"))

(deftest prompt-has-rules-and-sources
  (let [p (s/build-prompt "Q?" ev :ja)]
    (is (str/includes? p "INSUFFICIENT") "the non-fabrication rule is in the prompt (G4)")
    (is (str/includes? p "[1]"))
    (is (str/includes? p "snip one"))
    (is (str/includes? p "https://x/1"))))

(deftest synth-parses-citations
  (let [infer (with-meta (fn [_] "答えは [1] です。[2] も参照。") {:model-id "g@127.0.0.1:11434"})
        r (s/synthesize "Q?" ev infer :ja)]
    (is (= [1 2] (:citations r)))
    (is (false? (:insufficient r)))
    (is (true? (:charter-ok r)))
    (is (= "g@127.0.0.1:11434" (:model r)))))

(deftest synth-insufficient
  (is (true? (:insufficient (s/synthesize "Q?" ev (fn [_] "INSUFFICIENT 出典に情報なし") :ja)))))

(deftest synth-requires-infer
  (is (thrown? Exception (s/synthesize "Q?" ev nil :ja)) "infer required (G7)"))

(deftest synth-empty-evidence
  (is (true? (:insufficient (s/synthesize "Q?" [] (fn [_] "x") :ja)))))
