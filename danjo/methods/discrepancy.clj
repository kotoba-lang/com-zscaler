;; discrepancy.clj — 弾正 (danjo) appropriation↔outlay reconciliation → NON-ADJUDICATING
;; observations. ADR-2605301600. The clj sibling of the danjo crossref/statement_consistency
;; cells; emits `:danjo.obs/*` datoms in the SAME shape as methods/kotoba.py derived_datoms,
;; so the revenue ledger plugs straight into danjo's existing observation model.
;;
;; Per (programCode, fiscalYear): sum appropriations A, sum outlays O, report the FACTUAL
;; relation O vs A. Discipline:
;;   G4 — categories are FACTUAL relations only; NO crime/violation/verdict token is
;;        representable (observation-datoms RAISES if one appears in an attr OR a category value);
;;        every observation carries :danjo.obs/non-adjudicating true.
;;   G5 — every observation cites ≥2 upstream record CIDs (the appropriation + outlay records).
;;   G6 — the detector heuristic is published as an OPEN, versioned method-note (content-CID'd).
;;
;; danjo finds, it never judges: an O > A relation is a fact to surface, not a finding of
;; wrongdoing (timing / partial-corpus are declared false-positive modes). Legal
;; characterization happens via human counsel through chigiri — never here.
(ns root.danjo.methods.discrepancy
  (:require [clojure.string :as str])
  (:import [java.security MessageDigest]))

(load-file "revenue_ledger.clj")
(alias 'rl 'root.danjo.methods.revenue-ledger)

(defn- sha256-hex [^String s]
  (let [md (MessageDigest/getInstance "SHA-256")]
    (apply str (map #(format "%02x" (bit-and % 0xff)) (.digest md (.getBytes s "UTF-8"))))))

(def method-note
  "The OPEN, versioned detector heuristic (G6). The public audits the method, not just output."
  {:id "appropriation-outlay-reconciliation" :version "v1"
   :text (str "Per (programCode, fiscalYear): A = Σ appropriations, O = Σ outlays. "
              "Report the FACTUAL relation O vs A. Categories are factual relations only; "
              "no crime/violation/verdict. Aggregate, :representative. False positives: timing "
              "(outlay fy ≠ appropriation fy) and partial corpus (not all appropriation lines ingested).")})

(defn method-note-cid []
  (str "danjo.methodNote:" (:id method-note) ":" (:version method-note)
       "#" (subs (sha256-hex (pr-str method-note)) 0 24)))

(defn reconcile
  "Per-(programCode, fiscalYear) appropriation↔outlay reconciliation for fiscal-year `fy`.
   Returns one map per program with {:appropriated :outlaid :delta :category :source-record-cids}."
  [model fy]
  (let [aps (filter #(= fy (:fiscal-year %)) (:appropriations model))
        ous (filter #(= fy (:fiscal-year %)) (:outlays model))
        pcs (sort (distinct (concat (map :program-code aps) (map :program-code ous))))]
    (for [pc pcs
          :let [a-lines (filter #(= pc (:program-code %)) aps)
                o-lines (filter #(= pc (:program-code %)) ous)
                A (reduce + 0 (map :amount-jpy a-lines))
                O (reduce + 0 (map :amount-jpy o-lines))]]
      {:program-code pc :fiscal-year fy :appropriated A :outlaid O :delta (- O A)
       :category (cond (and (zero? A) (pos? O)) :outlay-without-appropriation-trace
                       (> O A)                  :outlay-exceeds-appropriation
                       :else                    :appropriation-outlay-within)
       :source-record-cids (vec (distinct (mapcat :source-record-cids (concat a-lines o-lines))))})))

(defn observations
  "The divergences (NOT the within-budget reconciliations) as danjo discrepancyObservation
   maps. Each is a FACT to surface, carrying its open method-note + declared false-positive modes."
  [model fy]
  (->> (reconcile model fy)
       (remove #(= :appropriation-outlay-within (:category %)))
       (map (fn [r]
              {:category (:category r)
               :observed-pattern (str (:program-code r) " FY" (:fiscal-year r)
                                       ": outlay " (:outlaid r) " vs appropriation "
                                       (:appropriated r) " (delta " (:delta r) ")")
               :source-record-cids (:source-record-cids r)
               :method-note-cid (method-note-cid)
               :known-false-positive-modes
               ["timing: outlay fiscal-year may differ from appropriation fiscal-year"
                "partial-corpus: some appropriation lines may not be ingested yet"]
               :non-adjudicating true}))))

(defn- obs-id [o]
  (str "danjo-obs:" (name (:category o)) ":" (first (:source-record-cids o))))

(defn- verdict-token? [x]
  (let [s (str/lower-case (str x))]
    (some #(str/includes? s %) rl/forbidden-verdict-tokens)))

(defn observation-datoms
  "danjo.discrepancyObservation maps → append-only EAVT `:danjo.obs/*` (same shape as
   kotoba.py derived_datoms). RAISES (G4) if a verdict token appears in any attr OR in a
   category value; RAISES (G5) if <2 source CIDs. A legal verdict is unrepresentable."
  [obs-list]
  (let [out (mapcat
             (fn [o]
               (when (< (count (:source-record-cids o)) 2)
                 (throw (ex-info "G5: observation needs ≥2 source-record-cids" {:obs o})))
               (let [e (obs-id o)]
                 [[:db/add e :danjo.obs/category (keyword (name (:category o)))]
                  [:db/add e :danjo.obs/non-adjudicating true]
                  [:db/add e :danjo.obs/pattern (:observed-pattern o)]
                  [:db/add e :danjo.obs/source-record-cids (vec (:source-record-cids o))]
                  [:db/add e :danjo.obs/method-note-cid (:method-note-cid o)]
                  [:db/add e :danjo.obs/known-false-positive-modes (vec (:known-false-positive-modes o))]
                  [:db/add e :danjo.obs/sourcing :representative]]))
             obs-list)]
    (doseq [[_ _ a v] out]
      (when (verdict-token? a)
        (throw (ex-info (str "G4: verdict attr " a " is unrepresentable") {:attr a})))
      (when (and (= a :danjo.obs/category) (verdict-token? v))
        (throw (ex-info (str "G4: verdict category " v " is unrepresentable") {:category v}))))
    (vec out)))

(defn -main [& args]
  (let [model (do (load-file "ingest.clj")
                  (let [in (find-ns 'root.danjo.methods.ingest)]
                    (-> ((ns-resolve in 'ingest) "../data/gov-revenue-corpus.jp.edn")
                        ((ns-resolve in 'with-budget) ((ns-resolve in 'ingest-budget) "../data/gov-fiscal-seed.jp.json")))))]
    (doseq [r (reconcile model 2024)]
      (println (:category r) (:program-code r) "A=" (:appropriated r) "O=" (:outlaid r) "Δ=" (:delta r)))
    (println "observations:" (count (observations model 2024)))))
