(ns aburi.methods.kotoba-bridge
  "aburi 炙り → kotoba engine transact bridge.
  ADR-2606161630 (ibuki kotoba_bridge pattern, ADR-2606101200).

  Takes the member's local aburi exposure log (the append-only commit-DAG `autorun` built)
  and transacts the un-pushed transactions to the LIVE kotoba engine at :8077, one
  `datomic.transact` call per local tx, oldest first.

  Discipline:
    - host allowlist (loopback + EVO-X2 LAN) — any other endpoint throws BEFORE I/O;
    - durable `:aburi-bridge/*` cursor ON the local log → exactly-once per local tx;
    - every pushed tx carries `:aburi.tx/*` provenance (local tx-id / CID / prev / as-of);
    - previous push's remote commit_cid sent as expected_parent (optimistic concurrency);
    - no-server-key — unsigned public-DID operator bearer ONLY when ABURI_KOTOBA_OPERATOR_DID
      is set; no secret key is held or read;
    - DRY-RUN by default — live requires ABURI_KOTOBA_LIVE=1.
  HTTP is an injectable fn (*http-post* / :transport), defaulting to babashka.http-client."
  (:require [clojure.string :as str]
            [aburi.methods.kotoba :as kt]))

;; ─── constants ────────────────────────────────────────────────────────────────

(def allowed-kotoba-hosts
  #{"127.0.0.1:8077" "localhost:8077" "192.168.1.70:8077"})

(def default-endpoint
  "http://127.0.0.1:8077/xrpc/com.etzhayyim.apps.kotoba.datomic.transact")

(def default-graph  "aburi")
(def live-env       "ABURI_KOTOBA_LIVE")
(def operator-did-env "ABURI_KOTOBA_OPERATOR_DID")
(def base-as-of     2606161630)

;; ─── boundary guard ──────────────────────────────────────────────────────────

(defn- url-parts [endpoint]
  (if-let [[_ scheme netloc]
           (re-find #"^([A-Za-z][A-Za-z0-9+.\-]*)://([^/?#]*)" (str endpoint))]
    {:scheme (str/lower-case scheme) :netloc netloc}
    {:scheme nil :netloc nil}))

(defn assert-kotoba
  "Refuse any endpoint whose host:port is not in the kotoba fleet allowlist (http only).
  Throws before any I/O; returns nil on the fleet."
  [endpoint]
  (let [{:keys [scheme netloc]} (url-parts endpoint)]
    (when-not (and (= "http" scheme)
                   (contains? allowed-kotoba-hosts (some-> netloc str/lower-case)))
      (throw (ex-info (str "kotoba endpoint " (pr-str endpoint)
                           " is outside the fleet allowlist "
                           (vec (sort allowed-kotoba-hosts))
                           " — ADR-2605215000")
                      {:aburi/kotoba-boundary-violation true :endpoint endpoint})))))

;; ─── EDN rendering ───────────────────────────────────────────────────────────

(defn- edn-val ^String [v]
  (cond
    (true? v)        "true"
    (false? v)       "false"
    (number? v)      (str v)
    (string? v)      (if (str/starts-with? v ":") v (pr-str v))
    (sequential? v)  (str "[" (str/join " " (map edn-val v)) "]")
    :else            (pr-str (str v))))

(defn tx->edn-vec
  "Local tx → `tx_edn` string: [:db/add …] forms + :aburi.tx/* provenance meta.
  tx is the aburi kotoba.cljc string-keyed map: {':tx/id' … ':tx/datoms' […] …}"
  ^String [tx]
  (let [tx-id  (get tx ":tx/id")
        meta-e (str "aburi-tx-" tx-id)
        forms  (concat (get tx ":tx/datoms")
                       [[":db/add" meta-e ":aburi.tx/id"         (str tx-id)]
                        [":db/add" meta-e ":aburi.tx/local-cid"  (get tx ":tx/cid")]
                        [":db/add" meta-e ":aburi.tx/local-prev" (get tx ":tx/prev")]
                        [":db/add" meta-e ":aburi.tx/as-of"      (str (get tx ":tx/as-of"))]])]
    (str "[" (str/join " "
                       (map (fn [[op e a v]]
                              (str "[" op " " (pr-str e) " " a " " (edn-val v) "]"))
                            forms)) "]")))

;; ─── no-server-key operator bearer (public DID only, no key held) ─────────────

#?(:clj
   (defn- operator-bearer []
     (let [did #?(:clj (System/getenv operator-did-env) :default nil)]
       (when (or (nil? did) (str/blank? did))
         (throw (ex-info (str "live push requires " operator-did-env
                              " (the node's PUBLIC operator DID, not a secret)")
                         {:aburi/kotoba-boundary-violation true})))
       (let [b64 (fn [^String s]
                   (.encodeToString (.withoutPadding (java.util.Base64/getUrlEncoder))
                                    (.getBytes s "UTF-8")))
             cj  (fn [m]
                   (str "{" (str/join ","
                              (map (fn [[k v]]
                                     (str "\"" k "\":\"" v "\""))
                                   (sort-by first m)))
                        "}"))]
         (str (b64 (cj [["alg" "none"]]))
              "." (b64 (cj [["sub" did]]))
              ".unsigned-loopback")))))

;; ─── injectable HTTP edge ──────────────────────────────────────────────────────

#?(:clj
   (defn default-http-post [url body-map headers timeout-s]
     (let [post     (requiring-resolve 'babashka.http-client/post)
           generate (requiring-resolve 'cheshire.core/generate-string)
           parse    (requiring-resolve 'cheshire.core/parse-string)
           resp     (post (str url) {:headers headers
                                     :body    (generate body-map)
                                     :timeout (long (* 1000 (double timeout-s)))
                                     :throw   false})
           status   (:status resp)]
       (if (<= 200 status 299)
         (parse (:body resp) true)
         (throw (ex-info (str "kotoba transact HTTP " status ": "
                              (let [b (str (:body resp))]
                                (subs b 0 (min 200 (count b)))))
                         {:aburi/kotoba-transact-http-error true :status status}))))))

(def ^:dynamic *http-post*
  #?(:clj default-http-post :default nil))

#?(:clj
   (defn default-transport
     "POST a transact. Attach unsigned operator bearer when ABURI_KOTOBA_OPERATOR_DID is set."
     ([url body] (default-transport url body {}))
     ([url body {:keys [timeout-s http-post] :or {timeout-s 60.0}}]
      (assert-kotoba url)
      (let [bearer  (try (operator-bearer) (catch Exception _ nil))
            headers (cond-> {"Content-Type" "application/json"}
                      bearer (assoc "Authorization" (str "Bearer " bearer)))]
        ((or http-post *http-post*) url body headers timeout-s)))))

;; ─── durable push cursor ──────────────────────────────────────────────────────

(defn- bridge-tx? [tx]
  ;; aburi kotoba.cljc uses STRING map keys: (get tx ":tx/datoms")
  (boolean (some (fn [[_ _ a _]] (str/starts-with? (str a) ":aburi-bridge/"))
                 (get tx ":tx/datoms"))))

(defn bridge-state
  "Replay the durable push cursor from txs: {:pushed-to <last pushed tx-id> :parent-commit <remote cid>}.
  Works on aburi kotoba.cljc string-keyed tx maps."
  [txs]
  (reduce (fn [st tx]
            (reduce (fn [st [_ _ a v]]
                      (case a
                        ":aburi-bridge/pushed-to-tx"  (assoc st :pushed-to (if (number? v) (long v) v))
                        ":aburi-bridge/parent-commit"  (assoc st :parent-commit v)
                        st))
                    st (get tx ":tx/datoms")))
          {:pushed-to 0 :parent-commit ""}
          txs))

(defn- data-txs [txs] (vec (remove bridge-tx? txs)))

(defn pending-txs
  "Data txs not yet pushed (tx-id > pushed-to, bridge txs excluded)."
  [txs]
  (let [data      (data-txs txs)
        pushed-to (:pushed-to (bridge-state txs))]
    (vec (filter (fn [tx]
                   (let [id (get tx ":tx/id")]
                     (if (number? id)
                       (> (long id) (long pushed-to))
                       ;; string tx-id: pushed-to might also be a string
                       (let [pushed-str (str pushed-to)
                             idx (first (keep-indexed
                                          (fn [i t] (when (= (str (get t ":tx/id")) pushed-str) i))
                                          data))]
                         (if idx
                           (some #(= (get % ":tx/id") id)
                                 (drop (inc idx) data))
                           true)))))
                 data))))

#?(:clj (defn- env-live? [] (= "1" (System/getenv live-env))))

;; ─── push ─────────────────────────────────────────────────────────────────────

#?(:clj
   (defn push
     "Push every not-yet-sent local data tx (oldest first), one transact per tx.
     Live requires ABURI_KOTOBA_LIVE=1 or :live true; otherwise DRY-RUN.
     After a live push, ONE :aburi-bridge/* checkpoint tx is appended (exactly-once cursor).
     Options: :graph :endpoint :transport :live :http-post :as-of-base."
     ([log-path] (push log-path {}))
     ([log-path {:keys [graph endpoint transport live http-post as-of-base]
                 :or   {graph      default-graph
                        endpoint   default-endpoint
                        as-of-base base-as-of}}]
      (assert-kotoba endpoint)
      (let [txs     (kt/read-log log-path)
            state   (bridge-state txs)
            pending (pending-txs txs)
            bodies  (mapv (fn [tx] {:graph graph :tx_edn (tx->edn-vec tx)}) pending)
            is-live (if (some? live) (boolean live) (env-live?))]
        (if-not is-live
          {:mode        "dry-run"
           :pending     (count bodies)
           :bodies      bodies
           :pushed-to   (:pushed-to state)
           :head        (kt/head-cid log-path)}
          (loop [pairs         (map vector pending bodies)
                 remote-cids   []
                 last-commit   (:parent-commit state)
                 datoms-confirmed 0]
            (if-let [[tx body] (first pairs)]
              (let [body  (if (seq last-commit)
                            (assoc body :expected_parent last-commit)
                            body)
                    t-fn  (or transport
                               (fn [url bdy]
                                 (default-transport url bdy {:http-post http-post})))
                    out   (t-fn endpoint body)]
                (when-not (contains? #{"ok" "committed" "success"} (str (:status out)))
                  (throw (ex-info (str "kotoba transact refused tx " (:tx/id tx) ": " (pr-str out))
                                  {:aburi/kotoba-transact-refused true
                                   :tx/id (:tx/id tx)
                                   :out   out})))
                (recur (rest pairs)
                       (conj remote-cids (or (:tx_cid out) ""))
                       (or (:commit_cid out) last-commit)
                       (+ datoms-confirmed (or (:datom_count out) 0))))
              (do
                (when (seq pending)
                  (let [beat (inc (count txs))
                        e    (str "aburi-bridge-" beat)
                        ds   [(kt/add e ":aburi-bridge/pushed-to-tx"  (get (peek pending) ":tx/id"))
                              (kt/add e ":aburi-bridge/parent-commit" last-commit)
                              (kt/add e ":aburi-bridge/graph"         graph)
                              (kt/add e ":aburi-bridge/remote-tx-cids" remote-cids)
                              (kt/add e ":aburi-bridge/beat"          beat)]
                        ;; aburi kotoba.cljc: (make-tx datoms tx-id as-of prev-cid)
                        ck   (kt/make-tx ds (str "bridge-" beat)
                                         (+ as-of-base beat)
                                         (kt/head-cid log-path))]
                    ;; aburi kotoba.cljc: (append-tx tx log-path)
                    (kt/append-tx ck log-path)))
                {:mode             "live"
                 :pushed           (count pending)
                 :remote-tx-cids   remote-cids
                 :parent-commit    last-commit
                 :datoms-confirmed datoms-confirmed
                 :pushed-to        (if (seq pending) (get (peek pending) ":tx/id")
                                       (:pushed-to state))}))))))))

;; ─── CLI entry ────────────────────────────────────────────────────────────────

#?(:clj
   (defn -main [& args]
     (let [argv  (vec args)
           log   (if-let [_i (some #(when (= % "--log") %) argv)]
                   (java.io.File. (get argv (inc (.indexOf (vec argv) "--log"))))
                   ;; default: data/local/persisted/aburi-exposure.kotoba.edn (same as py)
                   (java.io.File. (str (System/getProperty "user.dir")
                                       "/data/local/persisted/aburi-exposure.kotoba.edn")))
           res   (push log)]
       (if (= (:mode res) "dry-run")
         (println (str "# aburi kotoba-bridge DRY-RUN — " (:pending res)
                       " tx pending past cursor " (:pushed-to res)
                       " (set " live-env "=1 + member-sig + Council to push)"))
         (println (str "# aburi kotoba-bridge LIVE — pushed " (:pushed res)
                       " tx, " (:datoms-confirmed res) " datoms, head commit "
                       (subs (str (:parent-commit res)) 0 (min 18 (count (str (:parent-commit res)))))
                       "…"))))))
