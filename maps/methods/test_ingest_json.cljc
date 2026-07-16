(ns maps.methods.test-ingest-json
  "Focused tests for the hand-rolled, inlined JSON codec + EDN renderer in
  ingest.cljc. These are exercised only incidentally by test_methods (via the
  sample fixtures); a hand-written parser/encoder is bug-prone at the edges
  (escapes, unicode, nested, ints vs floats), so cover them directly."
  (:require [clojure.test :refer [deftest is]]
            [clojure.string :as str]
            [maps.methods.ingest :as ingest]))

;; ── json-encode (scalar + container types) ───────────────────────────────────

(deftest test-json-encode-scalars
  (is (= "null" (ingest/json-encode nil)))
  (is (= "true" (ingest/json-encode true)))
  (is (= "false" (ingest/json-encode false)))
  (is (= "42" (ingest/json-encode 42)))
  (is (= "\"hi\"" (ingest/json-encode "hi"))))

(deftest test-json-encode-escapes
  (is (= "\"a\\\"b\"" (ingest/json-encode "a\"b")))      ; embedded quote
  (is (= "\"a\\\\b\"" (ingest/json-encode "a\\b")))      ; embedded backslash
  (is (str/includes? (ingest/json-encode "line1\nline2") "\\n")))

(deftest test-json-encode-containers
  (is (= "[1, 2, 3]" (ingest/json-encode [1 2 3])))
  (is (= "{\"a\": 1}" (ingest/json-encode {"a" 1})))
  (is (= "[]" (ingest/json-encode [])))
  (is (= "{}" (ingest/json-encode {}))))

;; ── parse-json (roundtrip + edges) — #?(:clj) only ───────────────────────────

#?(:clj
   (deftest test-parse-json-scalars-and-types
     (is (= true (ingest/parse-json "true")))
     (is (= false (ingest/parse-json "false")))
     (is (= nil (ingest/parse-json "null")))
     (is (= 42 (ingest/parse-json "42")))
     (is (instance? Long (ingest/parse-json "42")))
     (is (= 3.14 (ingest/parse-json "3.14")))
     (is (instance? Double (ingest/parse-json "3.14")))
     (is (= "hi" (ingest/parse-json "\"hi\"")))))

#?(:clj
   (deftest test-parse-json-objects-and-arrays
     (is (= {"a" 1 "b" [2 3]} (ingest/parse-json "{\"a\": 1, \"b\": [2, 3]}")))
     (is (= [] (ingest/parse-json "[]")))
     (is (= {} (ingest/parse-json "{}")))
     (is (= {"nested" {"deep" true}}
            (ingest/parse-json "{\"nested\": {\"deep\": true}}")))))

#?(:clj
   (deftest test-parse-json-string-escapes
     (is (= "a\"b" (ingest/parse-json "\"a\\\"b\"")))     ; escaped quote
     (is (= "a\\b" (ingest/parse-json "\"a\\\\b\"")))     ; escaped backslash
     (is (= "tab\there" (ingest/parse-json "\"tab\\there\"")))
     (is (= "newline\nhere" (ingest/parse-json "\"newline\\nhere\"")))))

#?(:clj
   (deftest test-parse-json-unicode-escape
     (is (= "A" (ingest/parse-json "\"\\u0041\"")))       ; A = 'A'
     (is (= "あ" (ingest/parse-json "\"\\u3042\"")))))    ; hiragana

#?(:clj
   (deftest test-json-roundtrip-stable
     (let [v {"name" "shibuya \"crossing\"" "h" 12 "ratio" 0.5
              "tags" ["a" "b"] "meta" {"k" "v\nx"}}]
       (is (= v (ingest/parse-json (ingest/json-encode v)))))))

#?(:clj
   (deftest test-json-whitespace-tolerant
     (is (= {"a" 1} (ingest/parse-json "  {  \"a\" :  1  }  ")))))

;; ── render-features-edn (EDN renderer: keyword passthrough, escapes, sort) ────

(deftest test-render-features-edn-keyword-passthrough-and-sort
  (let [feats {":feature/c" (array-map ":feature/id" ":feature/c"
                                       ":feature/label" ":building"
                                       ":feature/name" "C")
               ":feature/a" (array-map ":feature/id" ":feature/a"
                                       ":feature/label" ":station"
                                       ":feature/name" "A")}
        edn (ingest/render-features-edn feats)]
    ;; keywords are emitted verbatim (not quoted), strings are quoted
    (is (str/includes? edn ":feature/label :building"))
    (is (str/includes? edn ":feature/name \"A\""))
    ;; ids sorted: a before c
    (is (< (.indexOf edn ":feature/a") (.indexOf edn ":feature/c")))
    ;; well-formed vector wrapper
    (is (str/starts-with? (str/triml edn) ";;"))
    (is (str/includes? edn "["))))

(deftest test-render-features-edn-escapes-quotes-in-strings
  (let [feats {":feature/x" (array-map ":feature/id" ":feature/x"
                                       ":feature/name" "say \"hi\"")}
        edn (ingest/render-features-edn feats)]
    (is (str/includes? edn "\\\""))))
