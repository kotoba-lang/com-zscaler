#!/usr/bin/env bb
;; sanae 早苗 — labor-liberation BUILD-PRIORITY coverage (which high-LPS toil still lacks an actor).
(ns sanae.methods.labor-coverage
  "labor_coverage.cljc — the build-priority worklist for the labor-liberation OSS-robotics roster
  (ADR-2606032100 / 2606032130). sanae.methods.labor-liberation ranks toil sectors by Liberation
  Priority Score (LPS = log10(headcount)·misery·automatability·charter-fit·coverage-gap); this
  namespace CROSS-REFERENCES that ranking against the actual actor roster (does 20-actors/<slug>
  exist?) so the org can SEE the highest-priority toil that still has no liberating actor — the
  next robots to build.

  A coverage map, not a mandate: it observes the gap between the LPS priority and the built
  roster. Charter-excluded sectors (charter-fit 0 → LPS 0, e.g. MINING N1) are NOT build targets
  and never appear in the worklist. Pure (file I/O — the roster probe — only behind #?(:clj …));
  the LPS seed is `:representative` (G8), so this is the SHAPE of the priority, not a sourced plan."
  (:require [sanae.methods.labor-liberation :as ll]
            [clojure.string :as str]
            #?(:clj [clojure.java.io :as io])))

(defn sector-slug
  "The actor slug a sector :name maps to — its first whitespace-delimited token, lower-cased.
  'hofuri (meat processing)' → \"hofuri\"; 'MINING (excluded N1)' → \"mining\"."
  [g]
  (-> (:name g) (str/split #"\s+") first str/lower-case))

#?(:clj
   (defn actor-exists?
     "True when an actor directory 20-actors/<slug> exists in the repo."
     [slug]
     (.isDirectory (io/file "20-actors" slug))))

(defn build-priority
  "Every labor-liberation seed sector annotated with {:slug :lps :has-actor}, sorted by LPS
  descending. :has-actor is the roster probe (false off the JVM/bb). The point of the list is the
  gap between priority and built coverage."
  ([] (build-priority #?(:clj actor-exists? :default (constantly false))))
  ([exists?]
   (->> ll/seed-gaps
        (map (fn [g] (assoc g :slug (sector-slug g) :lps (ll/lps g)
                            :has-actor (boolean (exists? (sector-slug g))))))
        (sort-by :lps #(compare %2 %1)))))

(defn coverage-gaps
  "The BUILD WORKLIST: charter-clean sectors (LPS > 0) with NO actor yet, highest-priority first.
  Charter-excluded sectors (LPS 0) are filtered out — they are not toil to liberate by robot."
  ([] (coverage-gaps (build-priority)))
  ([ranked]
   (->> ranked (remove :has-actor) (filter #(pos? (:lps %))))))

(defn coverage-report
  "Markdown build-priority report: the full ranked roster (✅ built / ⬜ gap) + the worklist."
  ([] (coverage-report (build-priority)))
  ([ranked]
   (let [gaps (coverage-gaps ranked)
         built (count (filter :has-actor ranked))
         live (count (filter #(pos? (:lps %)) ranked))]
     (str "# Labor-liberation build-priority coverage (ADR-2606032100)\n\n"
          "_LPS-ranked toil × actor roster. " built "/" live " charter-clean sectors have a "
          "liberating actor; " (count gaps) " priority gap(s) remain._\n\n"
          "| LPS | sector | slug | actor |\n|---:|---|---|---|\n"
          (str/join "\n"
            (for [g ranked]
              (format "| %.2f | %s | `%s` | %s |"
                      (double (:lps g)) (:name g) (:slug g)
                      (cond (zero? (double (:lps g))) "— (charter-excluded)"
                            (:has-actor g) "✅ built"
                            :else "⬜ **GAP**"))))
          "\n\n## Build worklist — highest-priority toil with no actor yet\n\n"
          (if (seq gaps)
            (str/join "\n" (for [g gaps]
                             (format "- **%s** (LPS %.2f, ISCO %s) — misery %.1f · automatability %.2f"
                                     (:name g) (double (:lps g)) (:isco g)
                                     (double (:misery g)) (double (:automatability g)))))
            "_(none — every charter-clean priority sector has an actor)_")
          "\n"))))

#?(:clj
   (defn -main [& _]
     (println (coverage-report))))

#?(:clj
   (when (= *file* (System/getProperty "babashka.file"))
     (-main)))
