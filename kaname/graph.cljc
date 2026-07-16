(ns kaname.graph
  "kaname 要 — the actor as a langgraph-clj StateGraph (ADR-2606172100 R1).

  This is kaname's BASIC ELEMENT: a compiled langgraph-clj graph whose nodes are the actor's
  perceive→reason→route→propose→persist cycle. The entry node is 世界認識 (world-perception): it
  builds the actor's cross-domain WORLD MODEL by joining every committed mirror (chie/tsumugi/inochi/
  hokorobi/shiori/web), optionally refreshing the web mirror by FETCHING live in clj first (G7).
  The world model then flows through leverage (the 要), route (→ OPENING), おせっかい (→ ossekai),
  and persist (append to the kotoba commit-DAG).

      :perceive-world  (世界認識)  → join mirrors (+ optional live web-fetch) → cross-domain world model
      :leverage                   → sos/leverage + R1 centrality → the 要
      :route                      → route the 要 to OPENING (G2)
      :osekkai                    → ossekai handoff proposal (advisory/unsent, G1/G3)
      :persist                    → kotoba Datom-log commit-DAG append (idempotent-by-content)

  langgraph-clj loads under babashka (bb.edn :deps). State is a map; each node returns a map merged
  into it. Pure except :perceive-world (reads mirror files; live fetch when :live?) and :persist
  (appends the local log). Constitutional gates live in the underlying methods (route/osekkai/ingest)."
  (:require [langgraph.graph :as g]
            [kaname.methods.sos :as sos]
            [kaname.methods.centrality :as centrality]
            [kaname.methods.join :as join]
            [kaname.methods.route :as route]
            [kaname.methods.osekkai :as osekkai]
            [kaname.methods.kotoba :as kkot]
            #?(:clj [kaname.methods.ingest :as ingest])
            #?(:clj [clojure.java.io :as io])))

;; ── nodes ─────────────────────────────────────────────────────────────────────

(defn perceive-world
  "世界認識 — build the actor's cross-domain world model from committed mirrors. When :live? and a
  :sources-path are given, FIRST refresh the web mirror by fetching live in clj (G7)."
  [{:keys [base-dir live? sources-path web-out]}]
  #?(:clj
     (when (and live? sources-path)
       (ingest/ingest-live! sources-path (or web-out (str (io/file base-dir "kaname" "data" "ingested-web.kotoba.edn"))))))
  (let [{:keys [graph loaded]} (join/join-mirrors #?(:clj (io/file base-dir) :default base-dir))]
    {:world graph :loaded loaded}))

(defn leverage [{:keys [world]}]
  (let [{:keys [nodes edges]} world
        res (sos/leverage nodes edges)
        res1 (centrality/leverage-r1 nodes edges res)]
    {:res1 res1 :point (centrality/kaname-point-r1 res1 nodes)}))

(defn route-point [{:keys [world res1]}]
  {:route (route/route-point (:nodes world) res1)})

(defn osekkai-handoff [{:keys [world res1]}]
  {:proposal (osekkai/point-proposal (:nodes world) res1)})

(defn persist [{:keys [world res1 loaded tx-id as-of log-path]}]
  (let [ds (kkot/leverage->datoms (:nodes world) res1 loaded)
        r (kkot/persist! ds {:tx-id tx-id :as-of as-of :log-path log-path})]
    {:persist r :head (:head r)}))

;; ── graph ───────────────────────────────────────────────────────────────────

(defn build
  "Compile the kaname StateGraph (perceive-world → leverage → route → osekkai → persist)."
  []
  (-> (g/state-graph)
      (g/add-node :perceive-world perceive-world)
      (g/add-node :leverage leverage)
      (g/add-node :route route-point)
      (g/add-node :osekkai osekkai-handoff)
      (g/add-node :persist persist)
      (g/add-edge :perceive-world :leverage)
      (g/add-edge :leverage :route)
      (g/add-edge :route :osekkai)
      (g/add-edge :osekkai :persist)
      (g/set-entry-point :perceive-world)
      (g/set-finish-point :persist)
      (g/compile-graph)))

(defn run
  "Invoke the actor graph once. input: {:base-dir :tx-id :as-of :log-path :live? :sources-path}.
  Returns the final state (incl. :point :route :proposal :persist :head)."
  [input]
  (g/invoke (build) input))

#?(:clj
   (defn -main
     [& argv]
     (let [base (or (first argv) "20-actors")
           log  (or (second argv) (str (io/file base "kaname" kkot/default-log)))
           n    (count (kotoba.datom/read-log log))
           out  (run {:base-dir base :log-path log
                      :tx-id (str "kaname-" n) :as-of (str "as-of:" n) :live? false})]
       (println (str "kaname graph: 要=" (some-> (:point out) second)
                     " mirrors=" (pr-str (:loaded out))
                     " persisted-head=" (:head out)
                     " appended=" (get-in out [:persist :appended])))
       0)))
