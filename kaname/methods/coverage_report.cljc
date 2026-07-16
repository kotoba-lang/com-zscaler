(ns kaname.methods.coverage-report
  "kaname 要 — honest coverage + gap map (G6, ADR-2606172100).

  As a SYNTHESIS layer, kaname's coverage is two-dimensional: which DOMAINS are populated, and
  which upstream MIRRORS (chie/tsumugi/keizu/kabuto/shiori/abaki/…) it has joined. The R0 seed is
  a BOUNDED, SYNTHETIC illustrative graph — it joins NO live mirror. This report measures that so
  no reader mistakes the seed for a real cross-everything world-model (no fabricated coverage):

    - node counts per :organism/kind
    - domain layers populated (edges per domain) vs. the full domain set → missing layers
    - source-mirrors referenced (:sos/source-actors) vs. the full mirror set → unjoined mirrors
    - :authoritative vs :representative split (sourcing honesty)
    - a worklist routed to the next (G7-gated) live join.

  Pure fns; reuses kaname.methods.sos. Portable .cljc."
  (:require [clojure.string :as str]
            [kaname.methods.sos :as sos]
            #?(:clj [clojure.java.io :as io])))

(def expected-kinds
  [":sos/entity" ":sos/interface" ":sos/instrument" ":sos/role"])

;; the mirror lineage kaname is meant to JOIN (from schema :bridge)
;; :amime added in ADR-2606212000 — the energy-flow mesh mirror feeding the :energy domain layer.
(def expected-mirrors
  [":chie" ":tsumugi" ":keizu" ":kabuto" ":shiori" ":abaki" ":shionome"
   ":busshi" ":hokorobi" ":kosatsu" ":inochi" ":amime" ":junkan" ":ossekai"])

(defn- tally [coll] (reduce (fn [m k] (update m k (fnil inc 0))) {} coll))

(defn coverage
  "Compute the coverage summary map from {:nodes :edges}."
  [nodes edges]
  (let [nvals (vals nodes)
        by-kind   (tally (map #(get % ":organism/kind") nvals))
        by-domain (tally (keep #(get % ":en/domain") edges))
        mirrors   (set (mapcat #(get % ":sos/source-actors") nvals))
        auth (count (filter #(= ":authoritative" (get % ":organism/sourcing")) nvals))
        repr (count (filter #(= ":representative" (get % ":organism/sourcing")) nvals))
        missing-kinds   (remove (set (keys by-kind)) expected-kinds)
        missing-domains (remove (set (keys by-domain)) sos/domains)
        unjoined-mirrors (remove mirrors expected-mirrors)]
    {:n-nodes (count nodes) :n-edges (count edges)
     :by-kind by-kind :by-domain by-domain
     :mirrors-joined (vec (sort mirrors))
     :authoritative auth :representative repr
     :missing-kinds (vec missing-kinds)
     :missing-domains (vec missing-domains)
     :unjoined-mirrors (vec unjoined-mirrors)}))

(defn- lstrip-colon [s] (if (and (string? s) (str/starts-with? s ":")) (subs s 1) s))

(defn report-md
  "Render the coverage + gap report markdown."
  [c]
  (let [L (transient [])]
    (conj! L "# kaname 要 — coverage & gap report (G6 sourcing honesty)\n")
    (conj! L (str "> kaname is a SYNTHESIS layer; the R0 seed is a **bounded, SYNTHETIC** "
                  "illustrative multilayer graph that joins **no live mirror** (mirrors-joined "
                  "below = the seed's declared provenance, not a live ingest). Live join over the "
                  "real mirrors is G7/Council-gated. No fabricated coverage.\n"))
    (conj! L (str "**Totals**: " (:n-nodes c) " nodes · " (:n-edges c) " 縁 · "
                  (:authoritative c) " :authoritative / " (:representative c) " :representative\n"))
    (conj! L "\n## Nodes by kind\n")
    (conj! L "| kind | count |")
    (conj! L "|---|---:|")
    (doseq [k expected-kinds]
      (conj! L (str "| `" k "` | " (get (:by-kind c) k 0) " |")))
    (conj! L "\n## Domain layers populated\n")
    (conj! L "| domain | 縁 |")
    (conj! L "|---|---:|")
    (doseq [d sos/domains]
      (conj! L (str "| `" (lstrip-colon d) "` | " (get (:by-domain c) d 0) " |")))
    (conj! L "\n## Mirror lineage joined (seed provenance)\n")
    (conj! L (str "- **joined**: " (if (seq (:mirrors-joined c))
                                     (str/join ", " (map lstrip-colon (:mirrors-joined c)))
                                     "(none)") "\n"))
    (conj! L "\n## Gap worklist (routed to next live join — G7-gated)\n")
    (when (seq (:missing-domains c))
      (conj! L (str "- **empty domain layers**: "
                    (str/join ", " (map lstrip-colon (:missing-domains c)))
                    " — no 縁 yet in these layers.\n")))
    (when (seq (:unjoined-mirrors c))
      (conj! L (str "- **unjoined mirrors**: "
                    (str/join ", " (map lstrip-colon (:unjoined-mirrors c)))
                    " — their live Datom outputs are not yet joined (G7).\n")))
    (when (seq (:missing-kinds c))
      (conj! L (str "- **absent node kinds**: " (str/join ", " (:missing-kinds c)) "\n")))
    (conj! L "\n---\n_kaname 要 · ADR-2606172100 · sourcing-honest · synthetic-seed · no fabricated coverage._\n")
    (str/join "\n" (persistent! L))))

#?(:clj
   (defn -main
     [& argv]
     (let [argv (vec argv)
           here (-> *file* io/file .getParentFile .getParentFile)
           seed (if (and (seq argv) (not (str/starts-with? (first argv) "--")))
                  (io/file (first argv))
                  (io/file here "data" "seed-sos.kotoba.edn"))
           outdir (io/file here "out")
           {:keys [nodes edges]} (sos/load-file* seed)]
       (.mkdirs outdir)
       (spit (io/file outdir "coverage-report.md") (report-md (coverage nodes edges)))
       (println (str "kaname coverage → " (io/file outdir "coverage-report.md")))
       0)))
