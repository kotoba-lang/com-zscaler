(ns hinagata.methods.analyze
  "hinagata 雛形 — edge-primary statutory-groundedness analyzer over the legal-template commons.
  1:1 Clojure port of `methods/analyze.py` (ADR-2606111954).

  Reads a kotoba-EDN legal-template graph (:lt/* nodes + :en/* 縁 over the
  legal-template-ontology), and surfaces — aggregate-first — where integrated STATUTORY
  GROUNDING accumulates over a TEMPLATE (how well-anchored in actual public law it is), routed
  to PUBLIC RELEASE (free use); which CLAUSES are the most-reused backbone; and which public
  STATUTES the commons most rests on.

  CONSTITUTIONAL (read before any change):
    N1 / G2 — edge-primary. statutory grounding lives ONLY on edges (:en/binding-load weighted
      by the DISCLOSED clause :clause/optionality). A template's groundedness is the INTEGRAL of
      its incident clause :cites-statute / :mandated-by + direct :cites-statute 縁 — computed on
      READ, never a stored per-template score. There is no :lt/score-of-template.
    G1 — COMMONS, never the practice of law. No advice, no opinion on a matter, no representation,
      no enforceability certification. The unit is ALWAYS a template / clause / statute-link.
      A statute citation is a DISCLOSED STRUCTURAL FACT, never a hinagata verdict.
    N3 — non-adjudicating. citations, instrument names, optionality categories are DISCLOSED
      facts sourced from the public instrument or its official guidance, never hinagata verdicts.

  House style: Python ':…' keyword strings stay strings (incl. all :lt/* / :en/* attrs);
  pure fns; file I/O only at edges via clojure.java.io. Portable .cljc."
  (:require [clojure.string :as str]))

;; ── minimal EDN reader (subset: vectors [], maps {}, :keyword, "string", num, bool, nil)
;; Mirrors analyze.py's _TOK / _tokens / _atom / _parse faithfully. Keywords are kept as
;; ":ns/name" strings (NOT clojure keywords) so the whole pipeline stays string-keyed,
;; byte-for-byte the same as the Python port.

(def ^:private tok-re
  ;; _TOK = re.compile(r'[\s,]+|;[^\n]*|(\[|\]|\{|\}|"(?:\\.|[^"\\])*"|[^\s,\[\]{}]+)')
  #"[\s,]+|;[^\n]*|(\[|\]|\{|\}|\"(?:\\.|[^\"\\])*\"|[^\s,\[\]{}]+)")

(defn tokens
  "Lazy seq of significant tokens (group 1 of each tok-re match that captured)."
  [s]
  (let [m (re-matcher tok-re s)]
    ((fn step []
       (lazy-seq
        (when (.find m)
          (let [t (.group m 1)]
            (if (nil? t)
              (step)
              (cons t (step))))))))))

(defn atom-of
  "Port of _atom: \"…\" → unescaped string; true/false/nil → bool/nil; \":…\" kept as string;
  int → long; else float; else raw string."
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
  "Consume one form from the token vector at index i. Returns [value next-i] or
  [end-marker next-i] when a closing ] or } is hit (matching _parse's _END sentinel)."
  [toks i]
  (let [t (nth toks i)
        i (inc i)]
    (cond
      (= t "[")
      (loop [i i, out []]
        (let [[x i] (parse-step toks i)]
          (if (= x end-marker)
            [out i]
            (recur i (conj out x)))))

      (= t "{")
      (loop [i i, out {}]
        (let [[k i] (parse-step toks i)]
          (if (= k end-marker)
            [out i]
            (let [[v i] (parse-step toks i)]
              (recur i (assoc out k v))))))

      (or (= t "]") (= t "}"))
      [end-marker i]

      :else
      [(atom-of t) i])))

(defn read-edn
  "Parse the first top-level form from EDN text (matches read_edn → _parse(_tokens(text)))."
  [text]
  (let [toks (vec (tokens text))]
    (first (parse-step toks 0))))

;; ── disclosed clause optionality → grounding weight (NOT a verdict; mirrors schema)
(def optionality-weight
  {":mandatory" 1.0 ":recommended" 0.6 ":optional" 0.3})

(def cite-kinds #{":cites-statute" ":mandated-by"})   ;; clause/template → statute (statutory anchor)
(def has-clause-kinds #{":has-clause"})                ;; template → clause (composition / provenance)
(def instantiate-kinds #{":instantiates"})            ;; clause → concept
(def govern-kinds #{":governed-by" ":applies-in"})    ;; template/statute → jurisdiction
(def translate-kinds #{":translates" ":supersedes" ":derived-from" ":conflicts-with"})

(defn load-graph
  "Return {:nodes nodes-by-id :edges edges} from a parsed list of EDN forms.
  (`load` is a clojure.core fn — named load-graph; the host edge reads the file.)
  The :nodes map carries ::node-order metadata = the first-touch :lt/id order from the parsed
  forms (a plain hash-map loses insertion order beyond 8 keys), so `node-vals` can iterate
  nodes in EDN read order = the Python dict iteration order (byte-parity for Counter ties)."
  [forms]
  (let [{:keys [nodes edges]}
        (reduce
         (fn [{:keys [nodes edges] :as acc} f]
           (cond
             (not (map? f)) acc
             (contains? f ":lt/id") (assoc-in acc [:nodes (get f ":lt/id")] f)
             (and (contains? f ":en/from") (contains? f ":en/to"))
             (update acc :edges conj f)
             :else acc))
         {:nodes {} :edges []}
         forms)
        order (->> forms
                   (filter map?)
                   (filter #(contains? % ":lt/id"))
                   (mapv #(get % ":lt/id")))]
    {:nodes (with-meta nodes {::node-order order}) :edges edges}))

(defn node-vals
  "(vals nodes) in EDN first-touch order (::node-order metadata), so Counter ties over node
  iteration tie exactly the Python dict order. Falls back to (vals nodes)."
  [nodes]
  (if-let [order (::node-order (meta nodes))]
    (mapv #(get nodes %) order)
    (vals nodes)))

#?(:clj
   (defn load-file*
     "Read + parse a legal-template EDN graph file → {:nodes :edges}. File I/O only at this edge."
     [path]
     (load-graph (read-edn (slurp (str path))))))

(defn- ->load
  "float(e.get(':en/binding-load', 0.0) or 0.0) — coerce to double, 0.0 on nil/false/missing."
  [e]
  (let [v (get e ":en/binding-load")]
    (if (or (nil? v) (false? v)) 0.0 (double v))))

(defn analyze
  "Edge-primary integrals (computed on read; transient — N1/G2).

   groundedness[template] = Σ over (clause c attached via :has-clause) of Σ (c's outbound
                            :cites-statute/:mandated-by binding-load × optionality-weight(c)),
                            PLUS Σ direct template→statute :cites-statute. How well-anchored in
                            actual public law — the publish-readiness surface.
   reusability[clause]    = Σ inbound :has-clause binding-load (the shared backbone).
   statute_pull[statute]  = Σ inbound :cites-statute/:mandated-by binding-load (load-bearing law).
   juris_reach[node]      = Σ incident :governed-by/:applies-in (jurisdictional span).

   Returns string-keyed maps mirroring the Python dict exactly. Ranking is fully determined by
   (-value, id) so no insertion-order metadata is needed."
  [nodes edges]
  (let [;; clause → its parent templates (from :has-clause), in edge order (Python list append)
        clause-templates
        (reduce
         (fn [m e]
           (if (contains? has-clause-kinds (get e ":en/kind"))
             (update m (get e ":en/to") (fnil conj []) (get e ":en/from"))
             m))
         {} edges)
        opt-weight (fn [clause-id]
                     (get optionality-weight
                          (get-in nodes [clause-id ":clause/optionality"]) 0.6))]
    (reduce
     (fn [acc e]
       (let [kind (get e ":en/kind")
             load- (->load e)
             src (get e ":en/from")
             dst (get e ":en/to")]
         (cond
           (contains? cite-kinds kind)
           (let [acc (update-in acc ["statute_pull" dst] (fnil + 0.0) load-)
                 src-kind (get-in nodes [src ":lt/kind"])]
             (cond
               (= src-kind ":clause")
               (let [w (opt-weight src)]
                 (reduce (fn [a tmpl]
                           (update-in a ["grounded" tmpl] (fnil + 0.0) (* load- w)))
                         acc (get clause-templates src [])))
               (= src-kind ":template")
               (update-in acc ["grounded" src] (fnil + 0.0) load-)
               :else acc))

           (contains? has-clause-kinds kind)
           (update-in acc ["reuse" dst] (fnil + 0.0) load-)

           (contains? govern-kinds kind)
           (update-in acc ["juris_reach" src] (fnil + 0.0) load-)

           (contains? instantiate-kinds kind)
           (update-in acc ["concept_reach" dst] (fnil + 0.0) load-)

           :else acc)))
     {"grounded" {} "reuse" {} "statute_pull" {} "juris_reach" {} "concept_reach" {}}
     edges)))

;; ── report rendering (matches report_md's f-strings) ────────────────────────

(defn- rank
  "Top-`limit` (id, label, value) rows of d, sorted by (-value, id) — mirrors Python's
  `sorted(d.items(), key=lambda kv: (-kv[1], kv[0]))`."
  ([d nodes] (rank d nodes 20))
  ([d nodes limit]
   (->> (sort-by (fn [[nid v]] [(- (double v)) nid]) d)
        (take limit)
        (mapv (fn [[nid v]]
                [nid (get-in nodes [nid ":lt/label"] nid) v])))))

(defn- fmt3 [v] (format "%.3f" (double v)))

(defn- lstrip-colon [s] (if (and (string? s) (str/starts-with? s ":")) (subs s 1) s))

(defn- dash
  "Python `x or '—'` — '—' on nil/false; else x."
  [v]
  (if (or (nil? v) (false? v)) "—" v))

(defn- count-kind [nodes k]
  (count (filter #(= k (get % ":lt/kind")) (vals nodes))))

(defn report-md
  "Render the legal-template-commons groundedness report markdown (1:1 with report_md)."
  [nodes edges res]
  (let [n-tmpl (count-kind nodes ":template")
        n-clause (count-kind nodes ":clause")
        n-statute (count-kind nodes ":statute")
        n-jx (count-kind nodes ":jurisdiction")
        n-concept (count-kind nodes ":concept")
        auth (count (filter #(= ":authoritative" (get % ":lt/sourcing")) (vals nodes)))
        L (transient [])]
    (conj! L "# hinagata 雛形 — legal-template-commons groundedness report (aggregate-first)\n")
    (conj! L (str "> **G1 — a COMMONS of fair, openly-licensed templates ANYONE may use, NEVER the "
                  "practice of law.** No advice, no opinion on a matter, no enforceability "
                  "certification. A clause's statute citation is a DISCLOSED structural fact (N3), "
                  "not a hinagata verdict. Statutory grounding lives only on edges, integrated on "
                  "read (N1). Templates are Apache-2.0 + Charter Rider; anyone may copy + adapt.\n"))
    (conj! L (str "**Graph**: " (count nodes) " nodes (" n-tmpl " templates · " n-clause " clauses · "
                  n-statute " statutes · " n-jx " jurisdictions · " n-concept " concepts) · "
                  (count edges) " 縁 · " auth "/" (count nodes) " :authoritative\n"))

    (conj! L "\n## Template statutory-groundedness — templates best anchored in actual public law\n")
    (conj! L (str "_Σ incident clause :cites-statute/:mandated-by + direct citation load × disclosed "
                  "optionality weight; routed to PUBLIC RELEASE (free use), never to advice._\n"))
    (conj! L "| rank | template | lang | license | stance | groundedness |")
    (conj! L "|---:|---|:--:|:--:|:--:|---:|")
    (doseq [[i [nid label v]] (map-indexed vector (rank (get res "grounded") nodes))]
      (let [n (get nodes nid {})
            lang (dash (get n ":template/lang"))
            lic (dash (get n ":template/license"))
            stance (lstrip-colon (str (dash (get n ":template/stance"))))]
        (conj! L (str "| " (inc i) " | " label " | " lang " | " lic " | " stance " | " (fmt3 v) " |"))))

    (conj! L "\n## Clause reusability — the shared backbone across the commons\n")
    (conj! L "_Σ inbound :has-clause load; how many templates reuse the clause._\n")
    (conj! L "| rank | clause | role | optionality | reusability |")
    (conj! L "|---:|---|:--:|:--:|---:|")
    (doseq [[i [nid label v]] (map-indexed vector (rank (get res "reuse") nodes 12))]
      (let [n (get nodes nid {})
            role (lstrip-colon (str (dash (get n ":clause/role"))))
            opt (lstrip-colon (str (dash (get n ":clause/optionality"))))]
        (conj! L (str "| " (inc i) " | " label " | " role " | " opt " | " (fmt3 v) " |"))))

    (conj! L "\n## Statute pull — the public laws the commons most rests on\n")
    (conj! L "_Σ inbound :cites-statute/:mandated-by load. DISCLOSED citations only (N3)._\n")
    (conj! L "| rank | statute | citation | instrument | pull |")
    (conj! L "|---:|---|---|---|---:|")
    (doseq [[i [nid label v]] (map-indexed vector (rank (get res "statute_pull") nodes 12))]
      (let [n (get nodes nid {})
            cite (dash (get n ":statute/citation"))
            inst (dash (get n ":statute/instrument"))]
        (conj! L (str "| " (inc i) " | " label " | " cite " | " inst " | " (fmt3 v) " |"))))

    (conj! L "\n## Jurisdictional reach — templates spanning the most jurisdictions\n")
    (conj! L "| rank | template | jurisdiction-reach |")
    (conj! L "|---:|---|---:|")
    (let [tmpl-reach (into {} (filter (fn [[k _]] (= ":template" (get-in nodes [k ":lt/kind"])))
                                      (get res "juris_reach")))]
      (doseq [[i [_nid label v]] (map-indexed vector (rank tmpl-reach nodes 8))]
        (conj! L (str "| " (inc i) " | " label " | " (fmt3 v) " |"))))

    (conj! L (str "\n---\n_hinagata 雛形 · ADR-2606111954 · commons-not-counsel · non-adjudicating · "
                  "edge-primary · public-release-routed. Live legal-corpus binding "
                  "(ADR-2605262800) + Council review is G7-gated._\n"))
    (str/join "\n" (persistent! L))))

#?(:clj
   (defn -main
     "CLI entry: analyze a seed EDN graph → out/groundedness-report.md (file I/O at the edge)."
     [& argv]
     (let [argv (vec argv)
           here (-> *file* clojure.java.io/file .getParentFile .getParentFile)
           seed (if (and (seq argv) (not (str/starts-with? (first argv) "--")))
                  (clojure.java.io/file (first argv))
                  (clojure.java.io/file here "data" "seed-legal-template-graph.kotoba.edn"))
           outdir (if (some #{"--out"} argv)
                    (clojure.java.io/file (nth argv (inc (.indexOf argv "--out"))))
                    (clojure.java.io/file here "out"))
           {:keys [nodes edges]} (load-file* seed)
           res (analyze nodes edges)]
       (.mkdirs outdir)
       (spit (clojure.java.io/file outdir "groundedness-report.md") (report-md nodes edges res))
       (println (str "hinagata: " (count nodes) " nodes, " (count edges) " 縁 → "
                     (clojure.java.io/file outdir "groundedness-report.md")))
       (when-let [top (first (rank (get res "grounded") nodes 1))]
         (println (str "  top groundedness template: " (nth top 1)
                       " (" (fmt3 (nth top 2)) ")")))
       0)))
