#!/usr/bin/env bb
;; tsuchifumi 土踏み — co-scientist: IDENTIFY (特定) + ANALYZE (分析) (clj-native, pure stdlib).
(ns tsuchifumi.methods.coscientist
  "coscientist — tsuchifumi 土踏み reasons about the earthing-EMF gap like a research
  programme, to IDENTIFY (特定) and ANALYZE (分析) what to do and what to study
  (ADR-2606212000, the ibuki co-scientist pattern ADR-2606201200 fitted to a CONTESTED
  domain).

  The Google 'AI co-scientist' shape — Generate → Reflect(Review) → Rank → Evolve →
  Meta-review — over a CHARTER-CLEAN intervention CATALOG (never LLM free-write, so a
  fear/sales/diagnosis 'intervention' is structurally unrepresentable). Deterministic +
  pure (no wall clock, no randomness) → reproducible tournament + content-addressable.

  THE DEFINING MOVE (G2 honesty applied to a research programme):
  candidates split into TWO TRACKS and are ranked by DIFFERENT objectives —
    :action   — a NO-REGRET intervention resting on ≥:emerging evidence. Ranked by
                EXPECTED RELIEF: utility = relief · wellbeing · evidence-weight / cost.
                IDENTIFIED → handed to social.cljc → ossekai (御節介) to carry.
    :research — a CONTESTED hypothesis worth STUDYING (it must NOT be acted on or
                asserted). Ranked by VALUE-OF-INFORMATION: voi = relevance · (1−confidence)
                / cost. IDENTIFIED → handed to suimin/mitooshi for evidence-synthesis.
  A :contested/:anecdotal candidate on the :action track is VETOED (you may STUDY a
  contested claim, never ACT on it) — the safety property that keeps the programme honest.

  CHARTER GATES (enforced in `review`, tested):
    G-mechanism    only `aligned-mechanisms` may enter; fear/sales/clinical/personal-
                   surveillance mechanisms are UNREPRESENTABLE (not in the catalog;
                   `review` rejects them if injected).
    G-evidence (G2) an :action candidate must rest on ≥:emerging evidence.
    G-falsifiable  every candidate carries a measurable prediction (no prediction → not science).
    G-non-diagnostic/no-commerce/no-fear — inherited via the mechanism set + social guards.
    G-leash        the identified action is a DRY-RUN proposal carried by ossekai; the
                   identified research is a STUDY request — coscientist never acts."
  (:require [clojure.string :as str]
            [tsuchifumi.methods.analyze :as an]
            [tsuchifumi.methods.tsuchifumi-edn :as te]))

;; ── mechanism vocabulary (closed) ───────────────────────────────────────────
(def aligned-mechanisms
  "The only mechanisms by which tsuchifumi may act on / study the gap — each transparent,
  non-extractive, no-regret or knowledge-building. Outside this set ⇒ cannot enter (G-mechanism)."
  #{"open-publication"          ;; release the relief / system-dynamics map openly
    "greenspace-advocacy"       ;; civic proposal for greenspace + grounded-design standards (institution)
    "outdoor-time-nudge"        ;; no-regret outdoor / barefoot-on-grass nudge (established)
    "evening-light-info"        ;; evening screen-light hygiene info (emerging; → suimin)
    "reciprocal-measurement"    ;; open, symmetric, consented AGGREGATE ambient-EMF measurement
    "evidence-synthesis-request"}) ;; commission a GRADE-rated review of a contested claim (→ suimin/mitooshi)

(def forbidden-mechanisms
  "UNREPRESENTABLE — exactly how a careless earthing/EMF actor would go wrong. The generator
  never emits these and `review` rejects them on sight (G1/G4/G5/G3)."
  #{"fear-marketing" "product-sales" "clinical-claim" "personal-dosimetry-surveillance"
    "engagement-maximizing" "manipulation" "deception" "ad-targeting"})

;; ── the intervention catalog (Generate's deterministic backbone) ─────────────
;; :track ∈ {:action :research}; :tier = resting evidence tier; relief/wellbeing/cost are
;; BASE expectations scaled per-state by `generate`; :prediction makes it falsifiable.
(def catalog
  [{:id "publish-relief-map" :mechanism "open-publication" :track :action :tier :established
    :intervention "openly publish the relief + system-dynamics map to the commons"
    :relief 0.5 :wellbeing 0.4 :cost 1
    :prediction "civic uptake + contributors arrive within the horizon"}
   {:id "greenspace-standard-advocacy" :mechanism "greenspace-advocacy" :track :action :tier :established
    :intervention "civic proposal for public greenspace + grounded-design access standards"
    :relief 0.9 :wellbeing 0.8 :cost 3
    :prediction "municipal greenspace / grounded-design proposals are filed"}
   {:id "outdoor-barefoot-nudge" :mechanism "outdoor-time-nudge" :track :action :tier :established
    :intervention "no-regret nudge: time outdoors / barefoot on safe grass/soil/sand"
    :relief 0.4 :wellbeing 0.7 :cost 1
    :prediction "cohort outdoor-time / soil-contact metric rises"}
   {:id "evening-light-hygiene" :mechanism "evening-light-info" :track :action :tier :emerging
    :intervention "evening screen-light hygiene info, routed to suimin"
    :relief 0.3 :wellbeing 0.6 :cost 1
    :prediction "self-reported sleep-onset improves (suimin)"}
   {:id "open-ambient-emf-measurement" :mechanism "reciprocal-measurement" :track :action :tier :emerging
    :intervention "open, symmetric, consented AGGREGATE ambient-EMF measurement (no personal dosimetry)"
    :relief 0.2 :wellbeing 0.3 :cost 2
    :prediction "established-tier exposure dataset coverage rises"}
   {:id "earthing-benefit-evidence-synthesis" :mechanism "evidence-synthesis-request" :track :research :tier :contested
    :intervention "commission a GRADE-rated synthesis of the earthing-therapy benefit claim"
    :relief 0.0 :wellbeing 0.6 :cost 2
    :prediction "a synthesis resolves / updates the claim's evidence tier"}
   {:id "nonthermal-emf-evidence-synthesis" :mechanism "evidence-synthesis-request" :track :research :tier :contested
    :intervention "commission a synthesis of sub-ICNIRP non-thermal EMF health evidence"
    :relief 0.0 :wellbeing 0.5 :cost 2
    :prediction "a synthesis updates the contested tier"}])

;; ── helpers ──────────────────────────────────────────────────────────────────
(def tier-weights an/tier-weights)
(defn- clamp01 [x] (max 0.0 (min 1.0 (double x))))
(defn- round3 [x] (/ (Math/round (* (double x) 1000.0)) 1000.0))
(defn- tw [t] (get tier-weights t 0.0))

(defn need
  "How acute the institutional gap is ∈ 0..1 — mean population-weighted earthing-deficit.
  Scales the expected relief of every candidate (a big gap makes relief more valuable)."
  [assessment]
  (let [rows (get assessment "regions")]
    (if (empty? rows) 0.5
        (clamp01 (/ (reduce + (map #(* (get % "earthing_deficit") (get % "population_weight")) rows))
                    (count rows))))))

;; ── Generate ─────────────────────────────────────────────────────────────────
(defn generate
  "Produce the candidate hypotheses, relief scaled by the assessment's `need`."
  [assessment]
  (let [n (need assessment) scale (+ 0.5 (* 0.5 n))]
    (mapv (fn [c] (assoc c
                         :relief-scaled (round3 (* (:relief c) scale))
                         :confidence (tw (:tier c))))
          catalog)))

;; ── Reflect / Review (the gates) ─────────────────────────────────────────────
(defn review
  "Charter review of one candidate → {:ok bool :reason kw|nil}."
  [c]
  (cond
    (not (aligned-mechanisms (:mechanism c)))
    {:ok false :reason :forbidden-mechanism}
    (str/blank? (str (:prediction c)))
    {:ok false :reason :not-falsifiable}
    (and (= :action (:track c)) (#{:contested :anecdotal} (:tier c)))
    {:ok false :reason :contested-cannot-be-action}   ; G2 — study it, never act on it
    :else {:ok true :reason nil}))

(defn surviving [cands] (vec (filter #(:ok (review %)) cands)))
(defn vetoed [cands]
  (vec (keep (fn [c] (let [r (review c)] (when-not (:ok r) (assoc c :veto (:reason r))))) cands)))

;; ── Rank (Elo over a track-specific objective) ───────────────────────────────
(defn utility
  "Action objective — EXPECTED RELIEF: relief · wellbeing · evidence-weight / cost."
  [c]
  (round3 (/ (* (:relief-scaled c) (:wellbeing c) (:confidence c)) (max 1 (:cost c)))))

(defn voi
  "Research objective — VALUE OF INFORMATION: relevance · (1−confidence) / cost.
  Study the most-relevant, most-UNcertain claim first (a settled claim has low VoI)."
  [c]
  (round3 (/ (* (:wellbeing c) (- 1.0 (:confidence c))) (max 1 (:cost c)))))

(defn- elo-update [ra rb sa]
  (let [ea (/ 1.0 (+ 1.0 (Math/pow 10 (/ (- rb ra) 400.0))))]
    [(+ ra (* 24 (- sa ea))) (+ rb (* 24 (- (- 1 sa) (- 1 ea))))]))

(defn rank
  "Deterministic round-robin Elo tournament; score = the given objective fn. Returns the
  candidates sorted by final Elo desc, each tagged :elo + :score."
  [cands objective]
  (if (empty? cands) []
      (let [scored (mapv (fn [c] (assoc c :score (objective c))) cands)
            n (count scored)
            init (vec (repeat n 1000.0))
            elos (reduce
                  (fn [el [i j]]
                    (let [si (:score (scored i)) sj (:score (scored j))
                          sa (cond (> si sj) 1.0 (< si sj) 0.0 :else 0.5)
                          [ri rj] (elo-update (el i) (el j) sa)]
                      (assoc el i ri j rj)))
                  init
                  (for [i (range n) j (range n) :when (< i j)] [i j]))]
        (->> (map-indexed (fn [i c] (assoc c :elo (round3 (nth elos i)))) scored)
             (sort-by (juxt #(- (:elo %)) #(- (:score %)) :id))
             vec))))

;; ── Evolve ───────────────────────────────────────────────────────────────────
(defn evolve
  "Synthesize the top-2 ACTION winners into an evolved hybrid candidate — the better
  mechanism, max relief, lifted wellbeing, min cost. Still charter-clean (mechanism from
  the aligned set). Returns nil if < 2 action winners."
  [ranked-action]
  (when (>= (count ranked-action) 2)
    (let [[a b] ranked-action
          c {:id (str (:id a) "+" (:id b)) :mechanism (:mechanism a) :track :action
             :tier (if (= (tw (:tier a)) (max (tw (:tier a)) (tw (:tier b)))) (:tier a) (:tier b))
             :intervention (str (:intervention a) " + " (:intervention b))
             :relief (max (:relief a) (:relief b))
             :wellbeing (round3 (clamp01 (+ (/ (+ (:wellbeing a) (:wellbeing b)) 2.0) 0.05)))
             :cost (max 1 (min (:cost a) (:cost b)))
             :prediction (str (:prediction a) "; " (:prediction b)) :evolved true}
          c (assoc c :relief-scaled (max (:relief-scaled a) (:relief-scaled b))
                   :confidence (max (:confidence a) (:confidence b)))]
      (when (:ok (review c)) c))))

;; ── Meta-review (narration; template fallback, Murakumo is the live leg) ─────
(defn meta-review
  "A words-level summary of the identified hypotheses (template; Murakumo narration is the
  G7-gated live leg, exactly like ibuki). Deterministic."
  [{:keys [action research]}]
  (str "co-scientist meta-review: "
       (if action
         (str "ACT NOW (no-regret, " (name (:tier action)) "): " (:intervention action)
              " — expected-relief utility " (:score action) ". ")
         "no surviving no-regret action this round. ")
       (if research
         (str "STUDY NEXT (contested, do NOT assert/act): " (:intervention research)
              " — value-of-information " (:score research) ".")
         "no open research hypothesis.")))

;; ── IDENTIFY (特定) + ANALYZE (分析) ──────────────────────────────────────────
(defn identify
  "Run the full programme over an analyze assessment. Returns the IDENTIFIED top action +
  top research hypothesis (特定) and the full ANALYSIS (分析): ranked tracks, evolved
  candidate, vetoed set with reasons, and the meta-review."
  [assessment]
  (let [gen (generate assessment)
        surv (surviving gen)
        vet (vetoed gen)
        act (filter #(= :action (:track %)) surv)
        res (filter #(= :research (:track %)) surv)
        ranked-act (rank act utility)
        evolved (evolve ranked-act)
        ranked-act* (if evolved (rank (conj (vec act) evolved) utility) ranked-act)
        ranked-res (rank res voi)
        top-act (first ranked-act*)
        top-res (first ranked-res)
        ident {:action top-act :research top-res}]
    {"identified" {"action" top-act "research" top-res}
     "ranked_action" ranked-act*
     "ranked_research" ranked-res
     "evolved" evolved
     "vetoed" (mapv (fn [c] {"id" (:id c) "mechanism" (:mechanism c) "veto" (:veto c)}) vet)
     "need" (round3 (need assessment))
     "meta_review" (meta-review ident)}))

;; ── datom emission (append-only EAVT; flagged) ───────────────────────────────
(defn- add [e a v] [":db/add" e a v])
(defn datoms
  "EAVT datoms for the IDENTIFIED hypotheses (action + research), each paired with its
  evidence-tier (G2). No forbidden attribute is ever emitted."
  [identification]
  (let [emit (fn [kind c]
               (when c
                 (let [e (str "tsuchifumi-hyp:" kind ":" (:id c))]
                   [(add e ":tsuchifumi.hyp/track" (str (:track c)))
                    (add e ":tsuchifumi.hyp/mechanism" (:mechanism c))
                    (add e ":tsuchifumi.hyp/evidence-tier" (str (:tier c)))
                    (add e ":tsuchifumi.hyp/score" (:score c))
                    (add e ":tsuchifumi.hyp/elo" (:elo c))
                    (add e ":tsuchifumi.hyp/prediction" (:prediction c))
                    (add e ":tsuchifumi/sourcing" ":synthetic")
                    (add e ":tsuchifumi/derived" true)])))]
    (vec (concat (emit "action" (get-in identification ["identified" "action"]))
                 (emit "research" (get-in identification ["identified" "research"]))))))

(defn render-datoms [identification]
  (str "[\n " (str/join "\n " (map pr-str (datoms identification))) "\n]\n"))

;; ── markdown report ──────────────────────────────────────────────────────────
(defn render-report [identification]
  (let [a (get-in identification ["identified" "action"])
        r (get-in identification ["identified" "research"])]
    (str
     "# tsuchifumi 土踏み — co-scientist: IDENTIFY (特定) + ANALYZE (分析)\n\n"
     "Generate → Reflect → Rank → Evolve → Meta-review over a charter-clean catalog "
     "(ADR-2606212000). Two tracks ranked by DIFFERENT objectives (the G2 honesty move): "
     "**:action** = no-regret (≥emerging), ranked by expected relief → ossekai; "
     "**:research** = contested hypothesis, ranked by value-of-information → suimin/mitooshi "
     "(study it, never act on it). need=" (get identification "need") ".\n\n"
     "## 特定 (identified)\n\n"
     "- **ACT NOW**: " (if a (str (:intervention a) "  \n  mechanism=" (:mechanism a)
                                  " · tier=" (name (:tier a)) " · utility=" (:score a)
                                  " · elo=" (:elo a) (when (:evolved a) " · (evolved hybrid)")
                                  "  \n  → routed to **ossekai 御節介** (dry-run, consent-bound)")
                          "— none surviving") "\n"
     "- **STUDY NEXT**: " (if r (str (:intervention r) "  \n  mechanism=" (:mechanism r)
                                     " · tier=" (name (:tier r)) " · VoI=" (:score r)
                                     "  \n  → routed to **suimin / mitooshi** (evidence-synthesis; NOT asserted)")
                             "— none open") "\n\n"
     "## 分析 — action ranking (by expected relief)\n\n"
     "| hypothesis | mechanism | tier | utility | elo |\n|---|---|---|---|---|\n"
     (str/join "\n" (for [c (get identification "ranked_action")]
                      (str "| " (:id c) " | " (:mechanism c) " | " (name (:tier c))
                           " | " (:score c) " | " (:elo c) " |")))
     "\n\n## 分析 — research ranking (by value-of-information)\n\n"
     "| hypothesis | tier | VoI | elo |\n|---|---|---|---|\n"
     (str/join "\n" (for [c (get identification "ranked_research")]
                      (str "| " (:id c) " | " (name (:tier c)) " | " (:score c) " | " (:elo c) " |")))
     "\n\n## vetoed (charter review)\n\n"
     (if (seq (get identification "vetoed"))
       (str/join "\n" (for [v (get identification "vetoed")]
                        (str "- " (get v "id") " (" (get v "mechanism") ") → "
                             (name (get v "veto")))))
       "_(none)_")
     "\n\n_" (get identification "meta_review") "_\n")))

;; ── CLI (bb) ─────────────────────────────────────────────────────────────────
#?(:clj
   (defn -main [& args]
     (let [seed (or (first args) "20-actors/tsuchifumi/kotoba/seed.edn")
           rows (te/reconstitute-rows (clojure.edn/read-string (slurp seed)))
           regions (vec (filter #(= (:type %) :region) rows))
           evidence (vec (filter #(= (:type %) :evidence) rows))
           assessment (an/assess regions evidence)
           ident (identify assessment)]
       (println (render-report ident)))))

#?(:clj
   (when (= *file* (System/getProperty "babashka.file"))
     (apply -main *command-line-args*)))
