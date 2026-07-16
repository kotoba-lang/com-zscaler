(ns kizuna.methods.kizuna
  "kizuna 絆 — actor-to-actor social-interaction self-evolution + SoS optimization
  (ADR-2606232200). The INTERNAL-actor sibling of kaname 要 (which runs SoS leverage
  over EXTERNAL-entity mirrors): kizuna reads etzhayyim's own actors interacting over
  the ATProto social protocol (follow / mention / like / post via XRPC) as a multiplex
  social graph, computes system-of-systems metrics over that actor society, and feeds
  per-actor GROWTH signals + dry-run tie PROPOSALS back into the loop so the collective
  optimizes its own flow (系流最適化).

  The loop (one beat): perceive(social events) → graph → assess(SoS) → propose(dry-run)
  → learn(fold signals). Pure + deterministic (sorted node order, ties broken by id;
  no wall clock, no randomness). Portable .cljc (bb).

  Gates (in code + tests):
   G1 PROPOSE-not-act — kizuna emits `:tie/proposed` (`:status :dry-run`, `:route :ossekai`).
      There is NO execute/auto-follow path; actuation is ossekai + member CACAO leash
      (no-server-key, ADR-2606072802). kizuna never follows/likes/posts on its own.
   G2 RECIPROCITY-positive, ANTI-addiction — the growth objective is reciprocity (相互, the
      social form of 相互監視) + connectivity/resilience, NEVER engagement/retention/affinity
      maximization (Charter §1.13 / Rider §2(h)). No engagement field is representable.
   G3 AGENT-only — nodes are actors (agent-centric, ADR-2606232100); a `:person/*` node is
      unrepresentable (person-excluded).
   G4 no-server-key — kizuna READS own actors' public repos + PROPOSES; it holds no key."
  (:require [clojure.string :as str]
            [clojure.set :as set]
            [clojure.edn :as edn]
            #?(:clj [clojure.java.io :as io])))

(def social-kinds #{":follow" ":mention" ":like" ":post"})
;; interaction weights for the integration scalar (a standing follow weighs more than
;; a single ephemeral like; tuned, not learned — transparent constants).
(def kind-weight {":follow" 3.0 ":mention" 1.5 ":like" 1.0})

;; ── perceive: parse events → multiplex social graph ───────────────────────────

(defn- person-node?
  "G3: a person/human node is unrepresentable. An event carrying a :person/* marker
  or a :sev/human flag is refused at parse."
  [ev]
  (or (get ev ":sev/human")
      (some (fn [[k _]] (str/starts-with? (str k) ":person")) ev)))

(defn graph
  "Build the multiplex social graph from events. Returns:
    {:actors #{id…}
     :activity {id post-count}
     :edges {:follow {from {to w}} :mention {…} :like {…}}
     :reciprocal #{#{a b}…}   ; mutual-follow pairs (相互)
     :order [id…]}            ; deterministic sorted node order
  Throws on a person node (G3)."
  [events]
  (when-let [bad (first (filter person-node? events))]
    (throw (ex-info "G3: person node is unrepresentable in kizuna (agent-only)" {:event bad})))
  (let [step
        (fn [acc ev]
          (let [from (get ev ":sev/from") to (get ev ":sev/to") kind (get ev ":sev/kind")]
            (cond-> acc
              from (update :actors conj from)
              to   (update :actors conj to)
              (= kind ":post") (update-in [:activity from] (fnil inc 0))
              (and to (contains? #{":follow" ":mention" ":like"} kind) (not= from to))
              (update-in [:edges kind from to] (fnil + 0.0) 1.0))))
        base {:actors #{} :activity {} :edges {":follow" {} ":mention" {} ":like" {}}}
        {:keys [actors edges] :as g} (reduce step base events)
        follows (get edges ":follow")
        recip (set (for [[a tos] follows
                         [b _] tos
                         :when (get-in follows [b a])]
                     #{a b}))]
    (assoc g :actors actors :reciprocal recip
           :order (vec (sort actors)))))

;; ── assess: SoS metrics over the actor society ────────────────────────────────

(defn- inbound [edges kind id]
  (reduce-kv (fn [m from tos] (if-let [w (get tos id)] (assoc m from w) m))
             {} (get edges kind)))

(defn integration
  "Per-actor INTEGRATION = weighted inbound social ties (how woven into the society an
  actor is). The growth scalar — low ⇒ peripheral/isolated ⇒ candidate for an intro."
  [{:keys [order edges]}]
  (into {}
        (for [id order]
          [id (reduce (fn [s [kind w] ]
                        (+ s (* w (reduce + 0.0 (vals (inbound edges kind id))))))
                      0.0 kind-weight)])))

(defn reciprocity
  "Per-actor reciprocity ratio = mutual follows / outbound follows (1.0 = every follow
  is returned; the charter-favored 相互 direction). 0 outbound ⇒ 0.0 (no claim)."
  [{:keys [order edges reciprocal]}]
  (into {}
        (for [id order]
          (let [outs (keys (get-in edges [":follow" id]))
                muts (count (filter (fn [o] (contains? reciprocal #{id o})) outs))]
            [id (if (seq outs) (/ (double muts) (count outs)) 0.0)]))))

;; undirected projection of ALL ties → Brandes betweenness (bridge actors)
(defn- adjacency [{:keys [order edges]}]
  (let [ids (set order)
        add (fn [adj a b] (if (and (contains? ids a) (contains? ids b) (not= a b))
                            (-> adj (update a (fnil conj #{}) b) (update b (fnil conj #{}) a))
                            adj))]
    (reduce (fn [adj kind]
              (reduce-kv (fn [adj from tos]
                           (reduce (fn [adj to] (add adj from to)) adj (keys tos)))
                         adj (get edges kind)))
            (into {} (map (fn [id] [id #{}]) order))
            [":follow" ":mention" ":like"])))

(defn- brandes-source [adj order s]
  (loop [queue [s] dist {s 0} sigma {s 1.0} preds {} stack []]
    (if (empty? queue)
      (let [delta (reduce
                   (fn [d w]
                     (reduce (fn [d v]
                               (let [c (* (/ (sigma v) (sigma w)) (+ 1.0 (get d w 0.0)))]
                                 (update d v (fnil + 0.0) c)))
                             d (get preds w)))
                   {} (reverse stack))]
        (reduce (fn [m w] (if (= w s) m (update m w (fnil + 0.0) (get delta w 0.0))))
                {} stack))
      (let [v (first queue)
            nbrs (sort (get adj v))
            [dist sigma preds queue*]
            (reduce (fn [[dist sigma preds q] w]
                      (let [dist (if (contains? dist w) dist (assoc dist w (inc (dist v))))
                            q    (if (= (dist w) (inc (dist v))) (conj q w) q)
                            [sigma preds]
                            (if (= (dist w) (inc (dist v)))
                              [(update sigma w (fnil + 0.0) (sigma v))
                               (update preds w (fnil conj []) v)]
                              [sigma preds])]
                        [dist sigma preds q]))
                    [dist sigma preds []] nbrs)]
        (recur (vec (concat (subvec (vec queue) 1) queue*))
               dist sigma preds (conj stack v))))))

(defn betweenness
  "Exact Brandes betweenness over the undirected all-ties projection. High = bridge
  actor holding clusters of the society together (the kaname 律速 reading, internal)."
  [g]
  (let [{:keys [order]} g
        adj (adjacency g)]
    (reduce (fn [acc s]
              (merge-with + acc (brandes-source adj order s)))
            (into {} (map (fn [id] [id 0.0]) order))
            order)))

(defn- role [{:keys [integ recip betw isolated?]}]
  (cond isolated?            ":isolated"
        (> betw 0.0)         ":bridge"
        (>= integ 6.0)       ":hub"
        :else                ":peripheral"))

(defn assess
  "SoS readout over the actor society: per-actor {:integration :reciprocity :betweenness
  :role}, the leverage actor (律速 = argmax betweenness), and the isolated set."
  [g]
  (let [integ (integration g)
        recip (reciprocity g)
        betw  (betweenness g)
        isolated (set (filter (fn [id] (<= (get integ id 0.0) 0.0)) (:order g)))
        per (into {}
                  (for [id (:order g)]
                    [id {:integration (get integ id 0.0)
                         :reciprocity (get recip id 0.0)
                         :betweenness (get betw id 0.0)
                         :role (role {:integ (get integ id) :recip (get recip id)
                                      :betw (get betw id) :isolated? (contains? isolated id)})}]))
        lever (->> (:order g)
                   (sort-by (fn [id] [(- (get betw id 0.0)) id]))
                   first)]
    {:per per :leverage-actor lever :isolated (vec (sort isolated))}))

;; ── propose: dry-run, reciprocity/connectivity-improving tie proposals ────────

(defn tie-proposals
  "For each low-integration / isolated actor, propose up to k follows toward the most
  CONNECTING actors (highest betweenness first) it does not already follow — improving
  the society's connectivity + opening a reciprocity opportunity. G1: every proposal is
  `:status :dry-run :route :ossekai` (kizuna never follows); G2: the objective is
  :connectivity+:reciprocity, NEVER engagement (no such field exists)."
  [g assessment & {:keys [k] :or {k 2}}]
  (let [{:keys [per leverage-actor]} assessment
        betw (into {} (map (fn [[id m]] [id (:betweenness m)]) per))
        follows (get-in g [:edges ":follow"])
        targets (->> (:order g)
                     (sort-by (fn [id] [(- (get betw id 0.0)) id])))
        needy (->> (:order g)
                   (filter (fn [id] (#{":isolated" ":peripheral"} (get-in per [id :role]))))
                   sort)]
    (vec
     (for [a needy
           b (->> targets
                  (remove (fn [b] (or (= b a) (get-in follows [a b]))))
                  (take k))]
       {":tie/from" a ":tie/to" b ":tie/kind" ":follow"
        ":tie/rationale" (if (= b leverage-actor) ":connect-to-leverage" ":improve-connectivity")
        ":tie/objective" ":connectivity+reciprocity"   ; G2 — never :engagement
        ":status" ":dry-run" ":route" ":ossekai"}))))  ; G1 — proposed, never executed

;; ── beat: the self-evolution loop step ────────────────────────────────────────

(defn beat
  "One self-evolution beat: perceive events → graph → assess SoS → propose dry-run ties
  → growth signal per actor. Pure; the readout is what a heartbeat persists (kotoba) and
  what each actor folds into its own optimization (系流最適化)."
  [events]
  (let [g (graph events)
        a (assess g)
        proposals (tie-proposals g a)]
    {:actors (count (:actors g))
     :reciprocal-pairs (count (:reciprocal g))
     :leverage-actor (:leverage-actor a)
     :isolated (:isolated a)
     :assessment (:per a)
     :proposals proposals
     ;; growth signal = the per-actor optimization target the society feeds back.
     :growth (into {} (for [[id m] (:per a)]
                        [id {:role (:role m)
                             :integration (:integration m)
                             :reciprocity (:reciprocity m)
                             :grow? (boolean (#{":isolated" ":peripheral"} (:role m)))}]))}))

;; ── seed I/O (clj only) ───────────────────────────────────────────────────────

;; seed-interactions.kotoba.edn is now stored datomized (tx-data: a single-entity
;; vector [{:db/id -1 :seed-interactions/events "<pr-str'd events vector>"}], per
;; the repo-wide edn-datomize convention). `unblob`/`reconstitute-entity` restore
;; the original `{:events [...]}` shape so this loader (and every downstream
;; caller: -main + the test suite) keeps working unchanged. Also tolerates the
;; pre-datomize raw-map shape for any other seed edn passed in.
#?(:clj
   (defn- unblob [v]
     (if (string? v)
       (try (let [parsed (edn/read-string v)] (if (coll? parsed) parsed v))
            (catch Exception _ v))
       v)))

#?(:clj
   (defn- tx-data? [content]
     (and (vector? content) (seq content) (map? (first content))
          (contains? (first content) :db/id))))

#?(:clj
   (defn- reconstitute-entity [tx-data]
     (into {} (map (fn [[k v]] [(keyword (name k)) (unblob v)]))
           (dissoc (first tx-data) :db/id))))

#?(:clj
   (defn load-events
     "Read the seed (or any kizuna interaction edn) → vector of events. Accepts
     both the datomized tx-data shape and the legacy raw-map shape."
     [path]
     (let [parsed (-> (slurp path) (edn/read-string))
           m (if (tx-data? parsed) (reconstitute-entity parsed) parsed)]
       (-> m :events vec))))

#?(:clj
   (defn -main [& args]
     (let [path (or (first args)
                    (-> *file* io/file .getParentFile .getParentFile
                        (io/file "data" "seed-interactions.kotoba.edn") str))
           r (beat (load-events path))]
       (println "kizuna 絆 beat:")
       (println "  actors:" (:actors r) " reciprocal-pairs:" (:reciprocal-pairs r))
       (println "  leverage-actor (律速):" (:leverage-actor r))
       (println "  isolated:" (:isolated r))
       (println "  proposals (dry-run → ossekai):" (count (:proposals r)))
       (doseq [p (:proposals r)]
         (println "   " (get p ":tie/from") "→" (get p ":tie/to")
                  (get p ":tie/rationale"))))))
