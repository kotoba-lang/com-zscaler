(ns hinagata.methods.validate
  "hinagata 雛形 — legal-template-commons integrity validator (ADR-2606111954).
  1:1 Clojure port of `methods/validate.py`.

  Maturity tooling: checks the kotoba-EDN graph's internal integrity beyond what the analyzer
  needs to run — referential integrity, statute grounding, template completeness, clause usage,
  and translation consistency. ERRORS are structural defects that must be fixed; WARNINGS are
  honestly-surfaced soft issues (e.g. a registered-but-unused statute) that are allowed but
  worth seeing (G5 sourcing honesty).

  CONSTITUTIONAL: G1 — a COMMONS, never the practice of law; G3/N3 — citations/instrument names
  are DISCLOSED facts, never verdicts. Validation checks STRUCTURE, never the law's merit.

  Node iteration follows EDN first-touch order (analyze/node-id-order) so the error/warning
  message lists are byte-identical to the Python dict iteration order. Portable .cljc."
  (:require [clojure.string :as str]
            [clojure.set]
            [hinagata.methods.analyze :as analyze]))

(def sign-clause "cl.signature-esign")

(defn- node-id-order
  "The :lt/id keys of `nodes` in EDN first-touch order (::node-order meta), else (keys nodes).
  Mirrors the dict-iteration order Python's `for nid, n in nodes.items()` uses."
  [nodes]
  (if-let [order (:hinagata.methods.analyze/node-order (meta nodes))]
    order
    (vec (keys nodes))))

(defn validate
  "Return [errors warnings] — each a vector of strings, in the exact order validate.py appends."
  [nodes edges]
  (let [order (node-id-order nodes)
        ;; by_kind: kind -> set of ids (membership only; order irrelevant for the set checks)
        by-kind (reduce (fn [m nid]
                          (update m (get-in nodes [nid ":lt/kind"]) (fnil conj #{}) nid))
                        {} order)
        templates (get by-kind ":template" #{})
        clauses (get by-kind ":clause" #{})
        statutes (get by-kind ":statute" #{})
        jurisdictions (get by-kind ":jurisdiction" #{})
        concepts (get by-kind ":concept" #{})
        cite-kinds #{":cites-statute" ":mandated-by"}
        E (transient [])
        W (transient [])
        e! (fn [m] (conj! E m))
        w! (fn [m] (conj! W m))]

    ;; 1. referential integrity — no dangling 縁
    (doseq [e edges]
      (when-not (contains? nodes (get e ":en/from"))
        (e! (str "dangling :en/from " (get e ":en/from") " (" (get e ":en/kind") ")")))
      (when-not (contains? nodes (get e ":en/to"))
        (e! (str "dangling :en/to " (get e ":en/to") " (" (get e ":en/kind") ")"))))

    ;; 2. statute → jurisdiction referential integrity (statutes in EDN order)
    (doseq [sid (filter statutes order)]
      (let [jx (get-in nodes [sid ":statute/jurisdiction"])]
        (when (and jx (not (contains? jurisdictions jx)))
          (e! (str "statute " sid " :statute/jurisdiction " jx " is not a jurisdiction node")))
        (when-not (clojure.string/starts-with? (str (get-in nodes [sid ":statute/url"] "")) "http")
          (e! (str "statute " sid " has no public :statute/url")))))

    ;; 3. edge-target kind sanity
    (doseq [e edges]
      (let [k (get e ":en/kind")
            to-kind (get-in nodes [(get e ":en/to") ":lt/kind"])
            from-kind (get-in nodes [(get e ":en/from") ":lt/kind"])]
        (when (and (contains? cite-kinds k) (not= to-kind ":statute"))
          (e! (str k " target " (get e ":en/to") " is " to-kind ", expected :statute")))
        (when (and (= k ":instantiates") (not= to-kind ":concept"))
          (e! (str ":instantiates target " (get e ":en/to") " is " to-kind ", expected :concept")))
        (when (and (contains? #{":governed-by" ":applies-in"} k) (not= to-kind ":jurisdiction"))
          (e! (str k " target " (get e ":en/to") " is " to-kind ", expected :jurisdiction")))
        (when (and (= k ":translates") (not= to-kind ":template"))
          (e! (str ":translates target " (get e ":en/to") " is " to-kind ", expected :template")))
        (when (and (= k ":conflicts-with") (not (and (= from-kind ":clause") (= to-kind ":clause"))))
          (e! (str ":conflicts-with must be clause↔clause, got " from-kind "→" to-kind
                   " (" (get e ":en/from") ")")))
        (when (and (= k ":derived-from") (not (and (= from-kind ":template") (= to-kind ":template"))))
          (e! (str ":derived-from must be template→template, got " from-kind "→" to-kind
                   " (" (get e ":en/from") ")")))
        (when (and (= k ":supersedes") (not (and (= from-kind ":template") (= to-kind ":template"))))
          (e! (str ":supersedes must be template→template, got " from-kind "→" to-kind
                   " (" (get e ":en/from") ")")))
        (when (and (= k ":conflicts-with") (= (get e ":en/from") (get e ":en/to")))
          (e! (str ":conflicts-with self-loop on " (get e ":en/from"))))))

    ;; 4. template completeness — clauses, a governing jurisdiction, a signature clause
    (let [has-clause (reduce (fn [m e]
                               (if (= ":has-clause" (get e ":en/kind"))
                                 (update m (get e ":en/from") (fnil conj #{}) (get e ":en/to"))
                                 m))
                             {} edges)
          governed (set (for [e edges :when (= ":governed-by" (get e ":en/kind"))] (get e ":en/from")))]
      (doseq [tid (filter templates order)]
        (let [cls (get has-clause tid #{})]
          (when (empty? cls)
            (e! (str "template " tid " has no clauses")))
          (when-not (contains? governed tid)
            (e! (str "template " tid " has no :governed-by jurisdiction")))
          (when-not (contains? cls sign-clause)
            (w! (str "template " tid " has no signature clause (" sign-clause ")")))))

      ;; 5. clause usage — every clause used by ≥1 template and instantiating ≥1 concept
      (let [used-clauses (if (seq has-clause)
                           (apply clojure.set/union (vals has-clause))
                           #{})
            instantiated (set (for [e edges :when (= ":instantiates" (get e ":en/kind"))]
                                (get e ":en/from")))]
        (doseq [cid (filter clauses order)]
          (when-not (contains? used-clauses cid)
            (w! (str "clause " cid " is not used by any template")))
          (when (and (not (contains? instantiated cid)) (not= cid "cl.definitions"))
            (w! (str "clause " cid " does not :instantiate any concept")))))

      ;; 6. statute grounding — every statute cited by ≥1 clause/template (else registry-only)
      (let [cited (set (for [e edges :when (contains? cite-kinds (get e ":en/kind"))] (get e ":en/to")))]
        (doseq [sid (filter statutes order)]
          (when-not (contains? cited sid)
            (w! (str "statute " sid " is registered but not cited by any clause (registry-only)")))))

      ;; 7. translation consistency — a translation's clause-concepts ⊆ its original's
      (letfn [(concepts-of [tid]
                (reduce (fn [cs cl]
                          (reduce (fn [cs2 e]
                                    (if (and (= ":instantiates" (get e ":en/kind"))
                                             (= cl (get e ":en/from")))
                                      (conj cs2 (get e ":en/to"))
                                      cs2))
                                  cs edges))
                        #{} (get has-clause tid #{})))]
        (doseq [e edges :when (= ":translates" (get e ":en/kind"))]
          (let [tr (get e ":en/from")
                orig (get e ":en/to")
                extra (clojure.set/difference (concepts-of tr) (concepts-of orig))]
            (when (seq extra)
              (w! (str "translation " tr " introduces concepts not in original " orig ": "
                       (vec (sort extra)))))))))

    ;; 8. concept usage — every concept instantiated by ≥1 clause
    (let [used-concepts (set (for [e edges :when (= ":instantiates" (get e ":en/kind"))]
                               (get e ":en/to")))]
      (doseq [cid (filter concepts order)]
        (when-not (contains? used-concepts cid)
          (w! (str "concept " cid " is not instantiated by any clause")))))

    [(persistent! E) (persistent! W)]))

#?(:clj
   (defn -main
     "CLI entry: validate a seed EDN graph → report; exit 1 on any ERROR. File I/O at the edge."
     [& argv]
     (let [argv (vec argv)
           here (-> *file* clojure.java.io/file .getParentFile .getParentFile)
           seed (if (and (seq argv) (not (clojure.string/starts-with? (first argv) "--")))
                  (clojure.java.io/file (first argv))
                  (clojure.java.io/file here "data" "seed-legal-template-graph.kotoba.edn"))
           {:keys [nodes edges]} (analyze/load-file* seed)
           [errors warnings] (validate nodes edges)]
       (println (str "hinagata validate: " (count nodes) " nodes, " (count edges) " 縁 — "
                     (count errors) " errors, " (count warnings) " warnings"))
       (doseq [m errors] (println (str "  ERROR  " m)))
       (doseq [m warnings] (println (str "  warn   " m)))
       (if (seq errors) 1 0))))
