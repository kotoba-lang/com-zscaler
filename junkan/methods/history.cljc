#!/usr/bin/env bb
;; junkan 循環 — as-of / regime-trajectory reader over the findings ledger.
(ns junkan.methods.history
  "history.cljc — junkan 循環 as-of / HISTORY reader over the findings ledger
  (ADR-2605290927). The ADR's defining data-model claim is that feedback-loop
  analysis is TEMPORAL: a stock's regime is only legible from how it moved across
  transactions. This namespace realizes that promise — it folds the append-only
  commit-DAG (kotoba.cljc) and reads, across consecutive txs, which asymmetry
  stocks (and loops) CHANGED regime (好循環⇄悪循環 — a `regimeShiftEvent` in the
  lexicon). Pure over a tx vector; read-only (G4); HYPOTHESIS (G5)."
  (:require [clojure.string :as str]))

(defn- datoms-of [tx] (get tx ":tx/datoms"))

(defn stock-regimes-of-tx
  "Extract {stock-entity → regime} from one tx's datoms (AVET-style scan of
  :junkan.gov.stock/regime)."
  [tx]
  (reduce (fn [m d]
            (if (= ":junkan.gov.stock/regime" (nth d 2))
              (assoc m (nth d 1) (nth d 3))
              m))
          {} (datoms-of tx)))

(defn loop-regimes-of-tx
  "Extract {loop-entity → regime} from one tx's datoms (:junkan.gov.loop/regime)."
  [tx]
  (reduce (fn [m d]
            (if (= ":junkan.gov.loop/regime" (nth d 2))
              (assoc m (nth d 1) (nth d 3))
              m))
          {} (datoms-of tx)))

(defn- diff-maps
  "[[entity from to] …] for entities whose value changed between two maps
  (entities present in both with differing values)."
  [a b]
  (vec (for [[k v2] b
             :let [v1 (get a k)]
             :when (and v1 (not= v1 v2))]
         [k v1 v2])))

(defn regime-shifts
  "Across the ordered ledger `txs`, the per-step stock-regime + loop-regime shifts.
  Returns a vector of {:from-tx :to-tx :stock-shifts [...] :loop-shifts [...]} for
  each consecutive pair (only pairs with ≥1 shift are kept). Each shift is
  [entity from-regime to-regime] — the regimeShiftEvent the lexicon names."
  [txs]
  (vec
   (for [[a b] (map vector txs (rest txs))
         :let [ss (diff-maps (stock-regimes-of-tx a) (stock-regimes-of-tx b))
               ls (diff-maps (loop-regimes-of-tx a) (loop-regimes-of-tx b))]
         :when (or (seq ss) (seq ls))]
     {:from-tx (get a ":tx/cid") :to-tx (get b ":tx/cid")
      :stock-shifts ss :loop-shifts ls})))

(defn summary
  "Digest of the ledger's temporal shape: tx count + total stock/loop regime shifts."
  [txs]
  (let [shifts (regime-shifts txs)]
    {:txs (count txs)
     :steps-with-shifts (count shifts)
     :stock-shift-count (reduce + (map (comp count :stock-shifts) shifts))
     :loop-shift-count (reduce + (map (comp count :loop-shifts) shifts))
     :shifts shifts}))

;; ── CLI (bb) ─────────────────────────────────────────────────────────────────
#?(:clj
   (defn -main [& args]
     (require 'junkan.methods.kotoba)
     (let [read-log (resolve 'junkan.methods.kotoba/read-log)
           log (or (first args)
                   "20-actors/junkan/data/persisted/junkan.governance.kotoba.edn")
           txs (read-log log)
           s (summary txs)]
       (println (str "ledger: " (:txs s) " txs · " (:steps-with-shifts s)
                     " steps with regime shifts"))
       (println (str "stock regime shifts: " (:stock-shift-count s)
                     " · loop regime shifts: " (:loop-shift-count s)))
       (doseq [step (:shifts s)]
         (doseq [[e from to] (:stock-shifts step)]
           (println (str "  " e ": " from " → " to)))))))

#?(:clj
   (when (= *file* (System/getProperty "babashka.file"))
     (apply -main *command-line-args*)))
