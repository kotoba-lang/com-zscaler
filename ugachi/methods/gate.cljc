#!/usr/bin/env bb
;; ugachi 穿ち — the §2(l) extraction RISK-GATE (clj-native, pure stdlib).
(ns ugachi.methods.gate
  "ugachi 穿ち — the §2(l) multi-generational (子・孫) × wellbecoming extraction gate
  (ADR-2606161800, implementing ADR-2606161700).

  採掘・採油は一律禁止ではない。A proposed extraction PROJECT is authorized ONLY by
  PASSING this gate. ASSESSMENT + R0 DESIGN ONLY — ugachi never digs.

  verdict ∈ {:refuse :route-to-recovery :propose-r0 :insufficient-evidence}
  Hard refusals (checked first, in order):
    G5  no consent (Tree of Life land sovereignty + community)        → :refuse :no-consent
    G6  fossil→combusted net-positive carbon (§2(d))                  → :refuse :carbon-positive
    G3  monopoly/chokepoint ENTRENCHMENT (§1.12)                       → :refuse :monopoly-entrenchment
    G3  irreversible multi-gen harm: irreversibility·(1-remediation)  → :refuse :irreversible-multigen-harm
        ≥ 0.5
  Then recovery-first preference:
        a VIABLE recovery alternative exists                          → :route-to-recovery (kanayama)
  Then permit-design:
        transparent (on-chain+open-source+1SBT=1vote) AND
        descendant-benefit ≥ 0.5                                       → :propose-r0
  Else                                                                 → :insufficient-evidence

  The gate REFUSES failing projects structurally (proven by tests): there is no
  path by which a no-consent / carbon-positive / monopoly-entrenching / irreversible
  project returns a permit. And there is NO actuation method — ugachi cannot extract."
  (:require [clojure.string :as str]
            [ugachi.methods.ugachi-edn :as ue]))

;; ── pure gate ────────────────────────────────────────────────────────────────

(defn net-irreversibility
  "Irreversibility net of the remediation/restoration plan ∈ 0..1."
  [p]
  (* (double (or (:irreversibility p) 0))
     (- 1.0 (double (or (:remediation p) 0)))))

(defn multigen-risk
  "Multi-gen (子・孫) × wellbecoming risk score ∈ 0..1 — net-irreversibility plus
  carbon and monopoly penalties, minus the descendant-benefit it serves. A scoring
  aid for ranking; the VERDICT is decided by the gate, not this score."
  [p]
  (let [carbon-pen (case (:carbon p) :net-positive 0.4 :net-neutral 0.1 :net-negative 0.0 0.1)
        mono-pen (case (:monopoly-effect p) :entrench 0.4 :neutral 0.1 :diversify 0.0 0.1)
        benefit (double (or (:descendant-benefit p) 0))]
    (max 0.0 (min 1.0 (+ (* 0.5 (net-irreversibility p))
                         (* 0.3 carbon-pen)
                         (* 0.2 mono-pen)
                         (* -0.15 benefit)
                         0.15)))))

(defn verdict
  "The §2(l) gate verdict for a project. Returns {:verdict … :reason … :route …}."
  [p]
  (let [ni (net-irreversibility p)]
    (cond
      (not (:consent p))
      {:verdict :refuse :reason :no-consent}

      (= (:carbon p) :net-positive)
      {:verdict :refuse :reason :carbon-positive}

      (= (:monopoly-effect p) :entrench)
      {:verdict :refuse :reason :monopoly-entrenchment}

      (>= ni 0.5)
      {:verdict :refuse :reason :irreversible-multigen-harm}

      (= (:recovery-alternative p) :viable)
      {:verdict :route-to-recovery :route :kanayama}

      (and (:transparent p) (>= (double (or (:descendant-benefit p) 0)) 0.5))
      {:verdict :propose-r0 :route :r0-design}

      :else
      {:verdict :insufficient-evidence})))

(defn assess-project [p]
  (let [v (verdict p)]
    (merge {"id" (:id p)
            "name" (:name p)
            "resource" (:resource p)
            "net_irreversibility" (net-irreversibility p)
            "multigen_risk" (multigen-risk p)
            "verdict" (:verdict v)}
           (when (:reason v) {"reason" (:reason v)})
           (when (:route v) {"route" (:route v)}))))

(defn assess [projects]
  (let [rows (mapv assess-project projects)
        tally (frequencies (map #(get % "verdict") rows))]
    {"projects" rows
     "tally" tally
     "refused" (count (filter #(= :refuse (get % "verdict")) rows))
     "permitted_r0" (count (filter #(= :propose-r0 (get % "verdict")) rows))
     "routed_to_recovery" (count (filter #(= :route-to-recovery (get % "verdict")) rows))}))

(defn refusal-drivers
  "Among the projects the §2(l) gate REFUSES, which constitutional concern drives the refusals — by
  COUNT (how many projects each reason refuses) and by SEVERITY (the summed multigen-risk of the
  projects it refuses). The per-project verdict says why ONE project is refused and `assess` tallies
  the verdicts; this profiles the failure modes and surfaces whether the most FREQUENT refusal
  reason is also the most SEVERE (no-consent refusals can be common but low-risk; irreversible-harm
  refusals rarer but high-risk). The reasons map to the gates: :no-consent (G5 land sovereignty),
  :carbon-positive (G6 / §2(d)), :monopoly-entrenchment (G3 / §1.12), :irreversible-multigen-harm
  (G3). ASSESSMENT only — a stewardship profile aggregating the constitutional REASONS (never an
  industry name, G2), never a target-list (G2/G5). Returns
  {:refused n :by-count [[reason count] …] :by-severity [[reason risk-sum] …]
   :dominant-by-count reason|nil :dominant-by-severity reason|nil}."
  [projects]
  (let [refused (filter #(= :refuse (get % "verdict")) (map assess-project projects))
        r3 (fn [x] (/ (Math/round (* (double x) 1000.0)) 1000.0))
        by-count (->> refused (map #(get % "reason")) frequencies
                      (sort-by (fn [[r c]] [(- c) (str r)])) (mapv vec))
        by-severity (->> refused
                         (reduce (fn [m row] (update m (get row "reason") (fnil + 0.0)
                                                     (get row "multigen_risk"))) {})
                         (sort-by (fn [[r s]] [(- s) (str r)]))
                         (mapv (fn [[r s]] [r (r3 s)])))]
    {:refused (count refused)
     :by-count by-count
     :by-severity by-severity
     :dominant-by-count (ffirst by-count)
     :dominant-by-severity (ffirst by-severity)}))

;; ── datom emission (append-only EAVT; flagged) ──────────────────────────────

(defn- add [e a v] [":db/add" e a v])
(defn- round3 [x] (/ (Math/round (* (double x) 1000.0)) 1000.0))

(defn datoms
  "Append-only EAVT datom VECTORS for the gate verdicts (the persistable form;
  render-datoms stringifies these; autorun/kotoba append these to the ledger).
  Every datom flagged :ugachi/derived + :ugachi/sourcing :synthetic. No
  :ugachi/actuate or :ugachi/extract attribute is ever emitted (G1)."
  [{:strs [projects]}]
  (vec
   (mapcat
    (fn [r]
      (let [e (str "ugachi-project:" (get r "id"))]
        (concat
         [(add e ":ugachi.project/resource" (str (get r "resource")))
          (add e ":ugachi.gate/net-irreversibility" (round3 (get r "net_irreversibility")))
          (add e ":ugachi.gate/multigen-risk" (round3 (get r "multigen_risk")))
          (add e ":ugachi.gate/verdict" (str (get r "verdict")))]
         (when (get r "reason") [(add e ":ugachi.gate/reason" (str (get r "reason")))])
         (when (get r "route")  [(add e ":ugachi.gate/route" (str (get r "route")))])
         [(add e ":ugachi/sourcing" ":synthetic")
          (add e ":ugachi/derived" true)])))
    projects)))

(defn render-datoms
  "EDN string of the gate-verdict datoms (see `datoms`)."
  [assessment]
  (str "[\n " (str/join "\n " (map pr-str (datoms assessment))) "\n]\n"))

;; ── markdown stewardship gate (resilience/restoration, never a target-list) ──

(defn render-report [assessment]
  (let [rows (->> (get assessment "projects")
                  (sort-by #(- (get % "multigen_risk"))))]
    (str
     "# ugachi 穿ち — §2(l) extraction stewardship GATE\n\n"
     "採掘・採油は一律禁止ではない (ADR-2606161700). Each PROPOSED project is authorized "
     "ONLY by passing the multi-generational (子・孫) × wellbecoming risk gate. "
     "**ASSESSMENT + R0 DESIGN ONLY — ugachi never digs.** A stewardship ledger, "
     "NEVER a target-list. All projects are :synthetic.\n\n"
     "Verdicts: **" (get assessment "permitted_r0") "** propose-r0 · **"
     (get assessment "routed_to_recovery") "** route-to-recovery · **"
     (get assessment "refused") "** refused.\n\n"
     "| project | resource | net-irrev | multigen-risk | verdict | reason/route |\n"
     "|---|---|---|---|---|---|\n"
     (str/join "\n"
               (for [r rows]
                 (str "| " (get r "name")
                      " | " (str (get r "resource"))
                      " | " (round3 (get r "net_irreversibility"))
                      " | " (round3 (get r "multigen_risk"))
                      " | " (name (get r "verdict"))
                      " | " (name (or (get r "reason") (get r "route") :-)) " |")))
     "\n\n_Refusals routed to: recovery (kanayama urban-mining) · energy transition (kamado) · restoration (inochi). De-monopolization → abaki/kabuto._\n"
     "\n_Even :propose-r0 is DESIGN-ONLY — live actuation is Council Lv7+ gated, never by ugachi (G1/G7)._\n")))

;; ── CLI (bb) ────────────────────────────────────────────────────────────────

#?(:clj
   (defn -main [& args]
     (let [seed (or (first args) "20-actors/ugachi/kotoba/seed.edn")
           ps (ue/projects seed)
           a (assess ps)]
       (println (render-report a))
       (println (str "-- " (count ps) " projects assessed --")))))

#?(:clj
   (when (= *file* (System/getProperty "babashka.file"))
     (apply -main *command-line-args*)))
