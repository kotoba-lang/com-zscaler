(ns mimamori.methods.kotoba
  "mimamori 見守り kotoba Datom-log writer. ADR-2606112300 + ADR-2605312345.
  Clojure port of `methods/kotoba.py`.

  Canonical state is the kotoba Datom log (root CLAUDE.md substrate boundary):
  content-addressed EAVT assertions, append-only. This module is mimamori's write
  path onto that log, mirroring the shionome pattern (ADR-2606091000): each
  transaction is content-addressed (sha256 over its canonical datoms + the
  previous tx's CID -> a commit-DAG); a tamper of any earlier tx breaks every
  later CID.

    - bond-datoms     -> EAVT assertions from a replayed Mishmeret engine
    - coverage-datoms -> AGGREGATE-ONLY coverage assertions (G5: counts, no DID)
    - make-tx / tx-cid -> a content-addressed transaction (links prev CID)
    - append-tx        -> append ONE transaction line (never rewrites)
    - read-log / head-cid / verify-chain -- read back + verify the DAG

  The content-addressed commit-DAG primitives (tx-cid / make-tx / append / read /
  verify, byte-compatible with Python) are the actor's shared `kotoba.datom`
  sibling, reused verbatim (NOT re-inlined) and re-exposed here under the same
  names `methods/kotoba.py` defines, so callers depending on either surface work.

  EAVT = [:db/add entity attribute value] -- :db/add only (no retract; exit is
  itself an appended state datom). Deterministic (caller supplies tx-id + as-of)."
  (:require [kotoba.datom :as kd]
            #?(:clj [clojure.java.io :as io])))

#?(:clj
   (def log-default
     "20-actors/mimamori/data/mimamori.datoms.kotoba.edn (resolved at the host edge)."
     (-> *file* io/file .getParentFile .getParentFile
         (io/file "data" "mimamori.datoms.kotoba.edn"))))

(defn- add* [entity attr value]
  (kd/add entity attr value))

(defn bond-datoms
  "Flatten a replayed Mishmeret engine's append-only (e a v tx op) datoms into
  kotoba EAVT assertions. The engine's validator already enforced G1/G2 -- every
  attr here is whitelist-clean by construction."
  [engine]
  (mapv (fn [[e a v _tx _op]] (add* e a v)) (:datoms engine)))

(def ^:private coverage-keys
  ;; Python iterates members_total, with_keeper, offers_pending, unkept_count,
  ;; active_bonds, relays -> attrs with hyphenated tails. The coverage map from
  ;; coverage-report/coverage is keyword-keyed (:members-total etc.).
  [:members-total :with-keeper :offers-pending :unkept-count :active-bonds :relays])

(defn coverage-datoms
  "AGGREGATE-ONLY coverage assertions (G5): counts only, no DID ever enters these."
  [c cycle]
  (let [eid (str "coverage." cycle)]
    (into [(add* eid ":mimamori.coverage/cycle" cycle)]
          (map (fn [k] (add* eid (str ":mimamori.coverage/" (name k)) (get c k))))
          coverage-keys)))

;; ── content-addressed commit-DAG (kotoba.datom verbatim, re-exposed) ──────────

(defn tx-cid
  ([datoms] (kd/tx-cid datoms ""))
  ([datoms prev-cid] (kd/tx-cid datoms prev-cid)))

(defn make-tx
  "Mirror of Python make_tx(datoms, *, tx_id, as_of, prev_cid)."
  [datoms {:keys [tx-id as-of prev-cid] :or {prev-cid ""}}]
  (kd/make-tx datoms {:tx-id tx-id :as-of as-of :prev-cid prev-cid}))

(defn tx->edn
  "EDN log-line for a transaction (Python _tx_to_edn shape)."
  [tx]
  (kd/tx->edn-line tx))

#?(:clj
   (defn append-tx
     "Append ONE transaction (the log only ever grows). Returns the CID."
     ([tx] (append-tx tx log-default))
     ([tx log-path] (kd/append-tx! tx log-path))))

#?(:clj
   (defn read-log
     "Parsed transactions, oldest first."
     ([] (read-log log-default))
     ([log-path] (kd/read-log log-path))))

#?(:clj
   (defn head-cid
     ([] (head-cid log-default))
     ([log-path] (kd/head-cid log-path))))

#?(:clj
   (defn verify-chain
     "Recompute every CID from (datoms, prev) -> {:ok :length :broken-at}."
     ([] (verify-chain log-default))
     ([log-path] (kd/verify-chain log-path))))
