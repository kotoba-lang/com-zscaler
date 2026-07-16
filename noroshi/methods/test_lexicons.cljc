(ns noroshi.methods.test-lexicons
  "Lexicon + cell-descriptor well-formedness tests for noroshi (烽) — ADR-2606051600.
  1:1 Clojure port of methods/test_lexicons.py (pytest → clojure.test).

  Reads EDN via the inlined `_edn` reader behind #?(:clj …)."
  (:require [clojure.test :refer [deftest is run-tests]]
            [clojure.string :as str]
            [clojure.set :as set]
            [noroshi.methods._edn :as edn]))

#?(:clj (def ^:private actor-root (-> (java.io.File. *file*) .getParentFile .getParentFile)))
#?(:clj (defn- list-edn [dir]
          (->> (.listFiles (java.io.File. actor-root dir))
               (filter #(str/ends-with? (.getName %) ".edn"))
               (sort-by #(.getName %)))))
#?(:clj (def ^:private lex-files (list-edn "lex")))
#?(:clj (def ^:private cell-files (list-edn "cells")))
(def ^:private faces #{":chip" ":isac" ":packaging" ":operation" ":learning"})

#?(:clj (defn- stem [file] (str/replace (.getName file) #"\.edn$" "")))
#?(:clj
   (defn- gate-ids []
     (set (map #(get % ":gate/id") (get (edn/load-edn (java.io.File. actor-root "manifest.edn")) ":actor/gates")))))

;; ── lexicons ─────────────────────────────────────────────────────────────────
(deftest test-lexicon-well-formed
  (doseq [lf lex-files]
    (let [d (edn/load-edn lf)]
      (is (= (get d ":lexicon") 1))
      (is (= (get d ":id") (str "com.etzhayyim.noroshi." (stem lf))))
      (let [main (get-in d [":defs" ":main"])]
        (is (= (get main ":type") "record"))
        (is (and (string? (get main ":key")) (seq (get main ":key"))))
        (let [rec (get main ":record")]
          (is (= (get rec ":type") "object"))
          (let [req (set (get rec ":required" []))
                props (set (map #(str/replace % #"^:+" "") (clojure.core/keys (get rec ":properties" {}))))]
            (is (set/subset? req props) (str (stem lf) ": required not in properties: " (sort (set/difference req props))))
            (doseq [[pname pdef] (get rec ":properties")]
              (is (contains? pdef ":type") (str (stem lf) "." pname " has no :type")))))))))

(deftest test-all-five-lexicons-present
  (is (= (set (map stem lex-files))
         #{"photonicDevice" "opticalLinkBudget" "isacWaveform" "senseEstimate" "packagingJob"})))

;; ── cell descriptors ─────────────────────────────────────────────────────────
(deftest test-cell-descriptor-well-formed
  (doseq [cf cell-files]
    (let [c (edn/load-edn cf)]
      (is (= (get c ":cell/id") (stem cf)))
      (is (contains? faces (get c ":cell/face")))
      (is (= (get c ":cell/kind") ":langgraph"))
      (is (= (get c ":cell/runtime") ":wasm"))
      (is (and (string? (get c ":cell/node")) (seq (get c ":cell/node"))))
      (is (set/subset? (set (get c ":cell/gates" [])) (gate-ids))))))

(deftest test-llm-cells-are-murakumo-only
  (doseq [cf cell-files]
    (let [llm (get (edn/load-edn cf) ":cell/llm")]
      (when llm
        (is (= (get llm ":provider") ":murakumo"))
        (is (= (get llm ":endpoint") "127.0.0.1:4000"))
        (is (= (get llm ":charter-rider") true))))))

#?(:clj (defn -main [& _] (run-tests 'noroshi.methods.test-lexicons)))
