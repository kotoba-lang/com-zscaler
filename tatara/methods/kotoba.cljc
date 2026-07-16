(ns tatara.methods.kotoba
  "tatara 鑪 — kotoba Datom-log writer (local, content-addressed). ADR-2606171800
  + ADR-2605262130 + ADR-2605312345.

  Appends content-addressed transactions to a local append-only EDN log with NO external
  I/O. Canonical state = the kotoba Datom log (content-addressed EAVT assertions,
  append-only — 非終末論). Each tx links the previous tx's CID → a verifiable commit-DAG.

    - graph-datoms(rows)     → EAVT assertions for every entity (plant / hub / flow).
                               E = the entity's id; cardinality-many list values fan out.
                               Plants carry operator-as-COMPANY only, never a person (G4).
    - derived-datoms(a)      → EAVT assertions for the analyzer's derived :concentration/*
                               signals, flagged :concentration/derived (never re-ingested).
    - make-tx / append-tx / read-log / head-cid / verify-chain — the commit-DAG.

  EAVT = [op entity attribute value]; op = :db/add only (append-only — no :db/retract).
  Deterministic: the caller supplies tx-id + as-of (no wall clock) → resume-safe.

  cljc-native (real keywords). Pure fns; file I/O only behind #?(:clj …)."
  (:require [clojure.string :as str]
            #?(:clj [clojure.edn :as edn])))

;; ── sha-256 host seam ─────────────────────────────────────────────────────────
(def ^:dynamic *sha256-hex*
  #?(:clj (fn [^String s]
            (let [d (.digest (java.security.MessageDigest/getInstance "SHA-256")
                             (.getBytes s "UTF-8"))]
              (str/join (map #(let [h (Integer/toHexString (bit-and % 0xff))]
                                (if (= 1 (count h)) (str "0" h) h))
                             d))))
     :default (fn [_] (throw (ex-info "bind tatara.methods.kotoba/*sha256-hex* on this host" {})))))

;; ── EAVT assertion ────────────────────────────────────────────────────────────
(def id-keys [:plant/id :hub/id :flow/id :chokepoint/id])

(def ^:private non-domain-keys
  "Keys that are NOT domain facts and must never be flattened into a graph datom, even though
  they are not entity-id candidates. `:db/id` is the Datomic/Datascript tx-data framing key the
  repo-wide edn-datomize pass adds to each seed record so the file is directly usable as
  (d/transact conn …) tx-data; it is scaffolding around the record, not a fact ABOUT the
  plant/hub/flow/chokepoint, so it must not appear in the persisted EAVT log or feed the
  content-addressed CID (mirrors the same exclusion added to watari/methods/kotoba.cljc in this
  same Phase 4 fan-out)."
  #{:db/id})

(defn add [entity attr value] [:db/add entity attr value])

(defn graph-datoms
  "Flatten the plant graph into append-only EAVT assertions. E = the entity's id (first
  present id key); cardinality-many list values fan out. Company-as-operator only (G4)."
  [rows]
  (reduce
   (fn [out r]
     (if-not (map? r)
       out
       (let [e (some (fn [k] (when (contains? r k) (get r k))) id-keys)]
         (if (nil? e)
           out
           (reduce (fn [out k]
                     (if (or (some #{k} id-keys) (contains? non-domain-keys k))
                       out
                       (let [v (get r k)
                             items (if (sequential? v) v [v])]
                         (reduce (fn [out item] (conj out (add e k item))) out items))))
                   out
                   (keys r))))))
   []
   rows))

(defn derived-datoms
  "Flatten the analyzer's derived :concentration/* signals into EAVT assertions, each flagged
  :concentration/derived true (aggregate, recomputed on read, never re-ingested as fact)."
  [a]
  (let [sectors (sort-by (fn [s] [(- (get-in a [:sector-stats s :plants])) (name s)])
                         (keys (:sector-stats a)))
        chokes (sort-by (fn [cp] [(- (count (get-in a [:choke-plants cp]))) (name cp)])
                        (keys (:choke-plants a)))]
    (-> []
        (into (mapcat (fn [s]
                        (let [st (get-in a [:sector-stats s])
                              e (str "concentration-sector-" (name s))]
                          [(add e :concentration/sector s)
                           (add e :concentration/hhi (:hhi st))
                           (add e :concentration/top-country (:top-country st))
                           (add e :concentration/top-share (:top-share st))
                           (add e :concentration/single-source (:single-source st))
                           (add e :concentration/derived true)]))
                      sectors))
        (into (mapcat (fn [cp]
                        (let [e (str "concentration-choke-" (name cp))]
                          [(add e :concentration/chokepoint cp)
                           (add e :concentration/chokepoint-plants (count (get-in a [:choke-plants cp])))
                           (add e :concentration/derived true)]))
                      chokes))
        ;; logistics modal + commodity split (computed by analyze; now persisted too)
        (into (mapcat (fn [m]
                        (let [e (str "split-mode-" (name m))]
                          [(add e :split/mode m)
                           (add e :split/flow-count (get-in a [:flow-modes m]))
                           (add e :split/derived true)]))
                      (sort-by (fn [m] [(- (get-in a [:flow-modes m])) (name m)]) (keys (:flow-modes a)))))
        (into (mapcat (fn [c]
                        (let [e (str "split-commodity-" (name c))]
                          [(add e :split/commodity c)
                           (add e :split/flow-count (get-in a [:flow-commodities c]))
                           (add e :split/derived true)]))
                      (sort-by (fn [c] [(- (get-in a [:flow-commodities c])) (name c)]) (keys (:flow-commodities a))))))))

;; ── canonical string for the CID preimage (deterministic) ────────────────────
(defn- canonical [datoms prev-cid]
  (str "{:prev " (pr-str prev-cid) " :datoms " (pr-str datoms) "}"))

(defn tx-cid
  ([datoms] (tx-cid datoms ""))
  ([datoms prev-cid] (str "b" (*sha256-hex* (canonical datoms prev-cid)))))

(defn make-tx [datoms & {:keys [tx-id as-of prev-cid] :or {prev-cid ""}}]
  {:tx/id tx-id :tx/as-of as-of :tx/prev prev-cid
   :tx/cid (tx-cid datoms prev-cid) :tx/count (count datoms) :tx/datoms datoms})

(def ^:private log-header
  (str ";; tatara kotoba Datom log — append-only EAVT transactions (content-addressed DAG).\n"
       ";; Aggregate concentration / resilience map, never a target-list (G2). Disclosed\n"
       ";; aggregate facility figures only, no per-worker data (G4). DO NOT hand-edit. ADR-2606171800.\n"))

#?(:clj
   (defn append-tx [tx log-path]
     (let [f (clojure.java.io/file (str log-path))]
       (when-let [parent (.getParentFile f)] (.mkdirs parent))
       (when-not (.exists f) (spit f log-header))
       (spit f (str (pr-str tx) "\n") :append true)
       (:tx/cid tx))))

#?(:clj
   (defn read-log [log-path]
     (let [f (clojure.java.io/file (str log-path))]
       (if-not (.exists f)
         []
         (->> (str/split-lines (slurp f))
              (map str/trim)
              (remove (fn [l] (or (str/blank? l) (str/starts-with? l ";"))))
              (mapv edn/read-string))))))

#?(:clj
   (defn head-cid [log-path]
     (let [txs (read-log log-path)]
       (if (seq txs) (:tx/cid (last txs)) ""))))

#?(:clj
   (defn verify-chain
     "Recompute every CID from its datoms + prev; verify the DAG is intact. {:ok :length :broken-at}."
     [log-path]
     (let [txs (read-log log-path)]
       (loop [i 0, prev "", ts txs]
         (if (empty? ts)
           {:ok true :length (count txs) :broken-at -1}
           (let [tx (first ts)
                 expect (tx-cid (:tx/datoms tx []) prev)]
             (if (or (not= (:tx/cid tx) expect) (not= (:tx/prev tx) prev))
               {:ok false :length (count txs) :broken-at i}
               (recur (inc i) (:tx/cid tx) (rest ts)))))))))
