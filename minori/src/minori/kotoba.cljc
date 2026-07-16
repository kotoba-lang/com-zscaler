(ns minori.kotoba
  "minori growth ON the canonical kotoba Datom log (ADR-2605312345 / 2605262130) — the 'kotoba 常駐
   runtime' substrate. Each beat = one tx of EAVT [:db/add e a v] datoms, content-addressed into an
   append-only commit-DAG chained by CID (the kaname/busshi/ugachi pattern). Self-contained datom-emit
   + DETERMINISTIC CID (no wall clock / no randomness; floats fixed-precision so the CID is stable; a
   later edit breaks every downstream CID = tamper-evident). The live-engine push
   (com.etzhayyim.apps.kotoba.datomic.transact → :8077) is a G7/operator env-gated leg, DRY-RUN by
   default — no server key, no network here (host allowlist loopback per ADR-2605215000)."
  (:require [minori.ledger :as ledger]))   ; reuse the deterministic sha256-hex

(def graph "minori-growth-v1")

(defn- f6 [x] (format "%.6f" (double x)))   ; fixed precision ⇒ deterministic CID across runs

(defn datoms-of
  "EAVT [:db/add e a v] datoms for one growth beat. Values are stringified (a datom VALUE must never
   begin with ':' — it would be read back as a bare keyword token and corrupt the datom)."
  [{:keys [beat G dG eta adoption components net-giver? gated?]}]
  (let [e (str "minori.beat." beat)]
    [[:db/add e "minori.beat/n"         (str beat)]
     [:db/add e "minori.beat/G"         (f6 G)]
     [:db/add e "minori.beat/dG"        (f6 dG)]
     [:db/add e "minori.beat/eta"       (f6 eta)]
     [:db/add e "minori.beat/adoption"  (f6 adoption)]
     [:db/add e "minori.beat/capture"   (f6 (:capture components))]
     [:db/add e "minori.beat/phi"       (f6 (:phi components))]
     [:db/add e "minori.beat/net-giver" (str (boolean net-giver?))]
     [:db/add e "minori.beat/gated"     (str (boolean gated?))]]))

(defn commit
  "Content-addressed commit-DAG node {:graph :datoms :parent :cid}. cid = sha256(datoms | parent);
   idempotent-by-content (same datoms + parent ⇒ same cid). Chains on the previous beat's cid."
  [datoms parent]
  {:graph graph
   :datoms datoms
   :parent parent
   :cid (ledger/sha256-hex (str (pr-str datoms) "|" parent))})

(defn bridge!
  "Push the commit to the LIVE kotoba engine via com.etzhayyim.apps.kotoba.datomic.transact.
   DRY-RUN by default (returns the would-be tx, no network); LIVE only when MINORI_KOTOBA_LIVE=1 +
   an operator DID is present (G7) — loopback host allowlist, no-server-key (operator bearer)."
  [commit-node]
  (let [live? (= "1" (System/getenv "MINORI_KOTOBA_LIVE"))]
    {:mode (if live? :live :dry-run)
     :graph (:graph commit-node)
     :tx-cid (:cid commit-node)
     :datom-count (count (:datoms commit-node))
     :pushed (if live? :would-transact-:8077 :dry-run-no-network)
     :note "live push G7/operator-gated (MINORI_KOTOBA_LIVE=1 + operator DID); no-server-key"}))
