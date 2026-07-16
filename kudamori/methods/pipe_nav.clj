;; kudamori 管守 — in-pipe crawler navigation over a sewer-network graph.
;;
;;   * diameter-fit — the crawler OD must fit inside the pipe ID minus a clearance
;;     margin; a segment it cannot enter RAISES (never crawl a pipe you don't fit);
;;   * shortest-route — BFS by hop count from the access manhole to a target segment
;;     over the undirected pipe graph;
;;   * blockage handling — a route may be required to AVOID blocked segments (route
;;     around), or the blockage is flagged when it is the cleaning target itself.
;;
;; Pure Clojure, no deps → babashka-runnable AND kotoba-pywasm-portable.
;; Per ADR-2606142030 (kudamori R0).
(ns kudamori.methods.pipe-nav)

;; ── diameter fit ─────────────────────────────────────────────────────────────
(defn fits?
  "True iff a crawler of `od-mm` outer diameter fits inside a pipe of `id-mm` inner
   diameter leaving at least `clearance-mm` total radial clearance."
  [od-mm id-mm clearance-mm]
  (<= (+ od-mm clearance-mm) id-mm))

(defn assert-fit!
  "Return the segment if the crawler fits; RAISE otherwise — a crawler must never be
   sent into a pipe it cannot physically clear."
  [{:keys [od-mm clearance-mm]} segment]
  (when-not (fits? od-mm (:id-mm segment) clearance-mm)
    (throw (ex-info "crawler does not fit pipe (diameter-fit failure)"
                    {:od-mm od-mm :clearance-mm clearance-mm
                     :segment (:id segment) :id-mm (:id-mm segment)})))
  segment)

;; ── adjacency over the pipe graph ────────────────────────────────────────────
(defn adjacency
  "Undirected adjacency {node-id #{[neighbour-id segment-id]…}} from segments,
   optionally dropping blocked segments (route-around)."
  ([segments] (adjacency segments false))
  ([segments avoid-blocked?]
   (reduce
    (fn [g {:keys [from to id blocked?]}]
      (if (and avoid-blocked? blocked?)
        g
        (-> g
            (update from (fnil conj #{}) [to id])
            (update to (fnil conj #{}) [from id]))))
    {} segments)))

(defn shortest-route
  "BFS shortest node path (by hop count) from `start` to `goal` over `segments`.
   Returns {:nodes [node-id…] :segments [segment-id…]} or nil if unreachable.
   When `avoid-blocked?` is true, blocked segments are excluded from the graph."
  ([segments start goal] (shortest-route segments start goal false))
  ([segments start goal avoid-blocked?]
   (let [g (adjacency segments avoid-blocked?)]
     (if (= start goal)
       {:nodes [start] :segments []}
       (loop [frontier [[start [start] []]]
              seen #{start}]
         (if (empty? frontier)
           nil
           (let [[node npath spath] (first frontier)
                 nbrs (for [[nb seg] (get g node #{})
                            :when (not (seen nb))]
                        [nb (conj npath nb) (conj spath seg)])
                 hit (some (fn [[nb np sp]] (when (= nb goal) {:nodes np :segments sp})) nbrs)]
             (or hit
                 (recur (vec (concat (rest frontier) nbrs))
                        (into seen (map first nbrs)))))))))))

;; ── plan a navigation to a target SEGMENT ────────────────────────────────────
(defn- seg-by-id [segments sid] (first (filter #(= (:id %) sid) segments)))

(defn plan-nav
  "Plan the crawler navigation from `access` manhole to a target segment.
   Walks to the nearer endpoint of the target segment, checks diameter-fit on EVERY
   traversed segment AND the target itself (raises on a no-fit), and reports whether
   the target is blocked (the cleaning case). Returns a plan map.
   Routes AROUND other blocked segments when `avoid-blocked?` (default true)."
  ([robot segments access target-seg-id]
   (plan-nav robot segments access target-seg-id true))
  ([robot segments access target-seg-id avoid-blocked?]
   (let [tseg (seg-by-id segments target-seg-id)]
     (when (nil? tseg)
       (throw (ex-info "unknown target segment" {:target target-seg-id})))
     ;; pick the reachable endpoint with the shorter route
     (let [routes (->> [(:from tseg) (:to tseg)]
                       (keep (fn [endpoint]
                               (when-let [r (shortest-route segments access endpoint avoid-blocked?)]
                                 (assoc r :endpoint endpoint))))
                       (sort-by #(count (:segments %))))
           route (first routes)]
       (when (nil? route)
         (throw (ex-info "target segment unreachable from access manhole"
                         {:access access :target target-seg-id})))
       ;; fit-check every traversed segment and the target itself (raises on no-fit)
       (doseq [sid (conj (:segments route) target-seg-id)]
         (assert-fit! robot (seg-by-id segments sid)))
       {:access access
        :target target-seg-id
        :target-blocked? (boolean (:blocked? tseg))
        :route-nodes (:nodes route)
        :route-segments (:segments route)
        :hops (count (:segments route))
        :fits true}))))
