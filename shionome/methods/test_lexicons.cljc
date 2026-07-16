(ns shionome.methods.test-lexicons
  "Cross-language oracle tests for 潮目 lexicon well-formedness.
  1:1 port of methods/test_lexicons.py. ADR-2606072200. Reads lex/*.edn via the shared reader."
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.string :as str]
            [shionome.methods.edn :as edn]))

(def lex-dir "20-actors/shionome/lex")
(def names ["capitalFlowObservation.edn" "bucketSnapshot.edn" "rotationFinding.edn" "networkPost.edn"])
(defn- lex [n] (edn/load-edn (str lex-dir "/" n)))

(deftest all-load-and-have-id
  (doseq [n names]
    (let [l (lex n)]
      (is (= 1 (get l ":lexicon")))
      (is (str/starts-with? (str (get l ":id")) "com.etzhayyim.shionome.")))))

(deftest each-has-main-record
  (doseq [n names]
    (let [main (get-in (lex n) [":defs" ":main"])]
      (is (= "record" (get main ":type")))
      (is (contains? main ":record")))))

(deftest required-lists-nonempty
  (doseq [n names]
    (let [req (get-in (lex n) [":defs" ":main" ":record" ":required"])]
      (is (vector? req))
      (is (seq req)))))

(deftest namespaces-unique
  (let [ids (map #(get (lex %) ":id") names)]
    (is (= (count ids) (count (set ids))))))

(deftest capitalflow-required-has-no-trade-notice
  (let [req (get-in (lex "capitalFlowObservation.edn") [":defs" ":main" ":record" ":required"])]
    (is (some #(= "noTradeNotice" %) req))))
