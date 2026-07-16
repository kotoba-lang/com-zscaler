(ns hinagata.methods.query
  "hinagata 雛形 — knowledge-graph query interface over the legal-template commons (ADR-2606111954).
  1:1 Clojure port of `methods/query.py`.

  Maturity / usability: the kotoba Datom EDN is a knowledge graph, not just a flat list — this
  module exposes the practical drafter queries that prove it (the point of the kotoba Datalog
  substrate). All queries are pure graph traversals over the loaded {:nodes :edges}; nothing is
  stored, nothing mutates (N1).

    templates-in-jurisdiction (jx)       — templates governed by a jurisdiction
    statutes-grounding-template (tmpl)   — every public statute a template rests on (clause→statute)
    translations-of (tmpl)               — its other-language versions (:translates, both directions)
    conflicting-clauses (clause)         — clauses a drafter must not combine with it (:conflicts-with)
    jurisdictions-for-concept (concept)  — where a legal concept is grounded in real law
    coverage-gaps (concept)              — major jurisdictions still lacking grounding (worklist)

  CONSTITUTIONAL: G1 — a COMMONS, never the practice of law. A statute citation is a DISCLOSED
  structural FACT (this clause cites this article), never a hinagata verdict (N3).

  House style: Python ':…' keyword strings stay strings; pure fns; requires the ported
  hinagata.methods.analyze for the loader + cite-kinds. Portable .cljc."
  (:require [hinagata.methods.analyze :as analyze]))

(defn label
  "nodes.get(nid, {}).get(':lt/label', nid) — the node's label, defaulting to its id."
  [nodes nid]
  (get-in nodes [nid ":lt/label"] nid))

(defn templates-in-jurisdiction
  "Templates :governed-by `jx` (de-duplicated, sorted) — only :template-kind sources."
  [nodes edges jx]
  (sort (set (for [e edges
                   :when (and (= ":governed-by" (get e ":en/kind"))
                              (= jx (get e ":en/to"))
                              (= ":template" (get-in nodes [(get e ":en/from") ":lt/kind"])))]
               (get e ":en/from")))))

(defn statutes-grounding-template
  "Every public statute a template rests on: clauses it :has-clause → those clauses' citations."
  [nodes edges tmpl]
  (let [clauses (set (for [e edges
                           :when (and (= ":has-clause" (get e ":en/kind"))
                                      (= tmpl (get e ":en/from")))]
                       (get e ":en/to")))
        statutes (reduce (fn [s e]
                           (if (and (contains? analyze/cite-kinds (get e ":en/kind"))
                                    (contains? clauses (get e ":en/from")))
                             (conj s (get e ":en/to"))
                             s))
                         #{} edges)]
    (sort statutes)))

(defn translations-of
  "A template's other-language versions via :translates (both directions) + sibling translations
  of the same original (mirrors query.py translations_of)."
  [_nodes edges tmpl]
  (let [out (reduce (fn [s e]
                      (if (= ":translates" (get e ":en/kind"))
                        (cond
                          (= tmpl (get e ":en/from")) (conj s (get e ":en/to"))
                          (= tmpl (get e ":en/to")) (conj s (get e ":en/from"))
                          :else s)
                        s))
                    #{} edges)
        originals (set (for [e edges
                             :when (and (= ":translates" (get e ":en/kind"))
                                        (= tmpl (get e ":en/from")))]
                         (get e ":en/to")))
        out (reduce (fn [s orig]
                      (reduce (fn [s2 e]
                                (if (and (= ":translates" (get e ":en/kind"))
                                         (= orig (get e ":en/to"))
                                         (not= tmpl (get e ":en/from")))
                                  (conj s2 (get e ":en/from"))
                                  s2))
                              s edges))
                    out originals)]
    (sort out)))

(defn conflicting-clauses
  "Clauses a drafter must not combine with `clause` via :conflicts-with (symmetric lookup)."
  [_nodes edges clause]
  (sort (reduce (fn [s e]
                  (if (= ":conflicts-with" (get e ":en/kind"))
                    (cond
                      (= clause (get e ":en/from")) (conj s (get e ":en/to"))
                      (= clause (get e ":en/to")) (conj s (get e ":en/from"))
                      :else s)
                    s))
                #{} edges)))

(defn jurisdictions-for-concept
  "Where a legal concept is grounded in real law: clauses that :instantiate the concept →
  statutes they cite → those statutes' :statute/jurisdiction."
  [nodes edges concept]
  (let [clauses (set (for [e edges
                           :when (and (= ":instantiates" (get e ":en/kind"))
                                      (= concept (get e ":en/to")))]
                       (get e ":en/from")))
        jx (reduce (fn [s e]
                     (if (and (contains? analyze/cite-kinds (get e ":en/kind"))
                              (contains? clauses (get e ":en/from")))
                       (let [j (get-in nodes [(get e ":en/to") ":statute/jurisdiction"])]
                         (if j (conj s j) s))
                       s))
                   #{} edges)]
    (sort jx)))

;; major national jurisdictions used as the gap-analysis denominator (exclude treaty/doctrinal ids)
(def major-jurisdictions
  ["jx.jp" "jx.us" "jx.eu" "jx.uk" "jx.de" "jx.fr" "jx.in" "jx.cn"
   "jx.kr" "jx.br" "jx.au" "jx.ca" "jx.es" "jx.sg" "jx.mx" "jx.id"
   "jx.ng" "jx.ae" "jx.it" "jx.ch" "jx.za" "jx.israel"])

(defn coverage-gaps
  "Major national jurisdictions that do NOT yet ground a concept — a self-documenting worklist.
  The inverse of jurisdictions-for-concept over major-jurisdictions (treaty / religious /
  customary ids are not counted as gaps)."
  [nodes edges concept]
  (let [have (set (jurisdictions-for-concept nodes edges concept))
        present (filter #(contains? nodes %) major-jurisdictions)]
    (sort (filter #(not (contains? have %)) present))))

(defn statute-reach
  "Per STATUTE, the count of distinct clauses + templates that CITE it (:cites-statute / :mandated-by)
  — the statute's reach across the commons, operationalizing the ontology's derived `:bond/statute-pull`.
  `statutes-grounding-template` is the forward query (a template's statutes); this is the INVERSE:
  which statute the commons most DEPENDS on, so a change to a widely-cited statute (a GDPR amendment, a
  民法 revision) shows exactly how many published clauses/templates must be re-checked. A DISCLOSED
  structural fact (citation counts), never an enforceability verdict (G1 commons-not-law / G3); on read
  (G2). Returns [statute citer-count label] by count descending."
  [nodes edges]
  (->> edges
       (filter #(#{":cites-statute" ":mandated-by"} (get % ":en/kind")))
       (reduce (fn [m e] (update m (get e ":en/to") (fnil conj #{}) (get e ":en/from"))) {})
       (map (fn [[statute citers]] [statute (count citers) (label nodes statute)]))
       (sort-by (fn [[s cnt _]] [(- cnt) (str s)]))
       vec))

;; command table (verb wording 1:1 with query.py _COMMANDS)
(def commands
  {"templates-in" ["templates-in-jurisdiction" "templates governed by"]
   "statutes-for" ["statutes-grounding-template" "statutes grounding"]
   "translations" ["translations-of" "translations of"]
   "conflicts" ["conflicting-clauses" "clauses conflicting with"]
   "jurisdictions-for" ["jurisdictions-for-concept" "jurisdictions grounding"]
   "gaps" ["coverage-gaps" "major jurisdictions still lacking grounding for"]})

(def ^:private fn-table
  {"templates-in-jurisdiction" templates-in-jurisdiction
   "statutes-grounding-template" statutes-grounding-template
   "translations-of" translations-of
   "conflicting-clauses" conflicting-clauses
   "jurisdictions-for-concept" jurisdictions-for-concept
   "coverage-gaps" coverage-gaps})

#?(:clj
   (defn -main
     "CLI entry: query.cljc <templates-in|statutes-for|translations|conflicts|jurisdictions-for|gaps> <id>."
     [& argv]
     (let [argv (vec argv)
           here (-> *file* clojure.java.io/file .getParentFile .getParentFile)
           {:keys [nodes edges]} (analyze/load-file*
                                  (clojure.java.io/file here "data" "seed-legal-template-graph.kotoba.edn"))]
       (if (or (< (count argv) 2) (not (contains? commands (first argv))))
         (do (binding [*out* *err*]
               (println (str "usage: query.cljc <" (clojure.string/join "|" (keys commands)) "> <id>")))
             2)
         (let [[fn-name verb] (get commands (first argv))
               arg (second argv)
               res ((get fn-table fn-name) nodes edges arg)]
           (println (str verb " " arg " (" (label nodes arg) "):"))
           (doseq [nid res]
             (println (str "  " nid "  —  " (label nodes nid))))
           (println (str "  [" (count res) " result(s)]"))
           0)))))
