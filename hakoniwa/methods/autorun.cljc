;; ported from 20-actors/hakoniwa/methods/autorun.py — real port replacing the unit_refactor
;; stage-0 "TODO: port-failed" stubs. NS fixed root.hakoniwa.* → hakoniwa.* (20-actors source root).
(ns hakoniwa.methods.autorun
  "autorun.py — hakoniwa 箱庭 AUTONOMOUS heartbeat loop on the kotoba Datom log. 1:1 Clojure port.
  ADR-2606111500 (R1).

  Each heartbeat the actor runs its whole pipeline ITSELF: load box (synthetic personas) →
  simulate (Friedkin-Johnsen K-replica ensemble) → distribution (G2 distribution-only) → narrate
  (Murakumo, graceful template fallback; G5) → draft + EMIT a guarded social post (G2 no-point +
  G3 no-steer) → PERSIST a content-addressed transaction to the append-only kotoba Datom log
  (commit-DAG, resume-safe).

  The post status is :published when a member-DID author is supplied (G7). Deterministic +
  idempotent-by-CID. File I/O behind #?(:clj ...). The Python argparse CLI is omitted."
  (:require [hakoniwa.methods.world :as w]
            [hakoniwa.methods.simulate :as s]
            [hakoniwa.methods.distribution :as d]
            [hakoniwa.methods.murakumo :as m]
            [hakoniwa.methods.social :as soc]
            [hakoniwa.methods.kotoba :as k]))

#?(:clj
   (def seed-path
     (str (System/getProperty "user.dir") "/20-actors/hakoniwa/data/seed-scenario.kotoba.edn")))
#?(:clj (def log-path k/log-default))
(def base-as-of 20260611)

(defn- scenario-label [nodes]
  (let [outs (w/outcomes nodes)]
    (if (seq outs)
      (get (val (first outs)) ":sim/label" "outcome")
      "outcome")))

#?(:clj
   (defn run-cycle
     "One autonomous heartbeat. cycle drives tx-id + as-of (deterministic / resume-safe)."
     [cycle {:keys [seed-path log-path steps replicas seed author publish swarm transport]
             :or {steps s/default-steps replicas s/default-replicas seed s/default-seed
                  author "" publish false swarm false transport nil}}]
     (let [{:keys [nodes edges]} (w/load-file* seed-path)
           [results meta] (if swarm
                            (s/swarm-ensemble nodes edges
                                              {:steps steps :replicas replicas :seed seed
                                               :step-fn (fn [st nm su an] (m/persona-step st nm su an true))})
                            (s/ensemble nodes edges {:steps steps :replicas replicas :seed seed}))
           dist (d/distribution results)
           label (scenario-label nodes)
           narr (m/narrate label dist)
           status (if (and publish (not= "" author)) ":published" ":dry-run")
           post0 (soc/draft-distribution-post label dist {:narration (get narr "text")
                                                          :author author :status status})
           post (assoc post0 ":post/narration-via" (get narr "via"))
           receipt (soc/emit post transport)
           datoms (vec (concat (k/world-datoms nodes edges meta)
                               (k/distribution-datoms dist)
                               (k/post-datoms [post] (str "post-c" cycle))))
           tx (k/make-tx datoms {:tx-id cycle :as-of (+ base-as-of cycle) :prev-cid (k/head-cid log-path)})
           cid (k/append-tx tx log-path)]
       {"cycle" cycle
        "scenario" label
        "p50" (get-in dist ["quantiles" ":p50"])
        "spread" (- (get-in dist ["quantiles" ":p90"]) (get-in dist ["quantiles" ":p10"]))
        "narration_via" (get narr "via")
        "post_status" (get post ":post/status")
        "emit" receipt
        "datoms" (count datoms)
        "cid" cid})))

#?(:clj
   (defn run-autonomous
     "Drive `cycles` self-paced heartbeats. Each appends one content-addressed tx."
     ([cycles] (run-autonomous cycles {}))
     ([cycles {:keys [seed-path log-path author publish swarm transport]
               :or {seed-path nil log-path nil author "" publish false swarm false transport nil}}]
      (let [sp (or seed-path hakoniwa.methods.autorun/seed-path)
            lp (or log-path hakoniwa.methods.autorun/log-path)
            beats (mapv (fn [c] (run-cycle c {:seed-path sp :log-path lp :author author
                                              :publish publish :swarm swarm :transport transport}))
                        (range 1 (inc cycles)))]
        {"cycles" cycles
         "beats" beats
         "log_length" (count (k/read-log lp))
         "head_cid" (k/head-cid lp)
         "chain" (k/verify-chain lp)
         "fleet" (m/fleet-available?)}))))
