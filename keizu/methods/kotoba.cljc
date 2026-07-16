(ns keizu.methods.kotoba
  "kotoba.cljc — keizu (系図) kotoba Datom-log writer (local, content-addressed). ADR-2606066000
  + ADR-2605262130 + ADR-2605312345. 1:1 Clojure port of `methods/kotoba.py`.

  The substrate boundary (root CLAUDE.md): canonical state is the kotoba Datom log —
  content-addressed EAVT assertions, append-only (非終末論). This module is the local,
  autonomous-loop write path: a self-driving heartbeat appends content-addressed transactions to a
  local append-only EDN log with NO external I/O.

  Constitutional posture is preserved by construction (keizu hard rules): an accountability MAP,
  NEVER a target-list; edge-primary — every derived signal is a concentration/co-occurrence computed
  on read from edges/flows, never a per-person score (G4); FACTUAL + non-adjudicating; no-doxxing —
  PII node attrs are unrepresentable (validated upstream by weave).

    - graph-datoms(g)        → EAVT assertions for every entity (node / committee / rel / money /
                              statement). E = the entity's id; lists fan out.
    - derived-datoms(c)      → EAVT assertions for the aggregate, edge-primary concentration metrics,
                              flagged :keizu.conc/derived. Never a per-person score.
    - make-tx / append-tx / read-log / head-cid / verify-chain — content-addressed commit-DAG.

  EAVT = [op entity attribute value]; op is :db/add only (append-only — no :db/retract). Stdlib only.
  Deterministic: the caller supplies tx-id + as-of (no wall clock) → resume-safe.

  House style: Python ':…' keyword strings stay strings; round HALF_EVEN via weave/pyround; canonical
  JSON (sha256 preimage) mirrors json.dumps(sort_keys=True, separators=(',', ':')); the shared
  keizu.methods.edn reader reads the log back; file I/O at the #?(:clj) edge. Omits the __main__ demo."
  (:require [clojure.string :as str]
            [keizu.methods.weave :as w]
            #?(:clj [keizu.methods.edn :as kedn])))

(def ID-KEYS [":node/id" ":committee/id" ":rel/id" ":money/id" ":statement/id"])

;; ── sha-256 (self-contained; no sibling provides one) ──────────────────────────
(defn- sha256-hex ^String [^String s]
  #?(:clj (let [d (.digest (java.security.MessageDigest/getInstance "SHA-256") (.getBytes s "UTF-8"))]
            (apply str (map #(format "%02x" (bit-and % 0xff)) d)))
     :default (throw (ex-info "bind a sha-256 impl on this host" {}))))

(defn- add
  "One append-only EAVT assertion: [:db/add <entity> <attr> <value>]."
  [entity attr value]
  [":db/add" entity attr value])

(defn- flatten-row [row out]
  (let [e (some (fn [k] (when (contains? row k) (get row k))) ID-KEYS)]
    (if (nil? e)
      out
      (reduce (fn [out [k v]]
                (if (contains? (set ID-KEYS) k)
                  out
                  (reduce (fn [out item] (conj out (add e k item)))
                          out
                          (if (sequential? v) v [v]))))
              out
              ;; preserve insertion order of the row map (string-keyed datom maps from weave)
              (seq row)))))

(defn graph-datoms
  "Flatten the woven relation graph into append-only EAVT assertions. Power-entity nodes only
  (PII node attrs are unrepresentable, validated upstream by weave)."
  [g]
  (let [omap-vals (fn [m] (map second (#'w/omap-items m)))]
    (-> []
        (as-> out (reduce #(flatten-row %2 %1) out (omap-vals (get g "nodes"))))
        (as-> out (reduce #(flatten-row %2 %1) out (omap-vals (get g "committees"))))
        (as-> out (reduce #(flatten-row %2 %1) out (get g "rels")))
        (as-> out (reduce #(flatten-row %2 %1) out (get g "money")))
        (as-> out (reduce #(flatten-row %2 %1) out (get g "statements"))))))

(defn derived-datoms
  "Flatten the aggregate, edge-primary concentration metrics into EAVT assertions, each flagged
  :keizu.conc/derived true (an accountability map recomputed on read, NEVER a per-person score or
  a target-list — G4). `c` is concentration()."
  ([c] (derived-datoms c "keizu.conc"))
  ([c prefix]
   (let [out (transient [])
         push! (fn [v] (conj! out v))]
     ;; headline counts
     (let [e (str prefix "-counts")]
       (push! (add e ":keizu.conc/node-count" (get c "node_count")))
       (push! (add e ":keizu.conc/committee-count" (get c "committee_count")))
       (push! (add e ":keizu.conc/rel-count" (get c "rel_count")))
       (push! (add e ":keizu.conc/money-count" (get c "money_count")))
       (push! (add e ":keizu.conc/statement-count" (get c "statement_count")))
       (push! (add e ":keizu.conc/derived" true)))
     ;; money concentration (by payee) + payer concentration — HHI + ranked shares
     (let [mc (get c "money_concentration")
           pc (get c "payer_concentration")
           em (str prefix "-money")]
       (push! (add em ":keizu.conc/money-hhi" (get mc "hhi")))
       (push! (add em ":keizu.conc/money-total" (get mc "total")))
       (push! (add em ":keizu.conc/payer-hhi" (get pc "hhi")))
       (push! (add em ":keizu.conc/derived" true))
       (doseq [[payee share] (get mc "shares")]
         (let [e (str prefix "-payee-" payee)]
           (push! (add e ":keizu.conc/payee" payee))
           (push! (add e ":keizu.conc/share" (w/pyround share 4)))
           (push! (add e ":keizu.conc/derived" true))))
       (doseq [[payer share] (get pc "shares")]
         (let [e (str prefix "-payer-" payer)]
           (push! (add e ":keizu.conc/payer" payer))
           (push! (add e ":keizu.conc/share" (w/pyround share 4)))
           (push! (add e ":keizu.conc/derived" true)))))
     ;; committee cross-organ concentration
     (doseq [r (get c "committee_cross_organ")]
       (let [e (str prefix "-xorgan-" (get r "committee"))]
         (push! (add e ":keizu.conc/committee" (get r "committee")))
         (push! (add e ":keizu.conc/member-count" (get r "member_count")))
         (push! (add e ":keizu.conc/distinct-organs" (get r "distinct_organs")))
         (push! (add e ":keizu.conc/derived" true))))
     ;; cross-committee seats (co-membership) + cross-organ connector seats
     (doseq [r (get c "cross_committee_seats")]
       (let [e (str prefix "-xseat-" (get r "seat"))]
         (push! (add e ":keizu.conc/seat" (get r "seat")))
         (push! (add e ":keizu.conc/committee-count" (get r "committee_count")))
         (push! (add e ":keizu.conc/derived" true))))
     (doseq [r (get c "connector_seats")]
       (let [e (str prefix "-connector-" (get r "seat"))]
         (push! (add e ":keizu.conc/connector-seat" (get r "seat")))
         (push! (add e ":keizu.conc/organs-bridged" (get r "organs_bridged")))
         (push! (add e ":keizu.conc/derived" true))))
     ;; revolving-door chains (as-of) + award-and-fund co-occurrence (FACTUAL, non-adjudicating)
     (doseq [[i r] (map-indexed vector (get c "revolving_door"))]
       (let [e (str prefix "-revolving-" i)]
         (push! (add e ":keizu.conc/revolving-from" (get r "from_label")))
         (push! (add e ":keizu.conc/revolving-to" (get r "to_label")))
         (push! (add e ":keizu.conc/as-of" (get r "as_of")))
         (push! (add e ":keizu.conc/non-adjudicating" true))
         (push! (add e ":keizu.conc/derived" true))))
     (doseq [r (get c "award_and_fund")]
       (let [e (str prefix "-awardfund-" (get r "node"))]
         (push! (add e ":keizu.conc/award-and-fund-node" (get r "node")))
         (push! (add e ":keizu.conc/received-total" (get r "received_total")))
         (push! (add e ":keizu.conc/donated-total" (get r "donated_total")))
         (push! (add e ":keizu.conc/non-adjudicating" true))  ;; co-occurrence, NOT an allegation
         (push! (add e ":keizu.conc/derived" true))))
     ;; by-jurisdiction
     (doseq [j (get c "by_jurisdiction")]
       (let [e (str prefix "-juris-" (get j "jurisdiction"))]
         (push! (add e ":keizu.conc/jurisdiction" (get j "jurisdiction")))
         (push! (add e ":keizu.conc/nodes" (get j "nodes")))
         (push! (add e ":keizu.conc/committees" (get j "committees")))
         (push! (add e ":keizu.conc/money-total" (get j "money_total")))
         (push! (add e ":keizu.conc/derived" true))))
     (persistent! out))))

;; ── canonical JSON for the CID preimage (json.dumps sort_keys=True separators=(',',':')) ──
(defn- json-escape ^String [^String s]
  (-> (str s)
      (str/replace "\\" "\\\\")
      (str/replace "\"" "\\\"")
      (str/replace "\n" "\\n")
      (str/replace "\t" "\\t")
      (str/replace "\r" "\\r")))

(defn- canon
  "Compact canonical JSON value: sort_keys=True, separators (',', ':'). Doubles use Python repr,
  integers print without a decimal point (matches json.dumps over the datom lists)."
  [v]
  (cond
    (nil? v) "null"
    (true? v) "true"
    (false? v) "false"
    (string? v) (str \" (json-escape v) \")
    #?(:clj (integer? v) :cljs (and (number? v) (== v (Math/floor v)))) (str v)
    (number? v) (#'w/py-float-repr (double v))
    (map? v) (str "{" (str/join "," (map (fn [k] (str (canon (str k)) ":" (canon (get v k))))
                                         (sort (keys v)))) "}")
    (sequential? v) (str "[" (str/join "," (map canon v)) "]")
    :else (str \" (json-escape (str v)) \")))

(defn- canonical [datoms prev-cid]
  (canon {"prev" prev-cid "datoms" datoms}))

(defn tx-cid
  "Content address = sha256 over (prev-cid, datoms) → a commit-DAG."
  ([datoms] (tx-cid datoms ""))
  ([datoms prev-cid] (str "b" (sha256-hex (canonical datoms prev-cid)))))

(defn make-tx
  [datoms & {:keys [tx-id as-of prev-cid] :or {prev-cid ""}}]
  {":tx/id" tx-id
   ":tx/as-of" as-of
   ":tx/prev" prev-cid
   ":tx/cid" (tx-cid datoms prev-cid)
   ":tx/count" (count datoms)
   ":tx/datoms" datoms})

;; ── EDN serialization (mirror of _edn_val / _tx_to_edn) ────────────────────────
(defn- edn-val [v]
  (cond
    (boolean? v) (if v "true" "false")
    #?(:clj (integer? v) :cljs (and (number? v) (== v (Math/floor v)))) (str v)
    (number? v) (#'w/py-float-repr (double v))
    (string? v) (if (str/starts-with? v ":") v (str \" (json-escape v) \"))
    (sequential? v) (str "[" (str/join " " (map edn-val v)) "]")
    :else (str \" (json-escape (str v)) \")))

(defn- tx-to-edn [tx]
  (let [datoms (str/join " " (map (fn [d] (str "[" (str/join " " (map edn-val d)) "]"))
                                  (get tx ":tx/datoms")))]
    (str "{:tx/id " (get tx ":tx/id") " :tx/as-of " (get tx ":tx/as-of") " "
         ":tx/prev " (str \" (json-escape (get tx ":tx/prev")) \") " "
         ":tx/cid " (str \" (json-escape (get tx ":tx/cid")) \") " "
         ":tx/count " (get tx ":tx/count") " :tx/datoms [" datoms "]}")))

(def ^:private LOG-HEADER
  (str ";; keizu kotoba Datom log — append-only EAVT transactions "
       "(content-addressed DAG). Accountability map, never a target-list; "
       "edge-primary, non-adjudicating, no-doxxing. DO NOT hand-edit. ADR-2606066000.\n"))

#?(:clj
   (defn append-tx
     "Append ONE transaction to the append-only log (never rewrites). Returns the tx CID."
     [tx log-path]
     (let [f (java.io.File. (str log-path))]
       (when-let [parent (.getParentFile f)] (.mkdirs parent))
       (when-not (.exists f)
         (spit f LOG-HEADER))
       (spit f (str (tx-to-edn tx) "\n") :append true)
       (get tx ":tx/cid"))))

#?(:clj
   (defn read-log
     "Read the log back as a list of transaction maps (uses the shared keizu.methods.edn reader)."
     [log-path]
     (let [f (java.io.File. (str log-path))]
       (if-not (.exists f)
         []
         (->> (str/split-lines (slurp f))
              (map str/trim)
              (remove (fn [line] (or (empty? line) (str/starts-with? line ";"))))
              (mapv kedn/parse-edn))))))

#?(:clj
   (defn head-cid [log-path]
     (let [txs (read-log log-path)]
       (if (seq txs) (get (last txs) ":tx/cid") ""))))

#?(:clj
   (defn verify-chain
     "Recompute every CID from its datoms + prev; verify the DAG is intact. {ok length broken_at}."
     [log-path]
     (let [txs (read-log log-path)]
       (loop [i 0, prev "", remaining txs]
         (if (empty? remaining)
           {"ok" true "length" (count txs) "broken_at" -1}
           (let [tx (first remaining)
                 expect (tx-cid (get tx ":tx/datoms" []) prev)]
             (if (or (not= (get tx ":tx/cid") expect) (not= (get tx ":tx/prev") prev))
               {"ok" false "length" (count txs) "broken_at" i}
               (recur (inc i) (get tx ":tx/cid") (rest remaining)))))))))
