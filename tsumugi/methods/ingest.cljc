(ns tsumugi.methods.ingest
  "tsumugi 紡ぎ — §D7.1 ingester: weave public follow/deps sources into the graph.
  Clojure port of methods/ingest.py (1:1).

  Merges the curated power seed with ingest sources into one woven kotoba-EDN graph —
  claimed-first, aggregate-first, :representative. Latent organisms are minted from public
  handles (:organism/claimed? false, :organism/standing :latent); they become :member only
  on the §D5 covenant claim.

  Gates: G1 power/public only (fixtures contain no private persons); G7 outward-gated (LIVE
  atproto fetch is operator-gated, lives in the Python main, omitted here — this port reads
  only committed fixtures); G5 sourcing-honesty (every ingested record stays :representative).

  The pure merge (weave-sources) takes already-loaded labelled sources; the file read + glob
  live at the #?(:clj) edge. read-edn is the shared analyze-scale reader (same EDN family;
  the Python `from analyze import read_edn` is byte-identical logic). stdlib only."
  (:require [clojure.string :as str]
            [tsumugi.methods.analyze-scale :as asc]
            #?(:clj [clojure.java.io :as io])))

(defn- key* [rec]
  (cond
    (contains? rec ":organism/id") ["org" (get rec ":organism/id")]
    (contains? rec ":en/id") ["en" (get rec ":en/id")]
    :else nil))

(defn- sd
  "setdefault — assoc only if the key is absent."
  [m k v]
  (if (contains? m k) m (assoc m k v)))

(defn weave-sources
  "Pure merge of labelled sources [{:name :records :seed?}…] → {:merged :counts}.
  Dedup by key (org/en id), first source wins; non-seed org records get the latent +
  sourcing defaults (G1/G5)."
  [sources]
  (reduce
   (fn [{:keys [merged seen counts]} {:keys [name records seed?]}]
     (let [step (reduce
                 (fn [{:keys [merged seen n]} rec]
                   (let [k (key* rec)]
                     (if (or (nil? k) (contains? seen k))
                       {:merged merged :seen seen :n n}
                       (let [org? (= "org" (first k))
                             rec (cond-> rec
                                   (and (not seed?) org?)
                                   (-> (sd ":organism/claimed?" false) (sd ":organism/standing" ":latent"))
                                   true
                                   (sd (if org? ":organism/sourcing" ":en/sourcing") ":representative"))]
                         {:merged (conj merged rec) :seen (conj seen k) :n (inc n)}))))
                 {:merged merged :seen seen :n 0} records)]
       {:merged (:merged step) :seen (:seen step)
        :counts (assoc counts name (:n step))}))
   {:merged [] :seen #{} :counts {}} sources))

(defn to-edn
  "Serialize the woven records to EDN (ingest.py style)."
  [recs]
  (let [v (fn [x]
            (cond
              (true? x) "true"
              (false? x) "false"
              (string? x) (if (str/starts-with? x ":") x (str "\"" x "\""))
              :else (str x)))
        head [";; tsumugi 紡ぎ — GENERATED woven graph (seed + ingest). DO NOT hand-edit."
              ";; claimed-first + aggregate-first + :representative (ADR-2606011800 §D7.1)."
              "["]
        rows (map (fn [r] (str "{" (str/join " " (map (fn [[k val]] (str k " " (v val))) r)) "}")) recs)]
    (str (str/join "\n" (concat head rows ["]"])) "\n")))

#?(:clj
   (defn weave
     "Read the seed + sorted data/ingest/*.edn and merge them (fixture mode — no network).
     Returns {:merged :counts}."
     ([] (weave "20-actors/tsumugi/data/seed-power-graph.kotoba.edn"
                "20-actors/tsumugi/data/ingest"))
     ([seed-path ingest-dir]
      (let [fname (fn [p] (last (str/split (str p) #"/")))
            ingest-files (->> (.listFiles (io/file ingest-dir))
                              (map #(.getPath %))
                              (filter #(str/ends-with? % ".edn"))
                              sort)
            sources (cons {:name (fname seed-path) :records (asc/read-edn (slurp seed-path)) :seed? true}
                          (map (fn [f] {:name (fname f) :records (asc/read-edn (slurp f)) :seed? false})
                               ingest-files))]
        (weave-sources sources)))))

#?(:clj
   (defn -main
     "CLI entry: mirrors ingest.main — fixture-mode weave (seed + data/ingest/*.edn) →
     out/woven-graph.kotoba.edn. --live is G11/Council-gated + R0 scaffold (refused).
     ADR-2606261200 cljc-native operator leg (was `python3 methods/ingest.py`)."
     [& argv]
     (if (some #{"--live"} (vec argv))
       (binding [*out* *err*]
         (println (str "REFUSED: live atproto ingest is G11/Council-gated + an R0 scaffold "
                       "(not implemented). Set TSUMUGI_OPERATOR_GATE + wire "
                       "app.bsky.graph.getFollows under Council (ADR-2605231902), then re-run.")))
       (let [here   (let [f  (when (and *file* (not (str/blank? *file*))) (io/file *file*))
                          pp (some-> f .getAbsoluteFile .getParentFile .getParentFile)]
                      (if (and pp (.isDirectory (io/file pp "data"))) pp (io/file "20-actors" "tsumugi")))
             {:keys [merged counts]} (weave)
             orgs   (filter #(contains? % ":organism/id") merged)
             edges  (filter #(contains? % ":en/id") merged)
             latent (count (filter #(false? (get % ":organism/claimed?")) orgs))
             out    (io/file here "out")]
         (.mkdirs out)
         (spit (io/file out "woven-graph.kotoba.edn") (to-edn merged))
         (println "woven (fixture mode — no network):")
         (doseq [[name n] counts] (println (str "  + " n " new records from " name)))
         (println (str "= " (count orgs) " organisms (" latent " latent/unclaimed) · " (count edges) " 縁"))
         (println (str "✓ wrote " (.getPath (io/file out "woven-graph.kotoba.edn"))))))))
