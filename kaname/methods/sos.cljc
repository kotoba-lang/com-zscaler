(ns kaname.methods.sos
  "kaname 要 — cross-domain system-of-systems leverage (律速) synthesizer (ADR-2606172100).

  The META synthesis layer above the power-mirror lineage (tsumugi / keizu / kabuto / chie /
  shiori / abaki / shionome / busshi / hokorobi / kosatsu / inochi). Reads a kotoba-EDN
  MULTILAYER graph — :organism/* nodes (entities / interfaces / instruments / public roles)
  and :en/* 縁 each tagged with a :en/domain layer (politics / religion / organization /
  ideology / economy / ecology / security / wellbecoming / ai / information / energy) — and computes, ON
  READ (edge-primary; no stored per-entity score), where the cross-domain BOTTLENECK is: the
  one structural position (the 要 / 律速段階) whose release would most improve resilience across
  the MAXIMUM number of domains at once.

  THE MATH (computed on read; N1/G4):
    C_i  cross-domain CONCENTRATION = Σ inbound 取-load to i across all domains.
    V_i  domain VERSATILITY         = # distinct domains i bears load in. THE SoS discriminator —
                                      a one-domain hoarder is NOT the 要; the 要 spans many.
    B_i  BRIDGE-LOAD                = Σ incident inter-layer connective load (:couples / :gates) —
                                      a bounded, deterministic proxy for inter-layer betweenness
                                      (full multiplex betweenness is R1; labelled honestly).
    L_i  LEVERAGE (律速) score      = C_i · (V_i / D) · (1 + B_i) · (1 − open_i).   要 = argmax L_i.
                                      Interpretation: L_i ≈ ΔΦ, the drop in aggregate cross-domain
                                      fragility if i's concentration is OPENED / routed-around. An
                                      already-OPEN (redundant / decentralized) position scores 0.

  CONSTITUTIONAL (read before any change):
    G1 — leverage MAP, never a target-list. The 要 is a STRUCTURAL POSITION, aggregate; natural
         persons are person-excluded (public ROLEs only). Enforced in route/osekkai + tests.
    G2 — routed to OPENING / route-around ONLY; capture/seize/control are unrepresentable (route.cljc).
    G4 — non-adjudicating + edge-primary. kaname reads DISCLOSED per-domain concentration; the
         leverage is the integral of incident 縁, computed on read; there is no score-of-entity.
    G5 — no thought-policing. :influences (ideology/religion) edges carry an on-the-record :en/basis
         and feed STRUCTURAL concentration only — never a belief-content verdict.

  House style mirrors the mirror-lineage cljc ports (chie/busshi/ugachi): self-contained minimal
  EDN reader, ':…' keywords kept as strings, pure fns, deterministic ordering by (-value, id),
  file I/O only at edges. Portable .cljc (clj-native — no Python twin)."
  (:require [clojure.string :as str]
            #?(:clj [clojure.java.io :as io])))

;; ── minimal EDN reader (subset: vectors [], maps {}, :keyword, "string", num, bool, nil) ──
;; Keywords are kept as ":ns/name" strings so the whole pipeline stays string-keyed.

(def ^:private tok-re
  #"[\s,]+|;[^\n]*|(\[|\]|\{|\}|\"(?:\\.|[^\"\\])*\"|[^\s,\[\]{}]+)")

(defn tokens
  "Lazy seq of significant tokens (group 1 of each tok-re match that captured)."
  [s]
  (let [m (re-matcher tok-re s)]
    ((fn step []
       (lazy-seq
        (when (.find m)
          (let [t (.group m 1)]
            (if (nil? t) (step) (cons t (step))))))))))

(defn atom-of
  "\"…\" → unescaped string; true/false/nil → bool/nil; \":…\" kept as string; int → long;
  else float; else raw string."
  [t]
  (cond
    (str/starts-with? t "\"")
    (-> (subs t 1 (dec (count t)))
        (str/replace "\\\"" "\"")
        (str/replace "\\\\" "\\"))
    (= t "true") true
    (= t "false") false
    (= t "nil") nil
    (str/starts-with? t ":") t
    :else
    (let [as-long (try (Long/parseLong t) (catch #?(:clj Exception :cljs :default) _ ::nan))]
      (if (not= as-long ::nan)
        as-long
        (let [as-dbl (try (Double/parseDouble t) (catch #?(:clj Exception :cljs :default) _ ::nan))]
          (if (not= as-dbl ::nan) as-dbl t))))))

(def ^:private end-marker ::end)

(defn- parse-step
  [toks i]
  (let [t (nth toks i)
        i (inc i)]
    (cond
      (= t "[")
      (loop [i i, out []]
        (let [[x i] (parse-step toks i)]
          (if (= x end-marker) [out i] (recur i (conj out x)))))
      (= t "{")
      (loop [i i, out {}]
        (let [[k i] (parse-step toks i)]
          (if (= k end-marker)
            [out i]
            (let [[v i] (parse-step toks i)] (recur i (assoc out k v))))))
      (or (= t "]") (= t "}")) [end-marker i]
      :else [(atom-of t) i])))

(defn read-edn
  "Parse the first top-level form from EDN text."
  [text]
  (let [toks (vec (tokens text))]
    (first (parse-step toks 0))))

;; ── domain model ──────────────────────────────────────────────────────────────

(def domains
  "The layers of the multiplex (kept as ':…' strings, matching the reader).
  :energy added in ADR-2606212000 — the system-of-systems ENERGY-FLOW domain, fed by amime
  網目's multi-site mesh concentration (ADR-2606212020). Energy was previously implicit inside
  :organization/:economy; making it an explicit layer lets a node that bears load in BOTH an
  energy chokepoint AND another domain surface as a higher-versatility cross-domain 要."
  [":politics" ":religion" ":organization" ":ideology" ":economy"
   ":ecology" ":security" ":wellbecoming" ":ai" ":information" ":energy"])

(def D (count domains))

;; 取 fed INTO :en/to (concentration onto the node); bridge = inter-layer connectivity.
(def concentration-kinds #{":concentrates" ":gates" ":influences"})
(def bridge-kinds        #{":couples" ":gates"})
(def fragility-kinds     #{":depends-on" ":gates"})

(defn load-graph
  "Return {:nodes nodes-by-id :node-order [ids…] :edges [edge…]} from parsed EDN forms.
  node-order = first-touch id order (deterministic emit/report ordering)."
  [forms]
  (reduce
   (fn [{:keys [nodes] :as a} f]
     (cond
       (not (map? f)) a
       (contains? f ":organism/id")
       (let [id (get f ":organism/id")]
         (-> a
             (assoc-in [:nodes id] f)
             (update :node-order (fn [v] (if (contains? nodes id) v (conj v id))))))
       (and (contains? f ":en/from") (contains? f ":en/to"))
       (update a :edges conj f)
       :else a))
   {:nodes {} :node-order [] :edges []}
   forms))

#?(:clj
   (defn load-file*
     "Read + parse a multilayer EDN graph file → {:nodes :node-order :edges}."
     [path]
     (load-graph (read-edn (slurp (str path))))))

(defn- ->load [e]
  (let [v (get e ":en/grasping-load")]
    (if (or (nil? v) (false? v)) 0.0 (double v))))

(defn- open? [node]
  (if (true? (get node ":sos/open?")) 1.0 0.0))

(defn leverage
  "Edge-primary multilayer integrals (computed on read; N1/G4). Returns
   {:concentration {node {domain load}}  ; inbound 取 per domain layer
    :C  {node load}                       ; Σ domains — cross-domain concentration
    :domains {node #{domain…}}            ; layers the node bears load in
    :V  {node int}                        ; |domains| — versatility (SoS discriminator)
    :bridge {node load}                   ; Σ incident inter-layer connective load (betweenness proxy)
    :reach  {node load}                   ; Σ outbound 縁-load (accountability cross-link)
    :fragility {node load}                ; Σ dependency/gate load on both endpoints (cascade)
    :leverage {node load}}                ; L = C·(V/D)·(1+B)·(1−open) — argmax = the 要."
  [nodes edges]
  (let [base
        (reduce
         (fn [acc e]
           (let [kind (get e ":en/kind")
                 load- (->load e)
                 src (get e ":en/from")
                 dst (get e ":en/to")
                 dom (get e ":en/domain")
                 acc (cond-> acc
                       ;; concentration accrues onto :en/to
                       (contains? concentration-kinds kind)
                       (update-in [:concentration dst dom] (fnil + 0.0) load-)
                       ;; bridge / fragility touch both endpoints
                       (contains? bridge-kinds kind)
                       (-> (update-in [:bridge src] (fnil + 0.0) load-)
                           (update-in [:bridge dst] (fnil + 0.0) load-))
                       (contains? fragility-kinds kind)
                       (-> (update-in [:fragility src] (fnil + 0.0) load-)
                           (update-in [:fragility dst] (fnil + 0.0) load-))
                       ;; reach = total outbound 縁-load
                       true (update-in [:reach src] (fnil + 0.0) load-))
                 ;; domain membership: both endpoints bear load in this layer
                 acc (if dom
                       (-> acc
                           (update-in [:domains src] (fnil conj #{}) dom)
                           (update-in [:domains dst] (fnil conj #{}) dom))
                       acc)]
             acc))
         {:concentration {} :bridge {} :fragility {} :reach {} :domains {}}
         edges)
        conc (:concentration base)
        C (into {} (map (fn [[nid m]] [nid (reduce + 0.0 (vals m))]) conc))
        V (into {} (map (fn [[nid s]] [nid (count s)]) (:domains base)))
        lev (into {}
                  (map (fn [[nid c]]
                         (let [v (/ (double (get V nid 0)) (double D))
                               b (double (get-in base [:bridge nid] 0.0))
                               o (open? (get nodes nid {}))]
                           [nid (* c v (+ 1.0 b) (- 1.0 o))]))
                       C))]
    (assoc base :C C :V V :leverage lev)))

(defn rank
  "Top-`limit` [id label value] of a node→value map, sorted by (-value, id) — deterministic
  (tie-break by id). Zero/negative values are dropped."
  ([d nodes] (rank d nodes 20))
  ([d nodes limit]
   (->> d
        (filter (fn [[_ v]] (> (double v) 0.0)))
        (sort-by (fn [[nid v]] [(- (double v)) nid]))
        (take limit)
        (mapv (fn [[nid v]] [nid (get-in nodes [nid ":organism/label"] nid) (double v)])))))

(defn kaname-point
  "The 要 — the single argmax-leverage node, as [id label leverage], or nil if none."
  [res nodes]
  (first (rank (:leverage res) nodes 1)))

;; ── report rendering ─────────────────────────────────────────────────────────

(defn- fmt3 [v] (format "%.3f" (double v)))
(defn- lstrip-colon [s] (if (and (string? s) (str/starts-with? s ":")) (subs s 1) s))
(defn- doms-str [s]
  (->> (sort s) (map lstrip-colon) (str/join "·")))

(defn report-md
  "Render the cross-domain leverage (要 / 律速) report markdown."
  [nodes edges res]
  (let [n-if   (count (filter #(= ":sos/interface"  (get % ":organism/kind")) (vals nodes)))
        n-ent  (count (filter #(= ":sos/entity"     (get % ":organism/kind")) (vals nodes)))
        n-inst (count (filter #(= ":sos/instrument" (get % ":organism/kind")) (vals nodes)))
        n-role (count (filter #(= ":sos/role"       (get % ":organism/kind")) (vals nodes)))
        auth   (count (filter #(= ":authoritative" (get % ":organism/sourcing")) (vals nodes)))
        kp     (kaname-point res nodes)
        L (transient [])]
    (conj! L "# kaname 要 — cross-domain system-of-systems leverage (律速) report\n")
    (conj! L (str "> **G1 — leverage MAP, NEVER a target-list.** The 要 is a STRUCTURAL POSITION "
                  "(a decision-point / interface / standard / gatekeeping institution), reported "
                  "aggregate; natural persons are person-excluded (public ROLEs only). **G2 — routed "
                  "to OPENING / route-around / redundancy ONLY**, never to capture or seize. **G4** "
                  "non-adjudicating + edge-primary (leverage = integral of incident 縁, on read; no "
                  "stored score). **G5** ideology/religion appear as STRUCTURAL interfaces with an "
                  "on-the-record basis — never a belief-content verdict. kaname PROPOSES; ossekai "
                  "CARRIES (consent-bound, transparent).\n"))
    (conj! L (str "**Graph**: " (count nodes) " nodes (" n-if " interfaces · " n-ent " entities · "
                  n-inst " instruments · " n-role " roles) · " (count edges) " 縁 across "
                  D " domain layers · " auth "/" (count nodes) " :authoritative\n"))
    (when kp
      (conj! L (str "\n## 要 — the leverage point\n"))
      (conj! L (str "**" (nth kp 1) "** — leverage **" (fmt3 (nth kp 2)) "**. Releasing this single "
                    "cross-domain position is the highest-ΔΦ move available (route → OPENING; "
                    "おせっかい handoff → ossekai).\n")))

    (conj! L "\n## Leverage ranking — L = C · (V/D) · (1 + bridge) · (1 − open)\n")
    (conj! L "_The 要 = argmax L. Concentration ALONE is not leverage — versatility (domains spanned) is the SoS discriminator._\n")
    (conj! L "| rank | structural position | kind | domains (V) | concentration C | bridge | leverage L |")
    (conj! L "|---:|---|---|---|---:|---:|---:|")
    (doseq [[i [nid _ lv]] (map-indexed vector (rank (:leverage res) nodes))]
      (let [label (get-in nodes [nid ":organism/label"] nid)
            kind  (lstrip-colon (get-in nodes [nid ":organism/kind"] "—"))
            dset  (get-in res [:domains nid] #{})
            c     (get-in res [:C nid] 0.0)
            b     (get-in res [:bridge nid] 0.0)]
        (conj! L (str "| " (inc i) " | " label " | " kind " | " (doms-str dset) " (" (count dset)
                      ") | " (fmt3 c) " | " (fmt3 b) " | " (fmt3 lv) " |"))))

    (conj! L "\n## Versatility discriminator — why concentration ALONE misleads\n")
    (conj! L "_A node may hoard heavily in ONE domain yet not be the SoS 要; the 要 spans many. Top concentration vs. top leverage:_\n")
    (conj! L "| structural position | concentration C | versatility V | leverage L |")
    (conj! L "|---|---:|---:|---:|")
    (doseq [[nid _ c] (rank (:C res) nodes 6)]
      (let [label (get-in nodes [nid ":organism/label"] nid)
            v (get-in res [:V nid] 0)
            lv (get-in res [:leverage nid] 0.0)]
        (conj! L (str "| " label " | " (fmt3 c) " | " v " | " (fmt3 lv) " |"))))

    (conj! L "\n## Reach — outbound 縁-load (accountability cross-link → danjo / keizu / tsumugi)\n")
    (conj! L "| rank | node | outbound-load |")
    (conj! L "|---:|---|---:|")
    (doseq [[i [_ label v]] (map-indexed vector (rank (:reach res) nodes 8))]
      (conj! L (str "| " (inc i) " | " label " | " (fmt3 v) " |")))

    (conj! L "\n## Fragility — lock-in cascade (loss propagates across layers)\n")
    (conj! L "| rank | node | fragility |")
    (conj! L "|---:|---|---:|")
    (doseq [[i [_ label v]] (map-indexed vector (rank (:fragility res) nodes 8))]
      (conj! L (str "| " (inc i) " | " label " | " (fmt3 v) " |")))

    (conj! L (str "\n---\n_kaname 要 · ADR-2606172100 · system-of-systems · map-not-target · "
                  "opening-only · non-adjudicating · edge-primary · no-thought-policing · "
                  "no-server-key. Bridge proxy is bounded (full multiplex betweenness = R1). "
                  "Live join over the real mirrors (chie/tsumugi/keizu/kabuto/shiori/abaki/…) is "
                  "G7/Council-gated. CLD view → junkan · intervention → ossekai._\n"))
    (str/join "\n" (persistent! L))))

#?(:clj
   (defn -main
     "CLI entry: analyze a seed multilayer EDN graph → out/leverage-report.md."
     [& argv]
     (let [argv (vec argv)
           here (-> *file* io/file .getParentFile .getParentFile)
           seed (if (and (seq argv) (not (str/starts-with? (first argv) "--")))
                  (io/file (first argv))
                  (io/file here "data" "seed-sos.kotoba.edn"))
           outdir (if (some #{"--out"} argv)
                    (io/file (nth argv (inc (.indexOf argv "--out"))))
                    (io/file here "out"))
           {:keys [nodes edges]} (load-file* seed)
           res (leverage nodes edges)]
       (.mkdirs outdir)
       (spit (io/file outdir "leverage-report.md") (report-md nodes edges res))
       (println (str "kaname: " (count nodes) " nodes, " (count edges) " 縁 across " D
                     " domains → " (io/file outdir "leverage-report.md")))
       (when-let [kp (kaname-point res nodes)]
         (println (str "  要 (leverage point): " (nth kp 1) " (L=" (fmt3 (nth kp 2)) ")")))
       0)))
