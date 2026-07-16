(ns maps.tests.test-search
  "maps — search method tests (ADR-2606064500). stdlib; network-free.
  1:1 Clojure port of test_methods.py (TestSearch) + test_methods.py tokenizer tests."
  (:require [clojure.test :refer [deftest is run-tests]]
            [maps.methods.search    :as search-ns]
            [maps.methods.ingest    :as ingest]
            [maps.tests.kotoba-local :as kl]))

;; ── tokenizer tests (pure, no store needed) ──────────────────────────────────

(deftest test-name-tokens-ascii
  (let [toks (search-ns/name-tokens "Tokyo")]
    ;; "tokyo" → prefixes 2..5: "to" "tok" "toky" "tokyo"
    (is (contains? toks "to"))
    (is (contains? toks "tok"))
    (is (contains? toks "toky"))
    (is (contains? toks "tokyo"))))

(deftest test-name-tokens-cjk
  (let [toks (search-ns/name-tokens "東京駅")]
    ;; "東京駅" → bigrams "東京" "京駅"
    (is (contains? toks "東京"))
    (is (contains? toks "京駅"))))

(deftest test-name-tokens-short-ascii-ignored
  ;; 1-char ASCII word — no prefix of length ≥2
  (let [toks (search-ns/name-tokens "a")]
    (is (empty? toks))))

(deftest test-query-tokens-truncate
  ;; query_tokens truncates to max-prefix (12) for a long ASCII word
  (let [toks (search-ns/query-tokens "internationalization")]
    (is (contains? toks "internationa"))
    ;; 12 chars max
    (is (every? #(<= (count %) 12) toks))))

(deftest test-query-tokens-cjk-bigrams
  (let [toks (search-ns/query-tokens "東京")]
    (is (contains? toks "東京"))))

(deftest test-tokenizer-parity-with-ingest
  ;; The read-path query-tokens must share bigrams/prefixes with write-path name-tokens
  ;; so that a query "tok" finds a feature named "Tokyo".
  (let [name-tok (search-ns/name-tokens "Tokyo")
        query-tok (search-ns/query-tokens "tok")]
    (is (seq (clojure.set/intersection name-tok query-tok))
        "query 'tok' must share ≥1 token with name-tokens('Tokyo')")))

;; ── search-places tests (in-memory store) ────────────────────────────────────

(defn- make-search-store []
  (let [feats {"st.tokyo"    {":feature/id"    "st.tokyo"
                               ":feature/label" ":station"
                               ":feature/name"  "Tokyo Station"
                               ":feature/sourcing" ":representative"}
                "pl.shibuya"  {":feature/id"    "pl.shibuya"
                               ":feature/label" ":place"
                               ":feature/name"  "Shibuya"
                               ":feature/sourcing" ":representative"}
                "pl.shinjuku" {":feature/id"    "pl.shinjuku"
                               ":feature/label" ":place"
                               ":feature/name"  "Shinjuku"
                               ":feature/sourcing" ":representative"}}
        ;; stamp name-tokens manually (mirrors to-kg-batch)
        store (kl/make-store)]
    (kl/ingest-batch!
     store
     {"entities"
      (mapv (fn [[fid f]]
              (let [toks (search-ns/name-tokens (get f ":feature/name" ""))]
                {"id"     fid
                 "claims" (into (mapv (fn [[k v]]
                                        {"pred" (subs (str k) 1) "value" (str v)})
                                      (dissoc f ":feature/id"))
                                (map (fn [t] {"pred" "feature/name-token" "value" t})
                                     (sort toks)))}))
            feats)})
    store))

(deftest test-search-places-finds-tokyo
  (let [store (make-search-store)
        qfn   (kl/make-query-fn store)
        results (search-ns/search-places qfn "tok")]
    (is (pos? (count results)) "expected search results for 'tok'")
    (is (some #(= "st.tokyo" (:id %)) results) "Tokyo Station must appear for query 'tok'")))

(deftest test-search-places-label-filter
  (let [store   (make-search-store)
        qfn     (kl/make-query-fn store)
        results (search-ns/search-places qfn "sh" :labels [":place"])]
    ;; Only :place features should appear
    (doseq [r results]
      (is (= ":place" (:label r)) "label filter must only return :place features"))))

(deftest test-search-places-empty-query
  (let [store (make-search-store)
        qfn   (kl/make-query-fn store)]
    ;; Empty / too-short query → nil or empty
    (is (empty? (or (search-ns/search-places qfn "") [])))))

(deftest test-search-places-cjk
  (let [store   (make-search-store)
        qfn     (kl/make-query-fn store)
        results (search-ns/search-places qfn "東京")]
    ;; "東京" may not match English-only seed — just assert no crash + returns seq
    (is (sequential? results))))

#?(:clj
   (when (= *ns* (find-ns 'maps.tests.test-search))
     (run-tests)))
