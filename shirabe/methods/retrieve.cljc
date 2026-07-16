(ns shirabe.methods.retrieve
  "shirabe 調べ — evidence retrieval (the SEARCH/FETCH leg of the ReAct loop). ADR-2606131600.

  Runs each plan sub-query through an injected `fetcher`, dedups + ranks results by
  query-token overlap, content-addresses each snippet for provenance, and stamps it with
  an injected `retrieved-at`. The result is the EVIDENCE synthesize may ground its answer in.

  CONSTITUTIONAL:
    G1 — read-only public web. The fetcher MUST be a read-only public-web search/fetch.
      shirabe never authenticates, submits a form, books, buys, or transacts — it LOOKS UP.
    G3 — no personalization / no surveillance. Retrieval carries the query and nothing
      about who asked. No cookies, no profile, no behavioural ranking.
    G5 — bounded + sourced. Results are capped (top-k) and every snippet keeps its source
      URL + retrieved-at; nothing is fabricated.
    G7 — the loop does no network I/O by default. `fetcher` is REQUIRED and injected:
      tests pass a fixture fetcher; the live web leg (live.clj) is an operator/member step.

  A `fetcher` is any fn `(fn [query] -> [{:title _ :url _ :snippet _} ...])`."
  (:require [clojure.string :as str]
            [clojure.set :as set]))

(def top-k 6)   ;; G5 — bounded evidence; a synthesis prompt is not a firehose

(defn- sha256-hex [^String s]
  (let [md (java.security.MessageDigest/getInstance "SHA-256")]
    (apply str (map #(format "%02x" (bit-and % 0xff)) (.digest md (.getBytes s "UTF-8"))))))

(defn- cid
  "Content address a snippet (sha256, raw) — provenance + dedup, kotoba 'b'-prefix parity."
  [text]
  (str "b" (subs (sha256-hex (or text "")) 0 32)))

(defn- tokens [s]
  (set (re-seq #"[0-9A-Za-z]+|[぀-ヿ㐀-鿿豈-﫿]" (str/lower-case (str s)))))

(defn- overlap [query item]
  (count (set/intersection (tokens query)
                           (set/union (tokens (:title item)) (tokens (:snippet item))))))

(defn retrieve
  "Run the plan's sub-queries through `fetcher`; return ranked, de-duped, sourced evidence.
  `fetcher` is REQUIRED (G7). `retrieved-at` is injected for determinism (no wall clock)."
  [plan fetcher retrieved-at]
  (when (nil? fetcher)
    (throw (ex-info (str "shirabe.retrieve: a read-only public-web `fetcher` must be injected "
                         "(G7 — the loop performs no implicit network I/O). See live.clj.")
                    {:gate :G7})))
  (let [subs (or (seq (:subqueries plan)) [(:question plan)])
        by-url (reduce
                (fn [acc q]
                  (let [hits (try (or (fetcher q) []) (catch Exception _ []))]  ;; fail-soft per query (G5)
                    (reduce
                     (fn [acc h]
                       (let [url (str/trim (str (:url h "")))]
                         (if (str/blank? url)
                           acc
                           (let [score (overlap q h) prev (get acc url)]
                             (if (or (nil? prev) (> score (:_score prev)))
                               (assoc acc url {:query q
                                               :title (str/trim (str (:title h "")))
                                               :url url
                                               :snippet (str/trim (str (:snippet h "")))
                                               :retrieved-at retrieved-at
                                               :_score score})
                               acc)))))
                     acc hits)))
                {} subs)
        ranked (->> (vals by-url)
                    (sort-by (juxt (comp - :_score) :url))   ;; score desc, url asc — deterministic
                    (take top-k))]
    (vec (map-indexed (fn [i e]
                        (-> e (dissoc :_score)
                            (assoc :rank (inc i) :snippet-cid (cid (:snippet e)))))
                      ranked))))
