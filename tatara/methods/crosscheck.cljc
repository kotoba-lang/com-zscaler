(ns tatara.methods.crosscheck
  "tatara 鑪 — kabuto-linkage crosscheck (ADR-2606171800; uchiwake crosscheck pattern).

  MEASURES how many tatara plant operators (:plant/operator) resolve to a real company in the
  kabuto 兜 supply-chain KG (:company/id), and emits a prioritized worklist of the operators that
  do NOT yet resolve. This operationalizes the cross-actor composition claim: tatara places the
  FACILITIES, kabuto holds the org→org SUPPLY edges — the join is :plant/operator ⇄ kabuto
  :company/id. tatara uses a short operator id (org.corp.tsmc); kabuto uses a country-qualified id
  (org.corp.tw.tsmc) — the crosscheck bridges the two by normalized token over id + name, and the
  unresolved remainder is an honest coverage finding, never hidden (G5).

  Pure measurement — it MUTATES nothing and asserts no fact; the output is a :derived report +
  worklist. cljc-native (clojure.edn). File I/O only behind #?(:clj …)."
  (:require [clojure.string :as str]
            [clojure.java.io :as io]
            [tatara.methods.analyze :as az]))

(defn- norm
  "Lowercase, strip everything but a-z0-9 (so org.corp.kr.sk-hynix ≈ skhynix)."
  [s]
  (-> (str s) str/lower-case (str/replace #"[^a-z0-9]" "")))

(defn- last-seg [id] (last (str/split (str id) #"\.")))

(defn kabuto-index
  "Build a lookup over kabuto company rows: [{:id :name :nid :nname}]."
  [company-rows]
  (vec (keep (fn [r]
               (when-let [id (:company/id r)]
                 {:id id :name (:company/name r "")
                  :nid (norm (last-seg id))
                  :nname (norm (:company/name r ""))}))
             company-rows)))

(defn resolve-operator
  "Resolve one tatara operator id to the best kabuto company id, or nil. Match priority:
  (1) kabuto id last-segment contains the operator token, else (2) kabuto name contains it.
  Deterministic: among candidates prefer an id-segment hit, then the shortest id, then lexical."
  [operator idx]
  (let [tok (norm (last-seg operator))]
    (when (>= (count tok) 3)
      (let [cands (filter (fn [c] (or (str/includes? (:nid c) tok)
                                      (str/includes? (:nname c) tok)))
                          idx)
            ranked (sort-by (fn [c] [(if (str/includes? (:nid c) tok) 0 1)
                                     (count (:id c)) (:id c)])
                            cands)]
        (:id (first ranked))))))

(defn crosscheck
  "Return {:total :resolved :coverage :rows :worklist}. rows = [{:operator :plants :kabuto}]."
  [plants company-rows]
  (let [idx (kabuto-index company-rows)
        op->plants (reduce (fn [m p] (update m (:plant/operator p) (fnil conj []) (:plant/name p)))
                           {} plants)
        operators (sort (keys op->plants))
        rows (mapv (fn [op]
                     {:operator op
                      :plants (op->plants op)
                      :kabuto (resolve-operator op idx)})
                   operators)
        resolved (count (filter :kabuto rows))
        total (count operators)]
    {:total total
     :resolved resolved
     :coverage (if (zero? total) 0.0 (/ (Math/round (* (/ (double resolved) total) 1000.0)) 1000.0))
     :rows rows
     :worklist (->> rows (remove :kabuto)
                    (sort-by (fn [r] [(- (count (:plants r))) (:operator r)]))
                    (mapv (fn [r] {:operator (:operator r) :plants (:plants r)})))}))

#?(:clj
   (defn render-report [cc]
     (let [P (fn [L s] (conj L s))
           L (-> []
                 (P "# tatara 鑪 — kabuto-linkage crosscheck")
                 (P "")
                 (P (str "> ADR-2606171800 · MEASURES :plant/operator ⇄ kabuto :company/id linkage. "
                         "Pure measurement (mutates nothing, asserts no fact). Unresolved = honest "
                         "coverage gap (G5), never hidden."))
                 (P "")
                 (P (str "- operators: **" (:total cc) "**  ·  resolved to a kabuto company: **"
                         (:resolved cc) "**  ·  coverage: **"
                         (int (* 100 (:coverage cc))) "%**"))
                 (P "")
                 (P "## Resolution")
                 (P "")
                 (P "| tatara operator | plants | kabuto company id |")
                 (P "|---|---:|---|"))
           L (reduce (fn [L r]
                       (P L (str "| `" (:operator r) "` | " (count (:plants r)) " | "
                                 (if (:kabuto r) (str "`" (:kabuto r) "`") "— *unresolved*") " |")))
                     L (:rows cc))
           L (-> L (P "") (P "## Ingest worklist — operators not yet in kabuto") (P ""))
           L (if (seq (:worklist cc))
               (reduce (fn [L w] (P L (str "- `" (:operator w) "` (" (count (:plants w)) " plant(s): "
                                           (str/join ", " (:plants w)) ")")))
                       L (:worklist cc))
               (P L "- (all operators resolve — full linkage)"))
           L (-> L (P "") (P "---")
                 (P (str "*Generated by `tatara/methods/crosscheck.cljc`. Honest: matches by "
                         "normalized token over kabuto id + name; an unresolved operator means "
                         "'not yet in kabuto' (e.g. a privately-held maker) — a worklist item, not "
                         "a fabricated link (G5).*")))]
       (str (str/join "\n" L) "\n"))))

(defn render-datoms
  "Flatten the linkage measurement into EAVT assertions, each flagged :linkage/derived — a
  measurement recomputed on read, never re-ingested as fact (symmetric with analyze/compose).
  Per-operator resolution + one aggregate coverage datom. Asserts no fabricated link (an
  unresolved operator persists :linkage/resolved false, never a guessed id — G5)."
  [cc]
  (let [per (mapcat (fn [r]
                      (let [e (str "linkage-" (:operator r))]
                        [[:db/add e :linkage/operator (:operator r)]
                         [:db/add e :linkage/kabuto (or (:kabuto r) "")]
                         [:db/add e :linkage/resolved (boolean (:kabuto r))]
                         [:db/add e :linkage/derived true]]))
                    (:rows cc))
        agg [[:db/add "linkage-aggregate" :linkage/total (:total cc)]
             [:db/add "linkage-aggregate" :linkage/resolved-count (:resolved cc)]
             [:db/add "linkage-aggregate" :linkage/coverage (:coverage cc)]
             [:db/add "linkage-aggregate" :linkage/derived true]]]
    (vec (concat per agg))))

#?(:clj
   (defn -main [& argv]
     (let [argv (vec argv)
           cand (-> *file* io/file .getAbsoluteFile .getParentFile .getParentFile)   ; tatara/
           here (if (.exists (io/file cand "data" "seed-plant-graph.kotoba.edn"))
                  cand
                  (io/file "20-actors" "tatara"))
           root (.getParentFile (.getAbsoluteFile here))                             ; 20-actors/
           plant-seed (io/file here "data" "seed-plant-graph.kotoba.edn")
           kab-seed (io/file root "kabuto" "data" "seed-public-companies.kotoba.edn")
           plants (filter :plant/id (az/load-edn plant-seed))
           companies (if (.exists kab-seed) (filter :company/id (az/load-edn kab-seed)) [])
           cc (crosscheck plants companies)
           outdir (io/file here "out")]
       (.mkdirs outdir)
       (spit (io/file outdir "kabuto-crosscheck.md") (render-report cc))
       (spit (io/file outdir "kabuto-linkage.kotoba.edn")
             (str ";; tatara — DERIVED kabuto-linkage datoms (ADR-2606171800). :linkage/derived — NOT fact.\n"
                  (pr-str (render-datoms cc)) "\n"))
       (println (str "tatara crosscheck: " (:resolved cc) "/" (:total cc) " operators resolve to "
                     "kabuto (" (int (* 100 (:coverage cc))) "% linkage); "
                     (count (:worklist cc)) " on the ingest worklist"))
       (when (empty? companies)
         (println "  (kabuto seed not found — 0 companies loaded)"))
       0)))
