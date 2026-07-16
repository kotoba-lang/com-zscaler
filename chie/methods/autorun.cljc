(ns chie.methods.autorun
  "chie 智慧 deterministic heartbeat — 常駐化 (ADR-2606171200; pattern: ADR-2606091000 /
  mimamori autorun). One cycle = load seed → analyze (edge-primary 取-concentration) →
  aggregate coverage + opening headline → persist ONE content-addressed transaction to the
  local append-only kotoba commit-DAG.

    - NO external I/O: offline seed in, LOCAL log out. Live ingest (regulator texts /
      disclosed rounds / Wikidata) stays G7/Council-gated.
    - Deterministic + resume-safe: cycle number derives from the log length (no wall clock,
      no randomness); same seed + same cycle → byte-identical CID (kotoba.datom parity, so a
      log written here verifies under the Python impl and vice-versa).
    - N1/G2: GROUND = node + edge datoms (the graph). The per-node opening/reach/fragility
      INTEGRALS are computed on read and are NOT persisted as ground; only an AGGREGATE
      coverage tx (counts + the single top opening-priority headline) is appended — an
      observation, never a per-entity score-of-everyone.
    - G4: the schema cannot represent :trade / :forecast / :ai/score, so the heartbeat
      cannot emit them."
  (:require [kotoba.datom :as kd]
            [chie.methods.analyze :as analyze]
            [chie.methods.datom-emit :as de]
            [chie.methods.coverage-report :as cov]
            [chie.methods.digest :as digest]))

(defn ground-datoms
  "GROUND EAVT assertions: one kd/add per (entity, attribute, value) for every node + 縁.
  Attribute selection + edge id mirror chie.methods.datom-emit (the canonical projection)."
  [nodes node-order edges]
  (let [node-ds (for [nid (or (seq node-order) (keys nodes))
                      a de/node-attrs
                      :let [v (get-in nodes [nid a])]
                      :when (and (contains? (get nodes nid) a) (some? v))]
                  (kd/add nid a v))
        edge-ds (for [e edges
                      :let [eid (str "en." (get e ":en/from") "."
                                     (let [k (get e ":en/kind")]
                                       (if (clojure.string/starts-with? k ":") (subs k 1) k))
                                     "." (get e ":en/to"))]
                      a de/edge-attrs
                      :let [v (get e a)]
                      :when (and (contains? e a) (some? v))]
                  (kd/add eid a v))]
    (vec (concat node-ds edge-ds))))

(def ^:private coverage-keys
  [:n-nodes :n-edges :authoritative :representative :open :closed])

(defn coverage-datoms
  "AGGREGATE-ONLY observation assertions (G5): counts + the top opening-priority headline.
  No per-entity integral is persisted (N1/G2); the headline names ONE entity = the most
  concentration-and-closed (the actor's whole purpose: who most needs OPENING), disclosed."
  [c top cycle]
  (let [eid (str "coverage." cycle)
        base (into [(kd/add eid ":chie.coverage/cycle" cycle)]
                   (map (fn [k] (kd/add eid (str ":chie.coverage/" (name k)) (get c k))))
                   coverage-keys)]
    (if top
      (-> base
          (conj (kd/add eid ":chie.coverage/top-opening-id" (nth top 0)))
          (conj (kd/add eid ":chie.coverage/top-opening-load" (double (nth top 2)))))
      base)))

#?(:clj
   (def log-default
     "20-actors/chie/data/chie.datoms.kotoba.edn (resolved at the host edge)."
     (-> *file* clojure.java.io/file .getParentFile .getParentFile
         (clojure.java.io/file "data" "chie.datoms.kotoba.edn"))))

#?(:clj
   (defn run-cycle
     "One heartbeat. Returns an aggregate-only summary (G5)."
     [seed-path log-path]
     (let [cycle (inc (count (kd/read-log log-path)))
           {:keys [nodes node-order edges]} (analyze/load-file* seed-path)
           res (analyze/analyze nodes edges)
           c (cov/coverage nodes edges)
           top (first (analyze/rank (:opening res) nodes 1))
           datoms (into (ground-datoms nodes node-order edges)
                        (coverage-datoms c top cycle))
           tx (kd/make-tx datoms {:tx-id cycle :as-of cycle :prev-cid (kd/head-cid log-path)})
           cid (kd/append-tx! tx log-path)
           chain (kd/verify-chain log-path)]
       (when-not (:ok chain)
         (throw (ex-info (str "kotoba log chain broken at " (:broken-at chain)) chain)))
       {:cycle cycle
        :cid cid
        :datoms (count datoms)
        :chain-length (:length chain)
        :nodes (count nodes)
        :edges (count edges)
        :top-opening (when top [(nth top 0) (double (nth top 2))])
        :digest (digest/narrate nodes res c)   ;; Murakumo-only narration (template default; fail-open)
        :coverage (select-keys c coverage-keys)})))

#?(:clj
   (defn -main
     "CLI: run N heartbeat cycles against the local log (default 1)."
     [& argv]
     (let [argv (vec argv)
           here (-> *file* clojure.java.io/file .getParentFile .getParentFile)
           seed (clojure.java.io/file here "data" "seed-ai-ecosystem.kotoba.edn")
           cycles (if (some #{"--cycles"} argv)
                    (Long/parseLong (nth argv (inc (.indexOf argv "--cycles"))))
                    1)]
       (dotimes [_ cycles]
         (let [s (run-cycle seed log-default)]
           (println (str "chie heartbeat cycle " (:cycle s) " → cid " (:cid s)
                         " (" (:datoms s) " datoms, chain " (:chain-length s) ", top-opening "
                         (first (:top-opening s)) " " (format "%.3f" (second (:top-opening s))) ")"))))
       0)))
