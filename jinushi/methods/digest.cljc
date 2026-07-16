(ns jinushi.methods.digest
  "jinushi 地主 — CAPSTONE digest: the whole 全世界 不動産取得 picture in one report.

  Fuses every layer the actor has acquired — LAND coverage (sanitized, real denominator),
  BUILDING ownership (count + floors/height 取-concentration), authoritative COMPANY linkage
  (GLEIF), and the per-jurisdiction public-record gate — into a single answer to the standing
  question 「今の root の全世界の不動産の取得 coverage は?」. Read-only synthesis of committed,
  content-addressed artifacts (no network); every number is a read-time aggregate (G2)."
  (:require [clojure.string :as str]
            [jinushi.methods.analyze :as analyze]
            [jinushi.methods.ingest :as ingest]
            [jinushi.methods.buildings :as buildings]
            [jinushi.methods.company-link :as company]
            [jinushi.methods.jurisdiction :as juris]
            [jinushi.methods.dvf-values :as dvf]
            [jinushi.methods.value-trend :as vtrend]
            [jinushi.methods.reconcile :as reconcile]
            #?(:clj [clojure.java.io :as io])))

#?(:clj
   (defn collect
     "Assemble the cross-layer metrics from the committed data layer."
     [dir]
     (let [areas (ingest/load-country-areas dir)
           land-ds (:dataset (ingest/sanitize (ingest/counting-dataset (ingest/load-all-snapshots dir)) areas))
           land (analyze/analyze land-ds {:country-area areas})
           bsnap (buildings/load-snapshot dir)
           bld (when bsnap (buildings/analyze bsnap))
           gleif (company/load-gleif dir)
           clink (when (and bsnap gleif) (company/coverage bsnap gleif))
           dvf-recs (dvf/load-all dir)
           dvf-a (when (seq dvf-recs) (dvf/analyze* dvf-recs))
           vtr (vtrend/trend (vtrend/load-years dir))
           recon (when (and bsnap gleif) (reconcile/report (reconcile/reconcile-owners bsnap gleif)))]
       {:land land
        :buildings (when bsnap {:snap bsnap :a bld})
        :company clink
        :values dvf-a
        :value-trend vtr
        :reconcile recon
        :jurisdictions (count juris/registry)})))

(defn render
  "Markdown digest from collected metrics."
  [{:keys [land buildings company values value-trend reconcile jurisdictions]}]
  (let [lc (:coverage land) con (:concentration land)
        bsnap (:snap buildings) ba (:a buildings) bc (:concentration ba)]
    (str/join "\n"
      (concat
       ["# jinushi 地主 — 全世界 不動産取得 (real-estate acquisition) digest" ""
        "## LAND (national parks = protected PUBLIC land; sanitized, real country-area denominator)"
        (format "- countries: **%d**   acquired: **%,.0f km²**   national-park land ÷ world land: **%.4g%%**"
                (:countries-touched lc) (:acquired-area-km2 lc) (* 100.0 (:world-coverage-frac lc)))
        "  (protected-public-land coverage — NOT all-land-ownership; private/urban land is sample-scale, G4 honesty)"
        (format "- land 取-concentration HHI %.0f (top holder %s); RETURN-to-commons candidates: %d"
                (:hhi con) (if-let [t (:top-holder con)] (format "%.1f%%" (* 100.0 (:share t))) "n/a")
                (count (:return-candidates land)))
        ""]
       (if buildings
         ["## BUILDINGS (ownership KG; public-record + symmetric gate)"
          (format "- buildings: **%d**   countries: **%d**   owners: **%d** (natural persons: %d)   with floors: %d / height: %d"
                  (:building-count bc) (count (:countries bsnap)) (:owner-count bc)
                  (:natural-person-owners bsnap) (:with-floors bsnap) (:with-height bsnap))
          "- 取-concentration by #buildings (rail operators):"
          (str/join "\n" (for [t (take 3 (:top-by-buildings bc))]
                           (format "    %s — %d buildings" (:label t) (:buildings t))))
          "- 取-concentration by TOTAL FLOORS (ビルのフロア — real-estate developers):"
          (str/join "\n" (for [t (take 3 (:top-by-floors bc))]
                           (format "    %s — %d floors / %d buildings" (:label t) (:floors t) (:buildings t))))
          ""]
         ["## BUILDINGS — (no snapshot)" ""])
       (if company
         ["## COMPANY LINKAGE (authoritative; GLEIF legal entities → corp KGs)"
          (format "- building owners → GLEIF: **%d** / %d   buildings linked: **%d**   jurisdictions: %d"
                  (:owners-gleif-linked company) (:owners-total company)
                  (:buildings-linked company) (count (:by-jurisdiction company)))
          "- join keys: owner LEI → kabuto/uchiwake/kanjō; owner QID → keizu/tsumugi" ""]
         [])
       (if values
         (concat
          ["## VALUE (FR DVF transactions — €/m², no owner identity)"
           (format "- %d mutations / **€%,.0f** across %d communes; median mutation €%,.0f"
                   (:mutations values) (:total-value-eur values) (count (:by-commune values))
                   (or (:median-mutation-eur values) 0.0))]
          (for [[c v] (:by-commune values)]
            (format "    commune %s: apartment median **€%,.0f/m²** (%d mutations)"
                    c (or (:appt-median-eur-m2 v) 0.0) (:mutations v)))
          (for [[c v] value-trend :when (seq (:yoy v))]
            (let [d (last (:yoy v))]
              (format "    commune %s trajectory %s→%s: %+.1f%% €/m² (vol %+.1f%%)"
                      c (:from d) (:to d) (:pct d) (or (:vol-pct d) 0.0))))
          [""])
         [])
       (if reconcile
         ["## RELIABILITY (信頼度 — cross-source, trust-weighted)"
          (format "- owners reconciled on LEI: **%d**; name authoritative from GLEIF; %d crowd↔authoritative disagreements resolved (agreement %.0f%%)"
                  (:reconciled reconcile) (:name-disagreements reconcile) (* 100.0 (:agreement-rate reconcile)))
          "- trust tiers: gov/registry 0.95 > curated-crowd 0.70 > open-crowd 0.60 > web 0.40 > unknown 0.30" ""]
         [])
       ["## PUBLIC-RECORD GATE (per-jurisdiction; natural-person bulk-ingestion)"
        (format "- %d jurisdictions classified; bulk-public: %s"
                jurisdictions
                (str/join " " (sort (filter #(= :bulk-public (juris/persons-mode %)) (keys juris/registry)))))
        "- everything else degrades honestly to :unknown (persons not bulk-ingested)" ""
        "_All numbers are read-time aggregates of committed, content-addressed public-record"
        "artifacts (verify.cljc checks CID/sha256). Map-not-target; non-monetized; 相互監視._"]))))

#?(:clj
   (defn -main [& _argv]
     (let [here (or (some-> (when (and *file* (not= *file* "NO_SOURCE_PATH")) (io/file *file*))
                            .getParentFile .getParentFile) (io/file "20-actors/jinushi"))
           root (or (some-> here .getParentFile .getParentFile) (io/file "."))
           dir (ingest/data-dir root)
           m (collect dir)
           txt (render m)
           out (io/file dir "jinushi-digest.md")]
       (spit out txt)
       (println txt)
       (println (str "\n→ " out))
       0)))
