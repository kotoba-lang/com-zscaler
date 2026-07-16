(ns tsumugi.methods.project-influence-posts
  "tsumugi 紡ぎ — diachronic influence MIRROR posts (observer voice, dry-run).
  Clojure port of methods/project_influence_posts.py (1:1, the pure core). ADR-2606061500.

  Projects a node/flow into an OBSERVER-voice post ABOUT a figure's documented influence —
  N2: only mirrors may be projected, never first-person (impersonation is unrepresentable —
  there is no branch that emits first-person text); G7: published false (dry-run). Depends only
  on the pure analyze_influence loaders (used by main, omitted); the projection itself is pure
  stdlib, no numpy. The numpy spectral analyze_influence stays unported."
  (:require [clojure.string :as str]))

(def KIND-VERB
  {":influences" "shaped" ":transmits" "was transmitted into" ":cites" "is cited by"
   ":reinterprets" "was reinterpreted by" ":synthesizes" "was synthesized into"
   ":translates" "was carried across language into" ":opposes" "was defined against by"})

(defn project-post
  "Project node (optionally via flow) → an observer-voice mirror post. Raises (N2) if the node
  is not a mirror — impersonation is refused."
  [node flow nodes tick]
  (when-not (get node ":mirror/is-mirror")
    (throw (ex-info (str (get node ":organism/id") " is not a mirror — refuse (N2)") {:gate "N2"})))
  (let [disclaimer (get node ":mirror/disclaimer" "観察像 — 本人ではない (observational mirror)")
        label (get node ":organism/label" (get node ":organism/id"))
        [body pid about-flow]
        (if flow
          (let [frm (get (get nodes (get flow ":flow/from")) ":organism/label" (get flow ":flow/from"))
                to (get (get nodes (get flow ":flow/to")) ":organism/label" (get flow ":flow/to"))
                verb (get KIND-VERB (get flow ":flow/kind") "influenced")
                w (double (get flow ":flow/signed-weight" 0.0))]
            [(str "観察: 「" frm "」 " verb " 「" to "」 (documented influence, weight "
                  (format "%+.2f" w) "). An information channel across history, not an endorsement.")
             (str "post." (subs (get flow ":flow/id") 3))
             (get flow ":flow/id")])
          (let [trad (str/join "," (map #(subs % 1) (get node ":hist/tradition" [])))]
            [(str "観察: 「" label "」 — public influence-bearing node ("
                  (subs (get node ":hist/subkind" "?") 1) "; " trad "). "
                  "Mapped for its documented influence on later thought, never adjudicated for truth.")
             (str "post.node." (get node ":organism/id"))
             nil]))]
    (cond-> {":post/id" pid
             ":post/about-node" (get node ":organism/id")
             ":post/voice" ":observer"            ; LOCKED (N2)
             ":post/text" (str disclaimer "\n" body)
             ":post/tick" tick
             ":post/published" false              ; DRY-RUN (G7)
             ":post/sourcing" ":representative"}
      about-flow (assoc ":post/about-flow" about-flow))))

(defn edn-str
  "Serialise a post map to a single-line EDN map (mirror of edn_str)."
  [p]
  (str "{"
       (str/join " "
                 (map (fn [[k v]]
                        (cond
                          (boolean? v) (str k " " (if v "true" "false"))
                          (and (string? v) (str/starts-with? v ":")) (str k " " v)
                          (string? v) (str k " \"" (-> (str v) (str/replace "\\" "\\\\")
                                                       (str/replace "\"" "\\\"") (str/replace "\n" "\\n")) "\"")
                          :else (str k " " v)))
                      p))
       "}"))

#?(:clj
   (defn -main
     "CLI entry: mirrors project_influence_posts.main — [seed.edn] [--out OUTDIR]. ADR-2606261200
     cljc-native operator leg. Reuses analyze_influence/load; the projection itself is pure."
     [& argv]
     (let [load*    (requiring-resolve 'tsumugi.methods.analyze-influence/load)
           argv     (vec argv)
           args     (vec (remove #(str/starts-with? % "--") argv))
           here     (let [f  (when (and *file* (not (str/blank? *file*))) (clojure.java.io/file *file*))
                          pp (some-> f .getAbsoluteFile .getParentFile .getParentFile)]
                      (if (and pp (.isDirectory (clojure.java.io/file pp "data"))) pp
                          (clojure.java.io/file "20-actors" "tsumugi")))
           seed     (if (seq args) (clojure.java.io/file (first args))
                        (clojure.java.io/file here "data" "seed-influence-history.kotoba.edn"))
           out      (if (some #{"--out"} argv) (clojure.java.io/file (nth argv (inc (.indexOf argv "--out"))))
                        (clojure.java.io/file here "out"))
           [nodes flows] (load* seed)
           tick     0
           edge-posts (for [f flows
                            :when (and (contains? nodes (get f ":flow/from"))
                                       (contains? nodes (get f ":flow/to")))]
                        (project-post (get nodes (get f ":flow/to")) f nodes tick))
           node-posts (for [[_ nd] nodes] (project-post nd nil nodes tick))
           posts    (vec (concat edge-posts node-posts))
           header   [";; tsumugi 紡ぎ — GENERATED dry-run mirror posts (ADR-2606061500). DO NOT hand-edit."
                     ";; N2 mirror-only: every post is OBSERVER voice ABOUT a node — never the figure speaking."
                     ";; G7 outward-gated: every :post/published is false. Live firehose = Council + operator."
                     "["]
           lines    (concat header (map edn-str posts) ["]"])]
       (assert (every? #(= (get % ":post/voice") ":observer") posts) "N2: all posts must be observer voice")
       (assert (every? #(false? (get % ":post/published")) posts) "G7: all posts must be dry-run")
       (.mkdirs out)
       (spit (clojure.java.io/file out "influence-posts.dryrun.kotoba.edn") (str (str/join "\n" lines) "\n"))
       (println (str "[tsumugi/posts] " (count posts) " dry-run mirror posts → "
                     (.getPath (clojure.java.io/file out "influence-posts.dryrun.kotoba.edn")))))))
