(ns ake.methods.analyze
  "analyze.cljc — 朱 (ake) end-to-end membrane over the :representative seed. ADR-2606052100.
  Clojure port of `methods/analyze.py`.

  Runs each seed edit through the full Wikipedia-stance pipeline:
      propose → triage (risk+quality+route) → [optimistic | sbt-vote | council-lv7 | refused]
              → append revision (append-only) → contributor trajectory

  and emits an offline scorecard (Markdown) + derived datoms (edn). NO live ingest / publish /
  promotion — all of that is G8 (Council Lv6+ + operator). This is a dry-run demonstration.

  The CORE is the already-ported `ake.methods._edn` (loader) + `ake.methods.revision`
  (append-only log) + this driver's siblings `ake.methods.triage` (the heart) +
  `ake.methods.contributor` (G9 trajectory). `run` is pure over a parsed seed; file I/O
  (load, report write) sits at the #?(:clj) -main edge.

  Convention (root CLAUDE.md): Python ':…' keyword strings stay strings; row maps use
  \"string\" keys matching the Python dicts; pure fns; byte-parity report (matches
  analyze.py's _report f-strings exactly, incl. the {:.2f} HALF_EVEN acceptance rate)."
  (:require [clojure.string :as str]
            [ake.methods._edn :as edn]
            [ake.methods.revision :as rev]
            [ake.methods.contributor :as contrib]
            [ake.methods.triage :as triage]))

(def TIMELOCK-H 48)      ;; the standard SBT-vote window

;; ── float formatting: Python f"{x:.2f}" (HALF_EVEN over the exact double) ──────────
(defn- fmt-2f [x]
  #?(:clj (-> (java.math.BigDecimal. (double x))
              (.setScale 2 java.math.RoundingMode/HALF_EVEN)
              (.toPlainString))
     :cljs (.toFixed (double x) 2)))

(defn- as-int
  "int(e.get(k, 0)) — coerce a value to a long (0 on nil/missing)."
  [v]
  (cond
    (nil? v) 0
    (number? v) (long v)
    :else (long #?(:clj (Long/parseLong (str v)) :cljs (js/parseInt (str v) 10)))))

(defn run
  "Drive every seed edit through the membrane. Pure over a parsed seed map.
  Returns {\"rows\" rows \"history\" history \"trajectory\" traj \"edits\" edits}."
  [seed]
  (let [edits (get seed ":edit/batch")]
    (loop [es edits
           as-of 2000
           history []
           traj (contrib/empty*)
           rows []]
      (if (empty? es)
        {"rows" rows "history" history "trajectory" traj "edits" edits}
        (let [e (first es)
              as-of (+ as-of 10)
              author (get e ":edit/author" "?")
              tri (try (triage/score-edit e) (catch #?(:clj clojure.lang.ExceptionInfo :cljs :default) ex ex))]
          (if (instance? #?(:clj Throwable :cljs js/Error) tri)
            ;; ValueError at intake → refused-at-intake row + refused trajectory event
            (recur (rest es) as-of history
                   (contrib/record traj author "refused" as-of)
                   (conj rows {"edit" (get e ":edit/id")
                               "route" ":refused-at-intake"
                               "risk" "-" "quality" "-"
                               "accepted" false
                               "note" #?(:clj (.getMessage ^Throwable tri) :cljs (.-message tri))}))
            (let [route (triage/kw* (get tri ":triage/route"))
                  risk (triage/kw* (get tri ":triage/risk"))
                  quality (get tri ":triage/quality")
                  [accepted decided note]
                  (cond
                    (= route "auto-accept")
                    [true true "optimistic fast-path (low risk + high quality)"]

                    (= route "vote")
                    (let [yes (as-int (get e ":review/yes" 0))
                          no (as-int (get e ":review/no" 0))
                          acc (> yes no)]
                      [acc true (str "1 SBT=1 vote " yes "-" no " / " TIMELOCK-H "h → "
                                     (if acc "accepted" "rejected"))])

                    (= route "council-lv7")
                    [false false "escalated to Council Lv7+ (invariant-adjacent) → pending (G7)"]

                    (= route "refused")
                    [false true (str "Charter-Rider §2 hit "
                                     ;; Python f-string !r on a str → single-quoted repr
                                     "'" (get tri ":triage/rider-token") "'"
                                     " → unpromotable (no vote)")]

                    :else [false true ""])
                  history* (if accepted (rev/append-revision history e as-of) history)
                  traj* (if decided
                          (contrib/record traj author (if accepted "accepted" "refused") as-of)
                          traj)]
              (recur (rest es) as-of history* traj*
                     (conj rows {"edit" (get e ":edit/id")
                                 "route" (str ":" route)
                                 "risk" risk "quality" quality
                                 "accepted" accepted "note" note})))))))))

;; ── report (1:1 of analyze._report f-strings) ─────────────────────────────────────

(defn- slice48
  "Python s[:48] — first 48 characters (codepoint slice)."
  [s]
  (let [s (str s)]
    (if (<= (count s) 48) s (subs s 0 48))))

(defn report
  "Render the membrane dry-run scorecard markdown byte-for-byte with analyze.py's _report."
  [res]
  (let [L (transient
           ["# 朱 (ake) — community-edit membrane dry-run\n"
            (str "Wikipedia-stance pipeline over the `:representative` seed. "
                 "No live ingest / publish / promotion (G8).\n")
            "## Routing\n"
            "| edit | risk | quality | route | accepted | note |"
            "|---|---|---|---|---|---|"])]
    (doseq [r (get res "rows")]
      (conj! L (str "| " (get r "edit") " | " (get r "risk") " | " (get r "quality") " | "
                    (get r "route") " | "
                    (if (get r "accepted") "✅" "—") " | " (get r "note") " |")))
    (conj! L "\n## Revision history (append-only, latest = current)\n")
    (let [history (get res "history")
          seen (volatile! #{})]
      (doseq [rec history]
        (let [key [(get rec ":revision/entity") (get rec ":revision/attr")]]
          (when-not (contains? @seen key)
            (vswap! seen conj key)
            (let [[ent attr] key
                  cur (rev/current history ent attr)
                  n (count (rev/history-of history ent attr))]
              (conj! L (str "- `" ent "` `" attr "` → " (get cur ":revision/sourcing")
                            " (`" (slice48 (get cur ":revision/value")) "…`), " n " revision(s)")))))))
    (conj! L "\n## Contributor trajectory (Wellbecoming, not a score-of-soul)\n")
    (let [traj (get res "trajectory")]
      ;; Python iterates `for did in res["trajectory"]` — dict insertion order.
      ;; contrib trajectory is keyed by DID in first-touch (record) order; preserve it.
      (doseq [did (keys traj)]
        (let [t (contrib/trajectory traj did)
              ar (get t "acceptanceRate")
              rate (if (nil? ar) "—" (fmt-2f ar))
              flag (if (get t "throttled") " ⚠ throttled" "")]
          (conj! L (str "- `" did "` — accepted " (get t "accepted") ", refused " (get t "refused")
                        ", acceptance " rate flag)))))
    (str (str/join "\n" (persistent! L)) "\n")))

#?(:clj
   (defn- ake-root
     "Resolve 20-actors/ake/ — from *file* if available, else walk up from user.dir."
     []
     (let [from-file (try (some-> *file* clojure.java.io/file .getParentFile .getParentFile)
                          (catch Exception _ nil))]
       (if (and from-file (.exists (clojure.java.io/file from-file "data" "seed-edit-graph.kotoba.edn")))
         from-file
         (let [cwd (clojure.java.io/file (System/getProperty "user.dir"))
               candidate (clojure.java.io/file cwd "20-actors" "ake")]
           (if (.exists (clojure.java.io/file candidate "data" "seed-edit-graph.kotoba.edn"))
             candidate
             cwd))))))

#?(:clj
   (defn -main
     "CLI: run the membrane over the seed → write out/membrane-dryrun.md + print it."
     [& argv]
     (let [argv (vec argv)
           root (ake-root)
           seed-path (if (and (seq argv) (not (str/starts-with? (first argv) "--")))
                       (clojure.java.io/file (first argv))
                       (clojure.java.io/file root "data" "seed-edit-graph.kotoba.edn"))
           outdir (clojure.java.io/file root "methods" "out")
           res (run (edn/reconstitute (edn/load-edn seed-path) "data.seed-edit-graph"))
           rpt (report res)]
       (.mkdirs outdir)
       (spit (clojure.java.io/file outdir "membrane-dryrun.md") rpt)
       (print rpt)
       0)))
