(ns kaname.methods.centrality
  "kaname 要 — R1 multilayer centrality (ADR-2606172100 R1). Upgrades the R0 bridge-load PROXY to
  real graph-theoretic measures over the multiplex projection:

    BETWEENNESS  — Brandes exact shortest-path betweenness over the undirected projection. B_i =
                   how many shortest paths between OTHER positions run through i. A true bridge /
                   cut-vertex measure — the honest replacement for the R0 bridge-load proxy.
    EIGENVECTOR  — power-iteration eigenvector centrality on the load-weighted adjacency. x_i high
                   when i is connected to other high-load positions (versatility-of-influence).
    ΔΦ           — percolation/fragmentation sensitivity: dissolving i (open its concentration +
                   drop it from the graph) strands the grasping-load of any component that detaches
                   from the giant component. ΔΦ_i = C_i (concentration dissolved) + stranded-load
                   (cascade). This is the concrete reading of 'L ≈ ΔΦ' — the system-fragility drop
                   if the 要 is opened.

    L1 (R1 leverage) = C_i · (V_i/D) · (1 + B'_i) · (1 − open_i), where B'_i = betweenness
                   normalized to [0,1]. argmax = the 要 (same shape as R0, real betweenness inside).

  All deterministic + pure (node order sorted; ties broken by id). Reuses kaname.methods.sos.
  Portable .cljc (clj-native). Full multiplex eigenvector-versatility (De Domenico et al.) over the
  tensor stays a future refinement; this R1 ships exact projection betweenness + ΔΦ."
  (:require [clojure.string :as str]
            [clojure.set :as set]
            [kaname.methods.sos :as sos]
            #?(:clj [clojure.java.io :as io])))

;; ── undirected projection ─────────────────────────────────────────────────────

(defn adjacency
  "Undirected adjacency {node #{neighbor…}} + weighted {node {neighbor load}} from edges.
  Self-loops dropped; parallel edges keep max load (weighted) / single neighbor (unweighted)."
  [nodes edges]
  (let [ids (set (keys nodes))]
    (reduce
     (fn [{:keys [adj w] :as a} e]
       (let [u (get e ":en/from") v (get e ":en/to")
             l (let [x (get e ":en/grasping-load")] (if (number? x) (double x) 0.0))]
         (if (and (contains? ids u) (contains? ids v) (not= u v))
           {:adj (-> adj (update u (fnil conj #{}) v) (update v (fnil conj #{}) u))
            :w   (-> w
                     (update-in [u v] (fnil max 0.0) l)
                     (update-in [v u] (fnil max 0.0) l))}
           a)))
     {:adj (into {} (map (fn [id] [id #{}]) ids))
      :w   (into {} (map (fn [id] [id {}]) ids))}
     edges)))

;; ── Brandes exact betweenness (unweighted) ────────────────────────────────────

(defn- brandes-source
  "Single-source dependency accumulation from s. Returns a map node→partial-betweenness."
  [adj order s]
  (let [;; BFS: dist, sigma (# shortest paths), preds, stack (reverse BFS order)
        init {:dist {s 0} :sigma {s 1.0} :preds {} :stack [] :queue [s]}
        {:keys [sigma preds stack]}
        (loop [{:keys [dist sigma preds stack queue] :as st} init]
          (if (empty? queue)
            st
            (let [v (first queue)
                  q (subvec (vec queue) 1)
                  st (assoc st :stack (conj stack v) :queue q)
                  st (reduce
                      (fn [st w]
                        (let [dv (get (:dist st) v)
                              dw (get (:dist st) w)]
                          (cond
                            (nil? dw)                       ; first visit
                            (-> st
                                (assoc-in [:dist w] (inc dv))
                                (update :queue conj w)
                                (assoc-in [:sigma w] (get (:sigma st) v))
                                (assoc-in [:preds w] [v]))
                            (= dw (inc dv))                 ; another shortest path
                            (-> st
                                (update-in [:sigma w] + (get (:sigma st) v))
                                (update-in [:preds w] conj v))
                            :else st)))
                      st
                      (sort (get adj v)))]              ; sorted neighbors → deterministic
              (recur st))))
        ;; back-propagate dependencies
        delta (reduce
               (fn [delta w]
                 (let [coeff (/ (+ 1.0 (get delta w 0.0)) (get sigma w))]
                   (reduce (fn [d v]
                             (update d v (fnil + 0.0) (* (get sigma v) coeff)))
                           delta
                           (get preds w []))))
               {}
               (reverse stack))]
    (dissoc delta s)))

(defn betweenness
  "Exact Brandes betweenness {node value} over the undirected projection (halved for undirected)."
  [nodes edges]
  (let [{:keys [adj]} (adjacency nodes edges)
        order (sort (keys nodes))
        raw (reduce (fn [acc s]
                      (merge-with + acc (brandes-source adj order s)))
                    {}
                    order)]
    (into {} (map (fn [id] [id (/ (get raw id 0.0) 2.0)]) order))))

;; ── eigenvector centrality (power iteration, load-weighted) ───────────────────

(defn eigenvector
  "Power-iteration eigenvector centrality {node value} on the load-weighted symmetric adjacency.
  Deterministic (fixed iters, L2-normalized, sorted keys)."
  ([nodes edges] (eigenvector nodes edges 100))
  ([nodes edges iters]
   (let [{:keys [w]} (adjacency nodes edges)
         ids (sort (keys nodes))
         x0 (into {} (map (fn [id] [id 1.0]) ids))]
     (loop [x x0, k iters]
       (if (zero? k)
         x
         (let [x' (into {} (map (fn [id]
                                  [id (reduce (fn [s [nbr l]] (+ s (* l (get x nbr 0.0))))
                                              0.0 (get w id {}))])
                                ids))
               norm (Math/sqrt (reduce + 0.0 (map (fn [v] (* v v)) (vals x'))))
               x' (if (> norm 1e-12)
                    (into {} (map (fn [[id v]] [id (/ v norm)]) x'))
                    x0)]
           (recur x' (dec k))))))))

;; ── ΔΦ percolation / fragmentation sensitivity ────────────────────────────────

(defn- components
  "Connected components of the projection MINUS `drop-id`, as a seq of node-sets."
  [adj drop-id]
  (let [nodes (disj (set (keys adj)) drop-id)]
    (loop [unseen nodes, comps []]
      (if (empty? unseen)
        comps
        (let [start (first (sort unseen))
              comp (loop [stack [start], seen #{}]
                     (if (empty? stack)
                       seen
                       (let [v (peek stack)
                             stack (pop stack)]
                         (if (contains? seen v)
                           (recur stack seen)
                           (recur (into stack (remove #(= % drop-id) (get adj v #{})))
                                  (conj seen v))))))]
          (recur (set/difference unseen comp) (conj comps comp)))))))

(defn- edge-load-within
  "Σ grasping-load of edges whose BOTH endpoints lie in node-set `s` (and neither is drop-id)."
  [edges s drop-id]
  (reduce (fn [acc e]
            (let [u (get e ":en/from") v (get e ":en/to")
                  l (let [x (get e ":en/grasping-load")] (if (number? x) (double x) 0.0))]
              (if (and (contains? s u) (contains? s v) (not= u drop-id) (not= v drop-id))
                (+ acc l) acc)))
          0.0 edges))

(defn delta-phi
  "ΔΦ {node value} — fragmentation sensitivity. Opening node i dissolves its concentration C_i and
  strands the internal load of any component that detaches from the giant component when i is
  removed. ΔΦ_i = C_i + stranded-load."
  [nodes edges res]
  (let [{:keys [adj]} (adjacency nodes edges)
        order (sort (keys nodes))]
    (into {}
          (map (fn [id]
                 (let [comps (components adj id)
                       comps (sort-by (fn [c] [(- (count c)) (first (sort c))]) comps)
                       giant (first comps)
                       stranded (reduce + 0.0
                                        (map #(edge-load-within edges % id) (rest comps)))
                       c-i (double (get-in res [:C id] 0.0))]
                   [id (+ c-i stranded)]))
               order))))

;; ── R1 leverage (real betweenness inside) ─────────────────────────────────────

(defn leverage-r1
  "Augment the R0 result with R1 centrality and the R1 leverage L1.
  L1_i = C_i · (V_i/D) · (1 + B'_i) · (1 − open_i), B'_i = betweenness / max(betweenness)."
  [nodes edges res]
  (let [bw (betweenness nodes edges)
        ev (eigenvector nodes edges)
        dphi (delta-phi nodes edges res)
        bmax (reduce max 1e-12 (vals bw))
        open? (fn [nid] (if (true? (get-in nodes [nid ":sos/open?"])) 1.0 0.0))
        l1 (into {}
                 (map (fn [[nid c]]
                        (let [v  (/ (double (get-in res [:V nid] 0)) (double sos/D))
                              b' (/ (double (get bw nid 0.0)) bmax)]
                          [nid (* c v (+ 1.0 b') (- 1.0 (open? nid)))]))
                      (:C res)))]
    (assoc res :betweenness bw :eigenvector ev :delta-phi dphi :leverage-r1 l1)))

(defn kaname-point-r1
  "The 要 by R1 leverage, as [id label L1], or nil."
  [res1 nodes]
  (first (sos/rank (:leverage-r1 res1) nodes 1)))

(defn leverage-concentration
  "Meta-metric on the R1 leverage distribution: is the system's leverage CONCENTRATED in one 要 (a
  single structural position whose opening dominates — effectively a single point of leverage) or
  DISTRIBUTED across many comparable positions (no single chokepoint; more resilient, but no one
  fix)? Computes the HHI of the per-node leverage shares, the top position's share, and the EFFECTIVE
  NUMBER of leverage points (1/HHI, the inverse-Simpson / Hill number — '≈N comparable points'). A
  structural read OF the leverage MAP — an aggregate distribution property, not a per-entity score
  (G4) and never a target-list (G1, structural positions only) — routed to the resilience picture: a
  low effective-number says the 要 is decisive (act there); a high one says leverage is diffuse.
  Returns {:hhi :top-share :top-id :effective-points :concentrated?} (concentrated? = fewer than 2
  effective points); an empty / zero leverage map yields nils + concentrated? false."
  [res1]
  (let [lev (:leverage-r1 res1)
        total (reduce + 0.0 (vals lev))]
    (if (pos? total)
      (let [shares (into {} (map (fn [[k v]] [k (/ v total)]) lev))
            hhi (reduce + 0.0 (map #(* % %) (vals shares)))
            [top-id top-share] (apply max-key val shares)]
        {:hhi hhi :top-share top-share :top-id top-id
         :effective-points (/ 1.0 hhi) :concentrated? (< (/ 1.0 hhi) 2.0)})
      {:hhi nil :top-share nil :top-id nil :effective-points nil :concentrated? false})))

;; ── report ─────────────────────────────────────────────────────────────────────

(defn- fmt3 [v] (format "%.3f" (double v)))
(defn- lstrip [s] (if (and (string? s) (str/starts-with? s ":")) (subs s 1) s))

(defn report-md
  [nodes edges res1]
  (let [kp (kaname-point-r1 res1 nodes)
        L (transient [])]
    (conj! L "# kaname 要 — R1 multilayer centrality (real betweenness · eigenvector · ΔΦ)\n")
    (conj! L (str "> R1 replaces the R0 bridge-load proxy with **exact Brandes betweenness**, "
                  "load-weighted **eigenvector** centrality, and **ΔΦ** percolation sensitivity "
                  "(the concrete reading of L ≈ ΔΦ). G1/G2 unchanged: structural positions only, "
                  "routed to OPENING.\n"))
    (when kp
      (conj! L (str "\n**要 (R1):** " (nth kp 1) " — L1 " (fmt3 (nth kp 2)) "\n")))
    (conj! L "\n| rank | structural position | C | V | betweenness | eigenvector | ΔΦ | L1 |")
    (conj! L "|---:|---|---:|---:|---:|---:|---:|---:|")
    (doseq [[i [nid _ l1]] (map-indexed vector (sos/rank (:leverage-r1 res1) nodes 12))]
      (let [label (get-in nodes [nid ":organism/label"] nid)]
        (conj! L (str "| " (inc i) " | " label
                      " | " (fmt3 (get-in res1 [:C nid] 0.0))
                      " | " (get-in res1 [:V nid] 0)
                      " | " (fmt3 (get-in res1 [:betweenness nid] 0.0))
                      " | " (fmt3 (get-in res1 [:eigenvector nid] 0.0))
                      " | " (fmt3 (get-in res1 [:delta-phi nid] 0.0))
                      " | " (fmt3 l1) " |"))))
    (conj! L (str "\n---\n_kaname 要 · ADR-2606172100 R1 · exact projection betweenness + ΔΦ; full "
                  "multiplex tensor eigenvector-versatility = future refinement. Live mirror join "
                  "→ join.cljc (G7-gated run)._\n"))
    (str/join "\n" (persistent! L))))

#?(:clj
   (defn -main
     [& argv]
     (let [argv (vec argv)
           here (-> *file* io/file .getParentFile .getParentFile)
           seed (if (and (seq argv) (not (str/starts-with? (first argv) "--")))
                  (io/file (first argv))
                  (io/file here "data" "seed-sos.kotoba.edn"))
           outdir (io/file here "out")
           {:keys [nodes edges]} (sos/load-file* seed)
           res (sos/leverage nodes edges)
           res1 (leverage-r1 nodes edges res)]
       (.mkdirs outdir)
       (spit (io/file outdir "centrality-r1.md") (report-md nodes edges res1))
       (when-let [kp (kaname-point-r1 res1 nodes)]
         (println (str "kaname R1 要: " (nth kp 1) " (L1=" (fmt3 (nth kp 2)) ")")))
       0)))
