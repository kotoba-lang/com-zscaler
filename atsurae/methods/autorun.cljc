#!/usr/bin/env bb
;; atsurae 誂え — autonomous heartbeat: analyze product line → append datoms to the ledger.
(ns atsurae.methods.autorun
  "autorun.cljc — atsurae 誂え deterministic heartbeat (ADR-2606212010).

  One beat: load the feature model, analyze the product line (variants / commonality /
  variation points), and APPEND the analysis datoms as one content-addressed transaction to the
  append-only product-line ledger (kotoba.cljc). prev-cid chaining keeps the ledger
  tamper-evident + resume-safe.

  Deterministic: caller supplies tx-id + as-of (no wall clock, no Math/random). IDEMPOTENT-BY-
  CONTENT: a beat whose datoms equal the previous beat's is a NO-OP — the ledger records CHANGES
  to the product line, not a liveness tick. No-server-key: local file only. SPEC ONLY — atsurae
  never manufactures."
  (:require [atsurae.methods.feature-model :as fm]
            [atsurae.methods.emit :as emit]
            [atsurae.methods.kotoba :as k]
            #?(:clj [clojure.edn :as edn])))

(defn beat
  "Run one heartbeat. opts: :model :tx-id :as-of :log-path (all required).
   IDEMPOTENT-BY-CONTENT: identical datoms → NO-OP. Returns
   {:head <cid> :count <n> :n-variants <int> :appended <bool> :reason <kw|nil>}."
  [{:keys [model tx-id as-of log-path]}]
  (let [a (fm/analyze model)
        ds (emit/datoms model)
        prev (k/head-cid log-path)
        last-ds (let [txs (k/read-log log-path)]
                  (when (seq txs) (get (last txs) ":tx/datoms")))
        unchanged? (= ds last-ds)
        base {:count (count ds) :n-variants (:n-variants a)
              :n-variation-points (count (:variation-points a))}]
    (if unchanged?
      (assoc base :head prev :appended false :reason :no-change)
      (let [tx (k/make-tx ds tx-id as-of prev)
            head (k/append-tx tx log-path)]
        (assoc base :head head :appended true :reason nil)))))

#?(:clj
   (defn -main [& args]
     (let [seed-path (or (first args) "20-actors/atsurae/kotoba/seed.edn")
           log-path (or (second args)
                        (-> (clojure.java.io/file *file*) .getParentFile .getParentFile
                            (clojure.java.io/file "data" "persisted" "atsurae.line.kotoba.edn") str))
           model (fm/classify (edn/read-string (slurp seed-path)))
           r (beat {:model model :tx-id "atsurae-beat-manual" :as-of "manual" :log-path log-path})]
       (println (str "product-line ledger head=" (:head r)
                     " datoms=" (:count r) " variants=" (:n-variants r)
                     " variation-points=" (:n-variation-points r)
                     " appended=" (:appended r)
                     (when (:reason r) (str " (" (name (:reason r)) ")"))))
       (println (str "chain=" (k/verify-chain log-path))))))

#?(:clj
   (when (= *file* (System/getProperty "babashka.file"))
     (apply -main *command-line-args*)))
