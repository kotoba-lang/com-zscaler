#!/usr/bin/env bb
;; iriai 入会 — the lifeline-commons INFRA gate (System-of-Systems, clj-native, pure stdlib).
(ns iriai.methods.infra
  "iriai 入会 — the lifeline-commons INFRA layer (ADR-2606272200).

  The System-of-Systems synthesizer over the four lifeline (ライフライン) domains —
  電気 (hikari 光) · 水道 (mizuho 水穂) · ガス (kamado 竈) · 通信 (noroshi 烽 / tsutae 伝え) —
  the way kaname 要 / amime 網目 synthesize across single-domain mirrors. iriai does NOT
  produce a lifeline (the producer actors do); it MEASURES, per region × lifeline, the
  COMMONS-GAP (who lacks an essential lifeline = the §1.16 social-security reach gap) and
  the RESILIENCE (single-source SPOF / N-1 contingency), and ROUTES each cell to a commons
  action. A COVERAGE + RESILIENCE MAP, never a target-list, never a shut-off list (G1).

  Edge-primary commons-gap is scored ON READ:
    coverage   = served-pop / total-pop
    commons-gap = (1 − coverage) · essentiality[lifeline] · (0.5 + 0.5·vulnerability)
  essentiality (disclosed, life-first): 水 1.0 · 電 0.9 · 通信 0.7 · ガス 0.6.

  verdict ∈ {:await-consent :provision :reinforce :redundancy :maintain :monitor}
  Order (consent gates every BUILD; a lifeline is never WITHHELD — there is no deny route):
    1. action-needed AND consent absent          → :await-consent  (G3 land/community sovereignty)
    2. disaster-degraded (coverage below baseline)→ :reinforce      (restore, not greenfield)
    3. commons-gap ≥ 0.30                          → :provision      (close the §1.16 reach gap)
    4. single-source OR N-1 margin < 0             → :redundancy     (no SPOF for a lifeline)
    5. coverage ≥ 0.85 AND resilient               → :maintain
    6. else                                        → :monitor

  ASSESSMENT + R0 DESIGN ONLY — iriai never energizes a grid, opens a valve, ignites gas,
  or activates a link (G5). Live actuation is the producer actor's + operator/Council step.
  There is NO :iriai/shutoff / :iriai/disconnect / :iriai/actuate attribute (G1/G5): a
  lifeline is a COMMONS right of use, never withheld as leverage."
  (:require [clojure.string :as str]))

;; ── disclosed constants (life-first essentiality; aggregate vulnerability mix) ──
(def essentiality
  "Disclosed essentiality weight per lifeline (0..1). Water first (life), then power,
  then telecom (modern participation), then road access, then gas (largely substitutable for
  cooking/heat). Road (道路) joins as a first-class commons lifeline (ADR-2606280900)."
  {:water 1.0 :electric 0.9 :telecom 0.7 :road 0.65 :gas 0.6})

(def ^:private provision-threshold 0.30)
(def ^:private adequate-coverage 0.85)

(def source-actor
  "Which producer actor sources each lifeline (iriai composes, never produces).
  Road (道路) is built/maintained by tatekata (construction) + the kuni-umi robotics fleet."
  {:electric "hikari" :water "mizuho" :gas "kamado" :telecom "noroshi" :road "tatekata"})

;; ── pure scoring (on read) ─────────────────────────────────────────────────────
(defn coverage [c]
  (let [tot (double (or (:total-pop c) 0))]
    (if (pos? tot) (max 0.0 (min 1.0 (/ (double (or (:served-pop c) 0)) tot))) 0.0)))

(defn gap-pop [c]
  (max 0 (- (long (or (:total-pop c) 0)) (long (or (:served-pop c) 0)))))

(defn commons-gap
  "Edge-primary commons-gap ∈ 0..1 — how much essential lifeline is MISSING, weighted
  by how essential it is and how vulnerable the region is. Bigger = more humans without
  an essential lifeline. Scored on read; the VERDICT is the gate's, not this score."
  [c]
  (let [cov (coverage c)
        ess (double (or (essentiality (:lifeline c)) 0.5))
        vuln (double (or (:vulnerability c) 0.5))]
    (max 0.0 (min 1.0 (* (- 1.0 cov) ess (+ 0.5 (* 0.5 vuln)))))))

(defn single-source? [c] (boolean (:single-source? c)))
(defn n1-margin [c] (double (or (:n1-margin c) 0.0)))
(defn resilient? [c] (and (not (single-source? c)) (>= (n1-margin c) 0.0)))

(defn- action-needed? [c]
  (or (>= (commons-gap c) provision-threshold)
      (boolean (:disaster-degraded? c))
      (single-source? c)
      (neg? (n1-margin c))))

(defn verdict
  "The lifeline-commons verdict for one region × lifeline cell.
  Returns {:verdict … :route …}."
  [c]
  (let [g (commons-gap c) cov (coverage c)]
    (cond
      (and (action-needed? c) (not (:consent c)))
      {:verdict :await-consent}

      (:disaster-degraded? c)
      {:verdict :reinforce :route :restore-to-baseline}

      (>= g provision-threshold)
      {:verdict :provision :route :close-reach-gap}

      (or (single-source? c) (neg? (n1-margin c)))
      {:verdict :redundancy :route :remove-spof}

      (and (>= cov adequate-coverage) (resilient? c))
      {:verdict :maintain}

      :else
      {:verdict :monitor})))

(defn assess-cell [c]
  (let [vd (verdict c)]
    (merge {"region" (:region c)
            "region_name" (:region-name c)
            "lifeline" (:lifeline c)
            "source" (or (source-actor (:lifeline c)) "?")
            "coverage" (coverage c)
            "gap_pop" (gap-pop c)
            "commons_gap" (commons-gap c)
            "single_source" (single-source? c)
            "n1_margin" (n1-margin c)
            "verdict" (:verdict vd)}
           (when (:route vd) {"route" (:route vd)}))))

(defn assess
  "Assess every lifeline-cell. Returns a map with per-cell rows + commons tallies."
  [cells]
  (let [rows (mapv assess-cell cells)
        tally (frequencies (map #(get % "verdict") rows))
        by-lifeline (reduce (fn [m r]
                              (update m (get r "lifeline")
                                      (fnil conj []) (get r "verdict")))
                            {} rows)]
    {"cells" rows
     "tally" tally
     "by_lifeline" (into {} (map (fn [[lf vs]] [lf (frequencies vs)]) by-lifeline))
     "provision"   (count (filter #(= :provision  (get % "verdict")) rows))
     "reinforce"   (count (filter #(= :reinforce  (get % "verdict")) rows))
     "redundancy"  (count (filter #(= :redundancy (get % "verdict")) rows))
     "await_consent" (count (filter #(= :await-consent (get % "verdict")) rows))
     "maintain"    (count (filter #(= :maintain   (get % "verdict")) rows))
     "monitor"     (count (filter #(= :monitor    (get % "verdict")) rows))
     "unserved_pop" (reduce + 0 (map #(get % "gap_pop") rows))}))

;; ── datom emission (append-only EAVT; flagged) ─────────────────────────────────
(defn- add [e a v] [":db/add" e a v])
(defn- round3 [x] (/ (Math/round (* (double x) 1000.0)) 1000.0))

(defn datoms
  "Append-only EAVT datom VECTORS for the infra coverage/resilience verdicts.
  Every datom flagged :iriai/derived + :iriai/sourcing :synthetic. NO
  :iriai/shutoff / :iriai/disconnect / :iriai/actuate / :iriai.person/* attribute
  is ever emitted (G1/G5) — a coverage MAP, never a shut-off list."
  [{:strs [cells]}]
  (vec
   (mapcat
    (fn [r]
      (let [e (str "iriai-cell:" (get r "region") ":" (name (get r "lifeline")))]
        (concat
         [(add e ":iriai.cell/region" (str (get r "region")))
          (add e ":iriai.cell/lifeline" (str (get r "lifeline")))
          (add e ":iriai.cell/source" (str (get r "source")))
          (add e ":iriai.infra/coverage" (round3 (get r "coverage")))
          (add e ":iriai.infra/commons-gap" (round3 (get r "commons_gap")))
          (add e ":iriai.infra/unserved-pop" (long (get r "gap_pop")))
          (add e ":iriai.infra/single-source" (boolean (get r "single_source")))
          (add e ":iriai.infra/n1-margin" (round3 (get r "n1_margin")))
          (add e ":iriai.infra/verdict" (str (get r "verdict")))]
         (when (get r "route") [(add e ":iriai.infra/route" (str (get r "route")))])
         [(add e ":iriai/sourcing" ":synthetic")
          (add e ":iriai/derived" true)])))
    cells)))

(defn render-datoms [assessment]
  (str "[\n " (str/join "\n " (map pr-str (datoms assessment))) "\n]\n"))

;; ── markdown commons map (coverage/resilience, never a shut-off list) ──────────
(defn render-report [assessment]
  (let [rows (->> (get assessment "cells")
                  (sort-by #(- (get % "commons_gap"))))]
    (str
     "# iriai 入会 — lifeline-commons COVERAGE + RESILIENCE map\n\n"
     "入会 (iriai) = the traditional COMMONS. The lifelines (電気/水道/ガス/通信) are held "
     "as a commons right of use (ADR-2606272200) — a COVERAGE + RESILIENCE map, **never a "
     "target-list, never a shut-off list**; a lifeline is never WITHHELD as leverage (G1). "
     "**ASSESSMENT + R0 DESIGN ONLY — iriai never energizes, flows, ignites, or activates** "
     "(G5); production is hikari/mizuho/kamado/noroshi under Council. All cells are :synthetic.\n\n"
     "Routes: **" (get assessment "provision") "** provision (§1.16 reach gap) · **"
     (get assessment "reinforce") "** reinforce (disaster) · **"
     (get assessment "redundancy") "** redundancy (no SPOF) · **"
     (get assessment "await_consent") "** await-consent · **"
     (get assessment "maintain") "** maintain · **"
     (get assessment "monitor") "** monitor. Unserved: **"
     (get assessment "unserved_pop") "** people.\n\n"
     "| region | lifeline | source | coverage | gap-pop | commons-gap | resilience | verdict |\n"
     "|---|---|---|---|---|---|---|---|\n"
     (str/join "\n"
               (for [r rows]
                 (str "| " (get r "region_name")
                      " | " (name (get r "lifeline"))
                      " | " (get r "source")
                      " | " (round3 (get r "coverage"))
                      " | " (get r "gap_pop")
                      " | " (round3 (get r "commons_gap"))
                      " | " (if (get r "single_source") "SPOF"
                                (if (neg? (get r "n1_margin")) "N-1<0" "ok"))
                      " | " (name (get r "verdict")) " |")))
     "\n\n_Sources: 電気→hikari 光 · 水道→mizuho 水穂 · ガス→kamado 竈 · 通信→noroshi 烽._\n"
     "_Funding of every provision/reinforce/redundancy cell is iriai.fund (§1.16 in-kind, cash≡0); "
     "the go/no-go is iriai.manage (1 SBT = 1 vote + Council). iriai only MAPS._\n")))

;; ── CLI (bb) ───────────────────────────────────────────────────────────────────
#?(:clj
   (defn -main [& args]
     (let [seed (or (first args) "20-actors/iriai/kotoba/seed.edn")
           rows (clojure.edn/read-string (slurp seed))
           cs (vec (filter #(= (:type %) :lifeline-cell) rows))
           a (assess cs)]
       (println (render-report a))
       (println (str "-- " (count cs) " lifeline-cells assessed --")))))

#?(:clj
   (when (= *file* (System/getProperty "babashka.file"))
     (apply -main *command-line-args*)))
