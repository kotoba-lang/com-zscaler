(ns meisai.methods.autorun
  "autorun.cljc — meisai 明細 AUTONOMOUS statement-intake heartbeat on the kotoba Datom log.
  1:1 Clojure port of `methods/autorun.py` (ADR-2606122400).

  Each heartbeat the actor sweeps the LOCAL intake directory (data/intake/*.edn — statement EDN the
  member-principal fetch leg already wrote), ingests every intake whose content CID is not yet in
  the log, and persists ONE content-addressed transaction per new intake to the append-only local
  kotoba Datom log, linking the previous CID into a verifiable commit-DAG.

  Constitution holds by construction: MEMBER-OWN data only (G1); credential/PAN unrepresentable
  (G2, ingest/guard raises before persist); local-only (G3, data/ gitignored, persist to LOCAL log
  only — publishes/pins/posts nothing); provenance + dedup by intake CID (G5, resume-safe,
  deterministic, no wall clock). NO external I/O. Byte-identical commit-DAG to autorun.py."
  (:require [clojure.string :as str]
            [meisai.methods.ingest :as ingest]
            [meisai.methods.kotoba :as k]
            #?(:clj [clojure.java.io :as io])))

(def base-as-of 20260612)

(defn ingested-cids
  "Every intake content CID already persisted (the dedup set) (1:1 with ingested_cids)."
  [txs]
  (reduce (fn [s tx]
            (reduce (fn [s d]
                      (if (and (= (count d) 4) (= (nth d 2) ":meisai.stmt/intake-cid"))
                        (conj s (nth d 3)) s))
                    s (get tx ":tx/datoms")))
          #{} txs))

#?(:clj
   (do
     (def ^:private here (-> (io/file *file*) .getParentFile .getParentFile))
     (def intake-default (str (io/file here "data" "intake")))

     (defn sweep
       "Deterministic intake worklist (sorted; no set iteration) (1:1 with sweep)."
       [intake-dir]
       (let [d (io/file intake-dir)]
         (if-not (.isDirectory d) []
                 (->> (.listFiles d)
                      (filter #(and (.isFile %) (str/ends-with? (.getName %) ".edn")))
                      (sort-by #(.getName %))
                      vec))))

     (defn run-cycle
       "One heartbeat: sweep intake → ingest every NEW statement (one tx each). Deterministic:
       tx ids continue from the log length; as-of = base-as-of + cycle (no wall clock)."
       [cycle intake-dir log-path]
       (loop [paths (sweep intake-dir)
              seen (ingested-cids (k/read-log log-path))
              appended [] skipped 0]
         (if (empty? paths)
           {:cycle cycle :appended appended :skipped skipped :head (k/head-cid log-path)}
           (let [[doc cid] (ingest/load-statement (first paths))]
             (if (contains? seen cid)
               (recur (rest paths) seen appended (inc skipped))
               (let [datoms (ingest/statement-datoms doc cid)
                     tx (k/make-tx datoms (inc (count (k/read-log log-path)))
                                   (+ base-as-of cycle) (k/head-cid log-path))
                     cid' (k/append-tx tx log-path)]
                 (recur (rest paths) (conj seen cid)
                        (conj appended {:intake (.getName (first paths)) :cid cid'
                                        :datoms (count datoms)})
                        skipped)))))))

     (defn -main [& argv]
       (let [argv (vec argv)
             opt (fn [f d] (let [i (.indexOf argv f)] (if (>= i 0) (nth argv (inc i)) d)))
             flag? (fn [f] (>= (.indexOf argv f) 0))
             cycles (Long/parseLong (str (opt "--cycles" "1")))
             intake (opt "--intake" intake-default)
             log-path (opt "--log" k/log-default)]
         (when (and (flag? "--fresh") (.exists (io/file log-path))) (.delete (io/file log-path)))
         (doseq [c (range 1 (inc cycles))]
           (let [r (run-cycle c intake log-path)]
             (println (str "cycle " (:cycle r) ": +" (count (:appended r)) " tx ("
                           (reduce + 0 (map :datoms (:appended r))) " datoms), "
                           (:skipped r) " already ingested, head "
                           (let [h (:head r)] (if (seq h) (subs h 0 (min 16 (count h))) "(empty)"))))))
         (let [v (k/verify-chain log-path)]
           (println (str "chain: ok=" (:ok v) " length=" (:length v)))
           (System/exit (if (:ok v) 0 1)))))))
