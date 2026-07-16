;; test_discrepancy.clj — appropriation↔outlay reconciliation → non-adjudicating observations.
;; Run: bb test_discrepancy.clj   (or: clojure -M test_discrepancy.clj)   from methods/.
(ns root.danjo.methods.test-discrepancy
  (:require [clojure.string :as str]))

(load-file "discrepancy.clj")
(load-file "ingest.clj")
(alias 'd  'root.danjo.methods.discrepancy)
(alias 'in 'root.danjo.methods.ingest)
(alias 'rl 'root.danjo.methods.revenue-ledger)

(def checks (atom 0)) (def fails (atom 0))
(defn check [l p] (swap! checks inc) (if p (println "  ok  " l) (do (swap! fails inc) (println "  FAIL" l))))
(defn throws? [f] (try (f) false (catch Exception _ true)))

;; helpers
(defn ap [pc a] {:program-code pc :program-name pc :account :general :fiscal-year 2024
                 :amount-jpy a :source-record-cids ["a1" "a2"]})
(defn ou [pc a] {:program-code pc :program-name pc :account :general :cofog "0"
                 :recipient-class "agg" :fiscal-year 2024 :amount-jpy a
                 :source-record-cids ["o1" "o2"]})

;; ── reconcile categories ──
(let [within  {:appropriations [(ap "P" 100)] :outlays [(ou "P" 60)]}
      exceeds {:appropriations [(ap "P" 100)] :outlays [(ou "P" 150)]}
      noapp   {:appropriations []             :outlays [(ou "P" 999)]}]
  (check "O<A → :appropriation-outlay-within"
         (= :appropriation-outlay-within (:category (first (d/reconcile within 2024)))))
  (check "O>A → :outlay-exceeds-appropriation"
         (= :outlay-exceeds-appropriation (:category (first (d/reconcile exceeds 2024)))))
  (check "exceeds delta is exact (1円)"
         (= 50 (:delta (first (d/reconcile exceeds 2024)))))
  (check "A=0,O>0 → :outlay-without-appropriation-trace"
         (= :outlay-without-appropriation-trace (:category (first (d/reconcile noapp 2024)))))

  ;; ── observations: only divergences, never the within-budget facts ──
  (check "within produces NO observation"  (empty? (d/observations within 2024)))
  (check "exceeds produces 1 observation"  (= 1 (count (d/observations exceeds 2024))))
  (let [o (first (d/observations exceeds 2024))]
    (check "observation is :non-adjudicating" (true? (:non-adjudicating o)))
    (check "observation carries a method-note CID (G6)"
           (str/starts-with? (:method-note-cid o) "danjo.methodNote:"))
    (check "observation declares false-positive modes (timing/partial-corpus)"
           (some #(str/includes? % "partial-corpus") (:known-false-positive-modes o)))))

;; ── observation-datoms: shape matches danjo kotoba.py derived_datoms ──
(let [obs (d/observations {:appropriations [(ap "P" 100)] :outlays [(ou "P" 150)]} 2024)
      ds  (d/observation-datoms obs)]
  (check "obs datoms emitted"            (pos? (count ds)))
  (check "all :db/add"                   (every? #(= :db/add (first %)) ds))
  (check ":danjo.obs/non-adjudicating present"
         (some #(and (= :danjo.obs/non-adjudicating (nth % 2)) (true? (nth % 3))) ds))
  (check ":danjo.obs/category present"   (some #(= :danjo.obs/category (nth % 2)) ds)))

;; ── G4: a verdict category is unrepresentable (RAISES) ──
(check "G4: verdict category RAISES"
       (throws? #(d/observation-datoms
                  [{:category :fraud-detected :observed-pattern "x"
                    :source-record-cids ["c1" "c2"] :method-note-cid "m" :non-adjudicating true}])))
;; ── G5: <2 source CIDs RAISES ──
(check "G5: <2 source CIDs RAISES"
       (throws? #(d/observation-datoms
                  [{:category :outlay-exceeds-appropriation :observed-pattern "x"
                    :source-record-cids ["only-one"] :method-note-cid "m" :non-adjudicating true}])))

;; ── JSON budget ingest feeds the reconciler (the existing danjo corpus, gov-fiscal-seed.jp.json) ──
(let [b (in/ingest-budget "../data/gov-fiscal-seed.jp.json")]
  (check "JSON budget ingest: 2 appropriations" (= 2 (count (:appropriations b))))
  (check "JSON budget ingest: 3 outlays"        (= 3 (count (:outlays b))))
  (check "JSON record CIDs are budgetRecord locators"
         (every? #(str/starts-with? (first (:source-record-cids %)) "gov.dataset.budgetRecord:")
                 (:appropriations b)))
  (let [recon (d/reconcile b 2024)]
    (check "MEXT general reconciles WITHIN (O<A, no divergence)"
           (= :appropriation-outlay-within
              (:category (first (filter #(= "JP-MEXT-EDUSCI" (:program-code %)) recon)))))))

;; ── observations persist + bridge through the existing pipeline (run-cycle! :extra-datoms) ──
(let [log (str (System/getProperty "java.io.tmpdir") "/danjo-disc-test-" (rand-int 1000000) ".kotoba.edn")
      _   (when (.exists (clojure.java.io/file log)) (.delete (clojure.java.io/file log)))
      model (in/with-budget (in/ingest "../data/gov-revenue-corpus.jp.edn")
                            {:appropriations [(ap "P" 100)] :outlays [(ou "P" 150)]})
      obs   (d/observations model 2024)
      r     (rl/run-cycle! {:seed model :log-path log :as-of 1
                            :extra-datoms (d/observation-datoms obs)})]
  (check "cycle persisted obs datoms with the model" (pos? (:datom-count r)))
  (check "chain verifies after persisting observations" (:ok (rl/verify-chain log)))
  (.delete (clojure.java.io/file log)))

(println (format "── discrepancy: %d checks, %d failures ──" @checks @fails))
(when (pos? @fails) (System/exit 1))
