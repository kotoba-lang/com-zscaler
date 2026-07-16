#!/usr/bin/env bb
;; 委 yudane — analyze → datoms → coverage (clj-native, pure stdlib).
(ns yudane.methods.analyze
  "委 yudane — the Human Intention Energy Engine core (Energy Order Protocol).

  OBSERVATION + TRANSLATION ONLY. Consented, AGGREGATE cohort intention is translated
  into controllable energy variables (an aggregate flex offer) → 撓 tawami / 澪 mio.
  This is the 意味変換 (meaning-translation) leg, and the most charter-sensitive actor,
  so privacy is STRUCTURAL:

    * an offer crosses the membrane only if CONSENTED — a member-signed capability that
      is present, unexpired, reciprocal, and over a cohort ≥ the k-anonymity floor;
    * the engine is CONTENT-FREE — only an intention CLASS + an AGGREGATE cohort signal
      are ever read or emitted; there is no per-person field, by construction.

  Hard invariants (proven by tests):
    G1  consent-bound — no capability ⇒ nothing is read (:refused-no-capability); an
        expired capability is refused (the revocable leash).
    G2  content-free / no-person — a cohort below the k-anonymity floor is refused (an
        individual could be identified); no :yudane.person/* / :score / :denunciation /
        :surveil attribute exists (the surveillance degeneration series is unrepresentable).
    G4  translation only — yudane never controls; no :yudane/dispatch is emitted."
  (:require [clojure.string :as str]))

;; ── consent params ───────────────────────────────────────────────────────────

(def k-anon-min 5) ;; a cohort below this is refused — no individual may be identifiable

(def best-use
  {:presence-shift :peak-shave
   :defer-flexible :renewable-absorb
   :schedule-known :flexibility
   :seasonal-prep :renewable-absorb})

;; ── pure consent logic ───────────────────────────────────────────────────────

(defn verdict
  "The consent verdict for an offer. Order of refusal is meaningful — the absence of a
  capability is checked before its expiry, and privacy (k-anonymity) is its own gate."
  [offer]
  (cond
    (str/blank? (str (:capability offer)))      :refused-no-capability
    (<= (double (or (:cap-expiry-h offer) 0)) 0) :refused-expired
    (not (:reciprocal offer))                    :refused-not-reciprocal
    (< (long (or (:cohort-size offer) 0)) k-anon-min) :refused-below-k-anon
    :else :consented))

(defn route [vd] (if (= vd :consented) :flex-offer :refused))

(defn analyze-offer
  "Per-offer derived observation map (string-keyed, agent-facing). CONTENT-FREE — it
  carries the intention CLASS + aggregate cohort size only, never any per-person data."
  [offer]
  (let [vd (verdict offer)
        fk (double (or (:flex-kwh offer) 0))]
    {"id" (:id offer)
     "name" (:name offer)
     "intent_class" (:intent-class offer)
     "cohort_size" (long (or (:cohort-size offer) 0))
     "verdict" vd
     ;; G1/G2: aggregate flex crosses the membrane ONLY when consented.
     "flex_kwh" (if (= vd :consented) fk 0.0)
     "best_use" (get best-use (:intent-class offer) :flexibility)
     "route" (route vd)
     "sourcing" (or (:sourcing offer) :representative)
     "source" (:source offer)}))

(defn analyze
  [offers]
  (let [rows (mapv analyze-offer offers)
        by-class (group-by #(get % "intent_class") rows)
        classes (vec (for [[cls crows] (sort-by (comp name key) by-class)]
                       {"intent_class" cls
                        "count" (count crows)
                        "consented_count" (count (filter #(= (get % "verdict") :consented) crows))
                        "consented_flex_kwh" (reduce + 0.0 (map #(get % "flex_kwh") crows))}))
        consented (filter #(= (get % "verdict") :consented) rows)]
    {"offers" rows
     "classes" classes
     "totals" {"offer_count" (count rows)
               "consented_count" (count consented)
               "refused_count" (- (count rows) (count consented))
               ;; the org's consented aggregate flex that crossed the membrane (content-free)
               "consented_flex_kwh" (reduce + 0.0 (map #(get % "flex_kwh") consented))}}))

;; ── datom emission (append-only EAVT; every derived datom flagged; content-free) ──

(defn- add [e a v] [":db/add" e a v])
(defn- round3 [x] (/ (Math/round (* (double x) 1000.0)) 1000.0))

(defn datoms
  "Append-only EAVT datom VECTORS. CONTENT-FREE: only intention CLASS + aggregate cohort
  size + verdict + flex are emitted — never per-person data. No :yudane.person/* /
  :score / :denunciation / :surveil / :dispatch / :trade attribute is ever emitted (G1/G2/G4)."
  [{:strs [offers classes totals]}]
  (let [odatoms (mapcat
                 (fn [r]
                   (let [e (str "yudane-offer:" (get r "id")) src (get r "source")]
                     (cond-> [(add e ":yudane.offer/name" (get r "name"))
                              (add e ":yudane.offer/intent-class" (str (get r "intent_class")))
                              (add e ":yudane.offer/cohort-size" (get r "cohort_size"))
                              (add e ":yudane.obs/verdict" (str (get r "verdict")))
                              (add e ":yudane.obs/flex-kwh" (round3 (get r "flex_kwh")))
                              (add e ":yudane.obs/best-use" (str (get r "best_use")))
                              (add e ":yudane.obs/route" (str (get r "route")))
                              (add e ":yudane/sourcing" (str (get r "sourcing")))
                              (add e ":yudane/derived" true)]
                       src (conj (add e ":yudane/source" src)))))
                 offers)
        kdatoms (mapcat
                 (fn [k]
                   (let [e (str "yudane-class:" (name (get k "intent_class")))]
                     [(add e ":yudane.class/offer-count" (get k "count"))
                      (add e ":yudane.class/consented-count" (get k "consented_count"))
                      (add e ":yudane.class/consented-flex-kwh" (round3 (get k "consented_flex_kwh")))
                      (add e ":yudane/derived" true)]))
                 classes)
        e "yudane-ledger:intention"
        ldatoms [(add e ":yudane.ledger/offer-count" (get totals "offer_count"))
                 (add e ":yudane.ledger/consented-count" (get totals "consented_count"))
                 (add e ":yudane.ledger/refused-count" (get totals "refused_count"))
                 (add e ":yudane.ledger/consented-flex-kwh" (round3 (get totals "consented_flex_kwh")))
                 (add e ":yudane/derived" true)]]
    (vec (concat odatoms kdatoms ldatoms))))

(defn render-datoms
  [assessment]
  (str "[\n " (str/join "\n " (map pr-str (datoms assessment))) "\n]\n"))

;; ── coverage (intention classes mapped) ──────────────────────────────────────

(def ^:private universe
  {:presence-shift 2 :defer-flexible 2 :schedule-known 2 :seasonal-prep 2})

(defn coverage
  [offers]
  (let [by-class (group-by :intent-class offers)
        rows (for [[cls target] (sort-by (comp name key) universe)
                   :let [have (count (get by-class cls []))]]
               {"intent_class" cls "have" have "target" target
                "gap" (max 0 (- target have))})]
    {"by_class" (vec rows)
     "total_have" (count offers)
     "total_target" (reduce + (vals universe))
     "total_gap" (reduce + (map #(get % "gap") rows))}))

;; ── markdown intention ledger (content-free) ─────────────────────────────────

(defn render-report
  [analysis coverage-map]
  (let [rows (->> (get analysis "offers") (sort-by #(- (get % "flex_kwh"))))
        totals (get analysis "totals")
        cov (get coverage-map "by_class")]
    (str
     "# 委 yudane — INTENTION→ENERGY ledger (content-free)\n\n"
     "OBSERVATION + TRANSLATION ONLY. Consented, AGGREGATE cohort intention is "
     "translated into energy variables → 撓 tawami / 澪 mio. **Privacy is structural**: "
     "an offer crosses only if CONSENTED (member-signed capability, unexpired, reciprocal, "
     "cohort ≥ k-anonymity floor of " k-anon-min "), and the engine is **content-free** — "
     "only an intention CLASS + an AGGREGATE cohort signal are read, never per-person "
     "text. The surveillance degeneration series (五人組→隣組→Stasi→social-credit) is "
     "unrepresentable.\n\n"
     "## Org consented aggregate flex\n\n"
     "- **consented flex = " (round3 (get totals "consented_flex_kwh")) " kWh** "
     "(crossed the membrane)\n"
     "- consented: " (get totals "consented_count") " / " (get totals "offer_count")
     " · refused: " (get totals "refused_count") "\n\n"
     "## Offer ledger (consented flex, highest first)\n\n"
     "| offer | intent class | cohort | verdict | flex kWh | best use |\n|---|---|---|---|---|---|\n"
     (str/join "\n"
               (for [r rows]
                 (str "| " (get r "name")
                      " | " (name (get r "intent_class"))
                      " | " (get r "cohort_size")
                      " | " (name (get r "verdict"))
                      " | " (round3 (get r "flex_kwh"))
                      " | " (name (get r "best_use")) " |")))
     "\n\n## Coverage (intention classes mapped)\n\n"
     "| intent class | have | target | gap |\n|---|---|---|---|\n"
     (str/join "\n"
               (for [c cov]
                 (str "| " (name (get c "intent_class")) " | " (get c "have")
                      " | " (get c "target") " | " (get c "gap") " |")))
     "\n\n_verdict → consented (→ :flex-offer to tawami/mio) · refused-no-capability · "
     "refused-expired · refused-not-reciprocal · refused-below-k-anon. Consent is "
     "member-signed + revocable; the cohort floor keeps every offer non-identifying._\n")))

;; ── CLI (bb) ────────────────────────────────────────────────────────────────

#?(:clj
   (defn -main [& args]
     (let [seed (or (first args) "20-actors/yudane/kotoba/seed.edn")
           rows (clojure.edn/read-string (slurp seed))
           offers (vec (filter #(= (:type %) :offer) rows))
           a (analyze offers)
           cov (coverage offers)]
       (println (render-report a cov))
       (println (str "-- " (count offers) " offers, "
                     (get-in a ["totals" "consented_count"]) " consented, flex "
                     (round3 (get-in a ["totals" "consented_flex_kwh"])) " kWh, "
                     (get cov "total_gap") " gap --")))))

#?(:clj
   (when (= *file* (System/getProperty "babashka.file"))
     (apply -main *command-line-args*)))
