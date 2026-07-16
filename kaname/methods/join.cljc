(ns kaname.methods.join
  "kaname 要 — live mirror JOIN (ADR-2606172100 R1). The system-of-systems join leg: read a sibling
  mirror's ALREADY-PRODUCED kotoba Datom-log output and LIFT its 縁 into a kaname domain layer of
  the multiplex, then reconcile entities across layers by label. Running a mirror to (re)produce its
  output is the G7/Council-gated step; joining its committed output is what kaname does here.

  Demonstrated on chie 智慧's REAL output (20-actors/chie/out/ai-ecosystem-datoms.kotoba.edn): its
  AI-ecosystem 縁 (invests-in / compute-deal / talent-flow / governs / sets-standard / depends-on)
  become the :ai layer of kaname's graph; kaname then computes leverage natively over the lifted
  real subgraph. When TWO+ mirrors are joined, an entity present in several (reconcile-by-label)
  gains domains across layers — and THAT cross-domain spanning is what makes it the 要.

  G1/G4 preserved: lifted nodes are :sos/entity structural positions; person nodes are dropped
  (a mirror's public-ROLE nodes lift as roles, private profiles are never present upstream).
  Pure fns; reuses kaname.methods.sos. Portable .cljc."
  (:require [clojure.string :as str]
            [kaname.methods.sos :as sos]
            #?(:clj [clojure.java.io :as io])))

;; ── parse a [e a v tx op] Datom-log into {:nodes :edges} ───────────────────────

(defn datoms->graph
  "Reconstruct {:nodes {id node} :node-order [..] :edges [edge]} from a vector of [e a v tx op]
  datoms (op :add only; later wins). Entities whose id starts \"en.\" with :en/from become edges;
  entities with :organism/kind become nodes."
  [datoms]
  (let [by-e (reduce
              (fn [m d]
                (if (and (vector? d) (>= (count d) 5) (= ":add" (nth d 4)))
                  (let [[e a v] d]
                    (-> m
                        (update-in [:attrs e] (fnil assoc {}) a v)
                        (update :order (fn [o] (if (contains? (:attrs m) e) o (conj o e))))))
                  m))
              {:attrs {} :order []}
              datoms)
        attrs (:attrs by-e)]
    (reduce
     (fn [acc e]
       (let [m (get attrs e)]
         (cond
           (and (str/starts-with? e "en.") (contains? m ":en/from"))
           (update acc :edges conj (assoc m ":en/id" e))
           (contains? m ":organism/kind")
           (-> acc
               (assoc-in [:nodes e] (assoc m ":organism/id" e))
               (update :node-order conj e))
           :else acc)))
     {:nodes {} :node-order [] :edges []}
     (:order by-e))))

#?(:clj
   (defn read-datom-log
     "Read a mirror's Datom-log EDN file → {:nodes :node-order :edges}."
     [path]
     (datoms->graph (sos/read-edn (slurp (str path))))))

(defn parse-graph
  "Auto-detect a mirror EDN graph: a vector of MAPS is a forms-graph (sos/load-graph); a vector of
  5-element VECTORS is a [e a v tx op] Datom log (datoms->graph). Returns {:nodes :node-order :edges}."
  [forms]
  (let [el (first (filter some? forms))]
    (cond
      (map? el)    (sos/load-graph forms)
      (vector? el) (datoms->graph forms)
      :else        {:nodes {} :node-order [] :edges []})))

#?(:clj
   (defn read-graph
     "Read a mirror EDN file in EITHER format (forms-graph or Datom log) → {:nodes :node-order :edges}."
     [path]
     (parse-graph (sos/read-edn (slurp (str path))))))

;; ── lift a mirror graph into a kaname domain layer ────────────────────────────

(def default-kind-map
  "Generic mirror 縁-kind → kaname kind. Accumulation/dependence/standard-setting map onto kaname
  vocabulary; reciprocal/structural kinds (:partners) drop (no axis). Identity entries for kaname's
  own kinds let an already-kaname-form mirror (e.g. the web-ingest output) pass through unchanged."
  {":compute-deal"  ":concentrates"   ":invests-in"   ":concentrates"
   ":talent-flow"   ":concentrates"   ":supplies"     ":concentrates"
   ":controls"      ":concentrates"   ":influences"   ":influences"
   ":governs"       ":gates"          ":sets-standard" ":gates"
   ":depends-on"    ":depends-on"     ":couples"      ":couples"
   ;; identity (kaname-form mirrors)
   ":concentrates"  ":concentrates"   ":gates"        ":gates"})

(defn lift
  "Lift a mirror {:nodes :edges} into kaname forms (vector of node + edge maps) in `domain`,
  tagged `source-actor`. Node ids are namespaced \"<tag>/<id>\" so distinct mirrors never collide
  pre-reconcile. Person ROLE nodes are kept as :sos/role; everything else as :sos/entity. Edges with
  an unmapped kind are dropped (honest — no fabricated axis).
  opts: {:kind-map <merged over default-kind-map>, :weight-attr <load attr, default :en/grasping-load>,
         :default-load <load for present-but-unweighted edges, default 0.0>,
         :scale <multiply every lifted load by this — for per-mirror normalization, default 1.0>}."
  [{:keys [nodes node-order edges]} domain source-actor & [opts]]
  (let [opts (if (map? opts) opts {:kind-map opts})
        km (merge default-kind-map (:kind-map opts))
        watt (:weight-attr opts ":en/grasping-load")
        dload (double (:default-load opts 0.0))
        scale (double (:scale opts 1.0))
        tag (str (if (str/starts-with? (str source-actor) ":") (subs source-actor 1) source-actor) "/")
        pfx (fn [id] (str tag id))
        role? (fn [n] (let [k (str (get n ":organism/kind"))]
                        (boolean (re-find #"(?i)role|person" k))))
        node-forms
        (mapv (fn [id]
                (let [n (get nodes id)
                      open? (let [o (get n ":ai/open?")] (true? o))]
                  (cond-> {":organism/id"   (pfx id)
                           ":organism/kind" (if (role? n) ":sos/role" ":sos/entity")
                           ":organism/label" (get n ":organism/label" id)
                           ":organism/sourcing" ":representative"
                           ":sos/source-actors" [source-actor]
                           ":sos/open?"     open?}
                    (get n ":sos/role") (assoc ":sos/role" (get n ":sos/role")))))
              (or (seq node-order) (keys nodes)))
        edge-forms
        (->> edges
             (keep (fn [e]
                     (when-let [k (get km (get e ":en/kind"))]
                       (let [l (get e watt)]
                         {":en/from" (pfx (get e ":en/from"))
                          ":en/to"   (pfx (get e ":en/to"))
                          ":en/kind" k
                          ":en/domain" domain
                          ":en/grasping-load" (* scale (if (number? l) (double l) dload))
                          ":en/sourcing" ":representative"}))))
             vec)]
    (into node-forms edge-forms)))

;; ── reconcile entities across layers by label ─────────────────────────────────

(defn- norm-label
  "Normalize an entity label for cross-mirror reconciliation: lower-case, drop trailing
  parentheticals (\"(AWS)\"/\"(Alphabet)\") and common corporate suffixes (Inc/Corp/Group/…), collapse
  whitespace. Conservative — keeps distinct multi-word names (e.g. \"google deepmind\" ≠ \"google\")."
  [s]
  (-> (str s)
      str/lower-case
      (str/replace #"\([^)]*\)" " ")
      (str/replace #"[.,]" " ")
      (str/replace #"(?i)\b(inc|corp|corporation|co|ltd|limited|llc|plc|group|holdings|platforms|ag|sa|nv)\b" " ")
      str/trim
      (str/replace #"\s+" " ")))

(defn reconcile-by-label
  "Merge kaname node-forms that share a normalized label into one canonical node (lowest id wins),
  unioning :sos/source-actors; rewrite every edge endpoint to the canonical id. THIS is what lets a
  shared entity span multiple domain layers (→ higher versatility → the 要). Returns rewritten forms."
  [forms]
  (let [nodes (filter #(contains? % ":organism/id") forms)
        edges (filter #(contains? % ":en/from") forms)
        ;; label → canonical id (deterministic: lowest id)
        canon (reduce (fn [m n]
                        (let [lab (norm-label (get n ":organism/label"))
                              id  (get n ":organism/id")]
                          (update m lab (fn [cur] (if (or (nil? cur) (neg? (compare id cur))) id cur)))))
                      {} nodes)
        id->canon (into {} (map (fn [n]
                                  [(get n ":organism/id")
                                   (get canon (norm-label (get n ":organism/label")))])
                                nodes))
        ;; merge nodes onto canonical id, unioning source-actors + OR-ing open?
        merged (reduce (fn [m n]
                         (let [cid (id->canon (get n ":organism/id"))
                               cur (get m cid)
                               srcs (into (set (get cur ":sos/source-actors" []))
                                          (get n ":sos/source-actors" []))]
                           (assoc m cid
                                  (-> (or cur n)
                                      (assoc ":organism/id" cid)
                                      (assoc ":sos/source-actors" (vec (sort srcs)))
                                      (assoc ":sos/open?" (or (true? (get cur ":sos/open?"))
                                                              (true? (get n ":sos/open?"))))))))
                       {} nodes)
        node-forms (vec (vals merged))
        edge-forms (mapv (fn [e]
                           (-> e
                               (assoc ":en/from" (id->canon (get e ":en/from") (get e ":en/from")))
                               (assoc ":en/to"   (id->canon (get e ":en/to")   (get e ":en/to")))))
                         edges)]
    (into node-forms edge-forms)))

(defn forms->graph
  "Convenience: forms (node + edge maps) → sos/load-graph result."
  [forms]
  (sos/load-graph forms))

;; ── per-mirror adapters (paths relative to 20-actors/) ────────────────────────
;; Each mirror has its OWN 縁 vocabulary; the adapter maps it into kaname's. Unmapped kinds drop
;; (no fabricated axis). weight-attr/default-load handle mirrors that don't carry :en/grasping-load.

(def mirror-adapters
  {:chie     {:path "chie/out/ai-ecosystem-datoms.kotoba.edn" :domain ":ai" :source ":chie"
              :kind-map {":compute-deal" ":concentrates" ":invests-in" ":concentrates"
                         ":talent-flow" ":concentrates" ":governs" ":gates"
                         ":sets-standard" ":gates" ":depends-on" ":depends-on"}}
   :tsumugi  {:path "tsumugi/out/woven-graph.kotoba.edn" :domain ":organization" :source ":tsumugi"
              :kind-map {":tends" ":concentrates" ":custodies" ":concentrates"
                         ":depends-on" ":depends-on" ":nests-in" ":couples"}}
   :inochi   {:path "inochi/data/seed-biosphere-graph.kotoba.edn" :domain ":ecology" :source ":inochi"
              :kind-map {":pressures" ":concentrates" ":depends-on" ":depends-on"
                         ":keystone-of" ":gates"}}
   :hokorobi {:path "hokorobi/data/seed-finrisk-graph.kotoba.edn" :domain ":economy" :source ":hokorobi"
              :weight-attr ":en/intensity" :default-load 0.5
              :kind-map {":exposes" ":concentrates" ":backstops" ":gates"
                         ":interconnects" ":couples" ":capitalizes" ":concentrates"}}
   :shiori   {:path "shiori/data/seed-wellbecoming-graph.kotoba.edn" :domain ":wellbecoming" :source ":shiori"
              :weight-attr ":en/intensity" :default-load 0.5
              :kind-map {":diminishes" ":concentrates" ":drives" ":concentrates"
                         ":relieves" ":gates" ":routes-to" ":couples"}}
   ;; :web — REAL web-ingested mirror (G7 founder-approved): DISCLOSED org relations from public
   ;; announcement pages (公開投稿) via Murakumo/Ollama gemma-4-E4B, every edge basis'd. Already
   ;; kaname-form → identity kind-map. See methods/ingest.cljc + data/ingested-pages/.
   :web      {:path "kaname/data/ingested-web.kotoba.edn" :domain ":economy" :source ":web"
              :kind-map {":concentrates" ":concentrates" ":depends-on" ":depends-on"
                         ":couples" ":couples" ":gates" ":gates"}}
   ;; :amime — the ENERGY-FLOW mesh mirror (ADR-2606212000 / 2606212020). amime 網目 solves the
   ;; multi-site energy mesh and commits a kaname-form :energy graph: flow concentrates onto loads
   ;; (:concentrates), single-path import is a :depends-on SPOF. Already kaname-form → identity
   ;; kind-map; lifts straight into the :energy domain layer. Running amime = G7; joining its
   ;; committed out/energy-sos.kotoba.edn = what kaname does here.
   :amime    {:path "amime/out/energy-sos.kotoba.edn" :domain ":energy" :source ":amime"
              :kind-map {":concentrates" ":concentrates" ":depends-on" ":depends-on"
                         ":couples" ":couples" ":gates" ":gates"}}})

#?(:clj
   (defn join-mirrors
     "Join several committed mirror outputs into ONE reconciled kaname multilayer graph.
     `base-dir` = the 20-actors directory; `ks` = adapter keys (default all that exist on disk).
     Returns {:forms reconciled-forms :graph {:nodes :node-order :edges} :loaded [adapter-keys]}."
     [base-dir & [ks opts]]
     (let [ks (or ks (keys mirror-adapters))
           normalize? (get opts :normalize true)   ; per-mirror max-load → 1.0 (fair cross-domain compare)
           present (filter (fn [k] (.exists (io/file base-dir (:path (mirror-adapters k))))) ks)
           lifted (mapcat (fn [k]
                            (let [{:keys [path domain source kind-map weight-attr default-load]} (mirror-adapters k)
                                  watt (or weight-attr ":en/grasping-load")
                                  dload (or default-load 0.0)
                                  g (read-graph (io/file base-dir path))
                                  maxl (reduce (fn [m e]
                                                 (let [l (get e watt)]
                                                   (max m (if (number? l) (double l) (double dload)))))
                                               0.0 (:edges g))
                                  scale (if (and normalize? (> maxl 1e-9)) (/ 1.0 maxl) 1.0)]
                              (lift g domain source {:kind-map kind-map :weight-attr watt
                                                     :default-load dload :scale scale})))
                          present)
           recon (reconcile-by-label (vec lifted))]
       {:forms recon :graph (forms->graph recon) :loaded (vec present)})))

(defn- lstrip [s] (if (and (string? s) (str/starts-with? s ":")) (subs s 1) s))

(defn joined-report-md
  "Render the multi-mirror SoS leverage report: cross-domain entities (V≥2) + bridges. `res1` is the
  R1 result (kaname.methods.centrality/leverage-r1) so betweenness/L1 are available."
  [nodes edges res1 loaded]
  (let [cross (->> (:V res1)
                   (filter (fn [[_ v]] (>= v 2)))
                   (sort-by (fn [[nid _]] [(- (double (get-in res1 [:leverage-r1 nid] 0.0))) nid]))
                   (take 12))
        domstr (fn [nid] (str/join "·" (map lstrip (sort (get-in res1 [:domains nid])))))
        srcstr (fn [nid] (str/join "," (map lstrip (get-in nodes [nid ":sos/source-actors"]))))
        L (transient [])]
    (conj! L "# kaname 要 — JOINED multi-mirror SoS leverage (REAL committed mirror outputs)\n")
    (conj! L (str "> Mirrors joined: **" (str/join " · " (map #(lstrip (str %)) loaded)) "** — "
                  (count nodes) " nodes / " (count edges) " 縁 across "
                  (count (distinct (keep #(get % ":en/domain") edges))) " domain layers. Per-mirror "
                  "loads NORMALIZED (each mirror's max → 1.0) for fair cross-domain comparison; "
                  "mirrors with no native 縁-load (shiori/hokorobi) use a flat representative 0.5 "
                  "(flagged, not a measured magnitude). **Running a mirror to (re)produce its output "
                  "= G7/Council-gated; joining a committed output = what kaname does.** G1 — "
                  "structural positions (organisations, not natural persons). G2 — routed to OPENING.\n"))
    (conj! L "\n## Cross-domain entities (V≥2) — the system-of-systems 要 candidates\n")
    (conj! L "_An entity reconciled across multiple mirrors spans multiple domain layers — THAT cross-domain spanning is what makes it a system-of-systems leverage point._\n")
    (conj! L "| rank | entity | domains | source mirrors | betweenness | L1 |")
    (conj! L "|---:|---|---|---|---:|---:|")
    (doseq [[i [nid v]] (map-indexed vector cross)]
      (conj! L (str "| " (inc i) " | " (get-in nodes [nid ":organism/label"] nid)
                    " | " (domstr nid) " (V=" v ")"
                    " | " (srcstr nid)
                    " | " (format "%.1f" (double (get-in res1 [:betweenness nid] 0.0)))
                    " | " (format "%.3f" (double (get-in res1 [:leverage-r1 nid] 0.0))) " |")))
    (conj! L "\n## Highest-betweenness structural bridges (cross-layer chokepoints)\n")
    (conj! L "_The positions most shortest-paths run through — the connective chokepoints, routed to redundancy/route-around._\n")
    (conj! L "| rank | node | betweenness |")
    (conj! L "|---:|---|---:|")
    (doseq [[i [_ label b]] (map-indexed vector (sos/rank (:betweenness res1) nodes 10))]
      (conj! L (str "| " (inc i) " | " label " | " (format "%.1f" b) " |")))
    (conj! L (str "\n---\n_kaname 要 · ADR-2606172100 R1 · multi-mirror SoS join over committed mirror "
                  "outputs · per-mirror normalized · exact Brandes betweenness · map-not-target · "
                  "opening-only · non-adjudicating._\n"))
    (str/join "\n" (persistent! L))))

#?(:clj
   (defn -main
     "MULTI-MIRROR JOIN: join every committed sibling-mirror output that exists on disk into ONE
     reconciled kaname multilayer graph, compute the cross-domain leverage, and surface the entities
     that span multiple domains (the system-of-systems 要). Writes out/joined-sos-leverage.md."
     [& argv]
     (let [here (-> *file* io/file .getParentFile .getParentFile)
           base (io/file here "..")
           outdir (io/file here "out")
           {:keys [graph loaded]} (join-mirrors base)
           {:keys [nodes edges]} graph
           res (sos/leverage nodes edges)
           res1 (require 'kaname.methods.centrality)
           res1 ((resolve 'kaname.methods.centrality/leverage-r1) nodes edges res)]
       (.mkdirs outdir)
       (spit (io/file outdir "joined-sos-leverage.md") (joined-report-md nodes edges res1 loaded))
       (println (str "kaname multi-mirror join: " (pr-str loaded)))
       (println (str "  reconciled graph: " (count nodes) " nodes, " (count edges) " 縁"))
       (println "  cross-domain 要 candidates (V≥2):")
       (doseq [[nid v] (->> (:V res)
                            (filter (fn [[_ v]] (>= v 2)))
                            (sort-by (fn [[nid _]] [(- (double (get-in res1 [:leverage-r1 nid] 0.0))) nid]))
                            (take 6))]
         (println (str "    " (get-in nodes [nid ":organism/label"] nid)
                       "  V=" v "  L1=" (format "%.3f" (get-in res1 [:leverage-r1 nid] 0.0)))))
       0)))
