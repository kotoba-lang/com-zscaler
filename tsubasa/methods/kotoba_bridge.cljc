#!/usr/bin/env bb
;; tsubasa 翼 — push the local commit-DAG into the LIVE kotoba engine (R3+). ADR-2606072802 §R3.
(ns tsubasa.methods.kotoba-bridge
  "kotoba_bridge.cljc — tsubasa 翼 bridge: each local fare-observation tx → one
  `com.etzhayyim.apps.kotoba.datomic.transact` against a running kotoba node (:8077), so
  the competition/fare readout lands on the REAL distributed Datom graph. Mirrors the
  kaname (ADR-2606172100) / ibuki (ADR-2606101200 §R3) bridges, over tsubasa's own
  busshi-family local ledger (`tsubasa.methods.kotoba`).

  Discipline (identical to kaname/ibuki):
    - host allowlist (loopback + EVO-X2 LAN, ADR-2605215000) — any other endpoint throws BEFORE I/O;
    - a durable `:bridge/*` cursor ON the local log → exactly-once per local tx, crash/re-run safe;
    - every pushed tx carries `:tsubasa.tx/*` provenance mapping back to the local commit-DAG;
    - the previous push's remote commit_cid is sent as `expected_parent` (optimistic concurrency);
    - AUTH PRINCIPAL = the leash: a usable member-issued CACAO `:delegation` → the push PRESENTS
      the member-signed `cacao_b64` and the actor writes AS ITS OWN did:key (identity.cljc) — no
      held key; absent/expired → FAIL-OPEN to the node's public-DID operator bearer (no secret);
    - DRY-RUN by default (returns exact request bodies, no I/O); live = TSUBASA_KOTOBA_LIVE=1 / :live true.
  HTTP is injectable (:http-post / :transport). Deterministic; no wall clock."
  (:require [tsubasa.methods.kotoba :as k]
            [clojure.string :as str]
            [multiformats.core :as mf]
            #?(:clj [babashka.http-client :as http])
            #?(:clj [cheshire.core :as json])))

(def allowed-kotoba-hosts #{"127.0.0.1:8077" "localhost:8077" "192.168.1.70:8077"})
(def default-endpoint "http://127.0.0.1:8077/xrpc/com.etzhayyim.apps.kotoba.datomic.transact")
(def default-graph "tsubasa")
(def live-env "TSUBASA_KOTOBA_LIVE")
(def operator-did-env "TSUBASA_KOTOBA_OPERATOR_DID")  ; PUBLIC did:key (1Password: etzhayyim-tsubasa-did)

(defn boundary-violation
  ([msg] (boundary-violation msg {}))
  ([msg data] (ex-info msg (assoc data :tsubasa/kotoba-boundary-violation true))))

(defn- host-of [endpoint]
  (some-> endpoint (str/replace #"^https?://" "") (str/split #"/") first))

(defn assert-kotoba
  "Refuse any endpoint whose host:port is not in the kotoba fleet allowlist. Throws before I/O."
  [endpoint]
  (when-not (contains? allowed-kotoba-hosts (host-of endpoint))
    (throw (boundary-violation (str "tsubasa kotoba bridge refused non-fleet endpoint: " endpoint)
                               {:endpoint endpoint :allowed allowed-kotoba-hosts})))
  nil)

;; ── graph-cid (CIDv1 dag-cbor sha2-256 over the graph NAME; kotoba-core cid.rs parity) ──
(defn graph-cid
  "CIDv1 + dag-cbor(0x71) + sha2-256 over the raw graph-NAME bytes, multibase base32lower ('b').
  Delegates to com-junkawasaki/multiformats-clj (byte-identical)."
  ^String [^String name]
  (mf/kotoba-cid name))

;; ── datom + tx rendering ──────────────────────────────────────────────────────
(defn- val->edn [v]
  (cond
    (boolean? v) (if v "true" "false")
    (integer? v) (str v)
    (float? v) (str (double v))
    (string? v) (if (str/starts-with? v ":") v (str \" v \"))   ; ":attr"/":db/add" → keyword text
    (sequential? v) (str "[" (str/join " " (map val->edn v)) "]")
    :else (str v)))

(defn- datom->edn [d] (str "[" (str/join " " (map val->edn d)) "]"))

(defn tx->edn-vec
  "Local tx → the `tx_edn` string: its [:db/add e a v] datom forms + :tsubasa.tx/* provenance."
  ^String [tx]
  (let [data (get tx ":tx/datoms")
        meta-e (str "tsubasa-tx:" (get tx ":tx/cid"))
        prov [(k/add meta-e ":tsubasa.tx/id" (get tx ":tx/id"))
              (k/add meta-e ":tsubasa.tx/local-cid" (get tx ":tx/cid"))
              (k/add meta-e ":tsubasa.tx/local-prev" (get tx ":tx/prev"))]]
    (str "[" (str/join " " (map datom->edn (concat data prov))) "]")))

;; ── cursor (exactly-once) ─────────────────────────────────────────────────────
(defn- bridge-tx? [tx]
  (some #(= ":bridge/pushed-cid" (nth % 2)) (get tx ":tx/datoms")))

(defn bridge-cursor
  "Replay the durable cursor from the log: {:pushed-cid <last pushed local CID> :parent-commit <remote>}."
  [txs]
  (let [last-bridge (last (filter bridge-tx? txs))]
    (if last-bridge
      (let [ds (get last-bridge ":tx/datoms")
            g (fn [a] (some (fn [d] (when (= a (nth d 2)) (nth d 3))) ds))]
        {:pushed-cid (g ":bridge/pushed-cid") :parent-commit (or (g ":bridge/parent-commit") "")})
      {:pushed-cid nil :parent-commit ""})))

(defn pending-txs
  "Data txs (non-:bridge) not yet pushed — those AFTER the cursor's pushed-cid, by position."
  [txs]
  (let [data (remove bridge-tx? txs)
        pushed (:pushed-cid (bridge-cursor txs))]
    (if (nil? pushed)
      (vec data)
      (->> data (drop-while #(not= pushed (get % ":tx/cid"))) (drop 1) vec))))

;; ── transport (operator bearer / member CACAO leash) ──────────────────────────
#?(:clj
   (defn- b64url-nopad ^String [^String s]
     (.encodeToString (.withoutPadding (java.util.Base64/getUrlEncoder)) (.getBytes s "UTF-8"))))

#?(:clj
   (defn- operator-bearer
     "Unsigned loopback bearer whose `sub` is the node's PUBLIC operator did:key (a public
     identifier, never a secret). The loopback trust boundary verifies the sig, not the bearer."
     [operator-did]
     (str (b64url-nopad (json/generate-string {"alg" "none"})) "."
          (b64url-nopad (json/generate-string {"sub" operator-did})) ".unsigned-loopback")))

#?(:clj
   (defn default-transport
     "POST one transact. Loopback needs no auth; attach an unsigned operator bearer only when an
     operator-did is supplied. Returns the parsed JSON map (or a status map)."
     ([url body] (default-transport url body {}))
     ([url body {:keys [timeout-s http-post operator-did] :or {timeout-s 60}}]
      (assert-kotoba url)
      (let [headers (cond-> {"Content-Type" "application/json"}
                      (and operator-did (not (str/blank? operator-did)))
                      (assoc "Authorization" (str "Bearer " (operator-bearer operator-did))))
            post (or http-post
                     (fn [u b h _t]
                       (let [r (http/post u {:headers h :body (json/generate-string b) :timeout (* 1000 timeout-s)})]
                         (json/parse-string (:body r) true))))]
        (post url body headers timeout-s)))))

(defn- env-live? [] #?(:clj (= "1" (System/getenv live-env)) :default false))

;; ── usable delegation (the leash) ─────────────────────────────────────────────
(defn- usable-delegation?
  "A member CACAO bundle usable now + scoped to this graph (present-only; we do NOT verify the
  member's signature here — kotoba does — we only check scope/expiry to decide the principal)."
  [delegation graph now-epoch]
  (boolean (and delegation
                (= (or (:graph delegation) (get delegation "graph")) graph)
                (let [exp (or (:exp delegation) (get delegation "exp"))]
                  (or (nil? exp) (nil? now-epoch) (>= exp now-epoch)))
                (or (:cacao-b64 delegation) (get delegation "cacao_b64")))))

(defn push
  "Push every not-yet-sent local data tx (oldest first), one transact per tx. Live requires
  TSUBASA_KOTOBA_LIVE=1 or :live true; otherwise DRY-RUN (returns exact bodies). After a live
  push, ONE :bridge/* checkpoint tx is appended (exactly-once cursor).
  Options: :graph :endpoint :transport :live :http-post :operator-did :delegation :now-epoch :as-of-base.
  Principal: a usable member :delegation → present cacao_b64 (write as the actor's own DID);
  else fail-open to the operator bearer."
  ([log-path] (push log-path {}))
  ([log-path {:keys [graph endpoint transport live http-post operator-did delegation now-epoch as-of-base]
              :or {graph default-graph endpoint default-endpoint as-of-base 2606210000}}]
   (assert-kotoba endpoint)
   (let [operator-did (or operator-did #?(:clj (System/getenv operator-did-env) :default nil))
         graph-id (if (and (str/starts-with? graph "b") (> (count graph) 40)) graph (graph-cid graph))
         txs (k/read-log log-path)
         state (bridge-cursor txs)
         pending (pending-txs txs)
         delegated (usable-delegation? delegation graph now-epoch)
         cacao (when delegated (or (:cacao-b64 delegation) (get delegation "cacao_b64")))
         bodies (mapv (fn [tx] (cond-> {:graph graph-id :tx_edn (tx->edn-vec tx)}
                                 delegated (assoc :cacao_b64 cacao)))
                      pending)
         is-live (if (some? live) (boolean live) (env-live?))
         principal (cond delegated "member-delegation"
                         (and delegation (not delegated)) "operator (delegation expired/mis-scoped)"
                         :else "operator")]
     (if-not is-live
       {:mode "dry-run" :pending (count bodies) :graph-cid graph-id :bodies bodies
        :delegated delegated :principal principal :pushed-cid (:pushed-cid state)}
       #?(:clj
          (loop [pairs (map vector pending bodies)
                 remote [] last-commit (:parent-commit state) confirmed 0]
            (if-let [[tx body] (first pairs)]
              (let [body (cond-> body (seq last-commit) (assoc :expected_parent last-commit))
                    out (if transport (transport endpoint body)
                            (default-transport endpoint body {:http-post http-post :operator-did (when-not delegated operator-did)}))]
                (when-not (contains? #{"ok" "committed" "success"} (str (:status out)))
                  (throw (ex-info (str "kotoba transact refused tx " (get tx ":tx/id") ": " (pr-str out))
                                  {:tsubasa/kotoba-transact-refused true :out out})))
                (recur (rest pairs) (conj remote (or (:tx_cid out) "")) (or (:commit_cid out) "")
                       (+ confirmed (or (:datom_count out) 0))))
              (do
                (when (seq pending)
                  (let [beat (inc (count txs))
                        e (str "bridge-" beat)
                        ds [(k/add e ":bridge/pushed-cid" (get (peek pending) ":tx/cid"))
                            (k/add e ":bridge/parent-commit" last-commit)
                            (k/add e ":bridge/graph" graph)
                            (k/add e ":bridge/beat" beat)
                            (k/add e ":bridge/principal" principal)]
                        ck (k/make-tx ds (str "bridge-" beat) (+ as-of-base beat) (k/head-cid log-path))]
                    (k/append-tx ck log-path)))
                {:mode "live" :pushed (count pending) :graph-cid graph-id :remote-tx-cids remote
                 :parent-commit last-commit :datoms-confirmed confirmed :delegated delegated
                 :principal principal
                 :pushed-cid (if (seq pending) (get (peek pending) ":tx/cid") (:pushed-cid state))})))
          :default {:mode "unsupported"})))))

#?(:clj
   (defn -main [& args]
     (let [log (or (first args)
                   "20-actors/tsubasa/data/persisted/tsubasa.observations.kotoba.edn")
           r (push log {})]   ; dry-run unless TSUBASA_KOTOBA_LIVE=1
       (println (str ";; tsubasa bridge — mode=" (:mode r) " pending/pushed="
                     (or (:pending r) (:pushed r)) " principal=" (:principal r)
                     " graph-cid=" (:graph-cid r))))))

#?(:clj
   (when (= *file* (System/getProperty "babashka.file"))
     (apply -main *command-line-args*)))
