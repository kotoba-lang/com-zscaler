(ns kumi.methods.kumi
  "kumi 組 — community/organization-unit dependency-influence-follow graph +
  system-dynamics observatory (ADR-2607101800-family). The EXTERNAL-community
  sibling of kizuna (internal actor-society graph) and keizu (government
  public-role graph): kumi graphs political / religious / cultural /
  historical / sports / civic / labor COMMUNITIES-as-units, never individual
  people, connected by follows / depends-on / influences ties, and reads off
  a junkan-style loop regime (virtuous/vicious/neutral/transitioning) plus a
  kaname-compatible leverage read (computed on read only, never stored).

  The pipeline (one beat): parse(seed) → graph → loop-classify → leverage-read
  → beat. Pure + deterministic (sorted node order; no wall clock, no
  randomness). Portable .cljc (bb).

  Gates (in code + tests):
   G1 person-excluded — a :person/* / :sev/human node is refused at parse. A
      community is a public-role entity, never a private individual or leader
      (mirrors kizuna G3, keizu G1, kaname G1).
   G2 public-declaration-only sourcing — a :kumi/depends-on tie needs >=2
      :sources (public charter/registry/citation refs); under-sourced ties are
      refused at parse, not silently dropped (mirrors keizu G3).
   G3 non-adjudicating / no belief-content — no doctrinal/ideological/verdict
      field is representable anywhere in this namespace; communities are
      structural nodes only (mirrors kaname G5, danjo G4).
   G4 no-causal-overclaim on :kumi/influences — every :influences tie MUST
      carry :co-occurrence-observed true; a bare/implicit causal claim is
      refused at parse, and no :causal-claim field is ever representable
      (mirrors junkan G5).
   G5 PROPOSE-not-act / no actuator — `beat` returns append-only findings
      data only. There is no dispatch/post/mention/execute path anywhere in
      this namespace — stronger than kizuna/kaname (which at least propose via
      ossekai): kumi has no actuator cell at all (mirrors junkan \"分析するだけ\").
   G6 edge-primary, no stored per-community power score — `leverage-read` is a
      pure function computed ON READ over the graph; its output is never
      written back onto a community record (no :kumi/power-score attr exists
      anywhere in this file) (mirrors keizu G4).
   G7 resilience-routing only, never a target-list — findings carry no
      priority/target/rank field; enforced by absence (mirrors kabuto/busshi/
      abaki's target-list prohibition).
   G8 Murakumo-only inference, no-server-key — standard cross-actor
      convention; this R0 namespace performs no LLM call and holds no key."
  (:require [clojure.string :as str]
            [clojure.edn :as edn]
            #?(:clj [clojure.java.io :as io])))

(def domain-classes
  #{":political" ":religious" ":cultural" ":historical" ":sports"
    ":civic-neighborhood" ":labor" ":other"})

(def edge-kinds #{":follows" ":depends-on" ":influences"})

;; ── parse: validate seed → refuse G1/G2/G4 violations ─────────────────────

(defn- person-node?
  "G1: a person/human node is unrepresentable. A community carrying a
  :person/* marker or a :sev/human flag is refused at parse."
  [c]
  (or (get c ":sev/human")
      (some (fn [[k _]] (str/starts-with? (str k) ":person")) c)))

(defn- under-sourced-depends-on?
  "G2: a :kumi/depends-on tie needs >=2 public-source citations."
  [e]
  (and (= (get e ":kumi/kind") ":depends-on")
       (< (count (get e ":sources" [])) 2)))

(defn- influences-missing-marker?
  "G4: a :kumi/influences tie MUST be explicitly labeled non-causal."
  [e]
  (and (= (get e ":kumi/kind") ":influences")
       (not (true? (get e ":co-occurrence-observed")))))

(defn parse
  "Validate a seed {:communities [...] :ties [...]}. Throws on G1/G2/G4
  violations; otherwise returns the seed unchanged (raw, pre-graph)."
  [{:keys [communities ties] :as seed}]
  (when-let [bad (first (filter person-node? communities))]
    (throw (ex-info "G1: person/human node is unrepresentable in kumi (community-only)"
                     {:community bad})))
  (when-let [bad (first (filter under-sourced-depends-on? ties))]
    (throw (ex-info "G2: :kumi/depends-on tie needs >=2 :sources (public-declaration-only sourcing)"
                     {:tie bad})))
  (when-let [bad (first (filter influences-missing-marker? ties))]
    (throw (ex-info "G4: :kumi/influences tie must carry :co-occurrence-observed true (non-causal)"
                     {:tie bad})))
  seed)

;; ── graph: build the community graph from a validated seed ────────────────

(defn graph
  "Build the community graph: {:communities {id m} :ties [...] :order [id...]}
  (deterministic sorted node order)."
  [seed]
  (let [{:keys [communities ties]} (parse seed)
        by-id (into {} (map (fn [c] [(get c ":id") c])) communities)
        order (vec (sort (keys by-id)))]
    {:communities by-id :ties (vec ties) :order order}))

;; ── loop-classify: junkan-style regime over dyad/triad cycles ─────────────

(defn- edge-of [ties a b]
  (first (filter (fn [e] (and (= (get e ":kumi/from") a) (= (get e ":kumi/to") b))) ties)))

(defn- dyad-loops
  "2-node cycles (a<->b). :virtuous if fully declared/sourced (no :influences
  edge); :neutral if either direction is a correlation-only :influences tie
  (lower confidence, per G4)."
  [order ties]
  (vec
   (for [a order b order
         :when (and (neg? (compare a b))
                    (edge-of ties a b) (edge-of ties b a))]
     (let [e1 (edge-of ties a b) e2 (edge-of ties b a)
           infl? (or (= (get e1 ":kumi/kind") ":influences")
                     (= (get e2 ":kumi/kind") ":influences"))]
       {:kind :dyad :nodes [a b] :regime (if infl? ":neutral" ":virtuous")}))))

(defn- triad-loops
  "3-node one-directional cycles (a->b->c->a). :vicious if NONE of the 3
  edges is reciprocated (a closed dependency chain with no diversification —
  structural fragility); :transitioning if PARTIALLY reciprocated; :neutral
  if any edge in the cycle is a correlation-only :influences tie (per G4)."
  [order ties]
  (vec
   (for [a order b order c order
         :when (and (apply distinct? [a b c])
                    (= a (first (sort [a b c])))
                    (edge-of ties a b) (edge-of ties b c) (edge-of ties c a))]
     (let [e1 (edge-of ties a b) e2 (edge-of ties b c) e3 (edge-of ties c a)
           infl? (some #(= (get % ":kumi/kind") ":influences") [e1 e2 e3])
           recip (count (filter true? [(boolean (edge-of ties b a))
                                        (boolean (edge-of ties c b))
                                        (boolean (edge-of ties a c))]))]
       {:kind :triad :nodes [a b c]
        :regime (cond infl? ":neutral"
                       (pos? recip) ":transitioning"
                       :else ":vicious")}))))

(defn loop-classify
  "junkan-style loop classification scoped to kumi's own community subgraph.
  Returns {:loops [...] :regimes {:virtuous n :vicious n :neutral n :transitioning n}}."
  [{:keys [order ties]}]
  (let [loops (into (dyad-loops order ties) (triad-loops order ties))
        regimes (reduce (fn [m l]
                          (update m (keyword (subs (:regime l) 1)) (fnil inc 0)))
                        {:virtuous 0 :vicious 0 :neutral 0 :transitioning 0}
                        loops)]
    {:loops loops :regimes regimes}))

;; ── leverage-read: kaname-compatible argmax, computed on read only (G6) ────

(def ^:private edge-weight {":follows" 1.0 ":depends-on" 2.0 ":influences" 0.5})

(defn- inbound-weight [ties id]
  (reduce (fn [s e] (if (= (get e ":kumi/to") id)
                       (+ s (get edge-weight (get e ":kumi/kind") 0.0))
                       s))
          0.0 ties))

(defn concentration
  "Per-community C_i = weighted inbound tie load (depends-on > follows >
  influences). Computed fresh from the graph every call — never stored."
  [{:keys [order ties]}]
  (into {} (for [id order] [id (inbound-weight ties id)])))

(defn- neighbor-domain-classes [{:keys [communities ties]} id]
  (let [neigh (set (concat (keep (fn [e] (when (= (get e ":kumi/to") id) (get e ":kumi/from"))) ties)
                            (keep (fn [e] (when (= (get e ":kumi/from") id) (get e ":kumi/to"))) ties)))]
    (set (keep (fn [n] (get-in communities [n ":domain-class"])) neigh))))

(defn versatility
  "Per-community V_i = # distinct neighbor domain-classes (the SoS
  discriminator — a one-domain hub is not the leverage point; a bridge across
  domains is)."
  [{:keys [order] :as g}]
  (into {} (for [id order] [id (count (neighbor-domain-classes g id))])))

(defn leverage-read
  "kaname-compatible leverage: L_i = C_i * (V_i / D), D = total distinct
  domain-classes present in the graph. Computed ON READ only — G6: no
  community record anywhere carries a stored power/leverage attribute; this
  function is the only place L is ever computed, and it returns a fresh map,
  never a mutation. Returns {:per {id L} :leverage-community argmax-id :D D}."
  [{:keys [order communities] :as g}]
  (let [conc (concentration g)
        vers (versatility g)
        D (max 1 (count (set (map (fn [c] (get c ":domain-class")) (vals communities)))))
        L (into {} (for [id order] [id (* (get conc id 0.0) (/ (double (get vers id 0)) D))]))
        top (->> order (sort-by (fn [id] [(- (get L id 0.0)) id])) first)]
    {:per L :leverage-community top :D D}))

;; ── beat: the analysis-only step (G5 — no actuator cell exists) ───────────

(defn beat
  "One analysis beat: parse → graph → loop-classify → leverage-read →
  append-only findings. Pure; there is no dispatch/post/execute path in this
  namespace (G5). `:findings :actuation-taken` is always false, mirroring
  junkan's findingRecord.actuationTaken const false."
  [seed]
  (let [g (graph seed)
        {:keys [loops regimes]} (loop-classify g)
        {:keys [per leverage-community D]} (leverage-read g)]
    {:community-count (count (:order g))
     :tie-count (count (:ties g))
     :loops loops
     :regimes regimes
     :leverage-per per
     :leverage-community leverage-community
     :domain-count D
     :findings {:status ":append-only" :actuation-taken false :route nil}}))

;; ── seed I/O (clj only) ─────────────────────────────────────────────────

#?(:clj
   (defn load-seed
     "Read the seed (or any kumi community-graph edn) → {:communities :ties}."
     [path]
     (-> (slurp path) (edn/read-string))))

#?(:clj
   (defn -main [& args]
     (let [path (or (first args)
                    (-> *file* io/file .getParentFile .getParentFile
                        (io/file "data" "seed-communities.kotoba.edn") str))
           r (beat (load-seed path))]
       (println "kumi 組 beat:")
       (println "  communities:" (:community-count r) " ties:" (:tie-count r))
       (println "  regimes:" (:regimes r))
       (println "  leverage-community (要):" (:leverage-community r))
       (println "  loops:" (count (:loops r)))
       (doseq [l (:loops r)]
         (println "   " (:kind l) (:nodes l) "->" (:regime l))))))
