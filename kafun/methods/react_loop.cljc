#!/usr/bin/env bb
;; kafun 花粉 — the remediation-bottleneck SYSTEM-DYNAMICS ReAct beat.
(ns kafun.methods.react-loop
  "react-loop — kafun's remediation-bottleneck ReAct beat over a SYSTEM-DYNAMICS forecast of
  readiness (ADR-2607102230; on ADR-2606211712 gate + ADR-2606201200 ibuki react-loop shape).

  Wraps the pipeline-bottleneck lens (`remediate/remediation-bottlenecks`) and the readiness
  stock-flow model (`dynamics.cljc`) in a ReAct cycle whose OBJECTIVE is forecasting which
  named external input (無花粉苗木 supply / landowner consent) unblocks the most restoration
  value soonest, and whose CONSTRAINT is kafun's OWN G1–G8 gates (a forecast can never make a
  replant=false / carbon-positive stand advance — every candidate is re-scored through the
  UNCHANGED `remediate/verdict`, never a duplicate or relaxed gate):

    SENSE       fold this loop's OWN ledger (kotoba.cljc) for the last persisted readiness
                stock + the PRIOR beat's forecast, then advance the stock by one step of the
                REALIZED (representative, R0) readiness rate
    ORIENT      surprise = |prior beat's forecast cumulative-unblocked − this beat's actually
                realized cumulative-unblocked| (leak-free: the forecast was recorded BEFORE the
                realized progress was known)
    HYPOTHESIZE candidate readiness-rate scenarios restricted to the CURRENT binding
                constraint (a charter-clean fixed catalog — never a free-form intervention;
                nil binding ⇒ no candidates, a monitor-only beat)
    REVIEW      a scenario may never REGRESS the pipeline (gain < 0); every candidate already
                only exists by construction as a re-score through `remediate/verdict`, so a
                :refuse/:protected-selective route is structurally unreachable by relaxing
                readiness alone (G1/G4 hold through the forecast, not just the live assessment)
    RANK        deterministic ranking by kaizen-WEIGHTED efficiency (Δunblocked ÷ assumed rate)
    EVOLVE      recombine the top-2 scenarios (jointly relaxing both bottlenecks) when that
                beats the single-bottleneck winner
    ACT         the top-reviewed scenario becomes a PRE-REGISTERED forecast — one more step of
                the stock-flow, persisted BEFORE the next beat's outcome is known (leak-free);
                this is a PROPOSAL routed to the relevant downstream actor (sanae for supply /
                musubi for consent, mirroring `ie_flow.cljc`'s downstream map) — kafun still
                supplies no sapling and grants no consent itself (G5 unchanged end-to-end)
    OBSERVE     next beat: compare the PRIOR forecast against what the ledger's realized stock
                now actually shows
    LEARN       proper-score the forecast (normalized abs-error) → update the per-scenario
                kaizen weight (bounded [weight-floor, weight-ceil])
    PERSIST     append one content-addressed tx to this loop's OWN ledger (kotoba.cljc) —
                idempotent-by-content, verify-chain tamper-evident, resume-safe, no-server-key

  G5 unchanged end-to-end: this loop never supplies sapling, grants consent, or cuts/plants —
  it only forecasts a readiness stock and proposes a ROUTE. Deterministic: logical beat = this
  loop's OWN ledger length (no wall clock); the candidate catalog + the representative realized
  rate are BOTH fixed enumerations/functions of the beat index — never sampled, never a
  function of kafun's own prior choice (so a proposal is never conflated with the outside
  world's actual pace)."
  (:require [clojure.string :as str]
            [kafun.methods.remediate :as rem]
            [kafun.methods.dynamics :as dyn]
            [kafun.methods.kotoba :as k]
            #?(:clj [clojure.edn :as edn])))

(def default-log "20-actors/kafun/data/persisted/kafun.react-loop.kotoba.edn")

(def learning-rate 0.4)
(def weight-floor 0.25)
(def weight-ceil 2.0)

(def scenario-catalog
  "Fixed, charter-clean catalog of readiness-rate scenarios — kafun MODELS these rates, never
  supplies them (G5). Each targets exactly one of the two named pipeline bottlenecks."
  [{:id :supply-slow  :target :await-sapling-supply :supply-rate 0.1  :consent-rate 0.0}
   {:id :supply-fast  :target :await-sapling-supply :supply-rate 0.34 :consent-rate 0.0}
   {:id :consent-slow :target :await-consent         :supply-rate 0.0 :consent-rate 0.1}
   {:id :consent-fast :target :await-consent         :supply-rate 0.0 :consent-rate 0.34}])

;; ── SENSE: fold this loop's OWN ledger ───────────────────────────────────────

(defn- round3 [x] (/ (Math/round (* (double x) 1000.0)) 1000.0))

(defn- baseline-unblocked
  "Stands already reaching :reforest-priority with ZERO accumulated readiness — the true
  beat-0 baseline (never conflated with `unblock-potential`, which asks a DIFFERENT question:
  how many WOULD unblock if a blocker were fully resolved)."
  [stands]
  (count (filter #(= :reforest-priority (:verdict (rem/verdict %))) stands)))

(defn- entity-attrs
  "Fold the LAST tx's datoms for one entity into an attr→value map. kafun's EDN-log convention:
  attrs are raw \":ns/name\" STRINGS (kotoba.cljc's minimal reader keeps a leading-`:` token as
  a string, never a keyword) — this mirrors `remediate.cljc`/`ie_flow.cljc` datom access."
  [txs entity]
  (if (empty? txs)
    {}
    (let [ds (get (last txs) ":tx/datoms")]
      (reduce (fn [m [_op e a v]] (if (= e entity) (assoc m a v) m)) {} ds))))

(defn- read-weights [attrs]
  (reduce-kv (fn [w a v]
               (if (str/starts-with? (str a) ":kafun.react.weight/")
                 (assoc w (subs (str a) (count ":kafun.react.weight/")) (double v))
                 w))
             {} attrs))

(defn- read-keyword-attr [attrs k]
  (when-let [s (get attrs k)]
    (when (not= s ":none") (keyword (subs s 1)))))

(defn read-state
  "Fold this loop's OWN ledger for the last persisted readiness stock + the prior beat's
  forecast + the learned per-scenario weights. Empty ledger (beat 0) → the zero stock (baseline
  = stands already unblocked with no readiness), no prior forecast."
  [txs stands]
  (let [attrs (entity-attrs txs "kafun:react-loop")]
    (if (empty? attrs)
      {:beat (count txs)
       :stock {:supply-level 0.0 :consent-level 0.0 :cumulative-unblocked (baseline-unblocked stands)}
       :prior-chosen nil :prior-predicted nil :weights {}}
      {:beat (count txs)
       :stock {:supply-level (double (get attrs ":kafun.react/supply-level" 0.0))
               :consent-level (double (get attrs ":kafun.react/consent-level" 0.0))
               :cumulative-unblocked (long (get attrs ":kafun.react/cumulative-unblocked" 0))}
       :prior-chosen (read-keyword-attr attrs ":kafun.react/chosen")
       :prior-predicted (when-let [p (get attrs ":kafun.react/predicted-unblocked")] (long p))
       :weights (read-weights attrs)})))

;; ── the REALIZED world (R0 representative stand-in for live telemetry) ───────

(defn representative-progress
  "A deterministic, modest per-beat REALIZED readiness-rate stand-in for live telemetry (R0; a
  real sapling-nursery/consent-registry feed is R1+, G7-gated exactly like kafun's own
  canopy-detection roadmap, MATURITY.md). A function of the beat index ONLY — never of kafun's
  own chosen scenario, so kafun's PROPOSAL is never conflated with the outside world's ACTUAL
  pace (G5: kafun routes information; it does not cause a landowner or nursery to act). Pure."
  [beatn]
  {:supply-rate (/ (double (mod beatn 5)) 20.0)
   :consent-rate (/ (double (mod (+ beatn 2) 7)) 20.0)})

;; ── OBSERVE + LEARN (leak-free proper scoring) ───────────────────────────────

(defn score-outcome
  "Proper-score the PRIOR beat's forecast against what the ledger now actually shows. Error is
  normalized by stand count so the score stays comparable across seed sizes. Pure."
  [predicted actual n-stands]
  (let [err (Math/abs (double (- (long actual) (long predicted))))
        norm (/ err (max 1.0 (double n-stands)))]
    {:predicted (long predicted) :actual (long actual) :abs-error err :score (max 0.0 (- 1.0 norm))}))

(defn update-weight
  "Kaizen update: a scenario whose forecast verified (score>0.5) is trusted more next time, a
  falsified one less, bounded to [weight-floor, weight-ceil]. Pure."
  [weights id score]
  (let [w (double (get weights id 1.0))
        w' (-> (+ w (* learning-rate (- (double score) 0.5))) (max weight-floor) (min weight-ceil))]
    (assoc weights id w')))

;; ── HYPOTHESIZE → REVIEW → RANK → EVOLVE ─────────────────────────────────────

(defn candidates-for
  "The charter-clean catalog restricted to the CURRENT binding constraint — kafun only proposes
  a scenario that targets the bottleneck actually jamming the pipeline, never a free-form
  intervention. nil binding (nothing stalled) ⇒ no candidates (a monitor-only beat)."
  [binding]
  (if binding (filterv #(= (:target %) binding) scenario-catalog) []))

(defn hypothesize
  "Score every candidate scenario by ONE step of the readiness stock-flow from `stock`:
  Δcumulative-unblocked ÷ the assumed rate-cost (efficiency). Pure; `stands` never mutated (G5)."
  [stock stands candidates]
  (mapv (fn [{:keys [supply-rate consent-rate] :as sc}]
          (let [next (dyn/step-system stock {:supply-rate supply-rate :consent-rate consent-rate} stands)
                gain (- (long (:cumulative-unblocked next)) (long (:cumulative-unblocked stock 0)))
                cost (max 1.0e-6 (+ (double supply-rate) (double consent-rate)))]
            (assoc sc :next next :gain gain :cost cost :efficiency (/ (double gain) cost))))
        candidates))

(defn review
  "Charter gate: a scenario may never REGRESS the pipeline. Structurally this should never
  trigger (readiness is monotone non-decreasing and `rem/verdict` never routes a stand BACKWARD
  out of :reforest-priority) — the filter defends the invariant rather than assuming it."
  [hyps]
  (filterv #(>= (:gain %) 0) hyps))

(defn rank
  "Deterministic ranking by kaizen-WEIGHTED efficiency, ties broken by :id name."
  [weights hyps]
  (->> hyps
       (mapv #(assoc % :weighted-efficiency (* (:efficiency %) (double (get weights (name (:id %)) 1.0)))))
       (sort-by (juxt (comp - :weighted-efficiency) (comp name :id)))
       vec))

(defn evolve
  "Recombine the top-2 ranked scenarios when they target DIFFERENT bottlenecks — a joint
  scenario relaxing both at once, kept only if its efficiency beats the current top. Pure."
  [stock stands ranked]
  (cond
    (empty? ranked) nil
    (< (count ranked) 2) (first ranked)
    :else
    (let [[a b] ranked]
      (if (= (:target a) (:target b))
        a
        (let [joint {:id :joint-evolved :target :joint
                     :supply-rate (max (:supply-rate a) (:supply-rate b))
                     :consent-rate (max (:consent-rate a) (:consent-rate b))}
              scored (first (hypothesize stock stands [joint]))]
          (if (> (:efficiency scored) (:efficiency a)) scored a))))))

;; ── the beat (pure plan; I/O only in `persist!`/`beat`) ──────────────────────

(defn plan
  "PURE core of one beat. `txs` = this loop's OWN ledger (the priors folded forward); `stands` =
  the fixed assessment population. No I/O, no randomness."
  [{:keys [txs stands]}]
  (let [prior (read-state txs stands)
        beatn (:beat prior)
        progress (representative-progress beatn)
        stock' (dyn/step-system (:stock prior) progress stands)
        outcome (when (and (:prior-chosen prior) (:prior-predicted prior))
                  (score-outcome (:prior-predicted prior) (:cumulative-unblocked stock') (count stands)))
        weights (if outcome
                  (update-weight (:weights prior) (name (:prior-chosen prior)) (:score outcome))
                  (:weights prior))
        binding (:binding-constraint (rem/remediation-bottlenecks (dyn/readiness-snapshot stock' stands)))
        cands (candidates-for binding)
        hyps (review (hypothesize stock' stands cands))
        ranked (rank weights hyps)
        chosen (evolve stock' stands ranked)
        predicted-unblocked (when chosen (:cumulative-unblocked (:next chosen)))]
    {:beat beatn
     :binding-constraint binding
     :stock stock'
     :outcome outcome
     :surprise (:abs-error outcome)
     :weights weights
     :generated (count cands)
     :surviving (count hyps)
     :ranked ranked
     :chosen chosen
     :predicted-unblocked predicted-unblocked}))

;; ── projection to datoms ──────────────────────────────────────────────────────

(defn- state-datoms [{:keys [beat stock weights chosen predicted-unblocked binding-constraint]}]
  (let [e "kafun:react-loop"
        base [(k/add e ":kafun.react/beat" (long beat))
              (k/add e ":kafun.react/supply-level" (round3 (:supply-level stock)))
              (k/add e ":kafun.react/consent-level" (round3 (:consent-level stock)))
              (k/add e ":kafun.react/cumulative-unblocked" (long (:cumulative-unblocked stock)))
              (k/add e ":kafun.react/binding-constraint" (str (or binding-constraint :none)))
              (k/add e ":kafun.react/chosen" (str (or (:id chosen) :none)))]
        pred (when predicted-unblocked [(k/add e ":kafun.react/predicted-unblocked" (long predicted-unblocked))])
        wds (vec (for [[id w] (sort-by key weights)] (k/add e (str ":kafun.react.weight/" id) (round3 w))))]
    (vec (concat base pred wds))))

(defn- beat-audit-datoms [{:keys [beat binding-constraint generated surviving chosen outcome surprise]}]
  (let [e (str "kafun:react-beat-" beat)
        base [(k/add e ":react-beat/beat" (long beat))
              (k/add e ":react-beat/binding-constraint" (str (or binding-constraint :none)))
              (k/add e ":react-beat/candidates" (long generated))
              (k/add e ":react-beat/surviving" (long surviving))
              (k/add e ":react-beat/chosen" (str (or (:id chosen) :none)))
              (k/add e ":react-beat/chosen-efficiency" (round3 (or (:efficiency chosen) 0.0)))]
        obs (when outcome
              [(k/add e ":react-beat/prior-predicted" (long (:predicted outcome)))
               (k/add e ":react-beat/prior-actual" (long (:actual outcome)))
               (k/add e ":react-beat/surprise" (round3 surprise))
               (k/add e ":react-beat/outcome-score" (round3 (:score outcome)))])]
    (vec (concat base obs))))

(defn project
  "Project the plan into one beat's datoms (deterministic, ordered)."
  [p]
  (vec (concat (state-datoms p) (beat-audit-datoms p))))

;; ── persist (idempotent-by-content) ──────────────────────────────────────────

(defn persist!
  "Append one beat's datoms to this loop's OWN commit-DAG, idempotent-by-content. Returns
  {:head :appended :reason :count}."
  [datoms {:keys [tx-id as-of log-path]}]
  (let [log-path (or log-path default-log)
        prev (k/head-cid log-path)
        last-ds (let [txs (k/read-log log-path)] (when (seq txs) (get (last txs) ":tx/datoms")))
        base {:count (count datoms) :head prev}]
    (if (= datoms last-ds)
      (assoc base :appended false :reason :no-change)
      (let [tx (k/make-tx datoms tx-id as-of prev)
            head (k/append-tx tx log-path)]
        (assoc base :appended true :reason nil :head head)))))

(defn beat
  "Run one full SD react-loop beat and persist it. opts:
    :stands    fixed assessment population (required)
    :tx-id :as-of  caller-supplied (no wall clock)
    :log-path  this loop's OWN ledger (default-log)
  Returns a compact status map."
  [{:keys [stands tx-id as-of log-path]}]
  (let [log-path (or log-path default-log)
        txs (k/read-log log-path)
        p (plan {:txs txs :stands stands})
        ds (project p)
        persisted (persist! ds {:tx-id (or tx-id (str "kafun-react-" (:beat p)))
                                 :as-of (or as-of (str "as-of:" (:beat p)))
                                 :log-path log-path})]
    {:beat (:beat p)
     :binding-constraint (:binding-constraint p)
     :supply-level (get-in p [:stock :supply-level])
     :consent-level (get-in p [:stock :consent-level])
     :cumulative-unblocked (get-in p [:stock :cumulative-unblocked])
     :chosen (get-in p [:chosen :id])
     :predicted-unblocked (:predicted-unblocked p)
     :outcome-score (get-in p [:outcome :score])
     :surprise (:surprise p)
     :appended (:appended persisted)
     :reason (:reason persisted)
     :head (:head persisted)}))

;; ── CLI (bb) ──────────────────────────────────────────────────────────────────

#?(:clj
   (defn -main [& args]
     (let [seed (or (first args) "20-actors/kafun/kotoba/seed.edn")
           log-path (or (second args) default-log)
           stands (vec (filter #(= (:type %) :stand) (edn/read-string (slurp seed))))
           r (beat {:stands stands :tx-id "kafun-react-manual" :as-of "manual" :log-path log-path})]
       (println (str "kafun react-loop beat #" (:beat r)
                     " binding=" (:binding-constraint r)
                     " supply=" (:supply-level r) " consent=" (:consent-level r)
                     " unblocked=" (:cumulative-unblocked r)
                     " -> " (:chosen r) " (predicted-next=" (:predicted-unblocked r) ")"
                     (when (:outcome-score r) (str " | prior-score=" (format "%.2f" (double (:outcome-score r)))))
                     " | appended=" (:appended r) (when (:reason r) (str " (" (:reason r) ")"))))
       (when (nil? (:binding-constraint r))
         (println "  no bottleneck stalled this beat -- nothing to propose (monitor-only)")))))

#?(:clj
   (when (= *file* (System/getProperty "babashka.file"))
     (apply -main *command-line-args*)))
