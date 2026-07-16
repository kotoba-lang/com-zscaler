(ns kaname.methods.kotoba
  "kaname 要 — kotoba Datom-log persistence (ADR-2606172100 R1 / ADR-2605262130 / ADR-2605312345).

  Persists the cross-domain leverage readout to the canonical kotoba Datom log as a content-addressed,
  append-only commit-DAG (the busshi/ugachi/mimamori pattern, via the shared kotoba.datom binding in
  20-actors/kotodama/src). Each beat is one tx of EAVT `[:db/add e a v]` datoms chained by CID; a
  later edit breaks every downstream CID (tamper-evident). Idempotent-by-content: if the leverage
  readout is unchanged from the last beat, NO new tx is appended (no-op).

  Constitutional: the leverage integrals are DERIVED (computed on read) — but a SNAPSHOT of the SoS
  readout (the 要, the cross-domain entities, the route) is a legitimate observation to record on the
  log, exactly as busshi records its observation ledger. Values are strings / longs (milli-scaled
  for floats) so the CID is deterministic. No wall clock, no randomness (caller supplies tx-id/as-of).
  no-server-key (local file append only). Portable .cljc (bb)."
  (:require [clojure.string :as str]
            [kaname.methods.sos :as sos]
            [kotoba.datom :as kd]
            #?(:clj [clojure.java.io :as io])))

(def default-log "data/persisted/kaname.leverage.kotoba.edn")

(defn- milli [x] (long (Math/round (* 1000.0 (double x)))))
(defn- nm
  "Name of a keyword OR a ':'-prefixed string, WITHOUT the leading colon. Datom VALUES must never
  start with ':' — kotoba.datom emits a ':'-prefixed string as a bare keyword token, and commas in
  it would split the value on read (EDN treats ',' as whitespace), corrupting the datom."
  [x]
  (let [s (if (keyword? x) (name x) (str x))]
    (if (str/starts-with? s ":") (subs s 1) s)))

(defn leverage->datoms
  "Project the SoS leverage readout (res1 from centrality/leverage-r1) into deterministic EAVT
  datoms: a summary entity `kaname:sos`, plus one record per cross-domain entity (V≥2) carrying its
  label / versatility / domains / leverage / betweenness / source-mirrors. Sorted → stable CID."
  [nodes res1 loaded]
  (let [pt (first (sos/rank (:leverage-r1 res1) nodes 1))
        cross (->> (:V res1)
                   (filter (fn [[_ v]] (>= (long v) 2)))
                   (sort-by (fn [[nid _]] [(- (double (get-in res1 [:leverage-r1 nid] 0.0))) nid])))
        summary [(kd/add "kaname:sos" ":kaname/mirrors-joined" (str/join "," (map nm loaded)))
                 (kd/add "kaname:sos" ":kaname/node-count" (long (count nodes)))
                 (kd/add "kaname:sos" ":kaname/cross-domain-count" (long (count cross)))
                 (kd/add "kaname:sos" ":kaname/point" (if pt (nth pt 1) ""))
                 (kd/add "kaname:sos" ":kaname/point-leverage-milli" (if pt (milli (nth pt 2)) 0))]
        ents (mapcat (fn [[nid v]]
                       [(kd/add nid ":kaname.entity/label" (get-in nodes [nid ":organism/label"] nid))
                        (kd/add nid ":kaname.entity/versatility" (long v))
                        (kd/add nid ":kaname.entity/domains" (str/join "·" (map nm (sort (get-in res1 [:domains nid])))))
                        (kd/add nid ":kaname.entity/leverage-milli" (milli (get-in res1 [:leverage-r1 nid] 0.0)))
                        (kd/add nid ":kaname.entity/betweenness-milli" (milli (get-in res1 [:betweenness nid] 0.0)))
                        (kd/add nid ":kaname.entity/source-mirrors" (str/join "," (map nm (get-in nodes [nid ":sos/source-actors"]))))])
                     cross)]
    (vec (concat summary ents))))

(defn- last-datoms
  "Datoms of the last LEVERAGE-readout tx — skipping bridge-cursor txs (`:bridge/*`), which the
  live-engine bridge interleaves into the same log. Without this skip, a bridge checkpoint between
  two beats would defeat idempotency-by-content (the compare would hit the checkpoint, not the
  prior readout)."
  [log-path]
  (let [txs (kd/read-log log-path)
        leverage (remove (fn [tx]
                           (some (fn [[_ _ a _]] (str/starts-with? (str a) ":bridge/"))
                                 (:tx/datoms tx)))
                         txs)]
    (when (seq leverage) (get (last leverage) :tx/datoms))))

(defn persist!
  "Append a leverage-readout tx to the commit-DAG, idempotent-by-content. opts: {:tx-id :as-of
  :log-path}. Returns {:head cid :appended bool :reason (:no-change|nil) :count n}."
  [datoms {:keys [tx-id as-of log-path]}]
  (let [log-path (or log-path default-log)
        prev (kd/head-cid log-path)
        last-ds (last-datoms log-path)
        base {:count (count datoms) :head prev}]
    (if (= datoms last-ds)
      (assoc base :appended false :reason :no-change)
      (let [tx (kd/make-tx datoms {:tx-id tx-id :as-of as-of :prev-cid prev})]
        #?(:clj (io/make-parents log-path))
        (let [head (kd/append-tx! tx log-path)]
          (assoc base :appended true :reason nil :head head))))))

(defn head [log-path] (kd/head-cid (or log-path default-log)))
(defn verify [log-path] (kd/verify-chain (or log-path default-log)))
