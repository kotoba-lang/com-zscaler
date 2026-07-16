(ns maps.methods.search
  "maps — kotoba-native place name search (ADR-2606064500 R2). stdlib only.
  1:1 Clojure port of `methods/search.py`.

  Tokenizer (ONE fn, used by BOTH write [ingest's to-kg-batch] and read [search-places]):
    ASCII words → all PREFIXES length 2..12 stored.
    CJK runs   → all adjacent BIGRAMS (+ single char for length-1 runs).

  query-fn abstraction: `(fn [pred objects limit] → [{:id id :claims [...]}])`
    - production: wrap HTTP POST to kotoba graph.sparql AVET endpoint
    - tests: in-memory KotobaLocal atom

  Portable .cljc."
  (:require [clojure.string :as str]))

(def ^:private max-prefix 12)
(def ^:private query-nsid "com.etzhayyim.apps.kotoba.graph.sparql")

(defn- cjk? [ch]
  (let [o (int ch)]
    (or (<= 0x3040 o 0x30FF)   ; hiragana + katakana
        (<= 0x3400 o 0x9FFF)   ; CJK unified
        (<= 0xF900 o 0xFAFF)   ; CJK compat
        (<= 0xFF66 o 0xFF9D)))) ; halfwidth katakana

(defn- runs [name]
  (let [s (str/lower-case (or (str name) ""))
        out (transient []) buf (volatile! []) kind (volatile! nil)]
    (doseq [ch s]
      (let [k (cond (cjk? ch) :cjk
                    (Character/isLetterOrDigit ch) :ascii
                    :else nil)]
        (when (not= k @kind)
          (when (seq @buf)
            (conj! out [@kind (str/join @buf)]))
          (vreset! buf [])
          (vreset! kind k))
        (when k (vswap! buf conj ch))))
    (when (seq @buf)
      (conj! out [@kind (str/join @buf)]))
    (persistent! out)))

(defn- bigrams [s]
  (if (>= (count s) 2)
    (mapv #(subs s % (+ % 2)) (range (- (count s) 1)))
    [s]))

(defn name-tokens
  "INDEX tokens for a feature name (stored as :feature/name-token at ingest)."
  [name]
  (reduce
   (fn [toks [kind text]]
     (cond
       (and (= kind :ascii) (>= (count text) 2))
       (into toks (map #(subs text 0 %) (range 2 (inc (min (count text) max-prefix)))))
       (= kind :cjk)
       (into (into toks (bigrams text))
             (when (= 1 (count text)) [text]))
       :else toks))
   #{}
   (runs name)))

(defn query-tokens
  "PROBE tokens for a search query (a subset of index tokens)."
  [q]
  (reduce
   (fn [toks [kind text]]
     (cond
       (and (= kind :ascii) (>= (count text) 2))
       (conj toks (subs text 0 (min (count text) max-prefix)))
       (= kind :cjk)
       (into (into toks (bigrams text))
             (when (= 1 (count text)) [text]))
       :else toks))
   #{}
   (runs q)))

;; ── HTTP AVET helper ─────────────────────────────────────────────────────────
#?(:clj
   (defn http-avet-fn
     "Build a production query-fn that POSTs to the kotoba graph.sparql endpoint.
     Uses babashka.http-client, not raw HttpURLConnection — babashka's SCI sandbox
     disallows HttpURLConnection/setRequestMethod."
     [endpoint]
     (fn [pred objects limit]
       (try
         (let [body (str "{\"index\":\"avet\",\"predicate\":\""
                         pred "\",\"objects\":"
                         (str "[" (str/join "," (map (fn [o] (str "\"" o "\"")) objects)) "]")
                         ",\"limit\":" limit "}")
               post (requiring-resolve 'babashka.http-client/post)
               resp (post (str (str/replace endpoint #"/$" "") "/xrpc/" query-nsid)
                          {:headers {"content-type" "application/json"}
                           :body body
                           :timeout 5000
                           :throw false})
               parse (requiring-resolve 'cheshire.core/parse-string)]
           (get (parse (:body resp)) "entities" []))
         (catch Exception _ [])))))

(defn search-places
  "Name search ranked by query-token overlap.
  query-fn-or-endpoint: an injectable (fn [pred objects limit] → [{\"id\" id \"claims\" [...]}])
  for direct/test use, OR a kotoba endpoint URL string — wrapped via http-avet-fn (the
  production HTTP AVET path).
  labels: optional collection of kebab keyword strings to restrict results.
  Returns [{:id :name :label :score}], best first."
  [query-fn-or-endpoint query & {:keys [labels limit] :or {limit 20}}]
  (let [query-fn #?(:clj (if (string? query-fn-or-endpoint)
                           (http-avet-fn query-fn-or-endpoint)
                           query-fn-or-endpoint)
                     :cljs query-fn-or-endpoint)
        qt (query-tokens query)]
    (when (seq qt)
      (let [want (when labels
                   (set (map #(if (str/starts-with? (str %) ":") (str %) (str ":" %)) labels)))
            results
            (reduce
             (fn [acc e]
               (let [stored (transient #{}) name (volatile! nil) label (volatile! nil)]
                 (doseq [c (get e "claims" [])]
                   (let [p (get c "pred") v (get c "value")]
                     (cond
                       (= p "feature/name-token") (conj! stored v)
                       (= p "feature/name")       (vreset! name v)
                       (= p "feature/label")      (vreset! label v))))
                 (let [stored-p (persistent! stored)
                       hits     (clojure.set/intersection qt stored-p)]
                   (if (and (or (nil? want) (contains? want @label))
                            (pos? (count hits)))
                     (conj acc {:id    (get e "id")
                                :name  @name
                                :label @label
                                :score (count hits)})
                     acc))))
             []
             (query-fn "feature/name-token" (vec qt) 2000))]
        (take limit
              (sort-by (fn [r] [(- (:score r)) (or (:name r) (:id r) "")])
                       results))))))
