(ns jinushi.methods.reconcile
  "jinushi 地主 — cross-source owner RECONCILIATION (the 信頼度 payoff).

  The same owner is described by several sources at different trust: Wikidata gives a (often
  English, crowd-curated) label + QID + LEI; GLEIF gives the AUTHORITATIVE legal name +
  jurisdiction for that LEI. This joins them on the LEI and resolves each attribute by source
  trust (confidence.cljc) — GLEIF's authoritative legal name wins over Wikidata's label —
  producing ONE unified owner record per LEI with the winning value, the contributing sources,
  and an honest disagreement flag (never silently dropped, G2).

  This is what makes 'ingest from all sources' more than a pile of snapshots: a trust-weighted
  single view, recomputed on read, diff-able across ingests (diff.cljc)."
  (:require [clojure.string :as str]
            [jinushi.methods.confidence :as c]
            [jinushi.methods.buildings :as buildings]
            [jinushi.methods.company-link :as company]
            #?(:clj [clojure.java.io :as io])))

(defn- norm [s] (-> (or s "") str/lower-case (str/replace #"[^\p{Alnum}]+" "")))

(defn reconcile-owners
  "Join building owners (Wikidata) ↔ GLEIF on LEI; resolve the owner name by source trust.
  Returns unified records: {:owner :lei :name :name-source :wikidata-label :gleif-name
  :jurisdiction :sources :name-agrees?}."
  [buildings-snap gleif]
  (->> (:owners buildings-snap)
       (keep (fn [[qid {:keys [label lei]}]]
               (when-let [g (and lei (get gleif lei))]
                 (let [wl label gl (:legal-name g)
                       resolved (c/resolve-conflict
                                 (cond-> []
                                   wl (conj {:source :wikidata :value wl})
                                   gl (conj {:source :gleif :value gl})))]
                   {:owner qid :lei lei
                    :name (:value resolved) :name-source (:source resolved)
                    :wikidata-label wl :gleif-name gl
                    :jurisdiction (:jurisdiction g)
                    :sources (vec (concat (when wl [:wikidata]) [:gleif]))
                    ;; "agrees" = the two names normalize to the same string (script/lang aside)
                    :name-agrees? (boolean (and wl gl (= (norm wl) (norm gl))))}))))
       (sort-by :lei) vec))

(defn report
  [recs]
  (let [n (count recs)
        gleif-won (count (filter #(= :gleif (:name-source %)) recs))
        disagree (filter #(and (:wikidata-label %) (:gleif-name %) (not (:name-agrees? %))) recs)]
    {:reconciled n
     :name-from-gleif gleif-won
     :name-disagreements (count disagree)
     :agreement-rate (if (pos? n) (/ (double (- n (count disagree))) n) 1.0)
     :sample-disagreements (vec (take 8 (map #(select-keys % [:lei :wikidata-label :gleif-name :name-source]) disagree)))}))

#?(:clj
   (defn -main [& _argv]
     (let [here (or (some-> (when (and *file* (not= *file* "NO_SOURCE_PATH")) (io/file *file*))
                            .getParentFile .getParentFile) (io/file "20-actors/jinushi"))
           root (or (some-> here .getParentFile .getParentFile) (io/file "."))
           dir (io/file root "80-data" "jinushi-land")
           bsnap (buildings/load-snapshot dir) gleif (company/load-gleif dir)]
       (if (or (nil? bsnap) (nil? gleif))
         (println "missing buildings or gleif")
         (let [recs (reconcile-owners bsnap gleif) rep (report recs)]
           (println (format "reconciled %d owners on LEI; authoritative name from GLEIF; %d name-disagreements (Wikidata label ≠ GLEIF legal name), agreement %.1f%%"
                            (:reconciled rep) (:name-disagreements rep) (* 100.0 (:agreement-rate rep))))
           (doseq [d (take 8 (:sample-disagreements rep))]
             (println (format "  %s : wikidata=%s ⇒ GLEIF=%s (authoritative wins)"
                              (:lei d) (:wikidata-label d) (:gleif-name d)))))))
     0))
