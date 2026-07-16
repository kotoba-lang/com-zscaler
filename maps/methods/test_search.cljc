(ns maps.methods.test-search
  "test_search.py — kotoba-native name search tests (ADR-2606064500 R2). unittest → clojure.test.
  1:1 port. TestTokenizer is pure; TestSearch runs the write→read loop against the in-process
  kotoba stand-in server (h3-independent). The Python __main__ runner is omitted."
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [clojure.set :as set]
            [maps.methods.search :as search]
            [maps.methods.ingest :as ingest]
            #?(:clj [maps.methods.kotoba-local-server :as kls])))

;; ── TestTokenizer (pure) ──────────────────────────────────────────────────────
(deftest test-ascii-prefixes-stored
  (let [toks (search/name-tokens "Tokyo")]
    (is (contains? toks "to"))
    (is (contains? toks "tok"))
    (is (contains? toks "tokyo"))
    (is (not (contains? toks "t")))))

(deftest test-query-probes-whole-word
  (is (= (search/query-tokens "Tok") #{"tok"})))

(deftest test-cjk-bigrams
  (is (= (search/name-tokens "東京駅") #{"東京" "京駅"}))
  (is (= (search/query-tokens "東京") #{"東京"})))

(deftest test-prefix-match-contract
  (is (set/subset? (search/query-tokens "tok") (search/name-tokens "Tokyo Tower"))))

;; ── TestSearch (HTTP loop against the in-process stand-in) ─────────────────────
(def ^:private token "member-did")
(def ^:private feats
  {"f.station.tokyo"     {":feature/id" "f.station.tokyo" ":feature/label" ":station" ":feature/name" "Tokyo Station" ":feature/sourcing" ":representative"}
   "f.place.tokyotower"  {":feature/id" "f.place.tokyotower" ":feature/label" ":place" ":feature/name" "Tokyo Tower" ":feature/sourcing" ":representative"}
   "f.bldg.marunouchi"   {":feature/id" "f.bldg.marunouchi" ":feature/label" ":building" ":feature/name" "Marunouchi Building" ":feature/sourcing" ":representative"}
   "f.station.tokyo-jp"  {":feature/id" "f.station.tokyo-jp" ":feature/label" ":station" ":feature/name" "東京駅" ":feature/sourcing" ":representative"}
   "f.station.shinjuku"  {":feature/id" "f.station.shinjuku" ":feature/label" ":station" ":feature/name" "新宿駅" ":feature/sourcing" ":representative"}})

#?(:clj (def ^:private server (atom nil)))
#?(:clj (def ^:private base (atom nil)))

#?(:clj
   (defn- with-server [f]
     (let [srv (kls/serve 0 token)]
       (kls/serve-forever srv)
       (reset! server srv)
       (reset! base (str "http://127.0.0.1:" (:port srv)))
       (ingest/push-batch (ingest/to-kg-batch feats) token @base)
       (try (f)
            (finally (kls/shutdown srv))))))

#?(:clj (use-fixtures :once with-server))

(deftest test-ascii-prefix-search
  #?(:clj
     (is (= (set (map :name (search/search-places @base "tok")))
            #{"Tokyo Station" "Tokyo Tower"}))))

(deftest test-ranking-more-tokens-first
  #?(:clj
     (let [rows (search/search-places @base "tokyo tower")]
       (is (= (:name (first rows)) "Tokyo Tower"))
       (is (> (:score (first rows)) (:score (last rows)))))))

(deftest test-label-filter
  #?(:clj
     (is (= (set (map :name (search/search-places @base "tokyo" :labels ["station"])))
            #{"Tokyo Station"}))))

(deftest test-cjk-search
  #?(:clj
     (is (= (set (map :name (search/search-places @base "東京")))
            #{"東京駅"}))))

(deftest test-other-word-matches-marunouchi
  #?(:clj
     (is (= (set (map :name (search/search-places @base "marun")))
            #{"Marunouchi Building"}))))

(deftest test-unknown-query-empty
  #?(:clj (is (= (search/search-places @base "zzqq") []))))

(deftest test-limit
  #?(:clj (is (= (count (search/search-places @base "tok" :limit 1)) 1))))

(deftest test-endpoint-down-fails-soft
  (is (= (search/search-places "http://127.0.0.1:1" "tok") [])))
