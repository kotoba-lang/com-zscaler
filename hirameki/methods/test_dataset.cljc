(ns hirameki.methods.test-dataset
  (:require [clojure.test :refer [deftest is run-tests]]
            [clojure.string :as str]
            [hirameki.methods.hirameki-edn :as he]
            [hirameki.methods.cid :as cid]
            [hirameki.methods.dataset :as ds]))

(def patents (he/patents "20-actors/hirameki/kotoba/seed.edn"))

(deftest materialize-deterministic-and-content-addressed
  (let [m1 (ds/materialize patents)
        m2 (ds/materialize patents)]
    (is (= m1 m2) "same corpus → byte-identical artifacts → same CID (idempotent)")
    (is (str/starts-with? (get-in m1 [:corpus :cid]) "bafkrei"))
    (is (str/starts-with? (get-in m1 [:datoms :cid]) "bafkrei"))
    (is (= (get-in m1 [:corpus :cid]) (cid/cidv1-raw (get-in m1 [:corpus :content])))
        "manifest CID = content-address of the bytes")))

(deftest corpus-is-sorted-and-normalized
  (let [c (ds/corpus-edn patents)
        ids (->> (str/split-lines c)
                 (remove #(str/starts-with? (str/triml %) ";;"))
                 (remove str/blank?)
                 (map #(:id (read-string %))))]
    (is (= ids (sort ids)) "corpus rows sorted by id (deterministic)")))

(deftest g2-corpus-has-no-imposes
  ;; a patent record is the gated object — it never carries an imposes/holder edge
  (let [c (ds/corpus-edn patents)]
    (is (not (str/includes? c ":imposes")))
    (is (not (str/includes? c "imposes-on")))))

(deftest g6-no-inventor-person-field
  (let [c (ds/corpus-edn patents)]
    (is (not (str/includes? c ":inventor")))
    (is (not (str/includes? c ":person")))))

(deftest manifest-shape
  (let [man (ds/publish-manifest (ds/materialize patents) "2026-06-21T00:00:00Z")]
    (is (= "hirameki" (get man "actor")))
    (is (= "2606212200" (get man "adr")))
    (is (str/includes? (get man "scope") "RELEASE map"))
    (is (get-in man ["artifacts" "corpus" "cid"]))
    (is (true? (get-in man ["single_block" "corpus"])) "R0 snapshot is a single raw block")))

#?(:clj
   (let [{:keys [fail error]} (run-tests 'hirameki.methods.test-dataset)]
     (when (pos? (+ fail error)) (System/exit 1))))
