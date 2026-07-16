(ns kabuto.methods.transact
  "transact.py — kabuto 兜 kotoba Datomic transact bridge (ADR-2606022000). 1:1 Clojure port of
  `methods/transact.py`.

  Pushes the public-company supply-chain graph into a RUNNING kotoba node's Datom log via
  POST /xrpc/com.etzhayyim.apps.kotoba.datomic.transact, emitting datomic list-form datoms
  `[:db/add E A V]` (E = the entity's stable id; cardinality-many values fan out). Data is BATCHED
  (the graph exceeds the 1 MiB tx_edn limit as one tx).

  AUTH (ADR-2605231525, no platform-held key): a write needs EITHER an operator JWT (KOTOBA_TOKEN) OR
  a CACAO authorising datom:transact. Without either it is a DRY RUN that prints the tx summary.

  CONSTITUTIONAL (kabuto G2/G4): public-record company facts only; a resilience + transparency map,
  NEVER a target-list. kabuto does not adjudicate.

  House style: pure datom/edn builders; the Python ':…' keyword strings stay strings; HTTP + file I/O
  only behind #?(:clj …). SELF-CONTAINED: requires the actor's existing kabuto-edn sibling. (The
  Python `__main__` CLI + urllib POST are preserved behind the #?(:clj) edge as -main / post.)"
  (:require [clojure.string :as str]
            [kabuto.methods.kabuto-edn :as kedn]
            #?(:clj [clojure.java.io :as io])))

#?(:clj (def ^:private actor (-> *file* io/file .getAbsoluteFile .getParentFile .getParentFile)))

(def nsid-transact "com.etzhayyim.apps.kotoba.datomic.transact")
(def id-keys
  [":company/id" ":company.address/id" ":company.contact/id"
   ":supply.edge/id" ":company.process/id"])
(def batch 3500)  ; datoms per tx (keeps tx_edn well under the 1 MiB server cap)

(defn edn-val
  "Render a value as EDN (mirrors edn_val): bool→true/false, number→str, list→[…], :kw-string raw,
  other string→quoted, else stringified+quoted."
  [x]
  (cond
    (boolean? x) (if x "true" "false")
    (number? x) (if (integer? x) (str x) (str (double x)))
    (sequential? x) (str "[" (str/join " " (map edn-val x)) "]")
    (string? x) (if (str/starts-with? x ":") x (kedn/edn-str x))
    :else (kedn/edn-str (str x))))

(defn rows-to-datoms
  "Flatten entity rows → `[:db/add E A V]` strings (cardinality-many values fan out). Mirrors
  rows_to_datoms(rows)."
  [rows]
  (reduce
   (fn [out r]
     (if-not (map? r)
       out
       (let [e (some #(get r %) id-keys)]
         (if (nil? e)
           out
           (reduce
            (fn [out k]
              (if (some #{k} id-keys)
                out
                (let [v (get r k)
                      items (if (sequential? v) v [v])]
                  (reduce (fn [out item]
                            (conj out (str "[:db/add " (kedn/edn-str e) " " k " " (edn-val item) "]")))
                          out items))))
            out
            (keys r))))))
   []
   rows))

(defn schema-datoms
  "Render each ontology :attributes entry as a `{…}` EDN map literal, dropping :db/doc (the kotoba EDN
  reader rejects '|'). Mirrors schema_datoms(). `onto` is the loaded ontology map."
  [onto]
  (let [attrs (if (map? onto) (get onto ":attributes" []) [])]
    (mapv (fn [a]
            (str "{" (str/join " " (for [[k v] a :when (not= k ":db/doc")]
                                     (str k " " (edn-val v)))) "}"))
          attrs)))

(defn tx-edn
  "Wrap datom strings into a top-level EDN vector (mirrors _tx_edn)."
  [datoms]
  (str "[\n " (str/join "\n " datoms) "\n]"))

#?(:clj
   (defn- post
     "POST JSON body to `url` (operator JWT from KOTOBA_TOKEN). Returns [status parsed-body]."
     [url body]
     (let [conn (doto ^java.net.HttpURLConnection (.openConnection (java.net.URL. url))
                  (.setRequestMethod "POST")
                  (.setDoOutput true)
                  (.setConnectTimeout 60000)
                  (.setReadTimeout 60000)
                  (.setRequestProperty "Content-Type" "application/json"))
           _ (when-let [tok (System/getenv "KOTOBA_TOKEN")]
               (.setRequestProperty conn "Authorization" (str "Bearer " tok)))
           _ (with-open [os (.getOutputStream conn)]
               (.write os (.getBytes ^String body "UTF-8")))
           status (try (.getResponseCode conn) (catch Exception _ -1))
           stream (try (.getInputStream conn) (catch Exception _ (.getErrorStream conn)))
           txt (if stream (slurp stream) "")]
       [status txt])))

#?(:clj
   (defn -main
     "CLI entry — DRY-RUN by default; live write needs --graph <CID> + KOTOBA_TOKEN (or --cacao).
     File I/O + HTTP at the edge."
     [& argv]
     (let [argv (vec argv)
           schema-path (io/file (.getParentFile (.getParentFile actor)) "00-contracts" "schemas"
                                "public-company-ontology.kotoba.edn")
           graph (let [i (.indexOf argv "--graph")]
                   (if (>= i 0) (nth argv (inc i)) (System/getenv "KABUTO_GRAPH_CID")))
           cacao (let [i (.indexOf argv "--cacao")]
                   (if (>= i 0) (nth argv (inc i)) (System/getenv "KOTOBA_CACAO_B64")))
           url (or (System/getenv "KOTOBA_URL") "http://127.0.0.1:8077")
           merged0 (io/file actor "data" "companies.merged.kotoba.edn")
           merged (if (.exists merged0) merged0 (io/file actor "data" "seed-public-companies.kotoba.edn"))
           schema (schema-datoms (kedn/load-edn schema-path))
           data (rows-to-datoms (kedn/load-edn merged))
           batches (let [b (vec (partition-all batch data))] (if (seq b) b [[]]))]
       (println (str "kabuto.transact: graph=" (or graph "(unset)")))
       (println (str "  schema tx: " (count schema) " attrs  ·  data: " (count data)
                     " datoms in " (count batches) " batch(es)"))
       (let [live (and graph (or cacao (System/getenv "KOTOBA_TOKEN")) (not (some #{"--dry-run"} argv)))]
         (if-not live
           (do (println "  DRY RUN — provide --graph <CID> + KOTOBA_TOKEN operator JWT (or --cacao) to write.")
               0)
           (let [send (fn [name datoms fatal]
                        (let [body (str "{\"graph\":" (kedn/edn-str graph)
                                        ",\"tx_edn\":" (kedn/edn-str (tx-edn datoms))
                                        (if cacao (str ",\"cacao_b64\":" (kedn/edn-str cacao)) "") "}")
                              [st resp] (post (str url "/xrpc/" nsid-transact) body)]
                          (if (not= st 200)
                            (let [msg (str "!! transact " name " → " st ": " (subs resp 0 (min 160 (count resp))))]
                              (if fatal
                                (do (binding [*out* *err*] (println msg)) false)
                                (do (println (str msg "  (best-effort; continuing)")) true)))
                            (do (println (str "  ok " name)) true))))]
             (if-not (send "schema" schema false)
               1
               (loop [bs (map-indexed vector batches) total 0]
                 (if (empty? bs)
                   (do (println (str "  ✓ " total " company-graph datoms committed to " graph)) 0)
                   (let [[i b] (first bs)]
                     (if-not (send (str "data[" (inc i) "/" (count batches) "]") b true)
                       1
                       (recur (rest bs) (+ total (count b))))))))))))))
