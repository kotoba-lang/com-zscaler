(ns hinagata.tests.test-wasm
  "hinagata 雛形 — WASM component entry tests (ADR-2606111954). 1:1 Clojure port of
  tests/test_wasm.py (pytest → clojure.test). Pure stdlib, NETWORK-FREE.

  Verifies the four `wasm/app.cljc` export bodies produce valid output from the dev seed.

  The Python __main__ demo runner is intentionally omitted (no behaviour, just printing)."
  (:require [clojure.test :refer [deftest is run-tests]]
            [clojure.string :as str]
            [clojure.set]
            [hinagata.wasm.app :as app]))

(deftest test-analyze-export-shape
  (let [out (app/parse-json (app/analyze))]
    (is (= (set (keys out)) #{"grounded" "reuse" "statute_pull"}))
    (is (seq (get out "grounded")) "no groundedness rows")
    (let [top (first (get out "grounded"))]
      (is (clojure.set/subset? #{"id" "label" "score"} (set (keys top)))))
    (let [scores (mapv #(get % "score") (get out "grounded"))]
      (is (= scores (vec (sort #(compare %2 %1) scores)))))))

(deftest test-datoms-export-is-eavt-edn
  (let [edn (app/datoms 7)]
    (is (and (str/starts-with? (str/triml edn) ";;") (str/includes? edn "[")))
    (is (str/includes? edn " 7 :add]"))                 ;; tx threads through ground datoms
    (is (str/includes? edn ":bond/is-transient true")))) ;; derived readouts flagged transient

(deftest test-coverage-export-is-markdown
  (let [md (app/coverage)]
    (is (and (str/starts-with? md "# hinagata")
             (str/includes? md "coverage of all template families")))))

(deftest test-envelope-export-builds-unsigned-record
  (let [out (app/parse-json (app/envelope "tmpl.nda-mutual"
                                          "did:web:etzhayyim.com:actor:x"
                                          "did:plc:alice,did:plc:bob"))]
    (is (and (contains? out "document") (contains? out "envelope")))
    (let [env (get out "envelope")]
      (is (= (get env "$type") "com.etzhayyim.esign.envelope"))
      (is (= (get env "status") "pending"))              ;; UNSIGNED — member signs client-side
      (is (= (get env "signers") ["did:plc:alice" "did:plc:bob"]))
      (is (str/starts-with? (get env "documentCid") "bafkrei")))))

(deftest test-exports-are-deterministic
  (is (= (app/analyze) (app/analyze)))
  (is (= (app/datoms 1) (app/datoms 1)))
  (is (= (app/envelope "tmpl.dpa-gdpr" "did:web:x" "did:plc:a")
         (app/envelope "tmpl.dpa-gdpr" "did:web:x" "did:plc:a"))))

#?(:clj (defn -main [& _] (run-tests 'hinagata.tests.test-wasm)))
