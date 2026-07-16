(ns hinagata.methods.coverage-report
  "hinagata 雛形 — legal-template-commons COVERAGE report (ADR-2606111954). 1:1 Clojure port of
  `methods/coverage_report.py`.

  Honest coverage measurement of the template graph: how much of the target space the seed
  covers — by template-family / concept, by jurisdiction, by legal system, by clause role, by
  citation force — plus a STATUTE-BINDING check (which clauses are NOT yet anchored to any
  public statute) and a gap map naming what is thin/missing.

  NOT a completeness claim: coverage of *all* template families / *all* jurisdictions is ~0 by
  design (a bounded :representative seed). Pure fns; reuses hinagata.methods.analyze for the
  loader + CITE_KINDS. Portable .cljc."
  (:require [clojure.string :as str]
            [clojure.set]
            [hinagata.methods.analyze :as analyze]))

;; honest external denominators (orders of magnitude; the point is ~0 coverage by design)
(def concept-denom
  [["Common contract families (~)" 120]
   ["Distinct legal concepts in general practice (~)" 2000]])
(def jurisdiction-denom
  [["UN member states (~)" 193]
   ["World legal jurisdictions incl. sub-national (~)" 320]])

(def systems [":civil-law" ":common-law" ":mixed" ":religious" ":customary" ":international"])
(def clause-roles [":definitions" ":payment" ":term" ":termination" ":confidentiality"
                   ":ip-assignment" ":ip-license" ":warranty" ":liability" ":governing-law"
                   ":dispute" ":privacy" ":data-rights" ":force-majeure" ":no-interest"
                   ":cooling-off" ":signature" ":boilerplate"])
(def forces [":mandated" ":cited" ":referenced"])
(def THIN 2) ;; a bucket with < THIN members is flagged thin

(defn- lstrip-colon [s] (if (and (string? s) (str/starts-with? s ":")) (subs s 1) s))

(defn- lstrip-chars
  "Python str.lstrip(chars) — strip every leading char that is a member of the char-set."
  [s chars]
  (let [cs (set chars)]
    (subs s (count (take-while cs s)))))

(defn- counter
  "Counter(seq) → plain map value->count, mirroring collections.Counter (nil keys allowed)."
  [coll]
  (reduce (fn [m v] (update m v (fnil inc 0))) {} coll))

(defn- ordered-counter
  "Counter(seq) but carrying ::order = first-touch key order (so a stable sort-by -count ties
  exactly the Python Counter iteration order). Returns [count-map order-vec]."
  [coll]
  (reduce (fn [[m order] v]
            (if (contains? m v)
              [(update m v inc) order]
              [(assoc m v 1) (conj order v)]))
          [{} []]
          coll))

(defn- comma
  "Python f'{n:,}' — group integer digits with commas (no fraction here)."
  [n]
  (let [s (str (long n))
        neg (str/starts-with? s "-")
        digits (if neg (subs s 1) s)
        rev (reverse (vec digits))
        grouped (->> rev
                     (partition-all 3)
                     (map #(apply str (reverse %)))
                     reverse
                     (str/join ","))]
    (str (when neg "-") grouped)))

(defn- sci2
  "Python f'{x:.2e}' — scientific notation, 2 fraction digits, lowercase e, signed 2-digit exp.
  Java %.2e already matches (e.g. \"2.83e-01\") on the host JDK."
  [x]
  (format "%.2e" (double x)))

(defn report
  "Render the legal-template-commons coverage-report markdown (1:1 with coverage_report.report)."
  [nodes edges]
  (let [vals* (analyze/node-vals nodes)
        tmpls (filter #(= ":template" (get % ":lt/kind")) vals*)
        clauses (filter #(= ":clause" (get % ":lt/kind")) vals*)
        statutes (filter #(= ":statute" (get % ":lt/kind")) vals*)
        jurisdictions (filter #(= ":jurisdiction" (get % ":lt/kind")) vals*)
        concepts (filter #(= ":concept" (get % ":lt/kind")) vals*)

        sys-c (counter (map #(get % ":jurisdiction/system") jurisdictions))
        role-c (counter (map #(get % ":clause/role") clauses))
        force-c (counter (for [e edges :when (get e ":en/force")] (get e ":en/force")))

        ;; which clauses cite at least one statute (the binding backbone) vs orphans
        cited-clauses (set (for [e edges
                                 :when (and (contains? analyze/cite-kinds (get e ":en/kind"))
                                            (= ":clause" (get-in nodes [(get e ":en/from") ":lt/kind"])))]
                             (get e ":en/from")))
        clause-ids (set (map #(get % ":lt/id") clauses))
        unbound (sort (clojure.set/difference clause-ids cited-clauses))

        L (transient [])]
    (conj! L "# hinagata 雛形 — legal-template-commons coverage report\n")
    (conj! L (str "> Honest denominator: coverage of all template families / all jurisdictions is "
                  "~0 by design (bounded seed). This names the clause↔statute backbone covered and "
                  "the next-wave gaps. PUBLIC, openly-licensed reference templates only — never "
                  "advice (G1).\n"))
    (conj! L (str "**Seed**: " (count tmpls) " templates · " (count clauses) " clauses · "
                  (count statutes) " statutes · " (count jurisdictions) " jurisdictions · "
                  (count concepts) " concepts · " (count edges) " 縁\n"))

    (conj! L "\n## Concept / family coverage vs denominators\n")
    (conj! L "| denominator | count | seed concepts | fraction |")
    (conj! L "|---|---:|---:|---:|")
    (doseq [[name denom] concept-denom]
      (conj! L (str "| " name " | " (comma denom) " | " (count concepts) " | "
                    (sci2 (/ (double (count concepts)) denom)) " |")))

    (conj! L "\n## Jurisdiction coverage vs denominators\n")
    (conj! L "| denominator | count | seed jurisdictions | fraction |")
    (conj! L "|---|---:|---:|---:|")
    (doseq [[name denom] jurisdiction-denom]
      (conj! L (str "| " name " | " (comma denom) " | " (count jurisdictions) " | "
                    (sci2 (/ (double (count jurisdictions)) denom)) " |")))

    (conj! L "\n## Citation-force spread (DISCLOSED facts, not verdicts)\n")
    (conj! L "| force | edges |")
    (conj! L "|:--:|---:|")
    (doseq [f forces]
      (conj! L (str "| " (lstrip-colon f) " | " (get force-c f 0) " |")))

    (letfn [(bucket [title ks cnt]
              (conj! L (str "\n## " title "\n"))
              (conj! L "| bucket | count | status |")
              (conj! L "|---|---:|:--|")
              (doseq [k ks]
                (let [c (get cnt k 0)
                      status (cond (= c 0) "— **MISSING**"
                                   (< c THIN) "⚠ thin"
                                   :else "ok")]
                  (conj! L (str "| " (lstrip-colon k) " | " c " | " status " |")))))]
      (bucket "Legal-system coverage (law is plural)" systems sys-c)
      (bucket "Clause-role coverage" clause-roles role-c))

    ;; language coverage — a worldwide commons must offer templates in more than one language
    (let [[lang-c lang-order] (ordered-counter (map #(get % ":template/lang") tmpls))
          translated (set (for [e edges :when (= ":translates" (get e ":en/kind"))]
                            (get e ":en/from")))]
      (conj! L "\n## Language coverage (a worldwide commons is multilingual)\n")
      (conj! L "| language | templates | status |")
      (conj! L "|---|---:|:--|")
      (doseq [lang (sort-by (fn [k] (- (get lang-c k))) lang-order)]
        (let [c (get lang-c lang)
              status (if (>= c THIN) "ok" "⚠ thin")]
          (conj! L (str "| " (or lang "—") " | " c " | " status " |"))))
      (conj! L (str "\n_" (count lang-c) " languages · " (count translated) "/" (count tmpls)
                    " templates linked to another by a `:translates` 縁._")))

    ;; per-jurisdiction statute depth — which jurisdictions are well-grounded vs thin
    (letfn [(jx-name [n]
              (let [code (get n ":jurisdiction/code")]
                (if (and code (not= code "INTL"))
                  code
                  (str/replace (get n ":lt/id") #"^jx\." ""))))]
      (let [jx-label (into {} (map (fn [n] [(get n ":lt/id") (jx-name n)]) jurisdictions))
            [jx-stat jx-order] (ordered-counter (map #(get % ":statute/jurisdiction") statutes))]
        (conj! L "\n## Per-jurisdiction statute depth (breadth honesty)\n")
        (conj! L "| jurisdiction | statutes | status |")
        (conj! L "|---|---:|:--|")
        (doseq [jid (sort-by (fn [j] (- (get jx-stat j))) jx-order)]
          (let [c (get jx-stat jid)
                status (cond (>= c 3) "ok" (>= c 1) "⚠ thin" :else "— **MISSING**")]
            (conj! L (str "| " (get jx-label jid jid) " | " c " | " status " |"))))
        (let [ungrounded (for [j jurisdictions
                               :when (= 0 (get jx-stat (get j ":lt/id") 0))]
                           (get jx-label (get j ":lt/id") (get j ":lt/id")))]
          (when (seq ungrounded)
            (conj! L (str "\n_jurisdictions with no statute yet: "
                          (str/join ", " ungrounded) "._"))))))

    ;; clause conflicts + template lineage — relational depth (drafting aids)
    (let [conflicts (for [e edges :when (= ":conflicts-with" (get e ":en/kind"))]
                      [(get e ":en/from") (get e ":en/to")])
          derived (for [e edges :when (= ":derived-from" (get e ":en/kind"))]
                    [(get e ":en/from") (get e ":en/to")])]
      (when (or (seq conflicts) (seq derived))
        (conj! L "\n## Relational depth — clause conflicts & template lineage\n")
        (when (seq conflicts)
          (conj! L "_Mutually-exclusive clauses a drafter must not combine (`:conflicts-with`):_\n")
          (doseq [[a b] conflicts]
            (let [la (get-in nodes [a ":lt/label"] a)
                  lb (get-in nodes [b ":lt/label"] b)]
              (conj! L (str "- " la " ⟷ " lb)))))
        (when (seq derived)
          (conj! L "\n_Template provenance (`:derived-from`):_\n")
          (doseq [[a b] derived]
            (let [la (get-in nodes [a ":lt/label"] a)
                  lb (get-in nodes [b ":lt/label"] b)]
              (conj! L (str "- " la " ← " lb)))))
        (let [supersedes (for [e edges :when (= ":supersedes" (get e ":en/kind"))]
                           [(get e ":en/from") (get e ":en/to")])]
          (when (seq supersedes)
            (conj! L "\n_Version supersession (`:supersedes`):_\n")
            (doseq [[a b] supersedes]
              (let [la (get-in nodes [a ":lt/label"] a)
                    lb (get-in nodes [b ":lt/label"] b)]
                (conj! L (str "- " la " ⇒ supersedes ⇒ " lb))))))))

    (conj! L "\n## Statute-binding integrity — clauses NOT yet anchored to any public statute\n")
    (conj! L (str "_Every clause SHOULD eventually cite the law it rests on (gap #2 of the design). "
                  "Unbound clauses are the next-wave binding worklist, not a defect — they are "
                  "honestly surfaced (G5)._\n"))
    (if (seq unbound)
      (conj! L (str "**" (count unbound) "/" (count clauses) " clauses unbound**: "
                    (str/join ", " (map #(if (str/starts-with? % "cl.")
                                           (lstrip-chars % "cl.") %) unbound))
                    "."))
      (conj! L (str "All " (count clauses) " clauses cite at least one public statute.")))

    (let [missing (for [s systems :when (= 0 (get sys-c s 0))] (lstrip-colon s))]
      (conj! L "\n## Gap map — next-wave targets\n")
      (if (seq missing)
        (conj! L (str "Missing legal-system buckets: " (str/join ", " missing) "."))
        (conj! L "No fully-missing legal-system buckets (thin buckets still listed above).")))
    (conj! L "\n---\n_hinagata 雛形 · ADR-2606111954 · coverage honesty (G5)._\n")
    (str/join "\n" (persistent! L))))

#?(:clj
   (defn -main
     "CLI entry: render coverage-report.md from a seed EDN graph."
     [& argv]
     (let [argv (vec argv)
           here (-> *file* clojure.java.io/file .getParentFile .getParentFile)
           seed (if (and (seq argv) (not (str/starts-with? (first argv) "--")))
                  (clojure.java.io/file (first argv))
                  (clojure.java.io/file here "data" "seed-legal-template-graph.kotoba.edn"))
           outdir (if (some #{"--out"} argv)
                    (clojure.java.io/file (nth argv (inc (.indexOf argv "--out"))))
                    (clojure.java.io/file here "out"))
           {:keys [nodes edges]} (analyze/load-file* seed)]
       (.mkdirs outdir)
       (spit (clojure.java.io/file outdir "coverage-report.md") (report nodes edges))
       (println (str "hinagata coverage → " (clojure.java.io/file outdir "coverage-report.md")))
       0)))
