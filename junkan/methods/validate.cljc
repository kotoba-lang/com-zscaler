#!/usr/bin/env bb
;; junkan 循環 — substrate integrity checker (ontology ↔ seed ↔ region-map).
(ns junkan.methods.validate
  "validate.cljc — junkan 循環 governance-asymmetry substrate integrity checker
  (ADR-2605290927). A standalone, read-only verifier that the seed is internally
  consistent with the ontology and the analysis region map — the single source of
  truth for 'is the substrate well-formed'. Returns {:errors :warnings :stats};
  a non-empty :errors means the substrate is broken. Mirrors the validate.py
  pattern of the hinagata/tate family. Read-only (G4): no I/O in `check`, no
  outward channel."
  (:require [junkan.methods.analyze :as az]
            [clojure.string :as str]))

(defn check
  "Verify `instruments` against `enums` (from the ontology) + the region map.
  Pure. Returns {:errors [...] :warnings [...] :stats {...}}."
  [instruments enums]
  (let [errs (atom []) warns (atom [])
        err! #(swap! errs conj %)
        warn! #(swap! warns conj %)
        ids (map :id instruments)]
    ;; structural completeness (誰が / 経緯 / 関係者 on every row)
    (doseq [i instruments]
      (when (str/blank? (str (:id i)))   (err! (str "instrument missing :id: " (:name i))))
      (when (str/blank? (str (:name i))) (err! (str (:id i) " missing :name")))
      (when (str/blank? (str (:enactor i)))   (err! (str (:id i) " missing :enactor (誰が)")))
      (when (str/blank? (str (:origin i)))    (err! (str (:id i) " missing :origin (経緯)")))
      (when-not (seq (:stakeholders i))       (err! (str (:id i) " missing :stakeholders (関係者)")))
      ;; enum validity
      (when-not (contains? (:stock enums) (:stock i))
        (err! (str (:id i) " invalid :stock " (:stock i))))
      (when-not (contains? (:polarity enums) (:polarity i))
        (err! (str (:id i) " invalid :polarity " (:polarity i))))
      (when-not (contains? (:kind enums) (:kind i))
        (err! (str (:id i) " invalid :kind " (:kind i))))
      (when-not (contains? (:reversibility enums) (:reversibility i))
        (err! (str (:id i) " invalid :reversibility " (:reversibility i))))
      (when-not (contains? (:sourcing enums) (:sourcing i))
        (err! (str (:id i) " invalid :sourcing " (:sourcing i))))
      ;; numeric ranges
      (when-not (<= 0.0 (double (or (:magnitude i) -1)) 1.0)
        (err! (str (:id i) " :magnitude out of 0..1")))
      (when-not (<= 0.0 (double (or (:confidence i) -1)) 1.0)
        (err! (str (:id i) " :confidence out of 0..1")))
      (when-not (<= 1 (or (:meadows i) 0) 12)
        (err! (str (:id i) " :meadows out of 1..12")))
      (when (neg? (or (:year i) 0))
        (err! (str (:id i) " :year negative")))
      ;; region-mapping (a jurisdiction must resolve to a known continent)
      (when (= :other (az/region-of (:jurisdiction i)))
        (warn! (str (:id i) " jurisdiction " (:jurisdiction i) " unmapped to a region"))))
    ;; uniqueness
    (doseq [[id n] (frequencies ids) :when (> n 1)]
      (err! (str "duplicate :id " id " (" n "×)")))
    ;; coverage invariants
    (let [stocks (set (map :stock instruments))]
      (doseq [s (:stock enums)]
        (when-not (contains? stocks s) (warn! (str "no instrument for stock " s)))))
    (let [pol (frequencies (map :polarity instruments))]
      (when (zero? (get pol :widen 0))  (err! "no widening instruments"))
      (when (zero? (get pol :narrow 0)) (err! "no narrowing/balancing instruments")))
    ;; stock balance: warn if the largest stock dwarfs the smallest (steer seeding)
    (let [sc (vals (into {} (map (fn [s] [s (count (filter #(= s (:stock %)) instruments))])
                                 #{:information-asymmetry :participation-barrier
                                   :coercion-asymmetry :paradigm-subordination :economic-capture})))
          mx (apply max 0 sc) mn (apply min (cons (max mx 1) (filter pos? sc)))]
      (when (and (pos? mx) (> (/ (double mx) mn) 4.0))
        (warn! (str "stock imbalance: max/min ratio " (Math/round (* 10.0 (/ (double mx) mn)))
                    "/10 (>4) — deepen the thinnest stock"))))
    {:errors @errs :warnings @warns
     :stats {:instruments (count instruments)
             :jurisdictions (count (distinct (map :jurisdiction instruments)))
             :unique-ids (count (distinct ids))}}))

;; ── CLI (bb) ─────────────────────────────────────────────────────────────────
#?(:clj
   (defn -main [& args]
     (let [seed (or (first args) "20-actors/junkan/kotoba/seed.governance-asymmetry.edn")
           onto (or (second args) "20-actors/junkan/kotoba/ontology.junkan-gov.edn")
           is (vec (filter #(= (:type %) :instrument) (clojure.edn/read-string (slurp seed))))
           enums (:enums (clojure.edn/read-string (slurp onto)))
           {:keys [errors warnings stats]} (check is enums)]
       (println (str "junkan substrate integrity — " (:instruments stats) " instruments / "
                     (:jurisdictions stats) " jurisdictions"))
       (println (str "errors: " (count errors) " · warnings: " (count warnings)))
       (doseq [e errors] (println "  ERROR  " e))
       (doseq [w warnings] (println "  warn   " w))
       (println (if (empty? errors) "✅ substrate OK" "❌ substrate has errors"))
       (System/exit (if (empty? errors) 0 1)))))

#?(:clj
   (when (= *file* (System/getProperty "babashka.file"))
     (apply -main *command-line-args*)))
