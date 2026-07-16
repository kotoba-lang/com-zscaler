(ns hinagata.methods.publish
  "hinagata 雛形 — publish the template commons (content-addressed, anyone may fetch + reuse).
  1:1 Clojure port of `methods/publish.py` (ADR-2606111954).

  Renders every template in the graph to its canonical body text, content-addresses each to a
  kotoba IPFS CIDv1 (raw/sha2-256) — the SAME CID `ipfs add --cid-version=1 --raw-leaves`
  produces — and snapshots bodies + a publish manifest into a git-tracked public data layer.

  // no-server-key: read-only — publishing writes public content-addressed artifacts; it holds
  // no signing key. IPFS pinning / IPNS publish is the operator add-on (G7), out of scope here.

  G1 — publishing fair PUBLIC templates is safe by construction; the commons offers documents,
  never advice. G4 — public venue, open license, content-addressed.

  House style: data maps stay string-keyed; ':…' strings stay strings; pure fns; file I/O only
  behind #?(:clj …). Requires the good cid.cljc + esign.cljc siblings. The Python `__main__`
  body-writer + JSON serialization is behind #?(:clj …)."
  (:require [clojure.string :as str]
            [hinagata.methods.cid :as cid]
            [hinagata.methods.esign :as esign]))

(def ^:private cite-kinds #{":cites-statute" ":mandated-by"})

(defn- lstrip-colon [v]
  (let [s (str v)] (if (str/starts-with? s ":") (subs s 1) s)))

(defn- group-edges
  "edges → {from-id [to-id …]} for a given :en/kind set (or predicate), in edge order."
  [edges pred]
  (reduce (fn [m e]
            (if (pred (get e ":en/kind"))
              (update m (get e ":en/from") (fnil conj []) (get e ":en/to"))
              m))
          {} edges))

(defn build-entries
  "Render every template, content-address each body, and build its publish entry. Returns
  {:entries [entry …] :bodies {template-id body-string …}} — the pure core of publish().
  Mirrors the Python entry dict shape (string-keyed)."
  [nodes edges]
  (let [cites (group-edges edges cite-kinds)
        has-clause (group-edges edges #(= ":has-clause" %))
        governed (group-edges edges #(= ":governed-by" %))
        templates (filter #(= ":template" (get-in nodes [% ":lt/kind"]))
                          (keys nodes))]
    (reduce
     (fn [{:keys [entries bodies]} tid]
       (let [body (esign/render-document tid nodes edges)
             raw (cid/bytes-of body)
             cid-str (cid/cidv1-raw raw)
             statutes (vec (for [cl (get has-clause tid [])
                                 st (get cites cl [])
                                 :let [s (get nodes st {})]]
                             {"id" st
                              "citation" (get s ":statute/citation")
                              "instrument" (get s ":statute/instrument")
                              "url" (get s ":statute/url")}))
             t (get nodes tid)
             entry {"id" tid
                    "title" (get t ":template/title")
                    "lang" (get t ":template/lang")
                    "license" (str (or (get t ":template/license") "Apache-2.0")
                                   " + etzhayyim Charter Rider")
                    "version" (get t ":template/version")
                    "stance" (lstrip-colon (get t ":template/stance" ""))
                    "jurisdictions" (mapv #(get-in nodes [% ":jurisdiction/code"])
                                          (get governed tid []))
                    "bodyCid" cid-str
                    "bodySha256" (cid/sha256-hex raw)
                    "bytes" (count raw)
                    "clauseCount" (count (get has-clause tid []))
                    "statutes" statutes}]
         {:entries (conj entries entry) :bodies (assoc bodies tid body)}))
     {:entries [] :bodies {}}
     templates)))

(defn manifest
  "Build the publish-manifest map (templates sorted by id), mirroring publish.py's manifest."
  [entries]
  {"actor" "hinagata"
   "adr" "2606111954"
   "schema" "legal-template-ontology@0.1.0"
   "license" "Apache-2.0 + etzhayyim Charter Rider v3.0"
   "note" (str "Public legal-document template commons. Every body is content-addressed "
               "(CIDv1 raw/sha2-256) and free to copy + adapt. NOT legal advice (G1).")
   "templateCount" (count entries)
   "templates" (vec (sort-by #(get % "id") entries))})

(defn publish-md
  "Human index markdown — 1:1 with publish.py's PUBLISH.md."
  [entries]
  (let [L (transient [])
        P (fn [s] (conj! L s))]
    (P "# hinagata 雛形 — published legal-template commons\n")
    (P (str "> Fair, openly-licensed legal-document templates (Apache-2.0 + etzhayyim Charter "
            "Rider). Each body is content-addressed — re-derive the CID with `ipfs add "
            "--cid-version=1 --raw-leaves` or `methods/cid.py` to verify. **NOT legal advice** "
            "(G1); each clause cites the public law it rests on, for traceability.\n"))
    (P (str "\n**" (count entries) " templates published.**\n"))
    (P "\n| template | lang | jurisdiction | clauses | statutes | bodyCid |")
    (P "|---|:--:|:--:|---:|---:|---|")
    (doseq [e (sort-by #(get % "id") entries)]
      (let [jx (let [j (str/join "," (filter some? (get e "jurisdictions")))]
                 (if (empty? j) "—" j))]
        (P (str "| " (get e "title") " | " (get e "lang") " | " jx " | " (get e "clauseCount") " | "
                (count (get e "statutes")) " | `" (subs (get e "bodyCid") 0 18) "…` |"))))
    (P (str "\n---\n_hinagata 雛形 · ADR-2606111954 · commons-not-counsel · G7 outward publish "
            "(IPFS pin / IPNS) is the operator add-on._\n"))
    (str/join "\n" (persistent! L))))

#?(:clj
   (defn publish
     "publish.py's publish(): write bodies + publish-manifest.json + PUBLISH.md, return manifest.
     File I/O only at this #?(:clj) edge."
     [nodes edges outdir]
     (let [bodies-dir (clojure.java.io/file outdir "bodies")]
       (.mkdirs bodies-dir)
       (let [{:keys [entries bodies]} (build-entries nodes edges)]
         (doseq [[tid body] bodies]
           (spit (clojure.java.io/file bodies-dir (str tid ".md")) body))
         (let [m (manifest entries)]
           (require 'cheshire.core)
           (spit (clojure.java.io/file outdir "publish-manifest.json")
                 (str ((requiring-resolve 'cheshire.core/generate-string) m {:pretty true}) "\n"))
           (spit (clojure.java.io/file outdir "PUBLISH.md") (publish-md entries))
           m)))))

#?(:clj
   (defn -main
     [& argv]
     (let [argv (vec argv)
           load-file* (requiring-resolve 'hinagata.methods.analyze/load-file*)
           here (-> *file* clojure.java.io/file .getParentFile .getParentFile)
           seed (if (and (seq argv) (not (str/starts-with? (first argv) "--")))
                  (clojure.java.io/file (first argv))
                  (clojure.java.io/file here "data" "seed-legal-template-graph.kotoba.edn"))
           outdir (if (some #{"--out"} argv)
                    (clojure.java.io/file (nth argv (inc (.indexOf argv "--out"))))
                    (clojure.java.io/file (.getParentFile (.getParentFile here)) "80-data" "legal-templates"))
           {:keys [nodes edges]} (load-file* seed)
           m (publish nodes edges outdir)]
       (println (str "hinagata publish → " outdir " (" (get m "templateCount") " templates content-addressed)"))
       0)))
