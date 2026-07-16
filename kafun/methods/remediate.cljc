#!/usr/bin/env bb
;; kafun 花粉 — the 花粉撲滅 remediation GATE (clj-native, pure stdlib).
(ns kafun.methods.remediate
  "kafun 花粉 — the 花粉撲滅 remediation gate (ADR-2606211712, the clj-native
  Tier-B actor-ization of ADR-2605100100 + 2605210928).

  撲滅 = ecological RESTORATION (主伐再造林), NEVER deforestation-for-profit.
  A forest STAND is routed for remediation ONLY by passing this gate.
  ASSESSMENT + R0 DESIGN ONLY — kafun never cuts and never plants.

  Edge-primary pollen-source CONCENTRATION is scored ON READ:
    pollen-burden = min(1, area-ha/10000) · emission-density · (0.5 + 0.5·exposed-pop-weight)
  and routed to remediation. The verdict decides WHAT to do with a stand.

  verdict ∈ {:refuse :await-consent :protected-selective :await-sapling-supply
             :reforest-priority :monitor}
  Order (hard refusals first — a 撲滅 that is not restoration is unrepresentable):
    1. replant=false (主伐 without 再造林)         → :refuse :clearcut-without-reforest (G1/G4)
    2. carbon :net-positive (after replant)        → :refuse :carbon-positive          (G4/§2(d))
    3. consent absent                              → :await-consent                    (G3)
    4. protected (watershed/steep/habitat)         → :protected-selective              (G1, never 皆伐)
    5. sapling-supply :none (無花粉苗木 L1-1)       → :await-sapling-supply
    6. burden ≥ 0.3 AND reforest-viability ≥ 0.5   → :reforest-priority                (L3-1)
    7. else                                        → :monitor

  The gate REFUSES non-restorative cuts STRUCTURALLY (proven by tests): there is
  no path by which a clearcut-without-reforest or net-carbon-positive stand returns
  a remediation permit. There is NO actuation method — kafun cannot cut or plant."
  (:require [clojure.string :as str]
            [kafun.methods.kafun-edn :as ke]))

;; ── pure scoring ──────────────────────────────────────────────────────────────

(def ^:private burden-threshold 0.3)
(def ^:private viability-threshold 0.5)
(def ^:private area-scale 10000.0)

(defn pollen-burden
  "Edge-primary pollen-source concentration ∈ 0..1 — bigger stand × more emission
  × more exposed people = more pollen on more humans. Scored on read; the VERDICT
  is decided by the gate, not this score."
  [s]
  (let [area-factor (min 1.0 (/ (double (or (:area-ha s) 0)) area-scale))
        emit (double (or (:emission-density s) 0))
        pop (double (or (:exposed-pop-weight s) 0))]
    (max 0.0 (min 1.0 (* area-factor emit (+ 0.5 (* 0.5 pop)))))))

(defn remediation-priority
  "Priority for ranking the reforestation worklist ∈ 0..1 — burden weighted by how
  viably it can actually be replanted and whether consent is in hand. A ranking aid;
  the VERDICT is the gate's, not this number."
  [s]
  (let [b (pollen-burden s)
        v (double (or (:reforest-viability s) 0))
        consent-w (if (:consent s) 1.0 0.3)]
    (max 0.0 (min 1.0 (* b v consent-w)))))

(defn verdict
  "The 花粉撲滅 remediation verdict for a stand. Returns {:verdict … :reason … :route …}."
  [s]
  (let [b (pollen-burden s)
        v (double (or (:reforest-viability s) 0))]
    (cond
      (not (:replant s))
      {:verdict :refuse :reason :clearcut-without-reforest}

      (= (:carbon s) :net-positive)
      {:verdict :refuse :reason :carbon-positive}

      (not (:consent s))
      {:verdict :await-consent}

      (:protected s)
      {:verdict :protected-selective :route :gradual-selective}

      (= (:sapling-supply s) :none)
      {:verdict :await-sapling-supply :route :mubunka-nae}

      (and (>= b burden-threshold) (>= v viability-threshold))
      {:verdict :reforest-priority :route :shubatsu-saizourin}

      :else
      {:verdict :monitor})))

(defn assess-stand [s]
  (let [vd (verdict s)]
    (merge {"id" (:id s)
            "name" (:name s)
            "species" (:species s)
            "prefecture" (:prefecture s)
            "pollen_burden" (pollen-burden s)
            "remediation_priority" (remediation-priority s)
            "verdict" (:verdict vd)}
           (when (:reason vd) {"reason" (:reason vd)})
           (when (:route vd) {"route" (:route vd)}))))

(defn assess [stands]
  (let [rows (mapv assess-stand stands)
        tally (frequencies (map #(get % "verdict") rows))]
    {"stands" rows
     "tally" tally
     "reforest_priority" (count (filter #(= :reforest-priority (get % "verdict")) rows))
     "awaiting_supply"   (count (filter #(= :await-sapling-supply (get % "verdict")) rows))
     "awaiting_consent"  (count (filter #(= :await-consent (get % "verdict")) rows))
     "protected"         (count (filter #(= :protected-selective (get % "verdict")) rows))
     "refused"           (count (filter #(= :refuse (get % "verdict")) rows))}))

(def ^:private blocker-relax
  "How resolving each pipeline blocker is MODELLED for the counterfactual — the external input that,
  once supplied, unblocks a stalled stand. :await-sapling-supply is the 無花粉苗木 L1-1 隘路 (→ sanae);
  :await-consent is land-sovereignty consent (→ musubi). Modelling only — kafun supplies neither."
  {:await-sapling-supply #(assoc % :sapling-supply :ok)
   :await-consent #(assoc % :consent true)})

(defn remediation-bottlenecks
  "Pipeline bottleneck view over a set of stands: tallies the verdict each reaches, then surfaces
  which BLOCKING stage (a stand stalled waiting on an external input — :await-sapling-supply, the
  無花粉苗木 L1-1 隘路, or :await-consent) holds the most stands, plus the COUNTERFACTUAL value of
  resolving it: how many of the binding constraint's stalled stands would advance to
  :reforest-priority (主伐再造林) once that input is supplied. The per-stand verdict says what to do
  with ONE stand and `assess` tallies the distribution; this names WHERE 再造林 is jammed and what
  un-jamming it buys — the constraint to clear first (→ sanae for L1-1, musubi for consent). The
  counterfactual is pure ASSESSMENT (a hypothetical re-scoring, never actuation — kafun supplies no
  sapling and grants no consent, G5); aggregate (verdict counts, no per-owner detail, G2). Returns
  {:tally {verdict count} :blockers [[verdict count] …] :binding-constraint verdict|nil
   :unblock-potential n|nil}."
  [stands]
  (let [tally (frequencies (map #(:verdict (verdict %)) stands))
        blockers (->> tally
                      (filter (fn [[v _]] (blocker-relax v)))
                      (sort-by (fn [[v c]] [(- c) (str v)]))
                      (mapv vec))
        binding (ffirst blockers)
        unblock-potential (when binding
                            (let [relax (blocker-relax binding)]
                              (->> stands
                                   (filter #(= binding (:verdict (verdict %))))
                                   (filter #(= :reforest-priority (:verdict (verdict (relax %)))))
                                   count)))]
    {:tally tally
     :blockers blockers
     :binding-constraint binding
     :unblock-potential unblock-potential}))

;; ── datom emission (append-only EAVT; flagged) ──────────────────────────────

(defn- add [e a v] [":db/add" e a v])
(defn- round3 [x] (/ (Math/round (* (double x) 1000.0)) 1000.0))

(defn datoms
  "Append-only EAVT datom VECTORS for the remediation verdicts (the persistable
  form; render-datoms stringifies these; autorun/kotoba append these to the ledger).
  Every datom flagged :kafun/derived + :kafun/sourcing :synthetic. No :kafun/actuate
  / :kafun/clearcut / :kafun.person/health attribute is ever emitted (G1/G2/G5)."
  [{:strs [stands]}]
  (vec
   (mapcat
    (fn [r]
      (let [e (str "kafun-stand:" (get r "id"))]
        (concat
         [(add e ":kafun.stand/species" (str (get r "species")))
          (add e ":kafun.rem/pollen-burden" (round3 (get r "pollen_burden")))
          (add e ":kafun.rem/remediation-priority" (round3 (get r "remediation_priority")))
          (add e ":kafun.rem/verdict" (str (get r "verdict")))]
         (when (get r "reason") [(add e ":kafun.rem/reason" (str (get r "reason")))])
         (when (get r "route")  [(add e ":kafun.rem/route" (str (get r "route")))])
         [(add e ":kafun/sourcing" ":synthetic")
          (add e ":kafun/derived" true)])))
    stands)))

(defn render-datoms
  "EDN string of the remediation-verdict datoms (see `datoms`)."
  [assessment]
  (str "[\n " (str/join "\n " (map pr-str (datoms assessment))) "\n]\n"))

;; ── markdown remediation map (restoration worklist, never a cut-list) ────────

(defn render-report [assessment]
  (let [rows (->> (get assessment "stands")
                  (sort-by #(- (get % "remediation_priority"))))]
    (str
     "# kafun 花粉 — 花粉撲滅 remediation MAP\n\n"
     "撲滅 = ecological RESTORATION (主伐再造林), NEVER deforestation-for-profit "
     "(ADR-2606211712). Each forest STAND is routed for remediation ONLY by passing "
     "the gate. **ASSESSMENT + R0 DESIGN ONLY — kafun never cuts and never plants.** "
     "A restoration worklist, NEVER a cut-list / target-list. All stands are :synthetic.\n\n"
     "Routes: **" (get assessment "reforest_priority") "** reforest-priority (主伐再造林, L3-1) · **"
     (get assessment "awaiting_supply") "** await 無花粉苗木 (L1-1) · **"
     (get assessment "awaiting_consent") "** await-consent · **"
     (get assessment "protected") "** protected-selective · **"
     (get assessment "refused") "** refused.\n\n"
     "| stand | species | burden | priority | verdict | reason/route |\n"
     "|---|---|---|---|---|---|\n"
     (str/join "\n"
               (for [r rows]
                 (str "| " (get r "name")
                      " | " (name (get r "species"))
                      " | " (round3 (get r "pollen_burden"))
                      " | " (round3 (get r "remediation_priority"))
                      " | " (name (get r "verdict"))
                      " | " (name (or (get r "reason") (get r "route") :-)) " |")))
     "\n\n_Bottlenecks: L1-1 無花粉苗木の量産 (await-sapling-supply) · L3-1 主伐再造林スケール (reforest-priority)._\n"
     "_Routed to: sanae (planting robotics) · inochi (biosphere restoration) · mitate/iyashi (allergic-rhinitis care)._\n"
     "\n_Even :reforest-priority is DESIGN-ONLY — live forestry is the landowner's + operator/Council step, never kafun (G5/G7)._\n")))

;; ── CLI (bb) ────────────────────────────────────────────────────────────────

#?(:clj
   (defn -main [& args]
     (let [seed (or (first args) "20-actors/kafun/kotoba/seed.edn")
           ;; kafun-edn/stands, not a raw read+filter: seed.edn is Datomic/
           ;; Datascript tx-data (Phase 4 EDN datomize) — kafun-edn/classify is
           ;; where the tx-data -> bare-row reconstitution lives.
           ss (ke/stands seed)
           a (assess ss)]
       (println (render-report a))
       (println (str "-- " (count ss) " stands assessed --")))))

#?(:clj
   (when (= *file* (System/getProperty "babashka.file"))
     (apply -main *command-line-args*)))
