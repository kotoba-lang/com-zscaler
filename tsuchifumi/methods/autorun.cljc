#!/usr/bin/env bb
;; tsuchifumi 土踏み — autonomous heartbeat: assess → append verdicts+risk to the ledger.
(ns tsuchifumi.methods.autorun
  "autorun.cljc — tsuchifumi 土踏み deterministic heartbeat (ADR-2606212000).

  One beat: load the regions + evidence + drivers, run the relief gate (analyze), the
  risk register (risk), AND the co-scientist (identify the top action + research
  hypothesis), and APPEND the combined verdict + risk + identified-hypothesis datoms as
  ONE content-addressed transaction to the append-only observation ledger (kotoba.cljc).
  prev-cid chaining keeps the ledger tamper-evident + resume-safe — the 持続永続化 leg.

  Deterministic by construction: the caller supplies tx-id + as-of (no wall clock,
  no Math/random) → resume-safe. IDEMPOTENT-BY-CONTENT: a beat whose datoms equal the
  previous beat's is a NO-OP (nothing appended) — the ledger records CHANGES, not a
  liveness tick. No-server-key: appends to a local file only, no network I/O.
  OBSERVATORY ONLY — tsuchifumi never diagnoses, treats, sells, or acts on a person."
  (:require [tsuchifumi.methods.analyze :as an]
            [tsuchifumi.methods.risk :as risk]
            [tsuchifumi.methods.coscientist :as cs]
            [tsuchifumi.methods.kotoba :as k]
            [tsuchifumi.methods.tsuchifumi-edn :as te]
            #?(:clj [clojure.edn :as edn])))

(defn beat
  "Run one heartbeat. opts:
     :regions :evidence :drivers   seed-derived vectors (regions required)
     :tx-id :as-of                 deterministic stamps (required)
     :log-path                     ledger path (required)
   IDEMPOTENT-BY-CONTENT: a beat whose datoms equal the last beat's is a NO-OP.
   Returns {:head :count :verdicts :severity :appended :reason}."
  [{:keys [regions evidence drivers tx-id as-of log-path]}]
  (let [assessment (an/assess regions (or evidence []))
        risk-a (risk/assess (or drivers []))
        ident (cs/identify assessment)
        ds (vec (concat (an/datoms assessment) (risk/datoms risk-a) (cs/datoms ident)))
        prev (k/head-cid log-path)
        last-ds (let [txs (k/read-log log-path)]
                  (when (seq txs) (get (last txs) ":tx/datoms")))
        unchanged? (= ds last-ds)
        base {:count (count ds)
              :verdicts (get assessment "tally")
              :severity (get risk-a "severity_tally")
              :identified {:action (get-in ident ["identified" "action" :id])
                           :research (get-in ident ["identified" "research" :id])}}]
    (if unchanged?
      (assoc base :head prev :appended false :reason :no-change)
      (let [tx (k/make-tx ds tx-id as-of prev)
            head (k/append-tx tx log-path)]
        (assoc base :head head :appended true :reason nil)))))

#?(:clj
   (defn -main [& args]
     (let [seed (or (first args) "20-actors/tsuchifumi/kotoba/seed.edn")
           log-path (or (second args)
                        (-> (clojure.java.io/file *file*) .getParentFile .getParentFile
                            (clojure.java.io/file "data" "persisted" "tsuchifumi.observation.kotoba.edn") str))
           rows (te/reconstitute-rows (edn/read-string (slurp seed)))
           regions (vec (filter #(= (:type %) :region) rows))
           evidence (vec (filter #(= (:type %) :evidence) rows))
           drivers (vec (filter #(= (:type %) :driver) rows))
           r (beat {:regions regions :evidence evidence :drivers drivers
                    :tx-id "tsuchifumi-beat-manual" :as-of "manual" :log-path log-path})]
       (println (str "observation ledger head=" (:head r)
                     " datoms=" (:count r)
                     " appended=" (:appended r)
                     (when (:reason r) (str " (" (name (:reason r)) ")"))))
       (println (str "verdicts=" (:verdicts r)))
       (println (str "severity=" (:severity r)))
       (println (str "identified=" (:identified r)))
       (println (str "chain=" (k/verify-chain log-path))))))

#?(:clj
   (when (= *file* (System/getProperty "babashka.file"))
     (apply -main *command-line-args*)))
