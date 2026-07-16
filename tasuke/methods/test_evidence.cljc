(ns tasuke.methods.test-evidence
  "Tests for 助 (tasuke) evidence preservation — G6 PII-by-reference + chain-of-custody.
  1:1 port of `methods/test_evidence.py` (pytest → clojure.test)."
  (:require [clojure.test :refer [deftest is run-tests]]
            [tasuke.methods.evidence :as ev]))

(defn- item [& {:as over}]
  (merge {":evidence/id" "e1" ":evidence/case" "c1" ":evidence/kind" ":screenshot"
          ":evidence/envelope-ref" "ipfs://bafyX" ":evidence/bytes" "abc"
          ":evidence/captured-at" 100}
         over))

;; ── G6 plaintext PII is unrepresentable ──────────────────────────────────────
(deftest test-plaintext-pii-field-refused
  (doseq [f [":evidence/plaintext" ":evidence/raw" ":evidence/pii"]]
    (is (thrown-with-msg? #?(:clj Exception :cljs js/Error) #"G6"
          (ev/preserve (assoc (item) f "secret data"))))))

(deftest test-envelope-ref-required
  (is (thrown-with-msg? #?(:clj Exception :cljs js/Error) #"envelope-ref"
        (ev/preserve (item ":evidence/envelope-ref" "")))))

;; ── chain-of-custody hash ────────────────────────────────────────────────────
(deftest test-preserve-hashes-bytes
  (let [r (ev/preserve (item))]
    (is (= (ev/sha256-hex "abc") (get r ":evidence/sha256")))
    (is (not (contains? r ":evidence/bytes")))))   ;; raw bytes discarded, never stored

(deftest test-precomputed-hash-kept
  (let [r (ev/preserve (item ":evidence/sha256" "deadbeef" ":evidence/bytes" nil))]
    (is (= "deadbeef" (get r ":evidence/sha256")))))

(deftest test-unknown-kind-refused
  (is (thrown-with-msg? #?(:clj Exception :cljs js/Error) #"unknown evidence kind"
        (ev/preserve (item ":evidence/kind" ":telepathy")))))

(deftest test-index-sorts-by-capture-time
  (let [rows (ev/index [(item ":evidence/id" "b" ":evidence/captured-at" 200)
                        (item ":evidence/id" "a" ":evidence/captured-at" 100)])]
    (is (= ["a" "b"] (mapv #(get % ":evidence/id") rows)))))

(deftest test-custody-intact-detects-tamper
  (let [r (ev/preserve (item))]
    (is (= true (ev/custody-intact? r "abc")))
    (is (= false (ev/custody-intact? r "tampered")))))

#?(:clj (defn -main [& _] (run-tests 'tasuke.methods.test-evidence)))
