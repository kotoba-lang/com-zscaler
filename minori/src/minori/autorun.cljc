(ns minori.autorun
  "minori 稔り resident-runtime heartbeat — one react beat per invocation, persisted to an
   append-only content-addressed ledger, with a growth evaluation (G_prev → G_now, dG, η, adoption).
   Deterministic + resume-safe (beat index = ledger length; no wall-clock, no randomness):
   re-running with no new lever = idempotent no-op. Run under bb:

     bb --classpath 20-actors/minori/src \\
        -e \"(require 'minori.autorun) (minori.autorun/-main)\"

   Live legs (real ie-flow scoreboard JOIN, real donation/OSS metrics, sending any social action)
   are G7/operator/member-gated — this loop only OBSERVES the MAP + SoS roster and PREPARES (dry-run)."
  (:require [minori.score   :as score]
            [minori.react   :as react]
            [minori.measure :as measure]
            [minori.social  :as social]
            [minori.reach   :as reach]
            [minori.kotoba  :as kotoba]
            [minori.ceiling :as ceiling]
            [minori.ledger  :as ledger]
            [clojure.edn    :as edn]
            [clojure.java.io :as io]))

(def defaults
  {:system           "20-actors/minori/system.edn"
   :valuation        "80-data/ie-flow/social-capital-valuation.edn"
   :sos              "80-data/ie-flow/system-of-systems.edn"
   :scoreboard       "80-data/ie-flow/scoreboard.edn"
   :capture-snapshot "80-data/social-capital/capture-snapshot.edn"
   :digest           "20-actors/minori/data/last-digest.md"
   :ledger           "20-actors/minori/data/ledger.edn"})

(defn- unblob
  "system.edn's non-scalar attrs (:system/boundary :system/reward :score/model) are
   pr-str'd string blobs in the datomic/datascript tx-data shape; parse them back."
  [v]
  (if (string? v)
    (try (let [parsed (edn/read-string v)] (if (coll? parsed) parsed v))
         (catch #?(:clj Exception :cljs :default) _ v))
    v))

(defn- reconstitute-system
  "system.edn is stored as datomic/datascript tx-data: [{:db/id -1 :actor/actor \"minori\" ...}].
   Bare top-level keys (:actor :adr :kind :doctrine) were promoted under the :actor/* namespace
   at datomize-time — un-promote those back to bare keys here so downstream lookups (:score/model
   etc, which were ALREADY namespaced and are left untouched by datomize) keep working unchanged."
  [tx-data]
  (into {}
        (map (fn [[k v]]
               [(if (= (namespace k) "actor") (keyword (name k)) k) (unblob v)]))
        (dissoc (first tx-data) :db/id)))

(defn next-worklist
  "The :intervention-worklist export (system.edn) — the prioritized REAL (mostly G7-gated) next
   step to MOVE the truth, ranked by lowest component first (highest leverage). minori sees the
   truth; its job is to surface what would actually raise it (dry-run; live legs operator/member-gated)."
  [m-after obs]
  (let [comps (:components m-after)
        items [{:lever :capture :value (:capture comps)
                :step "wire live donation-flow + OSS-adoption metric → real captured÷addressable"
                :gate :G7-operator}
               {:lever :eta :value (:eta comps)
                :step "raise REAL net-export (η→1) via member-principal deepen-symbiosis live leg"
                :gate :G7-member}
               {:lever :phi :value (:phi comps)
                :step (format "close the SoS RUNNING gap: %d actors hold the reward spec but only %d are measured running — roll out SoS-rule (run + scoreboard) to the remaining %d"
                              (or (:hold-spec obs) 0) (or (:running obs) 0)
                              (- (or (:hold-spec obs) 0) (or (:running obs) 0)))
                :gate :none}
               {:lever :adoption :value (:adoption comps)
                :step "sustain SoS-reward adoption across the roster"
                :gate :none}]]
    (assoc (first (sort-by :value items))
           :capture-note (get-in obs [:capture :note]))))

(defn run
  ([] (run defaults))
  ([{:keys [system valuation sos scoreboard capture-snapshot digest ledger] :as paths}]
   (let [sys      (reconstitute-system (edn/read-string (slurp system)))
         model    (:score/model sys)
         _val     (score/read-edn valuation)            ; the MAP being tracked (presence = observed)
         adoption (score/roster-adoption sos (:adoption (:targets model)))
         led      (ledger/load-ledger ledger)
         prev     (ledger/head led)
         state    (or (:state prev) {})
         done     (set (or (:done prev) []))
         {:keys [pick state' done' measure-before]}
           (react/beat {:state state :done done} model adoption)
         ;; 観測: minori is an observatory — it reads ALL available truth every beat (η from the
         ;; scoreboard, Φ from the roster, capture from the valuation MAP) and GROUNDS it into the
         ;; score (truth beats the loop's stubs; grounding can LOWER an optimistic stub — honest).
         obs      (measure/observe {:scoreboard scoreboard :capture-snapshot capture-snapshot} (:adopted adoption))
         ;; QUIESCENCE: a resident organism idles when nothing external changed. Fingerprint the REAL
         ;; observed inputs; if the last beat was CONVERGED and the fingerprint is unchanged, this beat
         ;; is a no-op — we do NOT append a redundant entry and do NOT grow the stub state (which would
         ;; thrash forever). minori only does real work + appends when an observation shifts.
         obs-fp   (ledger/sha256-hex (pr-str [(get-in obs [:colony-eta :mean]) (:running obs)
                                              (get-in obs [:capture :ratio]) (:adopted adoption)
                                              (:hold-spec obs)]))
         quiescent? (boolean (and prev (get-in prev [:convergence :converged?])
                                  (= obs-fp (:obs-fingerprint prev))))
         state''  (measure/ground state' obs)              ; grounds colony-η + capture (truth)
         grounded? (boolean (get-in obs [:colony-eta :mean]))
         m1       (score/growth state'' model adoption)    ; preliminary (colony-grounded η)
         next-action (next-worklist m1 obs)                ; the :intervention-worklist export
         ;; social action: PREPARE (never send) a charter-clean donor/contributor digest. The donor
         ;; digest reports the COLONY/org η (0.809) — that is the right number for donors, not
         ;; minori's own self-η. :dry-run-social-action export; a MEMBER reviews + sends (G7).
         digest-art (social/digest {:eta (get-in m1 [:components :eta])
                                    :adopted (:adopted adoption)
                                    :realized-phi (or (:realized-phi obs) 0.0)
                                    :next-step (:step next-action)
                                    :next-gate (:gate next-action)})
         _        (when digest (io/make-parents digest) (spit digest (:body digest-art)))
         ;; §1.13 reach: OBSERVE (capped) whether the gift actually reached humanity — reaction-rate
         ;; is unrepresentable here, and reach is NOT a reward gradient (rewarding reactions = an
         ;; engagement-maximiser, forbidden). Digest is prepared-unsent ⇒ no real send receipts ⇒ 0.
         reach-obs (reach/observe {:send-receipts nil})
         ;; (b) minori's OWN net-giver η — EARNED from real evidence this run: it EXPORTS order (the
         ;; kotoba commit persisted below + the public charter-clean digest), retains NOTHING privately
         ;; (no key, sells nothing; any captured value routes to the Public Fund, never to minori), and
         ;; gives freely (charter-clean). All three real + checkable ⇒ a perfect net-giver, η_self=1.0
         ;; (ADR-2606212200 rectification ceiling). If any fails (e.g. a non-clean digest) ⇒ no claim.
         eta-self (measure/eta-self {:exported? true
                                     :privately-retained? false
                                     :gives-freely? (:charter-clean digest-art)})
         state-f  (measure/ground state'' {:eta-self eta-self})
         m-after  (score/growth state-f model adoption)    ; final (η raised to minori's earned 1.0)
         dG       (- (:G m-after) (:G measure-before))
         reward   (:reward m-after)
         gated?   (:gated? m-after)
         ;; kotoba 常駐runtime: emit this beat as EAVT datoms on the canonical kotoba commit-DAG,
         ;; chained on the previous beat's kotoba CID; bridge to the live engine is dry-run (G7).
         beat-n   (count led)
         kdatoms  (kotoba/datoms-of {:beat beat-n :G (:G m-after) :dG dG
                                     :eta (:eta m-after) :adoption (:p adoption)
                                     :components (:components m-after)
                                     :net-giver? (:net-giver? m-after) :gated? gated?})
         kcommit  (kotoba/commit kdatoms (get-in prev [:kotoba :cid]))
         kbridge  (kotoba/bridge! kcommit)
         ;; 成長を評価する (mature): convergence/ceiling — self-drivable vs externally-gated headroom,
         ;; over the last 3 beats' real ΔG. Reports an HONEST plateau with the external blockers named.
         recent-dG (conj (vec (take-last 2 (map :dG led))) dG)
         convergence (ceiling/evaluate (:weights model)
                                       (:components m-after)
                                       {:recent-dG recent-dG})
         entry    {:actor "minori"
                   :adr "2606261114"
                   :kind :react-beat
                   :observed {:valuation valuation :sos-adopted (:adopted adoption)
                              :colony-eta (get-in obs [:colony-eta :mean])
                              :realized-phi (:realized-phi obs)
                              :running (:running obs) :hold-spec (:hold-spec obs)
                              :capture-grounded (get-in obs [:capture :ratio])
                              :grounded? grounded?}
                   :pick pick
                   :state state-f
                   :eta-self eta-self
                   :done (vec done')
                   :G (:G m-after)
                   :G-prev (:G measure-before)
                   :dG dG
                   :eta (:eta m-after)
                   :net-giver? (:net-giver? m-after)
                   :gated? gated?
                   :reward reward
                   :components (:components m-after)
                   :next-action next-action
                   :social-action {:status (:status digest-art)
                                   :charter-clean (:charter-clean digest-art)
                                   :bytes (:bytes digest-art)
                                   :artifact digest}
                   :reach {:reach (:reach reach-obs) :cap (:cap reach-obs)
                           :rewarded? false :note (:note reach-obs)}   ; §1.13: observed, never a reward gradient
                   :kotoba {:graph (:graph kcommit) :cid (:cid kcommit)
                            :parent (:parent kcommit) :datom-count (count kdatoms)
                            :bridge (:mode kbridge)}
                   :convergence convergence
                   :obs-fingerprint obs-fp
                   :adoption (:p adoption)}
         ;; quiescent ⇒ idle: do NOT append a redundant entry, do NOT persist stub growth.
         appended (if quiescent?
                    {:appended? false :head prev :ledger led}
                    (ledger/append! ledger entry))
         appended? (:appended? appended)
         head     (:head appended)
         chain    (ledger/verify-chain (:ledger appended))]
     {:appended? appended?
      :quiescent? quiescent?
      :beat (:beat head)
      :pick pick
      :G-prev (:G measure-before)
      :G (:G m-after)
      :dG dG
      :eta (:eta m-after)
      :adoption (:p adoption)
      :sos-adopted (:adopted adoption)
      :grounded? grounded?
      :colony-eta (get-in obs [:colony-eta :mean])
      :eta-self eta-self
      :net-giver? (:net-giver? m-after)
      :realized-phi (:realized-phi obs)
      :running (:running obs) :hold-spec (:hold-spec obs)
      :next-action next-action
      :social-action {:status (:status digest-art) :charter-clean (:charter-clean digest-art)
                      :bytes (:bytes digest-art) :artifact digest}
      :kotoba {:graph (:graph kcommit) :cid (:cid kcommit) :parent (:parent kcommit)
               :datom-count (count kdatoms) :bridge (:mode kbridge)}
      :convergence convergence
      :reach {:reach (:reach reach-obs) :cap (:cap reach-obs)
              :rewarded? false :note (:note reach-obs)}
      :gated? gated?
      :reward reward
      :components (:components m-after)
      :head-cid (:cid head)
      :verify-chain chain})))

(defn -main [& args]
  (let [paths (if (seq args) (merge defaults (apply hash-map (map read-string args))) defaults)
        r (run paths)]
    (println "─── minori 稔り react beat ───────────────────────────")
    (when (:quiescent? r)
      (println (format "⏸ QUIESCENT — no external change since beat #%s; resident runtime idling (no append, no stub growth)" (:beat r))))
    (println (format "beat #%s   pick=%s (%s)%s" (:beat r) (name (:id (:pick r))) (name (:kind (:pick r)))
                     (if (:quiescent? r) "  [idle]" "")))
    (println (format "G: %.4f → %.4f   ΔG=%+.5f   η=%.3f   adoption=%d actors (%.3f)"
                     (:G-prev r) (:G r) (:dG r) (:eta r) (:sos-adopted r) (:adoption r)))
    (println (format "reward=%.4f  net-giver?=%s  gated?=%s%s  appended?=%s"
                     (:reward r) (:net-giver? r) (:gated? r)
                     (if (:eta-self r) (format " (self-η earned=%.1f)" (:eta-self r)) "")
                     (:appended? r)))
    (println (format "components: η=%.3f adoption=%.3f capture=%.3f Φ=%.3f"
                     (get-in r [:components :eta]) (get-in r [:components :adoption])
                     (get-in r [:components :capture]) (get-in r [:components :phi])))
    (when (:colony-eta r)
      (println (format "観測: colony-η=%.3f (real scoreboard)  Φ=ln(running %d/%d hold-spec)=%.2f  grounded?=%s"
                       (:colony-eta r) (or (:running r) 0) (or (:hold-spec r) 0)
                       (or (:realized-phi r) 0.0) (:grounded? r))))
    (when-let [na (:next-action r)]
      (println (format "次の一手: [%s %.3f, gate=%s] %s"
                       (name (:lever na)) (:value na) (name (:gate na)) (:step na))))
    (when-let [sa (:social-action r)]
      (println (format "social-action: %s (charter-clean=%s, %d bytes) → %s"
                       (name (:status sa)) (:charter-clean sa) (:bytes sa) (:artifact sa))))
    (when-let [re (:reach r)]
      (println (format "reach(§1.13): %d/%d capped, rewarded?=%s — %s"
                       (:reach re) (:cap re) (:rewarded? re) (:note re))))
    (when-let [k (:kotoba r)]
      (println (format "kotoba: graph=%s tx-cid=%s… (%d datoms, parent=%s) bridge=%s"
                       (:graph k) (subs (str (:cid k)) 0 12) (:datom-count k)
                       (if (:parent k) (subs (str (:parent k)) 0 8) "∅-root") (name (:bridge k)))))
    (when-let [c (:convergence r)]
      (println (format "評価: %s%s  (self-headroom=%.3f, external-headroom=%.3f)"
                       (if (:converged? c) "CONVERGED — " "") (:verdict c)
                       (:self-drivable-headroom c) (:external-headroom c)))
      (doseq [b (:blocked-on c)]
        (println (format "   blocked: %s +%.4f (gate=%s)" (name (:lever b)) (:headroom b) (name (:gate b))))))
    (println (format "head-cid=%s…  verify-chain=%s" (subs (str (:head-cid r)) 0 12) (:verify-chain r)))
    r))
