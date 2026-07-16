(ns shirabe.methods.kotoba
  "shirabe 調べ — kotoba Datomic write path (canonical EAVT state, ADR-2605312345 + 2605262130).

  Projects a research SESSION into append-only kotoba-Datomic tx-data — vectors of
  [:db/add e a v] — and a content-addressed commit-DAG, the same local write path
  danjo / ibuki / meisai use (`methods/kotoba.*`). A session is transparent by construction
  (G5 相互監視 / 神の監視, reciprocal not asymmetric): the question, the sources it cited,
  the model used, and the answer are all on the append-only log, so anyone can audit what
  the research membrane was asked and how it answered.

  - session-datoms : [:db/add e a v] assertions (op :db/add only — append-only, 非終末論)
  - tx-cid/make-tx/tx->edn/append-tx/read-log/head-cid/verify-chain : commit-DAG
  The LIVE leg — POSTing a tx to the kotoba node's `com.etzhayyim.apps.kotoba.datomic.transact`
  — lives in live.clj (it needs HTTP; this ns stays pure so it loads under any Clojure).

  CONSTITUTIONAL:
    G2 — a persisted model id carrying an @host MUST be Murakumo-fleet (structural check).
    G6 — privacy. `:shirabe.session/member` is emitted ONLY when a member SIGNED the session;
      an unsigned/anonymous research session binds NO identity."
  (:require [clojure.string :as str]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [shirabe.methods.synthesize :as synthesize]))

(defn- add [e a v] [:db/add e a v])

(defn- sha256-hex [^String s]
  (let [md (java.security.MessageDigest/getInstance "SHA-256")]
    (apply str (map #(format "%02x" (bit-and % 0xff)) (.digest md (.getBytes s "UTF-8"))))))

(defn session-datoms
  "Flatten a research session into append-only [:db/add e a v] tx-data. Member id is bound
  only when signed (G6). Raises if a persisted model host is not Murakumo-fleet (G2)."
  ([session] (session-datoms session ""))
  ([session member]
   (let [id (:id session)
         sid (str "shirabe-session:" id)
         p (:plan session)
         r (:result session)
         model (str (:model r))
         base (cond-> [(add sid :shirabe.session/question (:question session))
                       (add sid :shirabe.session/qtype (:qtype p))
                       (add sid :shirabe.session/lang (:lang p))
                       (add sid :shirabe.session/freshness (:freshness p))
                       (add sid :shirabe.session/model model)
                       (add sid :shirabe.session/answer (:answer r))
                       (add sid :shirabe.session/insufficient (:insufficient r))
                       (add sid :shirabe.session/rounds (:rounds session))
                       (add sid :shirabe.session/source-count (count (:evidence session)))
                       (add sid :shirabe.session/started-at (:started-at session))
                       (add sid :shirabe.session/cited-sources (vec (:citations r)))]
                (seq member) (conj (add sid :shirabe.session/member member)))
         src (mapcat (fn [e]
                       (let [eid (str "shirabe-source:" id "." (:rank e))]
                         [(add eid :shirabe.src/session sid)
                          (add eid :shirabe.src/rank (:rank e))
                          (add eid :shirabe.src/url (:url e))
                          (add eid :shirabe.src/title (:title e))
                          (add eid :shirabe.src/snippet-cid (:snippet-cid e))
                          (add eid :shirabe.src/retrieved-at (:retrieved-at e))
                          (add eid :shirabe.src/query (:query e))]))
                     (:evidence session))]
     ;; G2 structural: a persisted model id of shape "<model>@host:port" must be Murakumo-fleet.
     (when (str/includes? model "@")
       (let [hp (subs model (inc (str/last-index-of model "@")))]
         (when-not (contains? synthesize/allowed-infer-hosts hp)
           (throw (ex-info (str "G2: persisted model host not Murakumo-fleet: " hp)
                           {:gate :G2 :host hp})))))
     (vec (concat base src)))))

;; ── content-addressed commit-DAG ──────────────────────────────────────────────
(defn- canonical [datoms prev-cid]
  (pr-str (array-map :prev prev-cid :datoms datoms)))

(defn tx-cid
  "Content address = sha256 over (prev-cid, datoms) → a commit-DAG."
  [datoms prev-cid]
  (str "b" (sha256-hex (canonical datoms (or prev-cid "")))))

(defn make-tx [datoms {:keys [tx-id as-of prev-cid] :or {prev-cid ""}}]
  (array-map :tx/id tx-id :tx/as-of as-of :tx/prev prev-cid
             :tx/cid (tx-cid datoms prev-cid) :tx/count (count datoms) :tx/datoms datoms))

(defn tx->edn [tx] (pr-str tx))

(defn append-tx
  "Append ONE transaction to the append-only log (never rewrites). Returns the tx CID."
  [tx log-path]
  (let [f (io/file log-path)]
    (io/make-parents f)
    (when-not (.exists f)
      (spit f (str ";; shirabe 調べ — append-only kotoba Datom log (content-addressed DAG). "
                   "Transparent by construction (G5 相互監視). DO NOT hand-edit. ADR-2606131600.\n")))
    (spit f (str (tx->edn tx) "\n") :append true)
    (:tx/cid tx)))

(defn read-log [log-path]
  (let [f (io/file log-path)]
    (if-not (.exists f) []
            (->> (str/split-lines (slurp f))
                 (map str/trim)
                 (remove #(or (str/blank? %) (str/starts-with? % ";")))
                 (mapv edn/read-string)))))

(defn head-cid [log-path] (:tx/cid (last (read-log log-path))))

(defn verify-chain
  "True iff every tx's :tx/prev chains and its :tx/cid re-derives (tamper-evident)."
  [log-path]
  (loop [txs (read-log log-path), prev ""]
    (if (empty? txs)
      true
      (let [tx (first txs)]
        (if (and (= (:tx/prev tx) prev)
                 (= (:tx/cid tx) (tx-cid (:tx/datoms tx) prev)))
          (recur (rest txs) (:tx/cid tx))
          false)))))

(defn persist!
  "Append a session to the local kotoba Datom log as one content-addressed tx. Deterministic:
  caller supplies tx-id + as-of (no wall clock) → resume-safe. Returns the tx CID."
  [session log-path {:keys [tx-id as-of member] :or {member ""}}]
  (let [datoms (session-datoms session member)
        prev (or (head-cid log-path) "")
        tx (make-tx datoms {:tx-id tx-id :as-of as-of :prev-cid prev})]
    (append-tx tx log-path)))
