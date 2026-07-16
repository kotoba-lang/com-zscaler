#!/usr/bin/env bb
;; iriai 入会 — autonomous heartbeat: assess infra+fund+manage → append to the commons ledger.
(ns iriai.methods.autorun
  "autorun.cljc — iriai 入会 deterministic heartbeat (ADR-2606272200).

  One beat: load the lifeline-cells + deployed assets, run the full commons pass —
    INFRA    (coverage/resilience verdict) →
    FUND     (§1.16 in-kind funding proposal, cash≡0) →
    MANAGE   (1 SBT = 1 vote governance + :intent actuation, no-server-key) →
    TWIN     (physical-simulation degradation: condition/RUL/safety) →
    MAINTAIN (operations/maintenance lifecycle verdict + OpEx) —
  and APPEND the combined infra+fund+manage+twin+maintain datoms as ONE content-addressed
  transaction to the append-only commons ledger (kotoba.cljc). prev-cid chaining keeps the
  ledger tamper-evident + resume-safe — the 持続永続化 leg.

  Deterministic by construction: the caller supplies tx-id + as-of (no wall clock,
  no Math/random) → resume-safe. IDEMPOTENT-BY-CONTENT: a beat whose datoms equal the
  previous beat's is a NO-OP (nothing appended) — the ledger records CHANGES, not a
  wall-clock liveness tick. No-server-key (G6): appends to a local file only, no network
  I/O. ASSESSMENT ONLY (G5) — iriai never energizes, flows, ignites, or activates."
  (:require [iriai.methods.infra :as infra]
            [iriai.methods.fund :as fund]
            [iriai.methods.manage :as manage]
            [iriai.methods.twin :as twin]
            [iriai.methods.maintain :as maintain]
            [iriai.methods.forecast :as forecast]
            [iriai.methods.kotoba :as k]
            #?(:clj [clojure.edn :as edn])))

(defn beat
  "Run one heartbeat. opts:
     :cells     vector of lifeline-cell maps (required)
     :assets    vector of deployed-asset maps (optional; twin + maintenance)
     :tx-id     deterministic tx id (required)
     :as-of     deterministic as-of stamp (required)
     :log-path  ledger path (required)
   IDEMPOTENT-BY-CONTENT: unchanged combined datoms → NO-OP (nothing appended).
   Returns {:head <cid> :count <n> :infra <tally> :fund <n> :gov <n>
            :twin <n> :maint <tally> :appended <bool> :reason <kw|nil>}."
  [{:keys [cells assets tx-id as-of log-path]}]
  (let [assessment (infra/assess cells)
        plan (fund/plan cells)
        gov (manage/ledger plan)
        assets (or assets [])
        tw (twin/assess assets)
        maint (maintain/plan assets)
        fcst (forecast/schedule assets)
        ds (vec (concat (infra/datoms assessment)
                        (fund/datoms plan)
                        (manage/datoms gov)
                        (twin/datoms tw)
                        (maintain/datoms maint)
                        (forecast/datoms fcst)))
        prev (k/head-cid log-path)
        last-ds (let [txs (k/read-log log-path)]
                  (when (seq txs) (get (last txs) ":tx/datoms")))
        unchanged? (= ds last-ds)
        base {:count (count ds)
              :infra (get assessment "tally")
              :fund (get plan "count")
              :gov (get gov "count")
              :twin (get tw "count")
              :maint (get maint "tally")
              :forecast {:due-now (get fcst "due-now") :within-5yr (get fcst "within-5yr")}}]
    (if unchanged?
      (assoc base :head prev :appended false :reason :no-change)
      (let [tx (k/make-tx ds tx-id as-of prev)
            head (k/append-tx tx log-path)]
        (assoc base :head head :appended true :reason nil)))))

#?(:clj
   (defn -main [& args]
     (let [seed (or (first args) "20-actors/iriai/kotoba/seed.edn")
           log-path (or (second args)
                        (-> (clojure.java.io/file *file*) .getParentFile .getParentFile
                            (clojure.java.io/file "data" "persisted" "iriai.commons.kotoba.edn") str))
           bridge? (some #{"--bridge"} args)
           rows (edn/read-string (slurp seed))
           cells (vec (filter #(= (:type %) :lifeline-cell) rows))
           assets (vec (filter #(= (:type %) :asset) rows))
           r (beat {:cells cells :assets assets
                    :tx-id "iriai-beat-manual" :as-of "manual" :log-path log-path})]
       (println (str "commons ledger head=" (:head r)
                     " datoms=" (:count r)
                     " appended=" (:appended r)
                     (when (:reason r) (str " (" (name (:reason r)) ")"))))
       (println (str "infra=" (:infra r) " fund=" (:fund r) " gov=" (:gov r)
                     " twin=" (:twin r) " maint=" (:maint r)))
       (println (str "chain=" (k/verify-chain log-path)))
       (when bridge?
         ;; push the local commit-DAG to the LIVE kotoba engine (DRY-RUN unless IRIAI_KOTOBA_LIVE=1,
         ;; FAIL-OPEN: engine down / boundary → :error, the beat still completed locally). G7 operator step.
         (let [br (try (require 'iriai.methods.kotoba-bridge)
                       ((resolve 'iriai.methods.kotoba-bridge/push) log-path)
                       (catch Exception e {:mode "error" :error (.getMessage e)}))]
           (println (str "bridge=" (:mode br)
                         (when-let [p (:pending br)] (str " pending=" p))
                         (when-let [p (:pushed br)] (str " pushed=" p))
                         (when-let [d (:datoms-confirmed br)] (str " datoms-confirmed=" d))
                         (when (:error br) (str " (" (:error br) ")")))))))))

#?(:clj
   (when (= *file* (System/getProperty "babashka.file"))
     (apply -main *command-line-args*)))
